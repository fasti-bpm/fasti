/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, test, watch } from '../support/test';
import { engine, password, user } from '../support/env';

test('preferences follow the user into a new browser context', async ({ app, api, browser, baseURL }) => {
  // the test changes preferences of a real user: put them back afterwards
  const original = await api.preferences();
  await open(app, 'preferences');
  const main = app.getByRole('main');
  await expect(main.getByRole('heading', { name: 'Preferences', level: 1 })).toBeVisible();
  const density = main.getByRole('group', { name: 'Density' });
  const startPage = main.getByRole('combobox', { name: 'Start page' });
  try {
    await density.getByRole('radio', { name: 'Compact' }).check();
    await startPage.selectOption({ index: 3 });
    const chosen = await startPage.inputValue();
    // the page sends the changes to the server shortly after they were made
    await expect
      .poll(async () => {
        const response = await app.request.get(`api/admin/auth/user/${engine}/preferences`);
        return response.ok() ? ((await response.json()) as Record<string, unknown>) : null;
      })
      .toMatchObject({ density: 'compact', startPage: chosen });

    // a second browser without any local state of the first one
    const context = await browser.newContext({ baseURL });
    const problems = { messages: [] as string[] };
    await watch(context, problems);
    try {
      const page = await context.newPage();
      // signing in on the login page opens the chosen start page
      await open(page, '');
      await page.getByLabel('User name').fill(user);
      await page.getByLabel('Password').fill(password);
      await page.getByRole('button', { name: 'Sign in' }).click();
      await expect(page).toHaveURL(new RegExp(`#/${chosen}`));

      await open(page, 'preferences');
      const other = page.getByRole('main');
      await expect(other.getByRole('heading', { name: 'Preferences', level: 1 })).toBeVisible();
      await expect(other.getByRole('group', { name: 'Density' }).getByRole('radio', { name: 'Compact' })).toBeChecked();
      await expect(other.getByRole('combobox', { name: 'Start page' })).toHaveValue(chosen);
      expect(problems.messages).toEqual([]);
    } finally {
      await context.close();
    }
  } finally {
    await api.savePreferences(original);
  }
});
