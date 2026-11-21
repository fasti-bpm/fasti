English | [Tiếng Việt](performance.vi.md)

# Database performance

> **New in the release after 1.1.0.** Earlier releases do not ship the optional index scripts or
> the startup hints.

A Fasti BPM database works on the Camunda 7.24 schema unchanged. That schema indexes foreign keys
well, but a few queries that the engine and the web interface run all the time have no index that
fits them once the tables grow: job acquisition, overdue tasks, tasks of one assignee by due date,
and the absence lookup at each task assignment. This page describes

- the **optional index scripts** (what they add, how to apply and remove them per database, and
  why they are safe next to Camunda 7.24 nodes),
- the **hints at engine start** (missing indexes, settings that cost performance) and how to
  switch them off,
- the **engine settings** worth checking on a large database,
- and the **measurements** behind every index in the scripts, including the candidates that were
  rejected.

## The optional index scripts

Every distribution ships one create and one drop script per database:

| Distribution | Location |
|---|---|
| Fasti BPM Run | `configuration/sql/optional/` |
| Container image | `/fasti/configuration/sql/optional/` |
| Tomcat, WildFly | `sql/optional/` |
| Maven | `dev.fasti.platform.distro:fasti-sql-scripts`, folder `sql/optional/` |

Files: `<database>_fasti_perf_indexes_create.sql` and `<database>_fasti_perf_indexes_drop.sql`
with `<database>` one of `postgres`, `mysql` (also for MariaDB), `oracle`, `mssql`, `db2`, `h2`.

### What they create

| Index | Table (columns) | Serves |
|---|---|---|
| `FASTI_IDX_JOB_DUEDATE` | `ACT_RU_JOB (DUEDATE_)`; PostgreSQL: `NULLS FIRST`, only jobs with retries left that are not suspended; Oracle: `(DUEDATE_, 0)` | job acquisition on every node every few seconds, timers by due date |
| `FASTI_IDX_TASK_DUE` | `ACT_RU_TASK (DUE_DATE_)` | overdue and due-soon tasks, sorting by due date (task lists, dashboard, workload) |
| `FASTI_IDX_TASK_ASSIGNEE_DUE` | `ACT_RU_TASK (ASSIGNEE_, DUE_DATE_)` | "my tasks" by due date, per-user counts of the workload page, unassigned overdue tasks |
| `FASTI_IDX_ID_INFO_USER` | `ACT_ID_INFO (USER_ID_, KEY_)` | absence lookup at each task assignment ([absence](absence.md)), user profile |

A second group is in the scripts as comments. Create those only for the usage named:

| Index | Table (columns) | When |
|---|---|---|
| `FASTI_IDX_HI_PI_PDEF_START` | `ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_)` | history list of one definition newest first, in particular with authorization enabled |
| `FASTI_IDX_HI_AI_PDEF_START` | `ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_)` | the activity statistics of a definition page on definitions with large histories (one more entry per activity instance in the largest history table) |
| `FASTI_IDX_INC_TIMESTAMP` | `ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_)` | tens of thousands of open incidents, sorted oldest first |

### Side by side with Camunda 7.24, and rollback

- The indexes are **non-unique and additive**. No table, column or constraint changes, and a
  Camunda 7.24 node does not see a difference except in query plans.
- The engine **never runs these scripts** and they are **not recorded in `ACT_GE_SCHEMA_LOG`**,
  so the schema version check of 7.24 and Fasti nodes is unaffected, and `databaseSchemaUpdate`
  does not touch them.
- Shapes that could make a 7.24 node's writes fail are excluded on purpose: no index on the long
  text column of `ACT_RU_VARIABLE` (PostgreSQL and SQL Server refuse rows whose key is too long),
  no filtered index on SQL Server (writes fail with error 1934 for sessions without the required
  `SET` options), no index on columns that change on every lock or update of `ACT_RU_JOB` and
  `ACT_RU_EXECUTION` (`LOCK_OWNER_`, `LOCK_EXP_TIME_`, `REV_`).
