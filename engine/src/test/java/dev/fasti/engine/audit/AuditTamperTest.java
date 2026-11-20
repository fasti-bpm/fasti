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
package dev.fasti.engine.audit;

import static dev.fasti.engine.audit.AuditTestSupport.sql;
import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.AuditVerification.Break;
import dev.fasti.engine.audit.impl.AuditChain;
import dev.fasti.engine.audit.impl.AuditRecord;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.After;
import org.junit.Test;

/**
 * Someone with access to the database changes, removes or inserts audit records; verifying finds
 * the first break. Each test has an engine of its own with a single chain.
 */
public class AuditTamperTest {

  static final String KEY = "a-key-that-is-long-enough-for-hmac-sha256!";

  ProcessEngine engine;
  AuditService audit;
  static int engines;

  void start(int checkpointInterval, String key) {
    engine = AuditTestSupport.engine("tamper" + (++engines), plugin -> {
      plugin.setChains(1);
      plugin.setCheckpointInterval(checkpointInterval);
      plugin.setHmacKey(key);
      plugin.setRetentionDays(0);
    });
    audit = AuditTrail.of(engine);
    engine.getRepositoryService().createDeployment().addModelInstance("t.bpmn", userTaskProcess("t")).deploy();
    for (int i = 0; i < 4; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "case-" + i);
    }
  }

  @After
  public void closeEngine() {
    ClockUtil.reset();
    if (engine != null) {
      // start the trail afresh, so that cleaning up can write its records
      sql(engine, "delete from FASTI_AUDIT_EVENT");
      sql(engine, "delete from FASTI_AUDIT_HEAD");
    }
    AuditTestSupport.cleanUp(engine, true);
  }

  List<AuditEvent> chain() {
    return audit.createQuery().oldestFirst().list().stream()
        .sorted((a, b) -> Long.compare(a.getSequence(), b.getSequence())).collect(Collectors.toList());
  }

  AuditEvent at(long sequence) {
    return chain().stream().filter(e -> e.getSequence() == sequence).findFirst().orElseThrow();
  }

  Break firstBreak() {
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isFalse();
    return verification.getFirstBreak();
  }

  @Test
  public void shouldVerifyAnUntouchedChain() {
    start(0, null);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.getChains()).hasSize(1);
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getFirstSequence()).isEqualTo(1);
    assertThat(chain.getLastSequence()).isEqualTo(chain.getRecords());
    assertThat(chain.getLastHash()).hasSize(64);
  }

  @Test
  public void shouldFindAChangedRecord() {
    start(0, null);
    AuditEvent victim = at(5);
    sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ? where ID_ = ?", "mallory", victim.getId());
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.HASH_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(5);
    assertThat(found.getRecordId()).isEqualTo(victim.getId());
  }

  @Test
  public void shouldFindAChangedTime() {
    start(0, null);
    AuditEvent victim = at(3);
    sql(engine, "update FASTI_AUDIT_EVENT set TIME_ = TIME_ - 1000 where ID_ = ?", victim.getId());
    assertThat(firstBreak().getReason()).isEqualTo(Break.HASH_MISMATCH);
  }

  @Test
  public void shouldFindARecordWhoseHashWasRecomputed() {
    start(0, null);
    // the intruder changes record 4 and fixes its hash, but cannot fix the link of record 5
    AuditEvent victim = at(4);
    AuditRecord forged = copy(victim);
    forged.setActor("mallory");
    String hash = AuditChain.hash(forged);
    sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ?, HASH_ = ? where ID_ = ?", "mallory", hash, victim.getId());
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.PREVIOUS_HASH_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(5);
  }

  @Test
  public void shouldFindARemovedRecord() {
    start(0, null);
    sql(engine, "delete from FASTI_AUDIT_EVENT where ID_ = ?", at(6).getId());
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.SEQUENCE_GAP);
    assertThat(found.getSequence()).isEqualTo(6);
    assertThat(found.getRecordId()).isNull();
  }

  @Test
  public void shouldFindRemovedFirstRecords() {
    start(0, null);
    sql(engine, "delete from FASTI_AUDIT_EVENT where SEQ_ <= 2");
    assertThat(firstBreak().getReason()).isEqualTo(Break.MISSING_START);
  }

  @Test
  public void shouldFindRemovedLastRecords() {
    start(0, null);
    long last = chain().get(chain().size() - 1).getSequence();
    sql(engine, "delete from FASTI_AUDIT_EVENT where SEQ_ = ?", last);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.HEAD_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(last);
  }

  @Test
  public void shouldFindARemovedHead() {
    start(0, null);
    sql(engine, "delete from FASTI_AUDIT_HEAD");
    assertThat(firstBreak().getReason()).isEqualTo(Break.HEAD_MISSING);
  }

  @Test
  public void shouldFindAnInsertedRecord() {
    start(0, null);
    List<AuditEvent> chain = chain();
    AuditEvent last = chain.get(chain.size() - 1);
    // a well-formed record after the last one, but without moving the head
    AuditRecord forged = copy(last);
    forged.setId("forged-1");
    forged.setSequence(last.getSequence() + 1);
    forged.setPreviousHash(last.getHash());
    forged.setActor("mallory");
    forged.setHash(AuditChain.hash(forged));
    insert(forged);
    assertThat(firstBreak().getReason()).isEqualTo(Break.HEAD_MISMATCH);
  }

  @Test
  public void shouldFindARecordInsertedInTheMiddle() {
    start(0, null);
    // shift record 3 and later one position up and put a forged record at 3
    sql(engine, "update FASTI_AUDIT_EVENT set SEQ_ = SEQ_ + 1000 where SEQ_ >= 3");
    sql(engine, "update FASTI_AUDIT_EVENT set SEQ_ = SEQ_ - 999 where SEQ_ >= 1003");
    AuditEvent second = at(2);
    AuditRecord forged = copy(second);
    forged.setId("forged-2");
    forged.setSequence(3);
    forged.setPreviousHash(second.getHash());
    forged.setHash(AuditChain.hash(forged));
    insert(forged);
    Break found = firstBreak();
    // record 4 (formerly 3) does not carry its position or the hash of the forged record
    assertThat(found.getSequence()).isEqualTo(4);
  }

  @Test
  public void shouldFindARewrittenChainThroughItsSignedCheckpoint() {
    start(5, KEY);
    List<AuditEvent> chain = chain();
    assertThat(chain).anySatisfy(e -> assertThat(e.getAction()).isEqualTo(AuditService.ACTION_CHECKPOINT));
    // the intruder rewrites every record from 2 on, with correct hashes and head, but has no key
    String previous = null;
    for (AuditEvent event : chain) {
      AuditRecord record = copy(event);
      if (event.getSequence() == 2) {
        record.setActor("mallory");
      }
      if (previous != null) {
        record.setPreviousHash(previous);
      }
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?", record.getActor(),
          record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set HASH_ = ?", previous);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(6);
  }

  @Test
  public void shouldNotDetectAConsistentRewriteWithoutKey() {
    // documented limit: without a signing key (and without an anchor kept elsewhere) a complete
    // rewrite of the chain and its head cannot be told apart
    start(5, null);
    String previous = null;
    for (AuditEvent event : chain()) {
      AuditRecord record = copy(event);
      if (event.getSequence() == 2) {
        record.setActor("mallory");
      }
      if (previous != null) {
        record.setPreviousHash(previous);
      }
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?", record.getActor(),
          record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set HASH_ = ?", previous);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.getChains().get(0).getCheckpointsVerified()).isZero();
  }

  @Test
  public void shouldKeepVerifyingAfterAPurgeAndFindAForgedPurge() {
    start(0, null);
    Date cut = new Date(ClockUtil.getCurrentTime().getTime() + 1);
    ClockUtil.setCurrentTime(new Date(cut.getTime() + 60_000));
    engine.getRuntimeService().startProcessInstanceByKey("t", "kept");
    AuditPurgeResult result = audit.purge(cut);
    assertThat(result.getRemoved()).isPositive();
    assertThat(result.getPurgedThrough()).hasSize(1);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.getChains().get(0).getPurgedThrough()).isEqualTo(result.getRemoved());
    assertThat(audit.createQuery().businessKey("kept").count()).isPositive();

    // removing more records at the start than the purge names is found
    long first = verification.getChains().get(0).getFirstSequence();
    sql(engine, "delete from FASTI_AUDIT_EVENT where SEQ_ = ?", first);
    assertThat(firstBreak().getReason()).isEqualTo(Break.MISSING_START);
  }

  @Test
  public void shouldRefusePurgingWithinTheRetentionPeriod() {
    engine = AuditTestSupport.engine("tamper" + (++engines), plugin -> plugin.setRetentionDays(30));
    audit = AuditTrail.of(engine);
    Date recent = new Date(ClockUtil.getCurrentTime().getTime() - 29L * 86_400_000L);
    assertThatThrownBy(() -> audit.purge(recent)).isInstanceOf(BadUserRequestException.class)
        .hasMessageContaining("30 days");
    assertThat(audit.purge(new Date(ClockUtil.getCurrentTime().getTime() - 31L * 86_400_000L)).getRemoved()).isZero();
  }

  void insert(AuditRecord record) {
    sql(engine, "insert into FASTI_AUDIT_EVENT (ID_, CHAIN_ID_, SEQ_, TIME_, TYPE_, ACTION_, ACTOR_, ENGINE_, TENANT_ID_, "
        + "PROC_DEF_KEY_, PROC_DEF_ID_, PROC_INST_ID_, BUSINESS_KEY_, ENTITY_TYPE_, ENTITY_ID_, ENTITY_NAME_, DETAILS_, "
        + "PREV_HASH_, HASH_) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", record.getId(),
        record.getChainId(), record.getSequence(), record.getTime(), record.getType(), record.getAction(), record.getActor(),
        record.getEngineName(), record.getTenantId(), record.getProcessDefinitionKey(), record.getProcessDefinitionId(),
        record.getProcessInstanceId(), record.getBusinessKey(), record.getEntityType(), record.getEntityId(),
        record.getEntityName(), record.getDetails(), record.getPreviousHash(), record.getHash());
  }

  static AuditRecord copy(AuditEvent event) {
    AuditRecord record = new AuditRecord(event.getType(), event.getAction());
    record.setId(event.getId());
    record.setChainId(event.getChainId());
    record.setSequence(event.getSequence());
    record.setTime(event.getTimestamp().getTime());
    record.setActor(event.getActor());
    record.setEngineName(event.getEngineName());
    record.setTenantId(event.getTenantId());
    record.setProcessDefinitionKey(event.getProcessDefinitionKey());
    record.setProcessDefinitionId(event.getProcessDefinitionId());
    record.setProcessInstanceId(event.getProcessInstanceId());
    record.setBusinessKey(event.getBusinessKey());
    record.setEntityType(event.getEntityType());
    record.setEntityId(event.getEntityId());
    record.setEntityName(event.getEntityName());
    record.setDetails(event.getDetails());
    record.setPreviousHash(event.getPreviousHash());
    record.setHash(event.getHash());
    return record;
  }
}
