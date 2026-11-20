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

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * The absence of a user: from when until when, and who takes over the tasks assigned to the user
 * in that time. Stored as user info of the identity service, so the database schema stays that
 * of Camunda 7.24.
 */
public final class Absence implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Where an absence stands relative to a point in time. */
  public enum Status {
    /** It starts later. */
    UPCOMING,
    /** It has started and not ended. */
    ACTIVE,
    /** It has ended. */
    ENDED
  }

  private final String userId;
  private final Date from;
  private final Date until;
  private final String substituteId;
  private final String note;

  public Absence(String userId, Date from, Date until, String substituteId, String note) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.from = copy(Objects.requireNonNull(from, "from"));
    this.until = copy(Objects.requireNonNull(until, "until"));
    this.substituteId = Objects.requireNonNull(substituteId, "substituteId");
    this.note = note;
  }

  /** The absent user. */
  public String getUserId() {
    return userId;
  }

  /** Start of the absence (inclusive). */
  public Date getFrom() {
    return copy(from);
  }

  /** End of the absence (exclusive). */
  public Date getUntil() {
    return copy(until);
  }

  /** The user who takes over. */
  public String getSubstituteId() {
    return substituteId;
  }

  /** An optional note for the substitute, for example where handover notes are. */
  public String getNote() {
    return note;
  }

  /** Whether the absence has started and not ended at the given time. */
  public boolean isActiveAt(Date time) {
    return getStatus(time) == Status.ACTIVE;
  }

  public Status getStatus(Date time) {
    if (time.before(from)) {
      return Status.UPCOMING;
    }
    return time.before(until) ? Status.ACTIVE : Status.ENDED;
  }

  /** Whether the two absences share some time. */
  public boolean overlaps(Date otherFrom, Date otherUntil) {
    return from.before(otherUntil) && otherFrom.before(until);
  }

  private static Date copy(Date date) {
    return date == null ? null : new Date(date.getTime());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Absence)) {
      return false;
    }
    Absence other = (Absence) o;
    return userId.equals(other.userId) && from.equals(other.from) && until.equals(other.until)
        && substituteId.equals(other.substituteId) && Objects.equals(note, other.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, from, until, substituteId, note);
  }

  @Override
  public String toString() {
    return "Absence[userId=" + userId + ", from=" + from.toInstant() + ", until=" + until.toInstant()
        + ", substituteId=" + substituteId + "]";
  }
}
