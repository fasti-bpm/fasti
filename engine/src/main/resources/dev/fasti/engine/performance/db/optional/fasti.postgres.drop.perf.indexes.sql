-- Fasti BPM: removes the optional performance indexes of postgres_fasti_perf_indexes_create.sql
-- (PostgreSQL; docs/performance.md). The engine works without them, so they can be dropped while
-- Camunda 7.24 and Fasti nodes are running; the queries they serve get slower again.
-- Run it with the schema of the engine tables first in the search_path (set search_path to
-- <schema>; when it is not public). Can be run again.
--
-- Lock waits: DROP INDEX CONCURRENTLY does not block reads or writes, but it waits for the
-- transactions that use the table, so it has no lock timeout here (a long transaction makes it
-- wait, nothing else). It cannot run inside a transaction: use psql (autocommit). For a tool that
-- wraps the script in a transaction remove the word "concurrently" and run
--   set lock_timeout = '10s';
-- first: a plain DROP INDEX needs an exclusive lock on the table, and while it waits every new
-- statement on the table waits behind it. If the timeout hits, run the script again later.

drop index concurrently if exists FASTI_IDX_JOB_DUEDATE;
drop index concurrently if exists FASTI_IDX_TASK_DUE;
drop index concurrently if exists FASTI_IDX_TASK_ASSIGNEE_DUE;
drop index concurrently if exists FASTI_IDX_ID_INFO_USER;
-- the commented indexes of the create script, if they were created
drop index concurrently if exists FASTI_IDX_HI_PI_PDEF_START;
drop index concurrently if exists FASTI_IDX_HI_AI_PDEF_START;
drop index concurrently if exists FASTI_IDX_INC_TIMESTAMP;
