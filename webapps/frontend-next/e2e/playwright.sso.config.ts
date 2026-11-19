/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// End-to-end tests of single sign-on (OpenID Connect) against a running
// distribution configured for an identity provider. Separate from the main
// suite: that one signs in with a password and seeds data through basic
// authentication. The tests are skipped unless E2E_OIDC_BASE_URL,
// E2E_OIDC_USER and E2E_OIDC_PASSWORD are set; see sso/sso.spec.ts.
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './sso',
  outputDir: '../target/e2e-sso/results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  reporter: [
    ['list'],
    ['html', { outputFolder: '../target/e2e-sso/report', open: 'never' }],
    ['junit', { outputFile: '../target/e2e-sso/junit.xml' }],
  ],
  use: {
    locale: 'en-US',
    timezoneId: 'UTC',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});
