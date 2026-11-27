-- Fasti BPM: optional performance indexes for IBM Db2 (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; db2_fasti_perf_indexes_drop.sql removes them.
--
-- Run on Db2 12.1 (Community Edition, 4 K pages): created, dropped and created again; with
-- FASTI_IDX_JOB_DUEDATE the job acquisition read 0 to 38 rows instead of 200,000. The other
-- definitions were measured on PostgreSQL 16 and MariaDB 11.4 (docs/performance.md); compare the
-- plans of the queries named below on a test system first.
--
-- Run it with the schema of the engine tables as current schema (db2 -tvf <file>). Plain
-- statements: run again, an index that exists gives the warning SQL0605W (SQLSTATE 01550), and
-- a name that exists with other columns is refused with SQL0601N; both mean an index of that name
-- is there, and the command line processor goes on with the next statement. A table that does not exist gives
-- SQL0204N (ACT_ID_INFO is missing where the engine never used its identity tables, for example
-- with LDAP); ignore it. Db2 builds indexes while reads and writes go on. Run RUNSTATS on the
-- tables afterwards.
--
-- Busy cluster: a build still needs a short exclusive lock at its end. The lock wait is limited
-- to 10 seconds here (the default LOCKTIMEOUT of -1 waits forever): if a statement fails with
-- SQL0911N, run the script again later. See "On a busy cluster" in docs/performance.md.
set current lock timeout 10;

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node): without it each
-- acquisition reads all of ACT_RU_JOB, mostly timers that are not due. SUSPENSION_STATE_ first,
-- so the jobs of suspended instances are not read at all, and RETRIES_ last, so the failed jobs
-- (no retries left) are rejected inside the index without reading their rows. Job lists by due
-- date (Cockpit, REST) do not use it. Check on a test system: with
-- jobExecutorAcquireByDueDate=true and ensureJobDueDateNotNull=false, the acquisition plan must
-- not read the whole index when nothing is due (Db2 sorts NULL last, as PostgreSQL does, where an
-- ascending index did that; see docs/performance.md).
-- Write cost: one entry per job; rewritten when a job is rescheduled, fails or is suspended.
create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (SUSPENSION_STATE_, DUEDATE_, RETRIES_);

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists and
-- the dashboard.
-- Write cost: one entry per task; changed only when the due date changes.
create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_);

-- Tasks of one assignee counted or sorted by due date ("my tasks") and unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_);

-- User info of one user: the absence lookup at each task assignment (fasti.absence.enabled) and
-- the user profile; ACT_ID_INFO has no index besides its primary key. Reading the absences of
-- all users (the absence listings) still reads the whole table.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_);

-- Second group: worth it only for the usage named; remove the "-- " to create them. They can be
-- run again like the ones above.
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
-- User task times of the process analytics (the newest user tasks of one process completed in
-- a range): the stock index of ACT_HI_TASKINST on PROC_DEF_KEY_ alone makes the database read all
-- tasks of the process. One more entry per user task, so only where that view is used on
-- processes with many tasks.
-- create index FASTI_IDX_HI_TI_PDKEY_END on ACT_HI_TASKINST (PROC_DEF_KEY_, END_TIME_);
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_);
