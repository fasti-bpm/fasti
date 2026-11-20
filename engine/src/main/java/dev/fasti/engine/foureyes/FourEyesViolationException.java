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

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.AuthorizationException;

/**
 * Thrown when a user task with a four-eyes rule would be completed (or ended with a BPMN error or
 * an escalation) by a person who completed one of the earlier steps the task checks, or by nobody
 * who can be identified. The REST API answers {@code 403} with the type
 * {@code FourEyesViolationException}, like other authorization failures.
 */
public class FourEyesViolationException extends AuthorizationException {

  private static final long serialVersionUID = 1L;

  protected final String taskId;
  protected final String taskDefinitionKey;
  protected final List<String> excludedUserIds;
  protected final List<String> earlierSteps;

  public FourEyesViolationException(String message, String taskId, String taskDefinitionKey,
      List<String> excludedUserIds, List<String> earlierSteps) {
    super(message);
    this.taskId = taskId;
    this.taskDefinitionKey = taskDefinitionKey;
    this.excludedUserIds = excludedUserIds == null ? Collections.emptyList() : List.copyOf(excludedUserIds);
    this.earlierSteps = earlierSteps == null ? Collections.emptyList() : List.copyOf(earlierSteps);
  }

  /** The task that was to be completed. */
  public String getTaskId() {
    return taskId;
  }

  /** The id of the user task in the model. */
  public String getTaskDefinitionKey() {
    return taskDefinitionKey;
  }

  /**
   * The users of this attempt (the authenticated user, the assignee) who completed an earlier
   * checked step; empty when nobody could be identified.
   */
  public List<String> getExcludedUserIds() {
    return excludedUserIds;
  }

  /** The ids of the earlier steps these users completed (or all checked steps, if nobody was identified). */
  public List<String> getEarlierSteps() {
    return earlierSteps;
  }
}
