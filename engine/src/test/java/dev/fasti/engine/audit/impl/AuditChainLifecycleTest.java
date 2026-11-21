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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTestSupport;
import dev.fasti.engine.audit.AuditTrail;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.TransactionListener;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.cfg.standalone.StandaloneTransactionContext;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.junit.After;
import org.junit.Test;

/**
 * The heads of the chains (created at startup, or in a transaction of their own on first use) and
 * what a transaction holds when it ends in a way the engine does not report.
 */
public class AuditChainLifecycleTest {

  /** What the probe of the plugin answers on this thread: whether a transaction is active outside the engine. */
  static final ThreadLocal<Boolean> OUTER_TRANSACTION = ThreadLocal.withInitial(() -> false);
  /** Whether the transactions of this thread report neither commit nor rollback to their listeners. */
  static final ThreadLocal<Boolean> SILENT_END = ThreadLocal.withInitial(() -> false);

  ProcessEngine engine;
  ProcessEngineConfigurationImpl configuration;

  /** A transaction context that, when asked to, reports neither commit nor rollback (as Spring after a failed commit). */
  static class SilentTransactionContext extends StandaloneTransactionContext {

    SilentTransactionContext(CommandContext commandContext) {
      super(commandContext);
    }

    @Override
    public void addTransactionListener(TransactionState state, TransactionListener listener) {
      if (SILENT_END.get() && (state == TransactionState.COMMITTED || state == TransactionState.ROLLED_BACK)) {
        return;
      }
      super.addTransactionListener(state, listener);
    }
  }

  void start(String name, boolean probe) {
    engine = AuditTestSupport.engine(name, plugin -> {
      plugin.setChains(1);
      plugin.setLockTimeoutSeconds(5);
      if (probe) {
        plugin.setTransactionActive(OUTER_TRANSACTION::get);
      }
    }, config -> config.setTransactionContextFactory(SilentTransactionContext::new));
    configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    engine.getRepositoryService().createDeployment().addModelInstance("l.bpmn", userTaskProcess("l")).deploy();
  }

  @After
  public void closeEngine() {
    OUTER_TRANSACTION.remove();
    SILENT_END.remove();
    AuditSession.HELD.remove();
    AuditTestSupport.cleanUp(engine, true);
  }

  AuditSettings settings() {
    return AuditServiceImpl.settingsOf(configuration);
  }

  AuditStore.Head head(String chainId) {
    return configuration.getCommandExecutorTxRequiresNew().execute(context -> AuditStore.head(context, settings(), chainId));
  }

  String chainOf(String businessKey) {
    List<AuditEvent> records = AuditTrail.of(engine).createQuery().businessKey(businessKey).types(AuditService.TYPE_PROCESS)
        .list();
    assertThat(records).hasSize(1);
    return records.get(0).getChainId();
  }

  @Test
  public void shouldCreateTheHeadsOfTheChainsTheEngineWritesAtStartup() {
    // with a probe: both kinds; a standalone engine without one writes every record last
    start("heads", true);
    assertThat(head("node-heads/0")).isNotNull();
    assertThat(head("node-heads/end-0")).isNotNull();
    engine.close();
    engine = AuditTestSupport.engine("heads2", plugin -> plugin.setChains(2));
    configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    assertThat(head("node-heads2/end-0")).isNotNull();
    assertThat(head("node-heads2/end-1")).isNotNull();
    assertThat(head("node-heads2/0")).isNull();
  }

