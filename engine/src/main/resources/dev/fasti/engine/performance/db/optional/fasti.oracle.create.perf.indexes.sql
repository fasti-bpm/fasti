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
-- already indexed (ORA-01408) is skipped, and so is a table that does not exist (ORA-00942;
-- ACT_ID_INFO is missing where the engine never used its identity tables, for example with LDAP).
-- On Enterprise Edition add the keyword ONLINE after the column list to build without blocking
-- writers, for example: create index ... on ACT_RU_TASK (DUE_DATE_) online
-- Gather statistics afterwards (DBMS_STATS.GATHER_TABLE_STATS).
--
-- Busy cluster: CREATE INDEX without ONLINE needs a lock that uncommitted changes of other
-- sessions hold, and fails at once with ORA-00054 while there are any (on a busy cluster, nearly
-- always); once it has the lock, writes to the table wait until the index is built. The session
-- therefore waits up to 60 seconds for the lock (ddl_lock_timeout, Oracle 11g and later); if
-- ORA-00054 still comes, use ONLINE or a quiet period. See "On a busy cluster" in
-- docs/performance.md.
alter session set ddl_lock_timeout = 60;

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node): without it each
-- acquisition reads all of ACT_RU_JOB, mostly timers that are not due. SUSPENSION_STATE_ first,
-- so the jobs of suspended instances are not read at all, and RETRIES_ last, so the failed jobs
-- (no retries left) are rejected inside the index without reading their rows. SUSPENSION_STATE_
-- is NOT NULL, so the jobs without due date are in the index as well (Oracle leaves a row out of
-- an index only when all its key columns are NULL). Job lists by due date (Cockpit, REST) do not
-- use it. Check on a test system: with jobExecutorAcquireByDueDate=true and
-- ensureJobDueDateNotNull=false, the acquisition plan must not read the whole index when nothing
-- is due (Oracle sorts NULL last, as PostgreSQL does, where an ascending index did that; see
-- docs/performance.md).
-- Write cost: one entry per job; rewritten when a job is rescheduled, fails or is suspended.
begin
  execute immediate 'create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (SUSPENSION_STATE_, DUEDATE_, RETRIES_)';
exception when others then
  if sqlcode not in (-955, -1408, -942) then raise; end if;
end;
/

-- Overdue tasks and tasks by due date (dueBefore/dueAfter): task lists and the dashboard. Oracle
-- leaves the tasks without due date out of this single-column index, so it serves the due date
-- filters (they exclude those tasks anyway), not a task list sorted by due date without such a
-- filter.
-- Write cost: one entry per task with a due date; changed only when the due date changes.
begin
  execute immediate 'create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_)';
exception when others then
  if sqlcode not in (-955, -1408, -942) then raise; end if;
end;
/

-- Tasks of one assignee counted or sorted by due date ("my tasks") and unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
begin
  execute immediate 'create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_)';
exception when others then
  if sqlcode not in (-955, -1408, -942) then raise; end if;
end;
/

-- User info of one user: the absence lookup at each task assignment (fasti.absence.enabled) and
-- the user profile; ACT_ID_INFO has no index besides its primary key. Reading the absences of
-- all users (the absence listings) still reads the whole table.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
begin
  execute immediate 'create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_)';
exception when others then
  if sqlcode not in (-955, -1408, -942) then raise; end if;
end;
/

-- Second group: worth it only for the usage named; remove the "-- " of a block to create it.
-- The blocks can be run again like the ones above.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- begin
--   execute immediate 'create index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_)';
-- exception when others then
--   if sqlcode not in (-955, -1408) then raise; end if;
-- end;
-- /
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- begin
--   execute immediate 'create index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_)';
-- exception when others then
--   if sqlcode not in (-955, -1408) then raise; end if;
-- end;
-- /
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- begin
--   execute immediate 'create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_)';
-- exception when others then
--   if sqlcode not in (-955, -1408) then raise; end if;
-- end;
-- /
