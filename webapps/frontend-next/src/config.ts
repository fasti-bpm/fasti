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
}

export const defaultConfig: AppConfig = {
  csrfCookieName: 'XSRF-TOKEN',
  defaultLocale: 'en',
};

export const config: AppConfig = { ...defaultConfig };

/** How long the page waits for `config.json` before it starts with the defaults. */
export const CONFIG_TIMEOUT = 3000;

/**
 * Reads `config.json`. A file that is missing, broken or not answered within
 * `timeout` ms leaves the defaults in place: the first render never waits
 * longer than that for a file most installations do not change.
 */
export async function loadConfig(timeout: number = CONFIG_TIMEOUT): Promise<AppConfig> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(assetUrl('config.json'), {
      credentials: 'same-origin',
      cache: 'no-cache',
      signal: controller.signal,
    });
    if (response.ok) {
      const raw: unknown = await response.json();
      if (raw && typeof raw === 'object') {
        const values = raw as Record<string, unknown>;
        if (typeof values.csrfCookieName === 'string' && /^[\w-]+$/.test(values.csrfCookieName)) {
          config.csrfCookieName = values.csrfCookieName;
        }
        if (typeof values.defaultLocale === 'string' && /^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(values.defaultLocale)) {
          config.defaultLocale = values.defaultLocale;
        }
      }
    }
  } catch {
    // the defaults apply
  } finally {
    clearTimeout(timer);
  }
  return config;
}
