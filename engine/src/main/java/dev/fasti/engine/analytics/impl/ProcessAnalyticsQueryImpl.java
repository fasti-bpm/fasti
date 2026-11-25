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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.fasti.engine.analytics.ActivityAnalytics;
import dev.fasti.engine.analytics.ActivityRework;
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
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.ProcessDefinition;

public class ProcessAnalyticsQueryImpl implements ProcessAnalyticsQuery {

  static final String ACTIVITY_BUCKETS = "selectFastiAnalyticsActivityBuckets";
  static final String ACTIVITY_RUNNING = "selectFastiAnalyticsActivityRunning";
  static final String RUNNING_SINCE = "selectFastiAnalyticsRunningSince";
  static final String REPEATS = "selectFastiAnalyticsRepeats";
  static final String INCIDENTS = "selectFastiAnalyticsIncidents";
  static final String INSTANCE_BUCKETS = "selectFastiAnalyticsInstanceBuckets";
  static final String INSTANCE_COUNTS = "selectFastiAnalyticsInstanceCounts";
  static final String INSTANCE_RUNNING = "selectFastiAnalyticsInstanceRunning";
  static final String KEY_HISTORY = "selectFastiAnalyticsKeyHistory";
  static final String TREND_BUCKETS = "selectFastiAnalyticsTrendBuckets";
  static final String TREND_STARTED = "selectFastiAnalyticsTrendStarted";
  static final String SLOWEST = "selectFastiAnalyticsSlowest";
  static final String OLDEST_RUNNING = "selectFastiAnalyticsOldestRunning";
  static final String USER_TASKS = "selectFastiAnalyticsUserTasks";

  static final String[] STATEMENTS = { ACTIVITY_BUCKETS, ACTIVITY_RUNNING, RUNNING_SINCE, REPEATS,
      INCIDENTS, INSTANCE_BUCKETS, INSTANCE_COUNTS, INSTANCE_RUNNING, KEY_HISTORY, TREND_BUCKETS, TREND_STARTED,
      SLOWEST, OLDEST_RUNNING, USER_TASKS };

  static final int MAX_ID_LENGTH = 255;
  static final long DEFAULT_RANGE = 30L * 24 * 60 * 60 * 1000;
  /** Activities checked for runs slower than their P90 in one statement, and in total. */
  static final int RUNNING_CHECKS_PER_STATEMENT = 200;
  static final int MAX_RUNNING_CHECKS = 2000;
  /**
   * The versions of a process are resolved to their ids, so that the statements can seek the
   * indexes on {@code (PROC_DEF_ID_, END_TIME_)}; with more versions than this the key is used.
   */
  static final int MAX_RESOLVED_DEFINITIONS = 500;
  static final String MULTI_INSTANCE_SUFFIX = "#multiInstanceBody";
  /** The bucket of canceled runs in the activity scan. */
  static final int CANCELED_BUCKET = -1;

