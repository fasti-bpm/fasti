/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoManager;
import org.camunda.bpm.webapp.impl.WebappLogger;

/**
 * Keeps the web application preferences of a user in the engine's user info
 * (ACT_ID_INFO), so they need no schema change and work with read-only
 * identity providers (LDAP) too: user info always lives in the engine
 * database, whatever provider supplies the users.
 *
 * <p>A user info value holds at most 255 characters, so the document is
 * stored Base64 encoded (plain ASCII, safe for byte- and character-sized
 * columns alike) in numbered chunks ({@code fasti.prefs.0}, ...), next to
 * their number ({@code fasti.prefs.chunks}) and a SHA-256 checksum of the
 * encoded document ({@code fasti.prefs.sum}). Documents written before the
 * checksum was introduced are read as well.
 *
 * <p>ACT_ID_INFO has no unique constraint on user and key, so this class
 * takes care of consistency itself:
 * <ul>
 *   <li>Writers of the same user are serialised by locking a row of their
 *   own ({@code fasti.prefs.lock}) with an update before they read. Its id is
 *   derived from the user id, so the primary key admits only one such row
 *   even when two nodes create it at the same time. A lock in the JVM keeps
 *   writers of one node from competing for the row.</li>
 *   <li>A reader fetches all rows in one query and accepts a document only if
 *   its chunks match the checksum and decode to a valid document. Rows that
 *   occur twice (left by concurrent first saves of older versions) are
 *   tolerated and removed.</li>
 * </ul>
 */
public class UserPreferencesStore {

  protected static final WebappLogger LOGGER = WebappLogger.INSTANCE;

  public static final String KEY_PREFIX = "fasti.prefs.";
  public static final String COUNT_KEY = KEY_PREFIX + "chunks";
  /** the checksum of the encoded document: tells whether all chunks come from one write */
  public static final String CHECKSUM_KEY = KEY_PREFIX + "sum";
  /** the row writers of a user lock */
  public static final String LOCK_KEY = KEY_PREFIX + "lock";
  static final int CHUNK_SIZE = 255;
  /** more chunks than the largest accepted document needs */
  static final int MAX_CHUNKS = 1024;
  /** combinations of duplicated rows tried before a document is given up as unreadable */
  static final int MAX_CANDIDATES = 64;
  static final String LOCK_ID_PREFIX = "fasti-prefs-";

  private static final ReentrantLock[] LOCAL_LOCKS = new ReentrantLock[64];

  static {
    for (int i = 0; i < LOCAL_LOCKS.length; i++) {
      LOCAL_LOCKS[i] = new ReentrantLock();
    }
  }

  protected final ProcessEngine processEngine;

  public UserPreferencesStore(ProcessEngine processEngine) {
    this.processEngine = processEngine;
  }

  /** The stored document of the user, or {@code null} if there is none or it cannot be read. */
  public String load(String userId) {
    Snapshot snapshot = execute(commandContext -> new Snapshot(readRows(commandContext, userId)));
    if (snapshot.document == null && snapshot.hasRows) {
      // a database without statement-level read consistency may have shown
      // the rows of a write that ran alongside only in part
      snapshot = execute(commandContext -> new Snapshot(readRows(commandContext, userId)));
    }
    if (snapshot.duplicated) {
      try {
        repair(userId);
      } catch (RuntimeException e) {
        LOGGER.warnUserPreferencesNotRepaired(userId, e);
      }
    }
    return snapshot.document;
  }

  /** Replaces the stored document of the user. */
  public void save(String userId, String document) {
    withWriteLock(userId, (commandContext, rows) -> write(commandContext, userId, rows, document));
  }

  /** Removes rows that occur twice, keeping the document if one can be read. */
  protected void repair(String userId) {
    withWriteLock(userId, (commandContext, rows) -> {
      if (!hasDuplicates(rows)) {
        // repaired by a write in the meantime
        return;
      }
      String document = resolve(rows);
      if (document != null) {
        write(commandContext, userId, rows, document);
      } else {
        // nothing readable is left: start afresh
        IdentityInfoManager manager = commandContext.getIdentityInfoManager();
        for (IdentityInfoEntity row : rows) {
          manager.deleteIdentityInfo(row);
        }
      }
    });
  }

  // writing ////////////////////////////////////////////////////////////////////////////////

