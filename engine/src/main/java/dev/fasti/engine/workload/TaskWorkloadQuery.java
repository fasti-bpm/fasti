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

  /** At most this many groups, this many users and this many tenants can be asked for at once. */
  int MAX_IDS = 200;

  /** No id may be longer than this; the id columns of the engine are no longer. */
  int MAX_ID_LENGTH = 255;

  /**
   * The candidate groups to count for. Repeated ids are asked for once; an empty, {@code null}
   * or too long id, or more than {@link #MAX_IDS} distinct ids, is refused with a
   * {@link org.camunda.bpm.engine.BadUserRequestException}.
   *
   * <p>An id is compared with the stored ones as the database compares them. On a database
   * with a case- or accent-insensitive collation (the defaults of SQL Server, MySQL and
   * MariaDB) {@code sales} therefore also counts the tasks offered to {@code Sales}, exactly as
   * a task query for {@code sales} does, and the entry carries the id that was asked for.</p>
   */
  TaskWorkloadQuery groupIdIn(Collection<String> groupIds);

  /** The assignees to count for; the rules of {@link #groupIdIn(Collection)} apply. */
  TaskWorkloadQuery userIdIn(Collection<String> userIds);

  /** Tasks due before this moment count as overdue; the current time of the engine by default. */
  TaskWorkloadQuery overdueBefore(Date overdueBefore);

  /**
   * Only tasks of these tenants; none means no restriction. An empty, {@code null} or too long
   * id, or more than {@link #MAX_IDS} ids, is refused.
   */
  TaskWorkloadQuery tenantIdIn(String... tenantIds);

  /**
   * Reads the workload.
   *
   * @throws WorkloadNotAvailableException if the mapping file of the workload statements is not
   *         part of the MyBatis mappings of the engine
   */
  TaskWorkload read();
}
