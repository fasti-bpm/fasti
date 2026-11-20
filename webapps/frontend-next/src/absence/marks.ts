/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { TaskListItem } from '../api/types';

/** The tasks with the absent user each is held for, where there is one. */
export function withSubstitutionMarks(tasks: TaskListItem[], marks: Map<string, string> | undefined): TaskListItem[] {
  if (!marks || marks.size === 0) {
    return tasks;
  }
  return tasks.map((task) => {
    const absentAssignee = marks.get(task.id);
    return absentAssignee ? { ...task, absentAssignee } : task;
  });
}
