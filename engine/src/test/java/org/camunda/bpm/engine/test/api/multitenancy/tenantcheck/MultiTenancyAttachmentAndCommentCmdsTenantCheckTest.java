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
package org.camunda.bpm.engine.test.api.multitenancy.tenantcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Attachment;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MultiTenancyAttachmentAndCommentCmdsTenantCheckTest {

  protected static final String TENANT_ONE = "tenant1";

  protected static final String PROCESS_DEFINITION_KEY = "oneTaskProcess";

  protected static final BpmnModelInstance ONE_TASK_PROCESS = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
    .startEvent()
    .userTask()
    .endEvent()
    .done();

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected TaskService taskService;
  protected IdentityService identityService;

  protected String processInstanceId;
  protected String taskId;

  protected Attachment taskAttachment;
  protected Attachment processInstanceAttachment;
  protected Comment taskComment;

  @Before
  public void init() {
    taskService = engineRule.getTaskService();
    identityService = engineRule.getIdentityService();

    testRule.deployForTenant(TENANT_ONE, ONE_TASK_PROCESS);

    processInstanceId = engineRule.getRuntimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY).getId();
    taskId = taskService.createTaskQuery().singleResult().getId();

    taskAttachment = createAttachment(taskId, null);
    processInstanceAttachment = createAttachment(null, processInstanceId);
    taskComment = taskService.createComment(taskId, null, "aComment");
    taskService.createComment(null, processInstanceId, "aComment");
  }

  // read //////////////////////////////////////////////////////////////////////

  @Test
  public void readWithAuthenticatedTenant() {
    identityService.setAuthentication("aUserId", null, Arrays.asList(TENANT_ONE));

    assertSucceeds(taskReadOperations());
    assertSucceeds(processInstanceReadOperations());
  }

  @Test
  public void readWithNoAuthenticatedTenant() {
    identityService.setAuthentication("aUserId", null);

    assertFails(taskReadOperations(),
        "Cannot read the task '" + taskId + "' because it belongs to no authenticated tenant.");
    assertFails(processInstanceReadOperations(),
        "Cannot read the process instance '" + processInstanceId + "' because it belongs to no authenticated tenant.");
  }

  @Test
  public void readWithDisabledTenantCheck() {
    identityService.setAuthentication("aUserId", null);
    engineRule.getProcessEngineConfiguration().setTenantCheckEnabled(false);

    assertSucceeds(taskReadOperations());
    assertSucceeds(processInstanceReadOperations());
  }

  @Test
  public void readHistoryWithAuthenticatedTenant() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null, Arrays.asList(TENANT_ONE));

    assertSucceeds(taskReadOperations());
    assertSucceeds(processInstanceReadOperations());
  }

  @Test
  public void readHistoryWithNoAuthenticatedTenant() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null);

    assertFails(taskReadOperations(),
        "Cannot read the historic task instance '" + taskId + "' because it belongs to no authenticated tenant.");
    assertFails(processInstanceReadOperations(),
        "Cannot read the historic process instance '" + processInstanceId + "' because it belongs to no authenticated tenant.");
  }

  @Test
  public void readHistoryWithDisabledTenantCheck() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null);
    engineRule.getProcessEngineConfiguration().setTenantCheckEnabled(false);

    assertSucceeds(taskReadOperations());
    assertSucceeds(processInstanceReadOperations());
  }

  // write /////////////////////////////////////////////////////////////////////

  @Test
  public void writeWithAuthenticatedTenant() {
    identityService.setAuthentication("aUserId", null, Arrays.asList(TENANT_ONE));

    assertSucceeds(taskWriteOperations());
    assertSucceeds(processInstanceWriteOperations());
  }

  @Test
  public void writeWithNoAuthenticatedTenant() {
    identityService.setAuthentication("aUserId", null);

    assertFails(taskWriteOperations(),
        "Cannot work on task '" + taskId + "' because it belongs to no authenticated tenant.");
    assertFails(processInstanceWriteOperations(),
        "Cannot update the process instance '" + processInstanceId + "' because it belongs to no authenticated tenant.");
  }

  @Test
  public void writeWithDisabledTenantCheck() {
    identityService.setAuthentication("aUserId", null);
    engineRule.getProcessEngineConfiguration().setTenantCheckEnabled(false);

    assertSucceeds(taskWriteOperations());
    assertSucceeds(processInstanceWriteOperations());
  }

  @Test
  public void writeHistoryWithAuthenticatedTenant() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null, Arrays.asList(TENANT_ONE));

    assertSucceeds(taskWriteOperations());
    assertSucceeds(finishedProcessInstanceWriteOperations());
  }

  @Test
  public void writeHistoryWithNoAuthenticatedTenant() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null);

    assertFails(taskWriteOperations(),
        "the historic task instance '" + taskId + "' because it belongs to no authenticated tenant.");
    assertFails(finishedProcessInstanceWriteOperations(),
        "the historic process instance '" + processInstanceId + "' because it belongs to no authenticated tenant.");
  }

  @Test
  public void writeHistoryWithDisabledTenantCheck() {
    taskService.complete(taskId);
    identityService.setAuthentication("aUserId", null);
    engineRule.getProcessEngineConfiguration().setTenantCheckEnabled(false);

    assertSucceeds(taskWriteOperations());
    assertSucceeds(finishedProcessInstanceWriteOperations());
  }

  // helper ////////////////////////////////////////////////////////////////////

  protected Map<String, Callable<Object>> taskWriteOperations() {
    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("createComment", () -> taskService.createComment(taskId, null, "anotherComment"));
    operations.put("createAttachment", () -> taskService.createAttachment("aType", taskId, null, "aName", "aDescription", "http://localhost/attachment"));
    operations.put("saveAttachment", () -> {
      taskService.saveAttachment(taskAttachment);
      return null;
    });
    operations.put("deleteTaskAttachment", () -> {
      taskService.deleteTaskAttachment(taskId, taskAttachment.getId());
      return null;
    });
    return operations;
  }

  protected Map<String, Callable<Object>> processInstanceWriteOperations() {
    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("createAttachment", () -> taskService.createAttachment("aType", null, processInstanceId, "aName", "aDescription", "http://localhost/attachment"));
    operations.putAll(finishedProcessInstanceWriteOperations());
    return operations;
  }

  protected Map<String, Callable<Object>> finishedProcessInstanceWriteOperations() {
    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("createComment", () -> taskService.createComment(null, processInstanceId, "anotherComment"));
    operations.put("saveAttachment", () -> {
      taskService.saveAttachment(processInstanceAttachment);
      return null;
    });
    operations.put("deleteAttachment", () -> {
      taskService.deleteAttachment(processInstanceAttachment.getId());
      return null;
    });
    return operations;
  }

  protected Map<String, Callable<Object>> taskReadOperations() {
    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("getTaskAttachments", () -> assertThat(taskService.getTaskAttachments(taskId)).hasSize(1));
    operations.put("getTaskAttachment", () -> assertThat(taskService.getTaskAttachment(taskId, taskAttachment.getId())).isNotNull());
    operations.put("getTaskAttachmentContent", () -> assertThat(taskService.getTaskAttachmentContent(taskId, taskAttachment.getId())).isNotNull());
    operations.put("getAttachment", () -> assertThat(taskService.getAttachment(taskAttachment.getId())).isNotNull());
    operations.put("getAttachmentContent", () -> assertThat(taskService.getAttachmentContent(taskAttachment.getId())).isNotNull());
    operations.put("getTaskComments", () -> assertThat(taskService.getTaskComments(taskId)).hasSize(1));
    operations.put("getTaskComment", () -> assertThat(taskService.getTaskComment(taskId, taskComment.getId())).isNotNull());
    operations.put("getTaskEvents", () -> assertThat(taskService.getTaskEvents(taskId)).isNotEmpty());
    return operations;
  }

  protected Map<String, Callable<Object>> processInstanceReadOperations() {
    Map<String, Callable<Object>> operations = new LinkedHashMap<>();
    operations.put("getProcessInstanceAttachments", () -> assertThat(taskService.getProcessInstanceAttachments(processInstanceId)).hasSize(1));
    operations.put("getProcessInstanceComments", () -> assertThat(taskService.getProcessInstanceComments(processInstanceId)).hasSize(1));
    operations.put("getAttachment", () -> assertThat(taskService.getAttachment(processInstanceAttachment.getId())).isNotNull());
    operations.put("getAttachmentContent", () -> assertThat(taskService.getAttachmentContent(processInstanceAttachment.getId())).isNotNull());
    return operations;
  }

  protected void assertSucceeds(Map<String, Callable<Object>> operations) {
    for (Map.Entry<String, Callable<Object>> operation : operations.entrySet()) {
      try {
        operation.getValue().call();
      } catch (Exception e) {
        throw new AssertionError("Operation '" + operation.getKey() + "' failed", e);
      }
    }
  }

  protected void assertFails(Map<String, Callable<Object>> operations, String message) {
    for (Map.Entry<String, Callable<Object>> operation : operations.entrySet()) {
      assertThatThrownBy(() -> operation.getValue().call())
        .as(operation.getKey())
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining(message);
    }
  }

  protected Attachment createAttachment(String taskId, String processInstanceId) {
    return taskService.createAttachment("aType", taskId, processInstanceId, "aName", "aDescription",
        new ByteArrayInputStream("someContent".getBytes(StandardCharsets.UTF_8)));
  }

}