- To roll back, run the drop script; it can run at any time, also while nodes are running. On
  PostgreSQL the schema after the drop script was compared with the schema before the create
  script (`pg_dump --schema-only`): identical.

### Applying them

Run the create script as the owner of the engine tables, then refresh the statistics. All scripts
can be run again (MySQL/MariaDB, PostgreSQL, SQL Server, Oracle and H2 skip an index that exists;
on Db2 the command line processor reports `SQL0601N` for it and goes on).

| Database | Command | Without blocking writes |
|---|---|---|
| PostgreSQL | `psql -f postgres_fasti_perf_indexes_create.sql`, then `ANALYZE` | The script uses `CREATE INDEX CONCURRENTLY`, which must not run inside a transaction: use psql (autocommit), not a tool that wraps the script in one. A failed concurrent build leaves an `INVALID` index behind (`select indexrelid::regclass from pg_index where not indisvalid`); drop it and run the script again. The startup hint reports an invalid index as missing. |
| MySQL, MariaDB | `mysql`/`mariadb` client: `source mysql_fasti_perf_indexes_create.sql`, then `ANALYZE TABLE` | The statements use `ALGORITHM=INPLACE, LOCK=NONE`; the server refuses a statement it cannot run that way instead of locking the table. |
| Oracle | SQL*Plus, SQLcl or SQL Developer | Add `ONLINE` after the column list (Enterprise Edition). |
| SQL Server | `sqlcmd -i mssql_fasti_perf_indexes_create.sql` | Add `WITH (ONLINE = ON)` (Enterprise Edition, Azure SQL). |
| Db2 | `db2 -tvf db2_fasti_perf_indexes_create.sql`, then `RUNSTATS` | Db2 builds indexes while reads and writes go on. |

Online builds still take short locks at the start and the end. On the measurement database each
index took 0.4 to 3.5 seconds to build (1 to 2 million rows, not concurrently).

