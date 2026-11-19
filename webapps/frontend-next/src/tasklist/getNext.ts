/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { isApiError } from '../api/http';
import type { Task, TaskSorting } from '../api/types';
import { parseEngineDate } from '../lib/dates';
import type { TaskQuery } from '../api/engine';
import { queueQuery } from './queue';

/**
 * "Get next work": the most suitable task the user may take is picked,
 * claimed and opened with one press. Only tasks nobody holds yet and that the
 * user is a candidate for, directly or through a group, are considered.
 */

/** How many tasks of a list are tried before the list is read again. */
export const MAX_TRIES = 5;
/** How often the list is read again when every tried task was taken by someone else. */
export const MAX_ROUNDS = 2;
/** How many tasks each query reads. */
export const BATCH = 25;
/** How many candidates are looked up in a filter at once. */
export const WIDE_BATCH = 200;
/** How many pages of candidates are looked up in a filter at most. */
export const MAX_PAGES = 5;

export type Candidate = Pick<Task, 'id' | 'name' | 'due' | 'priority' | 'created'>;

const time = (value: string | null | undefined): number | null => parseEngineDate(value)?.getTime() ?? null;

/**
 * The order in which tasks are offered: late tasks first, then the soonest
 * due; tasks without a due date after those. Among equals the higher
 * priority, then the task that has waited longest.
 */
export function rankCandidates<T extends Candidate>(tasks: readonly T[]): T[] {
  const keyed = tasks.map((task) => ({ task, due: time(task.due), created: time(task.created) ?? 0 }));
  keyed.sort((a, b) => {
    if (a.due !== b.due) {
      if (a.due === null) return 1;
      if (b.due === null) return -1;
      return a.due - b.due;
    }
    if (a.task.priority !== b.task.priority) return b.task.priority - a.task.priority;
    if (a.created !== b.created) return a.created - b.created;
    return a.task.id.localeCompare(b.task.id);
  });
  // the same task may come from both queries of a list
  const seen = new Set<string>();
  return keyed.map((entry) => entry.task).filter((task) => !seen.has(task.id) && !!seen.add(task.id));
}

/** True if a task is late at `now`. */
export function isOverdue(task: Candidate, now: Date = new Date()): boolean {
  const due = time(task.due);
  return due !== null && due < now.getTime();
}

/**
 * The criteria of the two queries that read the candidates: one for the
 * tasks with a due date, sorted by it, one for those without. Both are
 * sorted on the server so that the first page holds the best tasks.
 */
export function candidateQueries(
  userId: string | null,
  now: Date,
): { query: TaskQuery; sorting: TaskSorting[] }[] {
  const base: TaskQuery = {
    ...queueQuery('unclaimed', now),
    active: true,
    ...(userId ? { candidateUser: userId } : {}),
  };
  const byPriority: TaskSorting[] = [
    { sortBy: 'priority', sortOrder: 'desc' },
    { sortBy: 'created', sortOrder: 'asc' },
  ];
  return [
    {
      // every dated task falls due before this
      query: { ...base, dueBefore: '9999-12-31T23:59:59.000+0000' },
      sorting: [{ sortBy: 'dueDate', sortOrder: 'asc' }, ...byPriority],
    },
    { query: { ...base, withoutDueDate: true }, sorting: byPriority },
  ];
}

/**
 * How a claim ended: the task is now the user's, another user was faster,
 * the user may not claim it, or it failed for a reason of the task itself.
 */
export type ClaimResult = 'claimed' | 'taken' | 'forbidden' | 'failed';

/**
 * Claims a task and tells how it ended. A failure the server does not
 * explain (a conflict reported as a server error, a lost answer) is checked
 * by reading the task: the claim may have gone through after all.
 */
export async function claimOnce(
  taskId: string,
  userId: string,
  claim: (taskId: string) => Promise<unknown>,
  assigneeOf: (taskId: string) => Promise<string | null>,
): Promise<ClaimResult> {
  try {
    await claim(taskId);
    return 'claimed';
  } catch (error) {
    if (isApiError(error) && error.status === 403) return 'forbidden';
    // gone (completed or deleted) since the list was read
    if (isApiError(error) && error.status === 404) return 'taken';
    if (isApiError(error) && error.status === 401) throw error;
    let assignee: string | null;
    try {
      assignee = await assigneeOf(taskId);
    } catch (readError) {
      if (isApiError(readError) && readError.status === 404) return 'taken';
      // neither the claim nor the task can be read: the server is not answering
      throw error;
    }
    if (assignee === userId) return 'claimed';
    if (assignee) return 'taken';
    // still free, so the claim failed for a reason of this task; the next one may work
    return 'failed';
  }
}

export type NextWorkOutcome<T extends Candidate> =
  /** the task is now the user's */
  | { kind: 'claimed'; task: T; tried: number; forbidden: number }
  /** nothing to take */
  | { kind: 'empty' }
  /** every tried task was taken by others before the user, or may not be claimed */
  | { kind: 'missed'; tried: number; forbidden: number; failed: number };

/**
 * Reads the candidates and claims the best one. When a task was taken by
 * someone else in the meantime the next one is tried; when the tasks of a
 * list are used up the list is read again, without those that failed. At
 * most `maxTries` claims are made in all, so a busy queue cannot keep the
 * user waiting.
 */
export async function getNextWork<T extends Candidate>(
  read: () => Promise<T[]>,
  claim: (taskId: string) => Promise<ClaimResult>,
  options: { maxTries?: number; maxRounds?: number } = {},
): Promise<NextWorkOutcome<T>> {
  const maxTries = options.maxTries ?? MAX_TRIES;
  const maxRounds = options.maxRounds ?? MAX_ROUNDS;
  const failed = new Set<string>();
  let forbidden = 0;
  let broken = 0;
  let tried = 0;
  for (let round = 0; round <= maxRounds && tried < maxTries; round += 1) {
    const candidates = rankCandidates(await read()).filter((task) => !failed.has(task.id));
    if (candidates.length === 0) break;
    for (const candidate of candidates) {
      if (tried >= maxTries) break;
      tried += 1;
      const result = await claim(candidate.id);
      if (result === 'claimed') return { kind: 'claimed', task: candidate, tried, forbidden };
      failed.add(candidate.id);
      if (result === 'forbidden') forbidden += 1;
      if (result === 'failed') broken += 1;
    }
  }
  return tried === 0 ? { kind: 'empty' } : { kind: 'missed', tried, forbidden, failed: broken };
}
