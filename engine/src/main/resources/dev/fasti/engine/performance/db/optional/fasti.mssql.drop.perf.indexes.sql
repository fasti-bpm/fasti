-- Fasti BPM: removes the optional performance indexes of mssql_fasti_perf_indexes_create.sql
-- (Microsoft SQL Server; docs/performance.md). The engine works without them, so they can be
-- dropped while Camunda 7.24 and Fasti nodes are running; the queries they serve get slower
-- again. Can be run again (DROP INDEX IF EXISTS, SQL Server 2016 and later).
--
-- Busy cluster: DROP INDEX needs a schema modification lock on the table, and while it waits
-- for one the statements that come after it wait too. The wait is limited to 10 seconds here
-- (the default is to wait forever): if a statement fails with error 1222, run the script again
-- later (see docs/performance.md).
set lock_timeout 10000;

drop index if exists FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB;
drop index if exists FASTI_IDX_TASK_DUE on ACT_RU_TASK;
drop index if exists FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK;
drop index if exists FASTI_IDX_ID_INFO_USER on ACT_ID_INFO;
-- the commented indexes of the create script, if they were created
drop index if exists FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST;
drop index if exists FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST;
drop index if exists FASTI_IDX_HI_TI_PDKEY_END on ACT_HI_TASKINST;
drop index if exists FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT;
