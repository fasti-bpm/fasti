/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { settleLimited } from './limit';

describe('settleLimited', () => {
  it('never runs more calls at once than the limit and keeps the order of results', async () => {
    let running = 0;
    let most = 0;
    const results = await settleLimited(
      [1, 2, 3, 4, 5, 6, 7],
      async (n) => {
        running++;
        most = Math.max(most, running);
        await new Promise((resolve) => setTimeout(resolve, 5));
        running--;
        if (n === 3) throw new Error('three');
        return n * 2;
      },
      3,
    );
    expect(most).toBe(3);
    expect(results.map((r) => (r.status === 'fulfilled' ? r.value : 'x'))).toEqual([2, 4, 'x', 8, 10, 12, 14]);
  });

  it('settles at once for no items', async () => {
    expect(await settleLimited([], async () => 1)).toEqual([]);
  });
});
