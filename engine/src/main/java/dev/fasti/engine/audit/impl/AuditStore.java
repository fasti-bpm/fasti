/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fasti.engine.audit.impl;

import java.io.StringReader;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import dev.fasti.engine.audit.AuditService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the tables of the audit trail with plain JDBC on the connection of the
 * command, so that records are written in the transaction of what they record: if that rolls
 * back, so do they, and if they cannot be written, the command fails.
 *
 * <p>Appending to a chain first locks the row of the chain in {@code FASTI_AUDIT_HEAD} and reads
 * the last position and hash (with {@code select ... for update} where the database holds that
 * lock until the transaction ends, otherwise with an update of the row and a read), inserts the
 * records and moves the head. The lock is held until the transaction ends, so appends to one chain
 * are serialized while other chains (other slots, other nodes) are not affected. The wait for the
 * lock is limited ({@code lockTimeoutSeconds}), so that a transaction that cannot get the head
 * fails instead of hanging.</p>
 *
 * <p>Record ids are time-ordered (the layout of a version 7 UUID), so that new rows go to the end
 * of the primary key, which is the clustered index on MySQL, MariaDB and SQL Server. An id is
 * hashed as the text it is stored as, so records with random ids written before verify as they
 * did.</p>
 *
 * <p>Queries page in the database and exports read one forward-only cursor, so that neither holds
 * more than a page of records in memory. Messages of the database never reach the caller: they are
 * logged, and the exception names only what could not be done.</p>
 */
public final class AuditStore {

  private static final Logger LOG = LoggerFactory.getLogger(AuditStore.class);

  public static final String EVENT_TABLE = "FASTI_AUDIT_EVENT";
  public static final String HEAD_TABLE = "FASTI_AUDIT_HEAD";

  static final String COLUMNS = "ID_, CHAIN_ID_, SEQ_, TIME_, TYPE_, ACTION_, ACTOR_, ENGINE_, TENANT_ID_, PROC_DEF_KEY_, "
      + "PROC_DEF_ID_, PROC_INST_ID_, BUSINESS_KEY_, ENTITY_TYPE_, ENTITY_ID_, ENTITY_NAME_, DETAILS_, PREV_HASH_, HASH_";

  private AuditStore() {
  }

  /** The position and hash of the last record of a chain. */
  public static final class Head {
    public final String chainId;
    public final long sequence;
    public final String hash;
    /** The time of the last record, as {@link #heads(CommandContext, AuditSettings)} reads it; 0 otherwise. */
    public final long time;

    Head(String chainId, long sequence, String hash) {
      this(chainId, sequence, hash, 0);
    }

    Head(String chainId, long sequence, String hash, long time) {
      this.chainId = chainId;
      this.sequence = sequence;
      this.hash = hash;
      this.time = time;
    }
  }

  static Connection connection(CommandContext commandContext) {
    return commandContext.getDbSqlSession().getSqlSession().getConnection();
  }

  static String events(AuditSettings settings) {
    return settings.getTablePrefix() + EVENT_TABLE;
  }

  static String heads(AuditSettings settings) {
    return settings.getTablePrefix() + HEAD_TABLE;
  }

  /** Rows a cursor fetches at a time. */
  static final int FETCH_SIZE = 500;

  /**
   * A failure of the database: logged with what the driver says, reported without it, as driver
   * messages name tables, columns and constraints and the message of the exception reaches clients.
   */
  static ProcessEngineException failure(String what, SQLException e) {
    return failure(what, null, e);
  }

  static ProcessEngineException failure(String what, String detail, SQLException e) {
    LOG.error("The audit trail could not {}{}: {} (SQL state {})", what, detail == null ? "" : " (" + detail + ")",
        e.getMessage(), e.getSQLState(), e);
    return new ProcessEngineException("The audit trail could not " + what + ". The cause is in the log of the server.", e);
  }

  static boolean isTimeout(SQLException e) {
    // query cancelled (PostgreSQL, H2: 57014), lock wait timeout (H2: HYT00, MySQL and MariaDB: 1205 / 3024 / 1969)
    return e instanceof SQLTimeoutException || "57014".equals(e.getSQLState()) || "HYT00".equals(e.getSQLState())
        || "HYT01".equals(e.getSQLState()) || e.getErrorCode() == 1205 || e.getErrorCode() == 3024 || e.getErrorCode() == 1969;
  }

  static void timeout(PreparedStatement statement, int seconds) throws SQLException {
    if (seconds > 0) {
      statement.setQueryTimeout(seconds);
    }
  }

  // ids //////////////////////////////////////////////////////////////////////////////////////

  private static final SecureRandom ID_RANDOM = new SecureRandom();

  /** The millisecond (48 bits) and counter (12 bits) of the last id, so that the ids of this node only grow. */
  private static final AtomicLong ID_CLOCK = new AtomicLong();

