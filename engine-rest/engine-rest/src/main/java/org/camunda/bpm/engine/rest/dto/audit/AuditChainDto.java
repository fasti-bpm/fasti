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

/** The verification of one chain. */
public class AuditChainDto {

  protected String chainId;
  protected boolean intact;
  protected long records;
  protected long firstSequence;
  protected long lastSequence;
  protected String lastHash;
  protected long checkpoints;
  protected long checkpointsVerified;
  protected long purgedThrough;
  protected AuditBreakDto firstBreak;

  public static AuditChainDto fromChain(AuditVerification.Chain chain) {
    AuditChainDto dto = new AuditChainDto();
    dto.chainId = chain.getChainId();
    dto.intact = chain.isIntact();
    dto.records = chain.getRecords();
    dto.firstSequence = chain.getFirstSequence();
    dto.lastSequence = chain.getLastSequence();
    dto.lastHash = chain.getLastHash();
    dto.checkpoints = chain.getCheckpoints();
    dto.checkpointsVerified = chain.getCheckpointsVerified();
    dto.purgedThrough = chain.getPurgedThrough();
    dto.firstBreak = AuditBreakDto.fromBreak(chain.getFirstBreak());
    return dto;
  }

  public String getChainId() {
    return chainId;
  }

  public void setChainId(String chainId) {
    this.chainId = chainId;
  }

  public boolean isIntact() {
    return intact;
  }

  public void setIntact(boolean intact) {
    this.intact = intact;
  }

  public long getRecords() {
    return records;
  }

  public void setRecords(long records) {
    this.records = records;
  }

  public long getFirstSequence() {
    return firstSequence;
  }

  public void setFirstSequence(long firstSequence) {
    this.firstSequence = firstSequence;
  }

  public long getLastSequence() {
    return lastSequence;
  }

  public void setLastSequence(long lastSequence) {
    this.lastSequence = lastSequence;
  }

  public String getLastHash() {
    return lastHash;
  }

  public void setLastHash(String lastHash) {
    this.lastHash = lastHash;
  }

  public long getCheckpoints() {
    return checkpoints;
  }

  public void setCheckpoints(long checkpoints) {
    this.checkpoints = checkpoints;
  }

  public long getCheckpointsVerified() {
    return checkpointsVerified;
  }

  public void setCheckpointsVerified(long checkpointsVerified) {
    this.checkpointsVerified = checkpointsVerified;
  }

  public long getPurgedThrough() {
    return purgedThrough;
  }

  public void setPurgedThrough(long purgedThrough) {
    this.purgedThrough = purgedThrough;
  }

  public AuditBreakDto getFirstBreak() {
    return firstBreak;
  }

  public void setFirstBreak(AuditBreakDto firstBreak) {
    this.firstBreak = firstBreak;
  }
}
