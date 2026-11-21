-- Fasti BPM: removes the optional performance indexes of h2_fasti_perf_indexes_create.sql
-- (H2; docs/performance.md). The engine works without them, so they can be dropped at any
-- time; the queries they serve get slower again. Can be run again. A lock wait ends after 10
-- seconds.
set lock_timeout 10000;

drop index if exists FASTI_IDX_JOB_DUEDATE;
drop index if exists FASTI_IDX_TASK_DUE;
drop index if exists FASTI_IDX_TASK_ASSIGNEE_DUE;
drop index if exists FASTI_IDX_ID_INFO_USER;
-- the commented indexes of the create script, if they were created
drop index if exists FASTI_IDX_HI_PI_PDEF_START;
drop index if exists FASTI_IDX_HI_AI_PDEF_START;
drop index if exists FASTI_IDX_INC_TIMESTAMP;
