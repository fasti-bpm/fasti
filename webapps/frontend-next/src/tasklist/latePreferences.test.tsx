/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { connectPreferences, disconnectPreferences, readPreference } from '../lib/storage';
import { useRemembered } from '../ui/hooks';
import { useTasklistPreferences } from './state';

afterEach(() => {
  disconnectPreferences();
  window.localStorage.clear();
  vi.useRealTimers();
});

const saved = { name: 'Mine', criteria: [], onlyMine: true };

function remoteWith(stored: Record<string, unknown>) {
  return { load: vi.fn(async () => stored), save: vi.fn<(document: Record<string, unknown>) => Promise<void>>(async () => undefined) };
}

describe('preferences of the server that arrive after the first look', () => {
  it('does not write the defaults of the Tasklist on the first look', () => {
    renderHook(() => useTasklistPreferences('alice'));

    expect(window.localStorage.getItem('fasti.next.tasklist.alice')).toBeNull();
  });

  it('shows the server copy once it arrives, and builds the next change on it', async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useTasklistPreferences('alice'));
    const remote = remoteWith({ 'tasklist.alice': { savedSearches: [saved], cardVariables: ['amount'] } });

    await act(() => connectPreferences('alice', remote));
    expect(result.current[0].savedSearches).toEqual([saved]);
    act(() => result.current[1]({ onlyMine: true }));
    await act(() => vi.runAllTimersAsync());

    const stored = readPreference<Record<string, unknown>>('tasklist.alice', {});
    expect(stored.savedSearches).toEqual([saved]);
    expect(stored.cardVariables).toEqual(['amount']);
    expect(stored.onlyMine).toBe(true);
    expect(remote.save).toHaveBeenCalledTimes(1);
    expect(remote.save.mock.calls[0]?.[0]).toMatchObject({ 'tasklist.alice': { savedSearches: [saved], onlyMine: true } });
  });

  it('shows saved views of the monitoring pages that arrive late', async () => {
    const { result } = renderHook(() => useRemembered<unknown[]>('views.history', []));
    const view = { name: 'Failing', params: { state: 'failed' } };

    await act(() => connectPreferences('alice', remoteWith({ 'cockpit.views.history': [view] })));

    expect(result.current[0]).toEqual([view]);
  });
});
