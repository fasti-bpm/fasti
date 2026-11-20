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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import dev.fasti.engine.absence.Absence;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

/**
 * Finds who takes over the tasks of an absent user: the substitute, or, if that one is absent
 * too, their substitute, and so on, at most {@link AbsenceSettings#getMaxHops()} users far. The
 * first present user wins; a substitute that no longer exists, a way back to a user already
 * passed, or too many absent users in a row give no substitute, and the task stays where it is.
 */
public final class AbsenceResolver {

  private AbsenceResolver() {
  }

  /** The present substitute of the user, if the user is absent at {@code now}; otherwise {@code null}. */
  public static String resolve(CommandContext commandContext, int maxHops, String userId, Date now) {
    Absence absence = AbsenceStore.find(commandContext, userId);
    if (absence == null || !absence.isActiveAt(now)) {
      return null;
    }
    return follow(commandContext, maxHops, userId, absence.getSubstituteId(), now);
  }

  /** Starting with the given substitute of {@code userId}, the first user present at {@code now}, or {@code null}. */
  public static String follow(CommandContext commandContext, int maxHops, String userId, String substituteId, Date now) {
    Set<String> passed = new HashSet<>();
    passed.add(userId);
    String current = substituteId;
    for (int hop = 0; hop < maxHops; hop++) {
      if (current == null || !passed.add(current) || !userExists(commandContext, current)) {
        return null;
      }
      Absence next = AbsenceStore.find(commandContext, current);
      if (next == null || !next.isActiveAt(now)) {
        return current;
      }
      current = next.getSubstituteId();
    }
    return null;
  }

  /** Whether the identity service knows the user, regardless of what the current user may read. */
  public static boolean userExists(CommandContext commandContext, String userId) {
    return Boolean.TRUE.equals(commandContext.runWithoutAuthorization(
        () -> commandContext.getReadOnlyIdentityProvider().findUserById(userId) != null));
  }
}