  /**
   * A new record id: a version 7 UUID (RFC 9562) as lowercase text, made of the milliseconds of the
   * system clock, a counter within the millisecond and 62 random bits. The ids of this node grow (a
   * counter that runs over moves on to the next millisecond), and ids of several nodes sort by
   * time, so that inserts go to the end of the primary key.
   */
  public static String newId() {
    long now = System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL;
    long clock = ID_CLOCK.updateAndGet(previous -> Math.max(previous + 1, now << 12));
    long millis = (clock >>> 12) & 0xFFFF_FFFF_FFFFL;
    long counter = clock & 0xFFF;
    long high = (millis << 16) | 0x7000L | counter;
    long low = (ID_RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(high, low).toString();
  }

  // writing //////////////////////////////////////////////////////////////////////////////////

  /**
   * Appends the records to the chain, setting their id, position and hashes, and adds a signed
   * checkpoint after every {@code checkpointInterval} records. Returns the checkpoints written.
   */
  public static List<AuditRecord> append(CommandContext commandContext, AuditSettings settings, String chainId,
      List<AuditRecord> records) {
    List<AuditRecord> checkpoints = new ArrayList<>();
    if (records.isEmpty()) {
      return checkpoints;
    }
    Connection connection = connection(commandContext);
    try {
      Head head = lockHead(commandContext, settings, chainId);
      long sequence = head.sequence;
      String previous = head.hash;
      List<AuditRecord> written = new ArrayList<>();
      for (AuditRecord record : records) {
        sequence++;
        if (record.getPurgedThroughHash() != null) {
          attestPurge(settings, record, chainId, sequence);
        }
        seal(record, chainId, sequence, previous);
        previous = record.getHash();
        written.add(record);
        int interval = settings.getCheckpointInterval();
        if (interval > 0 && sequence % interval == 0) {
          sequence++;
          AuditRecord checkpoint = checkpoint(settings, chainId, sequence, previous, record.getTime());
          previous = checkpoint.getHash();
          written.add(checkpoint);
          checkpoints.add(checkpoint);
        }
      }
      insert(connection, settings, written);
      moveHead(connection, settings, chainId, sequence, previous, written.get(written.size() - 1).getTime());
    } catch (SQLException e) {
      if (isTimeout(e)) {
        LOG.error("The audit trail could not lock chain {} within {} seconds: {}", chainId, settings.getLockTimeoutSeconds(),
            e.getMessage());
        throw new ProcessEngineException("The audit trail could not write its records: the chain is held by another "
            + "transaction for longer than " + settings.getLockTimeoutSeconds() + " seconds (fasti.audit.lock-timeout-seconds). "
            + "Nothing was changed; try again.");
      }
      throw failure("write its records", "chain " + chainId, e);
    }
    return checkpoints;
  }

  static void moveHead(Connection connection, AuditSettings settings, String chainId, long sequence, String hash,
      long time) throws SQLException {
    try (PreparedStatement update = connection.prepareStatement(
        "update " + heads(settings) + " set SEQ_ = ?, HASH_ = ?, TIME_ = ? where CHAIN_ID_ = ?")) {
      update.setLong(1, sequence);
      update.setString(2, hash);
      update.setLong(3, time);
      update.setString(4, chainId);
      update.executeUpdate();
    }
  }

  /**
   * Signs the end of a chain: appends a signed checkpoint after its last record, unless that is a
   * checkpoint already, in the transaction of the command. Waits for the head at most
   * {@code lockTimeoutSeconds}. Nothing is written without a key or without checkpoints, for a
   * missing head or an empty chain, or when the last record does not match the head (verifying
   * reports that). Returns the checkpoint written, or {@code null}.
   */
  public static AuditRecord checkpointTail(CommandContext commandContext, AuditSettings settings, String chainId,
      int lockTimeoutSeconds) {
    return checkpointTail(commandContext, settings, chainId, lockTimeoutSeconds, -1);
  }

  /**
   * {@link #checkpointTail(CommandContext, AuditSettings, String, int)}, only if the head is still
   * at {@code expectedSequence} once it is locked (any position with a negative one): a chain that
   * moved meanwhile is written to, and left to its writer.
   */
  public static AuditRecord checkpointTail(CommandContext commandContext, AuditSettings settings, String chainId,
      int lockTimeoutSeconds, long expectedSequence) {
    if (!settings.isSigned() || settings.getCheckpointInterval() <= 0) {
      return null;
    }
    Connection connection = connection(commandContext);
    try {
      Head head = lockHead(commandContext, settings, chainId, false, lockTimeoutSeconds);
      if (head == null || head.sequence <= 0 || (expectedSequence >= 0 && head.sequence != expectedSequence)) {
        return null;
      }
      AuditRecord last = find(commandContext, settings, chainId, head.sequence);
      if (last != null && isCheckpoint(last)) {
        return null;
      }
      if (last == null || !AuditChain.same(head.hash, last.getHash())
          || !AuditChain.same(AuditChain.hash(last), last.getHash())) {
        LOG.warn("The last record of audit trail chain {} does not match its head; the chain is not signed. Verify the "
            + "audit trail.", chainId);
        return null;
      }
      AuditRecord checkpoint = checkpoint(settings, chainId, head.sequence + 1, head.hash,
          Math.max(last.getTime(), ClockUtil.getCurrentTime().getTime()));
      insert(connection, settings, List.of(checkpoint));
      moveHead(connection, settings, chainId, checkpoint.getSequence(), checkpoint.getHash(), checkpoint.getTime());
      return checkpoint;
    } catch (SQLException e) {
      if (isTimeout(e)) {
        throw new ProcessEngineException("The audit trail could not sign chain " + chainId + ": it is held by another "
            + "transaction for longer than " + lockTimeoutSeconds + " seconds.");
      }
      throw failure("sign the end of a chain", "chain " + chainId, e);
    }
  }

  static boolean isCheckpoint(AuditRecord record) {
    return AuditService.TYPE_AUDIT.equals(record.getType()) && AuditService.ACTION_CHECKPOINT.equals(record.getAction());
  }

  /**
   * Completes the details of a purge record once its position is known: what was removed and, with
   * a key, the signature over it, the chain and the id and position of the record.
   */
  static void attestPurge(AuditSettings settings, AuditRecord purge, String chainId, long sequence) {
    purge.setId(newId());
    AuditJson details = purge.getPurgeDetails()
        .put("purgedThroughSeq", purge.getPurgedThroughSequence())
        .put("purgedThroughHash", purge.getPurgedThroughHash());
    if (settings.isSigned()) {
      details.put("keyId", settings.getKeyId())
          .put("hmac", AuditChain.hmacHex(settings.getHmacKey(), AuditChain.purgeText(chainId,
              purge.getPurgedThroughSequence(), purge.getPurgedThroughHash(), purge.getId(), sequence)));
    }
    purge.setDetails(details.toJson());
  }

  static void seal(AuditRecord record, String chainId, long sequence, String previous) {
    if (record.getId() == null) {
      record.setId(newId());
    }
    record.setChainId(chainId);
    record.setSequence(sequence);
    record.setPreviousHash(previous);
    record.setHash(AuditChain.hash(record));
  }

  static AuditRecord checkpoint(AuditSettings settings, String chainId, long sequence, String previous, long time) {
    AuditRecord checkpoint = new AuditRecord(AuditService.TYPE_AUDIT, AuditService.ACTION_CHECKPOINT);
    checkpoint.setTime(time);
    checkpoint.setEngineName(settings.getEngineName());
    checkpoint.setEntityType("chain");
    checkpoint.setEntityId(null);
    checkpoint.setEntityName(chainId);
    AuditJson details = AuditJson.object()
        .put("signedSequence", sequence - 1)
        .put("signedHash", previous)
        .put("interval", settings.getCheckpointInterval());
    if (settings.isSigned()) {
      details.put("keyId", settings.getKeyId())
          .put("hmac", AuditChain.hmacHex(settings.getHmacKey(),
              AuditChain.checkpointText(chainId, sequence - 1, previous, settings.getCheckpointInterval())));
    }
    checkpoint.setDetails(details.toJson());
    seal(checkpoint, chainId, sequence, previous);
    return checkpoint;
  }

  /**
   * Locks the head row of the chain until the end of the transaction and reads it. A missing head
   * (a chain of a band this node did not prepare at startup, or a head removed from outside) is
   * created first, in a transaction of its own.
   */
  static Head lockHead(CommandContext commandContext, AuditSettings settings, String chainId) throws SQLException {
    return lockHead(commandContext, settings, chainId, true);
  }

  /**
   * Locks the head row of the chain until the end of the transaction and reads it; with
   * {@code create} false, a missing head is not created and {@code null} is returned.
   */
  static Head lockHead(CommandContext commandContext, AuditSettings settings, String chainId, boolean create)
      throws SQLException {
    return lockHead(commandContext, settings, chainId, create, settings.getLockTimeoutSeconds());
  }

  /** {@link #lockHead(CommandContext, AuditSettings, String, boolean)}, waiting at most the seconds given (0: as the database does). */
  static Head lockHead(CommandContext commandContext, AuditSettings settings, String chainId, boolean create,
      int lockTimeoutSeconds) throws SQLException {
    Connection connection = connection(commandContext);
    Head head = lockAndReadHead(connection, settings, chainId, lockTimeoutSeconds);
    if (head == null) {
      if (!create) {
        return null;
      }
      // Under REPEATABLE READ or stricter, locking a missing row can leave a lock on the gap
      // (MySQL, MariaDB), which an insert in a transaction of its own on another connection would
      // wait for until the lock wait timeout: then the head is created in this transaction. The
      // engine asks for READ COMMITTED.
      if (connection.getTransactionIsolation() < Connection.TRANSACTION_REPEATABLE_READ
          && createHead(commandContext, settings, chainId)) {
        head = lockAndReadHead(connection, settings, chainId, lockTimeoutSeconds);
      }
    }
    if (head == null) {
      if (!create) {
        return null;
      }
      // it could not be created on its own: in this transaction, which holds the new row until it
      // ends; a concurrent insert of the same head fails one of the two, like any other conflict
      head = lastRecord(connection, settings, chainId);
      insertHead(connection, settings, head);
    }
    return head;
  }

  /**
   * The databases on which {@code select ... for update} locks the row until the transaction ends
   * and waits for it as an update does: the same lock, the same timeouts and the same errors. At
   * its default isolation DB2 holds such a lock only while the cursor is on the row, and SQL Server
   * has no such clause: there the row is locked with an update.
   */
  static final Set<String> SELECT_FOR_UPDATE = Set.of("h2", "postgres", "mysql", "mariadb", "oracle");

  /**
   * Locks the head row of the chain until the end of the transaction and reads it; {@code null} if
   * there is no head. The statement waits for the transaction that holds the head; not for ever. H2
   * does not end a wait for a row when the statement times out, only when the lock timeout of the
   * session is over, so that is set for the statement.
   */
  static Head lockAndReadHead(Connection connection, AuditSettings settings, String chainId, int lockTimeoutSeconds)
      throws SQLException {
    Integer sessionLockTimeout = null;
    if (lockTimeoutSeconds > 0 && "h2".equals(settings.getDatabaseType())) {
      sessionLockTimeout = h2LockTimeout(connection, null);
      h2LockTimeout(connection, lockTimeoutSeconds * 1000);
    }
    try {
      if (settings.getDatabaseType() != null && SELECT_FOR_UPDATE.contains(settings.getDatabaseType())) {
        try (PreparedStatement select = connection.prepareStatement(
            "select SEQ_, HASH_ from " + heads(settings) + " where CHAIN_ID_ = ? for update")) {
          select.setString(1, chainId);
          timeout(select, lockTimeoutSeconds);
          try (ResultSet result = select.executeQuery()) {
            return result.next() ? new Head(chainId, result.getLong(1), result.getString(2)) : null;
          }
        }
      }
      try (PreparedStatement lock = connection.prepareStatement(
          "update " + heads(settings) + " set SEQ_ = SEQ_ where CHAIN_ID_ = ?")) {
        lock.setString(1, chainId);
        timeout(lock, lockTimeoutSeconds);
        lock.executeUpdate();
      }
      // read also when the update counted no row: a driver that counts changed rows rather than
      // matched ones (MySQL Connector/J with useAffectedRows=true) counts none here, although the
      // row is there and locked
      return readHead(connection, settings, chainId);
    } finally {
      if (sessionLockTimeout != null) {
        h2LockTimeout(connection, sessionLockTimeout);
      }
    }
  }

  /**
   * Creates the missing head of a chain in a transaction of its own and commits it, so that the
   * transaction that needs it does not hold a new row until it ends (another transaction that
   * appends to the same new chain would fail on the duplicate). The head continues after the last
   * record of the chain there is (verifying reported the missing head until now). Whether the
   * caller should lock the head again.
   */
  static boolean createHead(CommandContext commandContext, AuditSettings settings, String chainId) {
    CommandExecutor requiresNew = commandContext.getProcessEngineConfiguration().getCommandExecutorTxRequiresNew();
    try {
      return requiresNew.execute(context -> {
        Connection connection = connection(context);
        try {
          if (readHead(connection, settings, chainId) == null) {
            insertHead(connection, settings, lastRecord(connection, settings, chainId));
          }
          return true;
        } catch (SQLException e) {
          // reported below, without the message of the database reaching anyone but the log
          throw new ProcessEngineException("The audit trail could not create the head of chain " + chainId, e);
        }
      });
    } catch (RuntimeException e) {
      // created by another transaction at the same time (the caller locks it then), or this engine
      // cannot run a transaction of its own here (the caller creates it in its transaction then)
      LOG.debug("The head of audit trail chain {} was not created in a transaction of its own", chainId, e);
      return true;
    }
  }

  /** Sets the lock timeout of the H2 session in milliseconds, or reads it with {@code null}. */
  static Integer h2LockTimeout(Connection connection, Integer milliseconds) throws SQLException {
    if (milliseconds == null) {
      try (PreparedStatement select = connection.prepareStatement("call LOCK_TIMEOUT()");
          ResultSet result = select.executeQuery()) {
        return result.next() ? result.getInt(1) : null;
      }
    }
    try (PreparedStatement set = connection.prepareStatement("set LOCK_TIMEOUT " + milliseconds.intValue())) {
      set.execute();
    }
    return milliseconds;
  }

  static Head lastRecord(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement select = connection.prepareStatement("select SEQ_, HASH_ from " + events(settings)
        + " where CHAIN_ID_ = ? and SEQ_ = (select max(SEQ_) from " + events(settings) + " where CHAIN_ID_ = ?)")) {
      select.setString(1, chainId);
      select.setString(2, chainId);
      try (ResultSet result = select.executeQuery()) {
        if (result.next()) {
          return new Head(chainId, result.getLong(1), result.getString(2));
        }
      }
    }
    return new Head(chainId, 0, AuditChain.GENESIS);
  }

