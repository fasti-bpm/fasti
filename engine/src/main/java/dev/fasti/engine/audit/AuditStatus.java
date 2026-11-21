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

import java.util.List;
import java.util.Set;

/** How the audit trail of an engine is set up. Holds no secret. */
public interface AuditStatus {

  /** Identifies this node in the chain ids ({@code <node id>/<slot>}). */
  String getNodeId();

  /** How many chains this node writes to in parallel. */
  int getChains();

  /** The categories that are recorded. */
  Set<String> getTypes();

  /** How variable values are recorded: {@code hash}, {@code value} or {@code none}. */
  String getVariableValues();

  /** Patterns of variable names whose values are never recorded. */
  List<String> getMaskedVariables();

  /** Whether checkpoints are signed with an HMAC key. */
  boolean isSigned();

  /** First characters of the SHA-256 of the HMAC key, to tell keys apart; {@code null} without key. */
  String getKeyId();

  /**
   * Ids of earlier keys whose signatures are still accepted: of retired keys the engine holds
   * (checked) and of keys that are only listed (not checked); {@code unsigned} if records without
   * signature are accepted.
   */
  List<String> getRetiredKeyIds();

  /** Records between two checkpoints of a chain; 0 for none. */
  int getCheckpointInterval();

  /** Records younger than this many days cannot be purged. */
  int getRetentionDays();

  /** The history level of the engine, which decides which events the engine produces. */
  String getHistoryLevel();
}
