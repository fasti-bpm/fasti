/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import type { TaskListItem } from '../api/types';
import { newTasks } from './notifications';

const NOW = Date.parse('2026-11-16T10:00:00.000Z');

function task(id: string, created: string): TaskListItem {
  return { id, created } as unknown as TaskListItem;
}

describe('new task notices', () => {
  it('reports nothing on the first look at a list', () => {
    expect(newTasks(null, [task('a', '2026-11-16T10:00:00.000+0000')])).toEqual([]);
  });

  it('reports tasks created since the last look that were not listed', () => {
    const seen = { ids: new Set(['a']), at: NOW };
    const result = newTasks(seen, [
      task('a', '2026-11-16T09:00:00.000+0000'),
      task('b', '2026-11-16T10:00:30.000+0000'),
      // moved up from the next page after another task was completed
      task('c', '2026-11-15T08:00:00.000+0000'),
    ]);
    expect(result.map((entry) => entry.id)).toEqual(['b']);
  });
});
