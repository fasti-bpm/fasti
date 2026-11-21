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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import ch.qos.logback.classic.Level;
import dev.fasti.engine.absence.AbsenceService.Handover;
import dev.fasti.engine.absence.impl.AbsenceStore;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.commons.testing.ProcessEngineLoggingRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * What a caller learns and may do through the absence service (other users, their absences, the
 * tasks handed over), and how often the service reads the user info table.
 */
public class AbsenceAccessAndLookupTest {

  static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");
  static final String IDENTITY_INFO_LOGGER = "org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity";

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Rule
  public ProcessEngineLoggingRule loggingRule = new ProcessEngineLoggingRule().watch(IDENTITY_INFO_LOGGER, Level.DEBUG);

  protected ProcessEngineConfigurationImpl configuration;
  protected IdentityService identityService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected AuthorizationService authorizationService;
  protected AbsenceService absences;
  protected Permission defaultTaskPermission;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    authorizationService = engineRule.getAuthorizationService();
    absences = Absences.of(engineRule.getProcessEngine());
    defaultTaskPermission = configuration.getDefaultUserPermissionForTask();
    ClockUtil.setCurrentTime(Date.from(NOW));
    for (String id : List.of("alice", "bob", "carol", "dave", "erin")) {
      User user = identityService.newUser(id);
      identityService.saveUser(user);
    }
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    configuration.setDefaultUserPermissionForTask(defaultTaskPermission);
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
    for (User user : identityService.createUserQuery().list()) {
      identityService.deleteUser(user.getId());
    }
    ClockUtil.reset();
  }

  static Date at(Duration offset) {
    return Date.from(NOW.plus(offset));
  }

  void setAbsence(String user, String substitute) {
    absences.setAbsence(user, at(Duration.ofHours(-1)), at(Duration.ofDays(1)), substitute, null);
  }

  void grant(String userId, Resource resource, String resourceId, Permission... permissions) {
    // a user has one grant per resource id (the engine creates one for the assignee of a task)
    Authorization grant = authorizationService.createAuthorizationQuery().userIdIn(userId)
        .authorizationType(Authorization.AUTH_TYPE_GRANT).resourceType(resource).resourceId(resourceId).singleResult();
    if (grant == null) {
      grant = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
      grant.setUserId(userId);
      grant.setResource(resource);
      grant.setResourceId(resourceId);
    }
    for (Permission permission : permissions) {
      grant.addPermission(permission);
    }
    authorizationService.saveAuthorization(grant);
  }

  static BpmnModelInstance userTask(String assignee) {
    return Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaAssignee(assignee).endEvent().done();
  }

  /** How many statements on ACT_ID_INFO ran since the log was last cleared. */
  long identityInfoStatements() {
    return loggingRule.getLog(IDENTITY_INFO_LOGGER).stream()
        .filter(event -> event.getFormattedMessage().contains("Preparing:")).count();
  }

  void clearStatementLog() {
    loggingRule.getLog(IDENTITY_INFO_LOGGER).clear();
  }

  // what setting an absence tells about other users ////////////////////////

  @Test
  public void shouldNotTellWhetherASubstituteExistsThatTheCallerMayNotSee() {
    grant("alice", Resources.USER, "bob", Permissions.READ);
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("alice", null);

    // bob may be seen
    setAbsence("alice", "bob");
    // carol exists but may not be seen, nobody does not exist: the same answer
    Throwable hidden = catchThrowable(() -> setAbsence("alice", "carol"));
    Throwable missing = catchThrowable(() -> setAbsence("alice", "nobody"));

    assertThat(hidden).isInstanceOf(BadUserRequestException.class)
        .hasMessage("The substitute 'carol' cannot be chosen: there is no such user you may see, or tasks would "
            + "come back to 'alice' through the substitutes of absent substitutes. Choose someone else.");
    assertThat(missing).isInstanceOf(BadUserRequestException.class)
        .hasMessage(hidden.getMessage().replace("'carol'", "'nobody'"));
    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
  }

  @Test
  public void shouldAnswerASubstituteThatLeadsBackLikeOneThatDoesNotExist() {
    setAbsence("bob", "alice");

    Throwable leadsBack = catchThrowable(() -> setAbsence("alice", "bob"));
    Throwable missing = catchThrowable(() -> setAbsence("alice", "nobody"));

    assertThat(leadsBack).isInstanceOf(BadUserRequestException.class)
        .hasMessage(missing.getMessage().replace("'nobody'", "'bob'"));
    // nothing about when bob is away or who stands in for him
    assertThat(leadsBack.getMessage()).doesNotContain("absent at").doesNotContain("2026");
  }

  @Test
  public void shouldOnlyFollowAbsentSubstitutesTheCallerMaySee() {
    // alice -> bob (absent) -> carol
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    grant("alice", Resources.USER, "bob", Permissions.READ);
    grant("dave", Resources.USER, "alice", Permissions.READ);
    configuration.setAuthorizationEnabled(true);

    // alice may see bob, but not carol: whether carol is there is not hers to know
    identityService.setAuthentication("alice", null);
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("bob");
    identityService.clearAuthentication();
    // she may see both: the answer follows the absence of bob
    grant("alice", Resources.USER, "carol", Permissions.READ);
    identityService.setAuthentication("alice", null);
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("carol");
    // dave may see alice but not bob: the substitute of alice, nothing about bob
    identityService.setAuthentication("dave", null);
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("bob");
    identityService.clearAuthentication();

    // the same answer whether bob is absent or not, or no longer exists
    absences.clearAbsence("bob");
    identityService.setAuthentication("dave", null);
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("bob");
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    identityService.deleteUser("bob");
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("dave", null);
    assertThat(absences.resolveSubstitute("alice")).isEqualTo("bob");
    identityService.clearAuthentication();
    // the engine itself (assigning tasks) is not restricted
    configuration.setAuthorizationEnabled(false);
    assertThat(absences.resolveSubstitute("alice")).isNull();
  }

  // cycles /////////////////////////////////////////////////////////////////

  @Test
  public void shouldAcceptSubstitutesWhoAreNeverAbsentTogether() {
    // bob -> carol on days 1-4, carol -> alice on days 6-10: bob and carol are never away together,
    // so tasks of alice (days 1-10) never come back to her
    absences.setAbsence("bob", at(Duration.ofDays(1)), at(Duration.ofDays(4)), "carol", null);
    absences.setAbsence("carol", at(Duration.ofDays(6)), at(Duration.ofDays(10)), "alice", null);

    absences.setAbsence("alice", at(Duration.ofDays(1)), at(Duration.ofDays(10)), "bob", null);

    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
    // with carol away while bob is, they do come back
    absences.clearAbsence("alice");
    absences.setAbsence("carol", at(Duration.ofDays(3)), at(Duration.ofDays(10)), "alice", null);
    assertThatThrownBy(() -> absences.setAbsence("alice", at(Duration.ofDays(1)), at(Duration.ofDays(10)), "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("cannot be chosen");
  }

  @Test
  public void shouldIgnoreThePastOfAnAbsenceThatHasBegun() {
    // bob was away (substitute alice) until an hour ago; alice, away since yesterday, may name bob
    absences.setAbsence("bob", at(Duration.ofDays(-2)), at(Duration.ofMinutes(1)), "alice", null);
    ClockUtil.setCurrentTime(at(Duration.ofHours(1)));

    absences.setAbsence("alice", at(Duration.ofDays(-1)), at(Duration.ofDays(3)), "bob", null);

    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
  }

  // handover ///////////////////////////////////////////////////////////////

  @Test
  public void shouldNotHandOverTasksTheCallerMayNotAssign() {
    // a hardened setup: assignees may work on their tasks but not reassign them
    configuration.setDefaultUserPermissionForTask(Permissions.TASK_WORK);
    configuration.setAuthorizationEnabled(true);
    testRule.deploy(userTask("alice"));
    String first = runtimeService.startProcessInstanceByKey("process").getId();
    String second = runtimeService.startProcessInstanceByKey("process").getId();
    Task assignable = taskService.createTaskQuery().processInstanceId(second).singleResult();
    grant("alice", Resources.TASK, assignable.getId(), Permissions.TASK_ASSIGN);
    grant("alice", Resources.USER, "bob", Permissions.READ);
    identityService.setAuthentication("alice", null);
    setAbsence("alice", "bob");

    // delegating is refused by the task API, and so is handing over
    String firstTask = withoutAuthorization(() -> taskService.createTaskQuery().processInstanceId(first).singleResult().getId());
    assertThatThrownBy(() -> taskService.setAssignee(firstTask, "bob"))
        .isInstanceOf(org.camunda.bpm.engine.AuthorizationException.class);
    Handover handover = absences.handOverTasks("alice", 100);
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);

    assertThat(handover.getHandedOver()).isEqualTo(1);
    assertThat(handover.getNotAllowed()).isEqualTo(1);
    assertThat(handover.getRemaining()).isEqualTo(1);
    assertThat(taskService.createTaskQuery().taskId(firstTask).singleResult().getAssignee()).isEqualTo("alice");
    assertThat(taskService.createTaskQuery().taskId(assignable.getId()).singleResult().getAssignee()).isEqualTo("bob");
  }

  @Test
  public void shouldReachTasksBehindTheOnesTheCallerMayNotAssign() {
    configuration.setDefaultUserPermissionForTask(Permissions.TASK_WORK);
    configuration.setAuthorizationEnabled(true);
    testRule.deploy(userTask("alice"));
    for (int i = 0; i < 3; i++) {
      ClockUtil.setCurrentTime(at(Duration.ofSeconds(i * 10)));
      runtimeService.startProcessInstanceByKey("process");
    }
    ClockUtil.setCurrentTime(at(Duration.ofMinutes(5)));
    // only the newest task may be assigned
    List<Task> tasks = taskService.createTaskQuery().orderByTaskCreateTime().asc().list();
    grant("alice", Resources.TASK, tasks.get(2).getId(), Permissions.TASK_ASSIGN);
    grant("alice", Resources.USER, "bob", Permissions.READ);
    identityService.setAuthentication("alice", null);
    setAbsence("alice", "bob");

    Handover handover = absences.handOverTasks("alice", 1);
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);

    assertThat(handover.getHandedOver()).isEqualTo(1);
    assertThat(handover.getNotAllowed()).isEqualTo(2);
    assertThat(taskService.createTaskQuery().taskId(tasks.get(2).getId()).singleResult().getAssignee()).isEqualTo("bob");
  }

  @Test
  public void shouldLetAdministratorsHandOverEveryTask() {
    configuration.setDefaultUserPermissionForTask(Permissions.TASK_WORK);
    configuration.setAuthorizationEnabled(true);
    testRule.deploy(userTask("alice"));
    runtimeService.startProcessInstanceByKey("process");
    configuration.setAuthorizationEnabled(false);
    setAbsence("alice", "bob");
    configuration.setAuthorizationEnabled(true);

    // erin is an administrator (by group) who may change alice, without a permission on the task
    grant("erin", Resources.USER, "alice", Permissions.UPDATE);
    identityService.setAuthentication("erin", List.of(Groups.CAMUNDA_ADMIN));
    Handover handover = absences.handOverTasks("alice", 100);
    identityService.clearAuthentication();

    assertThat(handover.getHandedOver()).isEqualTo(1);
    assertThat(handover.getNotAllowed()).isZero();
  }

  <T> T withoutAuthorization(java.util.function.Supplier<T> action) {
    configuration.setAuthorizationEnabled(false);
    try {
      return action.get();
    } finally {
      configuration.setAuthorizationEnabled(true);
    }
  }

  // lookups ////////////////////////////////////////////////////////////////

  @Test
  public void shouldListTheSubstitutedAbsencesWithOneStatement() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    setAbsence("dave", "carol");
    absences.setAbsence("erin", at(Duration.ofDays(2)), at(Duration.ofDays(3)), "carol", null);
    clearStatementLog();

    List<Absence> covered = absences.getAbsencesSubstitutedBy("carol");

    assertThat(covered).extracting(Absence::getUserId).containsExactlyInAnyOrder("alice", "bob", "dave");
    // not one statement per absent user and per substitute on the way
    assertThat(identityInfoStatements()).isEqualTo(1);
  }

  @Test
  public void shouldFollowAChainOfSubstitutesWithTwoStatements() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    setAbsence("carol", "dave");
    testRule.deploy(userTask("alice"));
    clearStatementLog();

    String pi = runtimeService.startProcessInstanceByKey("process").getId();

    assertThat(taskService.createTaskQuery().processInstanceId(pi).singleResult().getAssignee()).isEqualTo("dave");
    // alice alone, then all absences at once: not one statement per substitute on the way
    assertThat(identityInfoStatements()).isEqualTo(2);
  }

  @Test
  public void shouldLookUpManyAssigneesOfACommandWithTwoStatements() {
    setAbsence("carol", "erin");
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().parallelGateway("fork")
        .userTask("first").camundaAssignee("alice").endEvent("firstEnd")
        .moveToNode("fork").userTask("second").camundaAssignee("bob").endEvent("secondEnd")
        .moveToNode("fork").userTask("third").camundaAssignee("carol").endEvent("thirdEnd")
        .moveToNode("fork").userTask("fourth").camundaAssignee("dave").endEvent("fourthEnd")
        .done());
    clearStatementLog();

    String pi = runtimeService.startProcessInstanceByKey("process").getId();

    assertThat(taskService.createTaskQuery().processInstanceId(pi).list()).extracting(Task::getAssignee)
        .containsExactlyInAnyOrder("alice", "bob", "erin", "dave");
    // the first assignee alone, then all absences at once: not one statement per assignee
    assertThat(identityInfoStatements()).isEqualTo(2);
  }

  @Test
  public void shouldLookUpTheCandidatesOfATaskWithOneStatement() {
    setAbsence("alice", "erin");
    setAbsence("bob", "erin");
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaCandidateUsers("alice,bob,carol,dave").endEvent().done());
    clearStatementLog();

    String pi = runtimeService.startProcessInstanceByKey("process").getId();

    assertThat(identityInfoStatements()).isEqualTo(1);
    Task task = taskService.createTaskQuery().processInstanceId(pi).singleResult();
    assertThat(taskService.getIdentityLinksForTask(task.getId())).extracting(link -> link.getUserId())
        .containsExactlyInAnyOrder("alice", "bob", "carol", "dave", "erin");
  }

  @Test
  public void shouldLookUpAUserOncePerCommand() {
    setAbsence("alice", "bob");
    // two tasks for alice, created by one command
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().parallelGateway("fork")
        .userTask("first").camundaAssignee("alice").endEvent("firstEnd")
        .moveToNode("fork")
        .userTask("second").camundaAssignee("alice").endEvent("secondEnd")
        .done());
    clearStatementLog();

    String pi = runtimeService.startProcessInstanceByKey("process").getId();

    assertThat(taskService.createTaskQuery().processInstanceId(pi).taskAssignee("bob").count()).isEqualTo(2);
    // alice and bob, each once (not once per task)
    assertThat(identityInfoStatements()).isEqualTo(2);
  }

  @Test
  public void shouldRemoveEndedAbsencesWhenAnAbsenceIsWritten() {
    setAbsence("alice", "bob");
    setAbsence("bob", "carol");
    absences.setAbsence("dave", at(Duration.ofDays(5)), at(Duration.ofDays(6)), "carol", null);
    ClockUtil.setCurrentTime(at(Duration.ofDays(2)));

    // reading leaves them: the user can still see that the absence has ended
    assertThat(absences.getAbsence("alice").getStatus(ClockUtil.getCurrentTime())).isEqualTo(Absence.Status.ENDED);
    assertThat(identityService.getUserInfoKeys("alice")).isNotEmpty();

    // the next write of any absence removes the ended ones, and only those
    absences.setAbsence("carol", null, at(Duration.ofDays(3)), "erin", null);

    assertThat(identityService.getUserInfoKeys("alice")).isEmpty();
    assertThat(identityService.getUserInfoKeys("bob")).isEmpty();
    assertThat(absences.getAbsence("alice")).isNull();
    assertThat(absences.getAbsence("dave")).isNotNull();
    assertThat(absences.getAbsence("carol")).isNotNull();

    // clearing does the same
    ClockUtil.setCurrentTime(at(Duration.ofDays(7)));
    absences.clearAbsence("erin");
    assertThat(identityService.getUserInfoKeys("dave")).isEmpty();
    assertThat(identityService.getUserInfoKeys("carol")).isEmpty();
  }

  @Test
  public void shouldKeepOtherUserInfoWhenRemovingEndedAbsences() {
    identityService.setUserInfo("alice", "phone", "123");
    setAbsence("alice", "bob");
    assertThat(identityService.getUserInfoKeys("alice")).contains("phone", AbsenceStore.KEY_SUBSTITUTE);
    ClockUtil.setCurrentTime(at(Duration.ofDays(2)));

    absences.setAbsence("carol", null, at(Duration.ofDays(3)), "erin", null);

    assertThat(identityService.getUserInfoKeys("alice")).containsExactly("phone");
  }
}
