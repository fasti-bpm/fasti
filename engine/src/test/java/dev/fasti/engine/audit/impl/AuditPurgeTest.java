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
