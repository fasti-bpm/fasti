-- Fasti BPM: optional performance indexes for MySQL and MariaDB (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; mysql_fasti_perf_indexes_drop.sql removes them.
--
-- Measured on MariaDB 11.4 and PostgreSQL 16 with 1,000,000 running instances, 1,000,000 tasks
-- and 1,000,000 jobs (990,000 of them timers that are not due); the numbers are in
-- docs/performance.md. Not run on MySQL itself. Only indexes that cut the work of their queries
-- by an order of magnitude are active here; the second group is commented out.
--
-- Run it in the database of the engine tables (mysql/mariadb client: source <file>). It can be
-- run again: each statement looks the index up in information_schema first, which works on
-- MySQL as well as on MariaDB (MySQL has no CREATE INDEX IF NOT EXISTS).
-- The indexes are built online (ALGORITHM=INPLACE LOCK=NONE): reads and writes go on. If the
-- server cannot build one that way it refuses the statement instead of locking the table.
-- Run ANALYZE TABLE on the tables afterwards.

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node) and timers by due
-- date: without it each acquisition reads all of ACT_RU_JOB, mostly timers that are not due.
-- (MySQL and MariaDB sort NULL first, so "due date is null or due" is one range of this index.)
-- Write cost: one entry per job; changed again only when a job is rescheduled.
set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (DUEDATE_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_JOB' and upper(index_name) = 'FASTI_IDX_JOB_DUEDATE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists,
-- the dashboard and the workload page.
-- Write cost: one entry per task; changed only when the due date changes.
set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_TASK' and upper(index_name) = 'FASTI_IDX_TASK_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Tasks of one assignee counted or sorted by due date ("my tasks", workload per user) and
-- unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_TASK' and upper(index_name) = 'FASTI_IDX_TASK_ASSIGNEE_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- User info by user: absences (fasti.absence.enabled) are looked up at each task assignment,
-- and ACT_ID_INFO has no index besides its primary key.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_ID_INFO' and upper(index_name) = 'FASTI_IDX_ID_INFO_USER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Second group: worth it only for the usage named; remove the "-- " to create them.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- create index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_) algorithm=inplace lock=none;
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- create index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_) algorithm=inplace lock=none;
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_) algorithm=inplace lock=none;
