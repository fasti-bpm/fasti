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
package dev.fasti.engine.absence;

import java.util.Date;
import java.util.List;

/**
 * Absences of users and their substitutes. Obtain it with {@link Absences#of}.
 *
 * <p>Authorization: a user manages their own absence; the absence of another user needs the
 * permission {@code UPDATE} (to change) or {@code READ} (to see) on that user (resource
 * {@code USER}), which members of {@code camunda-admin} have. Without an authenticated user, or
 * with authorization turned off, there are no checks, as everywhere in the engine.</p>
 */
public interface AbsenceService {

  /** Task variable (local): the absent user a task was assigned to before it went to the substitute. */
  String VARIABLE_ABSENT_ASSIGNEE = "fasti_absentAssignee";

  /** Task variable (local): the substitute who got the task; both variables go once the task moves on. */
  String VARIABLE_SUBSTITUTE = "fasti_absenceSubstitute";

  /**
   * Whether absences can be set on this engine: the plugin {@link AbsenceSubstitutionPlugin} is
   * registered and the user info of the identity service can be stored.
   */
  boolean isEnabled();

  /** Why absences cannot be set ({@code null} when {@link #isEnabled()}). */
  String getUnavailableReason();

  /**
   * The absence of the user, also an upcoming one, or an ended one until it is removed (the next
   * time any absence is set or cleared); {@code null} if none is set.
   */
  Absence getAbsence(String userId);

  /**
   * Sets (replaces) the absence of the user.
   *
   * @param from start, {@code null} for now
   * @param until end, after {@code from} and in the future
   * @param substituteId an existing user other than {@code userId}, whom the caller may see
   *        ({@code READ} on the user); the substitutes of absent substitutes are followed, but
   *        never back to {@code userId}
   * @param note optional, at most 255 characters
   * @throws org.camunda.bpm.engine.BadUserRequestException if a value is invalid or absences are not enabled
   */
  Absence setAbsence(String userId, Date from, Date until, String substituteId, String note);

  /** Removes the absence of the user, if any. */
  void clearAbsence(String userId);

  /**
   * The absences that are active now and whose tasks go to the given user, directly or through
   * absent substitutes.
   */
  List<Absence> getAbsencesSubstitutedBy(String userId);

  /**
   * The present user who takes over the tasks of the given user now, or {@code null} if the user
   * is not absent or no present substitute can be found. Absent substitutes are followed as far
   * as the caller may see them ({@code READ} on each user); otherwise the answer is the substitute
   * of the absence itself.
   */
  String resolveSubstitute(String userId);

  /**
   * Hands the open (active) tasks assigned to the user over to the substitute of the absence of the
   * user, which may also be upcoming. At most {@code maxTasks} tasks are handed over per call.
   * Besides the permission to manage the absence, the caller needs the permission to assign each
   * task, as with the task API ({@code TASK_ASSIGN} or {@code UPDATE}); tasks without it stay
   * with the user and are counted in {@link Handover#getNotAllowed()}. Administrators may hand
   * over every task.
   */
  Handover handOverTasks(String userId, int maxTasks);

  /** Result of {@link #handOverTasks}. */
  final class Handover {
    private final String substituteId;
    private final int handedOver;
    private final long remaining;
    private final int notAllowed;

    public Handover(String substituteId, int handedOver, long remaining) {
      this(substituteId, handedOver, remaining, 0);
    }

    public Handover(String substituteId, int handedOver, long remaining, int notAllowed) {
      this.substituteId = substituteId;
      this.handedOver = handedOver;
      this.remaining = remaining;
      this.notAllowed = notAllowed;
    }

    /** Who got the tasks. */
    public String getSubstituteId() {
      return substituteId;
    }

    /** How many tasks were handed over. */
    public int getHandedOver() {
      return handedOver;
    }

    /**
     * How many tasks are still assigned to the user (suspended ones, ones beyond the limit, ones
     * the caller may not assign).
     */
    public long getRemaining() {
      return remaining;
    }

    /** How many of the tasks this call looked at stayed because the caller may not assign them. */
    public int getNotAllowed() {
      return notAllowed;
    }
  }
}
