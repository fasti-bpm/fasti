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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.fasti.engine.absence.Absence;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.Session;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;

/**
 * What a command has already looked up about absences: the absence of a user, whether a user
 * exists, and whether all absences are loaded. A command that asks for the same user again
 * (several tasks, a chain of substitutes) is answered from here. Whether the second user a
 * command asks for makes it read all absences at once depends on the database: without an index
 * on ACT_ID_INFO (USER_ID_, ...) every lookup of one user reads the whole table, so reading all
 * absences costs no more; with the optional index FASTI_IDX_ID_INFO_USER a lookup of one user is
 * an index seek, which reading all absences would turn into a scan (see {@link Factory}).
 * The plugin registers the factory; without it nothing is remembered. Writes through
 * {@link AbsenceStore} keep it up to date; user info or users changed in the same command by
 * other means (the identity service) are not noticed until the command ends.
 */
public class AbsenceCache implements Session {

  protected final Map<String, Optional<Absence>> absences = new HashMap<>();
  protected final Map<String, Boolean> users = new HashMap<>();
  protected boolean complete;
  /** How many users this command looked up one by one. */
  protected int lookups;
  /** Whether reading all absences at once is cheaper than reading the absence of one more user. */
  protected final boolean readAllPreferred;

  public AbsenceCache() {
    this(false);
  }

  public AbsenceCache(boolean readAllPreferred) {
    this.readAllPreferred = readAllPreferred;
  }

  /** The cache of the command, or {@code null} on an engine without the plugin. */
  public static AbsenceCache of(CommandContext commandContext) {
    return commandContext.getSessionFactories().containsKey(AbsenceCache.class)
        ? commandContext.getSession(AbsenceCache.class)
        : null;
  }

  /** The remembered absence of the user: {@code null} if not looked up yet, empty if there is none. */
  public Optional<Absence> getAbsence(String userId) {
    Optional<Absence> absence = absences.get(userId);
    if (absence == null && complete) {
      return Optional.empty();
    }
    return absence;
  }

  /** Counts a lookup of one user that has to read the table; how many there were, this one included. */
  public int countLookup() {
    return ++lookups;
  }

  /**
   * Whether one statement for all absences is the better way to answer the lookups of more than
   * one user: only when the engine knows that ACT_ID_INFO has no index that serves a lookup by
   * user (see {@link Factory#setUserIndexPresent}).
   */
  public boolean isReadAllPreferred() {
    return readAllPreferred;
  }

  public void putAbsence(String userId, Absence absence) {
    absences.put(userId, Optional.ofNullable(absence));
  }

  /** Remembers all absences there are. */
  public void putAll(Map<String, Absence> all) {
    absences.clear();
    all.forEach(this::putAbsence);
    complete = true;
  }

  /** The absences of all users, if they were loaded in this command; otherwise {@code null}. */
  public Map<String, Absence> getAll() {
    if (!complete) {
      return null;
    }
    Map<String, Absence> all = new HashMap<>();
    absences.forEach((userId, absence) -> absence.ifPresent(value -> all.put(userId, value)));
    return all;
  }

  /** Forgets the absences (not the users), so that they are read again. */
  public void forgetAbsences() {
    absences.clear();
    complete = false;
  }

  public Boolean getUserExists(String userId) {
    return users.get(userId);
  }

  public void putUserExists(String userId, boolean exists) {
    users.put(userId, exists);
  }

  @Override
  public void flush() {
    // nothing to write
  }

  @Override
  public void close() {
    absences.clear();
    users.clear();
    complete = false;
    lookups = 0;
  }

  /**
   * Opens an {@link AbsenceCache} per command context. Until the plugin has looked at the indexes
   * of ACT_ID_INFO at engine start, and when it could not tell, absences are read user by user:
   * that never turns an index seek into a scan.
   */
  public static class Factory implements SessionFactory {

    protected volatile Boolean userIndexPresent;

    @Override
    public Class<?> getSessionType() {
      return AbsenceCache.class;
    }

    @Override
    public Session openSession() {
      return new AbsenceCache(Boolean.FALSE.equals(userIndexPresent));
    }

    public Boolean getUserIndexPresent() {
      return userIndexPresent;
    }

    /**
     * @param userIndexPresent whether ACT_ID_INFO has an index that serves the lookup of the user
     *        info of one user ({@code USER_ID_} leading, as FASTI_IDX_ID_INFO_USER); {@code null}
     *        if that is not known
     */
    public void setUserIndexPresent(Boolean userIndexPresent) {
      this.userIndexPresent = userIndexPresent;
    }
  }
}
