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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import dev.fasti.engine.absence.AbsenceService;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.IdentityLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in listener of user tasks.
 * <ul>
 * <li>{@code assignment} (also right after {@code create}, when the task is created with an
 * assignee): if the assignee is absent, the task goes to the present substitute, unless the absent
 * user makes the assignment themselves (they are evidently there). If the task was held by a
 * substitute and now goes to someone else, the substitution marks are removed.</li>
 * <li>{@code create}: the present substitute of each absent candidate user becomes a candidate
 * user as well (the absent user stays one).</li>
 * </ul>
 */
public class AbsenceTaskListener implements TaskListener {

  private static final Logger LOG = LoggerFactory.getLogger(AbsenceTaskListener.class);

  protected final AbsenceSettings settings;

  public AbsenceTaskListener(AbsenceSettings settings) {
    this.settings = settings;
  }

  @Override
  public void notify(DelegateTask delegateTask) {
    CommandContext commandContext = Context.getCommandContext();
    if (!settings.isAvailable() || commandContext == null || !(delegateTask instanceof TaskEntity)) {
      return;
    }
    TaskEntity task = (TaskEntity) delegateTask;
    String event = delegateTask.getEventName();
    if (EVENTNAME_ASSIGNMENT.equals(event)) {
      substituteAssignee(commandContext, task);
    } else if (EVENTNAME_CREATE.equals(event) && settings.isSubstituteCandidates()) {
      substituteCandidates(commandContext, task);
    }
  }

  protected void substituteAssignee(CommandContext commandContext, TaskEntity task) {
    String assignee = task.getAssignee();
    Object heldBy = task.getVariableLocal(AbsenceService.VARIABLE_SUBSTITUTE);
    if (heldBy != null) {
      if (heldBy.equals(assignee)) {
        return;
      }
      // the task moved on from the substitute (back to the absent user, or to someone else)
      SubstitutionRecorder.clearMarks(task);
    }
    if (assignee == null || assignee.equals(commandContext.getAuthenticatedUserId())) {
      return;
    }
    Date now = ClockUtil.getCurrentTime();
    String substitute = AbsenceResolver.resolve(commandContext, settings.getMaxHops(), assignee, now);
    if (substitute == null) {
      return;
    }
    LOG.debug("Task {}: assignee {} is absent, assigning substitute {}", task.getId(), assignee, substitute);
    SubstitutionRecorder.markSubstituted(task, assignee, substitute);
    SubstitutionRecorder.comment(commandContext, task,
        "Assigned to " + substitute + " as substitute for " + assignee + ", who is absent.");
    task.setAssignee(substitute);
  }

  protected void substituteCandidates(CommandContext commandContext, TaskEntity task) {
    Set<String> candidates = new LinkedHashSet<>();
    for (IdentityLink link : task.getCandidates()) {
      if (link.getUserId() != null) {
        candidates.add(link.getUserId());
      }
    }
    if (candidates.isEmpty()) {
      return;
    }
    Date now = ClockUtil.getCurrentTime();
    if (candidates.size() > 1) {
      // one statement instead of one per candidate
      AbsenceStore.findAll(commandContext);
    }
    for (String candidate : new ArrayList<>(candidates)) {
      String substitute = AbsenceResolver.resolve(commandContext, settings.getMaxHops(), candidate, now);
      if (substitute != null && candidates.add(substitute)) {
        LOG.debug("Task {}: candidate user {} is absent, adding substitute {}", task.getId(), candidate, substitute);
        task.addCandidateUser(substitute);
        SubstitutionRecorder.comment(commandContext, task,
            "Candidate user " + substitute + " added as substitute for " + candidate + ", who is absent.");
      }
    }
  }
}
