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
import java.util.function.Predicate;

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
    return follow(commandContext, maxHops, userId, substituteId, now, null);
  }

  /**
   * Like {@link #follow(CommandContext, int, String, String, Date)}, but for showing the result to
   * someone who may not see every user on the way: where the walk would have to look at a user
   * that {@code visible} rejects (whether they exist, whether they are absent), it stops and
   * answers the direct substitute, which says nothing about the users behind it.
   *
   * @param visible the users the result may tell something about; {@code null} for all
   */
  public static String follow(CommandContext commandContext, int maxHops, String userId, String substituteId, Date now,
      Predicate<String> visible) {
    Set<String> passed = new HashSet<>();
    passed.add(userId);
    String current = substituteId;
    for (int hop = 0; hop < maxHops; hop++) {
      if (current == null || !passed.add(current)) {
        return null;
      }
      if (visible != null && !visible.test(current)) {
        return substituteId;
      }
      if (!userExists(commandContext, current)) {
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
    AbsenceCache cache = AbsenceCache.of(commandContext);
    if (cache != null) {
      Boolean known = cache.getUserExists(userId);
      if (known != null) {
        return known;
      }
    }
    boolean exists = Boolean.TRUE.equals(commandContext.runWithoutAuthorization(
        () -> commandContext.getReadOnlyIdentityProvider().findUserById(userId) != null));
    if (cache != null) {
      cache.putUserExists(userId, exists);
    }
    return exists;
  }
}
