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
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds records of the audit trail. Results are ordered by time, newest first (or oldest first
 * with {@link #oldestFirst()}), then by chain and position. Reading needs membership of an
 * administrators group or {@code READ} on the resource {@code SYSTEM}.
 */
public interface AuditEventQuery {

  AuditEventQuery processInstanceId(String processInstanceId);

  AuditEventQuery businessKey(String businessKey);

  /** Records of this authenticated user. */
  AuditEventQuery actor(String actor);

  /** Records written by the engine itself (no authenticated user). */
  AuditEventQuery systemActor();

  /** One or more categories ({@code TYPE_*} of {@link AuditService}). */
  AuditEventQuery types(String... types);

  AuditEventQuery action(String action);

  AuditEventQuery tenantId(String tenantId);

  AuditEventQuery processDefinitionKey(String processDefinitionKey);

  AuditEventQuery entityId(String entityId);

  AuditEventQuery chainId(String chainId);

  /** Records at or after this time. */
  AuditEventQuery from(Date from);

  /** Records before this time. */
  AuditEventQuery to(Date to);

  AuditEventQuery oldestFirst();

  long count();

  List<AuditEvent> list();

  /** A page of the result; the page is cut in the database. */
  List<AuditEvent> listPage(int firstResult, int maxResults);

  /**
   * Hands the first {@code maxResults} records of the result to the consumer one by one, read from
   * one cursor, so that a large result is never held in memory; for exports. The consumer runs
   * inside the command that reads. Returns how many records it got.
   */
  long forEach(int maxResults, Consumer<? super AuditEvent> consumer);
}
