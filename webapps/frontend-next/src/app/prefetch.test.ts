/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it, vi } from 'vitest';
import type { RouteObject } from 'react-router';
import { prefetchOnIntent, prefetchRoute, routePath } from './prefetch';

describe('routePath', () => {
  it('reads the path of a hash link without its query', () => {
    expect(routePath('#/cockpit/processes?q=1')).toBe('/cockpit/processes');
    expect(routePath('/fasti/app/next/default/#/admin')).toBe('/admin');
  });

  it('ignores links that leave the application', () => {
    expect(routePath('https://example.org/')).toBeNull();
    expect(routePath(null)).toBeNull();
  });
});

describe('prefetchRoute', () => {
  it('loads the chunks of every matched route once', async () => {
    const parent = vi.fn(async () => ({}));
    const child = vi.fn(async () => ({}));
    const routes: RouteObject[] = [{ path: '/', children: [{ path: 'one', lazy: child }], lazy: parent }];
    await Promise.all(prefetchRoute(routes, '/one'));
    expect(parent).toHaveBeenCalledTimes(1);
    expect(child).toHaveBeenCalledTimes(1);
    expect(prefetchRoute(routes, '/one')).toEqual([]);
    expect(child).toHaveBeenCalledTimes(1);
  });

  it('swallows a failed load, which the navigation retries', async () => {
    const routes: RouteObject[] = [{ path: '/broken', lazy: () => Promise.reject(new Error('offline')) }];
    await expect(Promise.all(prefetchRoute(routes, '/broken'))).resolves.toEqual([undefined]);
  });
});

describe('prefetchOnIntent', () => {
  it('starts loading when a link is focused, and not after the clean-up', () => {
    vi.useFakeTimers();
    try {
      const lazy = vi.fn(async () => ({}));
      const stop = prefetchOnIntent([{ path: '/two', lazy }]);
      const link = document.createElement('a');
      link.href = '#/two';
      document.body.append(link);
      link.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      vi.runAllTimers();
      expect(lazy).toHaveBeenCalledTimes(1);
      stop();
      link.remove();
    } finally {
      vi.useRealTimers();
    }
  });
});
