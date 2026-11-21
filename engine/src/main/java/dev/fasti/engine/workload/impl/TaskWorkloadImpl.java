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
package dev.fasti.engine.workload.impl;

import java.util.List;

import dev.fasti.engine.workload.TaskLoad;
import dev.fasti.engine.workload.TaskWorkload;

public class TaskWorkloadImpl implements TaskWorkload {

  protected final TaskLoad totals;
  protected final long orphaned;
  protected final List<TaskLoad> groups;
  protected final List<TaskLoad> users;

  public TaskWorkloadImpl(TaskLoad totals, long orphaned, List<TaskLoad> groups, List<TaskLoad> users) {
    this.totals = totals;
    this.orphaned = orphaned;
    this.groups = groups;
    this.users = users;
  }

  @Override
  public TaskLoad getTotals() {
    return totals;
  }

  @Override
  public long getOrphaned() {
    return orphaned;
  }

  @Override
  public List<TaskLoad> getGroups() {
    return groups;
  }

  @Override
  public List<TaskLoad> getUsers() {
    return users;
  }
}
