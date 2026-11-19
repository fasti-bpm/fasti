/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { matchRoutes } from 'react-router';
import type { RouteObject } from 'react-router';

/*
 * Pages are separate chunks that load when the user navigates to them. When
 * the pointer rests on a link, or a link gets the keyboard focus, the chunks of
 * its page start loading, so the page usually opens without waiting.
 */
const started = new Set<string>();

/** the path inside the hash router that a link leads to, or null for other links */
export function routePath(href: string | null): string | null {
  if (!href) return null;
  const hash = href.indexOf('#/');
  if (hash < 0) return null;
  return href.slice(hash + 1).split('?')[0] ?? null;
}

/** starts loading the chunks of the page at the path; returns what it started */
export function prefetchRoute(routes: RouteObject[], path: string): Promise<unknown>[] {
  if (started.has(path)) return [];
  started.add(path);
  const matches = matchRoutes(routes, path) ?? [];
  return matches.flatMap(({ route }) => {
    if (typeof route.lazy !== 'function') return [];
    // a failed load is retried by the navigation itself
    return [Promise.resolve(route.lazy()).catch(() => undefined)];
  });
}

/** listens for intent on links in the document; returns the clean-up */
export function prefetchOnIntent(routes: RouteObject[]): () => void {
  let timer = 0;
  const onIntent = (event: Event) => {
    const link = (event.target as Element | null)?.closest?.('a[href]');
    const path = routePath(link?.getAttribute('href') ?? null);
    if (!path) return;
    window.clearTimeout(timer);
    // a pointer that only passes over a link does not load anything
    timer = window.setTimeout(() => prefetchRoute(routes, path), event.type === 'focusin' ? 0 : 80);
  };
  const onLeave = () => window.clearTimeout(timer);
  document.addEventListener('pointerover', onIntent);
  document.addEventListener('focusin', onIntent);
  document.addEventListener('pointerout', onLeave);
  return () => {
    window.clearTimeout(timer);
    document.removeEventListener('pointerover', onIntent);
    document.removeEventListener('focusin', onIntent);
    document.removeEventListener('pointerout', onLeave);
  };
}
