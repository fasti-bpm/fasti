/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  connectPreferences,
  disconnectPreferences,
  flushPreferences,
  onPreferencesReplaced,
  readPreference,
  writePreference,
} from './storage';

afterEach(() => {
  disconnectPreferences();
  window.localStorage.clear();
  vi.useRealTimers();
});

function remoteWith(stored: Record<string, unknown>) {
  return { load: vi.fn(async () => stored), save: vi.fn(async () => undefined) };
}

describe('preferences on the server', () => {
  it('sends the preferences of this browser when the server has none yet', async () => {
    writePreference('theme', 'dark');
    writePreference('tasklist.alice', { savedSearches: [1] });
    writePreference('tasklist.bob', { savedSearches: [2] });
    const remote = remoteWith({});

    await connectPreferences('alice', remote);

    expect(remote.save).toHaveBeenCalledWith({ theme: 'dark', 'tasklist.alice': { savedSearches: [1] } });
  });

  it('replaces the local preferences with the server copy and tells listeners', async () => {
    writePreference('theme', 'dark');
    writePreference('density', 'compact');
    writePreference('tasklist.bob', { onlyMine: true });
    const listener = vi.fn();
    const off = onPreferencesReplaced(listener);

    await connectPreferences('alice', remoteWith({ theme: 'light', locale: 'vi' }));
    off();

    expect(readPreference('theme', '')).toBe('light');
    expect(readPreference('locale', '')).toBe('vi');
    expect(readPreference('density', 'none')).toBe('none');
    expect(readPreference('tasklist.bob', null)).toEqual({ onlyMine: true });
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('does not hand the preferences of the previous user of a shared browser to a new user', async () => {
    await connectPreferences('alice', remoteWith({ theme: 'dark', tenant: 'acme' }));
    disconnectPreferences();
    const remote = remoteWith({});

    await connectPreferences('bob', remote);

    expect(remote.save).not.toHaveBeenCalled();
    expect(readPreference('theme', 'system')).toBe('system');
    expect(readPreference('tenant', null)).toBeNull();
  });

  it('stops sending once another tab logged in as someone else', async () => {
    const alice = remoteWith({ theme: 'dark' });
    await connectPreferences('alice', alice);
    window.localStorage.setItem('fasti.next-owner', '/bob');

    writePreference('locale', 'vi');
    await flushPreferences();

    expect(alice.save).not.toHaveBeenCalled();
  });

  it('ignores a late answer after the user changed', async () => {
    let answer: (value: Record<string, unknown>) => void = () => undefined;
    const slow = { load: vi.fn(() => new Promise<Record<string, unknown>>((resolve) => (answer = resolve))), save: vi.fn() };
    const pending = connectPreferences('alice', slow);
    const bob = remoteWith({ theme: 'light' });
    await connectPreferences('bob', bob);

    answer({ theme: 'dark' });
    await pending;
    writePreference('locale', 'vi');
    await flushPreferences();

    expect(readPreference('theme', '')).toBe('light');
    expect(slow.save).not.toHaveBeenCalled();
    expect(bob.save).toHaveBeenCalledWith({ theme: 'light', locale: 'vi' });
  });

  it('treats the same user name on another engine as another user', async () => {
    await connectPreferences('alice', remoteWith({ theme: 'dark' }), 'one');
    const other = remoteWith({});

    await connectPreferences('alice', other, 'two');

    expect(other.save).not.toHaveBeenCalled();
    expect(readPreference('theme', 'system')).toBe('system');
  });

  it('starts a new user from the defaults when the server cannot be reached', async () => {
    await connectPreferences('alice', remoteWith({ tenant: 'acme' }));

    await connectPreferences('bob', { load: () => Promise.reject(new Error('down')), save: vi.fn() });

    expect(readPreference('tenant', null)).toBeNull();
  });

  it('keeps working locally when the server cannot be reached', async () => {
    vi.useFakeTimers();
    const remote = { load: vi.fn(async () => Promise.reject(new Error('down'))), save: vi.fn() };

    await connectPreferences('alice', remote);
    writePreference('theme', 'dark');
    await vi.runAllTimersAsync();

    expect(readPreference('theme', '')).toBe('dark');
    expect(remote.save).not.toHaveBeenCalled();
  });

  it('sends changes after a short delay, once for several changes', async () => {
    vi.useFakeTimers();
    const remote = remoteWith({ theme: 'light' });
    await connectPreferences('alice', remote);

    writePreference('theme', 'dark');
    writePreference('locale', 'vi');
    expect(remote.save).not.toHaveBeenCalled();
    await vi.runAllTimersAsync();

    expect(remote.save).toHaveBeenCalledTimes(1);
    expect(remote.save).toHaveBeenCalledWith({ theme: 'dark', locale: 'vi' });
  });

  it('sends nothing after disconnecting, and survives a failing save', async () => {
    const remote = { load: vi.fn(async () => ({ a: 1 })), save: vi.fn(async () => Promise.reject(new Error('x'))) };
    await connectPreferences('alice', remote);
    writePreference('a', 2);
    await expect(flushPreferences()).resolves.toBeUndefined();
    expect(remote.save).toHaveBeenCalledTimes(1);

    disconnectPreferences();
    writePreference('a', 3);
    await flushPreferences();
    expect(remote.save).toHaveBeenCalledTimes(1);
  });
});
