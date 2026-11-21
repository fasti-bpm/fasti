/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { assetUrl } from './env';

/** Optional settings of an installation, read from `config.json`. */
export interface AppConfig {
  /** must match the cookie name configured for the CSRF prevention filter */
  csrfCookieName: string;
  /** BCP 47 tag of a language in `src/i18n/locales`; an unknown one means English */
  defaultLocale: string;
  /**
   * whether pages refresh less often while nobody is at the screen; `false` for wall displays
   * that nobody touches but that should stay current
   */
  idlePacing: boolean;
}

export const defaultConfig: AppConfig = {
  csrfCookieName: 'XSRF-TOKEN',
  defaultLocale: 'en',
  idlePacing: true,
};

export const config: AppConfig = { ...defaultConfig };

/** How long the first render waits for `config.json` before it starts with the defaults. */
export const CONFIG_TIMEOUT = 3000;

/**
 * How long a request that changes something waits for a `config.json` that is still on its way:
 * it may name the CSRF cookie, and a request sent with the wrong one is refused.
 */
export const CONFIG_WRITE_WAIT = 20_000;

/** Pauses before the attempts after a failed connection; a file that answers is not asked again. */
const RETRY_DELAYS = [1000, 3000, 10_000];

/** The reading of `config.json` in progress, or done; `null` before it started. */
let loading: Promise<void> | null = null;
let settled = false;
const listeners = new Set<() => void>();

/** For tests: forget that the settings were read. */
export function resetConfig(): void {
  Object.assign(config, defaultConfig);
  loading = null;
  settled = false;
  listeners.clear();
}

/**
 * Calls `listener` once `config.json` was read; registered after the first render, it hears of
 * a file that arrived only after the page had started with the defaults.
 */
export function onLateConfig(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function apply(raw: unknown): void {
  if (!raw || typeof raw !== 'object') return;
  const values = raw as Record<string, unknown>;
  if (typeof values.csrfCookieName === 'string' && /^[\w-]+$/.test(values.csrfCookieName)) {
    config.csrfCookieName = values.csrfCookieName;
  }
  if (typeof values.defaultLocale === 'string' && /^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(values.defaultLocale)) {
    config.defaultLocale = values.defaultLocale;
  }
  if (typeof values.idlePacing === 'boolean') {
    config.idlePacing = values.idlePacing;
  }
}

const pause = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function read(): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    try {
      const response = await fetch(assetUrl('config.json'), { credentials: 'same-origin', cache: 'no-cache' });
      if (response.ok) {
        apply(await response.json().catch(() => null));
      }
      // missing or broken: the defaults apply, asking again would not change that
      return;
    } catch {
      // no connection: worth another attempt, the file may name the CSRF cookie
      const delay = RETRY_DELAYS[attempt];
      if (delay === undefined) return;
      await pause(delay);
    }
  }
}

/**
 * Reads `config.json`. The promise settles once the file was read, or after `timeout` ms with
 * the defaults in place: the first render never waits longer than that for a file most
 * installations do not change. A file that answers later is still applied, and the listeners
 * of `onLateConfig` are told.
 */
export function loadConfig(timeout: number = CONFIG_TIMEOUT): Promise<AppConfig> {
  if (!loading) {
    loading = read().finally(() => {
      settled = true;
      // only those who started without the file listen by then
      for (const listener of [...listeners]) listener();
    });
  }
  let timer: ReturnType<typeof setTimeout> | undefined;
  return Promise.race([
    loading,
    new Promise<void>((resolve) => {
      timer = setTimeout(resolve, timeout);
    }),
  ]).then(() => {
    clearTimeout(timer);
    return config;
  });
}

/**
 * Settles once `config.json` was read, at once if it was read already or is not being read
 * at all, and at the latest after `maxWait` ms.
 */
export function configSettled(maxWait: number = CONFIG_WRITE_WAIT): Promise<void> {
  if (!loading || settled) return Promise.resolve();
  let timer: ReturnType<typeof setTimeout> | undefined;
  return Promise.race<void>([
    loading,
    new Promise<void>((resolve) => {
      timer = setTimeout(resolve, maxWait);
    }),
  ]).finally(() => clearTimeout(timer));
}
