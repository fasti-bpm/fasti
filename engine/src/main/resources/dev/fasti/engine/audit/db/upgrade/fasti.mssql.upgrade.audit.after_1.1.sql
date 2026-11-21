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
-- Run it with sqlcmd in the database of the audit tables. Each statement looks the index up in
-- sys.indexes first. Without ONLINE every insert into FASTI_AUDIT_EVENT waits while an index is
-- built, and with it every command that writes audit records, holding the head of its chain: the
-- other commands of that chain fail after fasti.audit.lock-timeout-seconds. On Enterprise Edition
-- and Azure SQL add WITH (ONLINE = ON) to each CREATE INDEX; elsewhere run the script at a quiet
-- time. LOCK_TIMEOUT lets a statement that waits for a lock give up after 10 seconds instead of
-- making every insert queue behind it: run the script again later.

set lock_timeout 10000;

if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_ORDER' and object_id = object_id('FASTI_AUDIT_EVENT'))
  create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT (TIME_, CHAIN_ID_, SEQ_);

if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_ACTOR_ORDER' and object_id = object_id('FASTI_AUDIT_EVENT'))
  create index FASTI_IDX_AUDIT_ACTOR_ORDER on FASTI_AUDIT_EVENT (ACTOR_, TIME_, CHAIN_ID_, SEQ_);

if not exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_TYPE_ORDER' and object_id = object_id('FASTI_AUDIT_EVENT'))
  create index FASTI_IDX_AUDIT_TYPE_ORDER on FASTI_AUDIT_EVENT (TYPE_, ACTION_, TIME_, CHAIN_ID_, SEQ_);

if exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_TIME' and object_id = object_id('FASTI_AUDIT_EVENT'))
  drop index FASTI_IDX_AUDIT_TIME on FASTI_AUDIT_EVENT;

if exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_ACTOR' and object_id = object_id('FASTI_AUDIT_EVENT'))
  drop index FASTI_IDX_AUDIT_ACTOR on FASTI_AUDIT_EVENT;

if exists (select 1 from sys.indexes where name = 'FASTI_IDX_AUDIT_TYPE' and object_id = object_id('FASTI_AUDIT_EVENT'))
  drop index FASTI_IDX_AUDIT_TYPE on FASTI_AUDIT_EVENT;
