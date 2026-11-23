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
package dev.fasti.engine.foureyes;

import static dev.fasti.engine.foureyes.FourEyesModels.CANDIDATES;
import static dev.fasti.engine.foureyes.FourEyesModels.draftApprove;
import static dev.fasti.engine.foureyes.FourEyesModels.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Two engines on one database, as in a rolling upgrade or a cluster where only some nodes run the
 * plugin (or still run Camunda 7.24): {@code plain} has no plugin, {@code guarded} has it.
 */
public class FourEyesMixedNodesTest {

  static ProcessEngine plain;
  static ProcessEngine guarded;

  @BeforeClass
  public static void buildEngines() {
    plain = configuration("plain", "create-drop").buildProcessEngine();
    ProcessEngineConfigurationImpl withPlugin = configuration("guarded", "false");
    withPlugin.getProcessEnginePlugins().add(new FourEyesPlugin());
    guarded = withPlugin.buildProcessEngine();
  }

  static ProcessEngineConfigurationImpl configuration(String name, String schemaUpdate) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createStandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-foureyes-mixed;DB_CLOSE_DELAY=10");
    configuration.setDatabaseSchemaUpdate(schemaUpdate);
    configuration.setProcessEngineName("foureyes-" + name);
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
    configuration.setJobExecutorActivate(false);
    return configuration;
  }

  @AfterClass
  public static void closeEngines() {
    if (guarded != null) {
      guarded.close();
    }
    if (plain != null) {
      plain.close();
    }
  }

  @After
  public void deleteDeployments() {
    for (Deployment deployment : guarded.getRepositoryService().createDeploymentQuery().list()) {
      guarded.getRepositoryService().deleteDeployment(deployment.getId(), true);
    }
  }

  static Task task(ProcessEngine engine, String processInstanceId) {
    return engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult();
  }

  static void claim(ProcessEngine engine, String userId, Task task) {
    engine.getTaskService().claim(task.getId(), userId);
  }

  static void completeAs(ProcessEngine engine, String userId, Task task) {
    engine.getIdentityService().setAuthenticatedUserId(userId);
    try {
      engine.getTaskService().claim(task.getId(), userId);
      engine.getTaskService().complete(task.getId());
    } finally {
      engine.getIdentityService().clearAuthentication();
    }
  }

  @Test
  public void shouldCountAStepCompletedOnANodeWithoutThePlugin() {
    guarded.getRepositoryService().createDeployment().addModelInstance("fourEyes.bpmn", draftApprove()).deploy();
    String pi = plain.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
    completeAs(plain, "alice", task(plain, pi));
    assertThat(guarded.getRuntimeService().getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft")).isNull();

    Task approve = task(guarded, pi);
    guarded.getIdentityService().setAuthenticatedUserId("alice");
    try {
      claim(guarded, "alice", approve);
      assertThatThrownBy(() -> guarded.getTaskService().complete(approve.getId()))
          .isInstanceOf(FourEyesViolationException.class);
    } finally {
      guarded.getIdentityService().clearAuthentication();
    }
    guarded.getTaskService().setAssignee(approve.getId(), null);
    completeAs(guarded, "bob", task(guarded, pi));
  }

  @Test
  public void shouldNotBeEnforcedByANodeWithoutThePlugin() {
    // documented: every node must run the plugin
    guarded.getRepositoryService().createDeployment().addModelInstance("fourEyes.bpmn", draftApprove()).deploy();
    String pi = guarded.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
    completeAs(guarded, "alice", task(guarded, pi));

    completeAs(plain, "alice", task(plain, pi));

    assertThat(plain.getRuntimeService().createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldRunAWrongRuleDeployedWithoutThePlugin() {
    BpmnModelInstance model = rule(Bpmn.createExecutableProcess("wrongRule").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft, ghost");
    plain.getRepositoryService().createDeployment().addModelInstance("wrongRule.bpmn", model).deploy();

    // parsed into the cache of the guarded engine: not refused, enforced for the known step
    String pi = guarded.getRuntimeService().startProcessInstanceByKey("wrongRule").getId();
    completeAs(guarded, "alice", task(guarded, pi));
    Task approve = task(guarded, pi);
    guarded.getIdentityService().setAuthenticatedUserId("alice");
    try {
      claim(guarded, "alice", approve);
      assertThatThrownBy(() -> guarded.getTaskService().complete(approve.getId()))
          .isInstanceOf(FourEyesViolationException.class)
          .hasMessageContaining("(draft)");
    } finally {
      guarded.getIdentityService().clearAuthentication();
    }
  }
}
