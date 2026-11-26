/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { stabilize } from './stable';

const t = (id: string, name = id) => ({ id, name });

describe('a stable task list', () => {
  it('keeps the order the user saw, drops done tasks and counts new ones', () => {
    const now = [t('new'), t('c', 'C2'), t('a')];
    expect(stabilize([t('a'), t('b'), t('c')], now, false)).toEqual({ tasks: [t('a'), t('c', 'C2')], added: 1 });
    expect(stabilize([], now, false).added).toBe(3);
  });

  it('keeps a task that arrivals may have pushed off a full page', () => {
    expect(stabilize([t('a'), t('b')], [t('new'), t('a')], true)).toEqual({ tasks: [t('a'), t('b')], added: 1 });
  });

  it('drops a completed task and shows the one that moved up from the next page in its place', () => {
    const done = (id: string) => id === 'a';
    // a full page: completing a pulled the first task of the next page in
    expect(stabilize([t('a'), t('b'), t('c')], [t('b'), t('c'), t('d')], true, done)).toEqual({
      tasks: [t('b'), t('c'), t('d')],
      added: 0,
    });
  });

  it('never holds a completed task, also while a list read before still has it', () => {
    const done = (id: string) => id === 'a';
    expect(stabilize([t('a'), t('b')], [t('a'), t('b')], true, done)).toEqual({ tasks: [t('b')], added: 0 });
  });

  it('still counts a task that arrived besides the one that moved up', () => {
    const done = (id: string) => id === 'a';
    // sorted newest first: a new task on top, the first of the next page at the end
    const result = stabilize([t('a'), t('b'), t('c')], [t('new'), t('b'), t('c')], true, done);
    expect(result.added).toBe(1);
    expect(result.tasks.map((task) => task.id)).toEqual(['b', 'c']);
  });

  it('takes nothing from a next page that a page that is not full does not have', () => {
    const done = (id: string) => id === 'a';
    expect(stabilize([t('a'), t('b')], [t('new'), t('b')], false, done)).toEqual({ tasks: [t('b')], added: 1 });
  });
});
