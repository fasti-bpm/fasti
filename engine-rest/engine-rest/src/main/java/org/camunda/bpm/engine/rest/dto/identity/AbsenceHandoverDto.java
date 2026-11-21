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

import dev.fasti.engine.absence.AbsenceService.Handover;

/** The result of handing over the open tasks of an absent user. */
public class AbsenceHandoverDto {

  protected String substituteId;
  protected int handedOver;
  protected long remaining;
  protected int notAllowed;

  public static AbsenceHandoverDto fromHandover(Handover handover) {
    AbsenceHandoverDto dto = new AbsenceHandoverDto();
    dto.substituteId = handover.getSubstituteId();
    dto.handedOver = handover.getHandedOver();
    dto.remaining = handover.getRemaining();
    dto.notAllowed = handover.getNotAllowed();
    return dto;
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
   * How many tasks are still assigned to the user (suspended ones, ones beyond the limit, ones the
   * caller may not assign).
   */
  public long getRemaining() {
    return remaining;
  }

  /** How many of the tasks looked at stayed because the caller may not assign them. */
  public int getNotAllowed() {
    return notAllowed;
  }
}
