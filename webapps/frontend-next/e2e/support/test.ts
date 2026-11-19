/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { test as base, expect, type BrowserContext, type Page } from '@playwright/test';
import { Api } from './api';
import { appPath, engine, password, user } from './env';

/**
 * Console errors that do not point to a defect of the application. Keep this
 * list short and explain every entry.
 */
const BENIGN_CONSOLE_ERRORS: RegExp[] = [
  // a request the application expects to fail and handles, e.g. the login
  // probe without a session or a 404 of an optional resource; the browser logs
  // every failed request regardless
  /Failed to load resource: the server responded with a status of (401|404)/,
];

interface Problems {
  messages: string[];
}

/** Records CSP violations, uncaught errors and console errors of every page of a context. */
export async function watch(context: BrowserContext, problems: Problems): Promise<void> {
  await context.exposeBinding('__e2eCspViolation', ({ frame }, text: string) => {
    problems.messages.push(`CSP violation in ${frame.url()}: ${text}`);
  });
  await context.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      const report = (window as unknown as { __e2eCspViolation?: (text: string) => void }).__e2eCspViolation;
      report?.(`${event.violatedDirective} blocked ${event.blockedURI || '(inline)'} at ${event.sourceFile}:${event.lineNumber}`);
    });
  });
  const attach = (page: Page) => {
    page.on('pageerror', (error) => problems.messages.push(`uncaught error in ${page.url()}: ${error.message}`));
    page.on('console', (message) => {
      if (message.type() !== 'error') {
        return;
      }
      const text = message.text();
      if (/Content Security Policy/i.test(text)) {
        problems.messages.push(`CSP console report in ${page.url()}: ${text}`);
      } else if (!BENIGN_CONSOLE_ERRORS.some((pattern) => pattern.test(text))) {
        problems.messages.push(`console error in ${page.url()}: ${text}`);
      }
    });
  };
  context.pages().forEach(attach);
  context.on('page', attach);
}

/** Signs in through the API of the context, so that pages open with a session. */
export async function signIn(context: BrowserContext, username = user, secret = password): Promise<void> {
  const request = context.request;
  const probe = await request.get(`api/admin/auth/user/${engine}`, { headers: { 'X-XSRF-TOKEN': 'Fetch' } });
  const token = probe.headers()['x-xsrf-token'];
  const response = await request.post(`api/admin/auth/user/${engine}/login/welcome`, {
    form: { username, password: secret },
    headers: { Accept: 'application/json', ...(token && token !== 'Required' ? { 'X-XSRF-TOKEN': token } : {}) },
  });
  expect(response.ok(), `login of ${username}: ${response.status()} ${await response.text()}`).toBeTruthy();
}

/** Opens a route of the new web UI, e.g. `cockpit/processes`. */
export async function open(page: Page, route = ''): Promise<void> {
  await page.goto(`${appPath}#/${route}`);
}

interface Fixtures {
  problems: Problems;
  /** a page with a session of the administrator */
  app: Page;
  api: Api;
}

export const test = base.extend<Fixtures>({
  problems: async ({ context }, use, testInfo) => {
    const problems: Problems = { messages: [] };
    await watch(context, problems);
    await use(problems);
    if (testInfo.status === testInfo.expectedStatus && problems.messages.length > 0) {
      throw new Error(`The browser reported problems:\n${problems.messages.join('\n')}`);
    }
  },
  page: async ({ page, problems }, use) => {
    void problems;
    await use(page);
  },
  app: async ({ page, context }, use) => {
    await signIn(context);
    await use(page);
  },
  api: async ({}, use) => {
    const api = await Api.login();
    await use(api);
    await api.dispose();
  },
});

export { expect };
