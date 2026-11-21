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
import static dev.fasti.spring.boot.mcp.McpTestFixture.EVE;
import static dev.fasti.spring.boot.mcp.McpTestFixture.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The MCP server over HTTP, as a client sees it, with the default settings: authentication
 * required, read-only tools, authorization checks of the engine, masking and limits.
 */
@SpringBootTest(classes = McpTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "camunda.bpm.process-engine-name=mcpProtocol",
        "spring.datasource.url=jdbc:h2:mem:mcpProtocol;DB_CLOSE_ON_EXIT=FALSE"
    })
class McpProtocolTest {

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

  // --- transport and authentication -------------------------------------------------------------

  @Test
  void refusesRequestsWithoutCredentials() {
    HttpResponse<String> response = mcp.post(mcp.request("tools/list", null), Map.of());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues("WWW-Authenticate")).anyMatch(value -> value.startsWith("Basic "));
    assertThat(response.body()).doesNotContain("get_engine_info");
  }

  @Test
  void refusesWrongPasswordsAndUnknownSchemes() {
    HttpResponse<String> wrong = mcp.post(mcp.request("tools/list", null),
        Map.of("Authorization", McpHttpClient.basic(EVE, "not-her-password")));
    HttpResponse<String> bearer = mcp.post(mcp.request("tools/list", null),
        Map.of("Authorization", "Bearer some-token"));
    HttpResponse<String> garbage = mcp.post(mcp.request("tools/list", null),
        Map.of("Authorization", "Basic %%%"));

    assertThat(wrong.statusCode()).isEqualTo(401);
    // bearer tokens are only accepted with OIDC configured
    assertThat(bearer.statusCode()).isEqualTo(401);
    assertThat(garbage.statusCode()).isEqualTo(401);
  }

  @Test
  void refusesForeignOrigins() {
    HttpResponse<String> response = mcp.post(mcp.request("tools/list", null),
        Map.of("Authorization", McpHttpClient.basic(ADMIN, ADMIN_PASSWORD), "Origin", "http://evil.example"));

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void refusesUnsupportedProtocolVersions() {
    HttpResponse<String> response = mcp.post(mcp.request("tools/list", null),
        Map.of("Authorization", McpHttpClient.basic(ADMIN, ADMIN_PASSWORD), "MCP-Protocol-Version", "1999-01-01"));

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void offersNoEventStreamOnGet() {
    HttpResponse<String> response = mcp.send(HttpRequest.newBuilder(mcp.uri())
        .header("Authorization", McpHttpClient.basic(ADMIN, ADMIN_PASSWORD))
        .header("Accept", "text/event-stream")
        .GET().build());

    assertThat(response.statusCode()).isEqualTo(405);
  }

  @Test
  @SuppressWarnings("unchecked")
  void initializes() {
    Map<String, Object> response = mcp.initialize(BOB, PASSWORD);

    Map<String, Object> result = (Map<String, Object>) response.get("result");
    assertThat(result.get("protocolVersion")).isEqualTo(McpHttpClient.PROTOCOL_VERSION);
    assertThat((Map<String, Object>) result.get("serverInfo")).containsEntry("name", "fasti-bpm");
    assertThat((Map<String, Object>) result.get("capabilities")).containsKeys("tools", "resources");
    assertThat((String) result.get("instructions")).contains("authenticated user");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listsOnlyReadToolsByDefault() {
    Map<String, Object> response = mcp.rpc(BOB, PASSWORD, "tools/list", Map.of());

    List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) response.get("result")).get("tools");
    List<String> names = tools.stream().map(tool -> (String) tool.get("name")).collect(Collectors.toList());
    assertThat(names).containsExactlyInAnyOrder("get_engine_info", "list_process_definitions",
        "get_process_definition", "list_process_instances", "get_process_instance",
        "list_historic_process_instances", "list_tasks", "get_task", "list_incidents", "get_incident",
        "list_decision_definitions", "evaluate_decision");
    for (Map<String, Object> tool : tools) {
      assertThat((String) tool.get("description")).as("description of %s", tool.get("name")).hasSizeGreaterThan(40);
      assertThat((Map<String, Object>) tool.get("inputSchema")).containsEntry("type", "object")
          .containsEntry("additionalProperties", false);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void doesNotRunWriteToolsByDefault() {
    Map<String, Object> response = mcp.rpc(ADMIN, ADMIN_PASSWORD, "tools/call",
        Map.of("name", "start_process_instance", "arguments", Map.of("processDefinitionKey", "otherProcess")));

    assertThat(response).containsKey("error");
    assertThat((String) ((Map<String, Object>) response.get("error")).get("message")).contains("Unknown tool");
    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processDefinitionKey("otherProcess").count())
        .isZero();
  }

  // --- tools as different users -----------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void reportsTheAuthenticatedUser() {
    Map<String, Object> info = mcp.callToolOk(CAROL, PASSWORD, "get_engine_info", Map.of());

    assertThat(info).containsEntry("product", "Fasti BPM").containsEntry("authorizationEnabled", true)
        .containsEntry("writeToolsEnabled", false);
    assertThat((Map<String, Object>) info.get("user")).containsEntry("id", CAROL)
        .containsEntry("groups", List.of("reviewers"));
  }

  @Test
  void showsEachUserOnlyWhatTheEngineAllows() {
    List<Map<String, Object>> asAdmin = McpHttpClient.items(
        mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "list_process_definitions", Map.of()));
    List<Map<String, Object>> asCarol = McpHttpClient.items(
        mcp.callToolOk(CAROL, PASSWORD, "list_process_definitions", Map.of()));
    Map<String, Object> asBob = mcp.callToolOk(BOB, PASSWORD, "list_process_definitions", Map.of());

    assertThat(asAdmin).extracting(item -> item.get("key"))
        .contains("mcpProcess", "otherProcess", "failingProcess");
    assertThat(asCarol).extracting(item -> item.get("key")).containsExactly("mcpProcess");
    assertThat(McpHttpClient.items(asBob)).isEmpty();
    assertThat(asBob).containsEntry("total", 0);
  }

  @Test
  void returnsErrorsNotDataToUsersWithoutPermission() {
    String id = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("otherProcess")
        .singleResult().getId();

    String byId = mcp.callToolError(BOB, PASSWORD, "get_process_definition", Map.of("id", id, "includeXml", true));
    String byKey = mcp.callToolError(CAROL, PASSWORD, "get_process_definition", Map.of("key", "otherProcess"));

    assertThat(byId).startsWith("Not authorized").doesNotContain("<definitions");
    assertThat(byKey).contains("not found or you are not allowed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void returnsTheModelOnRequest() {
    Map<String, Object> definition = mcp.callToolOk(CAROL, PASSWORD, "get_process_definition",
        Map.of("key", "mcpProcess", "includeXml", true));

    assertThat(definition).containsEntry("key", "mcpProcess").containsEntry("version", 1);
    assertThat((String) ((Map<String, Object>) definition.get("bpmn")).get("xml")).contains("fasti:fourEyes");
  }

  @Test
  @SuppressWarnings("unchecked")
  void masksSecretsAndCutsLongValues() {
    String id = engine.getRuntimeService().startProcessInstanceByKey("mcpProcess", "order-4711",
        Variables.createVariables()
            .putValue("dbPassword", "s3cr3t-value")
            .putValue("apiToken", "tok-123")
            .putValue("amount", 1200)
            .putValue("note", "x".repeat(100))).getId();

    Map<String, Object> instance = mcp.callToolOk(CAROL, PASSWORD, "get_process_instance",
        Map.of("id", id, "includeActivityHistory", true));

    assertThat(instance).containsEntry("businessKey", "order-4711").containsEntry("state", "ACTIVE");
    assertThat(instance.toString()).doesNotContain("s3cr3t-value").doesNotContain("tok-123");
    Map<String, Map<String, Object>> variables = ((List<Map<String, Object>>) instance.get("variables")).stream()
        .collect(Collectors.toMap(variable -> (String) variable.get("name"), variable -> variable));
    assertThat(variables.get("dbPassword")).containsEntry("masked", true).doesNotContainKey("value");
    assertThat(variables.get("apiToken")).containsEntry("masked", true).doesNotContainKey("value");
    assertThat(variables.get("amount")).containsEntry("value", 1200).containsEntry("type", "integer");
    assertThat(variables.get("note")).containsEntry("truncated", true).containsEntry("length", 100);
    assertThat((String) variables.get("note").get("value")).hasSize(40);
    assertThat((List<Map<String, Object>>) instance.get("activities"))
        .extracting(activity -> activity.get("activityId")).contains("review");
    assertThat((List<Map<String, Object>>) instance.get("activityHistory")).isNotEmpty();

    String denied = mcp.callToolError(BOB, PASSWORD, "get_process_instance", Map.of("id", id));
    assertThat(denied).contains("not found or you are not allowed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void describesTaskVariablesWithoutLoadingBinaries() {
    String processInstanceId = engine.getRuntimeService().startProcessInstanceByKey("mcpProcess",
        Variables.createVariables()
            .putValue("scan", Variables.byteArrayValue(new byte[100_000]))
            .putValue("invoice", Variables.fileValue("invoice.pdf").file(new byte[] { 1, 2 })
                .mimeType("application/pdf").create())
            .putValue("clientSecret", "abc-secret")
            .putValue("customer", "ACME")).getId();
    String taskId = engine.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult()
        .getId();
    engine.getTaskService().setVariableLocal(taskId, "customer", "local ACME");

    Map<String, Object> task = mcp.callToolOk(CAROL, PASSWORD, "get_task", Map.of("id", taskId));
    String denied = mcp.callToolError(BOB, PASSWORD, "get_task", Map.of("id", taskId));

    Map<String, Map<String, Object>> variables = ((List<Map<String, Object>>) task.get("variables")).stream()
        .collect(Collectors.toMap(variable -> (String) variable.get("name"), variable -> variable));
    assertThat(variables.get("scan")).containsEntry("type", "bytes").doesNotContainKey("value");
    assertThat(variables.get("invoice")).containsEntry("filename", "invoice.pdf").doesNotContainKey("value");
    assertThat(variables.get("clientSecret")).containsEntry("masked", true).doesNotContainKey("value");
    // the task's own variable hides the one of the process instance
    assertThat(variables.get("customer")).containsEntry("value", "local ACME");
    assertThat((List<String>) task.get("candidateGroups")).containsExactly("reviewers");
    assertThat(denied).contains("not found or you are not allowed");
  }

  @Test
  void enforcesResultLimits() {
    String error = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "list_process_definitions", Map.of("maxResults", 500));
    String unknown = mcp.callToolError(ADMIN, ADMIN_PASSWORD, "list_process_definitions", Map.of("bogus", true));

    assertThat(error).containsIgnoringCase("maxResults");
    assertThat(unknown).containsIgnoringCase("bogus");
  }

  @Test
  @SuppressWarnings("unchecked")
  void evaluatesDecisionsForAuthorizedUsersOnly() {
    Map<String, Object> result = mcp.callToolOk(ADMIN, ADMIN_PASSWORD, "evaluate_decision",
        Map.of("key", "mcpDecision", "variables", Map.of("amount", 1500)));
    String denied = mcp.callToolError(BOB, PASSWORD, "evaluate_decision",
        Map.of("key", "mcpDecision", "variables", Map.of("amount", 1500)));

    List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
    assertThat((Map<String, Object>) results.get(0).get("approver")).containsEntry("value", "manager");
    assertThat(denied).containsAnyOf("Not authorized", "not found");
  }

  @Test
  @SuppressWarnings("unchecked")
  void readsModelsAsResourcesWithPermissionChecks() {
    String id = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("mcpProcess")
        .singleResult().getId();
    String uri = "fasti://process-definitions/" + id + "/bpmn";

    Map<String, Object> allowed = mcp.rpc(CAROL, PASSWORD, "resources/read", Map.of("uri", uri));
    Map<String, Object> denied = mcp.rpc(BOB, PASSWORD, "resources/read", Map.of("uri", uri));
    Map<String, Object> templates = mcp.rpc(BOB, PASSWORD, "resources/templates/list", Map.of());

    List<Map<String, Object>> contents = (List<Map<String, Object>>) ((Map<String, Object>) allowed.get("result"))
        .get("contents");
    assertThat((String) contents.get(0).get("text")).contains("mcpProcess");
    assertThat(denied).containsKey("error").doesNotContainKey("result");
    assertThat(templates.toString()).contains("fasti://process-definitions/{processDefinitionId}/bpmn");
  }
}