  static Head readHead(Connection connection, AuditSettings settings, String chainId) throws SQLException {
    try (PreparedStatement select = connection.prepareStatement(
        "select SEQ_, HASH_ from " + heads(settings) + " where CHAIN_ID_ = ?")) {
      select.setString(1, chainId);
      try (ResultSet result = select.executeQuery()) {
        if (result.next()) {
          return new Head(chainId, result.getLong(1), result.getString(2));
        }
        return null;
      }
    }
  }

  /** Inserts the head of a chain at the record (position 0 and the genesis hash for an empty chain). */
  static void insertHead(Connection connection, AuditSettings settings, Head head) throws SQLException {
    if (head.sequence > 0) {
      LOG.warn("The head of audit trail chain {} was missing; it is recreated from record {}", head.chainId, head.sequence);
    }
    try (PreparedStatement insert = connection.prepareStatement(
        "insert into " + heads(settings) + " (CHAIN_ID_, SEQ_, HASH_, TIME_) values (?, ?, ?, 0)")) {
      insert.setString(1, head.chainId);
      insert.setLong(2, head.sequence);
      insert.setString(3, head.hash);
      insert.executeUpdate();
    }
  }

  /** Creates the head of a chain unless it exists; whether it was created. */
  public static boolean ensureHead(CommandContext commandContext, AuditSettings settings, String chainId) {
    Connection connection = connection(commandContext);
    try {
      if (readHead(connection, settings, chainId) != null) {
        return false;
      }
      insertHead(connection, settings, new Head(chainId, 0, AuditChain.GENESIS));
      return true;
    } catch (SQLException e) {
      throw failure("create the head of a chain", "chain " + chainId, e);
    }
  }

