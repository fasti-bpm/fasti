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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.el.QueryExpressionAllowList;
import org.camunda.bpm.engine.impl.el.QueryExpressionValidator;
import org.camunda.bpm.engine.impl.persistence.entity.FilterEntity;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.mock.Mocks;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class TaskQueryExpressionAllowListConfigurationTest {

  public static final String STATE_MANIPULATING_EXPRESSION =
      "${''.getClass().forName('" + TaskQueryExpressionAllowListConfigurationTest.class.getName() + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

  public static long MUTABLE_FIELD = 0;

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setEnableExpressionsInAdhocQueries(true);
    configuration.setAllowedExpressionFunctionsInQueries(" concat, groupOf ,,getGroup");
    configuration.setAllowedExpressionBeansInQueries("groupBean");
    configuration.setAllowedExpressionPropertiesInQueries("group");
  });

  @Rule
  public ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected TaskService taskService;
  protected FilterService filterService;
  protected IdentityService identityService;

  protected QueryExpressionValidator configuredValidator;

  @Before
  public void setUp() throws Exception {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    taskService = engineRule.getTaskService();
    filterService = engineRule.getFilterService();
    identityService = engineRule.getIdentityService();

    configuredValidator = processEngineConfiguration.getQueryExpressionValidator();

    processEngineConfiguration.getExpressionManager()
      .addFunction("groupOf", TaskQueryExpressionAllowListConfigurationTest.class.getMethod("groupOf", String.class));
    Mocks.register("groupBean", new GroupBean());
    Mocks.register("otherBean", new GroupBean());

    Task task = taskService.newTask("task");
    task.setAssignee("kermit");
    taskService.saveTask(task);
    taskService.addCandidateGroup("task", "group-of-kermit");

    identityService.setAuthentication("kermit", Collections.singletonList("accounting"));

    MUTABLE_FIELD = 0;
  }

  @After
  public void tearDown() {
    processEngineConfiguration.setQueryExpressionAllowListEnabled(true);
    processEngineConfiguration.setQueryExpressionValidator(configuredValidator);
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    identityService.clearAuthentication();
    Mocks.reset();

    for (Filter filter : filterService.createFilterQuery().list()) {
      filterService.deleteFilter(filter.getId());
    }
    taskService.deleteTask("task", true);
  }

  @Test
  public void shouldEnableAllowListByDefault() {
    ProcessEngineConfigurationImpl defaultConfiguration = new org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration();

    assertThat(defaultConfiguration.isQueryExpressionAllowListEnabled()).isTrue();
    assertThat(defaultConfiguration.getAllowedExpressionFunctionsInQueries()).isNull();
    assertThat(defaultConfiguration.getAllowedExpressionBeansInQueries()).isNull();
    assertThat(defaultConfiguration.getAllowedExpressionPropertiesInQueries()).isNull();
    assertThat(defaultConfiguration.isEnableExpressionsInAdhocQueries()).isFalse();
    assertThat(defaultConfiguration.isEnableExpressionsInStoredQueries()).isTrue();
  }

  @Test
  public void shouldCreateAllowListFromConfiguration() {
    assertThat(configuredValidator).isInstanceOf(QueryExpressionAllowList.class);

    QueryExpressionAllowList allowList = (QueryExpressionAllowList) configuredValidator;
    assertThat(allowList.getAdditionalFunctions()).containsExactly("concat", "groupOf", "getGroup");
    assertThat(allowList.getBeans()).containsExactly("groupBean");
    assertThat(allowList.getProperties()).containsExactly("group");
  }

  // extended allow-list //////////////////////////////////////////////////////

  @Test
  public void shouldAcceptConfiguredMethod() {
    assertThat(query().taskAssigneeLikeExpression("${'%'.concat(currentUser())}").count()).isEqualTo(1);
    assertThat(query().taskAssigneeLikeExpression("${'%'.concat('fozzie')}").count()).isEqualTo(0);

    String filterId = saveFilter(query().taskAssigneeLikeExpression("${'%'.concat(currentUser())}"));
    assertThat(filterService.count(filterId)).isEqualTo(1);
  }

  @Test
  public void shouldAcceptConfiguredFunction() {
    assertThat(query().taskCandidateGroupExpression("${groupOf(currentUser())}").includeAssignedTasks().count()).isEqualTo(1);
    assertThat(query().taskCandidateGroupExpression("${groupOf('fozzie')}").includeAssignedTasks().count()).isEqualTo(0);

    String filterId = saveFilter(query().taskCandidateGroupExpression("${groupOf(currentUser())}").includeAssignedTasks());
    assertThat(filterService.count(filterId)).isEqualTo(1);
  }

  @Test
  public void shouldAcceptConfiguredBeanWithPropertyAndMethod() {
    assertThat(query().taskCandidateGroupExpression("${groupBean.group}").includeAssignedTasks().count()).isEqualTo(1);
    assertThat(query().taskCandidateGroupExpression("${groupBean.getGroup()}").includeAssignedTasks().count()).isEqualTo(1);

    String filterId = saveFilter(query().taskCandidateGroupExpression("${groupBean.group}").includeAssignedTasks());
    assertThat(filterService.count(filterId)).isEqualTo(1);
  }

  @Test
  public void shouldRejectEverythingElse() {
    for (String expression : new String[] {
        STATE_MANIPULATING_EXPRESSION,
        "${otherBean.group}",
        "${groupBean.secret}",
        "${groupBean.getSecret()}",
        "${groupBean.getClass()}",
        "${groupBean.group.getClass()}",
        "${groupBean.class}",
        "${groupOf(''.getClass())}",
        "${'%'.concat(''.getClass())}",
        "${'%'.concat(currentUser()).getClass()}",
        "${'%' += currentUser()}",
        "${execution}"}) {

      assertThatThrownBy(() -> query().taskCandidateGroupExpression(expression).includeAssignedTasks().count())
        .as(expression)
        .isInstanceOf(BadUserRequestException.class)
        .hasMessageContaining("is not allowed");
      assertThatThrownBy(() -> saveFilter(query().taskCandidateGroupExpression(expression).includeAssignedTasks()))
        .as(expression)
        .isInstanceOf(BadUserRequestException.class)
        .hasMessageContaining("is not allowed");
    }
    assertThat(MUTABLE_FIELD).isZero();
    assertThat(filterService.createFilterQuery().count()).isEqualTo(0);
  }

  // custom validator /////////////////////////////////////////////////////////

  @Test
  public void shouldUseConfiguredValidator() {
    processEngineConfiguration.setQueryExpressionValidator(expression -> {
      if (!"${otherBean.group}".equals(expression)) {
        throw new BadUserRequestException("rejected by custom validator");
      }
    });

    // what the validator accepts is evaluated by the expression manager
    assertThat(query().taskCandidateGroupExpression("${otherBean.group}").includeAssignedTasks().count()).isEqualTo(1);
    String filterId = saveFilter(query().taskCandidateGroupExpression("${otherBean.group}").includeAssignedTasks());
    assertThat(filterService.count(filterId)).isEqualTo(1);

    // anything else is not
    assertThatThrownBy(() -> query().taskAssigneeExpression("${currentUser()}").count())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("rejected by custom validator");
    assertThatThrownBy(() -> saveFilter(query().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION)))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("rejected by custom validator");
    assertThatThrownBy(() -> filterService.count(filterId, query().or().taskName("task").taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION).endOr()))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("rejected by custom validator");
    assertThat(MUTABLE_FIELD).isZero();
  }

  @Test
  public void shouldRejectAllExpressionsWithoutValidator() {
    processEngineConfiguration.setQueryExpressionValidator(null);

    assertThatThrownBy(() -> query().taskAssigneeExpression("${currentUser()}").count())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("queryExpressionValidator");
    assertThatThrownBy(() -> saveFilter(query().taskAssigneeExpression("${currentUser()}")))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("queryExpressionValidator");
    assertThatThrownBy(() -> filterService.count(insertFilter(query().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION))))
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("queryExpressionValidator");
    assertThat(MUTABLE_FIELD).isZero();

    // queries without expressions are not affected
    assertThat(query().taskAssignee("kermit").count()).isEqualTo(1);
  }

  // switched off /////////////////////////////////////////////////////////////

  @Test
  public void shouldEvaluateAnyExpressionWhenSwitchedOff() {
    processEngineConfiguration.setQueryExpressionAllowListEnabled(false);

    // adhoc query
    assertExpressionChangesField(() -> query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).count());
    assertExpressionChangesField(() -> query().or().taskName("task").dueAfterExpression(STATE_MANIPULATING_EXPRESSION).endOr().count());

    // stored query
    String filterId = saveFilter(query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
    assertThat(MUTABLE_FIELD).isZero();
    assertExpressionChangesField(() -> filterService.count(filterId));

    // extending query
    String otherFilterId = saveFilter(query().taskName("task"));
    assertExpressionChangesField(() -> filterService.count(otherFilterId, query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION)));

    // other expressions work as before
    assertThat(query().taskAssigneeExpression("${currentUser()}").count()).isEqualTo(1);
    assertThat(query().taskCandidateGroupExpression("${otherBean.group}").includeAssignedTasks().count()).isEqualTo(1);
    assertThat(query().taskAssigneeExpression("${currentUser().toString()}").count()).isEqualTo(1);
  }

  @Test
  public void shouldStillObserveOtherSettingsWhenSwitchedOff() {
    processEngineConfiguration.setQueryExpressionAllowListEnabled(false);
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);

    assertThatThrownBy(() -> query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).count())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("Expressions are forbidden in adhoc queries");

    // and expressions are still limited to the criteria that support them
    TaskQueryImpl query = (TaskQueryImpl) query();
    query.addExpression("taskName", "${'task'}");
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    assertThatThrownBy(() -> query.count())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining("cannot be set by an expression");
    assertThat(MUTABLE_FIELD).isZero();
  }

  // helper ///////////////////////////////////////////////////////////////////

  protected TaskQuery query() {
    return taskService.createTaskQuery();
  }

  protected String saveFilter(TaskQuery query) {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(query);
    return filterService.saveFilter(filter).getId();
  }

  protected String insertFilter(TaskQuery query) {
    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQuery(query);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });
  }

  protected void assertExpressionChangesField(Runnable action) {
    MUTABLE_FIELD = 0;

    try {
      action.run();
    } catch (ProcessEngineException e) {
      // the expression does not return a value the query can be executed with
    }

    assertThat(MUTABLE_FIELD).isEqualTo(42);
    MUTABLE_FIELD = 0;
  }

  public static String groupOf(String user) {
    return "group-of-" + user;
  }

  public static class GroupBean {

    public String getGroup() {
      return "group-of-kermit";
    }

    public String getSecret() {
      return "secret";
    }

    public List<String> getGroups() {
      return Arrays.asList("group-of-kermit");
    }
  }

}
