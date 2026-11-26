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
package dev.fasti.engine.analytics;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Analytics of one process, computed by the database over the history: per activity (for a
 * heatmap and the bottlenecks), per process instance, per period, and for user tasks.
 *
 * <p>Requires {@code READ_HISTORY} on the process definition key when authorization is enabled,
 * like the historic activity statistics of the engine; only the history of tenants the user
 * belongs to is read when the tenant check is enabled.</p>
 *
 * <p>Answers are kept for a short time ({@code AnalyticsCache}, 60 seconds by default, system
 * property {@code fasti.analytics.cacheTtlSeconds}): the same query within that time, by the same
 * or another user who passes the permission check, gets the same answer without reading the
 * history again. Only answers of a range with {@link #finishedBefore(Date)} are kept; calls that
 * ask the same at the same time share one read either way. Answers are shared and must not be
 * changed.</p>
 *
 * <p>Each statement may run for {@link ProcessAnalytics#setStatementTimeout} (120 seconds by
 * default); a longer one fails the call with {@link AnalyticsTimeoutException}.</p>
 *
 * <p>The range selects what <em>finished</em> (activities, process instances, tasks): from
 * {@link #finishedAfter(Date)} (inclusive) to {@link #finishedBefore(Date)} (exclusive), by
 * default the last 30 days. What is running now is counted whatever the range.</p>
 */
public interface ProcessAnalyticsQuery {

  /** At most this many process definition ids, tenant ids and trend periods. */
  int MAX_IDS = 200;
  int MAX_PERIODS = 62;
  int MAX_RESULTS = 200;
  int MAX_USER_TASKS = 200_000;
  int DEFAULT_USER_TASKS = 50_000;

  /** Required: the process. All its versions unless {@link #processDefinitionIdIn(Collection)} narrows them. */
  ProcessAnalyticsQuery processDefinitionKey(String processDefinitionKey);

  /** Only these versions of the process; ids of other processes are refused. */
  ProcessAnalyticsQuery processDefinitionIdIn(Collection<String> processDefinitionIds);

  /** Only the history of these tenants. */
  ProcessAnalyticsQuery tenantIdIn(Collection<String> tenantIds);

  ProcessAnalyticsQuery finishedAfter(Date finishedAfter);

  ProcessAnalyticsQuery finishedBefore(Date finishedBefore);

  /** A target duration in milliseconds: the duration statistics also count how many stayed within it. */
  ProcessAnalyticsQuery targetDuration(Long targetMillis);

  /**
   * Leaves rework out of {@link #activities()}: rework groups the runs by scope, the most costly
   * part on a long history; {@link #rework()} reads it on its own, for example after the rest.
   */
  ProcessAnalyticsQuery withoutRework();

  /** One entry per activity that ran or runs. */
  List<ActivityAnalytics> activities();

  /** Rework per activity, as {@link #activities()} includes it unless {@link #withoutRework()}. */
  List<ActivityRework> rework();

  InstanceAnalytics instances();

  /**
   * One entry per period between consecutive boundaries (at least two, at most
   * {@value #MAX_PERIODS} + 1, ascending); the range of the query does not apply.
   */
  List<PeriodAnalytics> trend(List<Date> boundaries);

  /** The runs of an activity finished in the range, longest first. */
  List<SlowInstance> slowest(String activityId, int maxResults);

  /** The runs of an activity running now, oldest first. */
  List<SlowInstance> oldestRunning(String activityId, int maxResults);

  /** Waiting and working time of the user tasks completed in the range, the newest {@code maxTasks} at most. */
  UserTaskAnalytics userTasks(int maxTasks);
}
