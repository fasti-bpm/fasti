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
package dev.fasti.engine.analytics;

import org.camunda.bpm.engine.ProcessEngineException;

/**
 * The analytics query waited longer than allowed for its turn: this node already reads as many
 * analytics as {@link ProcessAnalytics#setMaxConcurrent(int)} allows, so that the engine keeps
 * its database connections for its own work. Ask again a little later.
 */
public class AnalyticsBusyException extends ProcessEngineException {

  private static final long serialVersionUID = 1L;

  public AnalyticsBusyException(String message) {
    super(message);
  }
}
