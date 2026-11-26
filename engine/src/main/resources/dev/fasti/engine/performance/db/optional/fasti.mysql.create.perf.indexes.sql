-- Fasti BPM: optional performance indexes for MySQL and MariaDB (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script: a database administrator does. Camunda 7.24 nodes and Fasti nodes work on the
-- same database with or without these indexes; mysql_fasti_perf_indexes_drop.sql removes them.
--
-- Measured on MariaDB 11.4 and PostgreSQL 16 with 1,000,000 running instances, 1,000,000 tasks
-- and 1,000,000 jobs (990,000 of them timers that are not due); the numbers are in
-- docs/performance.md. Not run on MySQL itself (only on MariaDB). Only indexes that cut the work
-- of their queries by an order of magnitude are active here; the second group is commented out.
--
-- Run it in the database of the engine tables (mysql/mariadb client: source <file>). It can be
-- run again: each statement looks the index up in information_schema first, which works on
-- MySQL as well as on MariaDB (MySQL has no CREATE INDEX IF NOT EXISTS). A statement whose table
-- does not exist is skipped (ACT_ID_INFO is missing where the engine never used its identity
-- tables, for example with LDAP).
-- The indexes are built online (ALGORITHM=INPLACE LOCK=NONE): reads and writes go on. If the
-- server cannot build one that way it refuses the statement instead of locking the table.
-- Run ANALYZE TABLE on the tables afterwards.
--
-- Busy cluster: an online build still needs an exclusive metadata lock at its start and its
-- end. While it waits for one (behind any open transaction that has used the table), every new
-- statement on the table waits behind it, on every node. The lock wait is therefore limited to
-- 10 seconds here (the server default is a day on MariaDB, a year on MySQL): if a statement
-- fails with "Lock wait timeout exceeded", run the script again later. On MariaDB Galera with the
-- default wsrep_OSU_method=TOI the build runs on all nodes at once and blocks the table there;
-- use a quiet period or the rolling schema upgrade method (RSU). See "On a busy cluster" in
-- docs/performance.md.
set session lock_wait_timeout = 10;

-- Job acquisition (selectNextJobsToExecute, every few seconds on every node): without it each
-- acquisition reads all of ACT_RU_JOB, mostly timers that are not due. SUSPENSION_STATE_ first,
-- so the jobs of suspended instances are not read at all, and RETRIES_ last, so the failed jobs
-- (no retries left) are rejected inside the index without reading their rows: an idle
-- acquisition then reads a few index entries however many incidents are open. MySQL and MariaDB
-- sort NULL first, so "due date is null or due" is one range of this index, also in due date
-- order (jobExecutorAcquireByDueDate). Job lists by due date (Cockpit, REST) do not use it.
-- Write cost: one entry per job; rewritten when a job is rescheduled, fails or is suspended.
set @fasti_ddl = (select if(count(*) = 0
      and (select count(*) from information_schema.tables where table_schema = database() and table_name = 'ACT_RU_JOB') > 0,
    'create index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (SUSPENSION_STATE_, DUEDATE_, RETRIES_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and table_name = 'ACT_RU_JOB' and index_name = 'FASTI_IDX_JOB_DUEDATE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Overdue tasks and tasks by due date (dueBefore/dueAfter, sorting by due date): task lists and
-- the dashboard.
-- Write cost: one entry per task; changed only when the due date changes.
set @fasti_ddl = (select if(count(*) = 0
      and (select count(*) from information_schema.tables where table_schema = database() and table_name = 'ACT_RU_TASK') > 0,
    'create index FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and table_name = 'ACT_RU_TASK' and index_name = 'FASTI_IDX_TASK_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Tasks of one assignee counted or sorted by due date ("my tasks") and unassigned overdue tasks.
-- Write cost: one entry per task, rewritten at each claim, unclaim and reassignment (as the
-- stock index on ASSIGNEE_ is).
set @fasti_ddl = (select if(count(*) = 0
      and (select count(*) from information_schema.tables where table_schema = database() and table_name = 'ACT_RU_TASK') > 0,
    'create index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and table_name = 'ACT_RU_TASK' and index_name = 'FASTI_IDX_TASK_ASSIGNEE_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- User info of one user: the absence lookup at each task assignment (fasti.absence.enabled) and
-- the user profile; ACT_ID_INFO has no index besides its primary key. Reading the absences of
-- all users (the absence listings) still reads the whole table.
-- Write cost: one entry per user info row; written only when a profile or an absence changes.
set @fasti_ddl = (select if(count(*) = 0
      and (select count(*) from information_schema.tables where table_schema = database() and table_name = 'ACT_ID_INFO') > 0,
    'create index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and table_name = 'ACT_ID_INFO' and index_name = 'FASTI_IDX_ID_INFO_USER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- Second group: worth it only for the usage named; remove the "-- " of a block to create it.
-- The blocks can be run again like the ones above.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- set @fasti_ddl = (select if(count(*) = 0,
--     'create index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_) algorithm=inplace lock=none',
--     'do 0')
--   from information_schema.statistics
--   where table_schema = database() and table_name = 'ACT_HI_PROCINST' and index_name = 'FASTI_IDX_HI_PI_PDEF_START');
-- prepare fasti_stmt from @fasti_ddl;
-- execute fasti_stmt;
-- deallocate prepare fasti_stmt;
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- set @fasti_ddl = (select if(count(*) = 0,
--     'create index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_) algorithm=inplace lock=none',
--     'do 0')
--   from information_schema.statistics
--   where table_schema = database() and table_name = 'ACT_HI_ACTINST' and index_name = 'FASTI_IDX_HI_AI_PDEF_START');
-- prepare fasti_stmt from @fasti_ddl;
-- execute fasti_stmt;
-- deallocate prepare fasti_stmt;
--
-- User task times of the process analytics (the newest user tasks of one process completed in
-- a range): the stock index of ACT_HI_TASKINST on PROC_DEF_KEY_ alone makes the database read all
-- tasks of the process. One more entry per user task, so only where that view is used on
-- processes with many tasks.
-- set @fasti_ddl = (select if(count(*) = 0,
--     'create index FASTI_IDX_HI_TI_PDKEY_END on ACT_HI_TASKINST (PROC_DEF_KEY_, END_TIME_) algorithm=inplace lock=none',
--     'do 0')
--   from information_schema.statistics
--   where table_schema = database() and table_name = 'ACT_HI_TASKINST' and index_name = 'FASTI_IDX_HI_TI_PDKEY_END');
-- prepare fasti_stmt from @fasti_ddl;
-- execute fasti_stmt;
-- deallocate prepare fasti_stmt;
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- set @fasti_ddl = (select if(count(*) = 0,
--     'create index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_) algorithm=inplace lock=none',
--     'do 0')
--   from information_schema.statistics
--   where table_schema = database() and table_name = 'ACT_RU_INCIDENT' and index_name = 'FASTI_IDX_INC_TIMESTAMP');
-- prepare fasti_stmt from @fasti_ddl;
-- execute fasti_stmt;
-- deallocate prepare fasti_stmt;
