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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import dev.fasti.engine.absence.AbsenceSubstitutionPlugin;
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
import org.camunda.bpm.model.bpmn.Bpmn;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.restassured.http.ContentType;

/**
 * The absence endpoints against real in-memory engines (with and without the plugin); the user
 * of a request is set on the engine by a command interceptor, as the authentication filter would.
 */
public class UserAbsenceRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String ABSENCE_URL = TEST_RESOURCE_ROOT_PATH + "/user/{id}/absence";
  protected static final String HANDOVER_URL = ABSENCE_URL + "/handover";
  protected static final String SUBSTITUTING_URL = ABSENCE_URL + "/substituting";

  /** Fixed time of the engines: 2026-11-21T08:00:00Z. */
  protected static final long NOW = 1_795_248_000_000L;
  protected static final String TOMORROW = "2026-11-22T08:00:00.000+0000";
  protected static final String NEXT_WEEK = "2026-11-28T08:00:00.000+0000";

  protected static volatile Authentication requestAuthentication;
  protected static ProcessEngine engine;
  protected static ProcessEngine engineWithoutPlugin;
  protected static ProcessEngine engineWithoutIdentityTables;

  @BeforeClass
  public static void buildEngines() {
    engine = buildEngine("absenceRestEngine", true);
    engineWithoutPlugin = buildEngine("absenceRestEngineWithoutPlugin", false);
    engineWithoutIdentityTables = buildEngine("absenceRestEngineWithoutIdentityTables", true);
  }

  protected static ProcessEngine buildEngine(String name, boolean withPlugin) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createStandaloneInMemProcessEngineConfiguration()
        .setProcessEngineName(name)
        .setJdbcUrl("jdbc:h2:mem:" + name)
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
    if (name.endsWith("WithoutIdentityTables")) {
      configuration.setDbIdentityUsed(false);
      configuration.setDatabaseSchemaUpdate("create-drop");
    }
    if (withPlugin) {
      configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
    }
    return configuration.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngines() {
    engine.close();
    engineWithoutPlugin.close();
    engineWithoutIdentityTables.close();
  }

  @Before
  public void setUpEngine() {
    use(engine);
    ClockUtil.setCurrentTime(new java.util.Date(NOW));
    for (String id : List.of("alice", "bob", "carol")) {
      engine.getIdentityService().saveUser(engine.getIdentityService().newUser(id));
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
    for (String id : List.of("alice", "bob", "carol")) {
      engine.getIdentityService().deleteUser(id);
    }
    engine.getRepositoryService().createDeploymentQuery().list()
        .forEach(deployment -> engine.getRepositoryService().deleteDeployment(deployment.getId(), true));
    ClockUtil.reset();
  }

  protected static ProcessEngineConfigurationImpl configuration(ProcessEngine processEngine) {
    return (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
  }

  protected void use(ProcessEngine target) {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(target.getProcessEngineConfiguration());
    when(processEngine.getIdentityService()).thenReturn(target.getIdentityService());
    when(processEngine.getTaskService()).thenReturn(target.getTaskService());
  }

  /** The REST date format writes the time zone of the JVM; compare the instants. */
  protected static Matcher<String> sameInstant(String expected) {
    return new TypeSafeMatcher<String>() {
      @Override
      protected boolean matchesSafely(String actual) {
        return parse(actual) == parse(expected);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("the same instant as ").appendValue(expected);
      }

      long parse(String value) {
        try {
          return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(value).getTime();
        } catch (ParseException e) {
          return Long.MIN_VALUE;
        }
      }
    };
  }

  protected static Map<String, Object> absence(String until, String substituteId, String note) {
    Map<String, Object> body = new HashMap<>();
    body.put("until", until);
    body.put("substituteId", substituteId);
    body.put("note", note);
    return body;
  }

  @Test
  public void shouldAnswerWithoutAbsence() {
    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("userId", equalTo("alice"))
        .body("enabled", equalTo(true))
        .body("unavailableReason", nullValue())
        .body("status", equalTo("none"))
        .body("substituteId", nullValue())
    .when().get(ABSENCE_URL);
  }

  @Test
  public void shouldSetReadAndClearTheAbsence() {
    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", "Notes in the wiki"))
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("status", equalTo("active"))
        .body("from", sameInstant("2026-11-21T08:00:00.000+0000"))
        .body("until", sameInstant(NEXT_WEEK))
        .body("substituteId", equalTo("bob"))
        .body("effectiveSubstituteId", equalTo("bob"))
        .body("note", equalTo("Notes in the wiki"))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("substituteId", equalTo("bob"))
    .when().get(ABSENCE_URL);

    given().pathParam("id", "bob")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("$", hasSize(1))
        .body("[0].userId", equalTo("alice"))
        .body("[0].until", sameInstant(NEXT_WEEK))
        .body("[0].effectiveSubstituteId", equalTo("bob"))
    .when().get(SUBSTITUTING_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
    .when().delete(ABSENCE_URL);

    assertThat(engine.getIdentityService().getUserInfoKeys("alice")).isEmpty();
  }

  @Test
  public void shouldSetAnUpcomingAbsence() {
    Map<String, Object> body = absence(NEXT_WEEK, "bob", null);
    body.put("from", TOMORROW);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(body)
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("status", equalTo("upcoming"))
        .body("from", sameInstant(TOMORROW))
        .body("effectiveSubstituteId", nullValue())
    .when().put(ABSENCE_URL);
  }

  @Test
  public void shouldRejectInvalidAbsences() {
    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "alice", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo("BadUserRequestException"))
        .body("message", containsString("own substitute"))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "nobody", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("'nobody' cannot be chosen"))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(null, "bob", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("until"))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence("next week", "bob", null))
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON)
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
    .when().put(ABSENCE_URL);
  }

  @Test
  public void shouldHandOverOpenTasks() {
    engine.getRepositoryService().createDeployment().addModelInstance("process.bpmn",
        Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
            .startEvent().userTask().camundaAssignee("alice").endEvent().done()).deploy();
    engine.getRuntimeService().startProcessInstanceByKey("process");
    engine.getRuntimeService().startProcessInstanceByKey("process");
    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect().statusCode(Status.OK.getStatusCode())
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").queryParam("maxTasks", 1)
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("substituteId", equalTo("bob"))
        .body("handedOver", equalTo(1))
        .body("notAllowed", equalTo(0))
        .body("remaining", equalTo(1))
    .when().post(HANDOVER_URL);

    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("handedOver", equalTo(1))
        .body("remaining", equalTo(0))
    .when().post(HANDOVER_URL);

    assertThat(engine.getTaskService().createTaskQuery().taskAssignee("bob").count()).isEqualTo(2);

    given().pathParam("id", "alice").queryParam("maxTasks", 0)
    .then().expect().statusCode(Status.BAD_REQUEST.getStatusCode())
    .when().post(HANDOVER_URL);
  }

  @Test
  public void shouldRefuseHandoverWithoutAbsence() {
    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("no current or upcoming absence"))
    .when().post(HANDOVER_URL);
  }

  @Test
  public void shouldLetUsersManageOnlyTheirOwnAbsence() {
    grant("carol", Resources.USER, "bob", Permissions.READ);
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("carol", Collections.emptyList());

    given().pathParam("id", "carol").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect().statusCode(Status.OK.getStatusCode())
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("AuthorizationException"))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
    .when().get(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
    .when().delete(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
    .when().post(HANDOVER_URL);
  }

  protected void grant(String userId, org.camunda.bpm.engine.authorization.Resource resource, String resourceId,
      org.camunda.bpm.engine.authorization.Permission... permissions) {
    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId(userId);
    grant.setResource(resource);
    grant.setResourceId(resourceId);
    for (org.camunda.bpm.engine.authorization.Permission permission : permissions) {
      grant.addPermission(permission);
    }
    engine.getAuthorizationService().saveAuthorization(grant);
  }

  @Test
  public void shouldAnswerASubstituteTheCallerMayNotSeeLikeOneThatDoesNotExist() {
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("carol", Collections.emptyList());
    String message = "The substitute '%s' cannot be chosen: there is no such user you may see, or tasks would come "
        + "back to 'carol' through the substitutes of absent substitutes. Choose someone else.";

    // bob exists, but carol has no READ on him
    given().pathParam("id", "carol").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo("BadUserRequestException"))
        .body("message", equalTo(String.format(message, "bob")))
    .when().put(ABSENCE_URL);

    given().pathParam("id", "carol").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "nobody", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo("BadUserRequestException"))
        .body("message", equalTo(String.format(message, "nobody")))
    .when().put(ABSENCE_URL);

    assertThat(engine.getIdentityService().getUserInfoKeys("carol")).isEmpty();
  }

  @Test
  public void shouldFollowAbsentSubstitutesOnlyAsFarAsTheCallerMaySee() {
    // alice -> bob (absent as well) -> carol
    engine.getIdentityService().clearAuthentication();
    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect().statusCode(Status.OK.getStatusCode()).body("effectiveSubstituteId", equalTo("bob"))
    .when().put(ABSENCE_URL);
    given().pathParam("id", "bob").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "carol", null))
    .then().expect().statusCode(Status.OK.getStatusCode())
    .when().put(ABSENCE_URL);
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("alice", Collections.emptyList());

    // alice may not see bob: her substitute, nothing about his absence
    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("substituteId", equalTo("bob"))
        .body("effectiveSubstituteId", equalTo("bob"))
    .when().get(ABSENCE_URL);

    requestAuthentication = null;
    configuration(engine).setAuthorizationEnabled(false);
    grant("alice", Resources.USER, "bob", Permissions.READ);
    grant("alice", Resources.USER, "carol", Permissions.READ);
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("alice", Collections.emptyList());

    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("effectiveSubstituteId", equalTo("carol"))
    .when().get(ABSENCE_URL);
  }

  @Test
  public void shouldLeaveTasksTheCallerMayNotAssign() {
    org.camunda.bpm.engine.authorization.Permission defaultPermission = configuration(engine).getDefaultUserPermissionForTask();
    // assignees may work on their tasks but not reassign them
    configuration(engine).setDefaultUserPermissionForTask(Permissions.TASK_WORK);
    try {
      grant("alice", Resources.USER, "bob", Permissions.READ);
      configuration(engine).setAuthorizationEnabled(true);
      engine.getRepositoryService().createDeployment().addModelInstance("process.bpmn",
          Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
              .startEvent().userTask().camundaAssignee("alice").endEvent().done()).deploy();
      engine.getRuntimeService().startProcessInstanceByKey("process");
      requestAuthentication = new Authentication("alice", Collections.emptyList());
      given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
      .then().expect().statusCode(Status.OK.getStatusCode())
      .when().put(ABSENCE_URL);

      given().pathParam("id", "alice")
      .then().expect()
          .statusCode(Status.OK.getStatusCode())
          .body("substituteId", equalTo("bob"))
          .body("handedOver", equalTo(0))
          .body("notAllowed", equalTo(1))
          .body("remaining", equalTo(1))
      .when().post(HANDOVER_URL);

      requestAuthentication = null;
      configuration(engine).setAuthorizationEnabled(false);
      assertThat(engine.getTaskService().createTaskQuery().taskAssignee("alice").count()).isEqualTo(1);
    } finally {
      configuration(engine).setDefaultUserPermissionForTask(defaultPermission);
    }
  }

  @Test
  public void shouldLetUserAdministratorsManageOtherAbsences() {
    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setGroupId("camunda-admin");
    grant.setResource(Resources.USER);
    grant.setResourceId(Authorization.ANY);
    grant.addPermission(Permissions.ALL);
    engine.getAuthorizationService().saveAuthorization(grant);
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("carol", List.of("camunda-admin"));

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("substituteId", equalTo("bob"))
    .when().put(ABSENCE_URL);
  }

  @Test
  public void shouldReportTheFeatureAsOffWithoutThePlugin() {
    use(engineWithoutPlugin);

    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("enabled", equalTo(false))
        .body("unavailableReason", containsString("not enabled"))
        .body("status", equalTo("none"))
    .when().get(ABSENCE_URL);

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("message", containsString("fasti.absence.enabled"))
    .when().put(ABSENCE_URL);
  }

  @Test
  public void shouldReportWhyAbsencesAreUnavailableWithoutIdentityTables() {
    use(engineWithoutIdentityTables);

    given().pathParam("id", "alice")
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("enabled", equalTo(false))
        .body("unavailableReason", containsString("ACT_ID_INFO"))
        .body("status", equalTo("none"))
    .when().get(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.OK.getStatusCode()).body("$", hasSize(0))
    .when().get(SUBSTITUTING_URL);
  }

  @Test
  public void shouldAnswerASetWithUpdatePermissionOnly() {
    grant("carol", Resources.USER, "alice", Permissions.UPDATE);
    grant("carol", Resources.USER, "bob", Permissions.READ);
    configuration(engine).setAuthorizationEnabled(true);
    requestAuthentication = new Authentication("carol", Collections.emptyList());

    given().pathParam("id", "alice").contentType(ContentType.JSON).body(absence(NEXT_WEEK, "bob", null))
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("status", equalTo("active"))
        .body("substituteId", equalTo("bob"))
        .body("effectiveSubstituteId", nullValue())
    .when().put(ABSENCE_URL);

    given().pathParam("id", "alice")
    .then().expect().statusCode(Status.FORBIDDEN.getStatusCode())
    .when().get(ABSENCE_URL);
  }
}
