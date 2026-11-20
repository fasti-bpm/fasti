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
package dev.fasti.engine.absence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import dev.fasti.engine.absence.AbsenceService.Handover;
import dev.fasti.engine.absence.impl.AbsenceStore;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.IdentityLinkType;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class AbsenceSubstitutionTest {

  static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl configuration;
  protected IdentityService identityService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected HistoryService historyService;
  protected AuthorizationService authorizationService;
  protected AbsenceService absences;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    historyService = engineRule.getHistoryService();
    authorizationService = engineRule.getAuthorizationService();
    absences = Absences.of(engineRule.getProcessEngine());
    ClockUtil.setCurrentTime(Date.from(NOW));
    for (String id : List.of("alice", "bob", "carol", "dave")) {
      User user = identityService.newUser(id);
      user.setFirstName(id.substring(0, 1).toUpperCase() + id.substring(1));
      identityService.saveUser(user);
    }
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
    for (Task task : taskService.createTaskQuery().list()) {
      if (task.getProcessInstanceId() == null && task.getCaseInstanceId() == null) {
        taskService.deleteTask(task.getId(), true);
      }
    }
    for (User user : identityService.createUserQuery().list()) {
      // also removes the user info, and with it the absences
      identityService.deleteUser(user.getId());
    }
    ClockUtil.reset();
  }

  static Date at(Duration offset) {
    return Date.from(NOW.plus(offset));
  }

  static BpmnModelInstance userTask(String assignee) {
    return Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaAssignee(assignee).endEvent().done();
  }

  static BpmnModelInstance candidateTask(String candidateUsers) {
    return Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaCandidateUsers(candidateUsers).endEvent().done();
  }

  Task startAndGetTask() {
    String processInstanceId = runtimeService.startProcessInstanceByKey("process").getId();
    return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
  }

  List<String> comments(Task task) {
    return taskService.getTaskComments(task.getId()).stream().map(Comment::getFullMessage).collect(Collectors.toList());
  }

  void setAbsence(String user, String substitute) {
    absences.setAbsence(user, at(Duration.ofHours(-1)), at(Duration.ofDays(1)), substitute, null);
  }

  // engine behaviour ///////////////////////////////////////////////////////

  @Test
  public void shouldAssignTaskOfAbsentAssigneeToSubstitute() {
    absences.setAbsence("alice", at(Duration.ofHours(-1)), at(Duration.ofDays(1)), "bob", "Handover notes in the wiki");
    testRule.deploy(userTask("alice"));

    Task task = startAndGetTask();

    assertThat(task.getAssignee()).isEqualTo("bob");
    assertThat(taskService.getVariableLocal(task.getId(), AbsenceService.VARIABLE_ABSENT_ASSIGNEE)).isEqualTo("alice");
    assertThat(taskService.getVariableLocal(task.getId(), AbsenceService.VARIABLE_SUBSTITUTE)).isEqualTo("bob");
    assertThat(comments(task)).containsExactly("Assigned to bob as substitute for alice, who is absent.");
    // queryable
    assertThat(taskService.createTaskQuery().taskVariableValueEquals(AbsenceService.VARIABLE_ABSENT_ASSIGNEE, "alice").count())
        .isEqualTo(1);
    assertThat(historyService.createHistoricTaskInstanceQuery().taskId(task.getId()).singleResult().getAssignee())
        .isEqualTo("bob");
    assertThat(historyService.createHistoricVariableInstanceQuery().variableName(AbsenceService.VARIABLE_ABSENT_ASSIGNEE)
        .singleResult().getValue()).isEqualTo("alice");
  }

  @Test
  public void shouldNotSubstituteBeforeOrAfterTheAbsence() {
    absences.setAbsence("alice", at(Duration.ofDays(1)), at(Duration.ofDays(2)), "bob", null);
    testRule.deploy(userTask("alice"));

    assertThat(startAndGetTask().getAssignee()).isEqualTo("alice");

    ClockUtil.setCurrentTime(at(Duration.ofDays(2)));
    Task task = startAndGetTask();
    assertThat(task.getAssignee()).isEqualTo("alice");
    assertThat(taskService.getVariablesLocal(task.getId())).isEmpty();

    ClockUtil.setCurrentTime(at(Duration.ofDays(1)));
    assertThat(startAndGetTask().getAssignee()).isEqualTo("bob");
  }

  @Test
  public void shouldSubstituteWhenAssignedThroughTheApi() {
    setAbsence("alice", "bob");
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").endEvent().done());
    Task task = startAndGetTask();
    assertThat(task.getAssignee()).isNull();

    identityService.setAuthenticatedUserId("dave");
    taskService.setAssignee(task.getId(), "alice");
    identityService.clearAuthentication();

    assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee()).isEqualTo("bob");
    UserOperationLogEntry entry = historyService.createUserOperationLogQuery().taskId(task.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_ASSIGN).property("assignee").singleResult();
    assertThat(entry.getUserId()).isEqualTo("dave");
    assertThat(entry.getNewValue()).isEqualTo("bob");
    assertThat(comments(task)).containsExactly("Assigned to bob as substitute for alice, who is absent.");
  }

  @Test
  public void shouldLetTheAbsentUserClaimATaskThemselves() {
    setAbsence("alice", "bob");
    testRule.deploy(candidateTask("alice,dave"));
    Task task = startAndGetTask();

    identityService.setAuthenticatedUserId("alice");
    taskService.claim(task.getId(), "alice");
    identityService.clearAuthentication();

    assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee()).isEqualTo("alice");
  }

  @Test
  public void shouldFollowAbsentSubstitutes() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    testRule.deploy(userTask("alice"));

    Task task = startAndGetTask();

    assertThat(task.getAssignee()).isEqualTo("carol");
    assertThat(taskService.getVariableLocal(task.getId(), AbsenceService.VARIABLE_ABSENT_ASSIGNEE)).isEqualTo("alice");
    assertThat(taskService.getVariableLocal(task.getId(), AbsenceService.VARIABLE_SUBSTITUTE)).isEqualTo("carol");
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("carol");
    assertThat(absences.resolveSubstitute("carol")).isNull();
  }

  @Test
  public void shouldKeepTheTaskWhenSubstitutesLeadBack() {
    // stored around the validation, as a concurrent change or an older engine could leave it
    store(new Absence("alice", at(Duration.ofHours(-1)), at(Duration.ofDays(1)), "bob", null));
    store(new Absence("bob", at(Duration.ofHours(-1)), at(Duration.ofDays(1)), "alice", null));
    testRule.deploy(userTask("alice"));

    Task task = startAndGetTask();

    assertThat(task.getAssignee()).isEqualTo("alice");
    assertThat(absences.resolveSubstitute("alice")).isNull();
  }

  @Test
  public void shouldKeepTheTaskWhenTheSubstituteWasDeleted() {
    setAbsence("alice", "bob");
    identityService.deleteUser("bob");
    testRule.deploy(userTask("alice"));

    assertThat(startAndGetTask().getAssignee()).isEqualTo("alice");
  }

  @Test
  public void shouldStopAfterMaxHops() {
    for (String id : List.of("u1", "u2", "u3", "u4", "u5", "u6")) {
      identityService.saveUser(identityService.newUser(id));
    }
    setAbsence("alice", "u1");
    setAbsence("u1", "u2");
    setAbsence("u2", "u3");
    setAbsence("u3", "u4");
    setAbsence("u4", "u5");
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("u5");
    setAbsence("u5", "u6");
    // six users in a row are more than the default of five
    assertThat(absences.resolveSubstitute("alice")).isNull();
  }

  @Test
  public void shouldRemoveTheMarksWhenTheTaskMovesOn() {
    setAbsence("alice", "bob");
    testRule.deploy(userTask("alice"));
    Task task = startAndGetTask();
    assertThat(task.getAssignee()).isEqualTo("bob");

    taskService.setAssignee(task.getId(), "dave");

    assertThat(taskService.getVariablesLocal(task.getId())).isEmpty();
    assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee()).isEqualTo("dave");
  }

  @Test
  public void shouldGiveTheTaskBackOnceTheAbsenceIsOver() {
    setAbsence("alice", "bob");
    testRule.deploy(userTask("alice"));
    Task task = startAndGetTask();

    absences.clearAbsence("alice");
    taskService.setAssignee(task.getId(), "alice");

    assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee()).isEqualTo("alice");
    assertThat(taskService.getVariablesLocal(task.getId())).isEmpty();
  }

  @Test
  public void shouldAddSubstitutesOfAbsentCandidateUsers() {
    setAbsence("alice", "bob");
    testRule.deploy(candidateTask("alice,dave"));

    Task task = startAndGetTask();

    List<String> candidates = taskService.getIdentityLinksForTask(task.getId()).stream()
        .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
        .map(IdentityLink::getUserId).collect(Collectors.toList());
    assertThat(candidates).containsExactlyInAnyOrder("alice", "dave", "bob");
    assertThat(task.getAssignee()).isNull();
    assertThat(comments(task)).containsExactly("Candidate user bob added as substitute for alice, who is absent.");
    assertThat(taskService.createTaskQuery().taskCandidateUser("bob").count()).isEqualTo(1);
  }

  @Test
  public void shouldNotAddACandidateTwice() {
    setAbsence("alice", "bob");
    testRule.deploy(candidateTask("alice,bob"));

    Task task = startAndGetTask();

    assertThat(taskService.getIdentityLinksForTask(task.getId())).hasSize(2);
    assertThat(comments(task)).isEmpty();
  }

  @Test
  public void shouldSubstituteInCmmnHumanTasks() {
    setAbsence("alice", "bob");
    testRule.deploy("dev/fasti/engine/absence/absentAssigneeCase.cmmn");

    String caseInstanceId = engineRule.getCaseService().createCaseInstanceByKey("absentAssigneeCase").getId();

    Task task = taskService.createTaskQuery().caseInstanceId(caseInstanceId).singleResult();
    assertThat(task.getAssignee()).isEqualTo("bob");
    assertThat(taskService.getVariableLocal(task.getId(), AbsenceService.VARIABLE_ABSENT_ASSIGNEE)).isEqualTo("alice");
  }

  @Test
  public void shouldLeaveStandaloneTasksAlone() {
    setAbsence("alice", "bob");
    Task task = taskService.newTask();
    task.setAssignee("alice");
    taskService.saveTask(task);

    assertThat(taskService.createTaskQuery().taskId(task.getId()).singleResult().getAssignee()).isEqualTo("alice");
  }

  // validation /////////////////////////////////////////////////////////////

  @Test
  public void shouldStoreTheAbsenceAsUserInfo() {
    Absence absence = absences.setAbsence("alice", at(Duration.ofHours(1)), at(Duration.ofDays(3)), "bob", "  On leave  ");

    assertThat(absence.getNote()).isEqualTo("On leave");
    assertThat(absences.getAbsence("alice")).isEqualTo(absence);
    assertThat(absence.getStatus(Date.from(NOW))).isEqualTo(Absence.Status.UPCOMING);
    assertThat(identityService.getUserInfo("alice", AbsenceStore.KEY_UNTIL)).isEqualTo("2026-11-24T08:00:00Z");
    assertThat(identityService.getUserInfo("alice", AbsenceStore.KEY_SUBSTITUTE)).isEqualTo("bob");

    // replacing keeps one row per key
    absences.setAbsence("alice", null, at(Duration.ofDays(1)), "carol", null);
    assertThat(identityService.getUserInfoKeys("alice")).containsExactlyInAnyOrder(
        AbsenceStore.KEY_FROM, AbsenceStore.KEY_UNTIL, AbsenceStore.KEY_SUBSTITUTE);
    assertThat(absences.getAbsence("alice").getFrom()).isEqualTo(Date.from(NOW));

    absences.clearAbsence("alice");
    assertThat(absences.getAbsence("alice")).isNull();
    assertThat(identityService.getUserInfoKeys("alice")).isEmpty();
  }

  @Test
  public void shouldSeeItsOwnChangesWithinOneCommand() {
    Absence inner = configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      // nested service calls share the command context with its entity cache
      setAbsence("alice", "bob");
      Absence read = absences.getAbsence("alice");
      assertThat(absences.getAbsencesSubstitutedBy("bob")).extracting(Absence::getUserId).containsExactly("alice");
      absences.setAbsence("alice", null, at(Duration.ofDays(2)), "carol", null);
      assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("carol");
      absences.clearAbsence("alice");
      assertThat(absences.getAbsence("alice")).isNull();
      return read;
    });

    assertThat(inner.getSubstituteId()).isEqualTo("bob");
    assertThat(absences.getAbsence("alice")).isNull();
    assertThat(identityService.getUserInfoKeys("alice")).isEmpty();
  }

  @Test
  public void shouldKeepOtherUserInfo() {
    identityService.setUserInfo("alice", "phone", "123");
    setAbsence("alice", "bob");
    absences.clearAbsence("alice");

    assertThat(identityService.getUserInfo("alice", "phone")).isEqualTo("123");
  }

  @Test
  public void shouldRejectInvalidAbsences() {
    Date from = at(Duration.ofHours(-1));
    Date until = at(Duration.ofDays(1));
    assertThatThrownBy(() -> absences.setAbsence("alice", from, until, "alice", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("own substitute");
    assertThatThrownBy(() -> absences.setAbsence("alice", from, until, "nobody", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("does not exist");
    assertThatThrownBy(() -> absences.setAbsence("nobody", from, until, "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("does not exist");
    assertThatThrownBy(() -> absences.setAbsence("alice", at(Duration.ofDays(2)), until, "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("after its start");
    assertThatThrownBy(() -> absences.setAbsence("alice", at(Duration.ofDays(-3)), at(Duration.ofDays(-1)), "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("in the future");
    assertThatThrownBy(() -> absences.setAbsence("alice", from, null, "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("required");
    assertThatThrownBy(() -> absences.setAbsence("alice", from, until, null, null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("substitute is required");
    assertThatThrownBy(() -> absences.setAbsence("alice", from, until, "bob", "x".repeat(256)))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("255");
    assertThat(absences.getAbsence("alice")).isNull();
  }

  @Test
  public void shouldRejectSubstitutesThatLeadBack() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");

    assertThatThrownBy(() -> absences.setAbsence("carol", at(Duration.ofHours(2)), at(Duration.ofDays(2)), "alice", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("lead back");
    assertThatThrownBy(() -> absences.setAbsence("bob", null, at(Duration.ofDays(2)), "alice", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("lead back");

    // not at the same time: fine
    absences.setAbsence("carol", at(Duration.ofDays(5)), at(Duration.ofDays(6)), "alice", null);
  }

  // handover and overview //////////////////////////////////////////////////

  @Test
  public void shouldHandOverOpenTasks() {
    testRule.deploy(userTask("alice"));
    Task first = startAndGetTask();
    Task second = startAndGetTask();
    Task suspended = startAndGetTask();
    runtimeService.suspendProcessInstanceById(suspended.getProcessInstanceId());
    Task standalone = taskService.newTask();
    standalone.setAssignee("alice");
    taskService.saveTask(standalone);
    // upcoming absence: hand over the day before
    absences.setAbsence("alice", at(Duration.ofDays(1)), at(Duration.ofDays(5)), "bob", null);

    identityService.setAuthenticatedUserId("alice");
    Handover handover = absences.handOverTasks("alice", 100);
    identityService.clearAuthentication();

    assertThat(handover.getSubstituteId()).isEqualTo("bob");
    assertThat(handover.getHandedOver()).isEqualTo(3);
    assertThat(handover.getRemaining()).isEqualTo(1);
    assertThat(taskService.createTaskQuery().taskAssignee("bob").list()).extracting(Task::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId(), standalone.getId());
    assertThat(taskService.createTaskQuery().taskAssignee("alice").singleResult().getId()).isEqualTo(suspended.getId());
    assertThat(taskService.getVariableLocal(first.getId(), AbsenceService.VARIABLE_SUBSTITUTE)).isEqualTo("bob");
    assertThat(comments(first)).containsExactly(
        "Handed over to bob as substitute for alice, who is absent from 2026-11-22T08:00:00Z until 2026-11-26T08:00:00Z.");
    assertThat(historyService.createUserOperationLogQuery().taskId(first.getId())
        .operationType(UserOperationLogEntry.OPERATION_TYPE_ASSIGN).singleResult().getUserId()).isEqualTo("alice");
  }

  @Test
  public void shouldHandOverInBatches() {
    testRule.deploy(userTask("alice"));
    startAndGetTask();
    startAndGetTask();
    startAndGetTask();
    setAbsence("alice", "bob");

    Handover handover = absences.handOverTasks("alice", 2);

    assertThat(handover.getHandedOver()).isEqualTo(2);
    assertThat(handover.getRemaining()).isEqualTo(1);
    assertThat(absences.handOverTasks("alice", 2).getHandedOver()).isEqualTo(1);
  }

  @Test
  public void shouldHandOverToAPresentSubstitute() {
    testRule.deploy(userTask("dave"));
    startAndGetTask();
    setAbsence("alice", "bob");
    setAbsence("dave", "alice");

    // alice, the substitute of dave, is absent herself: bob gets the task
    assertThat(absences.handOverTasks("dave", 10).getSubstituteId()).isEqualTo("bob");
  }

  @Test
  public void shouldRefuseHandoverWithoutAbsence() {
    assertThatThrownBy(() -> absences.handOverTasks("alice", 10))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("no current or upcoming absence");
  }

  @Test
  public void shouldHandOverTasksOfEveryTenant() {
    testRule.deployForTenant("tenant1", userTask("alice"));
    String processInstanceId = runtimeService.createProcessInstanceByKey("process").processDefinitionTenantId("tenant1")
        .execute().getId();
    setAbsence("alice", "bob");

    identityService.setAuthentication("alice", null, null);
    assertThat(absences.handOverTasks("alice", 10).getHandedOver()).isEqualTo(1);
    identityService.clearAuthentication();
    assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getAssignee())
        .isEqualTo("bob");
  }

  @Test
  public void shouldListTheAbsencesASubstituteCoversNow() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    absences.setAbsence("dave", at(Duration.ofDays(2)), at(Duration.ofDays(3)), "carol", null);

    assertThat(absences.getAbsencesSubstitutedBy("carol")).extracting(Absence::getUserId)
        .containsExactlyInAnyOrder("alice", "bob");
    assertThat(absences.getAbsencesSubstitutedBy("bob")).isEmpty();
    assertThat(absences.getAbsencesSubstitutedBy("dave")).isEmpty();
  }

  // authorization //////////////////////////////////////////////////////////

  @Test
  public void shouldLetUsersManageTheirOwnAbsenceOnly() {
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("alice", null);

    setAbsence("alice", "bob");
    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
    assertThatThrownBy(() -> setAbsence("dave", "bob")).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> absences.getAbsence("dave")).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> absences.clearAbsence("dave")).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> absences.handOverTasks("dave", 10)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> absences.getAbsencesSubstitutedBy("dave")).isInstanceOf(AuthorizationException.class);
    absences.clearAbsence("alice");
  }

  @Test
  public void shouldLetUserAdministratorsManageAbsencesOfOthers() {
    Authorization grant = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId("carol");
    grant.setResource(Resources.USER);
    grant.setResourceId("dave");
    grant.addPermission(Permissions.READ);
    grant.addPermission(Permissions.UPDATE);
    authorizationService.saveAuthorization(grant);
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("carol", null);

    setAbsence("dave", "bob");

    assertThat(absences.getAbsence("dave").getSubstituteId()).isEqualTo("bob");
    assertThatThrownBy(() -> setAbsence("alice", "bob")).isInstanceOf(AuthorizationException.class);
  }

  void store(Absence absence) {
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      AbsenceStore.save(commandContext, absence);
      return null;
    });
  }
}
