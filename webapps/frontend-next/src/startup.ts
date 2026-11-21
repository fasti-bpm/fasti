/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { CONFIG_TIMEOUT, loadConfig } from './config';
import { initialLocale, loadLocale, storedLocale } from './i18n';

/** The longest the first render waits for the settings and the language of the user. */
export const STARTUP_TIMEOUT = CONFIG_TIMEOUT + 1000;

/**
 * What the first render waits for: the settings of the installation and the
 * messages of the language to show. A language the user chose earlier does
 * not depend on the settings, so it is loaded beside them, not after them.
 *
 * Neither may hold the page back for good: after `timeout` ms the page starts
 * with what is there (the default settings, English) and switches to the
 * language once its messages arrive.
 */
export async function prepare(timeout: number = STARTUP_TIMEOUT): Promise<void> {
  const stored = storedLocale();
  const early = stored ? loadLocale(stored).catch(() => undefined) : null;
  const ready = loadConfig(Math.min(CONFIG_TIMEOUT, timeout))
    .then(() => early ?? loadLocale(initialLocale()))
    .catch(() => undefined);
  let timer: ReturnType<typeof setTimeout> | undefined;
  const deadline = new Promise<void>((resolve) => {
    timer = setTimeout(resolve, timeout);
  });
  await Promise.race([ready, deadline]);
  clearTimeout(timer);
}