  /**
   * Runs the body in a transaction that holds the lock row of the user, with
   * the rows read after the lock was taken.
   */
  protected void withWriteLock(String userId, BiConsumer<CommandContext, List<IdentityInfoEntity>> body) {
    ReentrantLock localLock = localLock(userId);
    localLock.lock();
    try {
      boolean done = execute(commandContext -> {
        if (!lockRow(commandContext, userId)) {
          return false;
        }
        body.accept(commandContext, readRows(commandContext, userId));
        return true;
      });
      if (!done) {
        createLockRow(userId);
        execute(commandContext -> {
          // should the row still be missing, the lock in the JVM is all there is
          lockRow(commandContext, userId);
          body.accept(commandContext, readRows(commandContext, userId));
          return null;
        });
      }
    } finally {
      localLock.unlock();
    }
  }

  protected ReentrantLock localLock(String userId) {
    return LOCAL_LOCKS[Math.floorMod(userId.hashCode(), LOCAL_LOCKS.length)];
  }

  /** Locks the lock row of the user until the transaction ends; false if there is none. */
  protected boolean lockRow(CommandContext commandContext, String userId) {
    String sql = "update " + tablePrefix() + "ACT_ID_INFO set VALUE_ = VALUE_ where ID_ = ?";
    try (PreparedStatement statement = commandContext.getDbSqlSession().getSqlSession().getConnection()
        .prepareStatement(sql)) {
      statement.setString(1, lockRowId(userId));
      return statement.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new ProcessEngineException("Could not lock the preferences of user '" + userId + "'", e);
    }
  }

  /** Creates the lock row of the user in a transaction of its own. */
  protected void createLockRow(String userId) {
    try {
      configuration().getCommandExecutorTxRequiresNew().execute(commandContext -> {
        IdentityInfoEntity row = new IdentityInfoEntity();
        row.setId(lockRowId(userId));
        row.setUserId(userId);
        row.setType(IdentityInfoEntity.TYPE_USERINFO);
        row.setKey(LOCK_KEY);
        row.setValue("1");
        commandContext.getDbEntityManager().insert(row);
        return null;
      });
    } catch (RuntimeException e) {
      // most likely another writer created it at the same time, as the primary
      // key admits only one; any other failure shows again in the next step
    }
  }

  /** The id of the lock row: derived from the user id, it is the same on every node. */
  static String lockRowId(String userId) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(userId.getBytes(StandardCharsets.UTF_8));
      StringBuilder id = new StringBuilder(LOCK_ID_PREFIX);
      // 64 characters fit every ID_ column
      for (int i = 0; id.length() < 64; i++) {
        id.append(Character.forDigit((hash[i / 2] >> (i % 2 == 0 ? 4 : 0)) & 0xf, 16));
      }
      return id.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Makes the rows of the user hold the given document: rows are updated in
   * place, missing ones inserted, surplus and duplicated ones deleted.
   */
  protected void write(CommandContext commandContext, String userId, List<IdentityInfoEntity> rows,
      String document) {
    String encoded = Base64.getEncoder().encodeToString(document.getBytes(StandardCharsets.UTF_8));
    List<String> chunks = new ArrayList<>();
    for (int start = 0; start < encoded.length(); start += CHUNK_SIZE) {
      chunks.add(encoded.substring(start, Math.min(encoded.length(), start + CHUNK_SIZE)));
    }
    Map<String, String> wanted = new LinkedHashMap<>();
    for (int i = 0; i < chunks.size(); i++) {
      wanted.put(KEY_PREFIX + i, chunks.get(i));
    }
    wanted.put(COUNT_KEY, Integer.toString(chunks.size()));
    wanted.put(CHECKSUM_KEY, checksum(encoded));

    Map<String, List<IdentityInfoEntity>> rowsByKey = new LinkedHashMap<>();
    for (IdentityInfoEntity row : rows) {
      rowsByKey.computeIfAbsent(row.getKey(), key -> new ArrayList<>()).add(row);
    }
    IdentityInfoManager manager = commandContext.getIdentityInfoManager();
    for (Map.Entry<String, List<IdentityInfoEntity>> entry : rowsByKey.entrySet()) {
      List<IdentityInfoEntity> found = entry.getValue();
      String value = wanted.remove(entry.getKey());
      int kept = 0;
      if (value != null) {
        // flushed as an update if the value changed
        found.get(0).setValue(value);
        kept = 1;
      }
      for (int i = kept; i < found.size(); i++) {
        manager.deleteIdentityInfo(found.get(i));
      }
    }
    for (Map.Entry<String, String> entry : wanted.entrySet()) {
      IdentityInfoEntity row = new IdentityInfoEntity();
      row.setUserId(userId);
      row.setType(IdentityInfoEntity.TYPE_USERINFO);
      row.setKey(entry.getKey());
      row.setValue(entry.getValue());
      commandContext.getDbEntityManager().insert(row);
    }
  }

