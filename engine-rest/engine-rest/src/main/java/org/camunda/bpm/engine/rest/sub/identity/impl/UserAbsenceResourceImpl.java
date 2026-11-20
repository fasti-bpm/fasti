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
package org.camunda.bpm.engine.rest.sub.identity.impl;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response.Status;

import dev.fasti.engine.absence.Absence;
import dev.fasti.engine.absence.AbsenceService;
import dev.fasti.engine.absence.Absences;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.rest.dto.identity.AbsenceDto;
import org.camunda.bpm.engine.rest.dto.identity.AbsenceHandoverDto;
import org.camunda.bpm.engine.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.rest.sub.identity.UserAbsenceResource;

public class UserAbsenceResourceImpl implements UserAbsenceResource {

  static final int DEFAULT_MAX_TASKS = 500;
  static final int LIMIT_MAX_TASKS = 1000;

  protected final AbsenceService absences;
  protected final String userId;

  public UserAbsenceResourceImpl(ProcessEngine processEngine, String userId) {
    this(Absences.of(processEngine), userId);
  }

  public UserAbsenceResourceImpl(AbsenceService absences, String userId) {
    this.absences = absences;
    this.userId = userId;
  }

  @Override
  public AbsenceDto getAbsence() {
    Date now = ClockUtil.getCurrentTime();
    Absence absence = absences.getAbsence(userId);
    AbsenceDto dto = AbsenceDto.fromAbsence(userId, absence, now);
    dto.setEnabled(absences.isEnabled());
    dto.setUnavailableReason(absences.getUnavailableReason());
    if (absence != null && absence.isActiveAt(now)) {
      dto.setEffectiveSubstituteId(absences.resolveSubstitute(userId));
    }
    return dto;
  }

  @Override
  public AbsenceDto setAbsence(AbsenceDto absence) {
    if (absence == null) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "An absence (until, substituteId) is required.");
    }
    Absence stored = absences.setAbsence(userId, absence.getFrom(), absence.getUntil(), absence.getSubstituteId(),
        absence.getNote());
    // answered from what was stored: changing an absence needs UPDATE, reading it again READ
    Date now = ClockUtil.getCurrentTime();
    AbsenceDto dto = AbsenceDto.fromAbsence(userId, stored, now);
    dto.setEnabled(true);
    if (stored.isActiveAt(now)) {
      try {
        dto.setEffectiveSubstituteId(absences.resolveSubstitute(userId));
      } catch (AuthorizationException e) {
        dto.setEffectiveSubstituteId(null);
      }
    }
    return dto;
  }

  @Override
  public void clearAbsence() {
    absences.clearAbsence(userId);
  }

  @Override
  public AbsenceHandoverDto handOverTasks(Integer maxTasks) {
    int limit = maxTasks == null ? DEFAULT_MAX_TASKS : maxTasks;
    if (limit < 1 || limit > LIMIT_MAX_TASKS) {
      throw new InvalidRequestException(Status.BAD_REQUEST, "maxTasks must be between 1 and " + LIMIT_MAX_TASKS + ".");
    }
    return AbsenceHandoverDto.fromHandover(absences.handOverTasks(userId, limit));
  }

  @Override
  public List<AbsenceDto> getSubstitutedAbsences() {
    Date now = ClockUtil.getCurrentTime();
    return absences.getAbsencesSubstitutedBy(userId).stream()
        .map(absence -> {
          AbsenceDto dto = AbsenceDto.fromAbsence(absence.getUserId(), absence, now);
          dto.setEnabled(true);
          dto.setEffectiveSubstituteId(userId);
          return dto;
        })
        .collect(Collectors.toList());
  }
}
