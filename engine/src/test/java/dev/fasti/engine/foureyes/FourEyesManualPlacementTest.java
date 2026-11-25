/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package dev.fasti.engine.foureyes;

import static dev.fasti.engine.foureyes.FourEyesModels.draftApprove;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A user who places a token into the process by hand (start at an activity, restart,
 * modification) may skip the checked steps; that user counts as having done them, so the task
 * that checks them needs another person.
 */
public class FourEyesManualPlacementTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new FourEyesPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected IdentityService identityService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected HistoryService historyService;

  @Before
  public void setUp() {
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    historyService = engineRule.getHistoryService();
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
  }

  @Test
  public void shouldCountTheUserWhoStartsAtTheGuardedTask() {
    testRule.deploy(draftApprove());

    String pi = as("alice", () -> runtimeService.createProcessInstanceByKey("fourEyes")
        .startBeforeActivity("approve").execute().getId());

    assertRefused("alice", task(pi, "approve"));
    completeAs("bob", task(pi, "approve"));
  }

  @Test
  public void shouldNotCountTheUserOfAnOrdinaryStart() {
    testRule.deploy(draftApprove());

    String pi = as("alice", () -> runtimeService.startProcessInstanceByKey("fourEyes").getId());
    String viaStartEvent = as("alice", () -> runtimeService.createProcessInstanceByKey("fourEyes")
        .startBeforeActivity(startEventId()).execute().getId());

    assertThat(runtimeService.getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft")).isNull();
    assertThat(runtimeService.getVariable(viaStartEvent, FourEyesService.VARIABLE_PREFIX + "draft")).isNull();
  }

  @Test
  public void shouldCountTheUserWhoMovesTheTokenToTheGuardedTask() {
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    String draftActivity = runtimeService.getActivityInstance(pi).getChildActivityInstances()[0].getId();

    as("alice", () -> {
      runtimeService.createProcessInstanceModification(pi)
          .cancelActivityInstance(draftActivity)
          .startBeforeActivity("approve")
          .execute();
      return null;
    });

    assertRefused("alice", task(pi, "approve"));
    completeAs("bob", task(pi, "approve"));
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_FULL)
  public void shouldCountTheUserWhoRestartsAtTheGuardedTask() {
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    completeAs("alice", task(pi, "draft"));
    runtimeService.deleteProcessInstance(pi, "restart");
    ProcessDefinition definition = engineRule.getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("fourEyes").singleResult();

    as("alice", () -> {
      runtimeService.restartProcessInstances(definition.getId())
          .startBeforeActivity("approve")
          .processInstanceIds(pi)
          .initialSetOfVariables()
          .execute();
      return null;
    });

    String restarted = runtimeService.createProcessInstanceQuery().processDefinitionKey("fourEyes").singleResult().getId();
    assertRefused("alice", task(restarted, "approve"));
    completeAs("bob", task(restarted, "approve"));
  }

  @Test
  public void shouldCountTheCreatorOfAnAsyncModification() {
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    ProcessDefinition definition = engineRule.getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("fourEyes").singleResult();

    org.camunda.bpm.engine.batch.Batch batch = as("alice", () -> runtimeService.createModification(definition.getId())
        .cancelAllForActivity("draft")
        .startBeforeActivity("approve")
        .processInstanceIds(pi)
        .executeAsync());
    executeBatch(batch);

    assertRefused("alice", task(pi, "approve"));
    completeAs("bob", task(pi, "approve"));
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_FULL)
  public void shouldCountTheCreatorOfAnAsyncRestart() {
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    completeAs("alice", task(pi, "draft"));
    runtimeService.deleteProcessInstance(pi, "restart");
    ProcessDefinition definition = engineRule.getRepositoryService().createProcessDefinitionQuery()
        .processDefinitionKey("fourEyes").singleResult();

    org.camunda.bpm.engine.batch.Batch batch = as("alice", () -> runtimeService.restartProcessInstances(definition.getId())
        .startBeforeActivity("approve")
        .processInstanceIds(pi)
        .initialSetOfVariables()
        .executeAsync());
    executeBatch(batch);

    String restarted = runtimeService.createProcessInstanceQuery().processDefinitionKey("fourEyes").singleResult().getId();
    assertRefused("alice", task(restarted, "approve"));
    completeAs("bob", task(restarted, "approve"));
  }

  void executeBatch(org.camunda.bpm.engine.batch.Batch batch) {
    org.camunda.bpm.engine.ManagementService management = engineRule.getManagementService();
    for (String definitionId : new String[] {batch.getSeedJobDefinitionId(), batch.getBatchJobDefinitionId()}) {
      for (org.camunda.bpm.engine.runtime.Job job : management.createJobQuery().jobDefinitionId(definitionId).list()) {
        management.executeJob(job.getId());
      }
    }
    for (org.camunda.bpm.engine.runtime.Job job : management.createJobQuery()
        .jobDefinitionId(batch.getBatchJobDefinitionId()).list()) {
      management.executeJob(job.getId());
    }
    management.deleteBatch(batch.getId(), true);
  }

  String startEventId() {
    return engineRule.getRepositoryService().getBpmnModelInstance(engineRule.getRepositoryService()
        .createProcessDefinitionQuery().processDefinitionKey("fourEyes").singleResult().getId())
        .getModelElementsByType(org.camunda.bpm.model.bpmn.instance.StartEvent.class).iterator().next().getId();
  }

  Task task(String processInstanceId, String key) {
    return taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(key).singleResult();
  }

  <T> T as(String userId, java.util.function.Supplier<T> action) {
    identityService.setAuthenticatedUserId(userId);
    try {
      return action.get();
    } finally {
      identityService.clearAuthentication();
    }
  }

  void completeAs(String userId, Task task) {
    as(userId, () -> {
      taskService.claim(task.getId(), userId);
      taskService.complete(task.getId());
      return null;
    });
  }

  void assertRefused(String userId, Task task) {
    as(userId, () -> {
      taskService.claim(task.getId(), userId);
      assertThatThrownBy(() -> taskService.complete(task.getId()))
          .isInstanceOf(FourEyesViolationException.class)
          .hasMessageContaining("'" + userId + "'");
      taskService.setAssignee(task.getId(), null);
      return null;
    });
  }
}
