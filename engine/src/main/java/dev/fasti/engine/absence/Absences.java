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
package dev.fasti.engine.absence;

import dev.fasti.engine.absence.impl.AbsenceServiceImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/** Entry point to the absences of a process engine. */
public final class Absences {

  private Absences() {
  }

  /** The absence service of the engine; it reports {@link AbsenceService#isEnabled()} {@code false} without the plugin. */
  public static AbsenceService of(ProcessEngine processEngine) {
    return new AbsenceServiceImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
