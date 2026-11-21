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

-- Fasti audit trail (docs/audit-trail.md). Optional tables, created only when the audit trail is
-- enabled; the tables of Camunda 7.24 are not changed. TIME_ is in milliseconds since
-- 1970-01-01T00:00Z, so the hashed value does not depend on time zones or timestamp precision.
-- The application only inserts into FASTI_AUDIT_EVENT (and deletes on an explicit purge);
-- revoke UPDATE (and, outside purges, DELETE) on it from the application's database user.

create table FASTI_AUDIT_EVENT (
    ID_ varchar(64) not null,
    CHAIN_ID_ varchar(255) not null,
    SEQ_ bigint not null,
    TIME_ bigint not null,
    TYPE_ varchar(32) not null,
    ACTION_ varchar(64),
    ACTOR_ varchar(255),
    ENGINE_ varchar(255),
    TENANT_ID_ varchar(64),
    PROC_DEF_KEY_ varchar(255),
    PROC_DEF_ID_ varchar(64),
    PROC_INST_ID_ varchar(64),
    BUSINESS_KEY_ varchar(255),
    ENTITY_TYPE_ varchar(64),
    ENTITY_ID_ varchar(64),
    ENTITY_NAME_ varchar(255),
    DETAILS_ clob(2G),
    PREV_HASH_ varchar(64) not null,
    HASH_ varchar(64) not null,
    primary key (ID_)
);

create unique index FASTI_IDX_AUDIT_CHAIN on FASTI_AUDIT_EVENT(CHAIN_ID_, SEQ_);
-- the order of a page (newest or oldest first), and the records of a time range
create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT(TIME_, CHAIN_ID_, SEQ_);
create index FASTI_IDX_AUDIT_PROCINST on FASTI_AUDIT_EVENT(PROC_INST_ID_);
create index FASTI_IDX_AUDIT_BUSKEY on FASTI_AUDIT_EVENT(BUSINESS_KEY_);
create index FASTI_IDX_AUDIT_ACTOR on FASTI_AUDIT_EVENT(ACTOR_);
create index FASTI_IDX_AUDIT_TYPE on FASTI_AUDIT_EVENT(TYPE_, TIME_);

-- one row per chain: position and hash of its last record; appending locks the row
create table FASTI_AUDIT_HEAD (
    CHAIN_ID_ varchar(255) not null,
    SEQ_ bigint not null,
    HASH_ varchar(64) not null,
    TIME_ bigint not null,
    primary key (CHAIN_ID_)
);
