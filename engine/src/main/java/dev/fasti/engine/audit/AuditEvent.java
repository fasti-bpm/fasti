/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fasti.engine.audit;

import java.util.Date;

/**
 * One record of the audit trail. Records are never changed: each one carries the hash of the
 * record before it in its chain and its own hash over all of its fields.
 */
public interface AuditEvent {

  /** Unique id of the record. */
  String getId();

  /** The chain the record belongs to: {@code <node id>/<slot>}. */
  String getChainId();

  /** Position in the chain, starting at 1 without gaps. */
  long getSequence();

  /** When it happened, by the clock of the engine. */
  Date getTimestamp();

  /** Category, one of the {@code TYPE_*} constants of {@link AuditService}. */
  String getType();

  /** What happened, for example {@code start}, {@code complete}, {@code assign} or an operation type of the user operation log. */
  String getAction();

  /** The authenticated user, or {@code null} for the engine itself (job executor, timers, startup). */
  String getActor();

  /** Name of the process engine that wrote the record. */
  String getEngineName();

  String getTenantId();

  String getProcessDefinitionKey();

  String getProcessDefinitionId();

  String getProcessInstanceId();

  String getBusinessKey();

  /** The kind of object the record is about, for example {@code task}, {@code variable}, {@code User}. */
  String getEntityType();

  String getEntityId();

  /** A readable name of the object: task name, variable name, deployment name, property. */
  String getEntityName();

  /** Further facts as a JSON object, or {@code null}. */
  String getDetails();

  /** SHA-256 (hex) of the previous record of the chain; 64 zeros for the first one. */
  String getPreviousHash();

  /** SHA-256 (hex) of the previous hash and the canonical form of this record. */
  String getHash();
}
