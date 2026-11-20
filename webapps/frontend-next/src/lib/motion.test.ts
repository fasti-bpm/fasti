/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { scrollBehavior } from './motion';

function prefersReducedMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: reduce && query.includes('reduce'), media: query }));
}

describe('scroll behaviour', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('scrolls smoothly by default', () => {
    prefersReducedMotion(false);
    expect(scrollBehavior()).toBe('smooth');
  });

  it('jumps when the user asked for less motion', () => {
    prefersReducedMotion(true);
    expect(scrollBehavior()).toBe('auto');
  });
});
