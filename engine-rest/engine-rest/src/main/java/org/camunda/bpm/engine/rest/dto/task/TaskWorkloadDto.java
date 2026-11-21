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

import dev.fasti.engine.workload.TaskLoad;
import dev.fasti.engine.workload.TaskWorkload;

/** Fasti: the open tasks the user may read, in total and per group and user asked for. */
public class TaskWorkloadDto {

  protected TaskLoadDto totals;
  protected long orphaned;
  protected List<TaskLoadDto> groups = new ArrayList<>();
  protected List<TaskLoadDto> users = new ArrayList<>();

  public static TaskWorkloadDto fromWorkload(TaskWorkload workload) {
    TaskWorkloadDto dto = new TaskWorkloadDto();
    dto.totals = TaskLoadDto.fromLoad(workload.getTotals());
    dto.orphaned = workload.getOrphaned();
    for (TaskLoad load : workload.getGroups()) {
      dto.groups.add(TaskLoadDto.fromLoad(load));
    }
    for (TaskLoad load : workload.getUsers()) {
      dto.users.add(TaskLoadDto.fromLoad(load));
    }
    return dto;
  }

  public TaskLoadDto getTotals() {
    return totals;
  }

  public long getOrphaned() {
    return orphaned;
  }

  public List<TaskLoadDto> getGroups() {
    return groups;
  }

  public List<TaskLoadDto> getUsers() {
    return users;
  }
}
