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

import java.util.Date;

import dev.fasti.engine.workload.TaskLoad;

/** One row of a grouped workload statement; sums over no rows arrive as {@code null}. */
public class TaskLoadEntity implements TaskLoad {

  protected String id;
  protected Long open;
  protected Long unassigned;
  protected Long overdue;
  protected Date oldest;

  public TaskLoadEntity() {
  }

  public TaskLoadEntity(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public long getOpen() {
    return open == null ? 0 : open;
  }

  public void setOpen(Long open) {
    this.open = open;
  }

  @Override
  public long getUnassigned() {
    return unassigned == null ? 0 : unassigned;
  }

  public void setUnassigned(Long unassigned) {
    this.unassigned = unassigned;
  }

  @Override
  public long getOverdue() {
    return overdue == null ? 0 : overdue;
  }

  public void setOverdue(Long overdue) {
    this.overdue = overdue;
  }

  @Override
  public Date getOldest() {
    return oldest;
  }

  public void setOldest(Date oldest) {
    this.oldest = oldest;
  }

  /** A copy of this row under another id. */
  public TaskLoadEntity withId(String id) {
    TaskLoadEntity copy = new TaskLoadEntity(id);
    copy.open = open;
    copy.unassigned = unassigned;
    copy.overdue = overdue;
    copy.oldest = oldest;
    return copy;
  }

  /** The counts of two rows together, and the older of their oldest tasks. */
  public static TaskLoadEntity sum(TaskLoadEntity a, TaskLoadEntity b) {
    TaskLoadEntity sum = new TaskLoadEntity(a.id);
    sum.open = a.getOpen() + b.getOpen();
    sum.unassigned = a.getUnassigned() + b.getUnassigned();
    sum.overdue = a.getOverdue() + b.getOverdue();
    sum.oldest = a.oldest == null ? b.oldest : b.oldest == null || a.oldest.before(b.oldest) ? a.oldest : b.oldest;
    return sum;
  }
}