  static void insert(Connection connection, AuditSettings settings, List<AuditRecord> records) throws SQLException {
    boolean national = settings.isNationalCharacterColumns();
    try (PreparedStatement insert = connection.prepareStatement(
        "insert into " + events(settings) + " (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      for (AuditRecord record : records) {
        int i = 1;
        insert.setString(i++, record.getId());
        insert.setString(i++, record.getChainId());
        insert.setLong(i++, record.getSequence());
        insert.setLong(i++, record.getTime());
        insert.setString(i++, record.getType());
        text(insert, i++, record.getAction(), national);
        text(insert, i++, record.getActor(), national);
        text(insert, i++, record.getEngineName(), national);
        text(insert, i++, record.getTenantId(), national);
        text(insert, i++, record.getProcessDefinitionKey(), national);
        text(insert, i++, record.getProcessDefinitionId(), national);
        text(insert, i++, record.getProcessInstanceId(), national);
        text(insert, i++, record.getBusinessKey(), national);
        text(insert, i++, record.getEntityType(), national);
        text(insert, i++, record.getEntityId(), national);
        text(insert, i++, record.getEntityName(), national);
        if (record.getDetails() == null) {
          insert.setNull(i++, national ? Types.NCLOB : Types.CLOB);
        } else if (national) {
          insert.setNCharacterStream(i++, new StringReader(record.getDetails()), record.getDetails().length());
        } else {
          insert.setString(i++, record.getDetails());
        }
        insert.setString(i++, record.getPreviousHash());
        insert.setString(i, record.getHash());
        if (records.size() == 1) {
          insert.executeUpdate();
        } else {
          insert.addBatch();
        }
      }
      if (records.size() > 1) {
        insert.executeBatch();
      }
    }
  }

