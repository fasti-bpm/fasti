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
package dev.fasti.engine.workload;

import dev.fasti.engine.workload.impl.TaskWorkloadQueryImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/** Entry point to the task workload of a process engine. */
public final class TaskWorkloads {

  private TaskWorkloads() {
  }

  public static TaskWorkloadQuery query(ProcessEngine processEngine) {
    return new TaskWorkloadQueryImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
