/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Date;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.FilterService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.filter.Filter;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.json.JsonTaskQueryConverter;
import org.camunda.bpm.engine.impl.persistence.entity.FilterEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.impl.util.JsonUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers every way an expression can get into a task query that is executed:
 * with the default configuration, only allow-listed expressions are accepted
 * and nothing else is ever evaluated.
 */
public class TaskQueryExpressionAllowListTest {

  protected static final String EXPECTED_FAILURE_MESSAGE = "is not allowed";

  public static final String STATE_MANIPULATING_EXPRESSION =
      "${''.getClass().forName('" + TaskQueryExpressionAllowListTest.class.getName() + "').getField('MUTABLE_FIELD').setLong(null, 42)}";

  public static long MUTABLE_FIELD = 0;

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected ProcessEngineConfigurationImpl processEngineConfiguration;
  protected TaskService taskService;
  protected FilterService filterService;
  protected IdentityService identityService;

  @Before
  public void setUp() {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    taskService = engineRule.getTaskService();
    filterService = engineRule.getFilterService();
    identityService = engineRule.getIdentityService();

    processEngineConfiguration.setEnableExpressionsInAdhocQueries(true);

    ClockUtil.setCurrentTime(new Date(1427547759000L));

    Task assignedTask = taskService.newTask("assignedTask");
    assignedTask.setName("assignedTask");
    assignedTask.setAssignee("kermit");
    assignedTask.setDueDate(new DateTime(ClockUtil.getCurrentTime()).plusDays(2).toDate());
    taskService.saveTask(assignedTask);

    Task candidateTask = taskService.newTask("candidateTask");
    candidateTask.setName("candidateTask");
    taskService.saveTask(candidateTask);
    taskService.addCandidateGroup(candidateTask.getId(), "accounting");

    // the queries are created a moment later
    ClockUtil.setCurrentTime(new Date(1427547759000L + 60 * 1000L));

    identityService.setAuthentication("kermit", Arrays.asList("accounting", "management"));

    MUTABLE_FIELD = 0;
  }

  @After
  public void tearDown() {
    processEngineConfiguration.setEnableExpressionsInAdhocQueries(false);
    identityService.clearAuthentication();
    ClockUtil.reset();

    for (Filter filter : filterService.createFilterQuery().list()) {
      filterService.deleteFilter(filter.getId());
    }
    taskService.deleteTask("assignedTask", true);
    taskService.deleteTask("candidateTask", true);
  }

  // allow-listed expressions /////////////////////////////////////////////////

  @Test
  public void shouldExecuteAdhocQueryWithAllowListedExpression() {
    assertThat(query().taskAssigneeExpression("${currentUser()}").count()).isEqualTo(1);
    assertThat(query().taskAssigneeExpression("${ currentUser() }").count()).isEqualTo(1);
    assertThat(query().taskAssigneeExpression("${'kermit'}").count()).isEqualTo(1);
    assertThat(query().taskAssigneeExpression("kermit").count()).isEqualTo(1);
    assertThat(query().taskCandidateGroupInExpression("${currentUserGroups()}").count()).isEqualTo(1);
    assertThat(query().taskCandidateGroupExpression("${currentUserGroups()[0]}").count()).isEqualTo(1);
    assertThat(query().taskCreatedBeforeExpression("${ now() }").count()).isEqualTo(2);
    assertThat(query().dueBeforeExpression("${ dateTime() }").count()).isEqualTo(0);
    assertThat(query().dueBeforeExpression("${ dateTime().plusWeeks(2) }").count()).isEqualTo(1);
    assertThat(query().dueBeforeExpression("${dateTime().plusDays(4).withTimeAtStartOfDay()}").count()).isEqualTo(1);
    assertThat(query().dueAfterExpression("${dateTime().plusDays(4).withTimeAtStartOfDay().toDate()}").count()).isEqualTo(0);
    assertThat(query().or().taskAssigneeExpression("${currentUser()}").taskCandidateGroupInExpression("${currentUserGroups()}").endOr().count())
      .isEqualTo(2);
  }

