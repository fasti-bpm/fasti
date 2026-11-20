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

import dev.fasti.engine.audit.impl.AuditServiceImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/** Entry point to the audit trail of a process engine. */
public final class AuditTrail {

  private AuditTrail() {
  }

  /** The audit service of the engine; it reports {@link AuditService#isEnabled()} {@code false} without the plugin. */
  public static AuditService of(ProcessEngine processEngine) {
    return new AuditServiceImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
