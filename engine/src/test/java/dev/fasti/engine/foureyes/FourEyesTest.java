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
import static dev.fasti.engine.foureyes.FourEyesModels.draftReviewApprove;
import static dev.fasti.engine.foureyes.FourEyesModels.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ParseException;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.RequiredHistoryLevel;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class FourEyesTest {

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
  protected FourEyesService fourEyes;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    formService = engineRule.getFormService();
    authorizationService = engineRule.getAuthorizationService();
    fourEyes = FourEyes.of(engineRule.getProcessEngine());
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
  }

  String start(String key) {
    return runtimeService.startProcessInstanceByKey(key).getId();
  }

  Task task(String processInstanceId, String key) {
    return taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(key).singleResult();
  }

  List<Task> tasks(String processInstanceId, String key) {
    return taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(key).list();
  }

  /** Claims and completes the task as the given user. */
  void completeAs(String userId, Task task) {
    completeAs(userId, task, null);
  }

  void completeAs(String userId, Task task, Map<String, Object> variables) {
    identityService.setAuthenticatedUserId(userId);
    try {
      taskService.claim(task.getId(), userId);
      taskService.complete(task.getId(), variables);
    } finally {
      identityService.clearAuthentication();
    }
  }

  void assertRefused(String userId, Task task) {
    identityService.setAuthenticatedUserId(userId);
    try {
      if (task.getAssignee() == null) {
        taskService.claim(task.getId(), userId);
      }
      assertThatThrownBy(() -> taskService.complete(task.getId()))
          .isInstanceOf(FourEyesViolationException.class)
          .isInstanceOf(AuthorizationException.class)
          .hasMessageContaining("Four-eyes control")
          .hasMessageContaining("'" + userId + "'");
    } finally {
      identityService.clearAuthentication();
    }
    assertThat(taskService.createTaskQuery().taskId(task.getId()).count()).isEqualTo(1);
  }

  // completion

  @Test
  public void shouldRefuseWhenTheSameUserCompletesBothSteps() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));

    Task approve = task(pi, "approve");
    identityService.setAuthenticatedUserId("alice");
    taskService.claim(approve.getId(), "alice");
    FourEyesViolationException e = (FourEyesViolationException) catchThrowable(() -> taskService.complete(approve.getId()));
    identityService.clearAuthentication();

    assertThat(e.getMessage()).isEqualTo("Four-eyes control: user 'alice' cannot complete task 'Approve' (approve) "
        + "after completing an earlier step it checks (draft). Another person must complete it.");
    assertThat(e.getTaskId()).isEqualTo(approve.getId());
    assertThat(e.getTaskDefinitionKey()).isEqualTo("approve");
    assertThat(e.getExcludedUserIds()).containsExactly("alice");
    assertThat(e.getEarlierSteps()).containsExactly("draft");
    assertThat(task(pi, "approve")).isNotNull();
  }

  @Test
  public void shouldAllowAnotherUser() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));

    completeAs("bob", task(pi, "approve"));

    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldRecordTheCompleterInAProcessVariable() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));

    assertThat(runtimeService.getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft")).isEqualTo("[\"alice\"]");
    // the guarded step itself is not recorded unless a rule names it
    completeAs("bob", task(pi, "approve"));
  }

  @Test
  public void shouldCountTheUserWhoCompletedWithoutClaiming() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    identityService.setAuthenticatedUserId("alice");
    taskService.complete(task(pi, "draft").getId());
    identityService.clearAuthentication();

    assertRefused("alice", task(pi, "approve"));
  }

  @Test
  public void shouldCountTheAssigneeAndTheUserWhoCompletedForThem() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    Task draft = task(pi, "draft");
    taskService.setAssignee(draft.getId(), "alice");
    identityService.setAuthenticatedUserId("bob");
    taskService.complete(draft.getId());
    identityService.clearAuthentication();

    assertRefused("alice", task(pi, "approve"));
    assertRefused("bob", task(pi, "approve"));
  }

  @Test
  public void shouldRefuseWhileTheAssigneeDidAnEarlierStep() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    Task approve = task(pi, "approve");
    taskService.setAssignee(approve.getId(), "alice");

    identityService.setAuthenticatedUserId("carol");
    assertThatThrownBy(() -> taskService.complete(approve.getId()))
        .isInstanceOf(FourEyesViolationException.class)
        .hasMessage("Four-eyes control: task 'Approve' (approve) is assigned to 'alice', who completed an earlier "
            + "step it checks (draft). Assign it to another person before completing it.");

    taskService.setAssignee(approve.getId(), "carol");
    taskService.complete(approve.getId());
    identityService.clearAuthentication();
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldUseTheAssigneeWithoutAuthentication() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    Task draft = task(pi, "draft");
    taskService.setAssignee(draft.getId(), "alice");
    taskService.complete(draft.getId());

    Task approve = task(pi, "approve");
    taskService.setAssignee(approve.getId(), "alice");
    assertThatThrownBy(() -> taskService.complete(approve.getId())).isInstanceOf(FourEyesViolationException.class);

    taskService.setAssignee(approve.getId(), "bob");
    taskService.complete(approve.getId());
  }

  @Test
  public void shouldRefuseWhenNobodyCanBeIdentified() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));

    Task approve = task(pi, "approve");
    assertThatThrownBy(() -> taskService.complete(approve.getId()))
        .isInstanceOf(FourEyesViolationException.class)
        .hasMessageContaining("can only be completed by an authenticated user or by its assignee");
  }

  @Test
  public void shouldAllowAnonymousCompletionWhileNoCheckedStepWasCompletedByAnyone() {
    // draft completed by nobody known: nothing recorded, nothing to compare
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    taskService.complete(task(pi, "draft").getId());

    taskService.complete(task(pi, "approve").getId());

    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldCheckEveryNamedStep() {
    testRule.deploy(draftReviewApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "review"));

    assertRefused("alice", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    assertRefused("bob", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    completeAs("carol", task(pi, "approve"));
  }

  @Test
  public void shouldOnlyCheckTheNamedSteps() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("review").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "review"));

    completeAs("bob", task(pi, "approve"));
  }

  @Test
  public void shouldSupportSeveralRulesInOneProcess() {
    testRule.deploy(rule(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("review").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "review", "draft"), "approve", "review"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    assertRefused("alice", task(pi, "review"));
    taskService.setAssignee(task(pi, "review").getId(), null);
    completeAs("bob", task(pi, "review"));

    // review is guarded and checked at the same time; the drafter may approve
    assertRefused("bob", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    completeAs("alice", task(pi, "approve"));
  }

  @Test
  public void shouldRememberEveryRoundOfALoop() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("loop").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .exclusiveGateway("decision")
          .condition("approved", "${approved}").endEvent("done")
        .moveToLastGateway()
          .condition("rework", "${!approved}").connectTo("draft")
        .done(), "approve", "draft"));
    String pi = start("loop");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "approve"), Variables.createVariables().putValue("approved", false));
    completeAs("carol", task(pi, "draft"));

    // alice drafted in the first round, carol in the second: neither may approve
    assertRefused("alice", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    assertRefused("carol", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    // bob only approved (rejected) before, which this rule does not check
    completeAs("bob", task(pi, "approve"), Variables.createVariables().putValue("approved", true));
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldRequireDifferentPeopleForTheInstancesOfAMultiInstanceStepThatChecksItself() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("twoApprovals").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
          .multiInstance().parallel().cardinality("2").multiInstanceDone()
        .endEvent().done(), "approve", "approve"));
    String pi = start("twoApprovals");
    List<Task> approvals = tasks(pi, "approve");
    assertThat(approvals).hasSize(2);

    completeAs("alice", approvals.get(0));
    assertRefused("alice", approvals.get(1));
    taskService.setAssignee(approvals.get(1).getId(), null);
    completeAs("bob", approvals.get(1));

    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldCountEveryCompleterOfAMultiInstanceStep() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
          .multiInstance().sequential().cardinality("2").multiInstanceDone()
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "draft"));

    assertThat(runtimeService.getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft"))
        .isEqualTo("[\"alice\",\"bob\"]");
    assertRefused("alice", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    assertRefused("bob", task(pi, "approve"));
    taskService.setAssignee(task(pi, "approve").getId(), null);
    completeAs("carol", task(pi, "approve"));
  }

  @Test
  public void shouldFindStepsInsideSubProcesses() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .subProcess("prepare").embeddedSubProcess()
          .startEvent().userTask("draft").camundaCandidateUsers(CANDIDATES).endEvent()
        .subProcessDone()
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));

    assertRefused("alice", task(pi, "approve"));
  }

  @Test
  public void shouldKeepTheRuleWithinOneProcessInstance() {
    // the called process has a step with the same id; the caller's draft does not count there
    BpmnModelInstance parent = Bpmn.createExecutableProcess("parent").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .callActivity("call").calledElement("child")
        .endEvent().done();
    BpmnModelInstance child = rule(Bpmn.createExecutableProcess("child").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft");
    testRule.deploy(parent, child);
    String pi = start("parent");
    completeAs("alice", task(pi, "draft"));
    String childPi = runtimeService.createProcessInstanceQuery().superProcessInstanceId(pi).singleResult().getId();
    completeAs("bob", task(childPi, "draft"));

    assertRefused("bob", task(childPi, "approve"));
    taskService.setAssignee(task(childPi, "approve").getId(), null);
    completeAs("alice", task(childPi, "approve"));
  }

  // other ways to end a task

  @Test
  public void shouldRefuseAFormSubmit() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    Task approve = task(pi, "approve");

    identityService.setAuthenticatedUserId("alice");
    taskService.claim(approve.getId(), "alice");
    assertThatThrownBy(() -> formService.submitTaskForm(approve.getId(), Variables.createVariables()))
        .isInstanceOf(FourEyesViolationException.class);
    identityService.clearAuthentication();
  }

  @Test
  public void shouldRefuseABpmnErrorAndAnEscalation() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaCandidateUsers(CANDIDATES)
          .boundaryEvent("rejected").error("REJECT").endEvent("rejectedEnd")
        .moveToActivity("approve")
          .boundaryEvent("escalated").escalation("ESCALATE").endEvent("escalatedEnd")
        .moveToActivity("approve")
        .endEvent().done(), "approve", "draft"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    Task approve = task(pi, "approve");

    identityService.setAuthenticatedUserId("alice");
    taskService.claim(approve.getId(), "alice");
    assertThatThrownBy(() -> taskService.handleBpmnError(approve.getId(), "REJECT"))
        .isInstanceOf(FourEyesViolationException.class);
    assertThatThrownBy(() -> taskService.handleEscalation(approve.getId(), "ESCALATE"))
        .isInstanceOf(FourEyesViolationException.class);
    identityService.clearAuthentication();

    identityService.setAuthenticatedUserId("bob");
    taskService.setAssignee(approve.getId(), "bob");
    taskService.handleBpmnError(approve.getId(), "REJECT");
    identityService.clearAuthentication();
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldLetAnExcludedUserClaimAndResolve() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    Task approve = task(pi, "approve");

    // claiming is not refused (the tasklist warns instead)
    identityService.setAuthenticatedUserId("alice");
    taskService.claim(approve.getId(), "alice");
    identityService.clearAuthentication();

    // resolving a delegated task hands it back to its owner, it does not complete it
    taskService.setAssignee(approve.getId(), "bob");
    identityService.setAuthenticatedUserId("bob");
    taskService.delegateTask(approve.getId(), "alice");
    identityService.setAuthenticatedUserId("alice");
    formService.submitTaskForm(approve.getId(), Variables.createVariables());
    identityService.setAuthenticatedUserId("bob");
    taskService.complete(approve.getId());
    identityService.clearAuthentication();
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldCheckACompletionFromAListener() {
    // a task listener that completes the guarded task bypasses the task service; the
    // built-in listener refuses it (the engine wraps exceptions of task listeners)
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").camundaAssignee("alice")
          .camundaTaskListenerClass(TaskListener.EVENTNAME_CREATE, AutoCompleteListener.class)
        .endEvent().done(), "approve", "draft"));
    String pi = start("fourEyes");
    Task draft = task(pi, "draft");

    identityService.setAuthenticatedUserId("alice");
    taskService.claim(draft.getId(), "alice");
    assertThatThrownBy(() -> taskService.complete(draft.getId()))
        .isInstanceOf(ProcessEngineException.class)
        .hasRootCauseInstanceOf(FourEyesViolationException.class);
    identityService.clearAuthentication();
    assertThat(task(pi, "draft")).isNotNull();
  }

  @Test
  public void shouldNotRecordACompletionThatAListenerTurnsIntoABpmnError() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
          .camundaTaskListenerClass(TaskListener.EVENTNAME_COMPLETE, RejectListener.class)
          .boundaryEvent("rejected").error("REJECTED").userTask("redraft").camundaCandidateUsers(CANDIDATES)
          .userTask("approve").camundaCandidateUsers(CANDIDATES).endEvent()
        .moveToActivity("draft").connectTo("approve")
        .done(), "approve", "draft"));
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "redraft"));

    // draft ended with the error of its listener, so alice did not complete it
    assertThat(runtimeService.getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft")).isNull();
    completeAs("alice", task(pi, "approve"));
  }

  public static class RejectListener implements TaskListener {
    @Override
    public void notify(org.camunda.bpm.engine.delegate.DelegateTask delegateTask) {
      throw new org.camunda.bpm.engine.delegate.BpmnError("REJECTED");
    }
  }

  @Test
  public void shouldIgnoreEntriesOfTheRecordThatAreNoUserIds() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    runtimeService.setVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft", "[null, 1, {\"a\": 2}, \"carol\"]");

    // carol counts; the rest is skipped instead of failing
    assertRefused("carol", task(pi, "approve"));
    identityService.setAuthenticatedUserId("carol");
    assertThat(fourEyes.getStatus(task(pi, "approve").getId()).getUserSteps()).containsExactly("draft");
    identityService.clearAuthentication();
  }

  public static class AutoCompleteListener implements TaskListener {
    @Override
    public void notify(org.camunda.bpm.engine.delegate.DelegateTask delegateTask) {
      delegateTask.complete();
    }
  }

  // history

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void shouldFallBackToTheHistoryWhenTheVariableIsGone() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    runtimeService.removeVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft");

    // the historic task instance of draft keeps alice as assignee
    assertRefused("alice", task(pi, "approve"));
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_FULL)
  public void shouldFallBackToTheOperationLogForACompletionWithoutAssignee() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    identityService.setAuthenticatedUserId("alice");
    taskService.complete(task(pi, "draft").getId());
    identityService.clearAuthentication();
    runtimeService.setVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft", "not a list");

    assertRefused("alice", task(pi, "approve"));
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void shouldNotCountCancelledTasks() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaCandidateUsers(CANDIDATES)
          .boundaryEvent("cancel").message("cancelDraft").userTask("redraft").camundaCandidateUsers(CANDIDATES)
          .userTask("approve").camundaCandidateUsers(CANDIDATES).endEvent()
        .moveToActivity("draft").connectTo("approve")
        .done(), "approve", "draft"));
    String pi = start("fourEyes");
    taskService.setAssignee(task(pi, "draft").getId(), "alice");
    runtimeService.correlateMessage("cancelDraft");
    completeAs("bob", task(pi, "redraft"));

    // alice held draft but did not complete it
    completeAs("alice", task(pi, "approve"));
  }

  // status

  @Test
  public void shouldReportTheStatusOfATask() {
    testRule.deploy(draftReviewApprove());
    String pi = start("fourEyes");
    completeAs("alice", task(pi, "draft"));
    completeAs("bob", task(pi, "review"));
    Task approve = task(pi, "approve");
    taskService.setAssignee(approve.getId(), "bob");

    identityService.setAuthenticatedUserId("alice");
    FourEyesStatus status = fourEyes.getStatus(approve.getId());
    identityService.clearAuthentication();

    assertThat(status.isEnabled()).isTrue();
    assertThat(status.isGuarded()).isTrue();
    assertThat(status.getEarlierSteps()).extracting(FourEyesStatus.Step::getId).containsExactly("draft", "review");
    assertThat(status.getEarlierSteps()).extracting(FourEyesStatus.Step::getName).containsExactly("Draft", "Review");
    assertThat(status.getUserId()).isEqualTo("alice");
    assertThat(status.isUserExcluded()).isTrue();
    assertThat(status.getUserSteps()).containsExactly("draft");
    assertThat(status.getAssignee()).isEqualTo("bob");
    assertThat(status.isAssigneeExcluded()).isTrue();
    assertThat(status.getAssigneeSteps()).containsExactly("review");
    assertThat(status.isBlocked()).isTrue();

    taskService.setAssignee(approve.getId(), "carol");
    identityService.setAuthenticatedUserId("carol");
    status = fourEyes.getStatus(approve.getId());
    identityService.clearAuthentication();
    assertThat(status.isUserExcluded()).isFalse();
    assertThat(status.isAssigneeExcluded()).isFalse();
    assertThat(status.isBlocked()).isFalse();
  }

  @Test
  public void shouldReportTasksWithoutRule() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");

    FourEyesStatus status = fourEyes.getStatus(task(pi, "draft").getId());

    assertThat(status.isEnabled()).isTrue();
    assertThat(status.isGuarded()).isFalse();
    assertThat(status.isBlocked()).isFalse();
    assertThat(status.getEarlierSteps()).isEmpty();
  }

  @Test
  public void shouldReportAGuardedTaskBeforeAnyCheckedStepIsDone() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent().parallelGateway("fork")
        .userTask("draft").camundaCandidateUsers(CANDIDATES).endEvent()
        .moveToNode("fork")
        .userTask("approve").camundaCandidateUsers(CANDIDATES).endEvent()
        .done(), "approve", "draft"));
    String pi = start("fourEyes");

    FourEyesStatus status = fourEyes.getStatus(task(pi, "approve").getId());

    assertThat(status.isGuarded()).isTrue();
    assertThat(status.isBlocked()).isFalse();
  }

  @Test
  public void shouldNeedReadPermissionOnTheTaskForTheStatus() {
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    String taskId = task(pi, "draft").getId();
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthenticatedUserId("mallory");
    try {
      assertThatThrownBy(() -> fourEyes.getStatus(taskId)).isInstanceOf(AuthorizationException.class)
          .isNotInstanceOf(FourEyesViolationException.class);

      configuration.setAuthorizationEnabled(false);
      Authorization read = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
      read.setUserId("mallory");
      read.setResource(Resources.TASK);
      read.setResourceId(taskId);
      read.addPermission(Permissions.READ);
      authorizationService.saveAuthorization(read);
      configuration.setAuthorizationEnabled(true);

      assertThat(fourEyes.getStatus(taskId).getUserId()).isEqualTo("mallory");
    } finally {
      identityService.clearAuthentication();
      configuration.setAuthorizationEnabled(false);
    }
  }

  @Test
  @RequiredHistoryLevel(ProcessEngineConfiguration.HISTORY_ACTIVITY)
  public void shouldIgnoreTheAuthorizationsOfTheUserWhenLookingForEarlierSteps() {
    // alice may work on the tasks but not read the history or the variables; she still counts,
    // also when only the history knows that she drafted
    testRule.deploy(draftApprove());
    String pi = start("fourEyes");
    for (Task task : taskService.createTaskQuery().processInstanceId(pi).list()) {
      grantTaskWork("alice", task.getId());
    }
    configuration.setAuthorizationEnabled(true);
    try {
      completeAs("alice", task(pi, "draft"));
      // approve was created with authorization checks on: its candidate users may work on it
      configuration.setAuthorizationEnabled(false);
      runtimeService.removeVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft");
      Task approve = task(pi, "approve");
      configuration.setAuthorizationEnabled(true);
      assertRefused("alice", approve);
    } finally {
      configuration.setAuthorizationEnabled(false);
    }
  }

  void grantTaskWork(String userId, String taskId) {
    Authorization authorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    authorization.setUserId(userId);
    authorization.setResource(Resources.TASK);
    authorization.setResourceId(taskId);
    authorization.addPermission(Permissions.READ);
    authorization.addPermission(Permissions.UPDATE);
    authorizationService.saveAuthorization(authorization);
  }

  @Test
  public void shouldReportAMissingTask() {
    assertThatThrownBy(() -> fourEyes.getStatus("unknown")).isInstanceOf(NotFoundException.class);
  }

  // deployment

  @Test
  public void shouldRefuseAnUnknownStep() {
    BpmnModelInstance model = rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent().userTask("approve").endEvent().done(), "approve", "draft");

    assertThatThrownBy(() -> testRule.deploy(model))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("fasti:fourEyes of user task 'approve'")
        .hasMessageContaining("'draft' is not an element of process 'fourEyes'")
        .satisfies(e -> assertThat(((ParseException) e).getResorceReports().get(0).getErrors().get(0).getMainElementId())
            .isEqualTo("approve"));
  }

  @Test
  public void shouldRefuseAStepThatIsNoUserTask() {
    BpmnModelInstance model = rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent().serviceTask("draft").camundaExpression("${true}").userTask("approve").endEvent().done(),
        "approve", "draft");

    assertThatThrownBy(() -> testRule.deploy(model))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("'draft' is not a user task");
  }

  @Test
  public void shouldRefuseAnEmptyRule() {
    BpmnModelInstance model = rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent().userTask("approve").endEvent().done(), "approve", " , ");

    assertThatThrownBy(() -> testRule.deploy(model))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("it names no user task");
  }

  @Test
  public void shouldRefuseAStepOfAnotherProcess() {
    BpmnModelInstance model = rule(Bpmn.createExecutableProcess("child").camundaHistoryTimeToLive(180)
        .startEvent().userTask("approve").endEvent().done(), "approve", "draft");

    assertThatThrownBy(() -> testRule.deploy(Bpmn.createExecutableProcess("parent").camundaHistoryTimeToLive(180)
        .startEvent().userTask("draft").callActivity().calledElement("child").endEvent().done(), model))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("'draft' is not an element of process 'child'");
  }
}