  /**
   * Binds text; for the NVARCHAR2 columns of Oracle as national characters, so that text the
   * database character set cannot hold is not changed after it was hashed.
   */
  static void text(PreparedStatement statement, int index, String value, boolean national) throws SQLException {
    if (value == null) {
      statement.setNull(index, national ? Types.NVARCHAR : Types.VARCHAR);
    } else if (national) {
      statement.setNString(index, value);
    } else {
      statement.setString(index, value);
    }
  }

  // reading //////////////////////////////////////////////////////////////////////////////////

  static AuditRecord read(ResultSet result) throws SQLException {
    AuditRecord record = new AuditRecord();
    record.id = result.getString("ID_");
    record.chainId = result.getString("CHAIN_ID_");
    record.sequence = result.getLong("SEQ_");
    record.time = result.getLong("TIME_");
    record.type = result.getString("TYPE_");
    record.action = result.getString("ACTION_");
    record.actor = result.getString("ACTOR_");
    record.engineName = result.getString("ENGINE_");
    record.tenantId = result.getString("TENANT_ID_");
    record.processDefinitionKey = result.getString("PROC_DEF_KEY_");
    record.processDefinitionId = result.getString("PROC_DEF_ID_");
    record.processInstanceId = result.getString("PROC_INST_ID_");
    record.businessKey = result.getString("BUSINESS_KEY_");
    record.entityType = result.getString("ENTITY_TYPE_");
    record.entityId = result.getString("ENTITY_ID_");
    record.entityName = result.getString("ENTITY_NAME_");
    record.details = result.getString("DETAILS_");
    record.previousHash = result.getString("PREV_HASH_");
    record.hash = result.getString("HASH_");
    return record;
  }

  /**
   * The statement that selects a page of the records of a query. The page is cut in the database
   * ({@code limit .. offset ..} on H2, PostgreSQL, MySQL and MariaDB; {@code offset .. fetch next ..}
   * on Oracle, SQL Server and DB2), so that the rows before and after it never reach the engine.
   * Binds the filters, then the page.
   */
  static String selectSql(AuditSettings settings, AuditEventQueryImpl query, List<String> tenants, long firstResult,
      long maxResults, List<Object> parameters) {
    String where = where(query, tenants, parameters);
    // one direction for all three columns, so that the index FASTI_IDX_AUDIT_ORDER(TIME_, CHAIN_ID_, SEQ_)
    // gives the order (read forwards or backwards) and the database stops after the page
    String order = query.isOldestFirst() ? "TIME_ asc, CHAIN_ID_ asc, SEQ_ asc" : "TIME_ desc, CHAIN_ID_ desc, SEQ_ desc";
    String sql = "select " + COLUMNS + " from " + events(settings) + where + " order by " + order;
    if (firstResult <= 0 && maxResults >= Integer.MAX_VALUE) {
      return sql;
    }
    if (isOffsetFetch(settings.getDatabaseType())) {
      parameters.add(firstResult);
      parameters.add(maxResults);
      return sql + " offset ? rows fetch next ? rows only";
    }
    parameters.add(maxResults);
    parameters.add(firstResult);
    return sql + " limit ? offset ?";
  }

