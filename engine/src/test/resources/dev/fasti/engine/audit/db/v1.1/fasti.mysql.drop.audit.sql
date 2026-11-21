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

-- Drops the tables of the Fasti audit trail, with all audit records. Export them first.

drop index FASTI_IDX_AUDIT_CHAIN on FASTI_AUDIT_EVENT;
drop index FASTI_IDX_AUDIT_TIME on FASTI_AUDIT_EVENT;
drop index FASTI_IDX_AUDIT_PROCINST on FASTI_AUDIT_EVENT;
drop index FASTI_IDX_AUDIT_BUSKEY on FASTI_AUDIT_EVENT;
drop index FASTI_IDX_AUDIT_ACTOR on FASTI_AUDIT_EVENT;
drop index FASTI_IDX_AUDIT_TYPE on FASTI_AUDIT_EVENT;

drop table FASTI_AUDIT_EVENT;
drop table FASTI_AUDIT_HEAD;
