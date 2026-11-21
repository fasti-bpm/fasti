/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { refreshAfterReturn } from './App';

describe('back after being away', () => {
  it('reads again only the shown queries that refresh by themselves', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: 0, retry: false } } });
    const live = vi.fn(() => Promise.resolve('live'));
    const paused = vi.fn(() => Promise.resolve('paused'));
    const plain = vi.fn(() => Promise.resolve('plain'));
    const paced = vi.fn(() => Promise.resolve('paced'));
    const observers = [
      new QueryObserver(queryClient, { queryKey: ['live'], queryFn: live, refetchInterval: 300_000 }),
      // the user paused the refresh of this list
      new QueryObserver(queryClient, { queryKey: ['paused'], queryFn: paused, refetchInterval: false }),
      new QueryObserver(queryClient, { queryKey: ['plain'], queryFn: plain }),
      new QueryObserver(queryClient, { queryKey: ['paced'], queryFn: paced, refetchInterval: () => 60_000 }),
    ];
    const stops = observers.map((observer) => observer.subscribe(() => undefined));
    await vi.waitFor(() =>
      expect(queryClient.getQueryCache().getAll().every((query) => query.state.status === 'success')).toBe(true),
    );

    await refreshAfterReturn(queryClient);

    expect(live).toHaveBeenCalledTimes(2);
    expect(paced).toHaveBeenCalledTimes(2);
    expect(paused).toHaveBeenCalledTimes(1);
    expect(plain).toHaveBeenCalledTimes(1);
    stops.forEach((stop) => stop());
    queryClient.clear();
  });
});
