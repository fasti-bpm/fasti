/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * The tasks of a list in the order the user saw them: tasks that arrived wait
 * until the user asks for them, the others show their current state. A held
 * task that is no longer on the page is dropped, unless arrivals may have
 * pushed it off a full page; then it stays until the user lets the new ones in.
 * Keeps the list calm while the user works through it and it refreshes.
 */
export function stabilize<T extends { id: string }>(
  held: readonly T[],
  current: readonly T[],
  pageFull: boolean,
): { tasks: T[]; added: number } {
  const byId = new Map(current.map((task) => [task.id, task]));
  const heldIds = new Set(held.map((task) => task.id));
  const added = current.filter((task) => !heldIds.has(task.id)).length;
  const keepMissing = pageFull && added > 0;
  const tasks = held.flatMap((task) => {
    const fresh = byId.get(task.id);
    return fresh ? [fresh] : keepMissing ? [task] : [];
  });
  return { tasks, added };
}
