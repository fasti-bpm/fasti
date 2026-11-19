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
import static org.camunda.bpm.engine.authorization.Permissions.DELETE_HISTORY;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.task.Attachment;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.Test;

/**
 * Creating, changing and deleting an attachment requires the same permissions
 * as changing and deleting a comment, i.e. the permission to work on the task
 * or to update the process instance.
 */
public class TaskAttachmentWriteAuthorizationTest extends AuthorizationTest {

  protected static final String PROCESS_KEY = "oneTaskProcess";
  protected static final String ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml";

  // standalone task ///////////////////////////////////////////////////////////

  @Test
  public void shouldNotWriteStandaloneTaskAttachmentWithoutAuthorization() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskWriteOperations(TASK_ID);

    // when/then
    assertNotAuthorized(operations,
        "'TASK_WORK' permission on resource 'myTask' of type 'Task'",
        "'UPDATE' permission on resource 'myTask' of type 'Task'");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldNotWriteStandaloneTaskAttachmentWithReadPermission() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskWriteOperations(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, READ);

    // when/then
    assertNotAuthorized(operations, "'TASK_WORK' permission on resource 'myTask' of type 'Task'");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldWriteStandaloneTaskAttachmentWithUpdatePermissionOnTask() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskWriteOperations(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, UPDATE);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(TASK_ID)).containsExactly("savedName", "createdName");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldWriteStandaloneTaskAttachmentWithTaskWorkPermissionOnTask() {
    // given
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskWriteOperations(TASK_ID);
    createGrantAuthorization(TASK, TASK_ID, userId, TASK_WORK);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(TASK_ID)).containsExactly("savedName", "createdName");

    deleteTask(TASK_ID, true);
  }

  @Test
  public void shouldWriteAttachmentOfDeletedStandaloneTask() {
    // given a standalone task which only exists in the history
    createTask(TASK_ID);
    Map<String, Callable<Object>> operations = taskWriteOperations(TASK_ID);
    deleteTask(TASK_ID, false);

    // when
    // the history of standalone tasks is not protected by any permission
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(TASK_ID)).containsExactly("savedName", "createdName");

    runWithoutAuthorization(() -> {
      historyService.deleteHistoricTaskInstance(TASK_ID);
      return null;
    });
  }

  // process task //////////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotWriteProcessTaskAttachmentWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);

    // when/then
    assertNotAuthorized(operations,
        "'TASK_WORK' permission on resource '" + taskId + "' of type 'Task'",
        "'TASK_WORK' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'",
        "'UPDATE' permission on resource '" + taskId + "' of type 'Task'",
        "'UPDATE_TASK' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotWriteProcessTaskAttachmentWithReadPermissions() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    createGrantAuthorization(TASK, taskId, userId, READ);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, READ_TASK, READ_HISTORY);

    // when/then
    assertNotAuthorized(operations, "'TASK_WORK' permission on resource '" + taskId + "' of type 'Task'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessTaskAttachmentWithUpdatePermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(taskId)).containsExactly("savedName", "createdName");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessTaskAttachmentWithTaskWorkPermissionOnTask() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    createGrantAuthorization(TASK, taskId, userId, TASK_WORK);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(taskId)).containsExactly("savedName", "createdName");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessTaskAttachmentWithUpdateTaskPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(taskId)).containsExactly("savedName", "createdName");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessTaskAttachmentWithTaskWorkPermissionOnProcessDefinition() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, TASK_WORK);

    // when
    assertAuthorized(operations);

    // then
    assertThat(attachmentNames(taskId)).containsExactly("savedName", "createdName");
  }

  // completed process task ////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotWriteCompletedProcessTaskAttachmentWithoutAuthorization() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(TASK, taskId, userId, UPDATE, TASK_WORK);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_TASK, TASK_WORK, READ_HISTORY);

    // when/then
    assertNotAuthorized(subMap(operations, "createAttachment", "saveAttachment"),
        "'UPDATE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
    assertNotAuthorized(subMap(operations, "deleteAttachment", "deleteTaskAttachment"),
        "'DELETE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldCreateAndSaveCompletedProcessTaskAttachmentWithUpdateHistoryPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, ProcessDefinitionPermissions.UPDATE_HISTORY);

    // when
    assertAuthorized(subMap(operations, "createAttachment", "saveAttachment"));

    // then
    assertThat(attachmentNames(taskId)).contains("savedName", "createdName");
    assertNotAuthorized(subMap(operations, "deleteAttachment", "deleteTaskAttachment"),
        "'DELETE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldDeleteCompletedProcessTaskAttachmentWithDeleteHistoryPermission() {
    // given
    startProcessInstanceByKey(PROCESS_KEY);
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    completeTask(taskId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, DELETE_HISTORY);

    // when
    assertAuthorized(subMap(operations, "deleteAttachment", "deleteTaskAttachment"));

    // then
    assertThat(attachmentNames(taskId)).containsExactly("toBeSaved");
    assertNotAuthorized(subMap(operations, "createAttachment", "saveAttachment"),
        "'UPDATE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  // process instance //////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotWriteProcessInstanceAttachmentWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceWriteOperations(processInstanceId);
    createGrantAuthorization(TASK, selectSingleTask().getId(), userId, UPDATE);

    // when/then
    assertNotAuthorized(operations,
        "'UPDATE' permission on resource '" + processInstanceId + "' of type 'ProcessInstance'",
        "'UPDATE_INSTANCE' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessInstanceAttachmentWithUpdatePermissionOnProcessInstance() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceWriteOperations(processInstanceId);
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, UPDATE);

    // when
    assertAuthorized(operations);

    // then
    assertThat(processInstanceAttachmentNames(processInstanceId)).containsExactly("savedName", "createdName");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteProcessInstanceAttachmentWithUpdateInstancePermissionOnProcessDefinition() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceWriteOperations(processInstanceId);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_INSTANCE);

    // when
    assertAuthorized(operations);

    // then
    assertThat(processInstanceAttachmentNames(processInstanceId)).containsExactly("savedName", "createdName");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldNotWriteFinishedProcessInstanceAttachmentWithoutAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceWriteOperations(processInstanceId);
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, UPDATE);
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, UPDATE_INSTANCE, READ_HISTORY);

    // when/then
    assertNotAuthorized(subMap(operations, "saveAttachment"),
        "'UPDATE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
    assertNotAuthorized(subMap(operations, "deleteAttachment"),
        "'DELETE_HISTORY' permission on resource 'oneTaskProcess' of type 'ProcessDefinition'");
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteFinishedProcessInstanceAttachmentWithHistoryPermissions() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    Map<String, Callable<Object>> operations = processInstanceWriteOperations(processInstanceId);
    completeTask(selectSingleTask().getId());
    createGrantAuthorization(PROCESS_DEFINITION, PROCESS_KEY, userId, DELETE_HISTORY,
        ProcessDefinitionPermissions.UPDATE_HISTORY);

    // when
    assertAuthorized(subMap(operations, "saveAttachment", "deleteAttachment"));

    // then
    assertThat(processInstanceAttachmentNames(processInstanceId)).containsExactly("savedName");
  }

  // unchanged behavior ////////////////////////////////////////////////////////

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteWithDisabledAuthorization() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    operations.putAll(processInstanceWriteOperations(processInstanceId));

    // when/then
    runWithoutAuthorization(() -> {
      assertAuthorized(operations);
      return null;
    });
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteAsAdmin() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    operations.putAll(processInstanceWriteOperations(processInstanceId));
    // administrators are granted all permissions on all resources
    createGrantAuthorizationGroup(TASK, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_INSTANCE, ANY, groupId, ALL);
    createGrantAuthorizationGroup(PROCESS_DEFINITION, ANY, groupId, ALL);

    // when/then
    assertAuthorized(operations);
  }

  @Test
  @Deployment(resources = ONE_TASK_PROCESS)
  public void shouldWriteWithoutAuthenticatedUser() {
    // given
    String processInstanceId = startProcessInstanceByKey(PROCESS_KEY).getId();
    String taskId = selectSingleTask().getId();
    Map<String, Callable<Object>> operations = taskWriteOperations(taskId);
    operations.putAll(processInstanceWriteOperations(processInstanceId));
    identityService.clearAuthentication();

    // when/then
    assertAuthorized(operations);
  }

  // helper ////////////////////////////////////////////////////////////////////

  /**
   * Creates three attachments for the given task and returns the operations
   * which create another one, change the first one and delete the other ones.
   */
  protected Map<String, Callable<Object>> taskWriteOperations(String taskId) {
    Attachment toBeSaved = createAttachment(taskId, null, "toBeSaved");
    String toBeDeletedId = createAttachment(taskId, null, "toBeDeleted").getId();
    String toBeDeletedByTaskId = createAttachment(taskId, null, "toBeDeletedByTask").getId();

    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("createAttachment", () -> taskService.createAttachment("aType", taskId, null,
        "createdName", "aDescription", "http://localhost/attachment"));
    operations.put("saveAttachment", () -> {
      toBeSaved.setName("savedName");
      taskService.saveAttachment(toBeSaved);
      return null;
    });
    operations.put("deleteAttachment", () -> {
      taskService.deleteAttachment(toBeDeletedId);
      return null;
    });
    operations.put("deleteTaskAttachment", () -> {
      taskService.deleteTaskAttachment(taskId, toBeDeletedByTaskId);
      return null;
    });
    return operations;
  }

  /**
   * Creates two attachments for the given process instance and returns the
   * operations which create another one, change the first one and delete the
   * other one.
   */
  protected Map<String, Callable<Object>> processInstanceWriteOperations(String processInstanceId) {
    Attachment toBeSaved = createAttachment(null, processInstanceId, "toBeSaved");
    String toBeDeletedId = createAttachment(null, processInstanceId, "toBeDeleted").getId();

    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("createAttachment (process instance)", () -> taskService.createAttachment("aType", null,
        processInstanceId, "createdName", "aDescription", "http://localhost/attachment"));
    operations.put("saveAttachment (process instance)", () -> {
      toBeSaved.setName("savedName");
      taskService.saveAttachment(toBeSaved);
      return null;
    });
    operations.put("deleteAttachment (process instance)", () -> {
      taskService.deleteAttachment(toBeDeletedId);
      return null;
    });
    return operations;
  }

  protected Map<String, Callable<Object>> subMap(Map<String, Callable<Object>> operations, String... names) {
    Map<String, Callable<Object>> result = new LinkedHashMap<>();
    for (Map.Entry<String, Callable<Object>> operation : operations.entrySet()) {
      for (String name : names) {
        if (operation.getKey().equals(name) || operation.getKey().equals(name + " (process instance)")) {
          result.put(operation.getKey(), operation.getValue());
        }
      }
    }
    assertThat(result).hasSize(names.length);
    return result;
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

  protected Iterable<String> attachmentNames(String taskId) {
    return runWithoutAuthorization(() -> taskService.getTaskAttachments(taskId).stream()
        .map(Attachment::getName)
        .sorted((a, b) -> b.compareTo(a))
        .collect(Collectors.toList()));
  }

  protected Iterable<String> processInstanceAttachmentNames(String processInstanceId) {
    return runWithoutAuthorization(() -> taskService.getProcessInstanceAttachments(processInstanceId).stream()
        .map(Attachment::getName)
        .sorted((a, b) -> b.compareTo(a))
        .collect(Collectors.toList()));
  }

  protected Attachment createAttachment(String taskId, String processInstanceId, String name) {
    return runWithoutAuthorization(() -> taskService.createAttachment("aType", taskId, processInstanceId,
        name, "aDescription", new ByteArrayInputStream("someContent".getBytes(StandardCharsets.UTF_8))));
  }

  protected void completeTask(String taskId) {
    runWithoutAuthorization(() -> {
      taskService.complete(taskId);
      return null;
    });
  }

}
