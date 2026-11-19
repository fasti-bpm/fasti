/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useClampedPage } from './common';

describe('page past the end', () => {
  it('moves to the last page when its rows are gone, and waits for the count', () => {
    const onPage = vi.fn();
    const { rerender } = renderHook(({ count }: { count?: number }) => useClampedPage(1, count, onPage, 20), {
      initialProps: {},
    });
    expect(onPage).not.toHaveBeenCalled();
    rerender({ count: 21 });
    expect(onPage).not.toHaveBeenCalled();
    rerender({ count: 20 });
    expect(onPage).toHaveBeenCalledWith(0);
  });
});
