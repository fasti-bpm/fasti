/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.engine.test.api.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_HISTORY;
import static org.camunda.bpm.engine.authorization.Permissions.READ_INSTANCE;
import static org.camunda.bpm.engine.authorization.Permissions.READ_TASK;
import static org.camunda.bpm.engine.authorization.Permissions.UPDATE;
import static org.camunda.bpm.engine.authorization.Resources.HISTORIC_PROCESS_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.HISTORIC_TASK;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.TASK;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.HistoricProcessInstancePermissions;
import org.camunda.bpm.engine.authorization.HistoricTaskPermissions;
import org.camunda.bpm.engine.impl.util.IoUtil;
import org.camunda.bpm.engine.task.Attachment;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.After;
import org.junit.Test;

/**
 * Reading the attachments, comments and events of a task requires that the
 * task (or its history) is readable; the same applies to the attachments and
 * comments of a process instance.
 */
public class TaskAttachmentAndCommentReadAuthorizationTest extends AuthorizationTest {

  protected static final String PROCESS_KEY = "oneTaskProcess";
  protected static final String ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml";
  protected static final String CONTENT = "someContent";

  @Override
  @After
  public void tearDown() {
    processEngineConfiguration.setEnableHistoricInstancePermissions(false);
    super.tearDown();
  }

  // standalone task ///////////////////////////////////////////////////////////

