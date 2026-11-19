/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { toEngineDate } from '../lib/dates';
import { tenantFilter } from '../lib/userPrefs';
import { request, seg, type QueryValue } from './http';

/**
 * The open tasks of a team, by candidate group and by assignee, read with the
 * public task queries only: `/task/count` and `/task`. The engine counts only
 * the tasks the user may read, so the board shows what the user is allowed to
 * see and nothing more.
 */
const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

/** at most this many groups and users get a row; the others are left out and the board says so */
export const WORKLOAD_LIMIT = 50;

export interface WorkloadRow {
  id: string;
  name: string;
  /** open tasks: of the group (claimed or not), or assigned to the user */
  open: number;
  /** of the group only: not claimed by anyone */
  unassigned: number | null;
  /** due before now */
  overdue: number;
  /** creation time of the oldest open task */
  oldest: string | null;
}

export interface WorkloadSummary {
  open: number;
  unassigned: number;
  overdue: number;
  /** unclaimed tasks that no group may claim, which only an administrator sees */
  orphaned: number;
  /** tasks someone has claimed */
  assigned: number;
}

type Query = Record<string, QueryValue>;

function count(query: Query, signal?: AbortSignal): Promise<number> {
  return request<{ count: number }>(`${engineApi()}/task/count`, { query: { ...tenantFilter(), ...query }, signal }).then(
    (result) => result.count,
  );
}

async function oldest(query: Query, signal?: AbortSignal): Promise<string | null> {
  const tasks = await request<{ created: string | null }[]>(`${engineApi()}/task`, {
    query: { ...tenantFilter(), ...query, sortBy: 'created', sortOrder: 'asc', firstResult: 0, maxResults: 1 },
    signal,
  });
  return tasks[0]?.created ?? null;
}

export async function getWorkloadSummary(now: Date, signal?: AbortSignal): Promise<WorkloadSummary> {
  const [open, unassigned, overdue, orphaned, assigned] = await Promise.all([
    count({}, signal),
    count({ unassigned: true }, signal),
    count({ dueBefore: toEngineDate(now) }, signal),
    count({ unassigned: true, withoutCandidateGroups: true }, signal),
    count({ assigned: true }, signal),
  ]);
  return { open, unassigned, overdue, orphaned, assigned };
}

/** The load of one candidate group: every open task the group may work on, claimed or not. */
export async function getGroupLoad(group: { id: string; name: string | null }, now: Date, signal?: AbortSignal): Promise<WorkloadRow> {
  const base = { candidateGroup: group.id, includeAssignedTasks: true };
  const [open, unassigned, overdue, first] = await Promise.all([
    count(base, signal),
    count({ candidateGroup: group.id, unassigned: true }, signal),
    count({ ...base, dueBefore: toEngineDate(now) }, signal),
    oldest(base, signal),
  ]);
  return { id: group.id, name: group.name || group.id, open, unassigned, overdue, oldest: first };
}

/** The load of one user: the open tasks assigned to the user. */
export async function getUserLoad(user: { id: string; name: string }, now: Date, signal?: AbortSignal): Promise<WorkloadRow> {
  const [open, overdue, first] = await Promise.all([
    count({ assignee: user.id }, signal),
    count({ assignee: user.id, dueBefore: toEngineDate(now) }, signal),
    oldest({ assignee: user.id }, signal),
  ]);
  return { id: user.id, name: user.name, open, unassigned: null, overdue, oldest: first };
}

/** Runs `work` for every item, a few at a time, so a large team does not flood the server. */
export async function mapLimited<T, R>(items: T[], limit: number, work: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array<R>(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await work(items[index] as T);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

export interface WorkloadIdentity {
  id: string;
  name: string | null;
}

/** The groups the user may read, one more than the limit to tell when there are more. */
export interface WorkloadPageOf<T> {
  items: T[];
  /** all identities that match, or null when a search merged several queries */
  count: number | null;
}

const like = (term: string): string => `%${term.replace(/[%_]/g, '')}%`;

/** One page of the groups, or the groups whose name contains the search term. */
export async function getWorkloadGroups(
  { search = '', page = 0 }: { search?: string; page?: number } = {},
  signal?: AbortSignal,
): Promise<WorkloadPageOf<WorkloadIdentity>> {
  const filter: Query = search ? { nameLike: like(search) } : {};
  const [items, total] = await Promise.all([
    request<WorkloadIdentity[]>(`${engineApi()}/group`, {
      query: { ...filter, sortBy: 'name', sortOrder: 'asc', firstResult: page * WORKLOAD_LIMIT, maxResults: WORKLOAD_LIMIT },
      signal,
    }),
    request<{ count: number }>(`${engineApi()}/group/count`, { query: filter, signal }),
  ]);
  return { items, count: total.count };
}

export interface WorkloadUser {
  id: string;
  firstName: string | null;
  lastName: string | null;
}

/**
 * One page of the users. A search matches the id, the first or the last
 * name; the engine has no query for "any of these", so the three are merged
 * and the result is the first page of each.
 */
export async function getWorkloadUsers(
  { search = '', page = 0 }: { search?: string; page?: number } = {},
  signal?: AbortSignal,
): Promise<WorkloadPageOf<WorkloadUser>> {
  const list = (filter: Query, first: number) =>
    request<WorkloadUser[]>(`${engineApi()}/user`, {
      query: { ...filter, sortBy: 'userId', sortOrder: 'asc', firstResult: first, maxResults: WORKLOAD_LIMIT },
      signal,
    });
  if (!search) {
    const [items, total] = await Promise.all([
      list({}, page * WORKLOAD_LIMIT),
      request<{ count: number }>(`${engineApi()}/user/count`, { signal }),
    ]);
    return { items, count: total.count };
  }
  const found = await Promise.all([
    list({ id: search }, 0),
    list({ firstNameLike: like(search) }, 0),
    list({ lastNameLike: like(search) }, 0),
  ]);
  const byId = new Map<string, WorkloadUser>();
  for (const user of found.flat()) byId.set(user.id, user);
  return { items: [...byId.values()], count: null };
}

export function byLoad(a: WorkloadRow, b: WorkloadRow): number {
  return b.overdue - a.overdue || b.open - a.open || a.name.localeCompare(b.name);
}

/**
 * A task filter without criteria of its own, so that the task list behind a
 * link shows exactly what the board counted; shared filters first. `null` if
 * the user has none.
 */
export async function getOpenTaskFilter(signal?: AbortSignal): Promise<string | null> {
  const filters = await request<{ id: string; owner: string | null; query: Record<string, unknown> | null }[]>(
    `${engineApi()}/filter`,
    { query: { resourceType: 'Task' }, signal },
  );
  const empty = (value: unknown) =>
    value === null || value === undefined || value === false || value === '' || (Array.isArray(value) && value.length === 0);
  // the order of a list is no criterion
  const open = filters.filter((filter) =>
    Object.entries(filter.query ?? {}).every(([name, value]) => name === 'sorting' || empty(value)),
  );
  return (open.find((filter) => !filter.owner) ?? open[0])?.id ?? null;
}