  @Test
  public void shouldExecuteFiltersOfDemoData() {
    // the filters created by the demo data of the web applications
    String myTasks = saveFilter(query().taskAssigneeExpression("${currentUser()}"));
    String myGroupTasks = saveFilter(query().taskCandidateGroupInExpression("${currentUserGroups()}").taskUnassigned());
    String allGroupTasks = saveFilter(query().taskCandidateGroupInExpression("${currentUserGroups()}"));
    String dueSoon = saveFilter(query().dueBeforeExpression("${dateTime().plusDays(4).withTimeAtStartOfDay()}"));
    // the examples given in the filter dialog of Tasklist
    String createdBeforeNow = saveFilter(query().taskCreatedBeforeExpression("${ now() }"));
    String dueWithinTwoWeeks = saveFilter(query().dueBeforeExpression("${ dateTime().plusWeeks(2) }"));
    String ownTasks = saveFilter(query().taskAssigneeExpression("${ currentUser() }"));

    assertThat(filterService.count(myTasks)).isEqualTo(1);
    assertThat(filterService.count(myGroupTasks)).isEqualTo(1);
    assertThat(filterService.count(allGroupTasks)).isEqualTo(1);
    assertThat(filterService.count(dueSoon)).isEqualTo(1);
    assertThat(filterService.count(createdBeforeNow)).isEqualTo(2);
    assertThat(filterService.count(dueWithinTwoWeeks)).isEqualTo(1);
    assertThat(filterService.count(ownTasks)).isEqualTo(1);

    assertThat(filterService.<Task>list(myTasks)).extracting("id").containsExactly("assignedTask");
    assertThat(filterService.<Task>singleResult(myGroupTasks).getId()).isEqualTo("candidateTask");
    assertThat(filterService.<Task>listPage(dueSoon, 0, 10)).extracting("id").containsExactly("assignedTask");

    // and they can be extended
    assertThat(filterService.count(myTasks, query().dueBeforeExpression("${dateTime().plusDays(1)}"))).isEqualTo(0);
    assertThat(filterService.count(myTasks, query().dueBeforeExpression("${dateTime().plusDays(3)}"))).isEqualTo(1);
    assertThat(filterService.count(myTasks, query().or().taskNameLike("assigned%").taskCreatedAfterExpression("${now()}").endOr())).isEqualTo(1);
  }

  // adhoc queries ////////////////////////////////////////////////////////////