  static boolean isOffsetFetch(String databaseType) {
    if (databaseType == null) {
      return false;
    }
    String type = databaseType.toLowerCase(Locale.ROOT);
    return type.equals("oracle") || type.equals("mssql") || type.equals("db2");
  }

  /** A page of the records the query selects, limited to the tenants (all with {@code null}). */
  public static List<AuditRecord> select(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants, int firstResult, int maxResults) {
    List<AuditRecord> records = new ArrayList<>();
    if (maxResults <= 0) {
      return records;
    }
    List<Object> parameters = new ArrayList<>();
    String sql = selectSql(settings, query, tenants, firstResult, maxResults, parameters);
    try (PreparedStatement select = connection(commandContext).prepareStatement(sql)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      select.setFetchSize(Math.min(maxResults, FETCH_SIZE));
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read records", e);
    }
    return records;
  }

  /**
   * Hands the first {@code maxResults} records the query selects to the consumer, one by one, from
   * one forward-only cursor that fetches {@value #FETCH_SIZE} rows at a time; how many.
   */
  public static long forEach(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants, int maxResults, Consumer<AuditRecord> consumer) {
    if (maxResults <= 0) {
      return 0;
    }
    List<Object> parameters = new ArrayList<>();
    String sql = selectSql(settings, query, tenants, 0, maxResults, parameters);
    Connection connection = connection(commandContext);
    long count = 0;
    try (PreparedStatement select = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      select.setFetchSize(streamingFetchSize(connection));
      try (ResultSet result = select.executeQuery()) {
        while (count < maxResults && result.next()) {
          consumer.accept(read(result));
          count++;
        }
      }
    } catch (SQLException e) {
      throw failure("read records", e);
    }
    return count;
  }

  /**
   * The fetch size that makes the driver read a result as a cursor. MySQL Connector/J buffers the
   * whole result unless asked to stream row by row with {@code Integer.MIN_VALUE}; the other drivers
   * (MariaDB Connector/J included) fetch the given number of rows at a time.
   */
  static int streamingFetchSize(Connection connection) {
    try {
      String driver = connection.getMetaData().getDriverName();
      if (driver != null && driver.toLowerCase(Locale.ROOT).contains("mysql connector")) {
        return Integer.MIN_VALUE;
      }
    } catch (SQLException | RuntimeException e) {
      // the default
    }
    return FETCH_SIZE;
  }

  /**
   * The statement that counts the records of a query. With a positive {@code limit} the database
   * stops once it has found that many records; the result is then at most {@code limit}. That caps
   * the records counted, not the rows examined: a filter no index serves may still read many rows.
   */
  static String countSql(AuditSettings settings, AuditEventQueryImpl query, List<String> tenants, long limit,
      List<Object> parameters) {
    String where = where(query, tenants, parameters);
    if (limit <= 0) {
      return "select count(*) from " + events(settings) + where;
    }
    String type = settings.getDatabaseType() == null ? "" : settings.getDatabaseType().toLowerCase(Locale.ROOT);
    // the limit is a number of the engine, not text of a request: written into the statement, as
    // not every database takes a parameter there
    String rows;
    if (type.equals("mssql")) {
      rows = "select top " + limit + " 1 as X_ from " + events(settings) + where;
    } else if (type.equals("oracle") || type.equals("db2")) {
      rows = "select 1 as X_ from " + events(settings) + where + " fetch first " + limit + " rows only";
    } else {
      rows = "select 1 as X_ from " + events(settings) + where + " limit " + limit;
    }
    return "select count(*) from (" + rows + ") T_";
  }

  public static long count(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants) {
    return count(commandContext, settings, query, tenants, 0);
  }

