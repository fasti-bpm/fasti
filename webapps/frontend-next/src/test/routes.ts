/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { RouteObject } from 'react-router';
import { beforeAll } from 'vitest';

/**
 * Loads every lazy page of the route tree before the tests of a file run.
 *
 * The pages are split into chunks that the test runner transforms on first import. Under parallel
 * load that first import can take longer than the wait of a `findBy` query, which made full-app
 * tests time out at random. Importing them once up front keeps the waits in the tests about the
 * behaviour under test, not about module loading.
 */
export function preloadRoutes(routes: RouteObject[]) {
  beforeAll(async () => {
    const pending: Promise<unknown>[] = [];
    const visit = (list: RouteObject[]) => {
      for (const route of list) {
        if (typeof route.lazy === 'function') pending.push(route.lazy());
        if (route.children) visit(route.children);
      }
    };
    visit(routes);
    await Promise.all(pending);
  }, 120_000);
}
