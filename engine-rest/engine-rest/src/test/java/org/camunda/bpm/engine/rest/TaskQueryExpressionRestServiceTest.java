/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Sends task queries with expressions to the REST API. Unlike the other tests of
 * the REST API, the task and filter services are not mocked here but belong to an
 * actual process engine, so that it is the engine that decides on the expressions
 * of a query that was created from the request.
 */
public class TaskQueryExpressionRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String TASK_QUERY_URL = TEST_RESOURCE_ROOT_PATH + "/task";
  protected static final String TASK_COUNT_QUERY_URL = TASK_QUERY_URL + "/count";

  protected static final String FILTER_URL = TEST_RESOURCE_ROOT_PATH + FilterRestService.PATH;
  protected static final String CREATE_FILTER_URL = FILTER_URL + "/create";
  protected static final String SINGLE_FILTER_URL = FILTER_URL + "/{id}";
  protected static final String EXECUTE_SINGLE_RESULT_FILTER_URL = SINGLE_FILTER_URL + "/singleResult";
  protected static final String EXECUTE_LIST_FILTER_URL = SINGLE_FILTER_URL + "/list";
  protected static final String EXECUTE_COUNT_FILTER_URL = SINGLE_FILTER_URL + "/count";

  protected static final String ADHOC_QUERY_FAILURE_MESSAGE = "Expressions are forbidden in adhoc queries";
  protected static final String STORED_QUERY_FAILURE_MESSAGE = "Expressions are forbidden in stored queries";
  protected static final String ALLOW_LIST_FAILURE_MESSAGE = "is not allowed";

  public static final String STATE_MANIPULATING_EXPRESSION =
      "${''.getClass().forName('" + TaskQueryExpressionRestServiceTest.class.getName() + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

  public static long MUTABLE_FIELD = 0;

  protected static ProcessEngine engine;
  protected static ProcessEngineConfigurationImpl engineConfiguration;

  protected TaskService taskService;
  protected FilterService filterService;
  protected String filterId;

  @BeforeClass
  public static void buildEngine() {
    engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
      .setProcessEngineName("taskQueryExpressionEngine")
      .setJdbcUrl("jdbc:h2:mem:taskQueryExpressionEngine")
      .setJobExecutorActivate(false)
      .buildProcessEngine();
    engineConfiguration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @Before
  public void setUpEngineServices() {
    taskService = engine.getTaskService();
    filterService = engine.getFilterService();

    when(processEngine.getTaskService()).thenReturn(taskService);
    when(processEngine.getFilterService()).thenReturn(filterService);

    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().taskName("aTaskName"));
    filterId = filterService.saveFilter(filter).getId();

    MUTABLE_FIELD = 0;
  }

  @After
  public void tearDown() {
    engineConfiguration.setEnableExpressionsInAdhocQueries(false);
    engineConfiguration.setEnableExpressionsInStoredQueries(true);
    engineConfiguration.setQueryExpressionAllowListEnabled(true);

    for (Filter filter : filterService.createFilterQuery().list()) {
      filterService.deleteFilter(filter.getId());
    }
  }

  @Test
  public void testQueryWithExpression() {
    Map<String, Object> query = Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION);

    assertAdhocQueryIsRejected(query, TASK_QUERY_URL);
    assertAdhocQueryIsRejected(query, TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testQueryWithExpressionInOrQuery() {
    Map<String, Object> query = withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION));

    assertAdhocQueryIsRejected(query, TASK_QUERY_URL);
    assertAdhocQueryIsRejected(query, TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testQueryWithExpressionInSecondOrQuery() {
    Map<String, Object> query = new HashMap<>();
    query.put("name", "aTaskName");
    query.put("orQueries", new Object[] {
        Collections.singletonMap("assignee", "anAssignee"),
        Collections.singletonMap("assigneeExpression", STATE_MANIPULATING_EXPRESSION)
    });

    assertAdhocQueryIsRejected(query, TASK_QUERY_URL);
    assertAdhocQueryIsRejected(query, TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testQueryWithExpressionInOrQueryOfOrQuery() {
    Map<String, Object> query = withOrQuery(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION)));

    assertAdhocQueryIsRejected(query, TASK_QUERY_URL);
    assertAdhocQueryIsRejected(query, TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testQueryWithExpressionInOrQueryWhenEnabled() {
    // the assertions on the unchanged field made in this class are only meaningful
    // if the expression of a request is evaluated as soon as the engine permits it
    engineConfiguration.setEnableExpressionsInAdhocQueries(true);
    engineConfiguration.setQueryExpressionAllowListEnabled(false);

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION)))
    .when()
      .post(TASK_QUERY_URL);

    assertEquals(42, MUTABLE_FIELD);
  }

  @Test
  public void testQueryWithOrQueryWithoutExpression() {
    Map<String, Object> query = withOrQuery(Collections.singletonMap("assignee", "anAssignee"));

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("size()", equalTo(0))
    .when()
      .post(TASK_QUERY_URL);

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .post(TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testExecuteFilterWithExpressionInOrQueryOfExtendingQuery() {
    Map<String, Object> extendingQuery = withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION));

    assertExtendingQueryIsRejected(extendingQuery, EXECUTE_LIST_FILTER_URL);
    assertExtendingQueryIsRejected(extendingQuery, EXECUTE_COUNT_FILTER_URL);

    // the single result is reported as an invalid result of the filter
    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(extendingQuery)
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
    .when()
      .post(EXECUTE_SINGLE_RESULT_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);
  }

  @Test
  public void testExecuteFilterWithExpressionInOrQueryOfExtendingQueryWhenEnabled() {
    engineConfiguration.setEnableExpressionsInAdhocQueries(true);
    engineConfiguration.setQueryExpressionAllowListEnabled(false);

    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION)))
    .when()
      .post(EXECUTE_LIST_FILTER_URL);

    assertEquals(42, MUTABLE_FIELD);
  }

  @Test
  public void testCreateFilterWithExpressionInOrQuery() {
    engineConfiguration.setEnableExpressionsInStoredQueries(false);

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(filterWithQuery(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION))))
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(STORED_QUERY_FAILURE_MESSAGE))
    .when()
      .post(CREATE_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);
    assertEquals(1, filterService.createFilterQuery().count());
  }

  @Test
  public void testUpdateFilterWithExpressionInOrQuery() {
    engineConfiguration.setEnableExpressionsInStoredQueries(false);

    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(filterWithQuery(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION))))
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(STORED_QUERY_FAILURE_MESSAGE))
    .when()
      .put(SINGLE_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);

    // the stored filter can still be executed
    given()
      .pathParam("id", filterId)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .get(EXECUTE_COUNT_FILTER_URL);
  }

  @Test
  public void testQueryWithAllowListedExpression() {
    engineConfiguration.setEnableExpressionsInAdhocQueries(true);

    Map<String, Object> query = new HashMap<>();
    // the requests are not authenticated, so there is no current user to refer to
    query.put("assigneeExpression", "${'kermit'}");
    query.put("ownerExpression", "kermit");
    query.put("dueBeforeExpression", "${dateTime().plusDays(4).withTimeAtStartOfDay()}");
    query.put("createdBeforeExpression", "${ now() }");
    query.put("orQueries", Collections.singletonList(Collections.singletonMap("followUpAfterExpression", "${dateTime().minusWeeks(1)}")));

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("size()", equalTo(0))
    .when()
      .post(TASK_QUERY_URL);

    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .post(TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testQueryWithOtherExpressionWhenEnabled() {
    engineConfiguration.setEnableExpressionsInAdhocQueries(true);

    Map<String, Object> query = Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION);
    assertQueryIsRejectedByAllowList(query, TASK_QUERY_URL);
    assertQueryIsRejectedByAllowList(query, TASK_COUNT_QUERY_URL);

    query = withOrQuery(Collections.singletonMap("assigneeExpression", STATE_MANIPULATING_EXPRESSION));
    assertQueryIsRejectedByAllowList(query, TASK_QUERY_URL);
    assertQueryIsRejectedByAllowList(query, TASK_COUNT_QUERY_URL);

    query = withOrQuery(withOrQuery(Collections.singletonMap("assigneeExpression", "${currentUser().getClass()}")));
    assertQueryIsRejectedByAllowList(query, TASK_QUERY_URL);
    assertQueryIsRejectedByAllowList(query, TASK_COUNT_QUERY_URL);
  }

  @Test
  public void testCreateAndExecuteFilterWithAllowListedExpression() {
    Map<String, Object> query = new HashMap<>();
    // the requests are not authenticated, so there is no current user to refer to
    query.put("assigneeExpression", "${'kermit'}");
    query.put("dueBeforeExpression", "${dateTime().plusDays(4).withTimeAtStartOfDay()}");
    query.put("orQueries", Collections.singletonList(Collections.singletonMap("followUpBeforeExpression", "${ dateTime().plusWeeks(2) }")));

    String id = given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(filterWithQuery(query))
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
    .when()
      .post(CREATE_FILTER_URL)
    .path("id");

    given()
      .pathParam("id", id)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .get(EXECUTE_COUNT_FILTER_URL);

    given()
      .pathParam("id", id)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(Collections.singletonMap("name", "aTaskName"))
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("size()", equalTo(0))
    .when()
      .post(EXECUTE_LIST_FILTER_URL);
  }

  @Test
  public void testCreateFilterWithOtherExpression() {
    assertFilterIsRejectedByAllowList(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION));
    assertFilterIsRejectedByAllowList(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION)));
    assertFilterIsRejectedByAllowList(Collections.singletonMap("assigneeExpression", "${currentUser()}${currentUser().getClass()}"));

    assertEquals(1, filterService.createFilterQuery().count());
  }

  @Test
  public void testUpdateFilterWithOtherExpression() {
    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(filterWithQuery(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION))))
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
    .when()
      .put(SINGLE_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);

    // the stored filter can still be executed
    given()
      .pathParam("id", filterId)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .get(EXECUTE_COUNT_FILTER_URL);
  }

  @Test
  public void testExecuteFilterWithOtherExpressionStoredBefore() {
    // a filter that was stored while its expression was still accepted
    engineConfiguration.setQueryExpressionAllowListEnabled(false);
    Filter filter = filterService.newTaskFilter("legacyFilter");
    filter.setQuery(taskService.createTaskQuery().or().taskName("aTaskName").dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());
    String legacyFilterId = filterService.saveFilter(filter).getId();
    engineConfiguration.setQueryExpressionAllowListEnabled(true);

    for (String url : new String[] { EXECUTE_LIST_FILTER_URL, EXECUTE_COUNT_FILTER_URL }) {
      given()
        .pathParam("id", legacyFilterId)
      .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
        .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
      .when()
        .get(url);

      given()
        .pathParam("id", legacyFilterId)
        .contentType(POST_JSON_CONTENT_TYPE)
        .body(Collections.singletonMap("name", "aTaskName"))
      .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
        .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
      .when()
        .post(url);
    }

    given()
      .pathParam("id", legacyFilterId)
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
    .when()
      .get(EXECUTE_SINGLE_RESULT_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);

    // the filter can still be read
    given()
      .pathParam("id", legacyFilterId)
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("query.orQueries[0].dueAfterExpression", equalTo(STATE_MANIPULATING_EXPRESSION))
    .when()
      .get(SINGLE_FILTER_URL);

    // and deleted
    given()
      .pathParam("id", legacyFilterId)
    .then().expect()
      .statusCode(Status.NO_CONTENT.getStatusCode())
    .when()
      .delete(SINGLE_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);
  }

  @Test
  public void testExecuteFilterWithOtherExpressionInExtendingQueryWhenEnabled() {
    engineConfiguration.setEnableExpressionsInAdhocQueries(true);

    for (String url : new String[] { EXECUTE_LIST_FILTER_URL, EXECUTE_COUNT_FILTER_URL }) {
      given()
        .pathParam("id", filterId)
        .contentType(POST_JSON_CONTENT_TYPE)
        .body(withOrQuery(Collections.singletonMap("dueAfterExpression", STATE_MANIPULATING_EXPRESSION)))
      .then().expect()
        .statusCode(Status.BAD_REQUEST.getStatusCode())
        .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
        .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
      .when()
        .post(url);
    }

    // an allow-listed expression is fine
    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(withOrQuery(Collections.singletonMap("dueAfterExpression", "${now()}")))
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("count", equalTo(0))
    .when()
      .post(EXECUTE_COUNT_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);
  }

  protected void assertQueryIsRejectedByAllowList(Map<String, Object> query, String url) {
    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
    .when()
      .post(url);

    assertEquals(0, MUTABLE_FIELD);
  }

  protected void assertFilterIsRejectedByAllowList(Map<String, Object> query) {
    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(filterWithQuery(query))
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(ALLOW_LIST_FAILURE_MESSAGE))
    .when()
      .post(CREATE_FILTER_URL);

    assertEquals(0, MUTABLE_FIELD);
  }

  protected Map<String, Object> withOrQuery(Map<String, Object> orQuery) {
    return Collections.<String, Object>singletonMap("orQueries", Collections.singletonList(orQuery));
  }

  protected Map<String, Object> filterWithQuery(Map<String, Object> query) {
    Map<String, Object> filter = new HashMap<>();
    filter.put("resourceType", EntityTypes.TASK);
    filter.put("name", "anotherFilter");
    filter.put("query", query);
    return filter;
  }

  protected void assertAdhocQueryIsRejected(Map<String, Object> query, String url) {
    given()
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(query)
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(ADHOC_QUERY_FAILURE_MESSAGE))
    .when()
      .post(url);

    assertEquals(0, MUTABLE_FIELD);
  }

  protected void assertExtendingQueryIsRejected(Map<String, Object> extendingQuery, String url) {
    given()
      .pathParam("id", filterId)
      .contentType(POST_JSON_CONTENT_TYPE)
      .body(extendingQuery)
    .then().expect()
      .statusCode(Status.BAD_REQUEST.getStatusCode())
      .body("type", equalTo(BadUserRequestException.class.getSimpleName()))
      .body("message", containsString(ADHOC_QUERY_FAILURE_MESSAGE))
    .when()
      .post(url);

    assertEquals(0, MUTABLE_FIELD);
  }

}
