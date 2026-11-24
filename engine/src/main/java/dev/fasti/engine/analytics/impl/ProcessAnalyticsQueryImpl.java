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
package dev.fasti.engine.analytics.impl;

import static org.camunda.bpm.engine.authorization.Permissions.READ_HISTORY;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import dev.fasti.engine.analytics.ActivityAnalytics;
import dev.fasti.engine.analytics.AnalyticsNotAvailableException;
import dev.fasti.engine.analytics.DurationStatistics;
import dev.fasti.engine.analytics.InstanceAnalytics;
import dev.fasti.engine.analytics.PeriodAnalytics;
import dev.fasti.engine.analytics.ProcessAnalyticsQuery;
import dev.fasti.engine.analytics.SlowInstance;
import dev.fasti.engine.analytics.UserTaskAnalytics;
import dev.fasti.engine.analytics.UserTaskAnalytics.UserTaskTimes;
import dev.fasti.engine.analytics.impl.AnalyticsParameter.ActivitySince;
import dev.fasti.engine.analytics.impl.AnalyticsRows.ActivityCountRow;
import dev.fasti.engine.analytics.impl.AnalyticsRows.BucketRow;
import dev.fasti.engine.analytics.impl.AnalyticsRows.InstanceCountRow;
import dev.fasti.engine.analytics.impl.AnalyticsRows.KeyCountRow;
import dev.fasti.engine.analytics.impl.AnalyticsRows.UserTaskRow;
import dev.fasti.engine.analytics.impl.DurationBuckets.Histogram;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;

public class ProcessAnalyticsQueryImpl implements ProcessAnalyticsQuery {

  static final String ACTIVITY_BUCKETS = "selectFastiAnalyticsActivityBuckets";
  static final String ACTIVITY_COUNTS = "selectFastiAnalyticsActivityCounts";
  static final String RUNNING_SINCE = "selectFastiAnalyticsRunningSince";
  static final String REPEATS = "selectFastiAnalyticsRepeats";
  static final String INCIDENTS = "selectFastiAnalyticsIncidents";
  static final String INSTANCE_BUCKETS = "selectFastiAnalyticsInstanceBuckets";
  static final String INSTANCE_COUNTS = "selectFastiAnalyticsInstanceCounts";
  static final String TREND_BUCKETS = "selectFastiAnalyticsTrendBuckets";
  static final String TREND_STARTED = "selectFastiAnalyticsTrendStarted";
  static final String SLOWEST = "selectFastiAnalyticsSlowest";
  static final String OLDEST_RUNNING = "selectFastiAnalyticsOldestRunning";
  static final String USER_TASKS = "selectFastiAnalyticsUserTasks";

  static final String[] STATEMENTS = { ACTIVITY_BUCKETS, ACTIVITY_COUNTS, RUNNING_SINCE, REPEATS, INCIDENTS,
      INSTANCE_BUCKETS, INSTANCE_COUNTS, TREND_BUCKETS, TREND_STARTED, SLOWEST, OLDEST_RUNNING, USER_TASKS };

  static final int MAX_ID_LENGTH = 255;
  static final long DEFAULT_RANGE = 30L * 24 * 60 * 60 * 1000;
  static final String MULTI_INSTANCE_BODY = "multiInstanceBody";
  static final String MULTI_INSTANCE_SUFFIX = "#" + MULTI_INSTANCE_BODY;
  /** At most this many activities are checked for runs slower than their P90. */
  static final int MAX_RUNNING_CHECKS = 500;

  protected final ProcessEngineConfigurationImpl configuration;
  protected String processDefinitionKey;
  protected List<String> processDefinitionIds = new ArrayList<>();
  protected List<String> tenantIds = new ArrayList<>();
  protected Date finishedAfter;
  protected Date finishedBefore;
  protected Long targetDuration;

  public ProcessAnalyticsQueryImpl(ProcessEngineConfigurationImpl configuration) {
    this.configuration = configuration;
  }

