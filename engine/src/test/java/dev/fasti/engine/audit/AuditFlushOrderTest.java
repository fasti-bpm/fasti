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

import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import dev.fasti.engine.audit.impl.AuditHistoryEventHandler;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInvocationContext;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The records of a command whose transaction ends with it are written after everything the
 * engine writes for it, so that the head of the chain is locked for the audit statements and the
 * commit only, not while the engine sends its own statements. A command that joins a transaction
 * that goes on after it writes its records first, on a chain of its own.
 */
public class AuditFlushOrderTest {

  static final String HEAD = "fasti_audit_head";
  static final String EVENT = "fasti_audit_event";
  static final String OWN_CHAIN = "node-order/end-0";
  static final String EXTENDED_CHAIN = "node-order/0";

  /** What the probe of the plugin answers on this thread: whether a transaction is active outside the engine. */
  static final ThreadLocal<Boolean> OUTER_TRANSACTION = ThreadLocal.withInitial(() -> false);

  final StatementTrace trace = new StatementTrace();
  ProcessEngine engine;
  ProcessEngineConfigurationImpl configuration;

  @Before
  public void startEngine() {
    engine = AuditTestSupport.engine("order", plugin -> {
      plugin.setChains(1);
      plugin.setLockTimeoutSeconds(3);
      plugin.setTransactionActive(OUTER_TRANSACTION::get);
    }, configuration -> {
      PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
          configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
      configuration.setDataSource(trace.wrap(dataSource));
      // no id block fetched in a transaction of its own in the middle of a traced command
      configuration.setIdGenerator(new StrongUuidGenerator());
    });
    configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("order")).deploy();
  }

  @After
  public void closeEngine() {
    OUTER_TRANSACTION.remove();
    AuditTestSupport.cleanUp(engine, true);
  }

  /** One transaction: the statements on the engine tables, then the head, the records, the head, the commit. */
  static void assertAuditLast(List<String> statements) {
    int lastEngine = StatementTrace.last(statements, " act_");
    int firstHead = StatementTrace.first(statements, HEAD);
    int lastHead = StatementTrace.last(statements, HEAD);
    int insert = StatementTrace.first(statements, "insert into " + EVENT);
    int commit = StatementTrace.first(statements, StatementTrace.COMMIT);
    assertThat(lastEngine).as("engine statements in %s", statements).isNotNegative();
    assertThat(firstHead).as("the head is locked after the last engine statement in %s", statements).isGreaterThan(lastEngine);
    assertThat(insert).as("records in %s", statements).isGreaterThan(firstHead).isLessThan(lastHead);
    assertThat(commit).as("commit in %s", statements).isGreaterThan(lastHead);
    assertThat(StatementTrace.last(statements, StatementTrace.COMMIT)).as("one transaction in %s", statements).isEqualTo(commit);
  }

  /** One transaction: the head, the records, the head, then the writes of the engine, the commit. */
  static void assertAuditFirst(List<String> statements) {
    int firstEngineWrite = firstEngineWrite(statements);
    int lastHead = StatementTrace.last(statements, HEAD);
    int insert = StatementTrace.first(statements, "insert into " + EVENT);
    int commit = StatementTrace.first(statements, StatementTrace.COMMIT);
    assertThat(insert).as("records in %s", statements).isNotNegative().isLessThan(lastHead);
    assertThat(firstEngineWrite).as("the engine writes after the head in %s", statements).isGreaterThan(lastHead);
    assertThat(commit).as("commit in %s", statements).isGreaterThan(firstEngineWrite);
    assertThat(StatementTrace.last(statements, StatementTrace.COMMIT)).as("one transaction in %s", statements).isEqualTo(commit);
  }

  static int firstEngineWrite(List<String> statements) {
    for (int i = 0; i < statements.size(); i++) {
      String sql = statements.get(i);
      if (sql.startsWith("insert into act_") || sql.startsWith("update act_") || sql.startsWith("delete from act_")) {
        return i;
      }
    }
    return -1;
  }

  String chainOf(String businessKey) {
    List<AuditEvent> records = AuditTrail.of(engine).createQuery().businessKey(businessKey).types(AuditService.TYPE_PROCESS)
        .list();
    assertThat(records).hasSize(1);
    return records.get(0).getChainId();
  }

  @Test
  public void shouldWriteTheRecordsOfAStartAfterTheEngine() {
    trace.clear();
    AuditTestSupport.startAs(engine, "alice", "order", "o-1");
    assertAuditLast(trace.executed());
    assertThat(chainOf("o-1")).isEqualTo(OWN_CHAIN);
  }

  @Test
  public void shouldWriteTheRecordsOfATaskCompletionAfterTheEngine() {
    AuditTestSupport.startAs(engine, "alice", "order", "o-2");
    Task task = engine.getTaskService().createTaskQuery().singleResult();
    trace.clear();
    AuditTestSupport.as(engine, "alice", () -> engine.getTaskService().complete(task.getId()));
    List<String> statements = trace.executed();
    assertAuditLast(statements);
    // the engine writes history and runtime rows of several tables before the chain is touched
    assertThat(statements.subList(0, StatementTrace.first(statements, HEAD)))
        .anyMatch(sql -> sql.contains("act_hi_taskinst")).anyMatch(sql -> sql.startsWith("delete from act_ru_task"));
  }

  @Test
  public void shouldWriteTheRecordsOfADeploymentAfterTheEngine() {
    trace.clear();
    AuditTestSupport.as(engine, "alice", () -> engine.getRepositoryService().createDeployment()
        .addModelInstance("q.bpmn", userTaskProcess("order2")).deploy());
    assertAuditLast(trace.executed());
  }

  @Test
  public void shouldWriteTheRecordsOfNestedCommandsWithTheOuterCommand() {
    trace.clear();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("order", "o-3");
      engine.getRuntimeService().startProcessInstanceByKey("order", "o-4");
      return null;
    });
    assertAuditLast(trace.executed());
    assertThat(AuditTrail.of(engine).createQuery().types(AuditService.TYPE_PROCESS).action("start").count()).isEqualTo(2);
  }

  @Test
  public void shouldNotTouchTheChainForACommandThatRecordsNothing() {
    trace.clear();
    engine.getTaskService().createTaskQuery().count();
    assertThat(trace.executed()).noneMatch(sql -> sql.contains("fasti_audit"));
  }

  @Test
  public void shouldWriteTheRecordsFirstWhenTheCommandJoinsATransaction() {
    // a transaction that goes on after the command (an outer Spring transaction) holds the head
    // while it writes engine rows in later commands: it writes as up to 1.1, on a chain of its own
    OUTER_TRANSACTION.set(true);
    trace.clear();
    engine.getRuntimeService().startProcessInstanceByKey("order", "o-6");
    assertAuditFirst(trace.executed());
    OUTER_TRANSACTION.set(false);
    assertThat(chainOf("o-6")).isEqualTo(EXTENDED_CHAIN);
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldWriteTheRecordsOfARequiresNewCommandLast() {
    // a "requires new" command begins and ends its own transaction, whatever is active around it
    OUTER_TRANSACTION.set(true);
    trace.clear();
    configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("order", "o-7");
      return null;
    });
    assertAuditLast(trace.executed());
    OUTER_TRANSACTION.set(false);
    assertThat(chainOf("o-7")).isEqualTo(OWN_CHAIN);
  }

  @Test
  public void shouldNotLetACommandThatWritesLastWaitForATransactionThatGoesOn() throws Exception {
    // A transaction that goes on holds its chain from its first command until it ends, and may
    // write engine rows meanwhile. A command that writes its records last holds its engine rows
    // while it waits for the head: on the same chain, the two could wait for each other. They use
    // chains of their own, so the command goes through while the other transaction holds its chain.
    AuditTestSupport.startAs(engine, "alice", "order", "o-8");
    Task task = engine.getTaskService().createTaskQuery().processInstanceBusinessKey("o-8").singleResult();
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    FutureTask<Boolean> outer = new FutureTask<>(() -> {
      OUTER_TRANSACTION.set(true);
      try {
        return configuration.getCommandExecutorTxRequired().execute(commandContext -> {
          engine.getRuntimeService().startProcessInstanceByKey("order", "o-9");
          // as at the end of a first command of the transaction: the chain is held from now on
          AuditHistoryEventHandler.session(commandContext).flush();
          held.countDown();
          try {
            return done.await(30, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            throw new IllegalStateException(e);
          }
        });
      } finally {
        OUTER_TRANSACTION.remove();
      }
    });
    new Thread(outer, "audit-outer-transaction").start();
    try {
      assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
      long start = System.nanoTime();
      // fails after the lock timeout of three seconds if it waits for the head the other one holds
      engine.getTaskService().complete(task.getId());
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(3000);
    } finally {
      done.countDown();
    }
    assertThat(outer.get(60, TimeUnit.SECONDS)).isTrue();
    assertThat(chainOf("o-9")).isEqualTo(EXTENDED_CHAIN);
    assertThat(AuditTrail.of(engine).createQuery().types(AuditService.TYPE_TASK).action("complete").chainId(OWN_CHAIN).count())
        .isEqualTo(1);
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldWriteTheRecordsFirstWhenTheAuditSessionIsOpenedLate() {
    trace.clear();
    // a command context that did not pass the interceptor of the plugin before the engine opened
    // its sessions: the audit session is opened after them and flushed before them, in the same
    // transaction, on the chain of transactions that write their records first
    CommandContext commandContext = configuration.getCommandContextFactory().createCommandContext();
    CommandInvocationContext invocation = new CommandInvocationContext(context -> null, configuration, true);
    Context.setCommandContext(commandContext);
    Context.setCommandInvocationContext(invocation);
    Context.setProcessEngineConfiguration(configuration);
    try {
      commandContext.getDbEntityManager();
      engine.getRuntimeService().startProcessInstanceByKey("order", "o-5");
      assertThat(AuditHistoryEventHandler.session(commandContext).getRecords()).isNotEmpty();
      assertThat(AuditHistoryEventHandler.session(commandContext).isLast()).isFalse();
    } catch (RuntimeException e) {
      invocation.trySetThrowable(e);
    } finally {
      try {
        commandContext.close(invocation);
      } finally {
        Context.removeCommandInvocationContext();
        Context.removeCommandContext();
        Context.removeProcessEngineConfiguration();
      }
    }
    assertAuditFirst(trace.executed());
    assertThat(chainOf("o-5")).isEqualTo(EXTENDED_CHAIN);
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }
}