  /** The number of records the query selects; counted up to {@code limit} if that is positive. */
  public static long count(CommandContext commandContext, AuditSettings settings, AuditEventQueryImpl query,
      List<String> tenants, long limit) {
    List<Object> parameters = new ArrayList<>();
    String sql = countSql(settings, query, tenants, limit, parameters);
    try (PreparedStatement select = connection(commandContext).prepareStatement(sql)) {
      bind(select, parameters);
      timeout(select, settings.getQueryTimeoutSeconds());
      try (ResultSet result = select.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    } catch (SQLException e) {
      throw failure("count records", e);
    }
  }

  /**
   * The conditions of a query. With {@code tenants}, only the records of those tenants and the
   * records without tenant, as the tenant check of the engine does for its own queries.
   */
  static String where(AuditEventQueryImpl query, List<String> tenants, List<Object> parameters) {
    List<String> conditions = new ArrayList<>();
    if (tenants != null) {
      if (tenants.isEmpty()) {
        conditions.add("TENANT_ID_ is null");
      } else {
        StringBuilder in = new StringBuilder("(TENANT_ID_ is null or TENANT_ID_ in (");
        for (int i = 0; i < tenants.size(); i++) {
          in.append(i == 0 ? "?" : ", ?");
          parameters.add(tenants.get(i));
        }
        conditions.add(in.append("))").toString());
      }
    }
    condition(conditions, parameters, "PROC_INST_ID_", query.processInstanceId);
    condition(conditions, parameters, "BUSINESS_KEY_", query.businessKey);
    condition(conditions, parameters, "ACTOR_", query.actor);
    if (query.systemActor) {
      conditions.add("ACTOR_ is null");
    }
    if (query.types != null && !query.types.isEmpty()) {
      StringBuilder in = new StringBuilder("TYPE_ in (");
      for (int i = 0; i < query.types.size(); i++) {
        in.append(i == 0 ? "?" : ", ?");
        parameters.add(query.types.get(i));
      }
      conditions.add(in.append(')').toString());
    }
    condition(conditions, parameters, "ACTION_", query.action);
    condition(conditions, parameters, "TENANT_ID_", query.tenantId);
    condition(conditions, parameters, "PROC_DEF_KEY_", query.processDefinitionKey);
    condition(conditions, parameters, "ENTITY_ID_", query.entityId);
    condition(conditions, parameters, "CHAIN_ID_", query.chainId);
    if (query.from != null) {
      conditions.add("TIME_ >= ?");
      parameters.add(query.from.getTime());
    }
    if (query.to != null) {
      conditions.add("TIME_ < ?");
      parameters.add(query.to.getTime());
    }
    return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
  }

  static void condition(List<String> conditions, List<Object> parameters, String column, String value) {
    if (value != null) {
      conditions.add(column + " = ?");
      parameters.add(value);
    }
  }

  static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
    for (int i = 0; i < parameters.size(); i++) {
      Object value = parameters.get(i);
      if (value instanceof Long) {
        statement.setLong(i + 1, (Long) value);
      } else if (value instanceof Integer) {
        statement.setInt(i + 1, (Integer) value);
      } else {
        statement.setString(i + 1, (String) value);
      }
    }
  }

