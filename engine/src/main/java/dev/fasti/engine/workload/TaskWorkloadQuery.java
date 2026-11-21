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
package dev.fasti.engine.workload;

import java.util.Collection;
import java.util.Date;

/**
 * Counts the open tasks per candidate group and per assignee with a handful of grouped
 * statements, instead of one task query per group and user.
 *
 * <p>The counts cover exactly the tasks a {@link org.camunda.bpm.engine.task.TaskQuery} of the
 * caller would return: the same authorization and tenant checks apply, so the numbers never
 * reveal a task the caller may not read. Nothing is written.</p>
 */
public interface TaskWorkloadQuery {

  /** At most this many groups and this many users can be asked for at once. */
  int MAX_IDS = 200;

  /** The candidate groups to count for. */
  TaskWorkloadQuery groupIdIn(Collection<String> groupIds);

  /** The assignees to count for. */
  TaskWorkloadQuery userIdIn(Collection<String> userIds);

  /** Tasks due before this moment count as overdue; the current time of the engine by default. */
  TaskWorkloadQuery overdueBefore(Date overdueBefore);

  /** Only tasks of these tenants. */
  TaskWorkloadQuery tenantIdIn(String... tenantIds);

  TaskWorkload read();
}
