-- Fasti BPM: removes the optional performance indexes of oracle_fasti_perf_indexes_create.sql
-- (Oracle; docs/performance.md). The engine works without them, so they can be dropped while
-- Camunda 7.24 and Fasti nodes are running; the queries they serve get slower again. Run it with
-- SQL*Plus, SQLcl or SQL Developer (each block ends with "/"). Can be run again: an index that
-- does not exist (ORA-01418) is skipped. The last blocks remove the commented indexes of the
-- create script, if they were created.
--
-- Busy cluster: DROP INDEX needs an exclusive lock on the table and fails at once with ORA-00054
-- while another session has uncommitted changes in it, which on a busy cluster is nearly always.
-- The session therefore waits up to 60 seconds for the lock (ddl_lock_timeout, Oracle 11g and
-- later); if ORA-00054 still comes, run the script again later. On Enterprise Edition 12c and
-- later "drop index ... online" does not need that lock.
alter session set ddl_lock_timeout = 60;

begin
  execute immediate 'drop index FASTI_IDX_JOB_DUEDATE';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_TASK_DUE';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_TASK_ASSIGNEE_DUE';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_ID_INFO_USER';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_HI_PI_PDEF_START';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_HI_AI_PDEF_START';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/

begin
  execute immediate 'drop index FASTI_IDX_INC_TIMESTAMP';
exception when others then
  if sqlcode != -1418 then raise; end if;
end;
/
