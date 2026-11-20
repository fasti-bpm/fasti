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

import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_REMOVAL_TIME_STRATEGY_START;

import dev.fasti.engine.absence.AbsenceService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.CommentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Event;

/**
 * Records a substitution on the task: a comment (when history is on) that says who got the task
 * for whom, and the local task variables that mark the task as held by a substitute.
 */
public final class SubstitutionRecorder {

  private SubstitutionRecorder() {
  }

  /** Marks the task as held by {@code substituteId} for {@code absentUserId}. */
  public static void markSubstituted(TaskEntity task, String absentUserId, String substituteId) {
    task.setVariableLocal(AbsenceService.VARIABLE_ABSENT_ASSIGNEE, absentUserId);
    task.setVariableLocal(AbsenceService.VARIABLE_SUBSTITUTE, substituteId);
  }

  /** Removes the marks, if the task has them. */
  public static void clearMarks(TaskEntity task) {
    if (task.hasVariableLocal(AbsenceService.VARIABLE_ABSENT_ASSIGNEE)) {
      task.removeVariableLocal(AbsenceService.VARIABLE_ABSENT_ASSIGNEE);
    }
    if (task.hasVariableLocal(AbsenceService.VARIABLE_SUBSTITUTE)) {
      task.removeVariableLocal(AbsenceService.VARIABLE_SUBSTITUTE);
    }
  }

  /**
   * Adds a comment to the task as the engine (without author), without the authorization check of
   * the comment API and without triggering an update event of the task.
   */
  public static void comment(CommandContext commandContext, TaskEntity task, String message) {
    ProcessEngineConfigurationImpl configuration = commandContext.getProcessEngineConfiguration();
    if (HistoryLevel.HISTORY_LEVEL_NONE.equals(configuration.getHistoryLevel())) {
      return;
    }
    CommentEntity comment = new CommentEntity();
    comment.setType(CommentEntity.TYPE_COMMENT);
    comment.setTime(ClockUtil.getCurrentTime());
    comment.setTaskId(task.getId());
    comment.setProcessInstanceId(task.getProcessInstanceId());
    comment.setAction(Event.ACTION_ADD_COMMENT);
    ExecutionEntity execution = task.getExecution();
    if (execution != null) {
      comment.setRootProcessInstanceId(execution.getRootProcessInstanceId());
      if (HISTORY_REMOVAL_TIME_STRATEGY_START.equals(configuration.getHistoryRemovalTimeStrategy())) {
        HistoricProcessInstanceEventEntity root = commandContext.getDbEntityManager()
            .selectById(HistoricProcessInstanceEventEntity.class, execution.getRootProcessInstanceId());
        if (root != null) {
          comment.setRemovalTime(root.getRemovalTime());
        }
      }
    }
    comment.setMessage(comment.toEventMessage(message));
    comment.setFullMessage(message);
    commandContext.getCommentManager().insert(comment);
  }
}
