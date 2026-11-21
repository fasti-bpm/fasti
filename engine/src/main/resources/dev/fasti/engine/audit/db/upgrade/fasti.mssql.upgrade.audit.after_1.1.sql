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
-- Only for databases that have the tables FASTI_AUDIT_EVENT and FASTI_AUDIT_HEAD; new tables are
-- created with this index. The tables of Camunda 7.24 are not changed.
--
-- A page of the audit trail is ordered by TIME_, CHAIN_ID_, SEQ_ (all descending, or all ascending
-- for oldest first). This index gives that order, so the database reads one page instead of
-- sorting every record the filter selects. It starts with TIME_ and so replaces
-- FASTI_IDX_AUDIT_TIME, which is dropped. Nodes of 1.0 and 1.1 keep working with it.
-- Creating the index reads the whole table; on a large table run it outside peak hours (and use
-- the online option of your database where it has one).

create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT(TIME_, CHAIN_ID_, SEQ_);
drop index FASTI_AUDIT_EVENT.FASTI_IDX_AUDIT_TIME;
