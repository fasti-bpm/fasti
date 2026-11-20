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
package dev.fasti.engine.absence.impl;

import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;

/**
 * Adds the absence task listener to every BPMN user task, as built-in listener, so that it runs
 * before the listeners of the model and also when custom listeners are skipped.
 */
public class AbsenceBpmnParseListener extends AbstractBpmnParseListener {

  protected final AbsenceSettings settings;
  protected final AbsenceTaskListener listener;

  public AbsenceBpmnParseListener(AbsenceSettings settings) {
    this.settings = settings;
    this.listener = new AbsenceTaskListener(settings);
  }

  public AbsenceSettings getSettings() {
    return settings;
  }

  @Override
  public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
    if (activity.getActivityBehavior() instanceof UserTaskActivityBehavior) {
      register(((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition(), listener);
    }
  }

  static void register(TaskDefinition taskDefinition, TaskListener listener) {
    if (taskDefinition != null) {
      taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_CREATE, listener);
      taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_ASSIGNMENT, listener);
    }
  }
}