  @Test
  public void shouldRejectAdhocQuery() {
    assertQueryFails(query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
    assertQueryFails(query().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));
    assertQueryFails(query().taskCandidateGroupInExpression("${currentUserGroups().getClass()}"));
    assertQueryFails(query().taskAssigneeExpression("${currentUser()}").dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
  }

  @Test
  public void shouldRejectAdhocQueryWithOrQuery() {
    assertQueryFails(query().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).taskName("assignedTask").endOr());
    assertQueryFails(query().taskName("assignedTask")
        .or().taskAssignee("kermit").taskOwner("kermit").endOr()
        .or().taskName("assignedTask").taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION).endOr());
    assertQueryFails(queryWithAttachedOrQueryExpression());
  }

  // saving filters ///////////////////////////////////////////////////////////

  @Test
  public void shouldRejectSavingFilter() {
    assertSavingFilterFails(newFilter(query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION)));
    assertSavingFilterFails(newFilter(query().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).taskName("assignedTask").endOr()));
    assertSavingFilterFails(newFilter(queryWithAttachedOrQueryExpression()));
    // also if the query is not subject to the rules for adhoc queries
    assertSavingFilterFails(newFilter(new TaskQueryImpl().dueAfterExpression(STATE_MANIPULATING_EXPRESSION)));

    assertThat(filterService.createFilterQuery().count()).isEqualTo(0);
  }

  @Test
  public void shouldRejectUpdatingFilter() {
    Filter filter = filterService.getFilter(saveFilter(query().taskAssigneeExpression("${currentUser()}")));

    filter.setQuery(query().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));
    assertSavingFilterFails(filter);

    filter.setQuery(query().or().taskName("assignedTask").taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION).endOr());
    assertSavingFilterFails(filter);

    // the stored filter is unchanged
    assertThat(filterService.count(filter.getId())).isEqualTo(1);
  }

  @Test
  public void shouldRejectSavingExtendedFilter() {
    Filter filter = filterService.getFilter(saveFilter(query().taskAssigneeExpression("${currentUser()}")));

    assertSavingFilterFails(filter.extend(query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION)));
    assertSavingFilterFails(filter.extend(query().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).taskName("assignedTask").endOr()));
    assertSavingFilterFails(filter.extend(queryWithAttachedOrQueryExpression()));
    assertSavingFilterFails(filter.extend(new TaskQueryImpl().dueAfterExpression(STATE_MANIPULATING_EXPRESSION)));

    assertThat(filterService.createFilterQuery().count()).isEqualTo(1);
  }

  @Test
  public void shouldRejectSavingFilterWithDeserializedQuery() {
    TaskQuery query = new JsonTaskQueryConverter()
        .toObject(JsonUtil.asObject("{\"dueAfterExpression\":\"" + STATE_MANIPULATING_EXPRESSION + "\"}"));
    assertSavingFilterFails(newFilter(query));

    query = new JsonTaskQueryConverter()
        .toObject(JsonUtil.asObject("{\"orQueries\":[{\"dueAfterExpression\":\"" + STATE_MANIPULATING_EXPRESSION + "\"}]}"));
    assertSavingFilterFails(newFilter(query));

    assertThat(filterService.createFilterQuery().count()).isEqualTo(0);
  }

  // executing filters ////////////////////////////////////////////////////////

  @Test
  public void shouldRejectExecutingFilterStoredBefore() {
    // a filter that was stored when its expression was still accepted
    String filterId = insertFilter(query().taskAssigneeExpression("${currentUser()}").dueAfterExpression(STATE_MANIPULATING_EXPRESSION));

    assertExecutingFilterFails(filterId, null);
    assertExecutingFilterFails(filterId, query().taskName("assignedTask"));
    assertExecutingFilterFails(filterId, query().taskAssigneeExpression("${currentUser()}"));
  }

  @Test
  public void shouldRejectExecutingFilterWithOrQueryStoredBefore() {
    String filterId = insertFilter(query().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).taskName("assignedTask").endOr());
    assertExecutingFilterFails(filterId, null);
    assertExecutingFilterFails(filterId, query().or().taskName("assignedTask").taskAssignee("kermit").endOr());

    filterId = insertFilter(queryWithAttachedOrQueryExpression());
    assertExecutingFilterFails(filterId, null);
    assertExecutingFilterFails(filterId, query().taskName("assignedTask"));
  }

  @Test
  public void shouldRejectExecutingFilterStoredBeforeAsJson() {
    String filterId = insertFilter("{\"taskAssigneeExpression\":\"${currentUser()}\","
        + "\"orQueries\":[{\"name\":\"assignedTask\",\"dueAfterExpression\":\"" + STATE_MANIPULATING_EXPRESSION + "\"}]}");

    assertExecutingFilterFails(filterId, null);
  }

  @Test
  public void shouldRepairFilterStoredBefore() {
    String filterId = insertFilter(query().taskAssigneeExpression("${currentUser()}").dueAfterExpression(STATE_MANIPULATING_EXPRESSION));

    // the filter can be read
    Filter filter = filterService.getFilter(filterId);
    assertThat(filterService.createFilterQuery().filterId(filterId).count()).isEqualTo(1);
    TaskQueryImpl storedQuery = filter.getQuery();
    assertThat(storedQuery.getExpressions()).containsEntry("dueAfter", STATE_MANIPULATING_EXPRESSION);
    assertThat(MUTABLE_FIELD).isZero();

    // and it can be corrected
    filter.setQuery(query().taskAssigneeExpression("${currentUser()}").dueAfterExpression("${now()}"));
    filterService.saveFilter(filter);
    assertThat(filterService.count(filterId)).isEqualTo(1);

    // or deleted
    filterService.deleteFilter(filterId);
    assertThat(filterService.createFilterQuery().count()).isEqualTo(0);
  }

  @Test
  public void shouldRejectExtendingFilter() {
    String filterId = saveFilter(query().taskAssigneeExpression("${currentUser()}"));

    assertExecutingFilterFails(filterId, query().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));
    assertExecutingFilterFails(filterId, query().taskAssigneeExpression(STATE_MANIPULATING_EXPRESSION));
    assertExecutingFilterFails(filterId, query().or().dueAfterExpression(STATE_MANIPULATING_EXPRESSION).taskName("assignedTask").endOr());
    assertExecutingFilterFails(filterId, queryWithAttachedOrQueryExpression());
    // also if the extending query is not subject to the rules for adhoc queries
    assertExecutingFilterFails(filterId, new TaskQueryImpl().dueAfterExpression(STATE_MANIPULATING_EXPRESSION));

    // the filter itself is still fine
    assertThat(filterService.count(filterId)).isEqualTo(1);
  }

  // helper ///////////////////////////////////////////////////////////////////

  protected TaskQuery query() {
    return taskService.createTaskQuery();
  }

  protected TaskQuery queryWithAttachedOrQueryExpression() {
    TaskQueryImpl orQuery = new TaskQueryImpl();
    orQuery.setOrQueryActive();
    orQuery.dueAfterExpression(STATE_MANIPULATING_EXPRESSION);

    TaskQueryImpl query = (TaskQueryImpl) taskService.createTaskQuery();
    query.addOrQuery(orQuery);
    return query;
  }

  protected Filter newFilter(TaskQuery query) {
    Filter filter = filterService.newTaskFilter("filter");
    filter.setQuery(query);
    return filter;
  }

  protected String saveFilter(TaskQuery query) {
    return filterService.saveFilter(newFilter(query)).getId();
  }

  /**
   * Stores a filter bypassing validation, like a filter that was stored by
   * an earlier version or with another configuration.
   */
  protected String insertFilter(TaskQuery query) {
    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQuery(query);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });
  }

  protected String insertFilter(String queryJson) {
    return processEngineConfiguration.getCommandExecutorTxRequired().execute(commandContext -> {
      FilterEntity filter = new FilterEntity(EntityTypes.TASK);
      filter.setQueryInternal(queryJson);
      filter.setName("filter");
      commandContext.getDbEntityManager().insert(filter);
      return filter.getId();
    });
  }

  protected void assertQueryFails(TaskQuery query) {
    assertRejected(() -> query.list());
    assertRejected(() -> query.count());
    assertRejected(() -> query.singleResult());
    assertRejected(() -> query.listPage(0, 10));
    assertRejected(() -> query.unlimitedList());
  }

  protected void assertSavingFilterFails(Filter filter) {
    assertRejected(() -> filterService.saveFilter(filter));
  }

  protected void assertExecutingFilterFails(String filterId, TaskQuery extendingQuery) {
    assertRejected(() -> filterService.list(filterId, extendingQuery));
    assertRejected(() -> filterService.listPage(filterId, extendingQuery, 0, 10));
    assertRejected(() -> filterService.singleResult(filterId, extendingQuery));
    assertRejected(() -> filterService.count(filterId, extendingQuery));
  }

  protected void assertRejected(Runnable action) {
    assertThatThrownBy(() -> action.run())
      .isInstanceOf(BadUserRequestException.class)
      .hasMessageContaining(EXPECTED_FAILURE_MESSAGE);
    assertThat(MUTABLE_FIELD).isZero();
  }

}
