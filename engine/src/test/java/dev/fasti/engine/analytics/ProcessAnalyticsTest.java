package dev.fasti.engine.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Process analytics against an engine on H2 with history level full: a review that may be sent
 * back (rework), four parallel approvals (multi-instance, no rework), times set by the clock.
 */
public class ProcessAnalyticsTest {

  static final long MINUTE = 60_000;
  static final long HOUR = 60 * MINUTE;
  static final Date T0 = new Date(1_790_000_000_000L);

  static ProcessEngine engine;

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-analytics;DB_CLOSE_DELAY=10");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("analytics");
    configuration.setHistory("full");
    configuration.setJobExecutorActivate(false);
    configuration.setAuthorizationEnabled(true);
    engine = configuration.buildProcessEngine();

    BpmnModelInstance model = Bpmn.createExecutableProcess("order")
        .startEvent("start")
        .userTask("review").name("Review order")
        .exclusiveGateway("reviewed")
          .condition("again", "${again}").connectTo("review")
        .moveToNode("reviewed")
          .condition("done", "${!again}")
        .userTask("approve").camundaCandidateGroups("approvers")
          .multiInstance().parallel().cardinality("4").multiInstanceDone()
        .endEvent("end")
        .done();
    engine.getRepositoryService().createDeployment().addModelInstance("order.bpmn", model).deploy();

