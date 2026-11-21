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
 * exists, and whether all absences are loaded. ACT_ID_INFO has no index on the user or the key
 * (and the schema stays that of Camunda 7.24), so every lookup reads the table; a command that
 * asks for the same user again (several tasks, a chain of substitutes) is answered from here,
 * and the second user a command asks for makes it read all absences at once.
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

  /** Opens an {@link AbsenceCache} per command context. */
  public static class Factory implements SessionFactory {

    @Override
    public Class<?> getSessionType() {
      return AbsenceCache.class;
    }

    @Override
    public Session openSession() {
      return new AbsenceCache();
    }
  }
}