  @Test
  public void shouldCreateAMissingHeadInATransactionOfItsOwn() throws Exception {
    // a chain whose head is missing (here removed; a chain of a further band is not prepared at
    // startup): the first transaction that appends to it must not hold the new row until it ends,
    // or a second one that appends to the same chain meanwhile fails on the duplicate
    start("missing", true);
    AuditTestSupport.sql(engine, "delete from FASTI_AUDIT_HEAD where CHAIN_ID_ = ?", "node-missing/0");
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    FutureTask<Boolean> first = new FutureTask<>(() -> {
      OUTER_TRANSACTION.set(true);
      try {
        return configuration.getCommandExecutorTxRequired().execute(commandContext -> {
          engine.getRuntimeService().startProcessInstanceByKey("l", "first");
          // as at the end of a first command of an outer transaction: the chain is held from now on
          AuditHistoryEventHandler.session(commandContext).flush();
          held.countDown();
          try {
            return release.await(30, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            throw new IllegalStateException(e);
          }
        });
      } finally {
        OUTER_TRANSACTION.remove();
      }
    });
    FutureTask<String> second = new FutureTask<>(() -> {
      OUTER_TRANSACTION.set(true);
      try {
        return engine.getRuntimeService().startProcessInstanceByKey("l", "second").getId();
      } finally {
        OUTER_TRANSACTION.remove();
      }
    });
    new Thread(first, "audit-first-use-1").start();
    try {
      assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
      // committed already, while the transaction that needed it goes on
      assertThat(head("node-missing/0")).isNotNull();
      new Thread(second, "audit-first-use-2").start();
      // the second one waits for the head (it does not see a missing head of its own to insert)
      Thread.sleep(300);
      assertThat(second.isDone()).isFalse();
    } finally {
      release.countDown();
    }
    assertThat(first.get(60, TimeUnit.SECONDS)).isTrue();
    assertThat(second.get(60, TimeUnit.SECONDS)).isNotNull();
    assertThat(chainOf("first")).isEqualTo("node-missing/0");
    assertThat(chainOf("second")).isEqualTo("node-missing/0");
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldGiveBackWhatATransactionThatEndedUnreportedHeld() {
    // the engine begins the transaction of the command (no outer one); it ends, but tells its
    // listeners neither of commit nor of rollback (Spring after a commit or rollback that failed)
    start("unreported", true);
    SILENT_END.set(true);
    engine.getRuntimeService().startProcessInstanceByKey("l", "unreported-1");
    SILENT_END.set(false);
    assertThat(AuditSession.holdsChain(settings())).isFalse();
    // the next transaction of the thread is an outermost one again, not one of the next band
    engine.getRuntimeService().startProcessInstanceByKey("l", "unreported-2");
    assertThat(chainOf("unreported-1")).isEqualTo("node-unreported/end-0");
    assertThat(chainOf("unreported-2")).isEqualTo("node-unreported/end-0");

    // the same for a "requires new" command
    SILENT_END.set(true);
    configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("l", "unreported-3");
      return null;
    });
    SILENT_END.set(false);
    assertThat(AuditSession.holdsChain(settings())).isFalse();
    assertThat(chainOf("unreported-3")).isEqualTo("node-unreported/end-0");
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldNotTakeTheChainOfAnEndedTransactionOnTheSameConnection() {
    // an entry left behind by a transaction that ended unreported, for the connection a pool now
    // hands out again: a transaction that begins in this command does not take its chain
    start("stale", false);
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      Object connection = AuditSession.physical(AuditStore.connection(commandContext));
      List<AuditSession.Held> held = AuditSession.HELD.get();
      AuditSession.Held stale = new AuditSession.Held(settings(), connection, "node-stale/0", 0, false, new Object(), held);
      held.add(stale);
      engine.getRuntimeService().startProcessInstanceByKey("l", "stale-1");
      AuditSession session = AuditHistoryEventHandler.session(commandContext);
      assertThat(session.isLast()).isTrue();
      session.flush();
      assertThat(AuditSession.HELD.get()).doesNotContain(stale).hasSize(1);
      // asked again in the same transaction: the same chain
      assertThat(session.chainFor(commandContext)).isEqualTo("node-stale/end-0");
      return null;
    });
    assertThat(chainOf("stale-1")).isEqualTo("node-stale/end-0");
    assertThat(AuditSession.HELD.get()).isEmpty();
  }

  @Test
  public void shouldKeepTheChainOfAnOuterTransactionWhileANestedOneRuns() {
    // what a suspended outer transaction holds stays held while a command that began its own runs
    start("outer", true);
    OUTER_TRANSACTION.set(true);
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("l", "outer-1");
      AuditHistoryEventHandler.session(commandContext).flush();
      assertThat(AuditSession.holdsChain(settings())).isTrue();
      configuration.getCommandExecutorTxRequiresNew().execute(nested -> {
        engine.getRuntimeService().startProcessInstanceByKey("l", "nested-1");
        return null;
      });
      assertThat(AuditSession.holdsChain(settings())).isTrue();
      return null;
    });
    OUTER_TRANSACTION.set(false);
    assertThat(AuditSession.holdsChain(settings())).isFalse();
    assertThat(chainOf("outer-1")).isEqualTo("node-outer/0");
    // nested in a transaction that holds a chain: the next band, created on first use
    assertThat(chainOf("nested-1")).isEqualTo("node-outer/end-1");
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }
}
