-- Fasti BPM: removes the optional performance indexes of db2_fasti_perf_indexes_create.sql
-- (IBM Db2; docs/performance.md). The engine works without them, so they can be dropped while
-- Camunda 7.24 and Fasti nodes are running; the queries they serve get slower again. Plain
-- statements: an index that does not exist is refused with SQL0204N and the command line
-- processor goes on. The last statements remove the commented indexes of the create script, if
-- they were created.
--
-- Busy cluster: DROP INDEX needs an exclusive lock on the table. The lock wait is limited to 10
-- seconds here (the default LOCKTIMEOUT of -1 waits forever): if a statement fails with
-- SQL0911N, run the script again later.
set current lock timeout 10;

drop index FASTI_IDX_JOB_DUEDATE;
drop index FASTI_IDX_TASK_DUE;
drop index FASTI_IDX_TASK_ASSIGNEE_DUE;
drop index FASTI_IDX_ID_INFO_USER;
drop index FASTI_IDX_HI_PI_PDEF_START;
drop index FASTI_IDX_HI_AI_PDEF_START;
drop index FASTI_IDX_INC_TIMESTAMP;
