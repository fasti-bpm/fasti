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
package org.camunda.bpm.engine.rest.dto.task;

import java.util.ArrayList;
import java.util.List;

import dev.fasti.engine.foureyes.FourEyesStatus;

/** The four-eyes status of a task for the user of the request ({@code GET /task/{id}/four-eyes}). */
public class FourEyesStatusDto {

  /** An earlier step the task checks. */
  public static class StepDto {
    protected String id;
    protected String name;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  protected String taskId;
  protected boolean enabled;
  protected boolean guarded;
  protected List<StepDto> earlierSteps = new ArrayList<>();
  protected String userId;
  protected boolean userExcluded;
  protected List<String> userSteps = new ArrayList<>();
  protected String assignee;
  protected boolean assigneeExcluded;
  protected List<String> assigneeSteps = new ArrayList<>();
  protected boolean blocked;

  public static FourEyesStatusDto fromStatus(FourEyesStatus status) {
    FourEyesStatusDto dto = new FourEyesStatusDto();
    dto.taskId = status.getTaskId();
    dto.enabled = status.isEnabled();
    dto.guarded = status.isGuarded();
    for (FourEyesStatus.Step step : status.getEarlierSteps()) {
      StepDto stepDto = new StepDto();
      stepDto.id = step.getId();
      stepDto.name = step.getName();
      dto.earlierSteps.add(stepDto);
    }
    dto.userId = status.getUserId();
    dto.userExcluded = status.isUserExcluded();
    dto.userSteps = new ArrayList<>(status.getUserSteps());
    dto.assignee = status.getAssignee();
    dto.assigneeExcluded = status.isAssigneeExcluded();
    dto.assigneeSteps = new ArrayList<>(status.getAssigneeSteps());
    dto.blocked = status.isBlocked();
    return dto;
  }

  public String getTaskId() {
    return taskId;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isGuarded() {
    return guarded;
  }

  public List<StepDto> getEarlierSteps() {
    return earlierSteps;
  }

  public String getUserId() {
    return userId;
  }

  public boolean isUserExcluded() {
    return userExcluded;
  }

  public List<String> getUserSteps() {
    return userSteps;
  }

  public String getAssignee() {
    return assignee;
  }

  public boolean isAssigneeExcluded() {
    return assigneeExcluded;
  }

  public List<String> getAssigneeSteps() {
    return assigneeSteps;
  }

  public boolean isBlocked() {
    return blocked;
  }
}
