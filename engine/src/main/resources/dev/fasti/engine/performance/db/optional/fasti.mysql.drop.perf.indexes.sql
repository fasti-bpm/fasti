-- Fasti BPM: removes the optional performance indexes of mysql_fasti_perf_indexes_create.sql
-- (MySQL and MariaDB; docs/performance.md). The engine works without them, so they can be
-- dropped at any time, also while Camunda 7.24 and Fasti nodes are running; the queries they
-- serve get slower again. Can be run again: an index that is not there is skipped.

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_JOB_DUEDATE on ACT_RU_JOB',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_JOB' and upper(index_name) = 'FASTI_IDX_JOB_DUEDATE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_TASK_DUE on ACT_RU_TASK',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_TASK' and upper(index_name) = 'FASTI_IDX_TASK_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_TASK_ASSIGNEE_DUE on ACT_RU_TASK',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_TASK' and upper(index_name) = 'FASTI_IDX_TASK_ASSIGNEE_DUE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_ID_INFO_USER on ACT_ID_INFO',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_ID_INFO' and upper(index_name) = 'FASTI_IDX_ID_INFO_USER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

-- the commented indexes of the create script, if they were created
set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_HI_PI_PDEF_START on ACT_HI_PROCINST',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_HI_PROCINST' and upper(index_name) = 'FASTI_IDX_HI_PI_PDEF_START');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_HI_AI_PDEF_START on ACT_HI_ACTINST',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_HI_ACTINST' and upper(index_name) = 'FASTI_IDX_HI_AI_PDEF_START');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_INC_TIMESTAMP on ACT_RU_INCIDENT',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'ACT_RU_INCIDENT' and upper(index_name) = 'FASTI_IDX_INC_TIMESTAMP');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;
