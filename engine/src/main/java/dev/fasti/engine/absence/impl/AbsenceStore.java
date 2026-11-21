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
package dev.fasti.engine.absence.impl;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.fasti.engine.absence.Absence;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCache;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;

/**
 * Reads and writes absences as user info ({@code ACT_ID_INFO}, type {@code userinfo}) within a
 * command. Times are stored as ISO-8601 instants in UTC. Writing replaces all rows of an absence,
 * which also removes duplicates left by concurrent first writes.
 *
 * <p>The Camunda 7.24 schema has no index on ACT_ID_INFO besides its primary key, so there each
 * statement here reads the table once; the optional index FASTI_IDX_ID_INFO_USER (USER_ID_, KEY_)
 * makes the lookup of one user an index seek. A command remembers what it looked up
 * ({@link AbsenceCache}); without that index, the second user a command looks up makes it read
 * all absences with one statement ({@link #findAll}), which answers every later lookup. A listing
 * reads all absences with one statement in any case. Ended absences are removed whenever an
 * absence is written ({@link #deleteEnded}), so the rows are those of current and upcoming
 * absences.</p>
 */
public final class AbsenceStore {

  public static final String KEY_FROM = "fasti.absence.from";
  public static final String KEY_UNTIL = "fasti.absence.until";
  public static final String KEY_SUBSTITUTE = "fasti.absence.substitute";
  public static final String KEY_NOTE = "fasti.absence.note";

  static final Set<String> KEYS = Set.of(KEY_FROM, KEY_UNTIL, KEY_SUBSTITUTE, KEY_NOTE);

  private AbsenceStore() {
  }

  /** The absence of the user, or {@code null} if none is set (or the stored values are unreadable). */
  public static Absence find(CommandContext commandContext, String userId) {
    if (userId == null) {
      return null;
    }
    AbsenceCache cache = AbsenceCache.of(commandContext);
    if (cache != null) {
      Optional<Absence> known = cache.getAbsence(userId);
      if (known != null) {
        return known.orElse(null);
      }
    }
    if (cache != null && cache.countLookup() > 1 && cache.isReadAllPreferred()) {
      // the command asks for a second user (a substitute on the way, another assignee) and the
      // table has no index on the user, so this lookup reads the whole table anyway: one
      // statement for all absences answers this and every later lookup of the command
      return findAll(commandContext).get(userId);
    }
    Absence absence = toAbsence(userId, rows(commandContext, userId));
    if (cache != null) {
      cache.putAbsence(userId, absence);
    }
    return absence;
  }

  /**
   * Whether the lookups of several users in this command are better answered by
   * {@link #findAll}: only where ACT_ID_INFO is known to have no index on the user.
   */
  public static boolean prefersReadingAll(CommandContext commandContext) {
    AbsenceCache cache = AbsenceCache.of(commandContext);
    return cache != null && cache.isReadAllPreferred();
  }

  /**
   * The absences of all users (of any time), by user, read with one statement. Later lookups of
   * the command are answered from the result. The rows are not put into the entity cache of the
   * command (which would hold and dirty-check every absence row until the command ends); only
   * the absences are remembered.
   */
  public static Map<String, Absence> findAll(CommandContext commandContext) {
    AbsenceCache cache = AbsenceCache.of(commandContext);
    if (cache != null) {
      Map<String, Absence> known = cache.getAll();
      if (known != null) {
        return known;
      }
    }
    Map<String, Absence> all = new LinkedHashMap<>();
    allRows(commandContext, false).forEach((userId, rows) -> {
      Absence absence = toAbsence(userId, rows);
      if (absence != null) {
        all.put(userId, absence);
      }
    });
    if (cache != null) {
      cache.putAll(all);
    }
    return all;
  }

  /** Stores the absence, replacing the one of the user. */
  public static void save(CommandContext commandContext, Absence absence) {
    // the rows of an absence have no common key: serialize the writes, or two first writes at
    // the same time would both insert, and a read could mix their values
    lock(commandContext);
    delete(commandContext, absence.getUserId());
    insert(commandContext, absence.getUserId(), KEY_FROM, absence.getFrom().toInstant().toString());
    insert(commandContext, absence.getUserId(), KEY_UNTIL, absence.getUntil().toInstant().toString());
    insert(commandContext, absence.getUserId(), KEY_SUBSTITUTE, absence.getSubstituteId());
    if (absence.getNote() != null && !absence.getNote().isEmpty()) {
      insert(commandContext, absence.getUserId(), KEY_NOTE, absence.getNote());
    }
    remember(commandContext, absence.getUserId(), absence);
  }

  /** Removes the absence of the user; whether there was one. */
  public static boolean delete(CommandContext commandContext, String userId) {
    lock(commandContext);
    List<IdentityInfoEntity> rows = rows(commandContext, userId);
    for (IdentityInfoEntity info : rows) {
      commandContext.getIdentityInfoManager().deleteIdentityInfo(info);
    }
    remember(commandContext, userId, null);
    return !rows.isEmpty();
  }

