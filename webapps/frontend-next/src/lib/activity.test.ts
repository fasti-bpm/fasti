/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AWAY_AFTER, IDLE_AFTER, onReturn, paced, usePacedInterval, usePresence } from './activity';

const input = () => window.dispatchEvent(new Event('pointerdown'));

describe('presence of the user', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('stretches the time between refreshes while nobody is at the screen', () => {
    expect(paced(30_000, 'active')).toBe(30_000);
    expect(paced(30_000, 'idle')).toBe(60_000);
    expect(paced(30_000, 'away')).toBe(300_000);
    // a refresh that is switched off stays off
    expect(paced(false, 'away')).toBe(false);
  });

  it('takes a user without input as idle, then as away, and as active again with the next input', () => {
    const { result, unmount } = renderHook(() => ({ presence: usePresence(), interval: usePacedInterval(30_000) }));
    expect(result.current).toEqual({ presence: 'active', interval: 30_000 });

    act(() => void vi.advanceTimersByTime(IDLE_AFTER - 1));
    expect(result.current.presence).toBe('active');
    act(() => void vi.advanceTimersByTime(1));
    expect(result.current).toEqual({ presence: 'idle', interval: 60_000 });

    act(() => void vi.advanceTimersByTime(AWAY_AFTER - IDLE_AFTER));
    expect(result.current).toEqual({ presence: 'away', interval: 300_000 });

    act(() => void input());
    expect(result.current).toEqual({ presence: 'active', interval: 30_000 });
    unmount();
  });

  it('stays active as long as there is input', () => {
    const { result, unmount } = renderHook(() => usePresence());
    for (let i = 0; i < 10; i++) {
      act(() => void vi.advanceTimersByTime(IDLE_AFTER / 2));
      act(() => void input());
    }
    expect(result.current).toBe('active');
    // the pause counts from the last input
    act(() => void vi.advanceTimersByTime(IDLE_AFTER));
    expect(result.current).toBe('idle');
    unmount();
  });

  it('tells when the user is back, and only then', () => {
    const back = vi.fn();
    const stop = onReturn(back);

    act(() => void input());
    expect(back).not.toHaveBeenCalled();
    act(() => void vi.advanceTimersByTime(IDLE_AFTER));
    expect(back).not.toHaveBeenCalled();
    act(() => void window.dispatchEvent(new Event('keydown')));
    expect(back).toHaveBeenCalledTimes(1);
    // more input of an active user is no return
    act(() => void vi.advanceTimersByTime(5_000));
    act(() => void input());
    expect(back).toHaveBeenCalledTimes(1);

    stop();
    act(() => void vi.advanceTimersByTime(AWAY_AFTER));
    act(() => void input());
    expect(back).toHaveBeenCalledTimes(1);
  });

  it('takes a tab that is shown again as input', () => {
    const { result, unmount } = renderHook(() => usePresence());
    act(() => void vi.advanceTimersByTime(AWAY_AFTER));
    expect(result.current).toBe('away');
    act(() => void document.dispatchEvent(new Event('visibilitychange')));
    expect(result.current).toBe('active');
    unmount();
  });

  it('starts anew when it is used again after nobody listened', () => {
    const first = renderHook(() => usePresence());
    act(() => void vi.advanceTimersByTime(AWAY_AFTER));
    expect(first.result.current).toBe('away');
    first.unmount();
    // the listeners are gone with the last subscriber: no timers are left behind
    expect(vi.getTimerCount()).toBe(0);
    const second = renderHook(() => usePresence());
    expect(second.result.current).toBe('active');
    second.unmount();
  });
});
