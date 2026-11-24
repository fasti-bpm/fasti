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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class AnalyticsRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String ANALYTICS_URL = TEST_RESOURCE_ROOT_PATH + "/analytics/process-definition/key/{key}";
  protected static final long HOUR = 3_600_000L;
  protected static final Date T0 = new Date(1_790_000_000_000L);
  protected static final String AFTER = "2026-09-01T00:00:00.000+0000";
  protected static final String BEFORE = "2026-12-01T00:00:00.000+0000";

  protected static volatile Authentication requestAuthentication;
  protected static ProcessEngine engine;

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createStandaloneInMemProcessEngineConfiguration()
        .setProcessEngineName("analyticsRestEngine")
        .setJdbcUrl("jdbc:h2:mem:analyticsRestEngine")
        .setHistory("full")
        .setAuthorizationEnabled(true)
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

    engine.getRepositoryService().createDeployment().addModelInstance("check.bpmn",
        Bpmn.createExecutableProcess("check").startEvent("start").userTask("review").endEvent("end").done()).deploy();
    for (int run = 0; run < 3; run++) {
      ClockUtil.setCurrentTime(new Date(T0.getTime() + run * HOUR));
      engine.getRuntimeService().startProcessInstanceByKey("check", "check-" + run);
      Task task = engine.getTaskService().createTaskQuery().processInstanceBusinessKey("check-" + run).singleResult();
      ClockUtil.setCurrentTime(new Date(T0.getTime() + run * HOUR + (run + 1) * HOUR));
      engine.getTaskService().claim(task.getId(), "ann");
      engine.getTaskService().complete(task.getId());
    }
    ClockUtil.setCurrentTime(new Date(T0.getTime() + 10 * HOUR));
    engine.getRuntimeService().startProcessInstanceByKey("check", "check-open");
    ClockUtil.reset();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @Before
  public void setUpEngine() {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(engine.getProcessEngineConfiguration());
  }

  @After
  public void clearAuthentication() {
    requestAuthentication = null;
  }

  @Test
  public void shouldAnswerActivities() {
    given().pathParam("key", "check").queryParam("finishedAfter", AFTER).queryParam("finishedBefore", BEFORE)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("activityId", hasItem("review"))
        .body("find { it.activityId == 'review' }.finished", equalTo(3))
        .body("find { it.activityId == 'review' }.running", equalTo(1))
        .body("find { it.activityId == 'review' }.durations.p50", notNullValue())
        .body("find { it.activityId == 'review' }.durations.approximate", equalTo(true))
        .when().get(ANALYTICS_URL + "/activities");
  }

  @Test
  public void shouldAnswerInstancesWithATarget() {
    given().pathParam("key", "check").queryParam("finishedAfter", AFTER).queryParam("finishedBefore", BEFORE)
        .queryParam("targetDuration", 2 * HOUR)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("completed", equalTo(3))
        .body("running", equalTo(1))
        .body("durations.withinTarget", equalTo(2))
        .when().get(ANALYTICS_URL + "/instances");
  }

  @Test
  public void shouldAnswerATrend() {
    given().pathParam("key", "check")
        .queryParam("boundaries", AFTER + "," + "2026-10-01T00:00:00.000+0000" + "," + BEFORE)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("$", hasSize(2))
        .when().get(ANALYTICS_URL + "/trend");
  }

  @Test
  public void shouldAnswerTheSlowestAndTheRunning() {
    given().pathParam("key", "check").pathParam("activityId", "review")
        .queryParam("finishedAfter", AFTER).queryParam("finishedBefore", BEFORE).queryParam("maxResults", 2)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("businessKey", equalTo(java.util.Arrays.asList("check-2", "check-1")))
        .when().get(ANALYTICS_URL + "/activities/{activityId}/instances");
    given().pathParam("key", "check").pathParam("activityId", "review").queryParam("running", true)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("businessKey", equalTo(Collections.singletonList("check-open")))
        .when().get(ANALYTICS_URL + "/activities/{activityId}/instances");
  }

  @Test
  public void shouldAnswerUserTasks() {
    given().pathParam("key", "check").queryParam("finishedAfter", AFTER).queryParam("finishedBefore", BEFORE)
        .then().expect().statusCode(Status.OK.getStatusCode())
        .body("tasks", equalTo(3))
        .body("sampled", equalTo(false))
        .body("byTask[0].key", equalTo("review"))
        .body("byAssignee[0].key", equalTo("ann"))
        .when().get(ANALYTICS_URL + "/user-tasks");
  }

  @Test
  public void shouldRefuseBadParameters() {
    given().pathParam("key", "check").queryParam("finishedAfter", "yesterday")
        .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("finishedAfter"))
        .when().get(ANALYTICS_URL + "/activities");
    given().pathParam("key", "check").queryParam("targetDuration", "-1")
        .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
        .when().get(ANALYTICS_URL + "/instances");
    given().pathParam("key", "check").queryParam("boundaries", AFTER)
        .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
        .when().get(ANALYTICS_URL + "/trend");
    given().pathParam("key", "check").pathParam("activityId", "review").queryParam("maxResults", 1000)
        .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
        .when().get(ANALYTICS_URL + "/activities/{activityId}/instances");
  }

  @Test
  public void shouldRequireReadHistory() {
    requestAuthentication = new Authentication("eve", Collections.emptyList());
    given().pathParam("key", "check")
        .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
        .when().get(ANALYTICS_URL + "/activities");

    requestAuthentication = null;
    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId("eve");
    grant.setResource(Resources.PROCESS_DEFINITION);
    grant.setResourceId("check");
    grant.addPermission(Permissions.READ_HISTORY);
    engine.getAuthorizationService().saveAuthorization(grant);
    try {
      requestAuthentication = new Authentication("eve", Collections.emptyList());
      given().pathParam("key", "check")
          .then().expect().statusCode(Status.OK.getStatusCode())
          .when().get(ANALYTICS_URL + "/activities");
    } finally {
      requestAuthentication = null;
      engine.getAuthorizationService().deleteAuthorization(grant.getId());
    }
  }
}
