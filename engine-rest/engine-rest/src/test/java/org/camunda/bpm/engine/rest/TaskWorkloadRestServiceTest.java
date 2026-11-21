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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.restassured.http.ContentType;

/**
 * The task workload endpoint against a real in-memory engine; the user of a request is set on
 * the engine by a command interceptor, as the authentication filter would.
 */
public class TaskWorkloadRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String WORKLOAD_URL = TEST_RESOURCE_ROOT_PATH + "/task/workload";
  protected static final long DAY = 86_400_000L;

  protected static volatile Authentication requestAuthentication;
  protected static ProcessEngine engine;
  protected List<String> taskIds = new ArrayList<>();

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createStandaloneInMemProcessEngineConfiguration()
        .setProcessEngineName("workloadRestEngine")
        .setJdbcUrl("jdbc:h2:mem:workloadRestEngine")
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
    configuration.setCustomPreCommandInterceptorsTxRequiresNew(new ArrayList<>(interceptors));
    engine = configuration.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @Before
  public void setUpEngine() {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(engine.getProcessEngineConfiguration());
    task(-5 * DAY, "alice", "sales");
    task(-DAY, null, "sales", "support");
    task(DAY, "bob");
    task(null, null);
  }

  @After
  public void tearDown() {
    requestAuthentication = null;
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).setAuthorizationEnabled(false);
    for (String taskId : taskIds) {
      engine.getTaskService().deleteTask(taskId, true);
    }
    for (Authorization authorization : engine.getAuthorizationService().createAuthorizationQuery().list()) {
      engine.getAuthorizationService().deleteAuthorization(authorization.getId());
    }
  }

  protected String task(Long dueFromNow, String assignee, String... groups) {
    Task task = engine.getTaskService().newTask();
    if (dueFromNow != null) {
      task.setDueDate(new Date(System.currentTimeMillis() + dueFromNow));
    }
    engine.getTaskService().saveTask(task);
    taskIds.add(task.getId());
    for (String group : groups) {
      engine.getTaskService().addCandidateGroup(task.getId(), group);
    }
    if (assignee != null) {
      engine.getTaskService().setAssignee(task.getId(), assignee);
    }
    return task.getId();
  }

  @Test
  public void shouldCountPerGroupAndUser() {
    given().queryParam("groupIdIn", "sales,support,idle").queryParam("userIdIn", "alice,bob")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("totals.id", nullValue())
        .body("totals.open", equalTo(4))
        .body("totals.unassigned", equalTo(2))
        .body("totals.overdue", equalTo(2))
        .body("totals.oldest", notNullValue())
        .body("orphaned", equalTo(1))
        .body("groups.id", contains("sales", "support", "idle"))
        .body("groups.open", contains(2, 1, 0))
        .body("groups.unassigned", contains(1, 1, 0))
        .body("groups.overdue", contains(2, 1, 0))
        .body("groups[2].oldest", nullValue())
        .body("users.id", contains("alice", "bob"))
        .body("users.open", contains(1, 1))
        .body("users.overdue", contains(1, 0))
    .when().get(WORKLOAD_URL);
  }

  @Test
  public void shouldAnswerTheTotalsWithoutParameters() {
    given()
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("totals.open", equalTo(4))
        .body("groups", hasSize(0))
        .body("users", hasSize(0))
    .when().get(WORKLOAD_URL);
  }

  @Test
  public void shouldTakeTheParametersAsBody() {
    Map<String, Object> body = new HashMap<>();
    body.put("groupIdIn", List.of("sales"));
    body.put("userIdIn", List.of("bob"));
    body.put("overdueBefore", "2100-01-01T00:00:00.000+0000");

    given().contentType(ContentType.JSON).body(body)
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("totals.overdue", equalTo(3))
        .body("groups.id", contains("sales"))
        .body("groups.overdue", contains(2))
        .body("users.id", contains("bob"))
        .body("users.overdue", contains(1))
    .when().post(WORKLOAD_URL);

    given().contentType(ContentType.JSON).body("{}")
    .then().expect().statusCode(Status.OK.getStatusCode()).body("totals.open", equalTo(4))
    .when().post(WORKLOAD_URL);
  }

  @Test
  public void shouldUseTheGivenMomentForOverdue() {
    given().queryParam("overdueBefore", "2000-01-01T00:00:00.000+0000").queryParam("groupIdIn", "sales")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("totals.overdue", equalTo(0))
        .body("groups.overdue", contains(0))
    .when().get(WORKLOAD_URL);
  }

  @Test
  public void shouldCountOnlyWhatTheUserMayRead() {
    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId("mary");
    grant.setResource(Resources.TASK);
    grant.setResourceId(taskIds.get(1));
    grant.addPermission(Permissions.READ);
    engine.getAuthorizationService().saveAuthorization(grant);
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("mary", List.of());

    given().queryParam("groupIdIn", "sales,support").queryParam("userIdIn", "alice,bob")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("totals.open", equalTo(1))
        .body("orphaned", equalTo(0))
        .body("groups.open", contains(1, 1))
        .body("users.open", contains(0, 0))
    .when().get(WORKLOAD_URL);
  }

  @Test
  public void shouldRefuseBadParameters() {
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i <= 200; i++) {
      ids.append(i == 0 ? "" : ",").append("group").append(i);
    }
    given().queryParam("groupIdIn", ids.toString())
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).body("message", containsString("more than 200"))
    .when().get(WORKLOAD_URL);

    given().queryParam("overdueBefore", "yesterday")
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode()).body("message", containsString("overdueBefore"))
    .when().get(WORKLOAD_URL);
  }
}
