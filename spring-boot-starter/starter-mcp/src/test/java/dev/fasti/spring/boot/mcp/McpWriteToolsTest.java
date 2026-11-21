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
package dev.fasti.spring.boot.mcp;

import static dev.fasti.spring.boot.mcp.McpTestFixture.ADMIN;
import static dev.fasti.spring.boot.mcp.McpTestFixture.ADMIN_PASSWORD;
import static dev.fasti.spring.boot.mcp.McpTestFixture.BOB;
import static dev.fasti.spring.boot.mcp.McpTestFixture.CAROL;
import static dev.fasti.spring.boot.mcp.McpTestFixture.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditTrail;

/**
 * Write tools, switched on with {@code fasti.mcp.write-tools.enabled=true}: they act as the
 * authenticated user, the engine checks permissions and four-eyes rules, and the audit trail
 * records the user.
 */
@SpringBootTest(classes = McpTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "camunda.bpm.process-engine-name=mcpWrite",
        "spring.datasource.url=jdbc:h2:mem:mcpWrite;DB_CLOSE_ON_EXIT=FALSE",
        "fasti.mcp.write-tools.enabled=true",
        "fasti.four-eyes.enabled=true",
        "fasti.audit.enabled=true"
    })
class McpWriteToolsTest {

  @LocalServerPort
  int port;

  @Autowired
  ProcessEngine engine;

  McpHttpClient mcp;

  @BeforeEach
  void setUp() {
    McpTestFixture.setUp(engine);
    mcp = new McpHttpClient(port, "/mcp");
  }

