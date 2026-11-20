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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fasti.engine.absence.Absence;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCache;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;

/**
 * Reads and writes absences as user info ({@code ACT_ID_INFO}, type {@code userinfo}) within a
 * command. Times are stored as ISO-8601 instants in UTC. Writing replaces all rows of an absence,
 * which also removes duplicates left by concurrent first writes.
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
    Map<String, String> values = new HashMap<>();
    for (IdentityInfoEntity info : rows(commandContext, userId)) {
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
  }

  /** Removes the absence of the user; whether there was one. */
  public static boolean delete(CommandContext commandContext, String userId) {
    lock(commandContext);
    List<IdentityInfoEntity> rows = rows(commandContext, userId);
    for (IdentityInfoEntity info : rows) {
      commandContext.getIdentityInfoManager().deleteIdentityInfo(info);
    }
    return !rows.isEmpty();
  }

  /**
   * Holds the exclusive lock of the engine (the row of ACT_GE_PROPERTY that deployments lock) until
   * the end of the transaction. Absences change rarely; this needs no new table.
   */
  static void lock(CommandContext commandContext) {
    commandContext.getPropertyManager().acquireExclusiveLock();
  }

  /** Users who have an absence set (of any time); some may have been removed within this command. */
  @SuppressWarnings("unchecked")
  public static Set<String> findUserIdsWithAbsence(CommandContext commandContext) {
    Set<String> ids = new LinkedHashSet<>(
        commandContext.getDbEntityManager().selectList("selectFastiUserIdsByIdentityInfoKey", KEY_SUBSTITUTE));
    // absences stored within this command are not in the database yet
    DbEntityCache cache = commandContext.getDbEntityManager().getDbEntityCache();
    for (IdentityInfoEntity info : cache.getEntitiesByType(IdentityInfoEntity.class)) {
      if (cache.isTransient(info) && KEY_SUBSTITUTE.equals(info.getKey())) {
        ids.add(info.getUserId());
      }
    }
    return ids;
  }

  /**
   * The rows of the absence of the user as this command sees them: from the database, without the
   * ones deleted and with the ones inserted within the command (not flushed yet).
   */
  @SuppressWarnings("unchecked")
  static List<IdentityInfoEntity> rows(CommandContext commandContext, String userId) {
    DbEntityCache cache = commandContext.getDbEntityManager().getDbEntityCache();
    List<IdentityInfoEntity> all = new ArrayList<>(
        commandContext.getDbEntityManager().selectList("selectIdentityInfoByUserId", userId));
    for (IdentityInfoEntity info : cache.getEntitiesByType(IdentityInfoEntity.class)) {
      if (cache.isTransient(info) && userId.equals(info.getUserId()) && !all.contains(info)) {
        all.add(info);
      }
    }
    List<IdentityInfoEntity> result = new ArrayList<>();
    for (IdentityInfoEntity info : all) {
      if (info.getParentId() == null && IdentityInfoEntity.TYPE_USERINFO.equals(info.getType())
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
