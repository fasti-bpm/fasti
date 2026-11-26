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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTestSupport;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditTrailPlugin;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Test;

/** Closing the engine waits for its last checkpoints only so long, also when their chains are held. */
public class AuditCheckpointerCloseTest {

  static final String KEY = "a-close-test-key-that-is-long-enough-0123456789";
  static final int CHAINS = 4;

  ProcessEngine engine;

  ProcessEngine build() {
    Consumer<AuditTrailPlugin> plugin = auditTrail -> {
      auditTrail.setChains(CHAINS);
      auditTrail.setHmacKey(KEY);
      auditTrail.setCheckpointIntervalSeconds(0);
    };
    return AuditTestSupport.engine("close", plugin, config -> config.setJdbcMaxActiveConnections(12));
  }

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  long checkpoints(String chainId) {
    return AuditTrail.of(engine).createQuery().chainId(chainId).types(AuditService.TYPE_AUDIT)
        .action(AuditService.ACTION_CHECKPOINT).count();
  }

  @Test
  public void shouldNotWaitLongerThanTheDeadlineForHeldChains() throws Exception {
    engine = build();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    engine.getRepositoryService().createDeployment().addModelInstance("c.bpmn", userTaskProcess("c")).deploy();
    // records on every chain of the node, written by a thread of each slot
    for (int slot = 0; slot < CHAINS; slot++) {
      FutureTask<Void> writer = new FutureTask<>(() -> engine.getRuntimeService().startProcessInstanceByKey("c"), null);
      AuditSessionTest.threadOfSlot(slot, CHAINS, writer).start();
      writer.get(60, TimeUnit.SECONDS);
    }
    // transactions hold the heads of all of them
    AuditSettings settings = AuditServiceImpl.settingsOf(configuration);
    CountDownLatch locked = new CountDownLatch(CHAINS);
    CountDownLatch release = new CountDownLatch(1);
    List<FutureTask<Object>> holders = new ArrayList<>();
    for (int slot = 0; slot < CHAINS; slot++) {
      String chainId = settings.endChainId(slot);
      FutureTask<Object> holder = new FutureTask<>(() -> configuration.getCommandExecutorTxRequired().execute(context -> {
        try {
          AuditStore.lockHead(context, settings, chainId);
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException(e);
        }
        locked.countDown();
        try {
          return release.await(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
      }));
      holders.add(holder);
      new Thread(holder, "audit-close-holder-" + slot).start();
    }
    long took;
    try {
      assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
      long started = System.nanoTime();
      engine.close();
      took = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    } finally {
      release.countDown();
    }
    for (FutureTask<Object> holder : holders) {
      try {
        holder.get(60, TimeUnit.SECONDS);
      } catch (java.util.concurrent.ExecutionException e) {
        // its connection may have been closed with the engine
      }
    }
    // four chains of five seconds each would take 20 seconds
    assertThat(took).isLessThan(TimeUnit.SECONDS.toMillis(AuditCheckpointer.CLOSE_SECONDS + 3));

    // the next start signs them when it closes
    engine = build();
    for (int slot = 0; slot < CHAINS; slot++) {
      assertThat(checkpoints(settings.endChainId(slot))).isZero();
    }
    engine.close();
    engine = build();
    for (int slot = 0; slot < CHAINS; slot++) {
      assertThat(checkpoints(settings.endChainId(slot))).isEqualTo(1);
    }
  }
}
