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

/**
 * What the four-eyes control says about one task for the authenticated user: whether the task has
 * a rule, which earlier steps it checks, and whether the user or the assignee completed one of
 * them (and so may not complete this task).
 */
public class FourEyesStatus {

  /** An earlier step a task checks: the id of the user task in the model and its name. */
  public static final class Step {
    private final String id;
    private final String name;

    public Step(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public String getId() {
      return id;
    }

    /** The name of the step in the model, or {@code null}. */
    public String getName() {
      return name;
    }
  }

  protected final boolean enabled;
  protected final String taskId;
  protected final List<Step> earlierSteps;
  protected final String userId;
  protected final List<String> userSteps;
  protected final String assignee;
  protected final List<String> assigneeSteps;
  protected final boolean blocked;

  public FourEyesStatus(boolean enabled, String taskId, List<Step> earlierSteps, String userId, List<String> userSteps,
      String assignee, List<String> assigneeSteps, boolean blocked) {
    this.enabled = enabled;
    this.taskId = taskId;
    this.earlierSteps = earlierSteps == null ? Collections.emptyList() : List.copyOf(earlierSteps);
    this.userId = userId;
    this.userSteps = userSteps == null ? Collections.emptyList() : List.copyOf(userSteps);
    this.assignee = assignee;
    this.assigneeSteps = assigneeSteps == null ? Collections.emptyList() : List.copyOf(assigneeSteps);
    this.blocked = blocked;
  }

  /** Whether the four-eyes plugin is registered with the engine. */
  public boolean isEnabled() {
    return enabled;
  }

  public String getTaskId() {
    return taskId;
  }

  /** Whether the task has a four-eyes rule (and the plugin enforces it). */
  public boolean isGuarded() {
    return !earlierSteps.isEmpty();
  }

  /** The earlier steps the task checks, in the order of the model. */
  public List<Step> getEarlierSteps() {
    return earlierSteps;
  }

  /** The authenticated user the status was computed for, or {@code null}. */
  public String getUserId() {
    return userId;
  }

  /** Whether the authenticated user completed one of the earlier steps. */
  public boolean isUserExcluded() {
    return !userSteps.isEmpty();
  }

  /** The ids of the earlier steps the authenticated user completed. */
  public List<String> getUserSteps() {
    return userSteps;
  }

  /** The assignee of the task, or {@code null}. */
  public String getAssignee() {
    return assignee;
  }

  /** Whether the assignee completed one of the earlier steps. */
  public boolean isAssigneeExcluded() {
    return !assigneeSteps.isEmpty();
  }

  /** The ids of the earlier steps the assignee completed. */
  public List<String> getAssigneeSteps() {
    return assigneeSteps;
  }

  /**
   * Whether the engine would refuse to let the authenticated user complete the task now: the
   * user or the assignee completed an earlier step, or neither exists while an earlier step has
   * been completed.
   */
  public boolean isBlocked() {
    return blocked;
  }
}
