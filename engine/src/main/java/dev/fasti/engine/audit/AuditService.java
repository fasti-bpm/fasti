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
 * The audit trail of a process engine: query its records, verify that none was changed,
 * removed or inserted, and purge old records after they were exported.
 *
 * <p>Reading, exporting and verifying need membership of an administrators group (for example
 * {@code camunda-admin}) or the permission {@code READ} on the resource {@code SYSTEM}; purging
 * needs an administrators group or {@code DELETE} on {@code SYSTEM}. Without authorization checks
 * anyone may, as everywhere in the engine.</p>
 */
public interface AuditService {

  String TYPE_PROCESS = "process";
  String TYPE_TASK = "task";
  String TYPE_VARIABLE = "variable";
  String TYPE_DEPLOYMENT = "deployment";
  String TYPE_OPERATION = "operation";
  String TYPE_INCIDENT = "incident";
  String TYPE_DECISION = "decision";
  String TYPE_ACTIVITY = "activity";
  /** Records the audit trail writes about itself: checkpoints, verifications, exports, purges. */
  String TYPE_AUDIT = "audit";

  String ACTION_CHECKPOINT = "checkpoint";
  String ACTION_VERIFY = "verify";
  String ACTION_EXPORT = "export";
  String ACTION_PURGE = "purge";

  /** Whether the audit trail plugin runs on this engine. */
  boolean isEnabled();

  /** The settings of the plugin, or {@code null} if it does not run. */
  AuditStatus getStatus();

  /** Whether the authenticated user may read the audit trail ({@code false} if it does not run). */
  boolean isReadAuthorized();

  AuditEventQuery createQuery();

  /** Verifies every chain and records the result as an audit record. */
  AuditVerification verify();

  /** Verifies one chain and records the result as an audit record. */
  AuditVerification verify(String chainId);

  /**
   * Records that the records selected by a filter were exported, by whom and how many.
   *
   * @param format for example {@code csv} or {@code json}
   * @param filter a readable description of the filter
   */
  void recordExport(String format, String filter, long count);

  /**
   * Removes, per chain, the records from the start of the chain up to the last one before
   * {@code before}, and appends a {@code purge} record to the chain that names the hash of the last
   * removed record, so the rest of the chain still verifies. Export the records first: an export
   * carries the hashes, so the removed part can still be verified against the purge record.
   *
   * @throws org.camunda.bpm.engine.BadUserRequestException if {@code before} is within the retention period
   */
  AuditPurgeResult purge(Date before);
}
