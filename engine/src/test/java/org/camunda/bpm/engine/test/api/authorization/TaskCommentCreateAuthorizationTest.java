/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_HISTORY;
import static org.camunda.bpm.engine.authorization.Permissions.READ_TASK;
import static org.camunda.bpm.engine.authorization.Permissions.TASK_WORK;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE_TASK;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.TASK;

import java.util.List;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.Test;

/**
 * Creating a comment requires the same permissions as changing and deleting
 * it, i.e. the permission to work on the task or to update the process
 * instance.
 */
public class TaskCommentCreateAuthorizationTest extends AuthorizationTest {

  protected static final String PROCESS_KEY = "oneTaskProcess";
  protected static final String ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml";

  // standalone task ///////////////////////////////////////////////////////////

  @Test
  public void shouldNotCreateStandaloneTaskCommentWithoutAuthorization() {
    // given
    createTask(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, READ);

    // when/then
    assertThatThrownBy(() -> taskService.createComment(TASK_ID, null, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("The user with id 'test' does not have one of the following permissions: "
          + "'TASK_WORK' permission on resource 'myTask' of type 'Task' "
          + "or 'UPDATE' permission on resource 'myTask' of type 'Task'");
    assertThat(taskComments(TASK_ID)).isEmpty();

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldCreateStandaloneTaskCommentWithUpdatePermissionOnTask() {
    // given
    createTask(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, UPDATE);

    // when
    taskService.createComment(TASK_ID, null, "aComment");

    // then
    assertThat(taskComments(TASK_ID)).containsExactly("aComment");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldCreateStandaloneTaskCommentWithTaskWorkPermissionOnTask() {
    // given
    createTask(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, TASK_WORK);

    // when
    taskService.addComment(TASK_ID, null, "aComment");

    // then
    assertThat(taskComments(TASK_ID)).containsExactly("aComment");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldCreateCommentOfDeletedStandaloneTask() {
    // given a standalone task which only exists in the history
    createTask(TASK_ID);
    deleteTask(TASK_ID, false);

    // when
    // the history of standalone tasks is not protected by any permission
    taskService.createComment(TASK_ID, null, "aComment");

    // then
    assertThat(taskComments(TASK_ID)).containsExactly("aComment");

    runWithoutAuthorization(() -> {
      historyService.deleteHistoricTaskInstance(TASK_ID);
      return null;
    });
  }

  // process task //////////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotCreateProcessTaskCommentWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK, READ_HISTORY);

    // when/then
    assertThatThrownBy(() -> taskService.createComment(taskId, null, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'TASK_WORK' permission on resource '" + taskId + "' of type 'Task'")
      .hasMessageContaining("'TASK_WORK' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'")
      .hasMessageContaining("'UPDATE' permission on resource '" + taskId + "' of type 'Task'")
      .hasMessageContaining("'UPDATE_TASK' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
    assertThatThrownBy(() -> taskService.createComment(taskId, processInstanceId, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'TASK_WORK' permission on resource '" + taskId + "' of type 'Task'");
    assertThat(taskComments(taskId)).isEmpty();
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotCreateProcessTaskCommentWithUpdatePermissionOnProcessInstance() {
    // given a comment that is created for a task and a process instance
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, UPDATE);

    // when/then
    // the task takes precedence
    assertThatThrownBy(() -> taskService.createComment(taskId, processInstanceId, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'TASK_WORK' permission on resource '" + taskId + "' of type 'Task'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessTaskCommentWithUpdatePermissionOnTask() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    taskService.createComment(taskId, null, "aComment");
    taskService.createComment(taskId, processInstanceId, "anotherComment");

    // then
    assertThat(taskComments(taskId)).containsExactlyInAnyOrder("aComment", "anotherComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessTaskCommentWithTaskWorkPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    taskService.createComment(taskId, null, "aComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessTaskCommentWithUpdateTaskPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    taskService.createComment(taskId, null, "aComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessTaskCommentWithTaskWorkPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_WORK);

    // when
    taskService.createComment(taskId, null, "aComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
  }

  // completed process task ////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotCreateCompletedProcessTaskCommentWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    completeTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE, TASK_WORK);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK, TASK_WORK, READ_HISTORY);

    // when/then
    assertThatThrownBy(() -> taskService.createComment(taskId, null, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'UPDATE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
    assertThat(taskComments(taskId)).isEmpty();
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateCompletedProcessTaskCommentWithUpdateHistoryPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    completeTask(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, ProcessDefinitionPermissions.UPDATE_HISTORY);

    // when
    taskService.createComment(taskId, null, "aComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
  }

  // process instance //////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotCreateProcessInstanceCommentWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    createGrantAuthorization(TASK, selectSingleTask().getId(), userId, UPDATE);

    // when/then
    assertThatThrownBy(() -> taskService.createComment(null, processInstanceId, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'UPDATE' permission on resource '" + processInstanceId + "' of type 'ProcessInstance'")
      .hasMessageContaining("'UPDATE_INSTANCE' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
    assertThat(processInstanceComments(processInstanceId)).isEmpty();
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessInstanceCommentWithUpdatePermissionOnProcessInstance() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, UPDATE);

    // when
    taskService.createComment(null, processInstanceId, "aComment");

    // then
    assertThat(processInstanceComments(processInstanceId)).containsExactly("aComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateProcessInstanceCommentWithUpdateInstancePermissionOnProcessDefinition() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_INSTANCE);

    // when
    taskService.createComment(null, processInstanceId, "aComment");

    // then
    assertThat(processInstanceComments(processInstanceId)).containsExactly("aComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotCreateFinishedProcessInstanceCommentWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_INSTANCE, READ_HISTORY);

    // when/then
    assertThatThrownBy(() -> taskService.createComment(null, processInstanceId, "aComment"))
      .isInstanceOf(AuthorizationException.class)
      .hasMessageContaining("'UPDATE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateFinishedProcessInstanceCommentWithUpdateHistoryPermission() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, ProcessDefinitionPermissions.UPDATE_HISTORY);

    // when
    taskService.createComment(null, processInstanceId, "aComment");

    // then
    assertThat(processInstanceComments(processInstanceId)).containsExactly("aComment");
  }

  // unchanged behavior ////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateCommentWithDisabledAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();

    // when
    createComment(taskId, null, "aComment");
    createComment(null, processInstanceId, "anotherComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
    assertThat(processInstanceComments(processInstanceId)).containsExactly("anotherComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateCommentAsAdmin() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    // administrators are granted all permissions on all resources
    createGrantAuthorizationGroup(TASK, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_INSTANCE, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_DEFINITION, ANY, groupId, ALL);

    // when
    taskService.createComment(taskId, null, "aComment");
    taskService.createComment(null, processInstanceId, "anotherComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
    assertThat(processInstanceComments(processInstanceId)).containsExactly("anotherComment");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateCommentWithoutAuthenticatedUser() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    identityService.clearAuthentication();

    // when
    taskService.createComment(taskId, null, "aComment");
    taskService.createComment(null, processInstanceId, "anotherComment");

    // then
    assertThat(taskComments(taskId)).containsExactly("aComment");
    assertThat(processInstanceComments(processInstanceId)).containsExactly("anotherComment");
  }

  // helper ////////////////////////////////////////////////////////////////////

  protected List<String> taskComments(String taskId) {
    return runWithoutAuthorization(() -> taskService.getTaskComments(taskId).stream()
        .map(Comment::getFullMessage)
        .collect(Collectors.toList()));
  }

  protected List<String> processInstanceComments(String processInstanceId) {
    return runWithoutAuthorization(() -> taskService.getProcessInstanceComments(processInstanceId).stream()
        .map(Comment::getFullMessage)
        .collect(Collectors.toList()));
  }

  protected void completeTask(String taskId) {
    runWithoutAuthorization(() -> {
      taskService.complete(taskId);
      return null;
    });
  }

}
