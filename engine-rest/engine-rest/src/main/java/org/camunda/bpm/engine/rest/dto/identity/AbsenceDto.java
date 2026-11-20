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
package org.camunda.bpm.engine.rest.dto.identity;

import java.util.Date;
import java.util.Locale;

import dev.fasti.engine.absence.Absence;

/**
 * The absence of a user. As request body, only {@code from}, {@code until}, {@code substituteId}
 * and {@code note} are used.
 */
public class AbsenceDto {

  /** No absence is set. */
  public static final String STATUS_NONE = "none";

  protected String userId;
  protected boolean enabled;
  protected String unavailableReason;
  protected String status = STATUS_NONE;
  protected Date from;
  protected Date until;
  protected String substituteId;
  protected String note;
  protected String effectiveSubstituteId;

  public static AbsenceDto fromAbsence(String userId, Absence absence, Date now) {
    AbsenceDto dto = new AbsenceDto();
    dto.userId = userId;
    if (absence != null) {
      dto.status = absence.getStatus(now).name().toLowerCase(Locale.ROOT);
      dto.from = absence.getFrom();
      dto.until = absence.getUntil();
      dto.substituteId = absence.getSubstituteId();
      dto.note = absence.getNote();
    }
    return dto;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  /** Whether absences can be set on the engine. */
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getUnavailableReason() {
    return unavailableReason;
  }

  public void setUnavailableReason(String unavailableReason) {
    this.unavailableReason = unavailableReason;
  }

  /** {@code none}, {@code upcoming}, {@code active} or {@code ended}. */
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Date getFrom() {
    return from;
  }

  public void setFrom(Date from) {
    this.from = from;
  }

  public Date getUntil() {
    return until;
  }

  public void setUntil(Date until) {
    this.until = until;
  }

  public String getSubstituteId() {
    return substituteId;
  }

  public void setSubstituteId(String substituteId) {
    this.substituteId = substituteId;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  /** Who gets the tasks now (the substitute, or theirs if they are absent too); only while active. */
  public String getEffectiveSubstituteId() {
    return effectiveSubstituteId;
  }

  public void setEffectiveSubstituteId(String effectiveSubstituteId) {
    this.effectiveSubstituteId = effectiveSubstituteId;
  }
}