  @Test
  @SuppressWarnings("unchecked")
  void offersWriteToolsWhenEnabled() {
    Map<String, Object> response = mcp.rpc(ADMIN, ADMIN_PASSWORD, "tools/list", Map.of());

    List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) response.get("result")).get("tools");
    assertThat(tools).extracting(tool -> tool.get("name"))
        .contains("start_process_instance", "claim_task", "unclaim_task", "complete_task", "retry_failed_job")
        .doesNotContain("deploy", "deploy_process", "execute_script");
    Map<String, Object> start = tools.stream().filter(tool -> "start_process_instance".equals(tool.get("name")))
        .findFirst().orElseThrow();
    assertThat((Map<String, Object>) start.get("annotations")).containsEntry("readOnlyHint", false);
  }

  @Test
  @SuppressWarnings("unchecked")
  void walksAProcessWithFourEyes() {
    Map<String, Object> started = mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "start_process_instance",
        Map.of("processDefinitionKey", "mcpProcess", "businessKey", "claim-1",
            "variables", Map.of("amount", 99, "urgent", true, "customer", "ACME")));
    String processInstanceId = (String) started.get("id");
    assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(processInstanceId)
        .singleResult().getStartUserId()).isEqualTo(ADMIN);
    assertThat(engine.getRuntimeService().getVariable(processInstanceId, "amount")).isEqualTo(99);

    // carol is a candidate (group reviewers)
    List<Map<String, Object>> claimable = McpHttpClient.items(mcp.callToolOk(CAROL, PASSWORD, "list_tasks",
        Map.of("claimableByMe", true, "processInstanceId", processInstanceId)));
    assertThat(claimable).extracting(task -> task.get("taskDefinitionKey")).containsExactly("review");
    String review = (String) claimable.get(0).get("id");

    // completing needs the task to be assigned to the caller
    assertThat(mcp.callToolError(ADMIN, ADMIN_PASSWORD, "complete_task", Map.of("id", review)))
        .contains("not assigned to you");

    mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "claim_task", Map.of("id", review));
    Map<String, Object> completed = mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "complete_task",
        Map.of("id", review, "variables", Map.of("reviewed", true)));
    List<Map<String, Object>> next = (List<Map<String, Object>>) completed.get("nextTasks");
    assertThat(next).extracting(task -> task.get("taskDefinitionKey")).containsExactly("approve");
    String approve = (String) next.get(0).get("id");

    // the reviewer may not approve: the engine refuses, the tool returns its reason
    String refused = refuseClaimOrComplete(approve);
    assertThat(refused).contains("four-eyes");
    assertThat(engine.getTaskService().createTaskQuery().taskId(approve).count()).isEqualTo(1);

    // the audit trail records the MCP user as the actor of what the tools did
    engine.getIdentityService().setAuthentication(ADMIN, List.of("camunda-admin"));
    try {
      List<AuditEvent> events = AuditTrail.of(engine).createQuery().processInstanceId(processInstanceId).list();
      assertThat(events).isNotEmpty();
      assertThat(events).extracting(AuditEvent::getActor).contains(ADMIN);
    } finally {
      engine.getIdentityService().clearAuthentication();
    }
  }

  /** The four-eyes rule refuses either the claim or the completion of the approval by the reviewer. */
  protected String refuseClaimOrComplete(String taskId) {
    Map<String, Object> claim = mcp.callTool(ADMIN, ADMIN_PASSWORD, "claim_task", Map.of("id", taskId));
    if (Boolean.TRUE.equals(claim.get("isError"))) {
      return claim.toString();
    }
    return mcp.callToolError(ADMIN, ADMIN_PASSWORD, "complete_task", Map.of("id", taskId));
  }

  @Test
  void refusesWritesWithoutPermission() {
    String start = mcp.callToolError(BOB, PASSWORD, "start_process_instance",
        Map.of("processDefinitionKey", "otherProcess"));
    String startAsReader = mcp.callToolError(CAROL, PASSWORD, "start_process_instance",
        Map.of("processDefinitionKey", "mcpProcess"));

    assertThat(start).containsAnyOf("Not authorized", "no processes deployed", "not found");
    assertThat(startAsReader).startsWith("Not authorized");
  }

  @Test
  void refusesNestedOrOversizedVariables() {
    String nested = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "start_process_instance",
        Map.of("processDefinitionKey", "otherProcess", "variables", Map.of("bean", Map.of("class", "java.lang.Runtime"))));

    assertThat(nested).isNotBlank();
    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey("otherProcess")
        .variableValueEquals("bean", "java.lang.Runtime").count()).isZero();
  }

  @Test
  void doesNotDiscloseFailedExpressions() {
    String error = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "start_process_instance",
        Map.of("processDefinitionKey", "expressionProcess"));

    assertThat(error).contains("reference").doesNotContain("top-secret-argument").doesNotContain("notThere");
  }

  @Test
  void returnsEngineValidationMessages() {
    String error = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "retry_failed_job", Map.of("jobId", "no-such-job"));

    assertThat(error).contains("no-such-job");
  }

  @Test
  void unclaimsOnlyOwnTasks() {
    String processInstanceId = engine.getRuntimeService().startProcessInstanceByKey("otherProcess").getId();
    Task task = engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult();
    engine.getTaskService().setAssignee(task.getId(), "someone-else");

    String refused = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "unclaim_task", Map.of("id", task.getId()));

    assertThat(refused).contains("not assigned to you");
    assertThat(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee())
        .isEqualTo("someone-else");
  }

  @Test
  @SuppressWarnings("unchecked")
  void retriesFailedJobs() {
    String processInstanceId = engine.getRuntimeService().startProcessInstanceByKey("failingProcess").getId();
    Job job = engine.getManagementService().createJobQuery().processInstanceId(processInstanceId).singleResult();
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        engine.getManagementService().executeJob(job.getId());
      } catch (RuntimeException expected) {
        // the class does not exist
      }
    }
    Incident incident = engine.getRuntimeService().createIncidentQuery().processInstanceId(processInstanceId)
        .singleResult();
    assertThat(incident).isNotNull();

    List<Map<String, Object>> incidents = McpHttpClient.items(mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "list_incidents",
        Map.of("processInstanceId", processInstanceId)));
    assertThat(incidents).extracting(item -> item.get("incidentType")).containsExactly("failedJob");
    Map<String, Object> details = mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "get_incident", Map.of("id", incident.getId()));
    assertThat((String) ((Map<String, Object>) details.get("job")).get("stacktrace")).contains("dev.fasti.does.not.Exist");

    assertThat(mcp.callToolError(BOB, PASSWORD, "retry_failed_job", Map.of("incidentId", incident.getId())))
        .contains("not found or you are not allowed");
    assertThat(mcp.callToolError(BOB, PASSWORD, "retry_failed_job", Map.of("jobId", job.getId())))
        .contains("not found or you are not allowed");

    Map<String, Object> retried = mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "retry_failed_job",
        Map.of("incidentId", incident.getId(), "retries", 2));

    assertThat(retried).containsEntry("jobId", job.getId()).containsEntry("retries", 2);
    assertThat(engine.getManagementService().createJobQuery().jobId(job.getId()).singleResult().getRetries())
        .isEqualTo(2);
    assertThat(engine.getRuntimeService().createIncidentQuery().processInstanceId(processInstanceId).count()).isZero();
  }
}
