-- Fasti BPM: optional performance indexes for Microsoft SQL Server (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; mssql_fasti_perf_indexes_drop.sql removes them.
--
-- NOT YET VERIFIED ON SQL SERVER: the index definitions were measured on PostgreSQL 16 and
-- MariaDB 11.4 (docs/performance.md); this script was only reviewed for syntax. Run it on a test
-- system first and compare the plans of the queries named below.
--
-- Run it in the database and default schema of the engine tables. It can be run again: an index
-- of that name on the table is skipped, and so is a table that does not exist (ACT_ID_INFO is
-- missing where the engine never used its identity tables, for example with LDAP).
-- Update the statistics afterwards.
-- None of the indexes is filtered, on purpose: a filtered index makes writes fail for sessions
-- without the SET options it requires (error 1934), which could hit other clients of the table.
--
-- Busy cluster: without ONLINE = ON (Enterprise Edition, Azure SQL: add "with (online = on)" to
-- each create index) a build holds a shared lock on the table for its whole duration, so writes
-- to it wait on every node until the index is built; with ONLINE = ON it still needs short
-- locks at its start and end. While a build waits for its lock, the statements that come after
-- it wait too. The lock wait is limited to 10 seconds here (the default is to wait forever): if a
-- statement fails with error 1222, run the script again later. See "On a busy cluster" in
-- docs/performance.md.
set lock_timeout 10000;

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node): without it each
-- acquisition reads all of ACT_RU_JOB, mostly timers that are not due. SUSPENSION_STATE_ first,
-- so the jobs of suspended instances are not read at all, and RETRIES_ last, so the failed jobs
-- (no retries left) are rejected inside the index without reading their rows. SQL Server sorts
-- NULL first, so "due date is null or due" is one range of this index, also in due date order
-- (jobExecutorAcquireByDueDate). Job lists by due date (Cockpit, REST) do not use it.
-- Write cost: one entry per job; rewritten when a job is rescheduled, fails or is suspended.
if object_id('ACT_RU_JOB') is not null
  and not exists (select 1 from sys.indexes where name = 'FASTI_IDX_JOB_DUEDATE' and object_id = object_id('ACT_RU_JOB'))
  create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (SUSPENSION_STATE_, DUEDATE_, RETRIES_);

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists and
-- the dashboard.
-- Write cost: one entry per task; changed only when the due date changes.
if object_id('ACT_RU_TASK') is not null
  and not exists (select 1 from sys.indexes where name = 'FASTI_IDX_TASK_DUE' and object_id = object_id('ACT_RU_TASK'))
  create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_);

-- Tasks of one assignee counted or sorted by due date ("my tasks") and unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
if object_id('ACT_RU_TASK') is not null
  and not exists (select 1 from sys.indexes where name = 'FASTI_IDX_TASK_ASSIGNEE_DUE' and object_id = object_id('ACT_RU_TASK'))
  create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_);

-- User info of one user: the absence lookup at each task assignment (fasti.absence.enabled) and
-- the user profile; ACT_ID_INFO has no index besides its primary key. Reading the absences of
-- all users (the absence listings) still reads the whole table.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
if object_id('ACT_ID_INFO') is not null
  and not exists (select 1 from sys.indexes where name = 'FASTI_IDX_ID_INFO_USER' and object_id = object_id('ACT_ID_INFO'))
  create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_);

-- Second group: worth it only for the usage named; remove the "-- " of a block to create it.
-- The blocks can be run again like the ones above.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_HI_PI_PDEF_START' and object_id = object_id('ACT_HI_PROCINST'))
--   create index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_);
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_HI_AI_PDEF_START' and object_id = object_id('ACT_HI_ACTINST'))
--   create index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_);
--
-- User task times of the process analytics (the newest user tasks of one process completed in
-- a range): the stock index of ACT_HI_TASKINST on PROC_DEF_KEY_ alone makes the database read all
-- tasks of the process. One more entry per user task, so only where that view is used on
-- processes with many tasks.
-- if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_HI_TI_PDKEY_END' and object_id = object_id('ACT_HI_TASKINST'))
--   create index FASTI_IDX_HI_TI_PDKEY_END on ACT_HI_TASKINST (PROC_DEF_KEY_, END_TIME_);
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_INC_TIMESTAMP' and object_id = object_id('ACT_RU_INCIDENT'))
--   create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_);
