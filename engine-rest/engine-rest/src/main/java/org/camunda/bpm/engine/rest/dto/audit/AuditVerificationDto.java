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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import dev.fasti.engine.audit.AuditVerification;

/** The result of verifying the audit trail ({@code POST /audit/verify}). */
public class AuditVerificationDto {

  protected boolean intact;
  protected Date verifiedAt;
  protected long records;
  protected List<AuditChainDto> chains;
  protected AuditBreakDto firstBreak;

  public static AuditVerificationDto fromVerification(AuditVerification verification) {
    AuditVerificationDto dto = new AuditVerificationDto();
    dto.intact = verification.isIntact();
    dto.verifiedAt = verification.getVerifiedAt();
    dto.records = verification.getRecords();
    dto.chains = new ArrayList<>();
    for (AuditVerification.Chain chain : verification.getChains()) {
      dto.chains.add(AuditChainDto.fromChain(chain));
    }
    dto.firstBreak = AuditBreakDto.fromBreak(verification.getFirstBreak());
    return dto;
  }

  public boolean isIntact() {
    return intact;
  }

  public void setIntact(boolean intact) {
    this.intact = intact;
  }

  public Date getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Date verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  public long getRecords() {
    return records;
  }

  public void setRecords(long records) {
    this.records = records;
  }

  public List<AuditChainDto> getChains() {
    return chains;
  }

  public void setChains(List<AuditChainDto> chains) {
    this.chains = chains;
  }

  public AuditBreakDto getFirstBreak() {
    return firstBreak;
  }

  public void setFirstBreak(AuditBreakDto firstBreak) {
    this.firstBreak = firstBreak;
  }
}
