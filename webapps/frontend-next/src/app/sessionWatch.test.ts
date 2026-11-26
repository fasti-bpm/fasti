/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { watchSession } from './session';

const stops: (() => void)[] = [];

afterEach(() => {
  stops.splice(0).forEach((stop) => stop());
  vi.useRealTimers();
});

function watch(current: () => Promise<{ userId: string } | null>) {
  const onEnded = vi.fn();
  const onSwitched = vi.fn();
  const check = vi.fn(current);
  stops.push(watchSession({ userId: 'alice', current: check, onEnded, onSwitched }));
  return { check, onEnded, onSwitched };
}

/** What another tab of the browser causes when it logs in. */
function loginInOtherTab(owner = 'default/bob') {
  window.dispatchEvent(new StorageEvent('storage', { key: 'fasti.next-owner', newValue: owner }));
}

describe('the session shared with other tabs', () => {
  it('notices a login as someone else in another tab', async () => {
    const { check, onSwitched, onEnded } = watch(async () => ({ userId: 'bob' }));

    loginInOtherTab();

    await vi.waitFor(() => expect(onSwitched).toHaveBeenCalledTimes(1));
    expect(check).toHaveBeenCalledTimes(1);
    expect(onEnded).not.toHaveBeenCalled();
  });

  it('ends the session when nobody is logged in any more', async () => {
    const { onEnded, onSwitched } = watch(async () => null);

    loginInOtherTab();

    await vi.waitFor(() => expect(onEnded).toHaveBeenCalledTimes(1));
    expect(onSwitched).not.toHaveBeenCalled();
  });

  it('stays when the same user logged in again, e.g. at another engine', async () => {
    const { check, onEnded, onSwitched } = watch(async () => ({ userId: 'alice' }));

    loginInOtherTab('other/alice');

    await vi.waitFor(() => expect(check).toHaveBeenCalledTimes(1));
    expect(onEnded).not.toHaveBeenCalled();
    expect(onSwitched).not.toHaveBeenCalled();
  });

  it('ignores other keys of the storage', async () => {
    const { check } = watch(async () => ({ userId: 'bob' }));

    window.dispatchEvent(new StorageEvent('storage', { key: 'fasti.next.theme', newValue: '"dark"' }));
    await Promise.resolve();

    expect(check).not.toHaveBeenCalled();
  });

  it('checks again when the window gets the focus, at most every few seconds', async () => {
    vi.useFakeTimers();
    const { check, onSwitched } = watch(async () => ({ userId: 'alice' }));

    window.dispatchEvent(new Event('focus'));
    expect(check).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(11_000);
    window.dispatchEvent(new Event('focus'));
    window.dispatchEvent(new Event('focus'));
    await vi.advanceTimersByTimeAsync(0);

    expect(check).toHaveBeenCalledTimes(1);
    expect(onSwitched).not.toHaveBeenCalled();
  });

  it('keeps the session when the server cannot be asked', async () => {
    const { check, onEnded, onSwitched } = watch(async () => Promise.reject(new Error('down')));

    loginInOtherTab();

    await vi.waitFor(() => expect(check).toHaveBeenCalledTimes(1));
    expect(onEnded).not.toHaveBeenCalled();
    expect(onSwitched).not.toHaveBeenCalled();
  });
});
