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

import java.util.Date;

import dev.fasti.engine.workload.TaskLoad;

/** Fasti: the open tasks of one candidate group, of one assignee, or of everyone. */
public class TaskLoadDto {

  protected String id;
  protected long open;
  protected long unassigned;
  protected long overdue;
  protected Date oldest;

  public static TaskLoadDto fromLoad(TaskLoad load) {
    TaskLoadDto dto = new TaskLoadDto();
    dto.id = load.getId();
    dto.open = load.getOpen();
    dto.unassigned = load.getUnassigned();
    dto.overdue = load.getOverdue();
    dto.oldest = load.getOldest();
    return dto;
  }

  public String getId() {
    return id;
  }

  public long getOpen() {
    return open;
  }

  public long getUnassigned() {
    return unassigned;
  }

  public long getOverdue() {
    return overdue;
  }

  public Date getOldest() {
    return oldest;
  }
}
