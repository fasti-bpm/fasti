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
-- Run it with the Db2 command line (db2 -tvf <file>) as the owner of the audit tables. Db2 builds
-- an index while inserts go on. It has no IF [NOT] EXISTS for indexes: run again, a statement
-- fails with SQLSTATE 42710 (the index exists) or 42704 (it is gone), which does no harm, and the
-- command line goes on with the next one. The lock timeout lets a statement that waits for a lock
-- give up after 10 seconds: run the script again later. Run RUNSTATS on the table afterwards.

set current lock timeout 10;

create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT (TIME_, CHAIN_ID_, SEQ_);
create index FASTI_IDX_AUDIT_ACTOR_ORDER on FASTI_AUDIT_EVENT (ACTOR_, TIME_, CHAIN_ID_, SEQ_);
create index FASTI_IDX_AUDIT_TYPE_ORDER on FASTI_AUDIT_EVENT (TYPE_, ACTION_, TIME_, CHAIN_ID_, SEQ_);
drop index FASTI_IDX_AUDIT_TIME;
drop index FASTI_IDX_AUDIT_ACTOR;
drop index FASTI_IDX_AUDIT_TYPE;
