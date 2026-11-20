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

/**
 * The four-eyes control of a process engine. Obtain it with {@link FourEyes#of}.
 */
public interface FourEyesService {

  /**
   * Name of the extension property ({@code camunda:property}) on a BPMN user task that lists, comma
   * separated, the ids of the user tasks of the same process whose completers may not complete
   * this task.
   */
  String PROPERTY_NAME = "fasti:fourEyes";

  /**
   * Prefix of the process instance variables in which the plugin records who completed a checked
   * step: {@code fasti_completedBy_<id of the user task>}, a JSON array of user ids.
   */
  String VARIABLE_PREFIX = "fasti_completedBy_";

  /** Whether the plugin {@link FourEyesPlugin} is registered with the engine. */
  boolean isEnabled();

  /**
   * The four-eyes status of the task for the authenticated user. Needs the permission to read the
   * task.
   *
   * @throws org.camunda.bpm.engine.exception.NotFoundException if the task does not exist
   * @throws org.camunda.bpm.engine.AuthorizationException if the user may not read the task
   */
  FourEyesStatus getStatus(String taskId);
}
