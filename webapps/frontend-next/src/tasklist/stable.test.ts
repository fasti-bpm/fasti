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
});
