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

import static dev.fasti.engine.audit.AuditTestSupport.as;
import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

/** What is recorded depends on what the history level lets the engine produce; deployments always are. */
public class AuditHistoryLevelTest {

  ProcessEngine engine;

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  ProcessEngine engine(String name, String historyLevel, String types) {
    // the history level is stored in the database: another level needs a database of its own
    Assume.assumeTrue("needs an in-memory H2 database per level", !ProcessEngineConfiguration.HISTORY_FULL.equals(historyLevel)
        ? AuditTestSupport.configuration(name).getJdbcUrl().startsWith("jdbc:h2:mem:") : true);
    engine = AuditTestSupport.engine(name, plugin -> {
      if (types != null) {
        plugin.setTypes(types);
      }
    }, configuration -> configuration.setHistory(historyLevel));
    return engine;
  }

  void work() {
    engine.getRepositoryService().createDeployment().addModelInstance("h.bpmn", userTaskProcess("h")).deploy();
    as(engine, "alice", () -> {
      String pi = engine.getRuntimeService().startProcessInstanceByKey("h").getId();
      Task task = engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
      engine.getTaskService().claim(task.getId(), "alice");
      engine.getRuntimeService().setVariable(pi, "decision", "yes");
      engine.getTaskService().complete(task.getId());
    });
  }

  long count(String type) {
    return AuditTrail.of(engine).createQuery().types(type).count();
  }

  @Test
  public void shouldRecordOnlyDeploymentsAtLevelNone() {
    engine("levelnone", ProcessEngineConfiguration.HISTORY_NONE, null);
    work();
    assertThat(count(AuditService.TYPE_DEPLOYMENT)).isEqualTo(1);
    assertThat(count(AuditService.TYPE_PROCESS)).isZero();
    assertThat(count(AuditService.TYPE_TASK)).isZero();
    assertThat(count(AuditService.TYPE_OPERATION)).isZero();
    assertThat(AuditTrail.of(engine).getStatus().getHistoryLevel()).isEqualTo("none");
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldRecordInstancesButNoOperationsAtLevelAudit() {
    engine("levelaudit", ProcessEngineConfiguration.HISTORY_AUDIT, null);
    work();
    assertThat(count(AuditService.TYPE_PROCESS)).isEqualTo(2);
    assertThat(count(AuditService.TYPE_TASK)).isPositive();
    assertThat(count(AuditService.TYPE_VARIABLE)).isEqualTo(1);
    assertThat(count(AuditService.TYPE_OPERATION)).isZero();
  }

  @Test
  public void shouldRecordActivitiesOnlyWhenAskedFor() {
    engine("levelfull", ProcessEngineConfiguration.HISTORY_FULL, null);
    work();
    assertThat(count(AuditService.TYPE_ACTIVITY)).isZero();
    assertThat(count(AuditService.TYPE_OPERATION)).isPositive();
    AuditTestSupport.cleanUp(engine, true);

    engine("levelall", ProcessEngineConfiguration.HISTORY_FULL, "all");
    work();
    assertThat(count(AuditService.TYPE_ACTIVITY)).isPositive();
  }

  @Test
  public void shouldRecordOnlyTheChosenTypes() {
    engine("leveltypes", ProcessEngineConfiguration.HISTORY_FULL, "task, deployment");
    work();
    assertThat(count(AuditService.TYPE_TASK)).isPositive();
    assertThat(count(AuditService.TYPE_DEPLOYMENT)).isEqualTo(1);
    assertThat(count(AuditService.TYPE_PROCESS)).isZero();
    assertThat(count(AuditService.TYPE_VARIABLE)).isZero();
    assertThat(count(AuditService.TYPE_OPERATION)).isZero();
  }
}