  @Test
  public void shouldNotReadStandaloneTaskDataWithoutAuthorization() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);

    // when/then
    assertNotAuthorized(operations, "'READ' permission on resource 'myTask' of type 'Task'");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldReadStandaloneTaskDataWithReadPermissionOnTask() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, READ);

    // when/then
    assertAuthorized(operations);

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldNotReadStandaloneTaskDataWithUpdatePermissionOnTask() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, UPDATE);

    // when/then
    assertNotAuthorized(operations, "'READ' permission on resource 'myTask' of type 'Task'");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldReadDataOfDeletedStandaloneTask() {
    // given a standalone task which only exists in the history
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);
    deleteTask(TASK_ID, false);

    // when/then
    // historic standalone tasks are readable without any permission
    assertAuthorized(operations);

    runWithoutAuthorization(() -> {
      historyService.deleteHistoricTaskInstance(TASK_ID);
      return null;
    });
  }

  @Test
  public void shouldNotReadDataOfDeletedStandaloneTaskWithHistoricInstancePermissionsEnabled() {
    // given a standalone task which only exists in the history
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);
    deleteTask(TASK_ID, false);

    // when/then
    assertNotAuthorized(operations, "'READ' permission on resource 'myTask' of type 'HistoricTask'");

    runWithoutAuthorization(() -> {
      historyService.deleteHistoricTaskInstance(TASK_ID);
      return null;
    });
  }

  @Test
  public void shouldReadDataOfDeletedStandaloneTaskWithHistoricTaskPermission() {
    // given a standalone task which only exists in the history
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskReadOperations(TASK_ID);
    deleteTask(TASK_ID, false);
    createGrantAuthorization(HISTORIC_TASK, TASK_ID, userId, HistoricTaskPermissions.READ);

    // when/then
    assertAuthorized(operations);

    runWithoutAuthorization(() -> {
      historyService.deleteHistoricTaskInstance(TASK_ID);
      return null;
    });
  }

  // process task //////////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadProcessTaskDataWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);

    // when/then
    assertNotAuthorized(operations,
        "'READ' permission on resource '" + taskId + "' of type 'Task'",
        "'READ_TASK' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'",
        "'READ_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessTaskDataWithReadPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(TASK, taskId, userId, READ);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessTaskDataWithReadTaskPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessTaskDataWithReadHistoryPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_HISTORY);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadProcessTaskDataWithReadPermissionOnOtherTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(TASK, "anotherTask", userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, "anotherProcess", userId, READ_TASK, READ_HISTORY);

    // when/then
    assertNotAuthorized(operations, "'READ' permission on resource '" + taskId + "' of type 'Task'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadProcessTaskDataWithHistoricTaskPermissionIfNotEnabled() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(HISTORIC_TASK, taskId, userId, HistoricTaskPermissions.READ);

    // when/then
    assertNotAuthorized(operations, "'READ' permission on resource '" + taskId + "' of type 'Task'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessTaskDataWithHistoricTaskPermission() {
    // given
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    createGrantAuthorization(HISTORIC_TASK, taskId, userId, HistoricTaskPermissions.READ);

    // when/then
    assertAuthorized(operations);
  }

  // completed process task ////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadCompletedProcessTaskDataWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    completeTask(taskId);

    // when/then
    assertNotAuthorized(operations,
        "'READ_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadCompletedProcessTaskDataWithRuntimePermissions() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK);

    // when/then
    assertNotAuthorized(operations,
        "'READ_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadCompletedProcessTaskDataWithReadHistoryPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_HISTORY);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadCompletedProcessTaskDataWithHistoricTaskPermission() {
    // given
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(HISTORIC_TASK, taskId, userId, HistoricTaskPermissions.READ);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadCompletedProcessTaskDataWithHistoricProcessInstancePermission() {
    // given
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskReadOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(HISTORIC_PROCESS_INSTANCE, processInstanceId, userId,
        HistoricProcessInstancePermissions.READ);

    // when/then
    assertAuthorized(operations);
  }

  // process instance //////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadProcessInstanceDataWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);

    // when/then
    assertNotAuthorized(operations,
        "'READ' permission on resource '" + processInstanceId + "' of type 'ProcessInstance'",
        "'READ_INSTANCE' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'",
        "'READ_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessInstanceDataWithReadPermissionOnProcessInstance() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, READ);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessInstanceDataWithReadInstancePermissionOnProcessDefinition() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_INSTANCE);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadProcessInstanceDataWithReadHistoryPermissionOnProcessDefinition() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_HISTORY);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadProcessInstanceDataWithReadPermissionOnTask() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    createGrantAuthorization(TASK, selectSingleTask().getId(), userId, READ);

    // when/then
    assertNotAuthorized(operations,
        "'READ' permission on resource '" + processInstanceId + "' of type 'ProcessInstance'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotReadFinishedProcessInstanceDataWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_INSTANCE);

    // when/then
    assertNotAuthorized(operations,
        "'READ_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadFinishedProcessInstanceDataWithReadHistoryPermission() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_HISTORY);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadFinishedProcessInstanceDataWithHistoricProcessInstancePermission() {
    // given
    processEngineConfiguration.setEnableHistoricInstancePermissions(true);
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceReadOperations(processInstanceId);
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(HISTORIC_PROCESS_INSTANCE, processInstanceId, userId,
        HistoricProcessInstancePermissions.READ);

    // when/then
    assertAuthorized(operations);
  }

  // unchanged behavior ////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadWithDisabledAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = taskReadOperations(selectSingleTask().getId());
    operations.putAll(processInstanceReadOperations(processInstanceId));

    // when/then
    runWithoutAuthorization(() -> {
      assertAuthorized(operations);
      return null;
    });
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadAsAdmin() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = taskReadOperations(selectSingleTask().getId());
    operations.putAll(processInstanceReadOperations(processInstanceId));
    // administrators are granted all permissions on all resources
    createGrantAuthorizationGroup(TASK, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_INSTANCE, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_DEFINITION, ANY, groupId, ALL);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldReadWithoutAuthenticatedUser() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = taskReadOperations(selectSingleTask().getId());
    operations.putAll(processInstanceReadOperations(processInstanceId));
    identityService.clearAuthentication();

    // when/then
    assertAuthorized(operations);
  }

  @Test
  public void shouldReturnNothingForUnknownTaskAndProcessInstance() {
    // when/then
    assertThat(taskService.getTaskAttachments("unknown")).isEmpty();
    assertThat(taskService.getTaskAttachment("unknown", "unknown")).isNull();
    assertThat(taskService.getTaskAttachmentContent("unknown", "unknown")).isNull();
    assertThat(taskService.getAttachment("unknown")).isNull();
    assertThat(taskService.getTaskComments("unknown")).isEmpty();
    assertThat(taskService.getTaskComment("unknown", "unknown")).isNull();
    assertThat(taskService.getTaskEvents("unknown")).isEmpty();
    assertThat(taskService.getProcessInstanceAttachments("unknown")).isEmpty();
    assertThat(taskService.getProcessInstanceComments("unknown")).isEmpty();
  }

  // sub tasks /////////////////////////////////////////////////////////////////

  @Test
  public void shouldOnlyReturnReadableSubTasks() {
    // given
    createTask(TASK_ID);
    runWithoutAuthorization(() -> {
      for (String subTaskId : Arrays.asList("subTask1", "subTask2")) {
        Task subTask = taskService.newTask(subTaskId);
        subTask.setParentTaskId(TASK_ID);
        taskService.saveTask(subTask);
      }
      return null;
    });
    createGrantAuthorization(TASK, "subTask2", userId, READ);

    // when
    List<Task> subTasks = taskService.getSubTasks(TASK_ID);

    // then
    assertThat(subTasks).extracting("id").containsExactly("subTask2");

    deleteTask("subTask1", true);
    deleteTask("subTask2", true);
    deleteTask(TASK_ID, true);
  }

  // helper ////////////////////////////////////////////////////////////////////

  /**
   * Creates an attachment and a comment for the given task and returns all
   * operations which read them.
   */
  protected Map<String, Callable<Object>> taskReadOperations(String taskId) {
    String attachmentId = createAttachment(taskId, null).getId();
    String commentId = createComment(taskId, null, "aComment").getId();

    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("getTaskAttachments", () -> {
      List<Attachment> attachments = taskService.getTaskAttachments(taskId);
      assertThat(attachments).extracting("id").containsExactly(attachmentId);
      return attachments;
    });
    operations.put("getTaskAttachment", () -> {
      Attachment attachment = taskService.getTaskAttachment(taskId, attachmentId);
      assertThat(attachment).isNotNull();
      return attachment;
    });
    operations.put("getTaskAttachmentContent", () -> {
      InputStream content = taskService.getTaskAttachmentContent(taskId, attachmentId);
      assertContent(content);
      return content;
    });
    operations.put("getAttachment", () -> {
      Attachment attachment = taskService.getAttachment(attachmentId);
      assertThat(attachment).isNotNull();
      return attachment;
    });
    operations.put("getAttachmentContent", () -> {
      InputStream content = taskService.getAttachmentContent(attachmentId);
      assertContent(content);
      return content;
    });
    operations.put("getTaskComments", () -> {
      List<Comment> comments = taskService.getTaskComments(taskId);
      assertThat(comments).extracting("id").containsExactly(commentId);
      return comments;
    });
    operations.put("getTaskComment", () -> {
      Comment comment = taskService.getTaskComment(taskId, commentId);
      assertThat(comment).isNotNull();
      return comment;
    });
    operations.put("getTaskEvents", () -> {
      List<?> events = taskService.getTaskEvents(taskId);
      assertThat(events).isNotEmpty();
      return events;
    });
    return operations;
  }

  /**
   * Creates an attachment and a comment for the given process instance and
   * returns all operations which read them.
   */
  protected Map<String, Callable<Object>> processInstanceReadOperations(String processInstanceId) {
    String attachmentId = createAttachment(null, processInstanceId).getId();
    String commentId = createComment(null, processInstanceId, "aComment").getId();

    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("getProcessInstanceAttachments", () -> {
      List<Attachment> attachments = taskService.getProcessInstanceAttachments(processInstanceId);
      assertThat(attachments).extracting("id").containsExactly(attachmentId);
      return attachments;
    });
    operations.put("getProcessInstanceComments", () -> {
      List<Comment> comments = taskService.getProcessInstanceComments(processInstanceId);
      assertThat(comments).extracting("id").containsExactly(commentId);
      return comments;
    });
    operations.put("getAttachment (process instance)", () -> {
      Attachment attachment = taskService.getAttachment(attachmentId);
      assertThat(attachment).isNotNull();
      return attachment;
    });
    operations.put("getAttachmentContent (process instance)", () -> {
      InputStream content = taskService.getAttachmentContent(attachmentId);
      assertContent(content);
      return content;
    });
    return operations;
  }

  protected void assertNotAuthorized(Map<String, Callable<Object>> operations, String... messageParts) {
    for (Map.Entry<String, Callable<Object>> operation : operations.entrySet()) {
      assertThatThrownBy(() -> operation.getValue().call())
        .as(operation.getKey())
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining("The user with id 'test' does not have")
        .satisfies(e -> assertThat(e.getMessage()).as(operation.getKey()).contains(messageParts));
    }
  }

  protected void assertAuthorized(Map<String, Callable<Object>> operations) {
    for (Map.Entry<String, Callable<Object>> operation : operations.entrySet()) {
      try {
        operation.getValue().call();
      } catch (Exception e) {
        throw new AssertionError("Operation '" + operation.getKey() + "' failed", e);
      }
    }
  }

  protected void assertContent(InputStream content) {
    assertThat(content).isNotNull();
    byte[] bytes = IoUtil.readInputStream(content, "content");
    assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
  }

  protected Attachment createAttachment(String taskId, String processInstanceId) {
    return runWithoutAuthorization(() -> taskService.createAttachment("aType", taskId, processInstanceId,
        "aName", "aDescription", new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8))));
  }

  protected void completeTask(String taskId) {
    runWithoutAuthorization(() -> {
      taskService.complete(taskId);
      return null;
    });
  }

}
