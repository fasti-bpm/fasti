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
package dev.fasti.engine.foureyes.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.fasti.engine.foureyes.FourEyesStatus.Step;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;

/**
 * The four-eyes rule of one user task: the earlier user tasks (steps) of the same process whose
 * completers may not complete it. Kept as property of the activity in the parsed process.
 */
public final class FourEyesRule {

  /** Property of a guarded activity: its rule. */
  static final String PROPERTY_RULE = "fasti.fourEyes.rule";
  /** Property of an activity whose completers are recorded, because a rule names it. */
  static final String PROPERTY_CHECKED = "fasti.fourEyes.checked";

  private final List<Step> steps;
  private final List<String> stepIds;

  FourEyesRule(List<Step> steps) {
    this.steps = List.copyOf(steps);
    List<String> ids = new ArrayList<>();
    for (Step step : steps) {
      ids.add(step.getId());
    }
    this.stepIds = Collections.unmodifiableList(ids);
  }

  public List<Step> getSteps() {
    return steps;
  }

  public List<String> getStepIds() {
    return stepIds;
  }

  /** The rule of the user task of the given task, or {@code null}. */
  public static FourEyesRule of(TaskEntity task) {
    ActivityImpl activity = activityOf(task);
    return activity == null ? null : (FourEyesRule) activity.getProperty(PROPERTY_RULE);
  }

  /** Whether a rule names the user task of the given task as earlier step. */
  static boolean isChecked(TaskEntity task) {
    ActivityImpl activity = activityOf(task);
    return activity != null && Boolean.TRUE.equals(activity.getProperty(PROPERTY_CHECKED));
  }

  static ActivityImpl activityOf(TaskEntity task) {
    if (task.getProcessInstanceId() == null || task.getTaskDefinitionKey() == null) {
      return null;
    }
    ExecutionEntity execution = task.getExecution();
    if (execution == null) {
      return null;
    }
    ActivityImpl activity = execution.getActivity();
    if (activity != null && task.getTaskDefinitionKey().equals(activity.getId())) {
      return activity;
    }
    ProcessDefinitionImpl processDefinition = execution.getProcessDefinition();
    return processDefinition == null ? null : processDefinition.findActivity(task.getTaskDefinitionKey());
  }
}
