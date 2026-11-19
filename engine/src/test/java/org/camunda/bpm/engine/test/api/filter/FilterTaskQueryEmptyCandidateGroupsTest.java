/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.PluggableProcessEngineTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A filter like "My group tasks" (<code>${currentUserGroups()}</code>) for a
 * user without groups: the expression yields no group, and the filter matches
 * no task instead of failing.
 */
public class FilterTaskQueryEmptyCandidateGroupsTest extends PluggableProcessEngineTest {

  protected static final String GROUP_TASKS = "${currentUserGroups()}";

  @Before
  public void setUp() {
    Group group = identityService.newGroup("accounting");
    identityService.saveGroup(group);
    identityService.saveUser(identityService.newUser("grouped"));
    identityService.saveUser(identityService.newUser("groupless"));
    identityService.createMembership("grouped", "accounting");

    Task candidate = taskService.newTask("candidateTask");
    taskService.saveTask(candidate);
    taskService.addCandidateGroup(candidate.getId(), "accounting");

    Task assigned = taskService.newTask("assignedTask");
    assigned.setAssignee("groupless");
    taskService.saveTask(assigned);
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    for (Filter filter : filterService.createFilterQuery().list()) {
      filterService.deleteFilter(filter.getId());
    }
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.deleteTask(task.getId(), true);
    }
    identityService.deleteUser("grouped");
    identityService.deleteUser("groupless");
    identityService.deleteGroup("accounting");
  }

  @Test
  public void shouldMatchNoTaskForUserWithoutGroups() {
    Filter filter = saveFilter(taskService.createTaskQuery().taskCandidateGroupInExpression(GROUP_TASKS));
    identityService.setAuthentication("groupless", Collections.emptyList());

    assertThat(filterService.count(filter.getId())).isZero();
    assertThat(filterService.list(filter.getId())).isEmpty();
    assertThat(filterService.listPage(filter.getId(), 0, 10)).isEmpty();
  }

  @Test
  public void shouldStillMatchTasksOfGroupsOfUser() {
    Filter filter = saveFilter(taskService.createTaskQuery().taskCandidateGroupInExpression(GROUP_TASKS));
    identityService.setAuthentication("grouped", Collections.singletonList("accounting"));

    assertThat(filterService.count(filter.getId())).isEqualTo(1);
    assertThat(filterService.<Task>list(filter.getId()))
        .extracting(Task::getId).containsExactly("candidateTask");
  }

  @Test
  public void shouldMatchOtherBranchesOfOrQuery() {
    // group tasks OR my tasks: only the second branch matches for a user without groups
    Filter filter = saveFilter(taskService.createTaskQuery()
        .or()
          .taskCandidateGroupInExpression(GROUP_TASKS)
          .taskAssigneeExpression("${currentUser()}")
        .endOr());
    identityService.setAuthentication("groupless", Collections.emptyList());

    assertThat(filterService.count(filter.getId())).isEqualTo(1);
    assertThat(filterService.<Task>list(filter.getId()))
        .extracting(Task::getId).containsExactly("assignedTask");
  }

  @Test
  public void shouldMatchNoTaskWhenIncludingAssignedTasks() {
    Filter filter = saveFilter(taskService.createTaskQuery()
        .taskCandidateGroupInExpression(GROUP_TASKS).includeAssignedTasks());
    identityService.setAuthentication("groupless", Collections.emptyList());

    assertThat(filterService.count(filter.getId())).isZero();
  }

  @Test
  public void shouldMatchNoTaskWithoutAuthentication() {
    Filter filter = saveFilter(taskService.createTaskQuery().taskCandidateGroupInExpression(GROUP_TASKS));

    assertThat(filterService.count(filter.getId())).isZero();
  }

  @Test
  public void shouldStillRefuseEmptyListPassedDirectly() {
    assertThatThrownBy(() -> taskService.createTaskQuery().taskCandidateGroupIn(Collections.emptyList()))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("Candidate group list is empty");
  }

  protected Filter saveFilter(org.camunda.bpm.engine.task.TaskQuery query) {
    Filter filter = filterService.newTaskFilter("group tasks").setQuery(query);
    return filterService.saveFilter(filter);
  }
}
