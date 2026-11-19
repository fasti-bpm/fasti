/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { TaskQuery } from '../api/engine';
import type { TaskSorting } from '../api/types';
import { toEngineDate } from '../lib/dates';

/**
 * Quick views of a task list by urgency: what is late, what falls due
 * within some hours, and the unclaimed tasks that have waited longest.
 */
export type QueueView = 'all' | 'overdue' | 'due4' | 'due8' | 'due24' | 'unclaimed';

export const QUEUE_VIEWS: QueueView[] = ['all', 'overdue', 'due4', 'due8', 'due24', 'unclaimed'];

const HOUR = 60 * 60 * 1000;

export const isQueueView = (value: unknown): value is QueueView => QUEUE_VIEWS.includes(value as QueueView);

function hoursOf(view: QueueView): number | null {
  const match = /^due(\d+)$/.exec(view);
  return match ? Number(match[1]) : null;
}

/** The criteria a view adds to the query of the filter, relative to `now`. */
export function queueQuery(view: QueueView, now: Date): TaskQuery {
  const hours = hoursOf(view);
  if (hours !== null) {
    return { dueAfter: toEngineDate(now), dueBefore: toEngineDate(new Date(now.getTime() + hours * HOUR)) };
  }
  switch (view) {
    case 'overdue':
      return { dueBefore: toEngineDate(now) };
    case 'unclaimed':
      return { unassigned: true };
    default:
      return {};
  }
}

/** The order a view suggests: soonest due first, or the longest waiting first. */
export function queueSorting(view: QueueView): TaskSorting | null {
  if (view === 'unclaimed') return { sortBy: 'created', sortOrder: 'asc' };
  if (view === 'overdue' || hoursOf(view) !== null) return { sortBy: 'dueDate', sortOrder: 'asc' };
  return null;
}

/** `now` to the minute, so that the query and its cache key change once a minute and not on every render. */
export function minuteOf(date: Date): Date {
  return new Date(Math.floor(date.getTime() / 60_000) * 60_000);
}

/** How much of the time between creation and due date is used up, 0 to 1; `null` without a due date. */
export function slaUsed(created: Date | null, due: Date | null, now: Date = new Date()): number | null {
  if (!due) return null;
  if (!created || due.getTime() <= created.getTime()) return now.getTime() >= due.getTime() ? 1 : 0;
  const used = (now.getTime() - created.getTime()) / (due.getTime() - created.getTime());
  return Math.min(1, Math.max(0, used));
}
