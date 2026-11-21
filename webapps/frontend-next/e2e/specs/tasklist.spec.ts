/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { answered, dialog } from '../support/ui';
import { createFilter } from '../support/data';
import type { Api, Task } from '../support/api';

async function openFilter(page: Page, filterId: string): Promise<void> {
  await open(page, `tasklist?filter=${filterId}`);
  await expect(page.getByRole('listbox', { name: 'Tasks' })).toBeVisible();
}

async function taskOf(api: Api, instanceId: string): Promise<Task> {
  const [task] = await api.tasks(instanceId);
  if (!task) {
    throw new Error(`no task in ${instanceId}`);
  }
  return task;
}

test.describe('Tasklist', () => {
  test('creates, edits and deletes a filter', async ({ app, api }) => {
    await api.startProcess('e2e_form');
    const name = `${PREFIX} ui filter ${Date.now()}`;
    await open(app, 'tasklist');
    await app.getByRole('button', { name: 'New filter' }).click();
    const editor = dialog(app);
    await expect(editor).toBeVisible();
    await editor.getByRole('textbox', { name: 'Name' }).fill(name);
    await editor.getByRole('tab', { name: 'Criteria' }).click();
    await editor.getByRole('button', { name: 'Add criterion' }).click();
    await editor.getByRole('combobox', { name: 'Criterion' }).last().selectOption('processDefinitionKey');
    await editor.getByRole('textbox', { name: 'Process key', exact: true }).fill('e2e_form');
    await editor.getByRole('button', { name: 'Save' }).click();
    await expect(editor).toBeHidden();

    const filters = app.getByRole('list', { name: 'Filters' });
    const entry = filters.getByRole('button', { name: new RegExp(name) });
    await expect(entry).toBeVisible();
    await entry.click();
    await expect(app.getByRole('listbox', { name: 'Tasks' }).getByRole('option').first()).toContainText('E2E fill form');
    const [stored] = await api.get<{ id: string; query: Record<string, unknown> }[]>('filter', { name });
    expect(stored?.query.processDefinitionKey).toBe('e2e_form');

    // edit
    const renamed = `${name} renamed`;
    await app.getByRole('button', { name: /Settings of the filter/ }).click();
    await editor.getByRole('textbox', { name: 'Name' }).fill(renamed);
    await editor.getByRole('button', { name: 'Save' }).click();
    await expect(editor).toBeHidden();
    await expect(filters.getByRole('button', { name: new RegExp(renamed) })).toBeVisible();
    expect(await api.get<unknown[]>('filter', { name: renamed })).toHaveLength(1);

    // delete
    await app.getByRole('button', { name: /Settings of the filter/ }).click();
    await editor.getByRole('button', { name: 'Delete filter' }).click();
    // only the delete is sent, not a save of the filter being deleted as well
    const saves: string[] = [];
    app.on('request', (request) => {
      if (request.method() === 'PUT' && /\/filter\/[^/]+$/.test(new URL(request.url()).pathname)) saves.push(request.url());
    });
    const deleted = answered(app, 'DELETE', /\/filter\/[^/]+$/);
    await dialog(app).last().getByRole('button', { name: 'Delete filter' }).click();
    // the filter is gone on the server once the delete was answered, not before
    expect((await deleted).status()).toBe(204);
    await expect(filters.getByRole('button', { name: new RegExp(renamed) })).toBeHidden();
    expect(await api.get<unknown[]>('filter', { name: renamed })).toHaveLength(0);
    expect(saves).toEqual([]);
  });

  test('claims and unclaims a task', async ({ app, api }) => {
    const filterId = await createFilter(api, `${PREFIX} claim ${Date.now()}`, 'e2e_tasks');
    const instance = await api.startProcess('e2e_tasks', {}, `${PREFIX}-claim`);
    const task = await taskOf(api, instance.id);
    await openFilter(app, filterId);
    await open(app, `tasklist?filter=${filterId}&task=${task.id}`);
    const details = app.getByRole('region', { name: 'Task details' });
    await expect(details.getByRole('heading', { name: 'E2E review' })).toBeVisible();

    await details.getByRole('button', { name: 'Claim', exact: true }).first().click();
    await expect(details.getByRole('button', { name: 'Unclaim' })).toBeVisible();
    await expect.poll(async () => (await taskOf(api, instance.id)).assignee).toBe(user);

    await details.getByRole('button', { name: 'Unclaim' }).click();
    await expect(details.getByRole('button', { name: 'Claim', exact: true }).first()).toBeVisible();
    await expect.poll(async () => (await taskOf(api, instance.id)).assignee).toBeNull();
  });

  test('claims several tasks at once', async ({ app, api }) => {
    const filterId = await createFilter(api, `${PREFIX} bulk ${Date.now()}`, 'e2e_tasks');
    const ids: string[] = [];
    for (let i = 0; i < 3; i++) {
      ids.push((await api.startProcess('e2e_tasks', {}, `${PREFIX}-bulk-${i}`)).id);
    }
    // only unassigned tasks of this test count
    for (const task of await api.get<Task[]>('task', { processDefinitionKey: 'e2e_tasks', assigned: true })) {
      await api.post(`task/${task.id}/unclaim`);
    }
    await openFilter(app, filterId);
    await expect(app.getByRole('listbox', { name: 'Tasks' }).getByRole('option')).not.toHaveCount(0);
    await app.getByRole('checkbox', { name: 'Select all tasks on this page' }).check();
    const bulk = app.getByRole('region', { name: 'Selected tasks' });
    await expect(bulk).toBeVisible();
    await bulk.getByRole('button', { name: 'Claim', exact: true }).click();
    await dialog(app).getByRole('button', { name: 'Claim', exact: true }).click();
    await expect(dialog(app)).toBeHidden();
    for (const id of ids) {
      await expect.poll(async () => (await taskOf(api, id)).assignee).toBe(user);
    }
  });

  test('completes a task with a form-js form', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_form', {}, `${PREFIX}-form`);
    const task = await taskOf(api, instance.id);
    const filterId = await createFilter(api, `${PREFIX} form ${Date.now()}`, 'e2e_form');
    await open(app, `tasklist?filter=${filterId}&task=${task.id}`);
    const details = app.getByRole('region', { name: 'Task details' });
    await expect(details.getByRole('heading', { name: 'E2E fill form' })).toBeVisible();

    const comment = details.getByLabel(/Comment/);
    await expect(comment).toBeVisible();
    // the field is required: completing without it is refused
    await details.getByRole('button', { name: 'Complete' }).click();
    await expect(comment).toBeVisible();
    expect(await api.tasks(instance.id)).toHaveLength(1);

    await comment.fill('looks good');
    await details.getByLabel('Approved').check();
    await details.getByRole('button', { name: 'Complete' }).click();
    await expect.poll(async () => (await api.tasks(instance.id)).length).toBe(0);
    const variables = await api.get<{ name: string; value: unknown }[]>('history/variable-instance', {
      processInstanceId: instance.id,
    });
    expect(Object.fromEntries(variables.map((v) => [v.name, v.value]))).toMatchObject({
      comment: 'looks good',
      approved: true,
    });
  });
});