  /**
   * The assignee of a task as last committed to the history ({@code ACT_HI_TASKINST}), or
   * {@code null}: what an update of the task changes it from.
   */
  public static String historicAssignee(CommandContext commandContext, AuditSettings settings, String taskId) {
    if (taskId == null) {
      return null;
    }
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select ASSIGNEE_ from " + settings.getTablePrefix() + "ACT_HI_TASKINST where ID_ = ?")) {
      select.setString(1, taskId);
      try (ResultSet result = select.executeQuery()) {
        return result.next() ? result.getString(1) : null;
      }
    } catch (SQLException e) {
      throw failure("read the assignee of a task", "task " + taskId, e);
    }
  }

  /**
   * Every chain with a head or with records, sorted. The chains of the records are found one after
   * the other in the index {@code FASTI_IDX_AUDIT_CHAIN} ({@code min(CHAIN_ID_)} after the one
   * before), so that each takes one look into the index instead of a read of every record.
   */
  public static List<String> chainIds(CommandContext commandContext, AuditSettings settings) {
    TreeSet<String> ids = new TreeSet<>();
    Connection connection = connection(commandContext);
    try {
      for (Head head : heads(connection, settings)) {
        ids.add(head.chainId);
      }
      try (PreparedStatement first = connection.prepareStatement("select min(CHAIN_ID_) from " + events(settings));
          PreparedStatement next = connection.prepareStatement(
              "select min(CHAIN_ID_) from " + events(settings) + " where CHAIN_ID_ > ?")) {
        timeout(first, settings.getQueryTimeoutSeconds());
        timeout(next, settings.getQueryTimeoutSeconds());
        String chainId = firstText(first);
        while (chainId != null) {
          ids.add(chainId);
          next.setString(1, chainId);
          chainId = firstText(next);
        }
      }
    } catch (SQLException e) {
      throw failure("list chains", e);
    }
    return new ArrayList<>(ids);
  }

  static String firstText(PreparedStatement select) throws SQLException {
    try (ResultSet result = select.executeQuery()) {
      return result.next() ? result.getString(1) : null;
    }
  }

  /** The heads of every chain. */
  public static List<Head> heads(CommandContext commandContext, AuditSettings settings) {
    try {
      return heads(connection(commandContext), settings);
    } catch (SQLException e) {
      throw failure("list chains", e);
    }
  }

  static List<Head> heads(Connection connection, AuditSettings settings) throws SQLException {
    List<Head> heads = new ArrayList<>();
    try (PreparedStatement select = connection.prepareStatement("select CHAIN_ID_, SEQ_, HASH_, TIME_ from " + heads(settings));
        ResultSet result = select.executeQuery()) {
      while (result.next()) {
        heads.add(new Head(result.getString(1), result.getLong(2), result.getString(3), result.getLong(4)));
      }
    }
    return heads;
  }

  public static Head head(CommandContext commandContext, AuditSettings settings, String chainId) {
    try {
      return readHead(connection(commandContext), settings, chainId);
    } catch (SQLException e) {
      throw failure("read the head of a chain", "chain " + chainId, e);
    }
  }

  /** Up to {@code limit} records of the chain after the position, in order. */
  public static List<AuditRecord> readChain(CommandContext commandContext, AuditSettings settings, String chainId,
      long afterSequence, int limit) {
    List<AuditRecord> records = new ArrayList<>();
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ > ? order by SEQ_ asc")) {
      select.setString(1, chainId);
      select.setLong(2, afterSequence);
      select.setMaxRows(limit);
      select.setFetchSize(Math.min(limit, 500));
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read a chain", "chain " + chainId, e);
    }
    return records;
  }

  /** The purge records of a chain, newest first. */
  public static List<AuditRecord> purges(CommandContext commandContext, AuditSettings settings, String chainId) {
    List<AuditRecord> records = new ArrayList<>();
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and TYPE_ = ? and ACTION_ = ? order by SEQ_ desc")) {
      select.setString(1, chainId);
      select.setString(2, AuditService.TYPE_AUDIT);
      select.setString(3, AuditService.ACTION_PURGE);
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          records.add(read(result));
        }
      }
    } catch (SQLException e) {
      throw failure("read the purges of a chain", "chain " + chainId, e);
    }
    return records;
  }

  /** The records at the start of a chain that one batch of a purge removes. */
  public static final class PurgeRange {
    public final long from;
    public final long through;
    /** The hash of the record at {@link #through}. */
    public final String throughHash;
    /** Whether the record after it is not old enough to be purged: the purge of the chain is complete. */
    public final boolean complete;

    PurgeRange(long from, long through, String throughHash, boolean complete) {
      this.from = from;
      this.through = through;
      this.throughHash = throughHash;
      this.complete = complete;
    }
  }

  /**
   * The records that one batch of a purge before the time removes: from the first record of the
   * chain, at most {@code limit} of them, up to the first one at or after the time. Only the start
   * of the chain is read, in the order of the index of the chain, so that a purge reads the records
   * it removes and the next one, not the whole chain. {@code null} if nothing is to be purged: the
   * chain is empty or starts with a record at or after the time, or a record is missing among the
   * first ones (verifying reports that).
   */
  public static PurgeRange purgeRange(CommandContext commandContext, AuditSettings settings, String chainId, long before,
      int limit) {
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select SEQ_, TIME_, HASH_ from " + events(settings) + " where CHAIN_ID_ = ? order by SEQ_ asc")) {
      select.setString(1, chainId);
      select.setMaxRows(limit + 1);
      select.setFetchSize(Math.min(limit + 1, FETCH_SIZE));
      long from = 0;
      long through = 0;
      String throughHash = null;
      try (ResultSet result = select.executeQuery()) {
        while (result.next()) {
          long sequence = result.getLong(1);
          if (from == 0) {
            from = sequence;
          } else if (sequence != through + 1) {
            LOG.warn("Audit trail chain {} has no record {}; it is not purged. Verify the audit trail.", chainId,
                through + 1);
            return null;
          }
          if (result.getLong(2) >= before) {
            return through == 0 ? null : new PurgeRange(from, through, throughHash, true);
          }
          if (sequence - from >= limit) {
            // the record after the batch is old as well
            break;
          }
          through = sequence;
          throughHash = result.getString(3);
        }
      }
      return through == 0 ? null : new PurgeRange(from, through, throughHash, false);
    } catch (SQLException e) {
      throw failure("find what to purge", "chain " + chainId, e);
    }
  }

  /** The record at a position, or {@code null}. */
  public static AuditRecord find(CommandContext commandContext, AuditSettings settings, String chainId, long sequence) {
    try (PreparedStatement select = connection(commandContext).prepareStatement(
        "select " + COLUMNS + " from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ = ?")) {
      select.setString(1, chainId);
      select.setLong(2, sequence);
      try (ResultSet result = select.executeQuery()) {
        return result.next() ? read(result) : null;
      }
    } catch (SQLException e) {
      throw failure("read a chain", "chain " + chainId, e);
    }
  }

  /** Removes the records of the chain up to the position; how many. */
  public static long deleteThrough(CommandContext commandContext, AuditSettings settings, String chainId, long sequence) {
    try (PreparedStatement delete = connection(commandContext).prepareStatement(
        "delete from " + events(settings) + " where CHAIN_ID_ = ? and SEQ_ <= ?")) {
      delete.setString(1, chainId);
      delete.setLong(2, sequence);
      return delete.executeUpdate();
    } catch (SQLException e) {
      throw failure("purge a chain", "chain " + chainId, e);
    }
  }

  // schema ///////////////////////////////////////////////////////////////////////////////////

  public static boolean isTablePresent(CommandContext commandContext) {
    return commandContext.getDbSqlSession().isTablePresent(EVENT_TABLE)
        && commandContext.getDbSqlSession().isTablePresent(HEAD_TABLE);
  }

  public static String createResource(String databaseType) {
    return "dev/fasti/engine/audit/db/create/fasti." + databaseType + ".create.audit.sql";
  }

  public static String dropResource(String databaseType) {
    return "dev/fasti/engine/audit/db/drop/fasti." + databaseType + ".drop.audit.sql";
  }

  public static void createTables(CommandContext commandContext) {
    String type = commandContext.getProcessEngineConfiguration().getDatabaseType();
    LOG.info("Creating the tables of the audit trail ({})", createResource(type));
    commandContext.getDbSqlSession().executeSchemaResource("create", "audit", createResource(type), false);
  }

  public static void dropTables(CommandContext commandContext) {
    String type = commandContext.getProcessEngineConfiguration().getDatabaseType();
    commandContext.getDbSqlSession().executeSchemaResource("drop", "audit", dropResource(type), false);
  }
}