  /**
   * Removes the absences that have ended at {@code now}, so that the rows the lookups read do not
   * grow with every absence ever set. Called when an absence is written, under the same lock.
   *
   * @return how many absences were removed
   */
  public static int deleteEnded(CommandContext commandContext, Date now) {
    lock(commandContext);
    int removed = 0;
    // read through the entity cache: the rows are deleted below
    for (Map.Entry<String, List<IdentityInfoEntity>> entry : allRows(commandContext, true).entrySet()) {
      Absence absence = toAbsence(entry.getKey(), entry.getValue());
      if (absence != null && absence.getStatus(now) == Absence.Status.ENDED) {
        for (IdentityInfoEntity info : entry.getValue()) {
          commandContext.getIdentityInfoManager().deleteIdentityInfo(info);
        }
        remember(commandContext, entry.getKey(), null);
        removed++;
      }
    }
    return removed;
  }

  /**
   * Holds the exclusive lock of the engine (the row of ACT_GE_PROPERTY that deployments lock) until
   * the end of the transaction. Absences change rarely; this needs no new table. The engine takes no
   * such lock on H2 (see DbSqlSession#lock), which is meant for development; a later write
   * replaces any duplicate rows.
   *
   * <p>What the command remembered about absences before it had the lock may be out of date
   * (another transaction may have written while this one waited), so it is forgotten: checks and
   * writes under the lock read the database again.</p>
   */
  static void lock(CommandContext commandContext) {
    commandContext.getPropertyManager().acquireExclusiveLock();
    AbsenceCache cache = AbsenceCache.of(commandContext);
    if (cache != null) {
      cache.forgetAbsences();
    }
  }

  static void remember(CommandContext commandContext, String userId, Absence absence) {
    AbsenceCache cache = AbsenceCache.of(commandContext);
    if (cache != null) {
      cache.putAbsence(userId, absence);
    }
  }

  static Absence toAbsence(String userId, List<IdentityInfoEntity> rows) {
    Map<String, String> values = new HashMap<>();
    for (IdentityInfoEntity info : rows) {
      values.putIfAbsent(info.getKey(), info.getValue());
    }
    String substitute = values.get(KEY_SUBSTITUTE);
    Date from = parse(values.get(KEY_FROM));
    Date until = parse(values.get(KEY_UNTIL));
    if (substitute == null || from == null || until == null) {
      return null;
    }
    return new Absence(userId, from, until, substitute, values.get(KEY_NOTE));
  }

  /**
   * The rows of the absence of the user as this command sees them: from the database, without the
   * ones deleted and with the ones inserted within the command (not flushed yet).
   */
  @SuppressWarnings("unchecked")
  static List<IdentityInfoEntity> rows(CommandContext commandContext, String userId) {
    List<IdentityInfoEntity> selected =
        commandContext.getDbEntityManager().selectList("selectIdentityInfoByUserId", userId);
    List<IdentityInfoEntity> result = new ArrayList<>();
    for (IdentityInfoEntity info : visible(commandContext, selected)) {
      if (userId.equals(info.getUserId())) {
        result.add(info);
      }
    }
    return result;
  }

  /**
   * The rows of all absences by user, as this command sees them; one statement.
   *
   * @param cached whether the rows go into the entity cache of the command, as rows that are
   *        changed or deleted must; rows that are only read stay out of it
   */
  @SuppressWarnings("unchecked")
  static Map<String, List<IdentityInfoEntity>> allRows(CommandContext commandContext, boolean cached) {
    List<IdentityInfoEntity> selected;
    if (cached) {
      selected = commandContext.getDbEntityManager().selectList("selectFastiIdentityInfosByKeys", new ArrayList<>(KEYS));
    } else {
      selected = (List<IdentityInfoEntity>) commandContext.getDbSqlSession().selectList("selectFastiIdentityInfosByKeys",
          new ListQueryParameterObject(new ArrayList<>(KEYS), 0, Integer.MAX_VALUE));
    }
    Map<String, List<IdentityInfoEntity>> byUser = new LinkedHashMap<>();
    for (IdentityInfoEntity info : visible(commandContext, selected)) {
      byUser.computeIfAbsent(info.getUserId(), userId -> new ArrayList<>()).add(info);
    }
    return byUser;
  }

  /**
   * The selected rows of absences plus the ones inserted, minus the ones deleted within the
   * command. The entity cache knows inserts and deletes by id, so the selected rows need not be
   * cached themselves.
   */
  static List<IdentityInfoEntity> visible(CommandContext commandContext, List<IdentityInfoEntity> selected) {
    DbEntityCache cache = commandContext.getDbEntityManager().getDbEntityCache();
    List<IdentityInfoEntity> all = new ArrayList<>(selected);
    for (IdentityInfoEntity info : cache.getEntitiesByType(IdentityInfoEntity.class)) {
      if (cache.isTransient(info) && !all.contains(info)) {
        all.add(info);
      }
    }
    List<IdentityInfoEntity> result = new ArrayList<>();
    for (IdentityInfoEntity info : all) {
      if (info.getUserId() != null && info.getParentId() == null
          && IdentityInfoEntity.TYPE_USERINFO.equals(info.getType())
          && KEYS.contains(info.getKey()) && !cache.isDeleted(info)) {
        result.add(info);
      }
    }
    return result;
  }

  static void insert(CommandContext commandContext, String userId, String key, String value) {
    IdentityInfoEntity info = new IdentityInfoEntity();
    info.setUserId(userId);
    info.setType(IdentityInfoEntity.TYPE_USERINFO);
    info.setKey(key);
    info.setValue(value);
    commandContext.getDbEntityManager().insert(info);
  }

  static Date parse(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Date.from(Instant.parse(value));
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
