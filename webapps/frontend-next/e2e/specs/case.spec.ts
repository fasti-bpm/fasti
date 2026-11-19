/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, test } from '../support/test';
import { PREFIX } from '../support/env';
import { createFilter } from '../support/data';

test.describe('Working on a case', () => {
  test('saves form values without completing and comments on the whole case', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_form', {}, `${PREFIX}-case`);
    const [task] = await api.tasks(instance.id);
    if (!task) throw new Error('no task');
    const filterId = await createFilter(api, `${PREFIX} case ${Date.now()}`, 'e2e_form');
    await open(app, `tasklist?filter=${filterId}&task=${task.id}`);
    const details = app.getByRole('region', { name: 'Task details' });

    // Save writes the values and leaves the task open
    await details.getByLabel(/Comment/).fill('half done');
    await details.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(details.getByText(/^Saved at /)).toBeVisible();
    await expect
      .poll(async () => (await api.get<{ name: string; value: unknown }[]>('variable-instance', { processInstanceIdIn: instance.id })).find((v) => v.name === 'comment')?.value)
      .toBe('half done');
    expect(await api.tasks(instance.id)).toHaveLength(1);

    // the case: a comment for everybody on it, listed with the steps
    await details.getByRole('tab', { name: 'Case' }).click();
    await details.getByLabel('Comment on the whole case').fill('customer called back');
    await details.getByRole('button', { name: 'Add comment' }).click();
    const history = details.getByRole('list', { name: 'Case history' });
    await expect(history.getByText('customer called back')).toBeVisible();
    await expect(history.getByText('whole case').first()).toBeVisible();
    const comments = await api.get<{ message: string; taskId: string | null }[]>(`process-instance/${instance.id}/comment`);
    expect(comments).toEqual(expect.arrayContaining([expect.objectContaining({ message: 'customer called back', taskId: null })]));
  });

  test('finds instances matching any of several variable conditions', async ({ app, api }) => {
    const tag = `or-${Date.now()}`;
    await api.startProcess('e2e_form', { tag, amount: 5 }, `${PREFIX}-${tag}-low`);
    await api.startProcess('e2e_form', { tag, amount: 500 }, `${PREFIX}-${tag}-high`);
    await api.startProcess('e2e_form', { tag, amount: 1, vip: true }, `${PREFIX}-${tag}-vip`);
    const [definition] = await api.get<{ id: string }[]>('process-definition', { key: 'e2e_form', latestVersion: true });
    await open(app, `cockpit/processes/definition/${definition?.id}`);

    await app.getByRole('button', { name: 'Variables', exact: true }).click();
    await app.getByLabel('Variable name, row 1').fill('amount');
    await app.getByLabel('Type, row 1').selectOption('number');
    await app.getByLabel('Comparison, row 1').selectOption('gt');
    await app.getByLabel('Value, row 1').fill('100');
    await app.getByRole('button', { name: 'Add condition' }).click();
    await app.getByLabel('Variable name, row 2').fill('vip');
    await app.getByLabel('Type, row 2').selectOption('boolean');
    await app.getByRole('button', { name: 'Any condition' }).click();
    await app.getByRole('button', { name: 'Apply' }).click();

    const table = app.getByRole('table').first();
    await expect(table.getByText(`${PREFIX}-${tag}-high`)).toBeVisible();
    await expect(table.getByText(`${PREFIX}-${tag}-vip`)).toBeVisible();
    await expect(table.getByText(`${PREFIX}-${tag}-low`)).toHaveCount(0);
    // the filter is in the address, so the view can be saved and shared
    expect(decodeURIComponent(app.url())).toContain('"m":"or"');
  });
});
