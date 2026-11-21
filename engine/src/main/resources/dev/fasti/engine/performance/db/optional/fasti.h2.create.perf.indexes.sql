-- Fasti BPM: optional performance indexes for H2 (docs/performance.md).
--
-- Optional and additive: non-unique indexes on tables of the Camunda 7.24 schema. No table,
-- column or constraint is changed, nothing is recorded in ACT_GE_SCHEMA_LOG, and the engine never
-- runs this script. h2_fasti_perf_indexes_drop.sql removes the indexes again.
--
-- H2 is the database for trying things out, and the engine reports the missing indexes there
-- only at INFO. The indexes matter on a production database with large tables (the numbers in
-- docs/performance.md are from PostgreSQL 16 and MariaDB 11.4); this script exists so that the
-- same set can be tried on H2. Can be run again. A lock wait ends after 10 seconds.
set lock_timeout 10000;

-- Job acquisition: suspended jobs skipped by the leading column, failed jobs inside the index.
create index if not exists FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB (SUSPENSION_STATE_, DUEDATE_, RETRIES_);

-- Overdue tasks and tasks by due date.
create index if not exists FASTI_IDX_TASK_DUE on ACT_RU_TASK (DUE_DATE_);

-- Tasks of one assignee counted or sorted by due date, unassigned overdue tasks.
create index if not exists FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK (ASSIGNEE_, DUE_DATE_);

-- User info of one user (the absence lookup at each task assignment).
create index if not exists FASTI_IDX_ID_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_);

-- Second group: worth it only for the usage named; remove the "-- " to create them.
--
-- History of one process definition newest first (Cockpit history list of a definition). One
-- more entry per started process instance.
-- create index if not exists FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST (PROC_DEF_ID_, START_TIME_);
--
-- Activity statistics of a definition (the definition page reads up to 2,000 finished activity
-- instances every 30 seconds). ACT_HI_ACTINST is the largest history table: one more entry per
-- activity instance, so only where that page is used on definitions with large histories.
-- create index if not exists FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST (PROC_DEF_ID_, START_TIME_);
--
-- Open incidents oldest first (incidents page, dashboard), with tens of thousands of open incidents.
-- create index if not exists FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT (INCIDENT_TIMESTAMP_);
