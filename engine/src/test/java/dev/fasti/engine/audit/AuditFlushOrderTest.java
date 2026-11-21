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

import dev.fasti.engine.audit.impl.AuditHistoryEventHandler;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInvocationContext;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The records of a command are written after everything the engine writes for it, so that the
 * head of the chain is locked for the audit statements and the commit only, not while the engine
 * sends its own statements.
 */
public class AuditFlushOrderTest {

  static final String HEAD = "fasti_audit_head";
  static final String EVENT = "fasti_audit_event";

  final StatementTrace trace = new StatementTrace();
  ProcessEngine engine;

  @Before
  public void startEngine() {
    engine = AuditTestSupport.engine("order", plugin -> plugin.setChains(1), configuration -> {
      PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
          configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
      configuration.setDataSource(trace.wrap(dataSource));
    });
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("order")).deploy();
  }

  @After
  public void closeEngine() {
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

  @Test
  public void shouldWriteTheRecordsOfAStartAfterTheEngine() {
    trace.clear();
    AuditTestSupport.startAs(engine, "alice", "order", "o-1");
    assertAuditLast(trace.executed());
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
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
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
  public void shouldStillRecordWhenTheAuditSessionIsOpenedLate() {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    trace.clear();
    // a command context that did not pass the interceptor of the plugin before the engine opened
    // its sessions: the audit session is opened after them and flushed before them, in the same
    // transaction
    CommandContext commandContext = configuration.getCommandContextFactory().createCommandContext();
    CommandInvocationContext invocation = new CommandInvocationContext(context -> null, configuration, true);
    Context.setCommandContext(commandContext);
    Context.setCommandInvocationContext(invocation);
    Context.setProcessEngineConfiguration(configuration);
    try {
      commandContext.getDbEntityManager();
      engine.getRuntimeService().startProcessInstanceByKey("order", "o-5");
      assertThat(AuditHistoryEventHandler.session(commandContext).getRecords()).isNotEmpty();
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
    List<String> statements = trace.executed();
    int commit = StatementTrace.first(statements, StatementTrace.COMMIT);
    assertThat(StatementTrace.last(statements, StatementTrace.COMMIT)).as("one transaction in %s", statements).isEqualTo(commit);
    assertThat(StatementTrace.first(statements, "insert into " + EVENT)).isNotNegative().isLessThan(commit);
    assertThat(StatementTrace.last(statements, " act_")).isLessThan(commit);
    assertThat(AuditTrail.of(engine).createQuery().businessKey("o-5").types(AuditService.TYPE_PROCESS).count()).isEqualTo(1);
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }
}
