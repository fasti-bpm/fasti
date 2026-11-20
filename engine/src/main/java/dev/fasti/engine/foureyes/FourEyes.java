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
package dev.fasti.engine.foureyes;

import dev.fasti.engine.foureyes.impl.FourEyesServiceImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/** Entry point to the four-eyes control of a process engine. */
public final class FourEyes {

  private FourEyes() {
  }

  /** The four-eyes service of the engine; it reports {@link FourEyesService#isEnabled()} {@code false} without the plugin. */
  public static FourEyesService of(ProcessEngine processEngine) {
    return new FourEyesServiceImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
