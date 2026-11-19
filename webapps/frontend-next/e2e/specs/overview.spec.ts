/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX } from '../support/env';
import type { Api } from '../support/api';

function main(page: Page) {
  return page.getByRole('main');
}

/**
 * Runs a test in Vietnamese. The language is a preference stored for the user
 * on the server, so it is put back once the page is closed.
 */
async function inVietnamese(page: Page, api: Api, run: () => Promise<void>): Promise<void> {
  const original = await api.preferences();
  // the copy on the server wins over the browser once the session starts
  await api.savePreferences({ ...original, locale: 'vi' });
  await page.addInitScript(() => window.localStorage.setItem('fasti.next.locale', JSON.stringify('vi')));
  try {
    await run();
  } finally {
    await page.close();
    await api.savePreferences(original);
  }
}

test.describe('Overview, phones and languages', () => {
  test('shows the health of the engine and an overview whose counts open their pages', async ({ app }) => {
    await open(app, 'admin/system');
    const health = main(app).getByRole('list', { name: 'Health' });
    await expect(health.getByRole('listitem')).toHaveCount(5);
    await expect(main(app).getByText(/The engine answered in \d/)).toBeVisible();
    await main(app).getByRole('link', { name: /^Users: \d/ }).click();
    await expect(app).toHaveURL(/#\/admin\/users/);
  });

  test('chooses the sorting of a table on a phone with one selector', async ({ app }) => {
    await app.setViewportSize({ width: 375, height: 812 });
    await open(app, 'cockpit/processes');
    const sortBy = main(app).getByRole('combobox', { name: 'Sort by' });
    await expect(sortBy).toBeVisible();
    // the header of the table no longer shows its sort buttons as chips
    await expect(main(app).getByRole('columnheader', { name: 'Name' }).getByRole('button')).not.toBeInViewport();
    await sortBy.selectOption({ label: 'Key' });
    await expect(main(app).getByRole('columnheader', { name: 'Key' })).toHaveAttribute('aria-sort', 'ascending');
    await main(app).getByRole('button', { name: /^Sort order: Ascending/ }).click();
    await expect(main(app).getByRole('columnheader', { name: 'Key' })).toHaveAttribute('aria-sort', 'descending');
  });

  test('finds a section by its English name in a Vietnamese interface', async ({ app, api }) => {
    await inVietnamese(app, api, async () => {
      await open(app, 'cockpit');
      await app.getByRole('button', { name: 'Tìm lệnh' }).click();
      const palette = app.getByRole('dialog');
      await palette.getByRole('combobox').fill('incidents');
      await expect(palette.getByRole('option', { name: /Sự cố/ }).first()).toBeVisible();
    });
  });

  test('lets the audit pick one of the instances that share a business key', async ({ app, api }) => {
    const key = `${PREFIX}-reused-${Date.now()}`;
    const first = await api.startProcess('e2e_tasks', {}, key);
    const second = await api.startProcess('e2e_tasks', {}, key);
    await open(app, `cockpit/audit?case=${encodeURIComponent(key)}`);
    const pick = main(app).getByRole('combobox', { name: /belongs to 2 instances/ });
    await expect(pick).toBeVisible();
    // the newest is shown first
    await expect(pick).toHaveValue(second.id);
    await pick.selectOption(first.id);
    await expect(app).toHaveURL(new RegExp(`caseid=${first.id}`));
  });

  test('shows the diagram tools in Vietnamese and still finds an element by its English name', async ({ app, api }) => {
    await inVietnamese(app, api, async () => {
      await open(app, 'cockpit/modeler');
      const task = app.locator('.djs-palette .entry.bpmn-icon-task');
      await expect(task).toHaveAttribute('aria-label', 'Tạo tác vụ');
      await task.click();
      const box = await app.locator('.djs-container').boundingBox();
      if (!box) throw new Error('no canvas');
      await app.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
      // a new shape starts with its label being edited: end that and select the shape
      await app.keyboard.press('Escape');
      await app.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
      await app.locator('.djs-context-pad .entry[data-action="replace"]').click();
      const menu = app.locator('.djs-popup');
      await menu.locator('input').first().fill('user task');
      await expect(menu.getByText('Tác vụ người dùng')).toBeVisible();
    });
  });
});
