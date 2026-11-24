English | [Tiếng Việt](process-analytics.vi.md)

# Process analytics

Fasti computes process analytics in the database, over the whole history the engine already
writes: a heatmap on the BPMN diagram, the bottlenecks of a process, how long process instances
take and how that changes over time, and where the time of user tasks goes. There is no separate
analytics store, no import and no delay: what finished a second ago is counted. Available from
Fasti 1.3 in the web interface (process → **Analytics**), over REST and in Java.

## What you get

| | Fasti | Camunda 7 Community Edition |
|---|---|---|
| Heatmap on the diagram | Reached, median, P90, P99, total time, waiting now, rework, incidents; the value on every activity | Running instances and incidents only |
| Durations | Percentiles (P50 to P99), average, maximum, histogram | — |
| Bottlenecks | Ranked by the time spent, with the time beyond the median | — |
| Live view | Runs waiting now, those slower than the historic P90, the oldest one | Running instances only |
| Rework | Activities run again in the same instance, multi-instance excluded | — |
| Targets | Share of instances within a target duration, exact | — |
| Trend | Median and P90 per day, week or month | — |
| User tasks | Waiting (until someone takes the task) vs working, per task and per assignee | — |
| Drill-down | The slowest finished runs and the oldest running ones, linked to their instances | — |

Camunda 7 Enterprise has a heatmap of how often activities ran and two duration reports in
Cockpit. Camunda Optimize, the paid analytics product for Camunda 7 and 8, imports the history
into Elasticsearch or OpenSearch and compares targets with averages. Fasti needs neither.

## In the web interface

Open a process in Monitoring and choose the tab **Analytics**.

- **Range and versions.** What finished in the last 7, 30, 90 or 365 days, for this version or
  all versions of the process.
- **Target.** Type a duration (`90m`, `4h`, `2d`): the tiles show which share of the instances
  completed within it, and the trend draws it as a line.
- **Heatmap.** Choose what the colours show. Each activity carries its value; select an activity
  for its details: percentiles, the histogram of its durations, rework and incidents, the slowest
  finished runs and the runs waiting longest now, each linked to its instance.
- **Bottlenecks.** The activities by the time spent in them, all runs together. The time beyond the
  median is what making the slow runs typical would save.
- **Duration over time.** Median and P90 of the instances completed per day, week or month.
- **User tasks.** Waiting and working time per task and per assignee.

A server without the analytics endpoints (Fasti before 1.3, during a rolling upgrade) gets the
former statistics, computed in the browser from the 2,000 newest runs.

## Accuracy

- Counts, totals, averages, minimum and maximum are exact.
- Percentiles of activities and process instances come from a histogram with twenty buckets per power
  of ten, computed by the database, and are within about 12 % of the exact value. The interface
  marks them with ≈. The share within a target is exact.
- Waiting and working times of user tasks are exact. Waiting runs from the creation of a task to
  its first assignment, taken from the identity link history that the engine writes at history
  level `full`; tasks without it are counted as "unknown waiting".
- Rework counts scopes (a process or subprocess instance) that ran an activity more than once in
  the range. The iterations of a multi-instance activity are not rework.
- A run counts in the range in which it finished. What runs now is counted whatever the range.

## Permissions and tenants

Reading analytics requires `READ_HISTORY` on the process definition key, as the history
statistics of the engine do. With the tenant check on, only the history of the user's tenants and
of shared definitions is read.

## REST

All under `/analytics/process-definition/key/{key}` (also `/engine/{name}/analytics/...`), with the
parameters `processDefinitionIdIn`, `tenantIdIn`, `finishedAfter`, `finishedBefore` and
`targetDuration` (milliseconds):

| Call | Answer |
|---|---|
| `GET .../activities` | One entry per activity: counts, duration statistics, running over P90, rework, incidents |
| `GET .../activities/{activityId}/instances` | The slowest finished runs, or with `running=true` the oldest running ones (`maxResults`, at most 200) |
| `GET .../instances` | Completed, terminated and running instances, duration statistics, within target |
| `GET .../trend?boundaries=...` | Per period between the given dates (2 to 63): started, completed, duration statistics |
| `GET .../user-tasks` | Waiting and working time per task and per assignee (`maxTasks`, 50,000 by default) |

Details and examples: the OpenAPI document of the REST API (tag *Process Analytics*).

## Java

```java
ProcessAnalyticsQuery query = ProcessAnalytics.query(processEngine)
    .processDefinitionKey("invoice")
    .finishedAfter(thirtyDaysAgo)
    .targetDuration(Duration.ofHours(4).toMillis());

List<ActivityAnalytics> activities = query.activities();
InstanceAnalytics instances = query.instances();
List<SlowInstance> slowest = query.slowest("approveInvoice", 10);
UserTaskAnalytics userTasks = query.userTasks(50_000);
```

`dev.fasti.engine.analytics` in `fasti-engine`.

## How it works, and the cost

The statements read the history tables `ACT_HI_ACTINST`, `ACT_HI_PROCINST`, `ACT_HI_TASKINST`,
`ACT_HI_IDENTITYLINK` and `ACT_HI_INCIDENT` of one process and group what they read in the
database. They use plain SQL that PostgreSQL, MySQL, MariaDB, Oracle, SQL Server, Db2 and H2 all
run: a duration goes into its bucket through a nested `CASE` of constant numbers, a date into its
period through a `CASE` of bound dates, so no date, logarithm or percentile function is needed.
Nothing is written and the schema is unchanged.

The cost grows with the history of the process in the range: the activity statements read the
runs of the process that finished in it, through the index on `(PROC_DEF_ID_, END_TIME_)` when
versions are chosen. Shorter ranges and one version are cheaper. User task times read at most
`maxTasks` tasks, the newest first. An application that replaced the default `mappings.xml`
must add `dev/fasti/engine/analytics/ProcessAnalytics.xml`; without it the endpoints answer 501.
