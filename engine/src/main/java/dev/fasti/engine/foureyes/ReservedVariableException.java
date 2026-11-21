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

import org.camunda.bpm.engine.AuthorizationException;

/**
 * Thrown when something other than the four-eyes control tries to set or remove a variable in
 * which the control records who completed a step
 * ({@value FourEyesService#VARIABLE_PREFIX}{@code <step>}) in a process instance. Nobody is
 * allowed to, so it is an {@link AuthorizationException}: the REST API answers {@code 403}.
 */
public class ReservedVariableException extends AuthorizationException {

  private static final long serialVersionUID = 1L;

  protected final String variableName;

  public ReservedVariableException(String variableName, String message) {
    super(message);
    this.variableName = variableName;
  }

  /** The name of the variable that was to be changed. */
  public String getVariableName() {
    return variableName;
  }
}
