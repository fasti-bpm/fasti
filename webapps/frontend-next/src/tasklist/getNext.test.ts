/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/http';
import { candidateQueries, claimOnce, getNextWork, isOverdue, rankCandidates, type Candidate, type ClaimResult } from './getNext';

const task = (id: string, due: string | null, priority = 50, created = '2026-03-01T08:00:00.000+0000'): Candidate => ({
  id,
  name: id,
  due,
  priority,
  created,
});

describe('the order of next work', () => {
  it('puts late tasks first, then the soonest due, then priority, then the oldest', () => {
    const tasks = [
      task('undated-high', null, 90),
      task('due-later', '2026-03-05T08:00:00.000+0000'),
      task('overdue', '2026-02-20T08:00:00.000+0000', 10),
      task('due-soon-low', '2026-03-02T08:00:00.000+0000', 20),
      task('due-soon-high', '2026-03-02T08:00:00.000+0000', 80),
      task('undated-old', null, 50, '2026-01-01T08:00:00.000+0000'),
      task('undated-new', null, 50, '2026-02-01T08:00:00.000+0000'),
    ];
    expect(rankCandidates(tasks).map((candidate) => candidate.id)).toEqual([
      'overdue',
      'due-soon-high',
      'due-soon-low',
      'due-later',
      'undated-high',
      'undated-old',
      'undated-new',
    ]);
  });

  it('lists a task read by both queries once', () => {
    const one = task('a', null);
    expect(rankCandidates([one, one, task('b', null)]).map((candidate) => candidate.id)).toEqual(['a', 'b']);
  });

  it('knows a late task', () => {
    const now = new Date('2026-03-01T12:00:00.000Z');
    expect(isOverdue(task('a', '2026-03-01T08:00:00.000+0000'), now)).toBe(true);
    expect(isOverdue(task('b', '2026-03-02T08:00:00.000+0000'), now)).toBe(false);
    expect(isOverdue(task('c', null), now)).toBe(false);
  });

  it('asks for free tasks the user is a candidate for, sorted on the server the same way', () => {
    const [dated, undated] = candidateQueries('mary', new Date());
    expect(dated?.query).toMatchObject({ candidateUser: 'mary', unassigned: true, active: true });
    expect(dated?.query.dueBefore).toBeTruthy();
    expect(dated?.sorting.map((sorting) => sorting.sortBy)).toEqual(['dueDate', 'priority', 'created']);
    expect(undated?.query).toMatchObject({ candidateUser: 'mary', withoutDueDate: true });
    expect(candidateQueries(null, new Date())[0]?.query).not.toHaveProperty('candidateUser');
  });
});

describe('claiming the next work', () => {
  const conflict = () => new ApiError('server', 500, 'TaskAlreadyClaimedException', 'Task is already claimed');

  it('tells a claim that another user won from one the user may not make', async () => {
    const owner = vi.fn(async () => 'john');
    expect(await claimOnce('t1', 'mary', async () => undefined, owner)).toBe('claimed');
    expect(await claimOnce('t1', 'mary', () => Promise.reject(conflict()), owner)).toBe('taken');
    expect(await claimOnce('t1', 'mary', () => Promise.reject(new ApiError('forbidden', 403)), owner)).toBe('forbidden');
    expect(await claimOnce('t1', 'mary', () => Promise.reject(new ApiError('notFound', 404)), owner)).toBe('taken');
  });

  it('counts a claim as made when the answer was lost but the task is the user’s', async () => {
    expect(await claimOnce('t1', 'mary', () => Promise.reject(new ApiError('network', 0)), async () => 'mary')).toBe('claimed');
  });

  it('moves on from a task whose claim failed while it stays free', async () => {
    const broken = new ApiError('server', 500, 'ProcessEngineException', 'listener failed');
    expect(await claimOnce('t1', 'mary', () => Promise.reject(broken), async () => null)).toBe('failed');
    const read = async () => [task('a', null, 90), task('b', null, 10)];
    expect(await getNextWork(read, async (id) => (id === 'a' ? 'failed' : 'claimed'))).toMatchObject({
      kind: 'claimed',
      task: { id: 'b' },
    });
  });

  it('stops when the server does not answer at all', async () => {
    const down = new ApiError('network', 0);
    await expect(
      claimOnce('t1', 'mary', () => Promise.reject(down), () => Promise.reject(new ApiError('network', 0))),
    ).rejects.toBe(down);
  });

  it('tries the next task when one was taken, and reads the list again when all were', async () => {
    const lists = [
      [task('a', '2026-02-01T08:00:00.000+0000'), task('b', null)],
      [task('a', '2026-02-01T08:00:00.000+0000'), task('b', null), task('c', null, 10)],
    ];
    const read = vi.fn(async () => lists.shift() ?? []);
    const claimed: string[] = [];
    const claim = vi.fn(async (id: string): Promise<ClaimResult> => {
      claimed.push(id);
      return id === 'c' ? 'claimed' : 'taken';
    });
    const outcome = await getNextWork(read, claim);
    expect(outcome).toMatchObject({ kind: 'claimed', task: { id: 'c' }, tried: 3, forbidden: 0 });
    expect(claimed).toEqual(['a', 'b', 'c']);
    expect(read).toHaveBeenCalledTimes(2);
  });

  it('skips tasks the user may not claim and reports them', async () => {
    const read = async () => [task('a', null, 90), task('b', null, 10)];
    const outcome = await getNextWork(read, async (id) => (id === 'a' ? 'forbidden' : 'claimed'));
    expect(outcome).toMatchObject({ kind: 'claimed', task: { id: 'b' }, forbidden: 1 });
  });

  it('gives up after a limited number of claims', async () => {
    let n = 0;
    const read = async () => Array.from({ length: 4 }, () => task(`t${(n += 1)}`, null));
    const claim = vi.fn(async (): Promise<ClaimResult> => 'taken');
    const outcome = await getNextWork(read, claim, { maxTries: 5, maxRounds: 5 });
    expect(outcome).toEqual({ kind: 'missed', tried: 5, forbidden: 0, failed: 0 });
    expect(claim).toHaveBeenCalledTimes(5);
  });

  it('says there is nothing to take, or that every task was forbidden', async () => {
    expect(await getNextWork(async () => [], vi.fn())).toEqual({ kind: 'empty' });
    expect(await getNextWork(async () => [task('a', null)], async () => 'forbidden')).toEqual({
      kind: 'missed',
      tried: 1,
      forbidden: 1,
      failed: 0,
    });
  });
});
