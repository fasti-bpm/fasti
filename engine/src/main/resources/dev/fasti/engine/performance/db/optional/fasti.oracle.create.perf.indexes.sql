-- Fasti BPM: optional performance indexes for Oracle (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; oracle_fasti_perf_indexes_drop.sql removes them.
--
-- NOT YET VERIFIED ON ORACLE: the index definitions were measured on PostgreSQL 16 and
-- MariaDB 11.4 (docs/performance.md); this script was only reviewed for syntax. Run it on a test
-- system first and compare the plans of the queries named below.
--
-- Run it with SQL*Plus, SQLcl or SQL Developer as the owner of the engine tables (each block
-- ends with "/"). It can be run again: an index that exists (ORA-00955) or whose column list is
-- already indexed (ORA-01408) is skipped.
-- On Enterprise Edition add the keyword ONLINE after the column list to build without blocking
-- writers, for example: create index ... on ACT_RU_TASK (DUE_DATE_) online
-- Gather statistics afterwards (DBMS_STATS.GATHER_TABLE_STATS).

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node) and timers by due
-- date: without it each acquisition reads all of ACT_RU_JOB, mostly timers that are not due.
-- The constant 0 as second key keeps the jobs without due date in the index (Oracle leaves rows
-- out of an index when all its key columns are NULL), as the tenant indexes of the engine do.
-- Check on a test system: with jobExecutorAcquireByDueDate=true and ensureJobDueDateNotNull=false,
-- the acquisition plan must not read the whole index when nothing is due (on PostgreSQL an
-- ascending index did that; see docs/performance.md).
-- Write cost: one entry per job; changed again only when a job is rescheduled.
begin
  execute immediate 'create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (DUEDATE_, 0)';
exception when others then
  if sqlcode not in (-955, -1408) then raise; end if;
end;
/

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists,
-- the dashboard and the workload page.
-- Write cost: one entry per task; changed only when the due date changes.
begin
  execute immediate 'create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_)';
exception when others then
  if sqlcode not in (-955, -1408) then raise; end if;
end;
/

-- Tasks of one assignee counted or sorted by due date ("my tasks", workload per user) and
-- unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
begin
  execute immediate 'create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_)';
exception when others then
  if sqlcode not in (-955, -1408) then raise; end if;
end;
/

-- User info by user: absences (fasti.absence.enabled) are looked up at each task assignment,
-- and ACT_ID_INFO has no index besides its primary key.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
begin
  execute immediate 'create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_)';
exception when others then
  if sqlcode not in (-955, -1408) then raise; end if;
end;
/

-- Second group: worth it only for the usage named; remove the "-- " to create them.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- create index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_);
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- create index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_);
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_);
