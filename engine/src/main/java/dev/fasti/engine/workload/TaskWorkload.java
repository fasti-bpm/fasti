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

import java.util.List;

/** The open tasks the caller may read, in total and for the groups and users asked for. */
public interface TaskWorkload {

  /** Every open task the caller may read. */
  TaskLoad getTotals();

  /** Unclaimed tasks that no group is offered, so that nobody but an administrator finds them. */
  long getOrphaned();

  /** One entry per group asked for, in that order; a group without tasks has zeros. */
  List<TaskLoad> getGroups();

  /** One entry per user asked for, in that order; a user without tasks has zeros. */
  List<TaskLoad> getUsers();
}
