/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { config, configSettled, defaultConfig, loadConfig, onLateConfig, resetConfig } from './config';
import { isLoaded } from './i18n';
import { writePreference } from './lib/storage';
import { prepare } from './startup';

/** A fetch that never answers, but gives up when its signal aborts, like a stalled connection. */
function stalled(): typeof fetch {
  return vi.fn(
    (_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
      }),
  );
}

const answer = (body: unknown): typeof fetch =>
  vi.fn(() => Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })));

describe('start-up', () => {
  beforeEach(() => {
    resetConfig();
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    resetConfig();
    localStorage.clear();
  });

  it('reads the settings of the installation', async () => {
    vi.stubGlobal('fetch', answer({ csrfCookieName: 'MY-TOKEN', defaultLocale: 'vi' }));

    await loadConfig();

    expect(config).toEqual({ csrfCookieName: 'MY-TOKEN', defaultLocale: 'vi', idlePacing: true });
  });

  it('keeps the defaults when the settings are not answered in time', async () => {
    vi.useFakeTimers();
    const fetchMock = stalled();
    vi.stubGlobal('fetch', fetchMock);

    const loading = loadConfig(3000);
    await vi.advanceTimersByTimeAsync(2999);
    let done = false;
    void loading.then(() => (done = true));
    await vi.advanceTimersByTimeAsync(0);
    expect(done).toBe(false);
    await vi.advanceTimersByTimeAsync(1);

    await expect(loading).resolves.toEqual(defaultConfig);
    // not given up: an answer that comes later still applies
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(vi.mocked(fetchMock).mock.calls[0]?.[1]?.signal).toBeUndefined();
  });

  it('keeps the defaults when the settings are broken or missing', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('<html>', { status: 200 }))));
    await expect(loadConfig()).resolves.toEqual(defaultConfig);

    resetConfig();
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('', { status: 404 }))));
    await expect(loadConfig()).resolves.toEqual(defaultConfig);

    resetConfig();
    vi.useFakeTimers();
    const offline = vi.fn(() => Promise.reject(new TypeError('offline')));
    vi.stubGlobal('fetch', offline);
    const loading = loadConfig();
    await vi.advanceTimersByTimeAsync(20_000);
    await expect(loading).resolves.toEqual(defaultConfig);
    // a failed connection is tried again a few times; an answer is not
    expect(offline).toHaveBeenCalledTimes(4);
  });

  it('applies settings that arrive after the page started without them, and tells who listens', async () => {
    vi.useFakeTimers();
    let answer: (response: Response) => void = () => undefined;
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => (answer = resolve)));
    vi.stubGlobal('fetch', fetchMock);
    const late = vi.fn();

    const loading = loadConfig(3000);
    await vi.advanceTimersByTimeAsync(3000);
    await expect(loading).resolves.toEqual(defaultConfig);
    onLateConfig(late);
    let settled = false;
    void configSettled().then(() => (settled = true));
    await vi.advanceTimersByTimeAsync(1000);
    expect(settled).toBe(false);

    answer(new Response(JSON.stringify({ csrfCookieName: 'MY-TOKEN', defaultLocale: 'vi', idlePacing: false })));
    await vi.advanceTimersByTimeAsync(0);

    expect(config).toEqual({ csrfCookieName: 'MY-TOKEN', defaultLocale: 'vi', idlePacing: false });
    expect(late).toHaveBeenCalledTimes(1);
    expect(settled).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('lets a write wait for settings that do not come only so long', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', stalled());
    void loadConfig(3000);

    let settled = false;
    void configSettled(20_000).then(() => (settled = true));
    await vi.advanceTimersByTimeAsync(19_999);
    expect(settled).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(settled).toBe(true);
  });

  it('does not let stalled settings hold back the first render', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', stalled());

    let ready = false;
    void prepare(4000).then(() => (ready = true));
    await vi.advanceTimersByTimeAsync(2900);
    expect(ready).toBe(false);
    await vi.advanceTimersByTimeAsync(1200);

    expect(ready).toBe(true);
    expect(config).toEqual(defaultConfig);
  });

  it('loads the language the user chose beside the settings, not after them', async () => {
    vi.useFakeTimers();
    writePreference('locale', 'de');
    vi.stubGlobal('fetch', stalled());

    const preparing = prepare(4000);
    // the settings are still pending, yet the language is being loaded already
    await vi.waitFor(() => expect(isLoaded('de')).toBe(true));
    await vi.advanceTimersByTimeAsync(4000);
    await preparing;
  });

  it('loads the default language of the installation', async () => {
    vi.stubGlobal('fetch', answer({ defaultLocale: 'fr' }));

    await prepare();

    expect(isLoaded('fr')).toBe(true);
  });
});
