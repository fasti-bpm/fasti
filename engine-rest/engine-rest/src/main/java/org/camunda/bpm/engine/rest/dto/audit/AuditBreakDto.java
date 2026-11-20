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
package org.camunda.bpm.engine.rest.dto.audit;

import dev.fasti.engine.audit.AuditVerification;

/** Where and why a chain does not verify. */
public class AuditBreakDto {

  protected String chainId;
  protected long sequence;
  protected String recordId;
  protected String reason;
  protected String message;

  public static AuditBreakDto fromBreak(AuditVerification.Break found) {
    if (found == null) {
      return null;
    }
    AuditBreakDto dto = new AuditBreakDto();
    dto.chainId = found.getChainId();
    dto.sequence = found.getSequence();
    dto.recordId = found.getRecordId();
    dto.reason = found.getReason();
    dto.message = found.getMessage();
    return dto;
  }

  public String getChainId() {
    return chainId;
  }

  public void setChainId(String chainId) {
    this.chainId = chainId;
  }

  public long getSequence() {
    return sequence;
  }

  public void setSequence(long sequence) {
    this.sequence = sequence;
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
