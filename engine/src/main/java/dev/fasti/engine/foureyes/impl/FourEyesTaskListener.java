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

import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;

/**
 * The {@code complete} listeners of the user tasks that take part in a four-eyes rule.
 * <ul>
 * <li>{@link #CHECK}, built-in (before the listeners of the model) on a guarded task: checks it,
 * unless the command checker already allowed it in this command (the usual case:
 * {@code TaskService#complete}, form submit). This catches completions that bypass the task
 * service, such as {@code DelegateTask#complete()} in a listener. The engine wraps exceptions of
 * task listeners, so there the refusal reaches the caller as the cause of a
 * {@code ProcessEngineException}.</li>
 * <li>{@link #RECORD}, after the listeners of the model on a checked step: records the actors of
 * the completion. Being last, it does not record a completion that a listener of the model ends
 * with a BPMN error instead, and it sees an assignee such a listener set.</li>
 * </ul>
 */
public class FourEyesTaskListener implements TaskListener {

  public static final FourEyesTaskListener CHECK = new FourEyesTaskListener(true);
  public static final FourEyesTaskListener RECORD = new FourEyesTaskListener(false);

  protected final boolean check;

  protected FourEyesTaskListener(boolean check) {
    this.check = check;
  }

  @Override
  public void notify(DelegateTask delegateTask) {
    CommandContext commandContext = Context.getCommandContext();
    if (commandContext == null || !(delegateTask instanceof TaskEntity)) {
      return;
    }
    TaskEntity task = (TaskEntity) delegateTask;
    if (check) {
      FourEyesRule rule = FourEyesRule.of(task);
      if (rule != null && !commandContext.getSession(CheckedTasks.class).contains(task.getId())) {
        FourEyesGuard.check(commandContext, task, rule);
      }
    } else if (FourEyesRule.isChecked(task)) {
      FourEyesGuard.record(commandContext, task);
    }
  }
}
