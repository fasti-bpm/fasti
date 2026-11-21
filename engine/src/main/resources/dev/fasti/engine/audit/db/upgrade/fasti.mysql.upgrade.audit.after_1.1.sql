--
-- SPDX-License-Identifier: Apache-2.0
-- Copyright Fasti BPM contributors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Fasti audit trail (docs/audit-trail.md): upgrade of audit tables created by Fasti BPM 1.0 or 1.1.
-- Only for databases that have the tables FASTI_AUDIT_EVENT and FASTI_AUDIT_HEAD; tables created by
-- a later version have these indexes already. The tables of Camunda 7.24 are not changed. The engine
-- never runs this script: a database administrator does. It can be run again: an index that exists
-- is not created again, and one that is gone is not dropped again.
--
-- A page of the audit trail is ordered by TIME_, CHAIN_ID_, SEQ_ (all descending, or all ascending
-- for oldest first). FASTI_IDX_AUDIT_ORDER gives that order, so the database reads one page instead
-- of sorting every record the filter selects; it starts with TIME_ and replaces FASTI_IDX_AUDIT_TIME.
-- FASTI_IDX_AUDIT_ACTOR_ORDER and FASTI_IDX_AUDIT_TYPE_ORDER put the columns of the usual filters
-- (the actor; the category and the action) before that order, so that a filtered page is read in
-- order as well and the database stops after it; they replace FASTI_IDX_AUDIT_ACTOR and
-- FASTI_IDX_AUDIT_TYPE, so an insert still updates seven indexes. The new indexes are created
-- before the old ones are dropped. Nodes of 1.0 and 1.1 keep working with them (no code names an
-- index). The drop scripts of 1.0 and 1.1 drop FASTI_IDX_AUDIT_TIME, _ACTOR and _TYPE by name and
-- fail on an upgraded table: drop it with the drop script of this version.
--
-- For MySQL and MariaDB. Run it in the database of the audit tables (mysql/mariadb client: source
-- <file>). Each statement looks the index up in information_schema first, as MySQL has no CREATE
-- INDEX IF NOT EXISTS. The indexes are built online (ALGORITHM=INPLACE LOCK=NONE): records are
-- written meanwhile; a server that cannot build one that way refuses the statement instead of
-- locking the table. Each statement still takes a short exclusive metadata lock at its start and
-- at its end, which waits for the open transactions on the table (a running export, a long
-- transaction), and every audited command queues behind it meanwhile. lock_wait_timeout lets such
-- a statement give up after 10 seconds instead: run the script again later. Run ANALYZE TABLE
-- FASTI_AUDIT_EVENT afterwards.

set session lock_wait_timeout = 10;

set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT (TIME_, CHAIN_ID_, SEQ_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_ORDER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_AUDIT_ACTOR_ORDER on FASTI_AUDIT_EVENT (ACTOR_, TIME_, CHAIN_ID_, SEQ_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_ACTOR_ORDER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) = 0,
    'create index FASTI_IDX_AUDIT_TYPE_ORDER on FASTI_AUDIT_EVENT (TYPE_, ACTION_, TIME_, CHAIN_ID_, SEQ_) algorithm=inplace lock=none',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_TYPE_ORDER');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_AUDIT_TIME on FASTI_AUDIT_EVENT',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_TIME');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_AUDIT_ACTOR on FASTI_AUDIT_EVENT',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_ACTOR');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;

set @fasti_ddl = (select if(count(*) > 0,
    'drop index FASTI_IDX_AUDIT_TYPE on FASTI_AUDIT_EVENT',
    'do 0')
  from information_schema.statistics
  where table_schema = database() and upper(table_name) = 'FASTI_AUDIT_EVENT' and upper(index_name) = 'FASTI_IDX_AUDIT_TYPE');
prepare fasti_stmt from @fasti_ddl;
execute fasti_stmt;
deallocate prepare fasti_stmt;
