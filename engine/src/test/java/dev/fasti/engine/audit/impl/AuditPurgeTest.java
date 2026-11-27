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
package dev.fasti.engine.audit.impl;

import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditPurgeResult;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTestSupport;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditTrailPlugin;
import dev.fasti.engine.audit.AuditVerification;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.After;
import org.junit.Test;

/** A purge removes each chain in batches, and one chain that fails does not stop the others. */
public class AuditPurgeTest {

  static final String KEY = "a-purge-test-key-that-is-long-enough-0123456789";

  ProcessEngine engine;
  ProcessEngineConfigurationImpl configuration;
  AuditService audit;

  void start(String name, Consumer<AuditTrailPlugin> plugin) {
    engine = AuditTestSupport.engine(name, auditTrail -> {
      auditTrail.setRetentionDays(0);
      auditTrail.setHmacKey(KEY);
      auditTrail.setCheckpointInterval(4);
      plugin.accept(auditTrail);
    }, config -> config.setJdbcMaxActiveConnections(12));
    configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    audit = AuditTrail.of(engine);
  }

  @After
  public void closeEngine() {
    ClockUtil.reset();
    AuditTestSupport.cleanUp(engine, true);
  }

  /** Records written an hour ago, on the thread that runs it. */
  void writeOldRecords(Runnable work) {
    ClockUtil.setCurrentTime(new Date(System.currentTimeMillis() - 3_600_000));
    try {
      work.run();
    } finally {
      ClockUtil.reset();
    }
  }

