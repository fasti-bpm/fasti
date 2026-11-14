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
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.FilterEntity;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Thorben Lindhauer
 *
 */
public class TaskQueryDisabledStoredExpressionsTest {

  protected static final String EXPECTED_STORED_QUERY_FAILURE_MESSAGE =
      "Expressions are forbidden in stored queries. This behavior can be toggled in the process engine configuration";
  public static final String STATE_MANIPULATING_EXPRESSION =
      "${''.getClass().forName('" + TaskQueryDisabledStoredExpressionsTest.class.getName() + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

  public static long MUTABLE_FIELD = 0;

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      "org/camunda/bpm/engine/test/api/task/task-query-disabled-stored-expressions-test.camunda.cfg.xml");
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected TaskService taskService;
  protected FilterService filterService;

  @Before
  public void setUp() {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    taskService = engineRule.getTaskService();
    filterService = engineRule.getFilterService();
    MUTABLE_FIELD = 0;
  }

  @Test
  public void testStoreFilterWithoutExpression() {
    TaskQuery taskQuery = taskService.createTaskQuery().dueAfter(new Date());
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskQuery);

    // saving the filter suceeds
    filterService.saveFilter(filter);
    assertEquals(1, filterService.createFilterQuery().count());

    // cleanup
    filterService.deleteFilter(filter.getId());
  }

  @Test
  public void testStoreFilterWithExpression() {
    TaskQuery taskQuery = taskService.createTaskQuery().dueAfterExpression(STATE_MANIPULATING_EXPRESSION);
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskQuery);

    try {
      filterService.saveFilter(filter);
    } catch (ProcessEngineException e) {
      testRule.assertTextPresent(EXPECTED_STORED_QUERY_FAILURE_MESSAGE, e.getMessage());
    }
    assertTrue(fieldIsUnchanged());
  }

  @Test
  public void testUpdateFilterWithExpression() {
    // given a stored filter
    TaskQuery taskQuery = taskService.createTaskQuery().dueAfter(new Date());
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskQuery);
    filterService.saveFilter(filter);

    // updating the filter with an expression does not suceed
    filter.setQuery(taskQuery.dueBeforeExpression(STATE_MANIPULATING_EXPRESSION));
    assertEquals(1, filterService.createFilterQuery().count());

    try {
      filterService.saveFilter(filter);
    } catch (ProcessEngineException e) {
      testRule.assertTextPresent(EXPECTED_STORED_QUERY_FAILURE_MESSAGE, e.getMessage());
    }
    assertTrue(fieldIsUnchanged());

    // cleanup
    filterService.deleteFilter(filter.getId());
  }

  @Test
  public void testCannotExecuteStoredFilter() {
    final TaskQuery filterQuery = taskService.createTaskQuery().dueAfterExpression(STATE_MANIPULATING_EXPRESSION);

    // store a filter bypassing validation
    // the API way of doing this would be by reconfiguring the engine
    String filterId = processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQuery(filterQuery);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });

    extendFilterAndValidateFailingQuery(filterId, null);

    // cleanup
    filterService.deleteFilter(filterId);
  }

  @Test
  public void testStoreFilterWithOrQueryWithoutExpression() {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().or().taskAssignee("kermit").taskOwner("gonzo").endOr());

    // saving and executing the filter succeeds
    filterService.saveFilter(filter);
    assertEquals(Long.valueOf(0), filterService.count(filter.getId()));
    assertEquals(0, filterService.list(filter.getId()).size());

    // cleanup
    filterService.deleteFilter(filter.getId());
  }

  @Test
  public void testStoreFilterWithOrQueryExpression() {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());

    assertSavingFilterFails(filter);
    assertEquals(0, filterService.createFilterQuery().count());
  }

  @Test
  public void testStoreFilterWithAttachedOrQueryExpression() {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(queryWithAttachedOrQueryExpression());

    assertSavingFilterFails(filter);
    assertEquals(0, filterService.createFilterQuery().count());
  }

  @Test
  public void testUpdateFilterWithOrQueryExpression() {
    // given a stored filter
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().dueAfter(new Date()));
    filterService.saveFilter(filter);

    try {
      // then updating the filter with an expression in an or query does not succeed
      filter.setQuery(taskService.createTaskQuery().or().dueBeforeExpression(STATE_MANIPULATING_EXPRESSION).endOr());
      assertSavingFilterFails(filter);

      filter.setQuery(queryWithAttachedOrQueryExpression());
      assertSavingFilterFails(filter);
    } finally {
      filterService.deleteFilter(filter.getId());
    }
  }

  @Test
  public void testCannotExecuteStoredFilterWithOrQueryExpression() {
    String filterId = insertFilter(taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());

    try {
      assertExecutingFilterFails(filterId, null);
      assertExecutingFilterFails(filterId, taskService.createTaskQuery().taskName("aTaskName"));
    } finally {
      filterService.deleteFilter(filterId);
    }
  }

  @Test
  public void testCannotExecuteStoredFilterWithAttachedOrQueryExpression() {
    String filterId = insertFilter(queryWithAttachedOrQueryExpression());

    try {
      assertExecutingFilterFails(filterId, null);
      assertExecutingFilterFails(filterId, taskService.createTaskQuery().or().taskName("aTaskName").endOr());
    } finally {
      filterService.deleteFilter(filterId);
    }
  }

  @Test
  public void testCannotExtendStoredFilterByOrQueryExpression() {
    // given a stored filter without expressions
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().taskName("aTaskName"));
    filterService.saveFilter(filter);

    // and an extending query that is not subject to the adhoc query validation
    TaskQueryImpl extendingQuery = new TaskQueryImpl();
    TaskQueryImpl orQuery = new TaskQueryImpl();
    orQuery.setOrQueryActive();
    orQuery.dueAfterExpression(STATE_MANIPULATING_EXPRESSION);
    extendingQuery.addOrQuery(orQuery);

    try {
      // then the extended query is validated as a stored query
      assertExecutingFilterFails(filter.getId(), extendingQuery);
    } finally {
      filterService.deleteFilter(filter.getId());
    }
  }

  @Test
  public void testCannotSaveExtendedFilterWithOrQueryExpression() {
    // given a stored filter that has an expression in an or query
    String filterId = insertFilter(taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());

    try {
      // when the filter is extended
      Filter extendedFilter = filterService.getFilter(filterId).extend(taskService.createTaskQuery().taskName("aTaskName"));

      // then the or query is still validated when saving the extended filter
      assertSavingFilterFails(extendedFilter);
      assertEquals(1, filterService.createFilterQuery().count());
    } finally {
      filterService.deleteFilter(filterId);
    }
  }

  @Test
  public void testCannotSaveFilterExtendedByOrQueryExpression() {
    // given a stored filter without expressions
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(taskService.createTaskQuery().taskName("aTaskName"));
    filterService.saveFilter(filter);

    try {
      // when the filter is extended by a query that has an expression in an or query
      Filter extendedFilter = filter.extend(taskService.createTaskQuery().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr());
      Filter filterExtendedByAttachedOrQuery = filter.extend(queryWithAttachedOrQueryExpression());

      // then the extended filter cannot be saved
      assertSavingFilterFails(extendedFilter);
      assertSavingFilterFails(filterExtendedByAttachedOrQuery);
      assertEquals(1, filterService.createFilterQuery().count());
    } finally {
      filterService.deleteFilter(filter.getId());
    }
  }

  protected TaskQuery queryWithAttachedOrQueryExpression() {
    TaskQueryImpl orQuery = new TaskQueryImpl();
    orQuery.setOrQueryActive();
    orQuery.dueAfterExpression(STATE_MANIPULATING_EXPRESSION);

    TaskQueryImpl query = (TaskQueryImpl) taskService.createTaskQuery();
    query.addOrQuery(orQuery);
    return query;
  }

  /**
   * Stores a filter bypassing validation; the API way of doing this would be
   * by reconfiguring the engine.
   */
  protected String insertFilter(final TaskQuery filterQuery) {
    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQuery(filterQuery);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });
  }

  protected void assertSavingFilterFails(Filter filter) {
    assertThatThrownBy(() -> filterService.saveFilter(filter))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining(EXPECTED_STORED_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());
  }

  protected void assertExecutingFilterFails(String filterId, TaskQuery extendingQuery) {
    assertThatThrownBy(() -> filterService.list(filterId, extendingQuery))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_STORED_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());

    assertThatThrownBy(() -> filterService.count(filterId, extendingQuery))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_STORED_QUERY_FAILURE_MESSAGE);
    assertTrue(fieldIsUnchanged());
  }

  protected boolean fieldIsUnchanged() {
    return MUTABLE_FIELD == 0;
  }

  protected void extendFilterAndValidateFailingQuery(String filterId, TaskQuery query) {
    try {
      filterService.list(filterId, query);
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_STORED_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());

    try {
      filterService.count(filterId, query);
    } catch (BadUserRequestException e) {
      testRule.assertTextPresent(EXPECTED_STORED_QUERY_FAILURE_MESSAGE, e.getMessage());
    }

    assertTrue(fieldIsUnchanged());
  }
}
