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
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.util.PluggableProcessEngineTest;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Thorben Lindhauer
 *
 */
public class TaskQueryDisabledAdhocExpressionsTest extends PluggableProcessEngineTest {

  protected static final String EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE = "Expressions are forbidden in adhoc queries. "
      + "This behavior can be toggled in the process engine configuration";
  public static final String STATE_MANIPULATING_EXPRESSION =
      "${''.getClass().forName('" + TaskQueryDisabledAdhocExpressionsTest.class.getName() + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

  public static long MUTABLE_FIELD = 0;

  @Test
  public void testDefaultSetting() {
    assertTrue(processEngineConfiguration.isEnableExpressionsInStoredQueries());
    assertFalse(processEngineConfiguration.isEnableExpressionsInAdhocQueries());
  }

  @Before
  public void setUp() throws Exception {
    MUTABLE_FIELD = 0;
  }

  @Test
  public void testAdhocExpressionsFail() {
    executeAndValidateFailingQuery(taskService.createTaskQuery().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().dueBeforeExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().dueDateExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().followUpAfterExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().followUpBeforeExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().followUpBeforeOrNotExistentExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().followUpDateExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskAssigneeLikeExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskCandidateGroupExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskCandidateGroupInExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskCandidateUserExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskCreatedAfterExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskCreatedBeforeExpression(STATE_MANIPULATING_EXPRESSION));
    executeAndValidateFailingQuery(taskService.createTaskQuery().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION));
  }

  @Test
  public void testExtendStoredFilterByExpression() {

    // given a stored filter
    TaskQuery taskQuery = taskService.createTaskQuery().dueAfterExpression("${now()}");
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskQuery);
    filterService.saveFilter(filter);

    // it is possible to execute the stored query with an expression
    assertEquals(Long.valueOf(0), filterService.count(filter.getId()));
    assertEquals(0, filterService.list(filter.getId()).size());

    // but it is not possible to executed the filter with an extended query that uses expressions
    extendFilterAndValidateFailingQuery(filter, taskService.createTaskQuery().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));

    // cleanup
    filterService.deleteFilter(filter.getId());
  }

  @Test
  public void testExtendStoredFilterByScalar() {
    // given a stored filter
    TaskQuery taskQuery = taskService.createTaskQuery().dueAfterExpression("${now()}");
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskQuery);
    filterService.saveFilter(filter);

    // it is possible to execute the stored query with an expression
    assertEquals(Long.valueOf(0), filterService.count(filter.getId()));
    assertEquals(0, filterService.list(filter.getId()).size());

    // and it is possible to extend the filter query when not using an expression
    assertEquals(Long.valueOf(0), filterService.count(filter.getId(), taskService.createTaskQuery().dueAfter(new Date())));
    assertEquals(0, filterService.list(filter.getId(), taskService.createTaskQuery().dueAfter(new Date())).size());

    // cleanup
    filterService.deleteFilter(filter.getId());
  }

  @Test
  public void testAdhocExpressionsInOrQueryFail() {
    assertAdhocQueryFails(taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());
    assertAdhocQueryFails(taskService.createTaskQuery().or().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION).endOr());
    assertAdhocQueryFails(taskService.createTaskQuery().or().taskCandidateGroupInExpression(STATE_MANIPULATING_EXPRESSION).endOr());

    // also when the expression is not in the first or query
    assertAdhocQueryFails(taskService.createTaskQuery()
        .or().taskName("aTaskName").endOr()
        .or().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION).endOr());
  }

  @Test
  public void testAdhocExpressionsInAttachedOrQueryFail() {
    // given an or query that is attached to an adhoc query
    TaskQueryImpl query = (TaskQueryImpl) taskService.createTaskQuery();
    query.addOrQuery((TaskQueryImpl) attachableOrQuery().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));

    // then
    assertAdhocQueryFails(query);

    // given an attached or query that has an or query attached itself
    query = (TaskQueryImpl) taskService.createTaskQuery();
    TaskQueryImpl orQuery = attachableOrQuery();
    orQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));
    query.addOrQuery(orQuery);

    // then
    assertAdhocQueryFails(query);
  }

  @Test
  public void testAdhocOrQueryWithoutExpression() {
    // given
    Task task = taskService.newTask();
    task.setAssignee("kermit");
    taskService.saveTask(task);

    try {
      // when
      TaskQuery query = taskService.createTaskQuery().or().taskAssignee("kermit").taskOwner("gonzo").endOr();
      TaskQueryImpl queryWithAttachedOrQuery = (TaskQueryImpl) taskService.createTaskQuery();
      queryWithAttachedOrQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskAssignee("kermit").taskOwner("gonzo"));

      // then
      assertEquals(1, query.count());
      assertEquals(1, query.list().size());
      assertEquals(1, queryWithAttachedOrQuery.count());
      assertEquals(1, queryWithAttachedOrQuery.list().size());
    } finally {
      taskService.deleteTask(task.getId(), true);
    }
  }

  @Test
  public void testAdhocExpressionsInOrQueryWhenEnabled() {
    // given
    Task task = taskService.newTask();
    task.setAssignee("kermit");
    taskService.saveTask(task);
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    try {
      // when
      TaskQuery query = taskService.createTaskQuery().or().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo").endOr();
      TaskQueryImpl queryWithAttachedOrQuery = (TaskQueryImpl) taskService.createTaskQuery();
      queryWithAttachedOrQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo"));

      // then
      assertEquals(1, query.count());
      assertEquals(1, query.list().size());
      assertEquals(1, queryWithAttachedOrQuery.count());
      assertEquals(1, queryWithAttachedOrQuery.list().size());
    } finally {
      processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);
      taskService.deleteTask(task.getId(), true);
    }
  }

  @Test
  public void testStateManipulatingExpressionIsEvaluatedWhenEnabled() {
    // the assertions on the unchanged field made in this class are only meaningful
    // if the expression does change the field as soon as it is evaluated
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    try {
      assertExpressionChangesField(taskService.createTaskQuery().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION));
      assertExpressionChangesField(taskService.createTaskQuery().or().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION).endOr());

      TaskQueryImpl queryWithAttachedOrQuery = (TaskQueryImpl) taskService.createTaskQuery();
      queryWithAttachedOrQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION));
      assertExpressionChangesField(queryWithAttachedOrQuery);
    } finally {
      processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);
    }
  }

  @Test
  public void testExpressionInOrQueryOfAttachedOrQueryIsNotEvaluated() {
    // given an attached or query that has an or query attached itself
    TaskQueryImpl query = (TaskQueryImpl) taskService.createTaskQuery();
    TaskQueryImpl orQuery = (TaskQueryImpl) attachableOrQuery().taskName("aTaskName");
    orQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskOwnerExpression(STATE_MANIPULATING_EXPRESSION));
    query.addOrQuery(orQuery);
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);
    // the expression is not on the allow-list for query expressions, which
    // rejects it wherever it occurs in the query
    processEngineConfiguration.setQueryExpressionAllowListEnabled(false);

    try {
      // when
      query.list();
      query.count();

      // then only the or queries of the executed query are a part of it
      assertTrue(fieldIsUnchanged());
    } finally {
      processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);
      processEngineConfiguration.setQueryExpressionAllowListEnabled(true);
    }
  }

  @Test
  public void testStoredFilterWithOrQueryExpression() {
    // given
    Task task = taskService.newTask();
    task.setAssignee("kermit");
    taskService.saveTask(task);

    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().or().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo").endOr());
    filterService.saveFilter(filter);

    TaskQueryImpl queryWithAttachedOrQuery = (TaskQueryImpl) taskService.createTaskQuery();
    queryWithAttachedOrQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo"));
    Filter filterWithAttachedOrQuery = filterService.newTaskFilter("filterWithAttachedOrQuery");
    filterWithAttachedOrQuery.setQuery(queryWithAttachedOrQuery);
    filterService.saveFilter(filterWithAttachedOrQuery);

    try {
      // then expressions in or queries of stored filters are evaluated
      assertEquals(Long.valueOf(1), filterService.count(filter.getId()));
      assertEquals(1, filterService.list(filter.getId()).size());
      assertEquals(Long.valueOf(1), filterService.count(filterWithAttachedOrQuery.getId()));
      assertEquals(1, filterService.list(filterWithAttachedOrQuery.getId()).size());

      // and the filters can be extended by queries without expressions
      assertEquals(Long.valueOf(1), filterService.count(filter.getId(), taskService.createTaskQuery().taskAssignee("kermit")));
      assertEquals(1, filterService.list(filter.getId(), taskService.createTaskQuery().or().taskName("foo").taskAssignee("kermit").endOr()).size());
    } finally {
      filterService.deleteFilter(filter.getId());
      filterService.deleteFilter(filterWithAttachedOrQuery.getId());
      taskService.deleteTask(task.getId(), true);
    }
  }

  @Test
  public void testExtendStoredFilterByOrQueryExpression() {
    // given a stored filter
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().or().dueAfterExpression("${now()}").taskName("aTaskName").endOr());
    filterService.saveFilter(filter);

    try {
      // then it is not possible to execute the filter with an extending query
      // that uses expressions in an or query
      assertExtendingFilterFails(filter,
          taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());

      TaskQueryImpl queryWithAttachedOrQuery = (TaskQueryImpl) taskService.createTaskQuery();
      queryWithAttachedOrQuery.addOrQuery((TaskQueryImpl) attachableOrQuery().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
      assertExtendingFilterFails(filter, queryWithAttachedOrQuery);
    } finally {
      filterService.deleteFilter(filter.getId());
    }
  }

  @Test
  public void testExtendStoredFilterByOrQueryExpressionWhenEnabled() {
    // given
    Task task = taskService.newTask();
    task.setAssignee("kermit");
    taskService.saveTask(task);

    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().taskAssignee("kermit"));
    filterService.saveFilter(filter);
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    try {
      // then
      assertEquals(Long.valueOf(1), filterService.count(filter.getId(),
          taskService.createTaskQuery().or().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo").endOr()));
      assertEquals(1, filterService.list(filter.getId(),
          taskService.createTaskQuery().or().taskAssigneeExpression("${'kermit'}").taskOwner("gonzo").endOr()).size());
    } finally {
      processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);
      filterService.deleteFilter(filter.getId());
      taskService.deleteTask(task.getId(), true);
    }
  }

  /**
   * Creates an or query the way it is done when a query is assembled from parts,
   * i.e. without using {@link TaskQuery#or()}.
   */
  protected TaskQueryImpl attachableOrQuery() {
    TaskQueryImpl orQuery = new TaskQueryImpl();
    orQuery.setOrQueryActive();
    return orQuery;
  }

  protected void assertAdhocQueryFails(TaskQuery query) {
    assertThatThrownBy(() -> query.list())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());

    assertThatThrownBy(() -> query.count())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());
  }

  protected void assertExpressionChangesField(TaskQuery query) {
    MUTABLE_FIELD = 0;

    // the expression is not on the allow-list for query expressions
    processEngineConfiguration.setQueryExpressionAllowListEnabled(false);
    try {
      query.list();
    } catch (ProcessEngineException e) {
      // the expression does not return a value the query can be executed with
    } finally {
      processEngineConfiguration.setQueryExpressionAllowListEnabled(true);
    }

    assertEquals(42, MUTABLE_FIELD);
  }

  protected void assertExtendingFilterFails(Filter filter, TaskQuery query) {
    assertThatThrownBy(() -> filterService.list(filter.getId(), query))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());

    assertThatThrownBy(() -> filterService.count(filter.getId(), query))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());
  }

  protected boolean fieldIsUnchanged() {
    return MUTABLE_FIELD == 0;
  }

  protected void executeAndValidateFailingQuery(TaskQuery query) {
    try {
      query.list();
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());

    try {
      query.count();
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());
  }

  protected void extendFilterAndValidateFailingQuery(Filter filter, TaskQuery query) {
    try {
      filterService.list(filter.getId(), query);
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());

    try {
      filterService.count(filter.getId(), query);
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_ADHOC_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());
  }
}
