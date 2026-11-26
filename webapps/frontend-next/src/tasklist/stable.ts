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
 *
 * A task the user completed (`completed`) is gone for good: it is never held,
 * and the tasks that moved up from the next page to fill its place are no
 * arrivals. They are shown at the end of the list, where the page has them.
 */
export function stabilize<T extends { id: string }>(
  held: readonly T[],
  current: readonly T[],
  pageFull: boolean,
  completed: (taskId: string) => boolean = () => false,
): { tasks: T[]; added: number } {
  const live = current.filter((task) => !completed(task.id));
  const byId = new Map(live.map((task) => [task.id, task]));
  const heldIds = new Set(held.map((task) => task.id));
  const done = held.filter((task) => completed(task.id)).length;
  const newcomers = live.filter((task) => !heldIds.has(task.id));
  // the first tasks of the next page filled the places of the completed ones: they follow the
  // last held task. A page that is not full had no next page to take them from.
  let lastHeld = -1;
  live.forEach((task, index) => {
    if (heldIds.has(task.id)) lastHeld = index;
  });
  const movedUp =
    pageFull && done > 0
      ? live.slice(lastHeld + 1).filter((task) => !heldIds.has(task.id)).slice(0, done)
      : [];
  const added = newcomers.length - movedUp.length;
  const keepMissing = pageFull && added > 0;
  const tasks = held.flatMap((task) => {
    if (completed(task.id)) return [];
    const fresh = byId.get(task.id);
    return fresh ? [fresh] : keepMissing ? [task] : [];
  });
  return { tasks: [...tasks, ...movedUp], added };
}
