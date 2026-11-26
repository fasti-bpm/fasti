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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.AuditVerification.Break;
import dev.fasti.engine.audit.impl.AuditChain;
import dev.fasti.engine.audit.impl.AuditRecord;
import dev.fasti.engine.audit.impl.AuditSettings;
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
  static final String OTHER_KEY = "another-key-that-is-long-enough-for-hmac!";

  ProcessEngine engine;
  AuditService audit;
  static int engines;

  void start(int checkpointInterval, String key) {
    engines++;
    build(checkpointInterval, key, plugin -> { });
    engine.getRepositoryService().createDeployment().addModelInstance("t.bpmn", userTaskProcess("t")).deploy();
    for (int i = 0; i < 4; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "case-" + i);
    }
  }

  void build(int checkpointInterval, String key, Consumer<AuditTrailPlugin> more) {
    engine = AuditTestSupport.engine("tamper" + engines, plugin -> {
      plugin.setChains(1);
      plugin.setCheckpointInterval(checkpointInterval);
      plugin.setHmacKey(key);
      plugin.setRetentionDays(0);
      more.accept(plugin);
    });
    audit = AuditTrail.of(engine);
  }

  /** The node comes back with other settings, on the trail it wrote so far. */
  void restart(int checkpointInterval, String key, Consumer<AuditTrailPlugin> more) {
    engine.close();
    build(checkpointInterval, key, more);
  }

  /** Purges everything before now and continues a minute later; what the purge removed. */
  AuditPurgeResult purgeNow() {
    Date cut = new Date(ClockUtil.getCurrentTime().getTime() + 1);
    ClockUtil.setCurrentTime(new Date(cut.getTime() + 60_000));
    engine.getRuntimeService().startProcessInstanceByKey("t", "kept");
    return audit.purge(cut);
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
  public void shouldFindACheckpointWhoseSignatureWasRemoved() {
    start(5, KEY);
    AuditEvent checkpoint = chain().stream().filter(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction())).findFirst()
        .orElseThrow();
    // the intruder strips the signature and fixes the hash of the checkpoint and of every later record
    AuditRecord stripped = copy(checkpoint);
    stripped.setDetails(checkpoint.getDetails().replaceAll(",?\"hmac\":\"[0-9a-f]+\"", ""));
    rewriteFrom(stripped);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(checkpoint.getSequence());
  }

  @Test
  public void shouldFindACheckpointRelabelledWithAnotherKey() {
    start(5, KEY);
    // the last checkpoint: a rewrite before a later one would break that one's signature
    AuditEvent checkpoint = lastCheckpoint();
    AuditRecord other = copy(checkpoint);
    other.setDetails(checkpoint.getDetails().replaceAll("\"keyId\":\"[0-9a-f]+\"", "\"keyId\":\"000000000000\""));
    rewriteFrom(other);
    // the hashes are consistent, but the checkpoint is not signed with a key of the engine
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_UNKNOWN_KEY);
    assertThat(found.getSequence()).isEqualTo(checkpoint.getSequence());
  }

  @Test
  public void shouldFindACheckpointWhoseSignatureAndKeyWereRemoved() {
    start(5, KEY);
    AuditEvent checkpoint = lastCheckpoint();
    AuditRecord stripped = copy(checkpoint);
    stripped.setDetails(checkpoint.getDetails().replaceAll(",?\"hmac\":\"[0-9a-f]+\"", "")
        .replaceAll(",?\"keyId\":\"[0-9a-f]+\"", ""));
    rewriteFrom(stripped);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_UNSIGNED);
    assertThat(found.getSequence()).isEqualTo(checkpoint.getSequence());
  }

  @Test
  public void shouldNotAcceptARetiredKeyAfterACheckpointOfTheCurrentKey() {
    start(5, KEY);
    for (int i = 0; i < 6; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "more-" + i);
    }
    restart(5, KEY, plugin -> plugin.setRetiredKeyIds("000000000000, unsigned"));
    assertThat(audit.verify().isAuthenticated()).isTrue();
    // relabelling the last checkpoint with a retired key id does not help: keys follow each other
    AuditEvent checkpoint = lastCheckpoint();
    AuditRecord other = copy(checkpoint);
    other.setDetails(checkpoint.getDetails().replaceAll("\"keyId\":\"[0-9a-f]+\"", "\"keyId\":\"000000000000\""));
    rewriteFrom(other);
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_UNKNOWN_KEY);
  }

  @Test
  public void shouldFindARemovedCheckpoint() {
    start(5, KEY);
    // the intruder removes the last checkpoint, moves the later records up and fixes every hash
    while (AuditService.ACTION_CHECKPOINT.equals(chain().get(chain().size() - 1).getAction())) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "after-the-checkpoint");
    }
    AuditEvent checkpoint = lastCheckpoint();
    removeAndRewrite(checkpoint);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISSING);
    assertThat(found.getRecordId()).isNull();
  }

  @Test
  public void shouldFindAChainWhoseCheckpointsWereAllTurnedIntoOtherRecords() {
    start(5, KEY);
    // rewrite of the whole chain: record 2 changed, no checkpoint left to contradict it
    String previous = null;
    for (AuditEvent event : chain()) {
      AuditRecord record = copy(event);
      if (event.getSequence() == 2) {
        record.setActor("mallory");
      }
      if (AuditService.ACTION_CHECKPOINT.equals(event.getAction())) {
        record.setAction(AuditService.ACTION_EXPORT);
      }
      if (previous != null) {
        record.setPreviousHash(previous);
      }
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ?, ACTION_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?",
          record.getActor(), record.getAction(), record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set HASH_ = ?", previous);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISSING);
    assertThat(found.getSequence()).isEqualTo(6);

    // the engine goes on and signs new checkpoints over the rewritten chain: the missing ones are still missed
    more(6);
    assertThat(chain()).anySatisfy(e -> assertThat(e.getAction()).isEqualTo(AuditService.ACTION_CHECKPOINT));
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_MISSING);
  }

  @Test
  public void shouldFindCheckpointsRemovedBeforeNewOnesWereWritten() {
    start(5, KEY);
    more(6);
    // the intruder changes record 2, turns every checkpoint after the first into another record and fixes the hashes
    AuditEvent kept = chain().stream().filter(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction())).findFirst()
        .orElseThrow();
    String previous = null;
    for (AuditEvent event : chain()) {
      if (event.getSequence() <= kept.getSequence()) {
        previous = event.getHash();
        continue;
      }
      AuditRecord record = copy(event);
      if (event.getSequence() == kept.getSequence() + 1) {
        record.setActor("mallory");
      }
      if (AuditService.ACTION_CHECKPOINT.equals(event.getAction())) {
        record.setAction(AuditService.ACTION_EXPORT);
      }
      record.setPreviousHash(previous);
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set ACTOR_ = ?, ACTION_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?",
          record.getActor(), record.getAction(), record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set HASH_ = ?", previous);
    // and waits until the engine has written a valid checkpoint again
    more(6);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISSING);
    assertThat(found.getSequence()).isGreaterThan(kept.getSequence());
  }

  @Test
  public void shouldKeepVerifyingWhenTheIntervalChanges() {
    start(5, KEY);
    restart(3, KEY, plugin -> { });
    more(4);
    assertThat(audit.verify().isAuthenticated()).isTrue();
    restart(20, KEY, plugin -> { });
    more(4);
    assertThat(audit.verify().isAuthenticated()).isTrue();
    more(12);
    assertThat(audit.verify().isAuthenticated()).isTrue();
    restart(4, KEY, plugin -> { });
    more(3);
    AuditVerification verification = audit.verify();
    assertThat(verification.getFirstBreak()).isNull();
    assertThat(verification.isAuthenticated()).isTrue();
    assertThat(chain().stream().filter(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction()))
        .map(e -> e.getDetails().replaceAll(".*\"interval\":(\\d+).*", "$1")).distinct()).contains("5", "3", "20", "4");
  }

  @Test
  public void shouldNotAuthenticateAChainWithoutCheckpointsWhereTheyAreDue() {
    // signed, but checkpoints were off: intact, but no signature covers the records
    start(0, KEY);
    AuditVerification unsigned = audit.verify();
    assertThat(unsigned.isIntact()).isTrue();
    assertThat(unsigned.isAuthenticated()).isFalse();
    restart(5, KEY, plugin -> { });
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_MISSING);

    // accepted as the part before signing started: intact, but not authenticated, also once checkpoints follow
    restart(5, KEY, plugin -> plugin.setRetiredKeyIds("unsigned"));
    AuditVerification verification = audit.verify();
    assertThat(verification.getFirstBreak()).isNull();
    assertThat(verification.isAuthenticated()).isFalse();
    more(6);
    verification = audit.verify();
    assertThat(verification.getFirstBreak()).isNull();
    assertThat(verification.getChains().get(0).getCheckpointsVerified()).isPositive();
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(verification.getChains().get(0).getAuthenticatedThrough()).isZero();
  }

  @Test
  public void shouldAuthenticateAnUntouchedSignedChain() {
    start(5, KEY);
    long lastCheckpoint = lastCheckpoint().getSequence();
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isTrue();
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.isAuthenticated()).isTrue();
    assertThat(chain.getCheckpoints()).isPositive().isEqualTo(chain.getCheckpointsVerified());
    // the signatures reach to the record before the last checkpoint; what follows has the hashes only
    assertThat(chain.getAuthenticatedThrough()).isEqualTo(lastCheckpoint - 1).isLessThan(chain.getLastSequence());
    // every verification adds a record: checkpoints keep their distance
    for (int i = 0; i < 12; i++) {
      assertThat(audit.verify().isAuthenticated()).isTrue();
    }
  }

  @Test
  public void shouldSayThatNoSignatureCoversAChainBeforeItsFirstCheckpoint() {
    start(500, KEY);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    // nothing is missing, but nothing is signed yet either: not authenticated
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(verification.getChains().get(0).getCheckpoints()).isZero();
    assertThat(verification.getChains().get(0).getAuthenticatedThrough()).isZero();
  }

  @Test
  public void shouldNotCallAnUnsignedChainAuthenticated() {
    start(5, null);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(verification.getChains().get(0).getCheckpointsVerified()).isZero();
  }

  @Test
  public void shouldRefuseUnsignedCheckpointsOnceAKeyIsSetUnlessListed() {
    start(5, null);
    // the key is turned on later: the checkpoints so far carry no signature
    restart(5, KEY, plugin -> { });
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_UNSIGNED);

    restart(5, KEY, plugin -> plugin.setRetiredKeyIds(AuditSettings.UNSIGNED_KEY_ID));
    assertThat(audit.getStatus().getRetiredKeyIds()).containsExactly(AuditSettings.UNSIGNED_KEY_ID);
    for (int i = 0; i < 6; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "signed-" + i);
    }
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    // accepted, not checked
    assertThat(verification.isAuthenticated()).isFalse();
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getCheckpointsVerified()).isPositive().isLessThan(chain.getCheckpoints());

    // after the first signed checkpoint, a checkpoint without signature is a break again
    AuditEvent checkpoint = lastCheckpoint();
    AuditRecord stripped = copy(checkpoint);
    stripped.setDetails(checkpoint.getDetails().replaceAll(",?\"hmac\":\"[0-9a-f]+\"", "")
        .replaceAll(",?\"keyId\":\"[0-9a-f]+\"", ""));
    rewriteFrom(stripped);
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_UNSIGNED);
  }

  @Test
  public void shouldCheckCheckpointsOfARetiredKeyTheEngineHolds() {
    start(5, OTHER_KEY);
    String retiredId = audit.getStatus().getKeyId();

    // the key is replaced and the old one forgotten
    restart(5, KEY, plugin -> { });
    assertThat(firstBreak().getReason()).isEqualTo(Break.CHECKPOINT_UNKNOWN_KEY);

    // its id is listed: accepted, but nothing proves the old part
    restart(5, KEY, plugin -> plugin.setRetiredKeyIds(retiredId));
    AuditVerification listed = audit.verify();
    assertThat(listed.isIntact()).isTrue();
    assertThat(listed.isAuthenticated()).isFalse();

    // the old key is still at hand: its checkpoints are checked
    restart(5, KEY, plugin -> plugin.setRetiredHmacKeys(OTHER_KEY));
    assertThat(audit.getStatus().getRetiredKeyIds()).containsExactly(retiredId);
    for (int i = 0; i < 6; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "rotated-" + i);
    }
    AuditVerification held = audit.verify();
    assertThat(held.isIntact()).isTrue();
    assertThat(held.isAuthenticated()).isTrue();

    // and a rewrite before one of them is found
    AuditEvent old = chain().stream().filter(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction())).findFirst()
        .orElseThrow();
    assertThat(old.getDetails()).contains(retiredId);
    AuditRecord changed = copy(at(2));
    changed.setDetails("{\"forged\":true}");
    rewriteFrom(changed);
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.CHECKPOINT_MISMATCH);
    assertThat(found.getSequence()).isEqualTo(old.getSequence());
  }

  void more(int instances) {
    for (int i = 0; i < instances; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("t", "more-" + i);
    }
  }

  AuditEvent lastCheckpoint() {
    return chain().stream().filter(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction()))
        .reduce((first, second) -> second).orElseThrow();
  }

  /** Removes the record, moves every later record one position down and fixes their hashes and the head. */
  void removeAndRewrite(AuditEvent removed) {
    List<AuditEvent> chain = chain();
    sql(engine, "delete from FASTI_AUDIT_EVENT where ID_ = ?", removed.getId());
    String previous = removed.getPreviousHash();
    long last = removed.getSequence() - 1;
    for (AuditEvent event : chain) {
      if (event.getSequence() <= removed.getSequence()) {
        continue;
      }
      AuditRecord record = copy(event);
      record.setSequence(event.getSequence() - 1);
      record.setPreviousHash(previous);
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set SEQ_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?", record.getSequence(),
          record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
      last = record.getSequence();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set SEQ_ = ?, HASH_ = ?", last, previous);
  }

  /** Writes the record as given and fixes the hashes of it and of every record after it, and the head. */
  void rewriteFrom(AuditRecord changed) {
    String previous = null;
    for (AuditEvent event : chain()) {
      if (event.getSequence() < changed.getSequence()) {
        continue;
      }
      AuditRecord record = event.getSequence() == changed.getSequence() ? changed : copy(event);
      if (previous != null) {
        record.setPreviousHash(previous);
      }
      record.setHash(AuditChain.hash(record));
      sql(engine, "update FASTI_AUDIT_EVENT set DETAILS_ = ?, PREV_HASH_ = ?, HASH_ = ? where ID_ = ?", record.getDetails(),
          record.getPreviousHash(), record.getHash(), record.getId());
      previous = record.getHash();
    }
    sql(engine, "update FASTI_AUDIT_HEAD set HASH_ = ?", previous);
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
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(verification.getChains().get(0).getCheckpointsVerified()).isZero();
  }

  @Test
  public void shouldKeepVerifyingAfterAPurgeAndFindMoreRemovedRecords() {
    start(0, null);
    AuditPurgeResult result = purgeNow();
    assertThat(result.getRemoved()).isPositive();
    assertThat(result.getPurgedThrough()).hasSize(1);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.getChains().get(0).getPurgedThrough()).isEqualTo(result.getRemoved());
    // without a key nothing proves that the purge record was written by the engine
    assertThat(verification.getChains().get(0).isPurgeAttested()).isFalse();
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(audit.createQuery().businessKey("kept").count()).isPositive();

    // removing more records at the start than the purge names is found
    long first = verification.getChains().get(0).getFirstSequence();
    sql(engine, "delete from FASTI_AUDIT_EVENT where SEQ_ = ?", first);
    assertThat(firstBreak().getReason()).isEqualTo(Break.MISSING_START);
  }

  @Test
  public void shouldAttestAPurgeWithTheKey() {
    start(5, KEY);
    AuditPurgeResult result = purgeNow();
    assertThat(result.getRemoved()).isPositive();
    AuditEvent purge = audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_PURGE).list().get(0);
    assertThat(purge.getDetails()).contains("\"hmac\":\"", "\"keyId\":\"" + audit.getStatus().getKeyId() + "\"",
        "\"purgedThroughSeq\":" + result.getRemoved());
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isTrue();
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getPurgedThrough()).isEqualTo(result.getRemoved());
    assertThat(chain.isPurgeAttested()).isTrue();
  }

  /**
   * What someone with access to the database, but without the key, does to hide the first records:
   * removes them and appends a purge record that names them, with a correct hash and head.
   */
  void forgePurge(long through, String signatureOf) {
    List<AuditEvent> chain = chain();
    AuditEvent firstKept = chain.stream().filter(e -> e.getSequence() == through + 1).findFirst().orElseThrow();
    AuditEvent last = chain.get(chain.size() - 1);
    sql(engine, "delete from FASTI_AUDIT_EVENT where SEQ_ <= ?", through);
    AuditRecord forged = new AuditRecord(AuditService.TYPE_AUDIT, AuditService.ACTION_PURGE);
    forged.setId("forged-purge");
    forged.setChainId(last.getChainId());
    forged.setSequence(last.getSequence() + 1);
    forged.setTime(last.getTimestamp().getTime());
    forged.setEngineName(last.getEngineName());
    forged.setEntityType("chain");
    forged.setEntityName(last.getChainId());
    forged.setDetails("{" + (signatureOf == null ? "" : signatureOf + ",") + "\"purgedThroughHash\":\""
        + firstKept.getPreviousHash() + "\",\"purgedThroughSeq\":" + through + "}");
    forged.setPreviousHash(last.getHash());
    forged.setHash(AuditChain.hash(forged));
    insert(forged);
    sql(engine, "update FASTI_AUDIT_HEAD set SEQ_ = ?, HASH_ = ?", forged.getSequence(), forged.getHash());
  }

  @Test
  public void shouldFindAForgedPurgeRecordThroughTheKey() {
    start(5, KEY);
    forgePurge(3, null);
    // every checkpoint signature still matches, as the signed hashes were not touched
    Break found = firstBreak();
    assertThat(found.getReason()).isEqualTo(Break.PURGE_UNATTESTED);
    assertThat(found.getSequence()).isEqualTo(1);
  }

  @Test
  public void shouldNotAcceptTheSignatureOfAnotherPurge() {
    start(5, KEY);
    purgeNow();
    AuditEvent purge = audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_PURGE).list().get(0);
    java.util.regex.Matcher signature = java.util.regex.Pattern.compile("\"hmac\":\"[0-9a-f]+\",\"keyId\":\"[0-9a-f]+\"")
        .matcher(purge.getDetails());
    assertThat(signature.find()).isTrue();
    long first = audit.verify().getChains().get(0).getFirstSequence();
    // more records removed, named by a forged purge record that carries the signature of the real one
    forgePurge(first + 1, signature.group());
    assertThat(firstBreak().getReason()).isEqualTo(Break.PURGE_UNATTESTED);
  }

  @Test
  public void shouldReportAForgedPurgeAsUnattestedWithoutKey() {
    // documented limit: without a key a purge record proves nothing, so the verdict says so
    start(5, null);
    forgePurge(3, null);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isFalse();
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getPurgedThrough()).isEqualTo(3);
    assertThat(chain.isPurgeAttested()).isFalse();
  }

  @Test
  public void shouldAcceptAnUnsignedPurgeWithAKeyOnlyWhenListed() {
    start(0, null);
    purgeNow();
    restart(0, KEY, plugin -> { });
    assertThat(firstBreak().getReason()).isEqualTo(Break.PURGE_UNATTESTED);
    restart(0, KEY, plugin -> plugin.setRetiredKeyIds("unsigned"));
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isFalse();
    assertThat(verification.getChains().get(0).isPurgeAttested()).isFalse();
    // the next purge is signed and attests the start again
    purgeNow();
    restart(0, KEY, plugin -> { });
    AuditVerification signed = audit.verify();
    assertThat(signed.isIntact()).isTrue();
    assertThat(signed.getChains().get(0).isPurgeAttested()).isTrue();
    // without checkpoints (interval 0) no signature covers the records after the purge
    assertThat(signed.isAuthenticated()).isFalse();
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
