-- Fasti BPM: removes the optional performance indexes of postgres_fasti_perf_indexes_create.sql
-- (PostgreSQL; docs/performance.md). The engine works without them, so they can be dropped at any
-- time, also while Camunda 7.24 and Fasti nodes are running; the queries they serve get slower again.
-- DROP INDEX CONCURRENTLY does not block writers, but it cannot run inside a transaction: use
-- psql (autocommit), or remove the word "concurrently". Can be run again.

drop index concurrently if exists FASTI_IDX_JOB_DUEDATE;
drop index concurrently if exists FASTI_IDX_TASK_DUE;
drop index concurrently if exists FASTI_IDX_TASK_ASSIGNEE_DUE;
drop index concurrently if exists FASTI_IDX_ID_INFO_USER;
-- the commented indexes of the create script, if they were created
drop index concurrently if exists FASTI_IDX_HI_PI_PDEF_START;
drop index concurrently if exists FASTI_IDX_HI_AI_PDEF_START;
drop index concurrently if exists FASTI_IDX_INC_TIMESTAMP;
