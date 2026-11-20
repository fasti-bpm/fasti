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
package org.camunda.bpm.engine.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditTrailPlugin;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * The audit trail endpoints against real in-memory engines (with and without the plugin); the
 * user of a request is set on the engine by a command interceptor, as the authentication filter would.
 */
public class AuditRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String AUDIT_URL = TEST_RESOURCE_ROOT_PATH + "/audit";
  protected static final String EVENT_URL = AUDIT_URL + "/event";
  protected static final String COUNT_URL = EVENT_URL + "/count";
  protected static final String EXPORT_URL = EVENT_URL + "/export";
  protected static final String VERIFY_URL = AUDIT_URL + "/verify";
  protected static final String PURGE_URL = AUDIT_URL + "/purge";
  protected static final String KEY = "a-rest-test-key-that-is-long-enough-0123456789";

  protected static volatile Authentication requestAuthentication;
  protected static ProcessEngine engine;
  protected static ProcessEngine engineWithoutPlugin;
  protected static String instanceId;

  @BeforeClass
  public static void buildEngines() {
    engine = buildEngine("auditRestEngine", true);
    engineWithoutPlugin = buildEngine("auditRestEngineWithoutPlugin", false);
  }

  protected static ProcessEngine buildEngine(String name, boolean withPlugin) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createStandaloneInMemProcessEngineConfiguration()
        .setProcessEngineName(name)
        .setJdbcUrl("jdbc:h2:mem:" + name)
        .setHistory(ProcessEngineConfiguration.HISTORY_FULL)
        .setJobExecutorActivate(false);
    List<CommandInterceptor> interceptors = new ArrayList<>();
    interceptors.add(new CommandInterceptor() {
      @Override
      public <T> T execute(Command<T> command) {
        Authentication authentication = requestAuthentication;
        if (authentication != null) {
          configuration.getIdentityService().setAuthentication(authentication);
        } else {
          configuration.getIdentityService().clearAuthentication();
        }
        return next.execute(command);
      }
    });
    configuration.setCustomPreCommandInterceptorsTxRequired(interceptors);
    if (withPlugin) {
      AuditTrailPlugin plugin = new AuditTrailPlugin();
      plugin.setNodeId("rest");
      plugin.setChains(1);
      plugin.setHmacKey(KEY);
      plugin.setRetentionDays(0);
      configuration.getProcessEnginePlugins().add(plugin);
    }
    return configuration.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngines() {
    engine.close();
    engineWithoutPlugin.close();
  }

  @Before
  public void setUpEngine() {
    use(engine);
    if (instanceId == null) {
      engine.getRepositoryService().createDeployment().addModelInstance("rest.bpmn",
          Bpmn.createExecutableProcess("rest").camundaHistoryTimeToLive(180).startEvent().userTask("approve").name("Approve")
              .endEvent().done()).deploy();
      requestAuthentication = new Authentication("alice", List.of());
      try {
        instanceId = engine.getRuntimeService().startProcessInstanceByKey("rest", "order-1").getId();
        engine.getRuntimeService().setVariable(instanceId, "comment", "=HYPERLINK(\"x\")");
      } finally {
        requestAuthentication = null;
      }
    }
  }

  @After
  public void tearDown() {
    requestAuthentication = null;
    engine.getIdentityService().clearAuthentication();
    configuration(engine).setAuthorizationEnabled(false);
    for (Authorization authorization : engine.getAuthorizationService().createAuthorizationQuery().list()) {
      engine.getAuthorizationService().deleteAuthorization(authorization.getId());
    }
  }

  protected static ProcessEngineConfigurationImpl configuration(ProcessEngine processEngine) {
    return (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
  }

  protected void use(ProcessEngine target) {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(target.getProcessEngineConfiguration());
  }

  @Test
  public void shouldDescribeTheSettingsWithoutTheKey() {
    Response response = given()
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("enabled", equalTo(true))
        .body("nodeId", equalTo("rest"))
        .body("chains", equalTo(1))
        .body("signed", equalTo(true))
        .body("keyId", notNullValue())
        .body("types", hasItem("task"))
        .body("variableValues", equalTo("hash"))
        .body("historyLevel", equalTo("full"))
    .when().get(AUDIT_URL);
    assertThat(response.asString()).doesNotContain(KEY);
  }

  @Test
  public void shouldSayWhenItIsOff() {
    use(engineWithoutPlugin);
    given().then().expect().statusCode(Status.OK.getStatusCode()).body("enabled", equalTo(false)).body("nodeId", nullValue())
    .when().get(AUDIT_URL);
    given().then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("not enabled"))
    .when().get(EVENT_URL);
  }

  @Test
  public void shouldListFilterAndCountRecords() {
    given().queryParam("processInstanceId", instanceId).queryParam("sortOrder", "asc")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("[0].type", equalTo("process"))
        .body("[0].action", equalTo("start"))
        .body("[0].actor", equalTo("alice"))
        .body("[0].businessKey", equalTo("order-1"))
        .body("[0].hash.length()", equalTo(64))
        .body("[0].time", greaterThan(0L))
        .body("processInstanceId", everyItem(equalTo(instanceId)))
    .when().get(EVENT_URL);

    given().queryParam("businessKey", "order-1").queryParam("type", "task,variable").queryParam("maxResults", 1)
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("$", hasSize(1))
        .body("type[0]", not(equalTo("process")))
    .when().get(EVENT_URL);

    given().queryParam("actor", "alice").queryParam("type", "process")
    .then().expect().statusCode(Status.OK.getStatusCode()).body("count", equalTo(1))
    .when().get(COUNT_URL);

    given().queryParam("actor", "system").queryParam("type", "deployment")
    .then().expect().statusCode(Status.OK.getStatusCode()).body("count", greaterThan(0))
    .when().get(COUNT_URL);

    given().queryParam("from", "2999-01-01T00:00:00.000+0000")
    .then().expect().statusCode(Status.OK.getStatusCode()).body("count", equalTo(0))
    .when().get(COUNT_URL);
  }

  @Test
  public void shouldRefuseWrongParameters() {
    given().queryParam("maxResults", 5000).then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).when().get(EVENT_URL);
    given().queryParam("sortOrder", "up").then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).when().get(EVENT_URL);
    given().queryParam("from", "yesterday").then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).when().get(COUNT_URL);
    given().queryParam("format", "xml").then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).when().get(EXPORT_URL);
  }

  @Test
  public void shouldExportCsvAndJsonAndRecordTheExport() {
    requestAuthentication = new Authentication("auditor", List.of(Groups.CAMUNDA_ADMIN));
    Response csv = given().queryParam("processInstanceId", instanceId).queryParam("format", "csv")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .header("Content-Disposition", containsString("attachment; filename=\"audit-trail-"))
        .header("Content-Type", containsString("text/csv"))
    .when().get(EXPORT_URL);
    String[] lines = csv.asString().split("\r\n");
    assertThat(lines[0]).contains("id,chainId,sequence,timestamp,time,type,action,actor");
    assertThat(lines.length - 1L).isEqualTo(AuditTrail.of(engine).createQuery().processInstanceId(instanceId).count());
    assertThat(lines[1]).contains(",process,start,alice,");

    Response json = given().queryParam("processInstanceId", instanceId).queryParam("format", "json")
    .then().expect().statusCode(Status.OK.getStatusCode()).header("Content-Type", containsString("application/json"))
    .when().get(EXPORT_URL);
    List<Map<String, Object>> records = json.jsonPath().getList("$");
    assertThat(records).hasSize(lines.length - 1);
    assertThat(records.get(0).get("previousHash")).isNotNull();

    List<String> exports = given().queryParam("type", "audit").queryParam("action", "export")
    .then().expect().statusCode(Status.OK.getStatusCode())
    .when().get(EVENT_URL).jsonPath().getList("actor");
    assertThat(exports).contains("auditor");
  }

  @Test
  public void shouldGuardSpreadsheetFormulasInCsvOnly() {
    String csv = given().queryParam("processInstanceId", instanceId).queryParam("type", "operation").queryParam("format", "csv")
        .then().expect().statusCode(Status.OK.getStatusCode()).when().get(EXPORT_URL).asString();
    for (String line : csv.split("\r\n")) {
      assertThat(line).doesNotStartWith("=");
    }
    assertThat(org.camunda.bpm.engine.rest.impl.AuditRestServiceImplAccess.cell("=1+1")).isEqualTo("\"'=1+1\"");
    assertThat(org.camunda.bpm.engine.rest.impl.AuditRestServiceImplAccess.cell("a,b")).isEqualTo("\"a,b\"");
    assertThat(org.camunda.bpm.engine.rest.impl.AuditRestServiceImplAccess.cell("plain")).isEqualTo("plain");
  }

  @Test
  public void shouldVerifyAndReportABreak() {
    given().then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("intact", equalTo(true))
        .body("chains[0].chainId", equalTo("rest/0"))
        .body("firstBreak", nullValue())
    .when().post(VERIFY_URL);

    // an intruder changes a record of a second engine on its own database
    ProcessEngine tampered = buildEngine("auditRestTampered", true);
    try {
      use(tampered);
      tampered.getRepositoryService().createDeployment().addModelInstance("t.bpmn",
          Bpmn.createExecutableProcess("t").camundaHistoryTimeToLive(180).startEvent().userTask().endEvent().done()).deploy();
      tampered.getRuntimeService().startProcessInstanceByKey("t");
      configuration(tampered).getCommandExecutorTxRequired().execute(commandContext -> {
        try (PreparedStatement statement = commandContext.getDbSqlSession().getSqlSession().getConnection()
            .prepareStatement("update FASTI_AUDIT_EVENT set ACTOR_ = 'mallory' where SEQ_ = 2")) {
          return statement.executeUpdate();
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException(e);
        }
      });
      given().then().expect()
          .statusCode(Status.OK.getStatusCode())
          .body("intact", equalTo(false))
          .body("firstBreak.reason", equalTo("hash-mismatch"))
          .body("firstBreak.sequence", equalTo(2))
          .body("chains[0].intact", equalTo(false))
      .when().post(VERIFY_URL);
      given().queryParam("chainId", "unknown").then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).when().post(VERIFY_URL);
    } finally {
      tampered.close();
    }
  }

  @Test
  public void shouldPurgeOnlyAfterAnExport() {
    given().contentType(ContentType.JSON).body(Map.of("before", "2000-01-01T00:00:00.000+0000"))
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).body("message", containsString("Export"))
    .when().post(PURGE_URL);
    given().contentType(ContentType.JSON).body(Map.of("before", "2000-01-01T00:00:00.000+0000", "exported", true))
    .then().expect().statusCode(Status.OK.getStatusCode()).body("removed", equalTo(0))
    .when().post(PURGE_URL);
  }

  @Test
  public void shouldAnswerForbiddenWithoutPermission() {
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("nobody", List.of());
    given().then().expect().statusCode(Status.FORBIDDEN.getStatusCode()).body("type", equalTo("AuthorizationException"))
    .when().get(EVENT_URL);
    given().then().expect().statusCode(Status.FORBIDDEN.getStatusCode()).when().get(COUNT_URL);
    given().then().expect().statusCode(Status.FORBIDDEN.getStatusCode()).when().post(VERIFY_URL);
    given().queryParam("format", "csv").then().expect().statusCode(Status.FORBIDDEN.getStatusCode()).when().get(EXPORT_URL);

    requestAuthentication = null;
    Authorization read = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    read.setUserId("reader");
    read.setResource(Resources.SYSTEM);
    read.setResourceId("*");
    read.addPermission(SystemPermissions.READ);
    engine.getAuthorizationService().saveAuthorization(read);

    requestAuthentication = new Authentication("reader", List.of());
    given().then().expect().statusCode(Status.OK.getStatusCode()).when().get(EVENT_URL);
    given().contentType(ContentType.JSON).body(Map.of("before", "2000-01-01T00:00:00.000+0000", "exported", true))
    .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
    .when().post(PURGE_URL);
    assertThat(AuditService.TYPE_AUDIT).isEqualTo("audit");
  }
}
