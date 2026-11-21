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

import org.camunda.bpm.engine.ProcessEngineException;

/**
 * A verification of the audit trail was asked for while another one runs on the engine. A
 * verification reads and hashes every record, so an engine runs one at a time; ask again when it
 * is done.
 */
public class AuditVerificationRunningException extends ProcessEngineException {

  private static final long serialVersionUID = 1L;

  public AuditVerificationRunningException() {
    super("A verification of the audit trail is running on this process engine; try again when it is done.");
  }
}
