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
package dev.fasti.engine.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.AuditTestSupport;
import dev.fasti.engine.audit.StatementTrace;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Test;

/**
 * What the removal time strategies cost when a root process instance ends, as documented in
 * docs/tuning-runtime.md: with {@code end} (the default) one update per history table, with
 * {@code start} none, because every history row gets its removal time when it is written.
 */
public class RemovalTimeStrategyStatementsTest {

  final StatementTrace trace = new StatementTrace();
  ProcessEngine engine;

  ProcessEngine engine(String strategy) {
    ProcessEngineConfigurationImpl configuration = AuditTestSupport.configuration("removal-" + strategy);
    configuration.setHistoryRemovalTimeStrategy(strategy);
    PooledDataSource dataSource = new PooledDataSource(ReflectUtil.getClassLoader(), configuration.getJdbcDriver(),
        configuration.getJdbcUrl(), configuration.getJdbcUsername(), configuration.getJdbcPassword());
    configuration.setDataSource(trace.wrap(dataSource));
    engine = configuration.buildProcessEngine();
    engine.getRepositoryService().createDeployment()
        .addModelInstance("r.bpmn", AuditTestSupport.userTaskProcess("removal")).deploy();
    return engine;
  }

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, false);
  }

  /** The removal time updates among the statements of completing the only task, which ends the instance. */
  List<String> removalTimeUpdatesOfTheEnd() {
    engine.getRuntimeService().startProcessInstanceByKey("removal");
    Task task = engine.getTaskService().createTaskQuery().singleResult();
    trace.clear();
    engine.getTaskService().complete(task.getId());
    return trace.executed().stream()
        .filter(sql -> sql.startsWith("update act_") && sql.contains("set removal_time_ ="))
        .collect(Collectors.toList());
  }

  @Test
  public void shouldUpdateEveryHistoryTableAtTheEndWithStrategyEnd() {
    engine(ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_END);
    List<String> updates = removalTimeUpdatesOfTheEnd();
    // one statement per table that can hold history of the instance, whether it has rows there or not
    assertThat(updates).hasSize(16).doesNotHaveDuplicates().allMatch(sql -> sql.contains("where root_proc_inst_id_ = ?"));
    HistoricProcessInstance instance = engine.getHistoryService().createHistoricProcessInstanceQuery().singleResult();
    assertThat(instance.getRemovalTime()).isAfter(instance.getEndTime());
  }

  @Test
  public void shouldNotUpdateAtTheEndWithStrategyStart() {
    engine(ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_START);
    engine.getRuntimeService().startProcessInstanceByKey("removal");
    // known from the start: the start of the root instance plus the time to live
    HistoricProcessInstance running = engine.getHistoryService().createHistoricProcessInstanceQuery().singleResult();
    assertThat(running.getEndTime()).isNull();
    assertThat(running.getRemovalTime()).isAfter(running.getStartTime());
    Task task = engine.getTaskService().createTaskQuery().singleResult();
    trace.clear();
    engine.getTaskService().complete(task.getId());
    assertThat(trace.executed()).noneMatch(sql -> sql.contains("set removal_time_ ="));
    HistoricProcessInstance ended = engine.getHistoryService().createHistoricProcessInstanceQuery().singleResult();
    assertThat(ended.getRemovalTime()).isEqualTo(running.getRemovalTime());
  }
}
