/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/http';
import {
  MAX_DOCUMENT_KEYS,
  connectPreferences,
  disconnectPreferences,
  flushPreferences,
  localDocument,
  onPreferencesRejected,
  onPreferencesReplaced,
  readPreference,
  writePreference,
  type PreferenceRemote,
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

  it('waits a few seconds for further changes before sending', async () => {
    vi.useFakeTimers();
    const remote = remoteWith({ theme: 'light' });
    await connectPreferences('alice', remote);

    writePreference('theme', 'dark');
    await vi.advanceTimersByTimeAsync(1000);
    expect(remote.save).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2000);

    expect(remote.save).toHaveBeenCalledTimes(1);
  });

  it('does not send a refused document again and tells the user once', async () => {
    vi.useFakeTimers();
    const remote = { load: vi.fn(async () => ({ a: 1 })), save: vi.fn(async () => Promise.reject(new ApiError('client', 413))) };
    const rejected = vi.fn();
    const off = onPreferencesRejected(rejected);
    await connectPreferences('alice', remote);

    writePreference('a', 2);
    await flushPreferences();
    await vi.runAllTimersAsync();
    expect(remote.save).toHaveBeenCalledTimes(1);
    writePreference('a', 3);
    await flushPreferences();
    await vi.runAllTimersAsync();
    off();

    expect(remote.save).toHaveBeenCalledTimes(2);
    expect(rejected).toHaveBeenCalledTimes(1);
  });

  it('tries again later when the server is unavailable', async () => {
    vi.useFakeTimers();
    const save = vi.fn().mockRejectedValueOnce(new ApiError('server', 503)).mockResolvedValue(undefined);
    await connectPreferences('alice', { load: async () => ({ a: 1 }), save });

    writePreference('a', 2);
    await flushPreferences();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith({ a: 2 });
  });

  it('does not send a document larger than the server keeps', async () => {
    const remote = remoteWith({ a: 1 });
    const rejected = vi.fn();
    const off = onPreferencesRejected(rejected);
    await connectPreferences('alice', remote);

    for (let i = 0; i <= MAX_DOCUMENT_KEYS; i++) writePreference(`key${i}`, i);
    await flushPreferences();
    off();

    expect(remote.save).not.toHaveBeenCalled();
    expect(rejected).toHaveBeenCalledTimes(1);
  });

  it('sends one upload at a time, and then the latest state', async () => {
    const answers: (() => void)[] = [];
    let running = 0;
    let most = 0;
    const save = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          running++;
          most = Math.max(most, running);
          answers.push(() => {
            running--;
            resolve();
          });
        }),
    );
    await connectPreferences('alice', { load: async () => ({ a: 1 }), save });

    writePreference('a', 2);
    const first = flushPreferences();
    writePreference('a', 3);
    void flushPreferences();
    writePreference('a', 4);
    const last = flushPreferences();
    expect(save).toHaveBeenCalledTimes(1);
    answers.shift()?.();
    await vi.waitFor(() => expect(save).toHaveBeenCalledTimes(2));
    answers.shift()?.();
    await Promise.all([first, last]);

    expect(most).toBe(1);
    expect(save).toHaveBeenLastCalledWith({ a: 4 });
  });

  it('sends the changes still waiting when the page is closed, with a request that outlives it', async () => {
    vi.useFakeTimers();
    const remote = remoteWith({ a: 1 });
    await connectPreferences('alice', remote);

    writePreference('a', 2);
    window.dispatchEvent(new Event('pagehide'));
    await vi.runAllTimersAsync();

    expect(remote.save).toHaveBeenCalledTimes(1);
    expect(remote.save).toHaveBeenCalledWith({ a: 2 }, { keepalive: true });
  });

  it('sends the latest state again when the page is closed during an upload', async () => {
    const answers: (() => void)[] = [];
    const save = vi.fn<PreferenceRemote['save']>(() => new Promise<void>((resolve) => answers.push(resolve)));
    await connectPreferences('alice', { load: async () => ({ a: 1 }), save });

    writePreference('a', 2);
    const uploading = flushPreferences();
    window.dispatchEvent(new Event('pagehide'));
    answers.forEach((answer) => answer());
    await uploading;

    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith({ a: 2 }, { keepalive: true });
  });

  it('does not let an upload that hangs hold up the next connection for long', async () => {
    vi.useFakeTimers();
    const hanging = vi.fn<PreferenceRemote['save']>(() => new Promise<void>(() => undefined));
    await connectPreferences('alice', { load: async () => ({ a: 1 }), save: hanging });
    writePreference('a', 2);
    void flushPreferences();
    const next = remoteWith({ a: 5 });

    const connecting = connectPreferences('alice', next);
    await vi.advanceTimersByTimeAsync(5000);
    await connecting;
    writePreference('a', 6);
    await flushPreferences();

    expect(next.save).toHaveBeenCalledWith({ a: 6 });
  });

  it('loads anew only after the upload of the connection before arrived', async () => {
    const answers: (() => void)[] = [];
    const save = vi.fn<PreferenceRemote['save']>(() => new Promise<void>((resolve) => answers.push(resolve)));
    await connectPreferences('alice', { load: async () => ({ a: 1 }), save });
    writePreference('a', 2);
    void flushPreferences();
    const again = remoteWith({ a: 2 });

    // the session is read again, e.g. after the user changed the own name
    const connecting = connectPreferences('alice', again);
    await Promise.resolve();
    expect(again.load).not.toHaveBeenCalled();
    answers.forEach((answer) => answer());
    await connecting;

    expect(again.load).toHaveBeenCalledTimes(1);
  });

  it('sends nothing when the page is closed without changes', async () => {
    const remote = remoteWith({ a: 1 });
    await connectPreferences('alice', remote);

    window.dispatchEvent(new Event('pagehide'));

    expect(remote.save).not.toHaveBeenCalled();
  });
});

