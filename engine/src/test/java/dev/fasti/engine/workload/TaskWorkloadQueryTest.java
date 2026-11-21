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
package dev.fasti.engine.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * The grouped workload counts equal what one task query per group and user returns, for a caller
 * with and without restrictions.
 */
public class TaskWorkloadQueryTest {

  static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl configuration;
  protected IdentityService identityService;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected AuthorizationService authorizationService;
  protected List<String> taskIds = new ArrayList<>();

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    identityService = engineRule.getIdentityService();
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    authorizationService = engineRule.getAuthorizationService();
    ClockUtil.setCurrentTime(Date.from(NOW));
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    configuration.setAuthorizationEnabled(false);
    for (String taskId : taskIds) {
      taskService.deleteTask(taskId, true);
    }
    for (Authorization authorization : authorizationService.createAuthorizationQuery().list()) {
      authorizationService.deleteAuthorization(authorization.getId());
    }
    ClockUtil.reset();
  }

  static Date at(Duration offset) {
    return Date.from(NOW.plus(offset));
  }

  /** A task without a process: created at the offset, with a due date, an assignee and candidate groups. */
  String task(Duration created, Duration due, String assignee, String... groups) {
    return task(null, created, due, assignee, groups);
  }

  String task(String tenantId, Duration created, Duration due, String assignee, String... groups) {
    ClockUtil.setCurrentTime(at(created));
    Task task = taskService.newTask();
    task.setName("task " + taskIds.size());
    task.setTenantId(tenantId);
    if (due != null) {
      task.setDueDate(at(due));
    }
    taskService.saveTask(task);
    taskIds.add(task.getId());
    for (String group : groups) {
      taskService.addCandidateGroup(task.getId(), group);
    }
    if (assignee != null) {
      taskService.setAssignee(task.getId(), assignee);
    }
    ClockUtil.setCurrentTime(Date.from(NOW));
    return task.getId();
  }

  void grant(String userId, Resource resource, String resourceId, Permission... permissions) {
    Authorization grant = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId(userId);
    grant.setResource(resource);
    grant.setResourceId(resourceId);
    for (Permission permission : permissions) {
      grant.addPermission(permission);
    }
    authorizationService.saveAuthorization(grant);
  }

  TaskWorkload read(List<String> groups, List<String> users) {
    return TaskWorkloads.query(engineRule.getProcessEngine()).groupIdIn(groups).userIdIn(users).read();
  }

  /** Compares every number with the task query that counts the same. */
  void assertMatchesTaskQueries(TaskWorkload workload) {
    Date now = Date.from(NOW);
    assertThat(workload.getTotals().getOpen()).isEqualTo(query().count());
    assertThat(workload.getTotals().getUnassigned()).isEqualTo(query().taskUnassigned().count());
    assertThat(workload.getTotals().getOverdue()).isEqualTo(query().dueBefore(now).count());
    assertThat(workload.getOrphaned()).isEqualTo(query().taskUnassigned().withoutCandidateGroups().count());
    for (TaskLoad group : workload.getGroups()) {
      String id = group.getId();
      assertThat(group.getOpen()).as("open of " + id)
          .isEqualTo(query().taskCandidateGroup(id).includeAssignedTasks().count());
      assertThat(group.getUnassigned()).as("unassigned of " + id).isEqualTo(query().taskCandidateGroup(id).count());
      assertThat(group.getOverdue()).as("overdue of " + id)
          .isEqualTo(query().taskCandidateGroup(id).includeAssignedTasks().dueBefore(now).count());
      assertThat(group.getOldest()).as("oldest of " + id)
          .isEqualTo(oldest(query().taskCandidateGroup(id).includeAssignedTasks()));
    }
    for (TaskLoad user : workload.getUsers()) {
      String id = user.getId();
      assertThat(user.getOpen()).as("open of " + id).isEqualTo(query().taskAssignee(id).count());
      assertThat(user.getUnassigned()).isZero();
      assertThat(user.getOverdue()).as("overdue of " + id).isEqualTo(query().taskAssignee(id).dueBefore(now).count());
      assertThat(user.getOldest()).as("oldest of " + id).isEqualTo(oldest(query().taskAssignee(id)));
    }
  }

  TaskQuery query() {
    return taskService.createTaskQuery();
  }

  static Date oldest(TaskQuery query) {
    List<Task> tasks = query.orderByTaskCreateTime().asc().listPage(0, 1);
    return tasks.isEmpty() ? null : tasks.get(0).getCreateTime();
  }

  void someTasks() {
    task(Duration.ofDays(-5), Duration.ofDays(-1), null, "sales");
    task(Duration.ofDays(-4), Duration.ofDays(2), "alice", "sales");
    task(Duration.ofDays(-3), Duration.ofHours(-2), "alice", "sales", "support");
    task(Duration.ofDays(-2), null, null, "support");
    task(Duration.ofDays(-1), Duration.ofDays(-3), "bob");
    task(Duration.ofHours(-6), null, null);
    task(Duration.ofHours(-5), Duration.ofMinutes(-1), null);
  }

  // counts ////////////////////////////////////////////////////////////////

  @Test
  public void shouldCountPerGroupAndPerUser() {
    someTasks();

    TaskWorkload workload = read(Arrays.asList("sales", "support", "idle"), Arrays.asList("alice", "bob", "carol"));

    assertThat(workload.getTotals().getOpen()).isEqualTo(7);
    assertThat(workload.getTotals().getUnassigned()).isEqualTo(4);
    assertThat(workload.getTotals().getOverdue()).isEqualTo(4);
    assertThat(workload.getTotals().getOldest()).isEqualTo(at(Duration.ofDays(-5)));
    assertThat(workload.getOrphaned()).isEqualTo(2);

    assertThat(workload.getGroups()).extracting(TaskLoad::getId).containsExactly("sales", "support", "idle");
    TaskLoad sales = workload.getGroups().get(0);
    assertThat(sales.getOpen()).isEqualTo(3);
    assertThat(sales.getUnassigned()).isEqualTo(1);
    assertThat(sales.getOverdue()).isEqualTo(2);
    assertThat(sales.getOldest()).isEqualTo(at(Duration.ofDays(-5)));
    TaskLoad support = workload.getGroups().get(1);
    assertThat(support.getOpen()).isEqualTo(2);
    assertThat(support.getUnassigned()).isEqualTo(1);
    assertThat(support.getOverdue()).isEqualTo(1);
    TaskLoad idle = workload.getGroups().get(2);
    assertThat(idle.getOpen()).isZero();
    assertThat(idle.getOldest()).isNull();

    assertThat(workload.getUsers()).extracting(TaskLoad::getId).containsExactly("alice", "bob", "carol");
    TaskLoad alice = workload.getUsers().get(0);
    assertThat(alice.getOpen()).isEqualTo(2);
    assertThat(alice.getOverdue()).isEqualTo(1);
    assertThat(alice.getOldest()).isEqualTo(at(Duration.ofDays(-4)));
    assertThat(workload.getUsers().get(1).getOpen()).isEqualTo(1);
    assertThat(workload.getUsers().get(2).getOpen()).isZero();

    assertMatchesTaskQueries(workload);
  }

  @Test
  public void shouldAnswerWithoutTasksAndWithoutIds() {
    TaskWorkload workload = read(null, null);

    assertThat(workload.getTotals().getOpen()).isZero();
    assertThat(workload.getTotals().getUnassigned()).isZero();
    assertThat(workload.getTotals().getOverdue()).isZero();
    assertThat(workload.getTotals().getOldest()).isNull();
    assertThat(workload.getOrphaned()).isZero();
    assertThat(workload.getGroups()).isEmpty();
    assertThat(workload.getUsers()).isEmpty();
  }

  @Test
  public void shouldCountATaskOfferedTwiceToAGroupOnce() {
    String taskId = task(Duration.ofDays(-1), Duration.ofHours(-1), null, "sales");
    taskService.addCandidateGroup(taskId, "sales");

    TaskLoad sales = read(Arrays.asList("sales"), null).getGroups().get(0);

    assertThat(sales.getOpen()).isEqualTo(1);
    assertThat(sales.getUnassigned()).isEqualTo(1);
    assertThat(sales.getOverdue()).isEqualTo(1);
  }

  @Test
  public void shouldUseTheGivenMomentForOverdue() {
    someTasks();

    TaskWorkload workload = TaskWorkloads.query(engineRule.getProcessEngine())
        .groupIdIn(Arrays.asList("sales")).userIdIn(Arrays.asList("alice")).overdueBefore(at(Duration.ofDays(3))).read();

    assertThat(workload.getTotals().getOverdue()).isEqualTo(5);
    assertThat(workload.getGroups().get(0).getOverdue()).isEqualTo(3);
    assertThat(workload.getUsers().get(0).getOverdue()).isEqualTo(2);
  }

  @Test
  public void shouldCountTheTasksOfATenantOnly() {
    task("tenant1", Duration.ofDays(-2), Duration.ofDays(-1), "alice", "sales");
    task("tenant2", Duration.ofDays(-3), null, "alice", "sales");
    task(Duration.ofDays(-4), null, null, "sales");

    TaskWorkload workload = TaskWorkloads.query(engineRule.getProcessEngine())
        .groupIdIn(Arrays.asList("sales")).userIdIn(Arrays.asList("alice")).tenantIdIn("tenant1").read();

    assertThat(workload.getTotals().getOpen()).isEqualTo(1);
    assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(1);
    assertThat(workload.getGroups().get(0).getOldest()).isEqualTo(at(Duration.ofDays(-2)));
    assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(1);
    assertThat(workload.getUsers().get(0).getOverdue()).isEqualTo(1);
  }

  @Test
  public void shouldCountTheTasksOfTheTenantsOfTheCallerOnly() {
    task("tenant1", Duration.ofDays(-2), null, "alice", "sales");
    task("tenant2", Duration.ofDays(-3), null, "alice", "sales");
    task(Duration.ofDays(-4), null, "alice", "sales");
    identityService.setAuthentication("mary", null, Arrays.asList("tenant1"));

    TaskWorkload workload = read(Arrays.asList("sales"), Arrays.asList("alice"));

    assertThat(workload.getTotals().getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(2);
    assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(2);
    assertMatchesTaskQueries(workload);
  }

  // authorization /////////////////////////////////////////////////////////

  @Test
  public void shouldCountOnlyTheTasksTheCallerMayRead() {
    someTasks();
    grant("mary", Resources.TASK, taskIds.get(0), Permissions.READ);
    grant("mary", Resources.TASK, taskIds.get(2), Permissions.READ);
    grant("mary", Resources.TASK, taskIds.get(5), Permissions.READ);
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("mary", null);

    TaskWorkload workload = read(Arrays.asList("sales", "support"), Arrays.asList("alice", "bob"));

    assertThat(workload.getTotals().getOpen()).isEqualTo(3);
    assertThat(workload.getTotals().getUnassigned()).isEqualTo(2);
    assertThat(workload.getOrphaned()).isEqualTo(1);
    assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(1).getOpen()).isEqualTo(1);
    assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(1);
    assertThat(workload.getUsers().get(1).getOpen()).isZero();
    assertMatchesTaskQueries(workload);
  }

  @Test
  public void shouldCountNothingForACallerWithoutPermissions() {
    someTasks();
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("mary", null);

    TaskWorkload workload = read(Arrays.asList("sales", "support"), Arrays.asList("alice", "bob"));

    assertThat(workload.getTotals().getOpen()).isZero();
    assertThat(workload.getOrphaned()).isZero();
    assertThat(workload.getGroups()).allMatch(load -> load.getOpen() == 0 && load.getOldest() == null);
    assertThat(workload.getUsers()).allMatch(load -> load.getOpen() == 0 && load.getOldest() == null);
  }

  @Test
  public void shouldCountTheTasksOfProcessesTheCallerMayReadTasksOf() {
    testRule.deploy(Bpmn.createExecutableProcess("visible").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaCandidateGroups("sales").endEvent().done());
    testRule.deploy(Bpmn.createExecutableProcess("hidden").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaCandidateGroups("sales").endEvent().done());
    runtimeService.startProcessInstanceByKey("visible");
    runtimeService.startProcessInstanceByKey("visible");
    runtimeService.startProcessInstanceByKey("hidden");
    Task claimed = taskService.createTaskQuery().processDefinitionKey("visible").listPage(0, 1).get(0);
    taskService.setAssignee(claimed.getId(), "alice");
    task(Duration.ofDays(-1), null, "alice", "sales");
    grant("mary", Resources.PROCESS_DEFINITION, "visible", Permissions.READ_TASK);
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("mary", null);

    TaskWorkload workload = read(Arrays.asList("sales"), Arrays.asList("alice"));

    assertThat(workload.getTotals().getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(2);
    assertThat(workload.getGroups().get(0).getUnassigned()).isEqualTo(1);
    assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(1);
    assertMatchesTaskQueries(workload);
  }

  @Test
  public void shouldHonourRevokedPermissions() {
    someTasks();
    grant("mary", Resources.TASK, Authorization.ANY, Permissions.READ);
    Authorization revoke = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_REVOKE);
    revoke.setUserId("mary");
    revoke.setResource(Resources.TASK);
    revoke.setResourceId(taskIds.get(1));
    revoke.removePermission(Permissions.READ);
    authorizationService.saveAuthorization(revoke);
    String mode = configuration.getAuthorizationCheckRevokes();
    configuration.setAuthorizationCheckRevokes(ProcessEngineConfigurationImpl.AUTHORIZATION_CHECK_REVOKE_ALWAYS);
    configuration.setAuthorizationEnabled(true);
    identityService.setAuthentication("mary", null);
    try {
      TaskWorkload workload = read(Arrays.asList("sales", "support"), Arrays.asList("alice", "bob"));

      assertThat(workload.getTotals().getOpen()).isEqualTo(6);
      assertThat(workload.getGroups().get(0).getOpen()).isEqualTo(2);
      assertThat(workload.getUsers().get(0).getOpen()).isEqualTo(1);
      assertMatchesTaskQueries(workload);
    } finally {
      configuration.setAuthorizationCheckRevokes(mode);
    }
  }

  // limits ////////////////////////////////////////////////////////////////

  @Test
  public void shouldRefuseTooManyIds() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i <= TaskWorkloadQuery.MAX_IDS; i++) {
      ids.add("id" + i);
    }

    assertThatThrownBy(() -> TaskWorkloads.query(engineRule.getProcessEngine()).groupIdIn(ids))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("more than 200");
    assertThatThrownBy(() -> TaskWorkloads.query(engineRule.getProcessEngine()).userIdIn(ids))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("more than 200");
    assertThatThrownBy(() -> TaskWorkloads.query(engineRule.getProcessEngine()).userIdIn(Arrays.asList("a", "")))
        .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldAskForAnIdOnce() {
    someTasks();

    TaskWorkload workload = read(Arrays.asList("sales", "sales"), Arrays.asList("alice", "alice"));

    assertThat(workload.getGroups()).hasSize(1);
    assertThat(workload.getUsers()).hasSize(1);
  }
}
