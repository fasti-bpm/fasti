-- Fasti BPM: optional performance indexes for PostgreSQL (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; postgres_fasti_perf_indexes_drop.sql removes them.
--
-- Measured on PostgreSQL 16 with 1,000,000 running instances, 1,000,000 tasks, 1,000,000 jobs
-- (990,000 of them timers that are not due) and 2,200,000 authorizations; the numbers are in
-- docs/performance.md. Only indexes that cut the work of their queries by an order of magnitude
-- are active here; the second group is commented out (see the conditions there).
--
-- CREATE INDEX CONCURRENTLY does not block writers, but it cannot run inside a transaction: use
-- psql (autocommit), not a tool that wraps the script in a transaction. For such a tool remove
-- the word "concurrently"; the tables are then locked against writes while each index is built
-- (seconds per million rows). A concurrent build that fails leaves an INVALID index of that name
-- behind, which "if not exists" then skips. Find it with
--   select indexrelid::regclass from pg_index where not indisvalid;
-- drop it (drop index concurrently <name>) and run the script again. The engine reports an
-- invalid index as missing. Run ANALYZE on the tables afterwards.

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node) and timers by due
-- date: without it each acquisition reads all of ACT_RU_JOB, mostly timers that are not due.
-- Partial, so failed and suspended jobs stay out of it. NULLS FIRST on purpose: with
-- jobExecutorAcquireByDueDate=true (and ensureJobDueDateNotNull=false) an ascending index would
-- let the planner walk the whole index whenever nothing is due (measured: 990,000 rows per
-- acquisition); this order cannot serve that sort, so the planner takes the due jobs and sorts them.
-- Write cost: one entry per executable job, and one more for each update of such a job that is
-- not a HOT update (in the measurements no job update was, with or without this index: locking
-- rewrites the row). Failed and suspended jobs cost nothing.
create index concurrently if not exists FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (DUEDATE_ nulls first)
  where RETRIES_ > 0 and SUSPENSION_STATE_ = 1;

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists,
-- the dashboard and the workload page.
-- Write cost: one entry per task, and one more for each update of the task (task updates are no
-- HOT updates already, because the stock schema indexes the assignee and the last update time).
create index concurrently if not exists FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_);

-- Tasks of one assignee counted or sorted by due date ("my tasks", workload per user) and
-- unassigned overdue tasks.
-- Write cost: one entry per task, and one more for each update of the task, as for
-- FASTI_IDX_TASK_DUE.
create index concurrently if not exists FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_);

-- User info by user: absences (fasti.absence.enabled) are looked up at each task assignment,
-- and ACT_ID_INFO has no index besides its primary key.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
create index concurrently if not exists FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_);

-- Second group: worth it only for the usage named; remove the "-- " to create them.
--
-- History of one process definition newest first (Cockpit history list of a definition). With
-- authorization enabled it also bounds the authorization join. One more entry per started
-- process instance.
-- create index concurrently if not exists FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_);
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- create index concurrently if not exists FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_);
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- create index concurrently if not exists FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_);
