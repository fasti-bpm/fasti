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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import dev.fasti.engine.foureyes.FourEyesPlugin;
import dev.fasti.engine.foureyes.FourEyesService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import io.restassured.http.ContentType;

/**
 * The four-eyes control over REST, against real in-memory engines (with and without the plugin);
 * the user of a request is set on the engine by a command interceptor, as the authentication
 * filter would.
 */
public class TaskFourEyesRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String TASK_URL = TEST_RESOURCE_ROOT_PATH + "/task/{id}";
  protected static final String FOUR_EYES_URL = TASK_URL + "/four-eyes";

  protected static volatile Authentication requestAuthentication;
  protected static ProcessEngine engine;
  protected static ProcessEngine engineWithoutPlugin;

  @BeforeClass
  public static void buildEngines() {
    engine = buildEngine("fourEyesRestEngine", true);
    engineWithoutPlugin = buildEngine("fourEyesRestEngineWithoutPlugin", false);
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
    if (withPlugin) {
      configuration.getProcessEnginePlugins().add(new FourEyesPlugin());
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
  }

  @After
  public void tearDown() {
    requestAuthentication = null;
    for (ProcessEngine target : List.of(engine, engineWithoutPlugin)) {
      ((ProcessEngineConfigurationImpl) target.getProcessEngineConfiguration()).setAuthorizationEnabled(false);
      for (Authorization authorization : target.getAuthorizationService().createAuthorizationQuery().list()) {
        target.getAuthorizationService().deleteAuthorization(authorization.getId());
      }
      target.getRepositoryService().createDeploymentQuery().list()
          .forEach(deployment -> target.getRepositoryService().deleteDeployment(deployment.getId(), true));
    }
  }

  protected void use(ProcessEngine target) {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(target.getProcessEngineConfiguration());
    when(processEngine.getTaskService()).thenReturn(target.getTaskService());
    when(processEngine.getFormService()).thenReturn(target.getFormService());
    when(processEngine.getIdentityService()).thenReturn(target.getIdentityService());
    when(processEngine.getRuntimeService()).thenReturn(target.getRuntimeService());
  }

  protected static void as(String userId) {
    requestAuthentication = userId == null ? null : new Authentication(userId, Collections.emptyList());
  }

  static BpmnModelInstance model() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").name("Draft contract").camundaCandidateUsers("alice,bob")
        .userTask("approve").name("Approve contract").camundaCandidateUsers("alice,bob")
          .boundaryEvent("rejected").error("REJECT").endEvent("rejectedEnd")
        .moveToActivity("approve")
        .endEvent().done();
    UserTask approve = model.getModelElementById("approve");
    ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
    approve.setExtensionElements(extensionElements);
    CamundaProperty property = model.newInstance(CamundaProperty.class);
    property.setCamundaName(FourEyesService.PROPERTY_NAME);
    property.setCamundaValue("draft");
    extensionElements.addExtensionElement(CamundaProperties.class).getCamundaProperties().add(property);
    return model;
  }

  /** Starts the process on the engine and lets alice complete the draft; returns the approval task. */
  protected Task draftedByAlice(ProcessEngine target) {
    target.getRepositoryService().createDeployment().addModelInstance("fourEyes.bpmn", model()).deploy();
    String pi = target.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
    Task draft = target.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
    target.getIdentityService().setAuthenticatedUserId("alice");
    try {
      target.getTaskService().claim(draft.getId(), "alice");
      target.getTaskService().complete(draft.getId());
    } finally {
      target.getIdentityService().clearAuthentication();
    }
    return target.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
  }

  @Test
  public void shouldReportTheStatusForTheUserOfTheRequest() {
    Task approve = draftedByAlice(engine);
    engine.getTaskService().setAssignee(approve.getId(), "alice");
    as("alice");

    given().pathParam("id", approve.getId())
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .contentType(ContentType.JSON)
        .body("taskId", equalTo(approve.getId()))
        .body("enabled", equalTo(true))
        .body("guarded", equalTo(true))
        .body("earlierSteps.id", contains("draft"))
        .body("earlierSteps.name", contains("Draft contract"))
        .body("userId", equalTo("alice"))
        .body("userExcluded", equalTo(true))
        .body("userSteps", contains("draft"))
        .body("assignee", equalTo("alice"))
        .body("assigneeExcluded", equalTo(true))
        .body("assigneeSteps", contains("draft"))
        .body("blocked", equalTo(true))
    .when().get(FOUR_EYES_URL);

    as("bob");
    given().pathParam("id", approve.getId())
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("userId", equalTo("bob"))
        .body("userExcluded", equalTo(false))
        .body("userSteps", empty())
        .body("assigneeExcluded", equalTo(true))
        .body("blocked", equalTo(true))
    .when().get(FOUR_EYES_URL);
  }

  @Test
  public void shouldReportATaskWithoutRule() {
    engine.getRepositoryService().createDeployment().addModelInstance("fourEyes.bpmn", model()).deploy();
    String pi = engine.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
    Task draft = engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
    as("alice");

    given().pathParam("id", draft.getId())
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("enabled", equalTo(true))
        .body("guarded", equalTo(false))
        .body("earlierSteps", empty())
        .body("blocked", equalTo(false))
    .when().get(FOUR_EYES_URL);
  }

  @Test
  public void shouldRefuseTheCompletionWith403() {
    Task approve = draftedByAlice(engine);
    engine.getTaskService().setAssignee(approve.getId(), "alice");
    as("alice");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .contentType(ContentType.JSON)
        .body("type", equalTo("FourEyesViolationException"))
        .body("message", equalTo("Four-eyes control: user 'alice' cannot complete task 'Approve contract' (approve) "
            + "after completing an earlier step it checks (draft). Another person must complete it."))
        .body("userId", nullValue())
    .when().post(TASK_URL + "/complete");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body("{\"withVariablesInReturn\": true}")
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
    .when().post(TASK_URL + "/complete");
  }

  @Test
  public void shouldRefuseAFormSubmitAndABpmnErrorWith403() {
    Task approve = draftedByAlice(engine);
    engine.getTaskService().setAssignee(approve.getId(), "alice");
    as("alice");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
    .when().post(TASK_URL + "/submit-form");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body("{\"errorCode\": \"REJECT\"}")
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
    .when().post(TASK_URL + "/bpmnError");
  }

  @Test
  public void shouldRefuseWhileTheAssigneeIsExcluded() {
    Task approve = draftedByAlice(engine);
    engine.getTaskService().setAssignee(approve.getId(), "alice");
    as("bob");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
        .body("message", containsString("is assigned to 'alice'"))
    .when().post(TASK_URL + "/complete");
  }

  @Test
  public void shouldLetAnotherUserComplete() {
    Task approve = draftedByAlice(engine);
    engine.getTaskService().setAssignee(approve.getId(), "bob");
    as("bob");

    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.NO_CONTENT.getStatusCode())
    .when().post(TASK_URL + "/complete");
  }

  @Test
  public void shouldAnswer403WhenAListenerCompletesTheGuardedTask() {
    // approve is completed right away by a listener, as alice, who just drafted
    BpmnModelInstance model = Bpmn.createExecutableProcess("autoApprove").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaAssignee("alice")
        .userTask("approve").camundaAssignee("alice")
          .camundaTaskListenerClass("create", AutoCompleteListener.class)
        .endEvent().done();
    UserTask approve = model.getModelElementById("approve");
    CamundaProperty property = model.newInstance(CamundaProperty.class);
    property.setCamundaName(FourEyesService.PROPERTY_NAME);
    property.setCamundaValue("draft");
    approve.getExtensionElements().addExtensionElement(CamundaProperties.class).getCamundaProperties().add(property);
    engine.getRepositoryService().createDeployment().addModelInstance("autoApprove.bpmn", model).deploy();
    String pi = engine.getRuntimeService().startProcessInstanceByKey("autoApprove").getId();
    Task draft = engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
    as("alice");

    given().pathParam("id", draft.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
    .when().post(TASK_URL + "/complete");
  }

  public static class AutoCompleteListener implements org.camunda.bpm.engine.delegate.TaskListener {
    @Override
    public void notify(org.camunda.bpm.engine.delegate.DelegateTask delegateTask) {
      delegateTask.complete();
    }
  }

  @Test
  public void shouldAnswer404ForAMissingTask() {
    as("alice");
    given().pathParam("id", "missing")
    .then().expect()
        .statusCode(Status.NOT_FOUND.getStatusCode())
        .body("type", equalTo("InvalidRequestException"))
        .body("message", equalTo("No matching task with id missing"))
    .when().get(FOUR_EYES_URL);
  }

  @Test
  public void shouldAnswer403WithoutReadPermission() {
    Task approve = draftedByAlice(engine);
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).setAuthorizationEnabled(true);
    as("mallory");

    given().pathParam("id", approve.getId())
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("AuthorizationException"))
    .when().get(FOUR_EYES_URL);
  }

  // the record of the control cannot be changed through the API

  protected static final String RECORD = FourEyesService.VARIABLE_PREFIX + "draft";
  protected static final String TASK_VARIABLES_URL = TASK_URL + "/variables";
  protected static final String TASK_VARIABLE_URL = TASK_VARIABLES_URL + "/{name}";
  protected static final String INSTANCE_VARIABLES_URL = TEST_RESOURCE_ROOT_PATH + "/process-instance/{id}/variables";
  protected static final String INSTANCE_VARIABLE_URL = INSTANCE_VARIABLES_URL + "/{name}";
  protected static final String EXECUTION_LOCAL_VARIABLE_URL = TEST_RESOURCE_ROOT_PATH
      + "/execution/{id}/localVariables/{name}";
  protected static final String EMPTY_RECORD = "{\"value\": \"[]\", \"type\": \"String\"}";
  protected static final String SET_RECORD = "{\"modifications\": {\"" + RECORD + "\": " + EMPTY_RECORD + "}}";
  protected static final String DELETE_RECORD = "{\"deletions\": [\"" + RECORD + "\"]}";
  protected static final String WITH_RECORD = "{\"variables\": {\"" + RECORD + "\": " + EMPTY_RECORD + "}}";

  protected void expectReserved(io.restassured.specification.RequestSpecification request, String method, String url) {
    request
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .contentType(ContentType.JSON)
        .body("type", equalTo("AuthorizationException"))
        .body("message", containsString("Four-eyes control: variable '" + RECORD + "' records who completed a step "
            + "and is written by the engine only; it cannot be set or removed."))
    .when().request(method, url);
  }

  @Test
  public void shouldRefuseChangesOfTheRecordThroughTheTaskVariables() {
    // authorization on: as a candidate, alice may update the variables of the task
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).setAuthorizationEnabled(true);
    Task approve = draftedByAlice(engine);
    as("alice");

    expectReserved(given().pathParam("id", approve.getId()).pathParam("name", RECORD)
        .contentType(ContentType.JSON).body(EMPTY_RECORD), "PUT", TASK_VARIABLE_URL);
    expectReserved(given().pathParam("id", approve.getId()).pathParam("name", RECORD), "DELETE", TASK_VARIABLE_URL);
    expectReserved(given().pathParam("id", approve.getId())
        .contentType(ContentType.JSON).body(SET_RECORD), "POST", TASK_VARIABLES_URL);
    expectReserved(given().pathParam("id", approve.getId())
        .contentType(ContentType.JSON).body(DELETE_RECORD), "POST", TASK_VARIABLES_URL);

    // a variable of her own she may set
    given().pathParam("id", approve.getId()).pathParam("name", "amount")
        .contentType(ContentType.JSON).body("{\"value\": 10, \"type\": \"Integer\"}")
    .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
    .when().put(TASK_VARIABLE_URL);

    // and she is still refused
    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.FORBIDDEN.getStatusCode())
        .body("type", equalTo("FourEyesViolationException"))
    .when().post(TASK_URL + "/complete");
    as(null);
    ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).setAuthorizationEnabled(false);
    org.junit.Assert.assertEquals("[\"alice\"]",
        engine.getRuntimeService().getVariable(approve.getProcessInstanceId(), RECORD));
  }

  @Test
  public void shouldRefuseChangesOfTheRecordWithACompletionOrAForm() {
    Task approve = draftedByAlice(engine);
    // bob may complete the task, but not with a record of his making
    as("bob");

    expectReserved(given().pathParam("id", approve.getId())
        .contentType(ContentType.JSON).body(WITH_RECORD), "POST", TASK_URL + "/complete");
    expectReserved(given().pathParam("id", approve.getId())
        .contentType(ContentType.JSON).body(WITH_RECORD), "POST", TASK_URL + "/submit-form");

    as(null);
    org.junit.Assert.assertEquals(1, engine.getTaskService().createTaskQuery().taskId(approve.getId()).count());
    org.junit.Assert.assertEquals("[\"alice\"]",
        engine.getRuntimeService().getVariable(approve.getProcessInstanceId(), RECORD));
  }

  @Test
  public void shouldRefuseChangesOfTheRecordThroughTheProcessInstanceAndExecutionVariables() {
    Task approve = draftedByAlice(engine);
    String pi = approve.getProcessInstanceId();
    as("bob");

    expectReserved(given().pathParam("id", pi).pathParam("name", RECORD)
        .contentType(ContentType.JSON).body(EMPTY_RECORD), "PUT", INSTANCE_VARIABLE_URL);
    expectReserved(given().pathParam("id", pi).pathParam("name", RECORD), "DELETE", INSTANCE_VARIABLE_URL);
    expectReserved(given().pathParam("id", pi)
        .contentType(ContentType.JSON).body(SET_RECORD), "POST", INSTANCE_VARIABLES_URL);
    expectReserved(given().pathParam("id", pi)
        .contentType(ContentType.JSON).body(DELETE_RECORD), "POST", INSTANCE_VARIABLES_URL);
    expectReserved(given().pathParam("id", pi).pathParam("name", RECORD)
        .contentType(ContentType.JSON).body(EMPTY_RECORD), "PUT", EXECUTION_LOCAL_VARIABLE_URL);
    expectReserved(given().pathParam("id", pi).pathParam("name", RECORD), "DELETE", EXECUTION_LOCAL_VARIABLE_URL);

    as(null);
    org.junit.Assert.assertEquals("[\"alice\"]", engine.getRuntimeService().getVariable(pi, RECORD));
  }

  @Test
  public void shouldNotReserveTheVariablesWithoutPlugin() {
    use(engineWithoutPlugin);
    Task approve = draftedByAlice(engineWithoutPlugin);
    as("alice");

    given().pathParam("id", approve.getId()).pathParam("name", RECORD)
        .contentType(ContentType.JSON).body(EMPTY_RECORD)
    .then().expect().statusCode(Status.NO_CONTENT.getStatusCode())
    .when().put(TASK_VARIABLE_URL);
  }

  @Test
  public void shouldReportTheControlAsDisabledWithoutPlugin() {
    use(engineWithoutPlugin);
    Task approve = draftedByAlice(engineWithoutPlugin);
    as("alice");

    given().pathParam("id", approve.getId())
    .then().expect()
        .statusCode(Status.OK.getStatusCode())
        .body("enabled", equalTo(false))
        .body("guarded", equalTo(false))
        .body("blocked", equalTo(false))
    .when().get(FOUR_EYES_URL);

    engineWithoutPlugin.getTaskService().setAssignee(approve.getId(), "alice");
    given().pathParam("id", approve.getId()).contentType(ContentType.JSON).body(EMPTY_JSON_OBJECT)
    .then().expect()
        .statusCode(Status.NO_CONTENT.getStatusCode())
    .when().post(TASK_URL + "/complete");
  }
}