  protected final ProcessEngineConfigurationImpl configuration;
  protected String processDefinitionKey;
  protected List<String> processDefinitionIds = new ArrayList<>();
  protected List<String> tenantIds = new ArrayList<>();
  protected Date finishedAfter;
  protected Date finishedBefore;
  protected Long targetDuration;
  protected boolean withRework = true;

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
  public ProcessAnalyticsQuery withoutRework() {
    this.withRework = false;
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

  /**
   * Runs a read with the checks of {@link #prepare(CommandContext)} on every call, then answers from
   * the {@link AnalyticsCache} when the same was read shortly before. The checks run in a short
   * transaction of their own; the read waits for its turn ({@link AnalyticsLimiter}) without
   * holding a database connection, and then reads in a transaction of its own.
   */
  protected <T> T executeCached(String kind, Object extra, BiFunction<CommandContext, AnalyticsParameter, T> work) {
    if (org.camunda.bpm.engine.impl.context.Context.getCommandContext() != null) {
      // called inside a transaction of the engine (a delegate, a listener): it holds a connection
      // already, so it reads in that transaction without waiting for a turn or for other callers
      return execute(commandContext -> {
        AnalyticsParameter parameter = prepare(commandContext);
        return AnalyticsCache.getWithoutWaiting(cacheKey(kind, parameter, extra),
            () -> work.apply(commandContext, parameter));
      }, true);
    }
    AnalyticsParameter parameter = execute(this::prepare, false);
    boolean scan = "activities".equals(kind) || "rework".equals(kind) || "userTasks".equals(kind);
    return AnalyticsCache.get(cacheKey(kind, parameter, extra), () -> AnalyticsLimiter
        .run(scan, () -> execute(commandContext -> work.apply(commandContext, parameter), true)));
  }

  /**
   * The key of an answer in the cache. A list of its parts rather than a joined string, so that
   * values containing the separator (tenant ids from a token, for example) cannot make two
   * different scopes share an entry.
   */
  protected Object cacheKey(String kind, AnalyticsParameter parameter, Object extra) {
    return Arrays.asList(kind, configuration.getProcessEngineName(), parameter.processDefinitionKey,
        new ArrayList<>(parameter.definitionIds), new ArrayList<>(parameter.tenantIds), parameter.tenantCheck,
        parameter.authenticatedTenantIds == null ? null : new ArrayList<>(parameter.authenticatedTenantIds),
        parameter.after.getTime(), parameter.before.getTime(), parameter.target, extra);
  }

  protected <T> T execute(Function<CommandContext, T> work, boolean reads) {
    if (processDefinitionKey == null) {
      throw new BadUserRequestException("processDefinitionKey is required");
    }
    return configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      if (reads) {
        customPlans(commandContext);
      }
      return work.apply(commandContext);
    });
  }

  /**
   * On PostgreSQL, plans for the values of this call: after five executions of a prepared
   * statement PostgreSQL may switch to a generic plan whose row estimate for the date range is a
   * constant, which made the aggregates of a long range twice as slow. Only for this transaction;
   * the planning time is small next to reading the history.
   */
  protected void customPlans(CommandContext commandContext) {
    if (!"postgres".equals(configuration.getDatabaseType())) {
      return;
    }
    setLocal(commandContext.getDbSqlSession().getSqlSession().getConnection(),
        "set local plan_cache_mode = force_custom_plan");
  }

  /**
   * Runs a {@code set local} behind a savepoint: a server that refuses it keeps its default and
   * the transaction stays usable. False when refused.
   */
  protected static boolean setLocal(java.sql.Connection connection, String sql) {
    java.sql.Savepoint savepoint = null;
    try {
      savepoint = connection.setSavepoint();
      try (java.sql.Statement statement = connection.createStatement()) {
        statement.execute(sql);
      }
      connection.releaseSavepoint(savepoint);
      return true;
    } catch (java.sql.SQLException e) {
      if (savepoint != null) {
        try {
          connection.rollback(savepoint);
        } catch (java.sql.SQLException ignored) {
          // nothing more to undo
        }
      }
      return false;
    }
  }

  /**
   * On PostgreSQL, groups with a hash table: the planner estimates the groups of an activity and
   * a duration bucket hundreds of times too many, and sorts the whole range on disk instead of
   * hashing the few hundred groups there are, which takes twice as long. Only for this read.
   */
  protected <T> T hashAggregate(CommandContext commandContext, Supplier<T> read) {
    if (!"postgres".equals(configuration.getDatabaseType())) {
      return read.get();
    }
    java.sql.Connection connection = commandContext.getDbSqlSession().getSqlSession().getConnection();
    // the setting is on unless the server or the user turned it off: then it stays as it is
    boolean changed = sortEnabled(connection) && setLocal(connection, "set local enable_sort = off");
    try {
      return read.get();
    } finally {
      if (changed) {
        setLocal(connection, "set local enable_sort = on");
      }
    }
  }

  protected static boolean sortEnabled(java.sql.Connection connection) {
    try (java.sql.Statement statement = connection.createStatement();
        java.sql.ResultSet setting = statement.executeQuery("show enable_sort")) {
      return setting.next() && "on".equals(setting.getString(1));
    } catch (java.sql.SQLException e) {
      return false;
    }
  }

  /** The checks every statement needs, and the parameter with the scope they share. */
  protected AnalyticsParameter prepare(CommandContext commandContext) {
    ensureStatementsMapped();
    commandContext.getAuthorizationManager().checkAuthorization(READ_HISTORY, PROCESS_DEFINITION, processDefinitionKey);

    AnalyticsParameter parameter = new AnalyticsParameter();
    parameter.processDefinitionKey = processDefinitionKey;
    parameter.databaseType = configuration.getDatabaseType();
    // the tenant scope first: the probe for history of a key without versions must not reveal
    // whether another tenant has such history
    parameter.tenantIds = new ArrayList<>(tenantIds);
    if (commandContext.getTenantManager().isTenantCheckEnabled()) {
      parameter.tenantCheck = true;
      Authentication authentication = commandContext.getAuthentication();
      if (authentication != null && authentication.getTenantIds() != null) {
        parameter.authenticatedTenantIds = new ArrayList<>(authentication.getTenantIds());
      }
    }
    for (String id : processDefinitionIds) {
      ProcessDefinitionEntity definition = commandContext.getProcessDefinitionManager().findLatestProcessDefinitionById(id);
      if (definition == null || !processDefinitionKey.equals(definition.getKey())) {
        throw new BadUserRequestException("processDefinitionIdIn contains " + id
            + ", which is not a version of process " + processDefinitionKey);
      }
      parameter.definitionIds.add(id);
    }
    if (processDefinitionIds.isEmpty()) {
      List<String> versions = definitionIdsOfKey(commandContext);
      if (versions.isEmpty()) {
        parameter.maxResults = 1;
        if (select(commandContext, KEY_HISTORY, parameter).isEmpty()) {
          throw new NotFoundException("No process with key " + processDefinitionKey);
        }
      } else if (versions.size() <= MAX_RESOLVED_DEFINITIONS) {
        parameter.definitionIds.addAll(versions);
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
   * The ids of the versions of the process that the tenant check lets the user see. The user's
   * permission is {@code READ_HISTORY} on the key, checked before; reading the definitions needs
   * no {@code READ} of its own.
   */
  protected List<String> definitionIdsOfKey(CommandContext commandContext) {
    commandContext.disableAuthorizationCheck();
    try {
      List<String> ids = new ArrayList<>();
      for (ProcessDefinition definition : commandContext.getProcessDefinitionManager()
          .findProcessDefinitionsByKey(processDefinitionKey)) {
        ids.add(definition.getId());
      }
      return ids;
    } finally {
      commandContext.enableAuthorizationCheck();
    }
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

  /** Reads at most {@code limit} rows: the statement limits its result in SQL. */
  protected <T> List<T> select(CommandContext commandContext, String statement, AnalyticsParameter parameter,
      int limit) {
    parameter.maxResults = limit;
    return select(commandContext, statement, parameter);
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
    return executeCached("activities", withRework, this::activities);
  }

  protected List<ActivityAnalytics> activities(CommandContext commandContext, AnalyticsParameter parameter) {
    Date now = ClockUtil.getCurrentTime();
    Map<String, ActivityAnalytics> activities = new TreeMap<>();

    // one scan of the range: counts, canceled runs and the histogram of the durations
    Map<String, Histogram> histograms = new HashMap<>();
    List<BucketRow> buckets = hashAggregate(commandContext, () -> select(commandContext, ACTIVITY_BUCKETS, parameter));
    for (BucketRow row : buckets) {
      if (row.key == null) {
        continue;
      }
      ActivityAnalytics activity = activities.computeIfAbsent(row.key, ProcessAnalyticsQueryImpl::newActivity);
      describe(activity, row.type, row.name);
      if (row.bucket == CANCELED_BUCKET) {
        activity.setCanceled(activity.getCanceled() + row.count);
      } else {
        activity.setFinished(activity.getFinished() + row.count);
        histograms.computeIfAbsent(row.key, ignored -> new Histogram())
            .add(row.bucket, row.count, row.total, row.minimum, row.maximum, row.withinTarget);
      }
    }
    for (ActivityCountRow row : this.<ActivityCountRow>select(commandContext, ACTIVITY_RUNNING, parameter)) {
      if (row.activityId == null) {
        continue;
      }
      ActivityAnalytics activity = activities.computeIfAbsent(row.activityId, ProcessAnalyticsQueryImpl::newActivity);
      describe(activity, row.activityType, row.activityName);
      activity.setRunning(activity.getRunning() + row.running);
      if (row.oldestRunning != null && (activity.getOldestRunningStart() == null
          || row.oldestRunning.before(activity.getOldestRunningStart()))) {
        activity.setOldestRunningStart(row.oldestRunning);
      }
    }
    for (ActivityAnalytics activity : activities.values()) {
      Histogram histogram = histograms.get(activity.getActivityId());
      if (histogram == null || histogram.getCount() == 0) {
        activity.setDurations(DurationStatistics.empty(parameter.target));
      } else {
        DurationStatistics durations = histogram.toStatistics(parameter.target, true);
        activity.setDurations(durations);
        activity.setTimeAboveMedian(histogram.timeAbove(durations.getP50()));
      }
    }

    // runs slower than the P90 of the range, among those running now, a batch per statement
    List<ActivitySince> checks = new ArrayList<>();
    for (ActivityAnalytics activity : activities.values()) {
      Long p90 = activity.getDurations().getP90();
      if (activity.getRunning() > 0 && p90 != null && checks.size() < MAX_RUNNING_CHECKS) {
        checks.add(new ActivitySince(activity.getActivityId(), new Date(now.getTime() - p90)));
      }
    }
    for (int start = 0; start < checks.size(); start += RUNNING_CHECKS_PER_STATEMENT) {
      parameter.activitySince = new ArrayList<>(
          checks.subList(start, Math.min(checks.size(), start + RUNNING_CHECKS_PER_STATEMENT)));
      for (KeyCountRow row : this.<KeyCountRow>select(commandContext, RUNNING_SINCE, parameter)) {
        ActivityAnalytics activity = activities.get(row.key);
        if (activity != null) {
          activity.setRunningOverP90(row.count);
        }
      }
    }
    parameter.activitySince = new ArrayList<>();

    if (withRework) {
      for (ActivityAnalytics activity : activities.values()) {
        activity.setRepeatedInstances(0L);
        activity.setRepeatedExecutions(0L);
      }
      for (ActivityRework rework : rework(commandContext, parameter)) {
        ActivityAnalytics activity = activities.get(rework.getActivityId());
        if (activity != null) {
          activity.setRepeatedInstances(rework.getRepeatedInstances());
          activity.setRepeatedExecutions(rework.getRepeatedExecutions());
        }
      }
    }

    for (KeyCountRow row : this.<KeyCountRow>select(commandContext, INCIDENTS, parameter)) {
      if (row.key == null) {
        continue;
      }
      ActivityAnalytics activity = activities.computeIfAbsent(row.key, id -> {
        ActivityAnalytics created = newActivity(id);
        created.setDurations(DurationStatistics.empty(parameter.target));
        if (withRework) {
          created.setRepeatedInstances(0L);
          created.setRepeatedExecutions(0L);
        }
        return created;
      });
      activity.setOpenIncidents(row.count);
      activity.setIncidents(row.extra);
    }
    return Collections.unmodifiableList(new ArrayList<>(activities.values()));
  }

  protected static void describe(ActivityAnalytics activity, String type, String name) {
    if (activity.getActivityType() == null) {
      activity.setActivityType(type);
    }
    if (name != null) {
      activity.setActivityName(name);
    }
  }

  @Override
  public List<ActivityRework> rework() {
    return executeCached("rework", null, this::rework);
  }

  /**
   * Scopes that ran an activity more than once in the range. The iterations of a multi-instance
   * activity share their parent, the body: those activities are taken from the deployed models of
   * the versions in scope and left out.
   */
  protected List<ActivityRework> rework(CommandContext commandContext, AnalyticsParameter parameter) {
    Set<String> multiInstance = multiInstanceActivities(commandContext, parameter);
    List<ActivityRework> rework = new ArrayList<>();
    for (KeyCountRow row : this.<KeyCountRow>select(commandContext, REPEATS, parameter)) {
      if (row.key != null && !multiInstance.contains(row.key)) {
        rework.add(new ActivityRework(row.key, row.count, row.extra));
      }
    }
    rework.sort(Comparator.comparing(ActivityRework::getActivityId));
    return Collections.unmodifiableList(rework);
  }

  protected Set<String> multiInstanceActivities(CommandContext commandContext, AnalyticsParameter parameter) {
    Set<String> ids = new HashSet<>();
    List<String> versions = parameter.definitionIds.isEmpty() ? definitionIdsOfKey(commandContext) : parameter.definitionIds;
    for (String id : versions.subList(0, Math.min(versions.size(), MAX_RESOLVED_DEFINITIONS))) {
      ProcessDefinitionEntity definition;
      try {
        definition = configuration.getDeploymentCache().findDeployedProcessDefinitionById(id);
      } catch (RuntimeException e) {
        // a version whose model cannot be read: its multi-instance activities stay unknown
        continue;
      }
      if (definition != null) {
        collectMultiInstance(definition.getActivities(), ids);
      }
    }
    return ids;
  }

  protected static void collectMultiInstance(List<? extends PvmActivity> activities, Set<String> ids) {
    for (PvmActivity activity : activities) {
      String id = activity.getId();
      if (id != null && id.endsWith(MULTI_INSTANCE_SUFFIX)) {
        ids.add(id.substring(0, id.length() - MULTI_INSTANCE_SUFFIX.length()));
      }
      collectMultiInstance(activity.getActivities(), ids);
    }
  }

  protected static ActivityAnalytics newActivity(String activityId) {
    ActivityAnalytics activity = new ActivityAnalytics();
    activity.setActivityId(activityId);
    return activity;
  }

  // --- instances and trend --------------------------------------------------------------------

  @Override
  public InstanceAnalytics instances() {
    return executeCached("instances", null, (commandContext, parameter) -> {
      InstanceCountRow count = new InstanceCountRow();
      List<InstanceCountRow> counts = new ArrayList<>(this.<InstanceCountRow>select(commandContext, INSTANCE_COUNTS, parameter));
      counts.addAll(this.<InstanceCountRow>select(commandContext, INSTANCE_RUNNING, parameter));
      for (InstanceCountRow row : counts) {
        if (row != null) {
          count.completed += row.completed;
          count.terminated += row.terminated;
          count.running += row.running;
        }
      }
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
    return executeCached("trend", bounds, (commandContext, parameter) -> {
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
    return executeCached("runs", statement + "|" + activityId + "|" + maxResults, (commandContext, parameter) -> {
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
    return executeCached("userTasks", maxTasks, (commandContext, parameter) -> {
      List<UserTaskRow> rows = select(commandContext, USER_TASKS, parameter, maxTasks + 1);
      boolean sampled = rows.size() > maxTasks;
      if (sampled) {
        rows = rows.subList(0, maxTasks);
      }
      Map<String, TaskTimes> byTask = new LinkedHashMap<>();
      Map<String, TaskTimes> byAssignee = new LinkedHashMap<>();
      for (UserTaskRow row : rows) {
        TaskTimes task = byTask.computeIfAbsent(row.taskDefinitionKey, TaskTimes::new);
        if (task.name == null) {
          task.name = row.name;
        }
        // a task completed without assignee goes under null, apart from a user named "null"
        TaskTimes assignee = byAssignee.computeIfAbsent(row.assignee, TaskTimes::new);
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
