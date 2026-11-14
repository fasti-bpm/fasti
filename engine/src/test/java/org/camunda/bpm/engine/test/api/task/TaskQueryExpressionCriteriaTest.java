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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.json.JsonTaskQueryConverter;
import org.camunda.bpm.engine.impl.persistence.entity.FilterEntity;
import org.camunda.bpm.engine.impl.util.JsonUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * The expressions of a query are stored by the name of the criterion they
 * belong to, which is the name of the method that receives their value. A
 * stored query must not be able to name any other method that way.
 */
public class TaskQueryExpressionCriteriaTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected TaskService taskService;
  protected FilterService filterService;

  @Before
  public void setUp() {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    taskService = engineRule.getTaskService();
    filterService = engineRule.getFilterService();

    Task assignedTask = taskService.newTask("assignedTask");
    assignedTask.setName("assignedTask");
    assignedTask.setAssignee("kermit");
    taskService.saveTask(assignedTask);
  }

  @After
  public void tearDown() {
    for (Filter filter : filterService.createFilterQuery().list()) {
      filterService.deleteFilter(filter.getId());
    }
    taskService.deleteTask("assignedTask", true);
  }

  @Test
  public void shouldSupportExpressionsOfTaskQueryOnly() throws Exception {
    // every criterion of the task query that can be set by an expression is supported
    Set<String> expressionMethods = new TreeSet<>();
    for (Method method : TaskQuery.class.getMethods()) {
      if (method.getName().endsWith("Expression")) {
        expressionMethods.add(method.getName());

        TaskQueryImpl query = new TaskQueryImpl();
        method.invoke(query, "${now()}");
        assertThat(query.getExpressions()).as(method.getName()).hasSize(1);

        processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
          query.ensureExpressionsAllowed();
          return null;
        });
      }
    }
    assertThat(expressionMethods).hasSize(20);

    // and nothing else
    TaskQueryImpl query = new TaskQueryImpl();
    query.addExpression("taskName", "${now()}");

    assertThatThrownBy(() -> processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
        query.ensureExpressionsAllowed();
        return null;
      }))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("cannot be set by an expression");
  }

  @Test
  public void shouldRejectExpressionForOtherCriterion() {
    // "nameExpression" does not exist, but "taskName" is a method of the query
    String filterId = insertFilter("{\"taskNameExpression\":\"${'assignedTask'}\"}");

    assertThatThrownBy(() -> filterService.count(filterId))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("The criterion 'taskName' of query type 'TaskQueryImpl' cannot be set by an expression");
    assertThatThrownBy(() -> filterService.list(filterId))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("The criterion 'taskName' of query type 'TaskQueryImpl' cannot be set by an expression");
    assertThatThrownBy(() -> filterService.list(filterId, query().taskAssignee("kermit")))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("The criterion 'taskName' of query type 'TaskQueryImpl' cannot be set by an expression");
  }

  @Test
  public void shouldRejectExpressionForMethodOfQuery() {
    for (String method : new String[] { "extend", "setExpressions", "addOrQuery", "executeList", "initializeFormKeys", "setCommandExecutor", "foo" }) {
      String filterId = insertFilter("{\"" + method + "Expression\":\"${null}\"}");

      assertThatThrownBy(() -> filterService.count(filterId))
        .as(method)
        .isInstanceOf(BadUserRequestException.class)
        .hasMessageContaining("cannot be set by an expression");
    }
  }

  @Test
  public void shouldRejectExpressionForOtherCriterionInOrQuery() {
    String filterId = insertFilter("{\"orQueries\":[{\"taskNameExpression\":\"${'assignedTask'}\"}]}");

    assertThatThrownBy(() -> filterService.count(filterId))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("cannot be set by an expression");
  }

  @Test
  public void shouldRejectSavingExpressionForOtherCriterion() {
    TaskQuery query = new JsonTaskQueryConverter().toObject(JsonUtil.asObject("{\"taskNameExpression\":\"${'assignedTask'}\"}"));

    assertThatThrownBy(() -> filterService.saveFilter(newFilter(query)))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("cannot be set by an expression");

    TaskQuery queryWithOrQuery = new JsonTaskQueryConverter()
        .toObject(JsonUtil.asObject("{\"orQueries\":[{\"taskNameExpression\":\"${'assignedTask'}\"}]}"));

    assertThatThrownBy(() -> filterService.saveFilter(newFilter(queryWithOrQuery)))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("cannot be set by an expression");

    assertThat(filterService.createFilterQuery().count()).isEqualTo(0);
  }

  @Test
  public void shouldStillEvaluateExpressionForSupportedCriterion() {
    String filterId = insertFilter("{\"taskAssigneeExpression\":\"${'kermit'}\"}");

    assertThat(filterService.count(filterId)).isEqualTo(1);
  }

  // helper ///////////////////////////////////////////////////////////////////

  protected TaskQuery query() {
    return taskService.createTaskQuery();
  }

  protected Filter newFilter(TaskQuery query) {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(query);
    return filter;
  }

  /**
   * Stores a filter bypassing validation, like a filter that was stored by
   * an earlier version.
   */
  protected String insertFilter(String queryJson) {
    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQueryInternal(queryJson);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });
  }

}
