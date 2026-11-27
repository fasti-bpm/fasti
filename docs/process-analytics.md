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
| `GET .../activities` | One entry per activity: counts, duration statistics, running over P90, rework, incidents; `rework=false` leaves rework out |
| `GET .../rework` | Rework per activity on its own: the costly part on a long history |
| `GET .../activities/{activityId}/instances` | The slowest finished runs, or with `running=true` the oldest running ones (`maxResults`, at most 200) |
| `GET .../instances` | Completed, terminated and running instances, duration statistics, within target |
| `GET .../trend?boundaries=...` | Per period between the given dates (2 to 63): started, completed, duration statistics |
| `GET .../user-tasks` | Waiting and working time per task and per assignee (`maxTasks`, 50,000 by default) |

Details and examples: the OpenAPI document of the REST API (tag *Process Analytics*).

Answers are kept for 60 seconds for the same query (`fasti.analytics.cache-ttl`, image
`FASTI_ANALYTICS_CACHE_TTL`, `0` turns this off, a number without unit is seconds), so a view
opened again or by a colleague is answered without reading the history; the permission is checked
on every call, and what runs now may be up to that much older. Calls that ask the same at the same
time read it once. Only answers of calls with `finishedBefore` are kept: without it the range ends
at the current millisecond, so the same answer would never be asked for again (such calls still
share a read that is running). The web interface ends the range at the next full minute (5, 15
and 60 minutes for 30, 90 and 365 days), so everyone looking at a range shares one read. At most
256 answers are kept per node; when a new one comes, expired answers make room first, then the
oldest.

**The engine comes first.** At most two scans of the range (activities, rework, user tasks) run
at the same time on a node (`fasti.analytics.max-concurrent`, image
`FASTI_ANALYTICS_MAX_CONCURRENT`, `0` for no limit), plus one quick read (instances, trend, runs),
so the quick parts of a view do not wait behind its scans. The others wait for their turn, in order, without holding a database connection, so the job executor,
task lists and the health check keep theirs. A call that gets no turn within 60 seconds
(`fasti.analytics.max-wait`, image `FASTI_ANALYTICS_MAX_WAIT`) answers 503. Each statement may
run for 120 seconds (`fasti.analytics.statement-timeout`, image
`FASTI_ANALYTICS_STATEMENT_TIMEOUT`, `0` for no timeout of its own, a number without unit is
seconds), so that two slow scans cannot hold both turns and their connections indefinitely; a
lower `jdbcStatementTimeout` of the engine applies either way. A call whose statement runs longer
answers 503 with a message that asks for a shorter range (Java: `AnalyticsTimeoutException`, a
kind of `AnalyticsBusyException`). The timeout is set on each statement through JDBC, so it works
on every supported database. Java: `ProcessAnalytics.setMaxConcurrent(int)`,
`setMaxWait(Duration)`, `setStatementTimeout(Duration)` and `setCacheTimeToLive(Duration)`.

The `fasti.analytics.*` properties (and `fasti.workload.cache-ttl`) are read by Fasti Run and the
container image. An embedded Spring Boot application, Tomcat or WildFly keeps the defaults above
unless it calls these Java methods at startup.

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

The cost grows with the history of the process in the range: the statements read the runs of the
process that finished in it. On `ACT_HI_ACTINST` and `ACT_HI_PROCINST` they seek the indexes on
`(PROC_DEF_ID_, END_TIME_)` (the versions of a process are resolved to their ids). The stock
schema indexes `ACT_HI_TASKINST` and `ACT_HI_INCIDENT` on `PROC_DEF_KEY_` only, so those
statements always give the key (and the chosen versions besides) and read the tasks and incidents
of that one process. For processes with many user tasks, the optional index
`FASTI_IDX_HI_TI_PDKEY_END` on `ACT_HI_TASKINST (PROC_DEF_KEY_, END_TIME_)` lets the user task
times read only the newest tasks of the range ([performance](performance.md)). Shorter ranges and
one version are cheaper. User task times read at most `maxTasks` tasks, the newest first, and the
drill-down at most `maxResults` runs; both limit in SQL. An application that replaced the default `mappings.xml` must add
`dev/fasti/engine/analytics/ProcessAnalytics.xml`; without it the endpoints answer 501.

## Measured

PostgreSQL 16 (4 CPUs, `work_mem` 16 MB), one process with 200,000 instances over a year: 2.45
million activity instances, 832,000 user tasks, 1.65 million identity links, 3,543 instances
running. Median of 11 calls in a row without the answer cache, as an administrator:

| Call | 30 days | 365 days |
|---|---|---|
| `activities?rework=false` (the heat map), one version | 0.12 s | 0.53 s |
| `activities?rework=false`, all versions | 0.13 s | 0.64 s |
| `rework`, one version / all versions | 0.11 / 0.13 s | 0.58 / 1.35 s |
| `instances` | 0.03–0.05 s | 0.08–0.13 s |
| `trend` | 0.06 s | 0.11–0.19 s |
| `user-tasks` (newest 50,000) | 0.36 s | 0.35–0.51 s |
| slowest / oldest running runs of an activity | 0.04–0.07 s | 0.02–0.33 s |

In the web interface the heat map of a year of all versions is on screen 1.0–1.7 s after the click
on the tab; a view opened again answers from the cache in about 0.1 s.

**Under load**, with the default settings: 25 people opening the analytics again and again (one to
three seconds apart, ranges from 7 to 365 days) got their views in 46 ms median and 134 ms at the
95th percentile, with PostgreSQL at 41 % of its CPUs, no error and the health check below 0.2 s.
While 10 of them did so, the engine kept about two thirds of its job throughput (28–35 % fewer
jobs per second than alone) and the task list answered in about 40 ms.

Compared with exact SQL over the same rows, every count matched, activity percentiles were within
4.7 %, instance percentiles within 0.3 % and the trend within 3.7 %; user task times were exact.
MariaDB 11.4 and SQL Server 2022 answered every call with the same results on a smaller history.
Oracle and Db2 were not run.

For long ranges on large histories:

- **PostgreSQL:** grouping a year of a busy process spills to disk with the default `work_mem`
  (4 MB); 32–64 MB for the engine's user makes it fit. The engine asks PostgreSQL to plan each
  analytics call for its own values (`plan_cache_mode`), which halves the time of long ranges,
  and to group the activity durations with a hash table (`enable_sort` off for that statement
  only): the planner expects hundreds of times more groups than there are and would sort the
  range on disk, which takes twice as long.
- **MySQL/MariaDB:** a larger `tmp_table_size` and `max_heap_table_size` (for example 256 MB) keeps
  the grouping in memory.
- **SQL Server:** add `sendStringParametersAsUnicode=false` to the JDBC URL so that the id
  parameters can use the indexes (this helps every query of the engine).
