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

import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.CallActivity;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaIn;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaOut;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * The record of the four-eyes control ({@code fasti_completedBy_<step>}) can only be written by
 * the control: no API and no code of the model sets or removes it in a process instance.
 */
public class FourEyesReservedVariableTest {

  static final String RECORD = FourEyesService.VARIABLE_PREFIX + "draft";
  static final String ALICE_DRAFTED = "[\"alice\"]";

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new FourEyesPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl configuration;
  protected IdentityService identityService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected FormService formService;
  protected AuthorizationService authorizationService;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    formService = engineRule.getFormService();
    authorizationService = engineRule.getAuthorizationService();
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
  }

  Task task(String processInstanceId, String key) {
    return taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(key).singleResult();
  }

  void completeAs(String userId, Task task) {
    identityService.setAuthenticatedUserId(userId);
    try {
      taskService.complete(task.getId());
    } finally {
      identityService.clearAuthentication();
    }
  }

  /** Starts draft -> approve (checks draft) and lets alice complete the draft. */
  String draftedByAlice() {
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    completeAs("alice", task(pi, "draft"));
    assertThat(runtimeService.getVariable(pi, RECORD)).isEqualTo(ALICE_DRAFTED);
    return pi;
  }

  void assertReserved(ThrowingCallable write) {
    assertThatThrownBy(write)
        .isInstanceOf(ReservedVariableException.class)
        .isInstanceOf(AuthorizationException.class)
        .isNotInstanceOf(FourEyesViolationException.class)
        .hasMessage("Four-eyes control: variable '" + RECORD + "' records who completed a step and is written by "
            + "the engine only; it cannot be set or removed.")
        .extracting(e -> ((ReservedVariableException) e).getVariableName()).isEqualTo(RECORD);
  }

  void assertAliceStillRefused(String pi) {
    assertThat(runtimeService.getVariable(pi, RECORD)).isEqualTo(ALICE_DRAFTED);
    identityService.setAuthenticatedUserId("alice");
    try {
      assertThatThrownBy(() -> taskService.complete(task(pi, "approve").getId()))
          .isInstanceOf(FourEyesViolationException.class);
    } finally {
      identityService.clearAuthentication();
    }
  }

  // the task API: needs only the permission every candidate and assignee has

  @Test
  public void shouldRefuseACandidateWhoErasesTheRecordThroughTheTaskApi() {
    // authorization on: alice is a candidate of both tasks and gets the task permissions with that
    configuration.setAuthorizationEnabled(true);
    testRule.deploy(draftApprove());
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    completeAs("alice", task(pi, "draft"));
    String approve = task(pi, "approve").getId();

    identityService.setAuthenticatedUserId("alice");
    assertThatThrownBy(() -> taskService.complete(approve)).isInstanceOf(FourEyesViolationException.class);
    assertReserved(() -> taskService.setVariable(approve, RECORD, "[]"));
    assertReserved(() -> taskService.setVariables(approve, Map.of("other", 1, RECORD, "[\"bob\"]")));
    assertReserved(() -> taskService.removeVariable(approve, RECORD));
    assertReserved(() -> taskService.removeVariables(approve, List.of(RECORD)));
    assertThatThrownBy(() -> taskService.complete(approve)).isInstanceOf(FourEyesViolationException.class);
    identityService.clearAuthentication();

    configuration.setAuthorizationEnabled(false);
    assertThat(runtimeService.getVariable(pi, RECORD)).isEqualTo(ALICE_DRAFTED);
    // nothing of a refused call is kept
    assertThat(runtimeService.getVariable(pi, "other")).isNull();
    assertAliceStillRefused(pi);
  }

  @Test
  public void shouldRefuseVariablesOfACompletionOrAFormThatChangeTheRecord() {
    String pi = draftedByAlice();
    String approve = task(pi, "approve").getId();

    // bob may complete the task, but the record is not his to write
    identityService.setAuthenticatedUserId("bob");
    assertReserved(() -> taskService.complete(approve, Map.of(RECORD, "[]")));
    assertReserved(() -> formService.submitTaskForm(approve, Map.of(RECORD, "[]")));
    assertReserved(() -> taskService.complete(approve, Map.of("amount", 10, RECORD, "[\"carol\"]")));
    identityService.clearAuthentication();

    assertThat(task(pi, "approve")).isNotNull();
    assertThat(runtimeService.getVariable(pi, "amount")).isNull();
    assertAliceStillRefused(pi);
  }

  // the runtime API and other ways into the process instance

  @Test
  public void shouldRefuseTheRuntimeApi() {
    String pi = draftedByAlice();

    assertReserved(() -> runtimeService.setVariable(pi, RECORD, "[]"));
    assertReserved(() -> runtimeService.setVariableLocal(pi, RECORD, Variables.stringValue("[]")));
    assertReserved(() -> runtimeService.setVariables(pi, Map.of(RECORD, "[]")));
    assertReserved(() -> runtimeService.setVariablesLocal(pi, Map.of(RECORD, 1)));
    assertReserved(() -> runtimeService.removeVariable(pi, RECORD));
    assertReserved(() -> runtimeService.removeVariableLocal(pi, RECORD));
    assertReserved(() -> runtimeService.removeVariables(pi, List.of(RECORD)));
    assertReserved(() -> runtimeService.removeVariablesLocal(pi, List.of(RECORD)));
    // the execution of the task, where the variable does not exist yet
    assertReserved(() -> runtimeService.setVariableLocal(task(pi, "approve").getExecutionId(), RECORD, "[]"));
    assertReserved(() -> runtimeService.createProcessInstanceModification(pi)
        .startBeforeActivity("approve").setVariable(RECORD, "[]").execute());

    assertAliceStillRefused(pi);
  }

  @Test
  public void shouldRefuseARecordSetBeforeTheStepIsCompleted() {
    testRule.deploy(draftApprove());

    // nobody can be named as having done a step
    assertReserved(() -> runtimeService.startProcessInstanceByKey("fourEyes", Map.of(RECORD, "[\"bob\"]")));
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();
    assertReserved(() -> taskService.setVariable(task(pi, "draft").getId(), RECORD, "[\"bob\"]"));
    assertThat(runtimeService.getVariable(pi, RECORD)).isNull();
  }

  @Test
  public void shouldLetTheRecordBeSetToTheValueItHas() {
    // a form or worker that sends back every variable it read
    String pi = draftedByAlice();

    runtimeService.setVariable(pi, RECORD, ALICE_DRAFTED);
    taskService.setVariables(task(pi, "approve").getId(), Map.of(RECORD, ALICE_DRAFTED, "amount", 10));

    assertThat(runtimeService.getVariable(pi, "amount")).isEqualTo(10);
    assertAliceStillRefused(pi);
    completeAs("bob", task(pi, "approve"));
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldLeaveLocalTaskVariablesAlone() {
    // a local variable of a task is not the record: the control reads the process instance
    String pi = draftedByAlice();
    String approve = task(pi, "approve").getId();

    taskService.setVariableLocal(approve, RECORD, "[]");
    taskService.removeVariableLocal(approve, RECORD);

    assertAliceStillRefused(pi);
  }

  @Test
  public void shouldDeleteAnInstanceWithRecords() {
    String pi = draftedByAlice();

    runtimeService.deleteProcessInstance(pi, "test");

    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
    assertThat(runtimeService.createVariableInstanceQuery().processInstanceIdIn(pi).count()).isZero();
  }

  // code of the model

  @Test
  public void shouldKeepTheRecordWhenADelegateRemovesAllVariables() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .serviceTask("clear").camundaClass(RemoveAllVariables.class)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft"));
    String pi = runtimeService.startProcessInstanceByKey("fourEyes", Map.of("amount", 10)).getId();

    completeAs("alice", task(pi, "draft"));

    assertThat(runtimeService.getVariables(pi)).containsOnlyKeys(RECORD);
    assertAliceStillRefused(pi);
  }

  public static class RemoveAllVariables implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
      execution.getProcessInstance().removeVariables();
    }
  }

  @Test
  public void shouldRefuseADelegateThatSetsTheRecord() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .serviceTask("forge").camundaClass(ForgeRecord.class)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft"));
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();

    identityService.setAuthenticatedUserId("alice");
    assertReserved(() -> taskService.complete(task(pi, "draft").getId()));
    identityService.clearAuthentication();

    assertThat(task(pi, "draft")).isNotNull();
  }

  public static class ForgeRecord implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
      execution.setVariable(RECORD, "[]");
    }
  }

  // copies of all variables made by the engine

  @Test
  public void shouldNotCopyRecordsToOrFromACalledProcess() {
    testRule.deploy(
        passingAllVariables(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
            .startEvent()
            .userTask("draft").camundaCandidateUsers(CANDIDATES)
            .callActivity("call").calledElement("called")
            .userTask("approve").camundaCandidateUsers(CANDIDATES)
            .endEvent().done(), "approve", "draft")),
        rule(Bpmn.createExecutableProcess("called").camundaHistoryTimeToLive(180)
            .startEvent()
            .userTask("check").camundaCandidateUsers(CANDIDATES)
            .userTask("confirm").camundaCandidateUsers(CANDIDATES)
            .endEvent().done(), "confirm", "check"));
    String pi = runtimeService.startProcessInstanceByKey("fourEyes", Map.of("amount", 10)).getId();
    completeAs("alice", task(pi, "draft"));

    ProcessInstance called = runtimeService.createProcessInstanceQuery().superProcessInstanceId(pi).singleResult();
    // the other variables are passed, the record of the calling instance is not
    assertThat(runtimeService.getVariables(called.getId())).containsOnlyKeys("amount");
    completeAs("bob", task(called.getId(), "check"));
    assertThat(runtimeService.getVariables(called.getId()))
        .containsOnlyKeys("amount", FourEyesService.VARIABLE_PREFIX + "check");
    runtimeService.setVariable(called.getId(), "amount", 20);
    completeAs("carol", task(called.getId(), "confirm"));

    // back in the calling instance: its record is as it was, the one of the called instance stayed there
    assertThat(runtimeService.getVariables(pi)).containsOnlyKeys("amount", RECORD);
    assertThat(runtimeService.getVariable(pi, "amount")).isEqualTo(20);
    assertAliceStillRefused(pi);
  }

  /** camunda:in and camunda:out with variables="all" on the call activity. */
  static BpmnModelInstance passingAllVariables(BpmnModelInstance model) {
    ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
    ((CallActivity) model.getModelElementById("call")).setExtensionElements(extensionElements);
    extensionElements.addExtensionElement(CamundaIn.class).setCamundaVariables("all");
    extensionElements.addExtensionElement(CamundaOut.class).setCamundaVariables("all");
    return model;
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_AUDIT)
  public void shouldNotCarryRecordsOverToARestartedInstance() {
    testRule.deploy(draftApprove());
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("fourEyes", Map.of("amount", 10));
    completeAs("alice", task(instance.getId(), "draft"));
    completeAs("bob", task(instance.getId(), "approve"));

    runtimeService.restartProcessInstances(instance.getProcessDefinitionId())
        .startBeforeActivity("draft").processInstanceIds(instance.getId()).execute();

    ProcessInstance restarted = runtimeService.createProcessInstanceQuery().processDefinitionKey("fourEyes").singleResult();
    assertThat(runtimeService.getVariables(restarted.getId())).containsOnlyKeys("amount");
    // the new instance starts over: bob drafts, alice approves
    completeAs("bob", task(restarted.getId(), "draft"));
    completeAs("alice", task(restarted.getId(), "approve"));
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(restarted.getId()).count()).isZero();
  }
}
