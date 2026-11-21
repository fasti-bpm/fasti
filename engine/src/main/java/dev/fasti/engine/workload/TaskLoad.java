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

import java.util.Date;

/** The open tasks of one candidate group, of one assignee, or of everyone. */
public interface TaskLoad {

  /** The id of the group or of the user; {@code null} for the totals. */
  String getId();

  /** Open tasks: offered to the group (claimed or not), or assigned to the user. */
  long getOpen();

  /** Those of the open tasks nobody has claimed; always 0 for a user. */
  long getUnassigned();

  /** Those of the open tasks that were due before the moment asked for. */
  long getOverdue();

  /** When the oldest of the open tasks was created; {@code null} without open tasks. */
  Date getOldest();
}
