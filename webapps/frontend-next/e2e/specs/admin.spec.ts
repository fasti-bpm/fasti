/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX } from '../support/env';
import { dialog } from '../support/ui';
import type { Api } from '../support/api';

function main(page: Page) {
  return page.getByRole('main');
}

async function exists(api: Api, path: string): Promise<boolean> {
  try {
    await api.get(path);
    return true;
  } catch {
    return false;
  }
}

/** Opens the details of an entity from its list and deletes it there. */
async function deleteFromDetails(page: Page, list: string, id: string): Promise<void> {
  await open(page, `admin/${list}/${id}`);
  await expect(main(page).getByRole('heading', { level: 1 })).toBeVisible();
  await main(page).getByRole('button', { name: /^Delete/ }).first().click();
  await dialog(page).getByRole('button', { name: /^Delete/ }).last().click();
  await expect(page).toHaveURL(new RegExp(`#/admin/${list}$`));
}

test.describe('Admin', () => {
  test('creates and deletes a user', async ({ app, api }) => {
    const id = `${PREFIX}user${Date.now()}`;
    await open(app, 'admin/users');
    await main(app).getByRole('button', { name: 'Create user' }).click();
    const create = dialog(app);
    await create.getByRole('textbox', { name: 'User id' }).fill(id);
    await create.getByRole('textbox', { name: 'First name' }).fill('Erika');
    await create.getByRole('textbox', { name: 'Last name' }).fill('Muster');
    await create.getByRole('textbox', { name: 'E-mail' }).fill('erika@example.org');
    await create.getByRole('textbox', { name: 'Password', exact: true }).fill('Secret-e2e-1');
    await create.getByRole('textbox', { name: 'Repeat the password' }).fill('Secret-e2e-1');
    await create.getByRole('button', { name: 'Create user' }).click();
    await expect(create).toBeHidden();
    await expect.poll(() => exists(api, `user/${id}/profile`)).toBe(true);

    await deleteFromDetails(app, 'users', id);
    await expect.poll(() => exists(api, `user/${id}/profile`)).toBe(false);
  });

  test('creates and deletes a group', async ({ app, api }) => {
    const id = `${PREFIX}group${Date.now()}`;
    await open(app, 'admin/groups');
    await main(app).getByRole('button', { name: 'Create group' }).click();
    const create = dialog(app);
    await create.getByRole('textbox', { name: 'Group id' }).fill(id);
    await create.getByRole('textbox', { name: 'Name' }).fill('E2E group');
    await create.getByRole('button', { name: 'Create group' }).click();
    await expect(create).toBeHidden();
    await expect.poll(() => exists(api, `group/${id}`)).toBe(true);

    await deleteFromDetails(app, 'groups', id);
    await expect.poll(() => exists(api, `group/${id}`)).toBe(false);
  });

  test('creates and deletes a tenant', async ({ app, api }) => {
    const id = `${PREFIX}tenant${Date.now()}`;
    await open(app, 'admin/tenants');
    await main(app).getByRole('button', { name: 'Create tenant' }).click();
    const create = dialog(app);
    await create.getByRole('textbox', { name: 'Tenant id' }).fill(id);
    await create.getByRole('textbox', { name: 'Name' }).fill('E2E tenant');
    await create.getByRole('button', { name: 'Create tenant' }).click();
    await expect(create).toBeHidden();
    await expect.poll(() => exists(api, `tenant/${id}`)).toBe(true);

    await deleteFromDetails(app, 'tenants', id);
    await expect.poll(() => exists(api, `tenant/${id}`)).toBe(false);
  });

  test('creates and deletes an authorization', async ({ app, api }) => {
    const userId = `${PREFIX}auth${Date.now()}`;
    await open(app, 'admin/authorizations');
    await main(app).getByRole('button', { name: 'Create authorization' }).click();
    const create = dialog(app);
    await create.getByRole('textbox', { name: 'User id' }).fill(userId);
    await create.getByRole('combobox', { name: 'Resource id' }).fill('cockpit');
    await create.getByRole('button', { name: 'Create', exact: true }).click();
    await expect(create).toBeHidden();
    const created = async () => (await api.get<unknown[]>('authorization', { userIdIn: userId })).length;
    await expect.poll(created).toBe(1);

    const row = main(app).getByRole('row').filter({ hasText: userId });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Delete authorization' }).click();
    await dialog(app).getByRole('button', { name: /^Delete/ }).last().click();
    await expect(row).toBeHidden();
    await expect.poll(created).toBe(0);
  });
});