  List<AuditEvent> purges(String chainId) {
    return audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_PURGE).chainId(chainId).list();
  }

  @Test
  public void shouldPurgeAChainInBatchesEachWithAPurgeRecord() {
    start("batches", plugin -> {
      plugin.setChains(1);
      plugin.setPurgeBatchSize(3);
    });
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("b.bpmn", userTaskProcess("b")).deploy();
      for (int i = 0; i < 3; i++) {
        engine.getRuntimeService().startProcessInstanceByKey("b", "old-" + i);
      }
    });
    String chainId = "node-batches/end-0";
    long old = audit.createQuery().chainId(chainId).count();
    assertThat(old).isGreaterThan(6);

    AuditPurgeResult result = audit.purge(new Date());
    assertThat(result.isComplete()).isTrue();
    assertThat(result.getFailedChains()).isEmpty();
    assertThat(result.getRemoved()).isEqualTo(old);
    assertThat(result.getPurgedThrough()).containsOnlyKeys(chainId);
    // one purge record per batch of at most three records, the first one written before any delete
    List<AuditEvent> purges = purges(chainId);
    assertThat(purges).hasSize((int) ((old + 2) / 3));
    assertThat(purges).allSatisfy(purge -> assertThat(purge.getDetails()).contains("\"hmac\":\""));
    // what is left: the purge records (and checkpoints among them), verified and attested
    AuditVerification verification = audit.verify(chainId);
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.isAuthenticated()).isTrue();
    AuditVerification.Chain chain = verification.getChains().get(0);
    assertThat(chain.getPurgedThrough()).isEqualTo(result.getPurgedThrough().get(chainId));
    assertThat(chain.isPurgeAttested()).isTrue();
    assertThat(audit.createQuery().chainId(chainId).list()).allSatisfy(event -> assertThat(event.getType())
        .isEqualTo(AuditService.TYPE_AUDIT));

    // nothing old left: a second purge removes nothing and writes no purge record
    AuditPurgeResult again = audit.purge(new Date(System.currentTimeMillis() - 1_800_000));
    assertThat(again.getRemoved()).isZero();
    assertThat(purges(chainId)).hasSize(purges.size());
  }

  @Test
  public void shouldSignAChainThatAPurgeLeavesWithPurgeRecordsOnly() {
    start("purgedonly", plugin -> {
      plugin.setChains(1);
      plugin.setPurgeBatchSize(3);
      plugin.setCheckpointInterval(1000);
      plugin.setCheckpointIntervalSeconds(0);
    });
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("p")).deploy();
      engine.getRuntimeService().startProcessInstanceByKey("p", "old");
    });
    String chainId = "node-purgedonly/end-0";
    // no checkpoint yet (a node that went away before it signed its chain)
    assertThat(audit.verify(chainId).isAuthenticated()).isFalse();

    assertThat(audit.purge(new Date()).isComplete()).isTrue();
    List<AuditEvent> left = audit.createQuery().chainId(chainId).list();
    assertThat(left).allSatisfy(event -> assertThat(event.getType()).isEqualTo(AuditService.TYPE_AUDIT));
    assertThat(left).anySatisfy(event -> assertThat(event.getAction()).isEqualTo(AuditService.ACTION_CHECKPOINT));
    AuditVerification verification = audit.verify(chainId);
    assertThat(verification.getFirstBreak()).isNull();
    assertThat(verification.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldNotSignAChainWithRecordsLeftAfterAPurge() {
    start("purgedsome", plugin -> {
      plugin.setChains(1);
      plugin.setCheckpointInterval(1000);
      plugin.setCheckpointIntervalSeconds(0);
    });
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("q.bpmn", userTaskProcess("q")).deploy();
      engine.getRuntimeService().startProcessInstanceByKey("q", "old");
    });
    engine.getRuntimeService().startProcessInstanceByKey("q", "recent");
    String chainId = "node-purgedsome/end-0";

    assertThat(audit.purge(new Date(System.currentTimeMillis() - 1_800_000)).isComplete()).isTrue();
    // the recent records are not attested by a purge: they are left to the node that wrote them
    assertThat(audit.createQuery().chainId(chainId).list())
        .noneSatisfy(event -> assertThat(event.getAction()).isEqualTo(AuditService.ACTION_CHECKPOINT));
    assertThat(audit.verify(chainId).isAuthenticated()).isFalse();
  }

  @Test
  public void shouldPurgeUpToTheFirstRecordThatIsNotOldEnough() {
    start("boundary", plugin -> {
      plugin.setChains(1);
      plugin.setPurgeBatchSize(2);
    });
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("o.bpmn", userTaskProcess("o")).deploy();
      engine.getRuntimeService().startProcessInstanceByKey("o", "old");
    });
    String chainId = "node-boundary/end-0";
    Date cut = new Date(System.currentTimeMillis() - 1_800_000);
    long old = audit.createQuery().chainId(chainId).to(cut).count();
    assertThat(old).isGreaterThan(4);
    engine.getRuntimeService().startProcessInstanceByKey("o", "new");
    List<String> young = audit.createQuery().chainId(chainId).from(cut).list().stream().map(AuditEvent::getId)
        .collect(Collectors.toList());
    assertThat(young).isNotEmpty();

    AuditPurgeResult result = audit.purge(cut);
    assertThat(result.isComplete()).isTrue();
    assertThat(result.getRemoved()).isEqualTo(old);
    // nothing old is left, and the young records are all there
    assertThat(audit.createQuery().chainId(chainId).to(cut).count()).isZero();
    assertThat(audit.createQuery().chainId(chainId).list().stream().map(AuditEvent::getId)).containsAll(young);
    assertThat(audit.verify(chainId).isIntact()).isTrue();
  }

  @Test
  public void shouldVerifyAChainWhileABatchedPurgeRemovesItsStart() {
    start("verifying", plugin -> {
      plugin.setChains(1);
      plugin.setPurgeBatchSize(3);
    });
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("v.bpmn", userTaskProcess("v")).deploy();
      for (int i = 0; i < 3; i++) {
        engine.getRuntimeService().startProcessInstanceByKey("v", "old-" + i);
      }
    });
    String chainId = "node-verifying/end-0";
    AuditSettings settings = AuditServiceImpl.settingsOf(configuration);
    AuditServiceImpl service = new AuditServiceImpl(configuration);
    Date before = new Date();
    int[] batches = {0};
    // reads two records at a time, and a purge commits a batch at the start of the chain before
    // each further page: the reading meets a gap after every page, many more times than twice
    AuditVerifier verifier = new AuditVerifier(configuration.getCommandExecutorTxRequired(), settings) {
      {
        pageSize = 2;
      }

      @Override
      protected List<AuditRecord> read(String chain, long after) {
        if (after > 0) {
          long[] batch = configuration.getCommandExecutorTxRequiresNew()
              .execute(context -> service.purgeBatch(context, settings, before, chain));
          if (batch != null) {
            batches[0]++;
          }
        }
        return super.read(chain, after);
      }
    };
    AuditVerification.Chain chain = verifier.verifyChain(chainId);
    assertThat(batches[0]).isGreaterThan(2);
    assertThat(chain.getFirstBreak()).isNull();
    assertThat(chain.isIntact()).isTrue();
    assertThat(chain.getPurgedThrough()).isPositive();
    assertThat(chain.isPurgeAttested()).isTrue();
  }

  @Test
  public void shouldNotRecreateAMissingHeadInAPurge() {
    start("headless", plugin -> plugin.setChains(1));
    writeOldRecords(() -> {
      engine.getRepositoryService().createDeployment().addModelInstance("h.bpmn", userTaskProcess("h")).deploy();
      engine.getRuntimeService().startProcessInstanceByKey("h");
    });
    String chainId = "node-headless/end-0";
    AuditTestSupport.sql(engine, "delete from FASTI_AUDIT_HEAD where CHAIN_ID_ = ?", chainId);
    AuditPurgeResult result = audit.purge(new Date());
    assertThat(result.getFailedChains()).containsExactly(chainId);
    assertThat(result.getRemoved()).isZero();
    // verifying still reports what happened to the head
    AuditVerification.Chain chain = audit.verify(chainId).getChains().get(0);
    assertThat(chain.getFirstBreak()).isNotNull();
    assertThat(chain.getFirstBreak().getReason()).isEqualTo(AuditVerification.Break.HEAD_MISSING);
  }

  @Test
  public void shouldPurgeTheOtherChainsWhenOneCannotBe() throws Exception {
    start("failing", plugin -> {
      plugin.setChains(2);
      plugin.setLockTimeoutSeconds(1);
    });
    engine.getRepositoryService().createDeployment().addModelInstance("f.bpmn", userTaskProcess("f")).deploy();
    // old records on both chains of the node, written by a thread of each slot
    for (int slot = 0; slot < 2; slot++) {
      FutureTask<Void> writer = new FutureTask<>(() -> writeOldRecords(
          () -> engine.getRuntimeService().startProcessInstanceByKey("f")), null);
      AuditSessionTest.threadOfSlot(slot, 2, writer).start();
      writer.get(60, TimeUnit.SECONDS);
    }
    String held = "node-failing/end-1";
    String free = "node-failing/end-0";
    assertThat(audit.createQuery().chainId(held).count()).isPositive();
    assertThat(audit.createQuery().chainId(free).count()).isPositive();

    // a transaction holds chain 1 for longer than the lock timeout
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    FutureTask<Boolean> holder = new FutureTask<>(() -> configuration.getCommandExecutorTxRequired().execute(context -> {
      try {
        AuditStore.lockHead(context, AuditServiceImpl.settingsOf(configuration), held);
      } catch (java.sql.SQLException e) {
        throw new IllegalStateException(e);
      }
      locked.countDown();
      try {
        return release.await(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }));
    new Thread(holder, "audit-purge-holder").start();
    AuditPurgeResult result;
    try {
      assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
      result = audit.purge(new Date());
    } finally {
      release.countDown();
    }
    assertThat(holder.get(60, TimeUnit.SECONDS)).isTrue();
    assertThat(result.isComplete()).isFalse();
    assertThat(result.getFailedChains()).containsExactly(held);
    assertThat(result.getPurgedThrough()).containsOnlyKeys(free);
    assertThat(result.getRemoved()).isPositive();
    assertThat(purges(held)).isEmpty();

    // purged again once the chain is free: the rest
    AuditPurgeResult again = audit.purge(new Date());
    assertThat(again.isComplete()).isTrue();
    assertThat(again.getPurgedThrough()).containsKey(held);
    assertThat(audit.verify().isIntact()).isTrue();
  }
}