  // reading ////////////////////////////////////////////////////////////////////////////////

  /** All preference rows of the user except the lock row, in one query. */
  @SuppressWarnings("unchecked")
  protected List<IdentityInfoEntity> readRows(CommandContext commandContext, String userId) {
    List<IdentityInfoEntity> rows = new ArrayList<>();
    for (IdentityInfoEntity row : (List<IdentityInfoEntity>) commandContext.getDbEntityManager()
        .selectList("selectIdentityInfoByUserId", userId)) {
      String key = row.getKey();
      if (userId.equals(row.getUserId()) && IdentityInfoEntity.TYPE_USERINFO.equals(row.getType())
          && key != null && key.startsWith(KEY_PREFIX) && !LOCK_KEY.equals(key)) {
        rows.add(row);
      }
    }
    return rows;
  }

  static boolean hasDuplicates(List<IdentityInfoEntity> rows) {
    Set<String> keys = new HashSet<>();
    for (IdentityInfoEntity row : rows) {
      if (!keys.add(row.getKey())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The document the rows hold, or {@code null} if they hold none that is
   * complete and valid. Where a key occurs more than once, the combinations of
   * its values are tried in turn, up to {@value #MAX_CANDIDATES} of them.
   */
  static String resolve(List<IdentityInfoEntity> rows) {
    Map<String, List<String>> values = new HashMap<>();
    for (IdentityInfoEntity row : rows) {
      List<String> known = values.computeIfAbsent(row.getKey(), key -> new ArrayList<>());
      if (row.getValue() != null && !known.contains(row.getValue())) {
        known.add(row.getValue());
      }
    }
    List<String> checksums = values.getOrDefault(CHECKSUM_KEY, List.of());
    int[] budget = { MAX_CANDIDATES };
    candidates:
    for (String countValue : values.getOrDefault(COUNT_KEY, List.of())) {
      int count;
      try {
        count = Integer.parseInt(countValue);
      } catch (NumberFormatException e) {
        continue;
      }
      if (count < 1 || count > MAX_CHUNKS) {
        continue;
      }
      List<List<String>> chunkValues = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        List<String> chunk = values.get(KEY_PREFIX + i);
        if (chunk == null || chunk.isEmpty()) {
          continue candidates;
        }
        chunkValues.add(chunk);
      }
      String document = firstValid(chunkValues, checksums, budget);
      if (document != null) {
        return document;
      }
    }
    return null;
  }

  static String firstValid(List<List<String>> chunkValues, List<String> checksums, int[] budget) {
    int[] choice = new int[chunkValues.size()];
    while (budget[0]-- > 0) {
      StringBuilder encoded = new StringBuilder();
      for (int i = 0; i < choice.length; i++) {
        encoded.append(chunkValues.get(i).get(choice[i]));
      }
      String document = decode(encoded.toString(), checksums);
      if (document != null) {
        return document;
      }
      // next combination
      int i = 0;
      while (i < choice.length && ++choice[i] == chunkValues.get(i).size()) {
        choice[i++] = 0;
      }
      if (i == choice.length) {
        return null;
      }
    }
    return null;
  }

  /** The document, if the encoded text matches a checksum (when there is one) and is valid. */
  static String decode(String encoded, List<String> checksums) {
    if (!checksums.isEmpty() && !checksums.contains(checksum(encoded))) {
      return null;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(encoded);
      String document = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
      UserPreferencesResource.validate(document);
      return document;
    } catch (IllegalArgumentException | CharacterCodingException e) {
      return null;
    }
  }

  static String checksum(String encoded) {
    try {
      return Base64.getEncoder().encodeToString(
          MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** What one read found. */
  protected static class Snapshot {
    protected final String document;
    protected final boolean hasRows;
    protected final boolean duplicated;

    protected Snapshot(List<IdentityInfoEntity> rows) {
      this.document = resolve(rows);
      this.hasRows = !rows.isEmpty();
      this.duplicated = hasDuplicates(rows);
    }
  }

  // engine access //////////////////////////////////////////////////////////////////////////

  protected ProcessEngineConfigurationImpl configuration() {
    return (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
  }

  protected String tablePrefix() {
    String prefix = configuration().getDatabaseTablePrefix();
    return prefix == null ? "" : prefix;
  }

  protected <T> T execute(Function<CommandContext, T> body) {
    return configuration().getCommandExecutorTxRequired().execute(body::apply);
  }
}
