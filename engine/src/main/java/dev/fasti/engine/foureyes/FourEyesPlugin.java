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

import java.util.ArrayList;

import dev.fasti.engine.foureyes.impl.CheckedTasks;
import dev.fasti.engine.foureyes.impl.FourEyesBpmnParseListener;
import dev.fasti.engine.foureyes.impl.FourEyesCommandChecker;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Four-eyes control for approvals: a BPMN user task with the extension property
 * {@value FourEyesService#PROPERTY_NAME} (ids of earlier user tasks of the same process, comma
 * separated) cannot be completed by a person who completed one of those earlier steps in the same
 * process instance. The engine refuses the completion with a {@link FourEyesViolationException}.
 *
 * <p>Who completed a step is recorded in the process instance variable
 * {@value FourEyesService#VARIABLE_PREFIX}{@code <id>} and, where history is kept, also read from the
 * historic task instances and the user operation log, so the control works at every history level
 * and the database schema stays that of Camunda 7.24.</p>
 */
public class FourEyesPlugin implements ProcessEnginePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(FourEyesPlugin.class);

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    if (configuration.getCustomPreBPMNParseListeners() == null) {
      configuration.setCustomPreBPMNParseListeners(new ArrayList<>());
    }
    configuration.getCustomPreBPMNParseListeners().add(new FourEyesBpmnParseListener());
    if (configuration.getCustomSessionFactories() == null) {
      configuration.setCustomSessionFactories(new ArrayList<>());
    }
    configuration.getCustomSessionFactories().add(new CheckedTasks.Factory());
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl configuration) {
    // after the tenant and authorization checks, which init() has added
    configuration.getCommandCheckers().add(FourEyesCommandChecker.create());
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    LOG.info("Four-eyes control is enabled on process engine '{}'", processEngine.getName());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
