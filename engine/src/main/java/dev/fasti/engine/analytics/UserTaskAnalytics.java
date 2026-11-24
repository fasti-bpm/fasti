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
package dev.fasti.engine.analytics;

import java.util.Collections;
import java.util.List;

/**
 * Where the time of user tasks goes: waiting until someone takes the task, then working on it.
 *
 * <p>Waiting runs from the creation of the task to its first assignment, working from then to
 * the completion. A task assigned when it was created waits zero. The first assignment comes from
 * the identity link history, which the engine writes at history level {@code full}; tasks without
 * it count in {@link UserTaskTimes#getUnknownWaiting()}.</p>
 */
public class UserTaskAnalytics {

  /** The times of one group of tasks: one task definition, or one assignee. */
  public static class UserTaskTimes {

    protected final String key;
    protected final String name;
    protected final long count;
    protected final long unknownWaiting;
    protected final DurationStatistics waiting;
    protected final DurationStatistics working;

    public UserTaskTimes(String key, String name, long count, long unknownWaiting, DurationStatistics waiting,
        DurationStatistics working) {
      this.key = key;
      this.name = name;
      this.count = count;
      this.unknownWaiting = unknownWaiting;
      this.waiting = waiting;
      this.working = working;
    }

    /** The task definition key, or the assignee; {@code null} for tasks completed without an assignee. */
    public String getKey() {
      return key;
    }

    /** The name of the task definition (the latest one read); {@code null} for an assignee. */
    public String getName() {
      return name;
    }

    public long getCount() {
      return count;
    }

    public long getUnknownWaiting() {
      return unknownWaiting;
    }

    public DurationStatistics getWaiting() {
      return waiting;
    }

    public DurationStatistics getWorking() {
      return working;
    }
  }

  protected final long tasks;
  protected final boolean sampled;
  protected final List<UserTaskTimes> byTask;
  protected final List<UserTaskTimes> byAssignee;

  public UserTaskAnalytics(long tasks, boolean sampled, List<UserTaskTimes> byTask, List<UserTaskTimes> byAssignee) {
    this.tasks = tasks;
    this.sampled = sampled;
    this.byTask = Collections.unmodifiableList(byTask);
    this.byAssignee = Collections.unmodifiableList(byAssignee);
  }

  /** The completed tasks read. */
  public long getTasks() {
    return tasks;
  }

  /** {@code true} if the range held more completed tasks than were read: the newest ones were read. */
  public boolean isSampled() {
    return sampled;
  }

  public List<UserTaskTimes> getByTask() {
    return byTask;
  }

  public List<UserTaskTimes> getByAssignee() {
    return byAssignee;
  }
}