    // ten orders: order i is reviewed after (i + 1) hours, then approved four times in one hour;
    // orders 0 and 1 are sent back once; order 9 stays in review
    for (int order = 0; order < 10; order++) {
      ClockUtil.setCurrentTime(new Date(T0.getTime() + order * MINUTE));
      Map<String, Object> variables = new HashMap<>();
      variables.put("again", order < 2);
      ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("order", "order-" + order, variables);
      if (order == 9) {
        continue;
      }
      long reviewAt = T0.getTime() + order * MINUTE + (order + 1) * HOUR;
      Task review = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
      ClockUtil.setCurrentTime(new Date(reviewAt - 30 * MINUTE));
      engine.getTaskService().claim(review.getId(), "ann");
      ClockUtil.setCurrentTime(new Date(reviewAt));
      engine.getTaskService().complete(review.getId());
      if (order < 2) {
        engine.getRuntimeService().setVariable(instance.getId(), "again", false);
        Task again = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        engine.getTaskService().setAssignee(again.getId(), "bob");
        ClockUtil.setCurrentTime(new Date(reviewAt + HOUR));
        engine.getTaskService().complete(again.getId());
        reviewAt += HOUR;
      }
      ClockUtil.setCurrentTime(new Date(reviewAt + HOUR));
      for (Task approval : engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).list()) {
        engine.getTaskService().complete(approval.getId());
      }
    }
    ClockUtil.setCurrentTime(new Date(T0.getTime() + 20 * HOUR));
  }

  @AfterClass
  public static void closeEngine() {
    ClockUtil.reset();
    engine.close();
  }

  @After
  public void clearAuthentication() {
    engine.getIdentityService().clearAuthentication();
  }

  ProcessAnalyticsQuery query() {
    return ProcessAnalytics.query(engine).processDefinitionKey("order")
        .finishedAfter(new Date(T0.getTime() - HOUR)).finishedBefore(new Date(T0.getTime() + 20 * HOUR));
  }

  static <T> Map<String, T> byId(List<T> entries, Function<T, String> id) {
    return entries.stream().collect(Collectors.toMap(id, entry -> entry));
  }

  @Test
  public void shouldCountAndTimeEveryActivity() {
    Map<String, ActivityAnalytics> activities = byId(query().activities(), ActivityAnalytics::getActivityId);

    ActivityAnalytics review = activities.get("review");
    assertThat(review.getActivityType()).isEqualTo("userTask");
    assertThat(review.getActivityName()).isEqualTo("Review order");
    assertThat(review.getFinished()).isEqualTo(11);
    assertThat(review.getRunning()).isEqualTo(1);
    assertThat(review.getOldestRunningStart()).isEqualTo(new Date(T0.getTime() + 9 * MINUTE));
    DurationStatistics durations = review.getDurations();
    assertThat(durations.getCount()).isEqualTo(11);
    assertThat(durations.isApproximate()).isTrue();
    assertThat(durations.getMinimum()).isEqualTo(HOUR);
    assertThat(durations.getMaximum()).isEqualTo(9 * HOUR);
    // 1, 1, 2, 2, 3 ... 9 hours (two reworks of one hour): the median is 4 hours
    assertThat((double) durations.getP50()).isCloseTo(4 * HOUR, within(0.13 * 4 * HOUR));
    assertThat(durations.getTotal()).isEqualTo((1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 1 + 1) * HOUR);

    assertThat(activities.get("approve").getFinished()).isEqualTo(36);
    assertThat(activities.get("approve#multiInstanceBody").getFinished()).isEqualTo(9);
    assertThat(activities.get("end").getFinished()).isEqualTo(9);
  }

  @Test
  public void shouldFindReworkButNotMultiInstanceIterations() {
    Map<String, ActivityAnalytics> activities = byId(query().activities(), ActivityAnalytics::getActivityId);

    assertThat(activities.get("review").getRepeatedInstances()).isEqualTo(2);
    assertThat(activities.get("review").getRepeatedExecutions()).isEqualTo(2);
    assertThat(activities.get("approve").getRepeatedInstances()).isZero();
    assertThat(activities.get("reviewed").getRepeatedInstances()).isZero();
  }

  @Test
  public void shouldCountRunningOnesSlowerThanTheP90() {
    // the open review started 20 hours minus 9 minutes ago; nine in ten reviews took at most ~8 hours
    ActivityAnalytics review = byId(query().activities(), ActivityAnalytics::getActivityId).get("review");
    assertThat(review.getRunningOverP90()).isEqualTo(1);
  }

  @Test
  public void shouldTimeInstancesAgainstATarget() {
    InstanceAnalytics instances = query().targetDuration(5 * HOUR).instances();

    assertThat(instances.getCompleted()).isEqualTo(9);
    assertThat(instances.getRunning()).isEqualTo(1);
    // order i takes i + 2 hours, orders 0 and 1 one more
    assertThat(instances.getDurations().getMinimum()).isEqualTo(3 * HOUR);
    assertThat(instances.getDurations().getMaximum()).isEqualTo(10 * HOUR);
    assertThat(instances.getDurations().getWithinTarget()).isEqualTo(4);
  }

  @Test
  public void shouldSplitATrendIntoPeriods() {
    List<Date> bounds = Arrays.asList(T0, new Date(T0.getTime() + 6 * HOUR), new Date(T0.getTime() + 12 * HOUR));
    List<PeriodAnalytics> trend = query().trend(bounds);

    assertThat(trend).hasSize(2);
    assertThat(trend.get(0).getStarted()).isEqualTo(10);
    assertThat(trend.get(1).getStarted()).isZero();
    // completed before hour 6: orders 0 (3 h), 1 (4 h), 2 (4 h), 3 (5 h)
    assertThat(trend.get(0).getDurations().getCount()).isEqualTo(4);
    assertThat(trend.get(1).getDurations().getCount()).isEqualTo(5);
  }

  @Test
  public void shouldListTheSlowestAndTheOldestRunning() {
    List<SlowInstance> slowest = query().slowest("review", 3);
    assertThat(slowest).extracting(SlowInstance::getBusinessKey).containsExactly("order-8", "order-7", "order-6");
    assertThat(slowest.get(0).getDuration()).isEqualTo(9 * HOUR);

    List<SlowInstance> running = query().oldestRunning("review", 5);
    assertThat(running).extracting(SlowInstance::getBusinessKey).containsExactly("order-9");
    assertThat(running.get(0).getEndTime()).isNull();
    assertThat(running.get(0).getDuration()).isEqualTo(20 * HOUR - 9 * MINUTE);
  }

  @Test
  public void shouldSplitUserTaskTimeIntoWaitingAndWorking() {
    UserTaskAnalytics tasks = query().userTasks(1000);

    assertThat(tasks.isSampled()).isFalse();
    UserTaskAnalytics.UserTaskTimes review = tasks.getByTask().stream()
        .filter(entry -> "review".equals(entry.getKey())).findFirst().get();
    assertThat(review.getName()).isEqualTo("Review order");
    assertThat(review.getCount()).isEqualTo(11);
    // first reviews: claimed 30 minutes before completion; reworks: assigned at once, one hour
    assertThat(review.getWaiting().isApproximate()).isFalse();
    assertThat(review.getWorking().getP50()).isEqualTo(30 * MINUTE);
    assertThat(review.getWorking().getMaximum()).isEqualTo(HOUR);
    assertThat(review.getWaiting().getMaximum()).isEqualTo(9 * HOUR - 30 * MINUTE);
    assertThat(review.getWaiting().getMinimum()).isZero();

    UserTaskAnalytics.UserTaskTimes approvals = tasks.getByTask().stream()
        .filter(entry -> "approve".equals(entry.getKey())).findFirst().get();
    assertThat(approvals.getCount()).isEqualTo(36);
    assertThat(approvals.getUnknownWaiting()).isEqualTo(36);

    assertThat(tasks.getByAssignee()).extracting(UserTaskAnalytics.UserTaskTimes::getKey).contains("ann", "bob");
  }

  @Test
  public void shouldReadTheNewestTasksOnlyWhenCapped() {
    UserTaskAnalytics tasks = query().userTasks(5);
    assertThat(tasks.isSampled()).isTrue();
    assertThat(tasks.getTasks()).isEqualTo(5);
  }

  @Test
  public void shouldRequireReadHistory() {
    engine.getIdentityService().setAuthentication("eve", Collections.emptyList());
    assertThatThrownBy(() -> query().activities()).isInstanceOf(AuthorizationException.class);
    engine.getIdentityService().clearAuthentication();

    Authorization grant = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setUserId("eve");
    grant.setResource(Resources.PROCESS_DEFINITION);
    grant.setResourceId("order");
    grant.addPermission(Permissions.READ_HISTORY);
    engine.getAuthorizationService().saveAuthorization(grant);
    try {
      engine.getIdentityService().setAuthentication("eve", Collections.emptyList());
      assertThat(query().activities()).isNotEmpty();
    } finally {
      engine.getIdentityService().clearAuthentication();
      engine.getAuthorizationService().deleteAuthorization(grant.getId());
    }
  }

  @Test
  public void shouldNotFindAnUnknownProcess() {
    assertThatThrownBy(() -> ProcessAnalytics.query(engine).processDefinitionKey("nothing").activities())
        .isInstanceOf(org.camunda.bpm.engine.exception.NotFoundException.class);
  }

  @Test
  public void shouldGiveTheSameAnswerForTheVersionAndForAllVersions() {
    String id = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("order")
        .singleResult().getId();
    Map<String, ActivityAnalytics> all = byId(query().activities(), ActivityAnalytics::getActivityId);
    Map<String, ActivityAnalytics> one = byId(query().processDefinitionIdIn(Collections.singletonList(id)).activities(),
        ActivityAnalytics::getActivityId);
    assertThat(one.keySet()).isEqualTo(all.keySet());
    assertThat(one.get("review").getFinished()).isEqualTo(all.get("review").getFinished());
    assertThat(one.get("review").getRunning()).isEqualTo(1);
  }

  @Test
  public void shouldRefuseVersionsOfAnotherProcess() {
    assertThatThrownBy(() -> query().processDefinitionIdIn(Collections.singletonList("other:1:1")).activities())
        .isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldRefuseBadTrendBounds() {
    assertThatThrownBy(() -> query().trend(Collections.singletonList(T0))).isInstanceOf(BadUserRequestException.class);
    assertThatThrownBy(() -> query().trend(Arrays.asList(new Date(T0.getTime() + 1), T0)))
        .isInstanceOf(BadUserRequestException.class);
  }
}