**Verified**: PostgreSQL 16 and MariaDB 11.4 (both scripts run twice each, plans and write cost
measured below). **Not verified**: Oracle, SQL Server and Db2. Those scripts were reviewed for
syntax only and say so in their header; run them on a test system first. On Oracle and Db2 check
in particular that job acquisition with `jobExecutorAcquireByDueDate=true` does not scan the whole
new index when nothing is due (see [Job acquisition](#job-acquisition)).

## Hints at engine start

At each start the engine reads the index catalog of its database and its own configuration, and
logs what costs performance. It never changes anything, never fails the start, and a check it
cannot do (no permission to read the catalog, an unknown database) is skipped silently.

- **`FASTI-PERF-001`**: one warning listing the indexes of the create script that the database
  does not have, with the path of the script in each distribution. An index counts as present
  when any usable index of the table starts with the same columns in the same order, whatever its
  name, so an index a database administrator created counts too. On PostgreSQL an `INVALID`
  index counts as missing, and a partial index counts only with the script's predicate. On H2 the
  message is logged at INFO (H2 is for development).
- **`FASTI-PERF-002`**: history is written (level other than `none`), but history cleanup is
  disabled or has no batch window, so the history tables grow without limit.
- **`FASTI-PERF-003`**: `historyCleanupStrategy=endTimeBased`, whose selection no index supports.
- **`FASTI-PERF-004`**: the connection pool (HikariCP, the pool of the standalone engine, Tomcat
  JDBC, DBCP 2) allows fewer connections than job executor threads + 2, on a node that executes jobs.
- **`FASTI-PERF-005`**: `ensureJobDueDateNotNull=true`, but `ACT_RU_JOB` holds executable jobs
  without due date. This node never acquires those; they come from nodes without the setting
  (Camunda 7.24 or Fasti with the default).
- **`FASTI-PERF-006`**: `jobExecutorAcquireByDueDate=true` with `ensureJobDueDateNotNull=false`:
  every acquisition then sorts the due jobs and the jobs without due date (see
  [Job acquisition](#job-acquisition)).

| Distribution | Switches (`warn` default, `off`) |
|---|---|
| Run, Spring Boot starter | `fasti.performance.index-check`, `fasti.performance.config-check` |
| Container image | `FASTI_PERFORMANCE_INDEX_CHECK`, `FASTI_PERFORMANCE_CONFIG_CHECK` |
| Tomcat (`conf/bpm-platform.xml`), WildFly (`standalone*.xml`) | plugin `dev.fasti.engine.performance.PerformanceAdvisorPlugin`, properties `indexCheck`, `configCheck` |
| Own engine configuration | add `PerformanceAdvisorPlugin` to the process engine plugins |

## Engine settings for large databases

These settings are per node: a Fasti node with them and a Camunda 7.24 node without them work on
the same database.

| Setting | Recommendation |
|---|---|
| History cleanup | Set a batch window (`historyCleanupBatchWindowStartTime` / `EndTime`); without one, cleanup only runs when someone starts it. Keep the default strategy `removalTimeBased` (it deletes through the indexed `REMOVAL_TIME_` columns). Raise `historyCleanupDegreeOfParallelism` to 2–4 on large histories. Cleanup jobs are shared: any node with cleanup enabled may run them. |
| History level | `full` writes noticeably more than `audit` (variable details, operation log). [Four-eyes](four-eyes.md) and the operation log want `full`; otherwise `audit` or `activity` is enough. |
| Job executor | Size the threads to CPU and I/O, `maxJobsPerAcquisition` about queue + free threads, a small queue in a cluster so one node does not lock jobs it cannot run soon. `backoffTimeInMillis` (for example 50) and `maxBackoff` (for example 5000) reduce acquisition collisions between nodes. |
| Connection pool | At least job executor threads + concurrent requests + 2, and below what the database can serve for all nodes together (`FASTI-PERF-004` checks the first part). |
| `ensureJobDueDateNotNull` | Turns the acquisition condition into one range on the due date. With `FASTI_IDX_JOB_DUEDATE` the default acquisition is already cheap (6 buffers when nothing is due), so the setting matters mainly together with `jobExecutorAcquireByDueDate=true`. Enable it only when every node of the cluster sets it, after the jobs without due date have run (`FASTI-PERF-005`). |
| `jobExecutorAcquireByDueDate`, `…ByPriority` | Only with `FASTI_IDX_JOB_DUEDATE`; see [Job acquisition](#job-acquisition). |
| `queryMaxResultsLimit`, `jdbcStatementTimeout` | Set them (for example 2000 and 60 s) so an unbounded or pathological query cannot hold a connection. |
| `enableHistoricInstancePermissions` | Leave off unless needed: it multiplies the rows of `ACT_RU_AUTHORIZATION`. |
| Isolation level | READ COMMITTED (`READ_COMMITTED_SNAPSHOT ON` on SQL Server, `transaction-isolation=READ-COMMITTED` on MySQL/MariaDB) rather than `skipIsolationLevelCheck`. |
| Database | PostgreSQL: autovacuum scale factors per table for `ACT_RU_JOB`, `ACT_RU_EXECUTION`, `ACT_RU_VARIABLE` and the history tables (cleanup deletes in bulk); fresh statistics after bulk loads on every database. |

## Measurements

### Data set and method

One data set per database, generated directly in the engine tables after the engine had created
the schema and deployed 20 process definitions (user task with candidate group and due date,
asynchronous service task, timer). This is the PostgreSQL set; the MariaDB set has the same shape
at half the size and no variables:

- 1,000,000 running process instances, each waiting in a user task;
- 1,000,000 tasks: 60 % assigned among 5,000 users (about 200 each), half with a due date, about
  29,000 overdue;
- 1,600,000 candidate links (200 groups, 100,000 candidate users), 2,000,000 variables;
- 1,000,000 jobs: 990,000 timers due in 30 to 395 days, 5,000 failed (no retries left), 5,000
  suspended; 20,000 open incidents;
- 2,200,000 authorizations, as the engine writes them with authorization enabled (one per
  assignee and candidate of each task);
- 101,600 user info rows (20,000 users, 400 of them with an absence);
- 1,000,000 historic process instances, 1,600,000 historic activity instances.

The queries are the engine's own (Java API, so the statements come from the MyBatis mappings):
job acquisition, task counts and lists as the task list, dashboard and workload page use them,
the absence lookup, history lists and incidents, each without and with authorization. Per query
the table gives the statement as the database executed it (PostgreSQL `auto_explain` with
`ANALYZE` and `BUFFERS`; MariaDB `ANALYZE FORMAT=JSON`), after a first execution of the same
query, before and after the index. Buffers are 8 kB pages read from memory or disk; they do not
depend on load from other processes on the machine, whereas the times do (a shared developer
machine). The MariaDB data set is half the size (see below).

### PostgreSQL 16

| Query | Before | After | Index |
|---|---|---|---|
| Job acquisition, nothing due (the normal case) | 40,009 buffers, 253 ms (API median 142 ms) | 6 buffers, 0.1 ms (API 5.9 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Job acquisition, nothing due, `jobExecutorAcquireByDueDate=true` (API, median) | 228 ms | 15 ms | `FASTI_IDX_JOB_DUEDATE` (`NULLS FIRST`) |
| Job acquisition, `ensureJobDueDateNotNull=true` (API, median) | 156 ms | 33 ms (ascending variant: 7.5 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Job acquisition, 1,000 due jobs (API, median) | 203 ms | 80–90 ms (the statement still read 40,000 buffers: the exclusive-job subquery scanned the table) | `FASTI_IDX_JOB_DUEDATE` |
| Overdue tasks, first 15 by due date | 27,417 buffers, 64 ms | 36 buffers, 0.2 ms | `FASTI_IDX_TASK_DUE` |
| Count of overdue tasks (28,800) | 27,273 buffers, 91 ms | 18,196 buffers, 45 ms | `FASTI_IDX_TASK_DUE` |
| Count of overdue tasks of a candidate group | 50,028 buffers, 63 ms | 28,206 buffers, 35 ms | `FASTI_IDX_TASK_DUE` |
| Count of unassigned overdue tasks | 27,389 buffers, 61 ms | 9,729 buffers, 16 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Overdue tasks of one assignee (count) | 203 buffers | 20 buffers | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Tasks of one assignee, first 15 by due date | 203 buffers | 36 buffers | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| User info of one user (absence lookup) | 1,359 buffers, 23 ms | 8 buffers, 0.3 ms | `FASTI_IDX_ID_INFO_USER` |
| One user info entry | 1,359 buffers, 23 ms | 4 buffers, 0.1 ms | `FASTI_IDX_ID_INFO_USER` |
| History of one definition, newest 50 | 1,260 buffers, 2.9 ms | 71 buffers, 0.3 ms | `FASTI_IDX_HI_PI_PDEF_START` (second group) |
| … with authorization | 1,309,627 buffers, 1,279 ms | 350,498 buffers, 399 ms | `FASTI_IDX_HI_PI_PDEF_START` |
| Activity statistics of a definition (2,000 rows) | 29,909 buffers, 114 ms | 1,058 buffers, 9 ms | `FASTI_IDX_HI_AI_PDEF_START` (second group) |
| Open incidents, oldest 50 | 834 buffers, 21 ms | 67 buffers, 1.4 ms | `FASTI_IDX_INC_TIMESTAMP` (second group) |

With authorization enabled the effect depends on the query. The overdue list went from 87 to
48 ms, but counts over all tasks a user may see stayed very slow on this data set: counting all
overdue tasks took 60 s before and 63 and 76 s in two runs after, unassigned overdue tasks 21 s
before and 33 and 36 s after (CPU-bound runs, so the times vary; the statements read fewer
buffers after). The engine joins every candidate task with the user's authorizations there, and
none of the measured indexes fixed that; the workload page and the dashboard should not depend on
such counts on very large task tables.

<a id="job-acquisition"></a>**Job acquisition.** Without the index each acquisition reads all of
`ACT_RU_JOB`, on every node every few seconds, which with many future timers is the largest
constant load the engine puts on the database. A first variant with an ascending index made one
case much worse: with `jobExecutorAcquireByDueDate=true` and `ensureJobDueDateNotNull=false` the
statement is "due or without due date, ordered by due date, first three", and PostgreSQL walked
the whole index in order when nothing was due (990,000 index entries, 228 ms → 1,145 ms per
acquisition). The shipped index is `NULLS FIRST`, an order that cannot serve that sort, so the
planner fetches the due jobs through the index and sorts them (15 ms). With 100,000 due jobs that
sort costs more than the ordered walk would (83,000 against 42,000 buffers), which is a transient
backlog rather than the steady state. With `ensureJobDueDateNotNull=true` the shipped index
measured 33 ms per acquisition (median; minimum 19 ms) against 7.5 ms for the ascending variant
and 156 ms without index. MySQL, MariaDB, SQL Server and H2 sort NULL first anyway;
Oracle and Db2 sort NULL last like PostgreSQL and are not verified (see the script headers).

The priority index `(PRIORITY_ DESC, DUEDATE_)` that the Camunda documentation suggests for
`jobExecutorAcquireByPriority=true` has the same trap, and the scripts do not ship it. Measured
with acquisition by priority (API, median): no index 238 ms when nothing is due and 320 ms with
1,000 due jobs; `FASTI_IDX_JOB_DUEDATE` 11 ms and 52 ms; `FASTI_IDX_JOB_DUEDATE` plus the priority
index 711 ms and 54 ms, because PostgreSQL walked the priority index when nothing was due.

**Write cost.** Engine throughput with 8 threads (start, claim and complete a user task, then an
asynchronous service task and a timer through the job executor, authorization and history `full`
on), 60 seconds after 15 seconds of warm-up, configurations alternating:

| Indexes | Instances started, claimed and completed per second in the measured minute, each run | Mean |
|---|---|---|
| none | 26.4, 26.3, 27.0 | 26.6 |
| active set | 25.7, 21.7, 28.2, 31.9 | 26.9 |
| active set + second group | 27.0, 27.2 | 27.1 |

No throughput regression could be detected: the differences are within the run-to-run
variation of the shared machine (±15 %). That does not make the write cost zero. On PostgreSQL an
update that is not a HOT update adds an entry to every index of the table, and updates of
`ACT_RU_TASK` and `ACT_RU_JOB` were not HOT updates with or without the new indexes (the stock
schema already indexes the assignee and the last update time, and locking rewrites a job row), so
each task update and each update of an executable job now writes one or two more index entries.
The share of HOT updates of `ACT_RU_EXECUTION` stayed at 99 %.

**Rejected candidates** (in the analysis, measured, not shipped):

| Index | Result |
|---|---|
| `ACT_RU_AUTHORIZATION (USER_ID_, RESOURCE_TYPE_, RESOURCE_ID_) INCLUDE (TYPE_, PERMS_)` | Harmful. The planner switched the authorization check of task queries to a bitmap of all of the user's and groups' authorizations: overdue task list with authorization 48 ms → 4,187 ms, count of a candidate group 133 ms → 17,044 ms (some other queries got faster, for example the process instance count 164 → 89 ms). 190 MB. |
| `ACT_RU_IDENTITYLINK (GROUP_ID_, TYPE_, TASK_ID_)` | No order-of-magnitude gain: count of a candidate group 50,028 → 27,448 buffers, but the planner switched to a scan of the task table (8,149 pages read from disk in that run, 47 → 125 ms); with assigned tasks 40,031 → 30,103 buffers. 124 MB. |
| `ACT_RU_VARIABLE (NAME_, PROC_INST_ID_)` | Not used by any measured PostgreSQL plan (task queries filtered by process variable kept the stock index). Not measured on MariaDB (that data set has no variables). 136 MB on the hottest runtime table. |

### MariaDB 11.4

The MariaDB data set has the same shape at half the size (500,000 running instances, tasks and
jobs, 900,000 candidate links, 1,060,000 authorizations, 500,000 historic process instances and
785,000 historic activity instances, no variables): MariaDB's tables took about twice the space
of PostgreSQL's, and the measurement database ran in memory. InnoDB buffer pool 2 GB,
`READ-COMMITTED`. Rows are the rows the server examined (`r_rows` of `ANALYZE FORMAT=JSON`).

| Query | Before | After | Index |
|---|---|---|---|
| Job acquisition, nothing due | all 500,000 rows, 142 ms (API median 252 ms) | 5,000 rows, 14 ms (API 18 ms) | `FASTI_IDX_JOB_DUEDATE` |
| … with `jobExecutorAcquireByDueDate=true` (API) | 263 ms | 17 ms | `FASTI_IDX_JOB_DUEDATE` |
| … with 1,000 due jobs | all 437,655 rows, 320 ms (API 360 ms) | 1,100 rows, 3.6 ms (API 10 ms) | `FASTI_IDX_JOB_DUEDATE` |
| Overdue tasks, first 15 by due date | all 500,000 rows, 121 ms | 15 rows, 0.1 ms | `FASTI_IDX_TASK_DUE` |
| Count of overdue tasks (13,800) | all 500,000 rows, 88 ms | 13,827 rows, 2.1 ms | `FASTI_IDX_TASK_DUE` |
| Count of unassigned overdue tasks | all 500,000 rows, 88 ms | 5,411 rows, 0.8 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Overdue tasks of one assignee (count) | 100 rows, 0.28 ms | 6 rows, 0.02 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| Tasks of one assignee, first 15 by due date | 100 rows and a sort, 0.45 ms | 15 rows, 0.09 ms | `FASTI_IDX_TASK_ASSIGNEE_DUE` |
| User info of one user (absence lookup) | all 101,600 rows, 21 ms | 5 rows, 0.03 ms | `FASTI_IDX_ID_INFO_USER` |
| One user info entry | all 101,600 rows, 30 ms | 1 row, 0.03 ms | `FASTI_IDX_ID_INFO_USER` |
| History of one definition, newest 50 | 500,000 index entries, 1,074 ms | 25,000 rows (all of the definition; no early stop), 154 ms | `FASTI_IDX_HI_PI_PDEF_START` (second group) |
| Activity statistics of a definition (2,000 rows) | 37,500 rows, 77 ms | 2,000 rows, 5 ms | `FASTI_IDX_HI_AI_PDEF_START` (second group) |
| Open incidents, oldest 50 | all 10,000 rows, 2.5 ms | 50 rows, 0.1 ms | `FASTI_IDX_INC_TIMESTAMP` (second group) |

MySQL and MariaDB have no partial index, so the 5,000 rows of an idle acquisition are the failed
and suspended jobs that the PostgreSQL index leaves out. They sort NULL first, so "due or without
due date, ordered by due date" is one range of the index and acquisition by due date gains as
much as the default (no trap as on PostgreSQL). With authorization enabled, MariaDB evaluates
the authorization subquery for every candidate task: the counts and lists over all tasks a user
may see took 16 to 70 seconds before and after the indexes on this data set; the indexes do not
change that.

Each index took 0.4 to 2.0 seconds to build online (`ALGORITHM=INPLACE, LOCK=NONE`) and
26 to 29 MB (`FASTI_IDX_ID_INFO_USER` 6.5 MB).

**Write cost** (same benchmark as on PostgreSQL, alternating):

| Run (in this order) | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| Indexes | none | active set | none | active set | active set | none | active set |
| Completed instances per second | 13.1 | 12.3 | 11.4 | 9.3 | 10.3 | 10.6 | 9.1 |

Throughput fell steadily during the series in both configurations (13.1 to 9.1 per second, as the
history and authorization rows of the benchmark accumulated), so the cost of the indexes cannot
be separated from that drift: between neighbouring runs the runs with the indexes were 3 % faster
to 18 % slower. Treat the write cost on MariaDB as not measured precisely; it is bounded by one
more index entry per job and per task insert and one per claim.

## Reproducing

The measurement harness (generator SQL, the query driver on the engine API, the plan collectors)
is not part of the distribution. The method above is enough to repeat it: create the schema with
the engine, generate rows of the shape listed, `ANALYZE`, then run the engine queries with
`auto_explain` (PostgreSQL) or the general log and `ANALYZE FORMAT=JSON` (MariaDB).
