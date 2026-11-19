/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** Root of the web applications, always with a trailing slash. */
export const baseUrl = (process.env.E2E_BASE_URL ?? 'http://localhost:18800/fasti/').replace(/\/*$/, '/');
export const user = process.env.E2E_USER ?? 'demo';
export const password = process.env.E2E_PASSWORD ?? 'demo';
export const engine = process.env.E2E_ENGINE ?? 'default';

/** Path of the new web UI below the base URL. */
export const appPath = `app/next/${engine}/`;

/** Prefix of every name the tests create, so that the teardown finds them. */
export const PREFIX = 'e2e';
