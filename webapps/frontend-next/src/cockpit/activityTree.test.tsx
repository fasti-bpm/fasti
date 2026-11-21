/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { renderHook } from '@testing-library/react';
import { replaceEqualDeep } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ActivityInstance } from '../api/cockpit';
import { toTree, treeInterval, treeSize, useTreeInterval } from './activityTree';

const leaf = (id: string, incidents = 0): ActivityInstance => ({
  id,
  activityId: id.split(':')[0] ?? id,
  activityName: null,
  activityType: 'userTask',
  incidents: Array.from({ length: incidents }, (_, i) => ({ id: `${id}-incident-${i}`, activityId: id })),
  childActivityInstances: [],
  childTransitionInstances: [],
});

const answer = (incidentsOfB = 0): ActivityInstance => ({
  id: 'root',
  activityId: 'process',
  activityName: 'Process',
  activityType: 'processDefinition',
  childActivityInstances: [
    { ...leaf('sub:1'), activityType: 'subProcess', childActivityInstances: [leaf('a:1'), leaf('a:2')] },
    leaf('b:1', incidentsOfB),
  ],
  childTransitionInstances: [leaf('c:1')],
});

describe('activity instance tree', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('names the nodes and marks tokens in transition', () => {
    const tree = toTree(answer(2), new Map([['a', 'Approve'], ['b', 'Book']]));
    expect(tree.name).toBe('Process');
    expect(tree.children.map((child) => [child.name, child.transition, child.incidents])).toEqual([
      ['sub', false, 0],
      ['Book', false, 2],
      ['c', true, 0],
    ]);
    expect(tree.children[0]?.children.map((child) => child.name)).toEqual(['Approve', 'Approve']);
  });

  it('keeps the tree of an answer that did not change', () => {
    const names = new Map<string, string>();
    // what the query does with a new answer: parts equal to the last answer are kept
    const first = answer();
    const second = replaceEqualDeep(first, answer());
    expect(second).toBe(first);
    expect(toTree(second, names)).toBe(toTree(first, names));
  });

  it('builds only the part that changed anew', () => {
    const names = new Map<string, string>();
    const first = answer();
    const before = toTree(first, names);
    const second = replaceEqualDeep(first, answer(1));
    const after = toTree(second, names);

    expect(after).not.toBe(before);
    // the sub-process and the transition did not change: the same nodes, not rendered again
    expect(after.children[0]).toBe(before.children[0]);
    expect(after.children[2]).toBe(before.children[2]);
    expect(after.children[1]).not.toBe(before.children[1]);
    expect(after.children[1]?.incidents).toBe(1);
  });

  it('builds the tree anew for other names', () => {
    const first = answer();
    const before = toTree(first, new Map());
    const after = toTree(first, new Map([['b', 'Book']]));
    expect(after).not.toBe(before);
    expect(after.children[1]?.name).toBe('Book');
  });

  it('counts the instances of a tree', () => {
    expect(treeSize(undefined)).toBe(0);
    expect(treeSize(answer())).toBe(6);
  });

  it('reads a large or a quiet tree less often', () => {
    expect(treeInterval(30_000, 6, 0)).toBe(30_000);
    expect(treeInterval(30_000, 101, 0)).toBe(60_000);
    expect(treeInterval(30_000, 501, 0)).toBe(120_000);
    expect(treeInterval(30_000, 6, 2 * 60_000)).toBe(60_000);
    expect(treeInterval(30_000, 6, 10 * 60_000)).toBe(120_000);
    // the larger of the two, not their product
    expect(treeInterval(30_000, 501, 10 * 60_000)).toBe(120_000);
    // never faster than the page refreshes, and off stays off
    expect(treeInterval(300_000, 501, 10 * 60_000)).toBe(300_000);
    expect(treeInterval(false, 501, 0)).toBe(false);
  });

  it('returns to the usual interval when the tree changes', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useTreeInterval(30_000));
    const first = answer();
    expect(result.current(first)).toBe(30_000);
    vi.advanceTimersByTime(2 * 60_000);
    // the same answer again: the instance stands still
    expect(result.current(first)).toBe(60_000);
    vi.advanceTimersByTime(8 * 60_000);
    expect(result.current(first)).toBe(120_000);
    expect(result.current(replaceEqualDeep(first, answer(1)))).toBe(30_000);
  });
});
