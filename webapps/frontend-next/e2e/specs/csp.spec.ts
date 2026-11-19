/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, test } from '../support/test';
import { appPath } from '../support/env';

// Every test fails on CSP violations and console errors (see support/test.ts);
// this one walks through the main pages, including those with diagrams and
// editors, and checks that the policy is sent at all.
const ROUTES = [
  '',
  'tasklist',
  'processes',
  'cockpit',
  'cockpit/processes',
  'cockpit/processes/key/e2e_tasks',
  'cockpit/decisions',
  'cockpit/deployments',
  'cockpit/incidents',
  'cockpit/history',
  'cockpit/batches',
  'cockpit/audit',
  'cockpit/cleanup',
  'cockpit/modeler',
  'admin',
  'admin/users',
  'admin/system',
  'preferences',
];

test('the new web UI is served with a nonce-based policy', async ({ app }) => {
  const response = await app.request.get(appPath);
  const policy = response.headers()['content-security-policy'] ?? '';
  expect(policy).toMatch(/script-src [^;]*'nonce-/);
  expect(policy).not.toContain("'unsafe-eval'");
  const nonce = /'nonce-([^']+)'/.exec(policy)?.[1];
  expect(nonce).toBeTruthy();
  expect(await response.text()).toContain(`nonce="${nonce}"`);
});

test('the main pages render without policy violations or console errors', async ({ app }) => {
  for (const route of ROUTES) {
    await open(app, route);
    await expect(app.getByRole('main').getByRole('heading').first(), route).toBeVisible();
  }
});