  @Override
  public ProcessAnalyticsQuery processDefinitionKey(String processDefinitionKey) {
    if (processDefinitionKey == null || processDefinitionKey.isEmpty()) {
      throw new BadUserRequestException("processDefinitionKey is empty");
    }
    if (processDefinitionKey.length() > MAX_ID_LENGTH) {
      throw new BadUserRequestException("processDefinitionKey is longer than " + MAX_ID_LENGTH + " characters");
    }
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public ProcessAnalyticsQuery processDefinitionIdIn(Collection<String> processDefinitionIds) {
    this.processDefinitionIds = distinct("processDefinitionIdIn", processDefinitionIds);
    return this;
  }

  @Override
  public ProcessAnalyticsQuery tenantIdIn(Collection<String> tenantIds) {
    this.tenantIds = distinct("tenantIdIn", tenantIds);
    return this;
  }

  @Override
  public ProcessAnalyticsQuery finishedAfter(Date finishedAfter) {
    this.finishedAfter = finishedAfter;
    return this;
  }

  @Override
  public ProcessAnalyticsQuery finishedBefore(Date finishedBefore) {
    this.finishedBefore = finishedBefore;
    return this;
  }

  @Override
  public ProcessAnalyticsQuery targetDuration(Long targetMillis) {
    if (targetMillis != null && targetMillis < 0) {
      throw new BadUserRequestException("targetDuration is negative");
    }
    this.targetDuration = targetMillis;
    return this;
  }

  protected static List<String> distinct(String name, Collection<String> ids) {
    if (ids == null) {
      return new ArrayList<>();
    }
    LinkedHashSet<String> distinct = new LinkedHashSet<>();
    for (String id : ids) {
      if (id == null || id.isEmpty()) {
        throw new BadUserRequestException(name + " contains an empty id");
      }
      if (id.length() > MAX_ID_LENGTH) {
        throw new BadUserRequestException(name + " contains an id longer than " + MAX_ID_LENGTH + " characters");
      }
      distinct.add(id);
    }
    if (distinct.size() > MAX_IDS) {
      throw new BadUserRequestException(name + " has more than " + MAX_IDS + " entries");
    }
    return new ArrayList<>(distinct);
  }

  // --- commands -------------------------------------------------------------------------------

  protected <T> T execute(Function<CommandContext, T> work) {
    if (processDefinitionKey == null) {
      throw new BadUserRequestException("processDefinitionKey is required");
    }
    return configuration.getCommandExecutorTxRequired().execute(work::apply);
  }

  /** The checks every statement needs, and the parameter with the scope they share. */
  protected AnalyticsParameter prepare(CommandContext commandContext) {
    ensureStatementsMapped();
    commandContext.getAuthorizationManager().checkAuthorization(READ_HISTORY, PROCESS_DEFINITION, processDefinitionKey);

    AnalyticsParameter parameter = new AnalyticsParameter();
    parameter.processDefinitionKey = processDefinitionKey;
    for (String id : processDefinitionIds) {
      ProcessDefinitionEntity definition = commandContext.getProcessDefinitionManager().findLatestProcessDefinitionById(id);
      if (definition == null || !processDefinitionKey.equals(definition.getKey())) {
        throw new BadUserRequestException("processDefinitionIdIn contains " + id
            + ", which is not a version of process " + processDefinitionKey);
      }
      parameter.definitionIds.add(id);
    }
    parameter.tenantIds = new ArrayList<>(tenantIds);
    if (commandContext.getTenantManager().isTenantCheckEnabled()) {
      parameter.tenantCheck = true;
      Authentication authentication = commandContext.getAuthentication();
      if (authentication != null && authentication.getTenantIds() != null) {
        parameter.authenticatedTenantIds = new ArrayList<>(authentication.getTenantIds());
      }
    }

    Date now = ClockUtil.getCurrentTime();
    Date before = finishedBefore != null ? finishedBefore : new Date(now.getTime() + 1);
    Date after = finishedAfter != null ? finishedAfter : new Date(before.getTime() - DEFAULT_RANGE);
    if (!after.before(before)) {
      throw new BadUserRequestException("finishedAfter must be before finishedBefore");
    }
    parameter.after = after;
    parameter.before = before;
    parameter.target = targetDuration;
    return parameter;
  }

  /**
   * The statements live in a mapping file of their own; an application that replaced the default
   * {@code mappings.xml} with a copy that lacks it would otherwise get a MyBatis error that does
   * not say what to do.
   */
  protected void ensureStatementsMapped() {
    Configuration mybatis = configuration.getSqlSessionFactory().getConfiguration();
    for (String statement : STATEMENTS) {
      if (!mybatis.hasStatement(configuration.getDbSqlSessionFactory().mapStatement(statement))) {
        throw new AnalyticsNotAvailableException("The analytics statements are not mapped: the MyBatis mappings of "
            + "the engine lack dev/fasti/engine/analytics/ProcessAnalytics.xml. Add it to the custom mappings.xml.");
      }
    }
  }

  @SuppressWarnings("unchecked")
  protected <T> List<T> select(CommandContext commandContext, String statement, AnalyticsParameter parameter) {
    return (List<T>) commandContext.getDbEntityManager().selectListWithRawParameter(statement, parameter, 0,
        Integer.MAX_VALUE);
  }

  /** Reads at most {@code limit} rows; the database may still sort them all. */
  protected <T> List<T> select(CommandContext commandContext, String statement, AnalyticsParameter parameter,
      int limit) {
    String mapped = configuration.getDbSqlSessionFactory().mapStatement(statement);
    return commandContext.getDbSqlSession().getSqlSession().selectList(mapped, parameter, new RowBounds(0, limit));
  }

  protected static Map<String, Histogram> histograms(List<BucketRow> rows) {
    Map<String, Histogram> histograms = new HashMap<>();
    for (BucketRow row : rows) {
      String key = row.key == null ? "" : row.key;
      histograms.computeIfAbsent(key, ignored -> new Histogram())
          .add(row.bucket, row.count, row.total, row.minimum, row.maximum, row.withinTarget);
    }
    return histograms;
  }

  // --- activities -----------------------------------------------------------------------------

  @Override
  public List<ActivityAnalytics> activities() {
    return execute(this::activities);
  }

  protected List<ActivityAnalytics> activities(CommandContext commandContext) {
    AnalyticsParameter parameter = prepare(commandContext);
    Date now = ClockUtil.getCurrentTime();

    Map<String, ActivityAnalytics> activities = new TreeMap<>();
    Set<String> multiInstanceInner = new HashSet<>();
    for (ActivityCountRow row : this.<ActivityCountRow>select(commandContext, ACTIVITY_COUNTS, parameter)) {
      if (row.activityId == null) {
        continue;
      }
      ActivityAnalytics activity = activities.computeIfAbsent(row.activityId, id -> newActivity(id));
      if (activity.getActivityType() == null) {
        activity.setActivityType(row.activityType);
      }
      if (row.activityName != null) {
        activity.setActivityName(row.activityName);
      }
      activity.setFinished(activity.getFinished() + row.finished);
      activity.setCanceled(activity.getCanceled() + row.canceled);
      activity.setRunning(activity.getRunning() + row.running);
      if (row.oldestRunning != null && (activity.getOldestRunningStart() == null
          || row.oldestRunning.before(activity.getOldestRunningStart()))) {
        activity.setOldestRunningStart(row.oldestRunning);
      }
      if (MULTI_INSTANCE_BODY.equals(row.activityType) && row.activityId.endsWith(MULTI_INSTANCE_SUFFIX)) {
        multiInstanceInner.add(row.activityId.substring(0, row.activityId.length() - MULTI_INSTANCE_SUFFIX.length()));
      }
    }

    Map<String, Histogram> histograms = histograms(select(commandContext, ACTIVITY_BUCKETS, parameter));
    for (ActivityAnalytics activity : activities.values()) {
      Histogram histogram = histograms.get(activity.getActivityId());
      if (histogram == null) {
        activity.setDurations(DurationStatistics.empty(parameter.target));
      } else {
        DurationStatistics durations = histogram.toStatistics(parameter.target, true);
        activity.setDurations(durations);
        activity.setTimeAboveMedian(histogram.timeAbove(durations.getP50()));
      }
    }

    // runs slower than the P90 of the range, among those running now
    for (ActivityAnalytics activity : activities.values()) {
      Long p90 = activity.getDurations().getP90();
      if (activity.getRunning() > 0 && p90 != null && parameter.activitySince.size() < MAX_RUNNING_CHECKS) {
        parameter.activitySince.add(new ActivitySince(activity.getActivityId(), new Date(now.getTime() - p90)));
      }
    }
    if (!parameter.activitySince.isEmpty()) {
      for (KeyCountRow row : this.<KeyCountRow>select(commandContext, RUNNING_SINCE, parameter)) {
        ActivityAnalytics activity = activities.get(row.key);
        if (activity != null) {
          activity.setRunningOverP90(row.count);
        }
      }
      parameter.activitySince.clear();
    }

    // the iterations of a multi-instance activity share their parent, the body: no rework
    for (KeyCountRow row : this.<KeyCountRow>select(commandContext, REPEATS, parameter)) {
      ActivityAnalytics activity = activities.get(row.key);
      if (activity != null && !multiInstanceInner.contains(row.key)) {
        activity.setRepeatedInstances(row.count);
        activity.setRepeatedExecutions(row.extra);
      }
    }

    for (KeyCountRow row : this.<KeyCountRow>select(commandContext, INCIDENTS, parameter)) {
      if (row.key == null) {
        continue;
      }
      ActivityAnalytics activity = activities.computeIfAbsent(row.key, id -> {
        ActivityAnalytics created = newActivity(id);
        created.setDurations(DurationStatistics.empty(parameter.target));
        return created;
      });
      activity.setOpenIncidents(row.count);
      activity.setIncidents(row.extra);
    }
    return new ArrayList<>(activities.values());
  }

  protected static ActivityAnalytics newActivity(String activityId) {
    ActivityAnalytics activity = new ActivityAnalytics();
    activity.setActivityId(activityId);
    return activity;
  }

  // --- instances and trend --------------------------------------------------------------------

  @Override
  public InstanceAnalytics instances() {
    return execute(commandContext -> {
      AnalyticsParameter parameter = prepare(commandContext);
      List<InstanceCountRow> counts = select(commandContext, INSTANCE_COUNTS, parameter);
      InstanceCountRow count = counts.isEmpty() || counts.get(0) == null ? new InstanceCountRow() : counts.get(0);
      Histogram histogram = histograms(select(commandContext, INSTANCE_BUCKETS, parameter)).get("");
      DurationStatistics durations = histogram == null ? DurationStatistics.empty(parameter.target)
          : histogram.toStatistics(parameter.target, true);
      return new InstanceAnalytics(count.completed, count.terminated, count.running, durations);
    });
  }

  @Override
  public List<PeriodAnalytics> trend(List<Date> boundaries) {
    if (boundaries == null || boundaries.size() < 2) {
      throw new BadUserRequestException("a trend needs at least two period boundaries");
    }
    if (boundaries.size() > MAX_PERIODS + 1) {
      throw new BadUserRequestException("a trend has at most " + MAX_PERIODS + " periods");
    }
    for (int index = 1; index < boundaries.size(); index++) {
      if (boundaries.get(index - 1) == null || boundaries.get(index) == null
          || !boundaries.get(index - 1).before(boundaries.get(index))) {
        throw new BadUserRequestException("period boundaries must be ascending");
      }
    }
    List<Date> bounds = new ArrayList<>(boundaries);
    return execute(commandContext -> {
      AnalyticsParameter parameter = prepare(commandContext);
      int periods = bounds.size() - 1;
      parameter.periodStart = bounds.get(0);
      parameter.periodEnd = bounds.get(periods);
      parameter.periodEnds = new ArrayList<>(bounds.subList(1, bounds.size()));
      parameter.periodOfEndSql = AnalyticsParameter.periodSql("P.END_TIME_", periods);
      parameter.periodOfStartSql = AnalyticsParameter.periodSql("P.START_TIME_", periods);

      Map<String, Histogram> histograms = histograms(select(commandContext, TREND_BUCKETS, parameter));
      Map<Integer, Histogram> byPeriod = new HashMap<>();
      histograms.forEach((key, histogram) -> byPeriod.put(period(key), histogram));
      Map<Integer, Long> started = new HashMap<>();
      for (KeyCountRow row : this.<KeyCountRow>select(commandContext, TREND_STARTED, parameter)) {
        started.put(period(row.key), row.count);
      }
      List<PeriodAnalytics> trend = new ArrayList<>();
      for (int period = 0; period < periods; period++) {
        Histogram histogram = byPeriod.get(period);
        DurationStatistics durations = histogram == null ? DurationStatistics.empty(parameter.target)
            : histogram.toStatistics(parameter.target, true);
        trend.add(new PeriodAnalytics(bounds.get(period), bounds.get(period + 1), started.getOrDefault(period, 0L),
            durations));
      }
      return trend;
    });
  }

  protected static int period(String key) {
    if (key == null || key.isEmpty()) {
      return -1;
    }
    return (int) Double.parseDouble(key.trim());
  }

  // --- drill-down -----------------------------------------------------------------------------

  @Override
  public List<SlowInstance> slowest(String activityId, int maxResults) {
    return runs(activityId, maxResults, SLOWEST);
  }

  @Override
  public List<SlowInstance> oldestRunning(String activityId, int maxResults) {
    return runs(activityId, maxResults, OLDEST_RUNNING);
  }

  protected List<SlowInstance> runs(String activityId, int maxResults, String statement) {
    if (activityId == null || activityId.isEmpty() || activityId.length() > MAX_ID_LENGTH) {
      throw new BadUserRequestException("activityId is empty or longer than " + MAX_ID_LENGTH + " characters");
    }
    if (maxResults < 1 || maxResults > MAX_RESULTS) {
      throw new BadUserRequestException("maxResults must be between 1 and " + MAX_RESULTS);
    }
    return execute(commandContext -> {
      AnalyticsParameter parameter = prepare(commandContext);
      parameter.activityId = activityId;
      List<SlowInstance> runs = select(commandContext, statement, parameter, maxResults);
      long now = ClockUtil.getCurrentTime().getTime();
      for (SlowInstance run : runs) {
        if (run.getEndTime() == null && run.getStartTime() != null) {
          run.setDuration(Math.max(0, now - run.getStartTime().getTime()));
        }
      }
      return new ArrayList<>(runs);
    });
  }

  // --- user tasks -----------------------------------------------------------------------------

  @Override
  public UserTaskAnalytics userTasks(int maxTasks) {
    if (maxTasks < 1 || maxTasks > MAX_USER_TASKS) {
      throw new BadUserRequestException("maxTasks must be between 1 and " + MAX_USER_TASKS);
    }
    return execute(commandContext -> {
      AnalyticsParameter parameter = prepare(commandContext);
      List<UserTaskRow> rows = select(commandContext, USER_TASKS, parameter, maxTasks + 1);
      boolean sampled = rows.size() > maxTasks;
      if (sampled) {
        rows = rows.subList(0, maxTasks);
      }
      Map<String, TaskTimes> byTask = new LinkedHashMap<>();
      Map<String, TaskTimes> byAssignee = new LinkedHashMap<>();
      for (UserTaskRow row : rows) {
        TaskTimes task = byTask.computeIfAbsent(String.valueOf(row.taskDefinitionKey), ignored -> new TaskTimes(row.taskDefinitionKey));
        if (task.name == null) {
          task.name = row.name;
        }
        TaskTimes assignee = byAssignee.computeIfAbsent(String.valueOf(row.assignee), ignored -> new TaskTimes(row.assignee));
        task.add(row);
        assignee.add(row);
      }
      Long target = parameter.target;
      return new UserTaskAnalytics(rows.size(), sampled, times(byTask.values(), target, true),
          times(byAssignee.values(), target, false));
    });
  }

  protected static List<UserTaskTimes> times(Collection<TaskTimes> groups, Long target, boolean named) {
    List<TaskTimes> sorted = new ArrayList<>(groups);
    sorted.sort(Comparator.comparingLong((TaskTimes times) -> times.count).reversed());
    List<UserTaskTimes> result = new ArrayList<>();
    for (TaskTimes times : sorted.subList(0, Math.min(sorted.size(), MAX_RESULTS))) {
      result.add(new UserTaskTimes(times.key, named ? times.name : null, times.count, times.unknownWaiting,
          DurationBuckets.exact(times.waiting.toArray(), target), DurationBuckets.exact(times.working.toArray(), target)));
    }
    return result;
  }

  /** The waiting and working times of one group of tasks, collected row by row. */
  protected static class TaskTimes {
    final String key;
    String name;
    long count;
    long unknownWaiting;
    final Longs waiting = new Longs();
    final Longs working = new Longs();

    TaskTimes(String key) {
      this.key = key;
    }

    void add(UserTaskRow row) {
      count++;
      if (row.startTime == null || row.endTime == null) {
        return;
      }
      long start = row.startTime.getTime();
      long end = row.endTime.getTime();
      if (row.firstAssignment == null) {
        unknownWaiting++;
        return;
      }
      long assigned = Math.min(Math.max(row.firstAssignment.getTime(), start), end);
      waiting.add(assigned - start);
      working.add(end - assigned);
    }
  }

  /** A growing array of longs. */
  protected static class Longs {
    long[] values = new long[16];
    int size;

    void add(long value) {
      if (size == values.length) {
        values = java.util.Arrays.copyOf(values, size * 2);
      }
      values[size++] = value;
    }

    long[] toArray() {
      return java.util.Arrays.copyOf(values, size);
    }
  }
}
