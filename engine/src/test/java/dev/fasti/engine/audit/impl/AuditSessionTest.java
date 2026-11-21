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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTestSupport;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditVerification;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Test;

/**
 * Which chain a transaction appends to, and how long it holds it: nested transactions use slots
 * of their own, a wait for a chain ends, and what a transaction holds is known per engine and
 * given back wherever the transaction ends.
 */
public class AuditSessionTest {

  ProcessEngine engine;
  ProcessEngineConfigurationImpl configuration;

  void start(String name, int chains, int lockTimeoutSeconds) {
    engine = AuditTestSupport.engine(name, plugin -> {
      plugin.setChains(chains);
      plugin.setLockTimeoutSeconds(lockTimeoutSeconds);
    }, config -> config.setJdbcMaxActiveConnections(12));
    configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    engine.getRepositoryService().createDeployment().addModelInstance("s.bpmn", userTaskProcess("s")).deploy();
  }

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
    AuditSession.HELD.remove();
  }

  AuditSettings settings() {
    return AuditServiceImpl.settingsOf(configuration);
  }

  @Test
  public void shouldGiveEveryNestingDepthSlotsOfItsOwn() {
    for (int chains = 1; chains <= 5; chains++) {
      for (int depth = 0; depth < 4; depth++) {
        for (long thread = 1; thread < 40; thread++) {
          int slot = AuditSession.slot(thread, chains, depth);
          assertThat(slot).isBetween(depth * chains, (depth + 1) * chains - 1);
          // the slot of a thread keeps its place in every band
          assertThat(slot - depth * chains).isEqualTo(AuditSession.slot(thread, chains, 0));
        }
      }
    }
  }

  /** A thread whose transactions prefer the slot. */
  static Thread threadOfSlot(int slot, int chains, Runnable work) {
    for (int i = 0; i < 1000; i++) {
      Thread thread = new Thread(work, "audit-slot-" + slot);
      if (AuditSession.slot(thread.getId(), chains, 0) == slot) {
        return thread;
      }
    }
    throw new AssertionError("no thread for slot " + slot);
  }

  @Test
  public void shouldNotLetNestedTransactionsOfTwoThreadsWaitForEachOther() throws Exception {
    // two chains; thread A holds chain 0 and thread B chain 1 in their outer transactions, then
    // each starts a nested one: neither may take the chain the other's outer transaction holds
    start("ring", 2, 5);
    CyclicBarrier bothHold = new CyclicBarrier(2);
    List<FutureTask<Boolean>> tasks = new ArrayList<>();
    for (int slot = 0; slot < 2; slot++) {
      FutureTask<Boolean> task = new FutureTask<>(() -> configuration.getCommandExecutorTxRequired().execute(outer -> {
        engine.getRuntimeService().startProcessInstanceByKey("s");
        AuditHistoryEventHandler.session(outer).flush();
        try {
          bothHold.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        configuration.getCommandExecutorTxRequiresNew().execute(nested -> {
          engine.getRuntimeService().startProcessInstanceByKey("s");
          return null;
        });
        return true;
      }));
      tasks.add(task);
      threadOfSlot(slot, 2, task).start();
    }
    for (FutureTask<Boolean> task : tasks) {
      assertThat(task.get(60, TimeUnit.SECONDS)).isTrue();
    }
    AuditService audit = AuditTrail.of(engine);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count()).isEqualTo(4);
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    List<String> chains = verification.getChains().stream().filter(chain -> chain.getRecords() > 0)
        .map(AuditVerification.Chain::getChainId).collect(Collectors.toList());
    // the outer transactions in the slots of the node, the nested ones in the next band
    assertThat(chains).contains("node-ring/end-0", "node-ring/end-1", "node-ring/end-2", "node-ring/end-3");
  }

  @Test
  public void shouldFailInsteadOfWaitingForEverForAChain() throws Exception {
    start("locktimeout", 1, 1);
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    FutureTask<Boolean> holder = new FutureTask<>(() -> configuration.getCommandExecutorTxRequired().execute(outer -> {
      engine.getRuntimeService().startProcessInstanceByKey("s");
      AuditHistoryEventHandler.session(outer).flush();
      held.countDown();
      try {
        return release.await(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }));
    new Thread(holder, "audit-holder").start();
    assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
    try {
      long start = System.nanoTime();
      assertThatThrownBy(() -> engine.getRuntimeService().startProcessInstanceByKey("s"))
          .isInstanceOf(ProcessEngineException.class).hasMessageContaining("fasti.audit.lock-timeout-seconds");
      // one second, not the lock timeout of the database (10 seconds in the test configuration and more elsewhere)
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(8000);
    } finally {
      release.countDown();
    }
    assertThat(holder.get(60, TimeUnit.SECONDS)).isTrue();
    // the command that gave up wrote nothing; the next one goes through
    engine.getRuntimeService().startProcessInstanceByKey("s");
    AuditService audit = AuditTrail.of(engine);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count()).isEqualTo(2);
    assertThat(engine.getRuntimeService().createProcessInstanceQuery().count()).isEqualTo(2);
    assertThat(audit.verify().isIntact()).isTrue();
  }

  @Test
  public void shouldKnowWhatATransactionHoldsPerEngine() {
    start("held", 2, 5);
    AuditSettings other = new AuditSettings("other-node", 2, null, null, null, null, 0, 0);
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      assertThat(AuditSession.holdsChain(settings())).isFalse();
      engine.getRuntimeService().startProcessInstanceByKey("s");
      AuditSession session = AuditHistoryEventHandler.session(commandContext);
      session.flush();
      String chain = session.chainFor(commandContext);
      assertThat(chain).startsWith("node-held/");
      assertThat(AuditSession.holdsChain(settings())).isTrue();
      // another engine in the same transaction, on the same connection, has nothing yet
      assertThat(AuditSession.holdsChain(other)).isFalse();
      // and gets a chain of its own node, in its first band, not the chain of this engine
      String otherChain = new AuditSession(other).chainFor(commandContext);
      assertThat(otherChain).startsWith("other-node/").isIn("other-node/0", "other-node/1");
      assertThat(AuditSession.holdsChain(other)).isTrue();
      assertThat(new AuditSession(other).chainFor(commandContext)).isEqualTo(otherChain);
      assertThat(session.chainFor(commandContext)).isEqualTo(chain);
      return null;
    });
    // both were given back when the transaction ended
    assertThat(AuditSession.holdsChain(settings())).isFalse();
    assertThat(AuditSession.holdsChain(other)).isFalse();
    assertThat(AuditSession.HELD.get()).isEmpty();
  }

  @Test
  public void shouldGiveAChainBackWhenTheTransactionEndsOnAnotherThread() throws Exception {
    start("released", 1, 5);
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("s");
      AuditHistoryEventHandler.session(commandContext).flush();
      assertThat(AuditSession.holdsChain(settings())).isTrue();
      AuditSession.Held entry = AuditSession.HELD.get().get(0);
      // as a transaction manager does that completes a timed-out transaction on its own thread
      Thread reaper = new Thread(() -> AuditSession.release(entry), "audit-reaper");
      reaper.start();
      try {
        reaper.join(10_000);
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
      assertThat(AuditSession.holdsChain(settings())).isFalse();
      return null;
    });
    assertThat(AuditSession.HELD.get()).isEmpty();
    // the thread is not taken for one that still holds a chain: its next transaction is an outermost one again
    engine.getRuntimeService().startProcessInstanceByKey("s");
    assertThat(AuditTrail.of(engine).verify().getChains())
        .filteredOn(chain -> chain.getRecords() > 0).extracting(AuditVerification.Chain::getChainId)
        .containsExactly("node-released/end-0");
  }
}
