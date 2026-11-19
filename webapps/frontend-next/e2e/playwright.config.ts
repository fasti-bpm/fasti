/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// End-to-end tests of the new web UI against a running distribution.
//
//   E2E_BASE_URL     root of the web applications (default http://localhost:18800/fasti/)
//   E2E_USER         administrator used by the tests (default demo)
//   E2E_PASSWORD     password of that user (default demo)
//
// The tests use the Chromium build of Playwright (`npx playwright install chromium`).
import { defineConfig, devices } from '@playwright/test';
import { baseUrl } from './support/env';

export default defineConfig({
  testDir: './specs',
  outputDir: '../target/e2e/results',
  globalSetup: './support/global-setup.ts',
  globalTeardown: './support/global-teardown.ts',
  // the tests share one engine; order matters less than isolation, but the
  // engine-wide pages (batches, operation log) are easier to read in sequence
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  reporter: [
    ['list'],
    ['html', { outputFolder: '../target/e2e/report', open: 'never' }],
    ['junit', { outputFile: '../target/e2e/junit.xml' }],
  ],
  use: {
    baseURL: baseUrl,
    locale: 'en-US',
    timezoneId: 'UTC',
    acceptDownloads: true,
    // an action that cannot happen fails with what it waited for instead of using up the test
    actionTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
