/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, test } from '../support/test';

test('the welcome page links to the applications', async ({ app }) => {
  await open(app, '');
  const main = app.getByRole('main');
  for (const name of ['Tasks', 'Cockpit', 'Admin']) {
    await expect(main.getByRole('link', { name, exact: false }).first()).toBeVisible();
  }
  await main.getByRole('link', { name: /Cockpit/ }).first().click();
  await expect(app).toHaveURL(/#\/cockpit/);
});