describe('keys that left the namespace of the per-user Tasklist entries', () => {
  it('moves them to their new names for the owner of the browser and adds them to the server copy', async () => {
    vi.useFakeTimers();
    window.localStorage.setItem('fasti.next.tasklist.processFields', JSON.stringify({ invoice: [{ name: 'amount', label: 'Amount' }] }));
    window.localStorage.setItem('fasti.next.tasklist.notify', 'true');
    const remote = remoteWith({ theme: 'dark' });

    await connectPreferences('alice', remote);
    await vi.runAllTimersAsync();

    expect(window.localStorage.getItem('fasti.next.tasklist.processFields')).toBeNull();
    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBeNull();
    expect(readPreference('tasks.notify', false)).toBe(true);
    expect(remote.save).toHaveBeenCalledWith({
      theme: 'dark',
      'tasks.notify': true,
      'tasks.processFields': { invoice: [{ name: 'amount', label: 'Amount' }] },
    });
  });

  it('keeps them until the server copy could take them in', async () => {
    vi.useFakeTimers();
    window.localStorage.setItem('fasti.next.tasklist.notify', 'true');

    await connectPreferences('alice', { load: () => Promise.reject(new Error('down')), save: vi.fn() });
    expect(readPreference('tasks.notify', false)).toBe(true);
    const remote = remoteWith({ theme: 'dark' });
    await connectPreferences('alice', remote);
    await vi.runAllTimersAsync();

    expect(readPreference('tasks.notify', false)).toBe(true);
    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBeNull();
    expect(remote.save).toHaveBeenCalledWith({ theme: 'dark', 'tasks.notify': true });
  });

  it('keeps them until an upload with their values was stored', async () => {
    vi.useFakeTimers();
    window.localStorage.setItem('fasti.next.tasklist.notify', 'true');
    const failing = { load: vi.fn(async () => ({ theme: 'dark' })), save: vi.fn(async () => Promise.reject(new ApiError('server', 503))) };

    await connectPreferences('alice', failing);
    await vi.advanceTimersByTimeAsync(3000);
    expect(failing.save).toHaveBeenCalledWith({ theme: 'dark', 'tasks.notify': true });
    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBe('true');
    // the page is reloaded: the server copy still lacks the value, and so does a local copy that was lost
    disconnectPreferences();
    window.localStorage.removeItem('fasti.next.tasks.notify');
    const remote = remoteWith({ theme: 'dark' });
    await connectPreferences('alice', remote);
    await vi.runAllTimersAsync();

    expect(readPreference('tasks.notify', false)).toBe(true);
    expect(remote.save).toHaveBeenCalledWith({ theme: 'dark', 'tasks.notify': true });
    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBeNull();
  });

  it('drops an old key at once when the server already has its value', async () => {
    window.localStorage.setItem('fasti.next.tasklist.notify', 'true');
    const remote = remoteWith({ 'tasks.notify': false });

    await connectPreferences('alice', remote);

    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBeNull();
    expect(readPreference('tasks.notify', true)).toBe(false);
    expect(remote.save).not.toHaveBeenCalled();
  });

  it('drops them for a new user of a shared browser', async () => {
    await connectPreferences('bob', remoteWith({ theme: 'light' }));
    disconnectPreferences();
    window.localStorage.setItem('fasti.next.tasklist.notify', 'true');
    const alice = remoteWith({});

    await connectPreferences('alice', alice);

    expect(window.localStorage.getItem('fasti.next.tasklist.notify')).toBeNull();
    expect(readPreference('tasks.notify', false)).toBe(false);
    expect(alice.save).not.toHaveBeenCalled();
  });

  it('keeps the entry of a user of that very name', async () => {
    writePreference('tasklist.notify', { onlyMine: true });

    await connectPreferences('notify', remoteWith({}));

    expect(readPreference('tasklist.notify', null)).toEqual({ onlyMine: true });
  });

  it('shares the new names, and never the entries of other users', () => {
    writePreference('tasks.notify', true);
    writePreference('tasklist.bob', { onlyMine: true });

    expect(localDocument('alice')).toEqual({ 'tasks.notify': true });
  });
});
