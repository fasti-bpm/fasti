/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX } from '../support/env';
import { answered, dialog } from '../support/ui';
import { waitFor } from '../support/api';

function main(page: Page) {
  return page.getByRole('main');
}

test.describe('Team work and incident help', () => {
  test('shows the runbook of the failed step and hints for its error', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_incident', { ok: false }, `${PREFIX}-runbook`);
    await waitFor(
      async () => (await api.get<{ id: string }[]>('incident', { processInstanceId: instance.id }))[0],
      'an incident',
      60_000,
    );
    await open(app, `cockpit/processes/instance/${instance.id}?tab=incidents`);
    await main(app).getByRole('button', { name: 'Error details' }).first().click();
    const details = dialog(app);
    await expect(details.getByRole('heading', { name: 'What to check' })).toBeVisible();
    const runbook = details.getByRole('link', { name: /Open runbook/ });
    await expect(runbook).toHaveAttribute('href', 'https://runbooks.example.org/e2e/fragile-work');
    await expect(runbook).toHaveAttribute('rel', 'noopener noreferrer');
    await expect(details.getByText('An expression or script could not be evaluated')).toBeVisible();
    await expect(details.getByRole('heading', { name: 'Stack trace' })).toBeVisible();
  });

  test('adds a business variable as a column of the running instances', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_tasks', { e2eAmount: 4711 }, `${PREFIX}-columns`);
    const [definition] = await api.get<{ id: string }[]>('process-definition', { key: 'e2e_tasks', latestVersion: true });
    await open(app, `cockpit/processes/definition/${definition?.id}`);
    await main(app).getByRole('button', { name: /^Variable columns/ }).click();
    const picker = main(app).getByRole('form', { name: 'Variables shown as columns' });
    // the choice is a preference of the user and may be left from an earlier run
    const clear = picker.getByRole('button', { name: 'Remove all' });
    if (await clear.isVisible()) await clear.click();
    await picker.getByRole('combobox', { name: 'Variable name' }).fill('e2eAmount');
    await picker.getByRole('button', { name: 'Add column', exact: true }).click();
    await picker.getByRole('button', { name: 'Done' }).click();
    await expect(main(app).getByRole('columnheader', { name: 'e2eAmount' })).toBeVisible();
    const row = main(app).getByRole('row').filter({ hasText: `${PREFIX}-columns` });
    await expect(row).toContainText('4711');
    // remembered for the process; preferences reach the server shortly after a change
    await app.waitForTimeout(1500);
    await app.reload();
    await expect(main(app).getByRole('button', { name: 'Variable columns (1)' })).toBeVisible();
    await api.delete(`process-instance/${instance.id}?skipCustomListeners=true`);
  });

  test('counts the open tasks of a group and opens them in the task list', async ({ app, api }) => {
    await api.post('group/create', { id: `${PREFIX}team`, name: 'E2E team', type: 'WORKFLOW' });
    const instance = await api.startProcess('e2e_tasks', {}, `${PREFIX}-workload`);
    const [task] = await api.tasks(instance.id);
    await api.post(`task/${task?.id}/identity-links`, { groupId: `${PREFIX}team`, type: 'candidate' });

    await open(app, 'cockpit/workload');
    const groups = main(app).getByRole('table', { name: 'By candidate group' });
    const open1 = groups.getByRole('link', { name: 'Open tasks of E2E team: 1' });
    await expect(open1).toBeVisible();
    await expect(groups.getByRole('link', { name: 'Unassigned tasks of E2E team: 1' })).toBeVisible();
    await open1.click();
    await expect(app).toHaveURL(/#\/tasklist/);
    await expect(app.getByText('Candidate group: ')).toBeVisible();
    await expect(app.getByRole('option', { name: /E2E review/ }).or(app.getByText('E2E review')).first()).toBeVisible();
    await api.delete(`process-instance/${instance.id}?skipCustomListeners=true`);
  });

  test('reads the numbers of the board with one request', async ({ app }) => {
    const calls: string[] = [];
    app.on('request', (request) => {
      // below the engine of the API: /task/count, /task/workload, ...
      const path = new URL(request.url()).pathname.split('/api/engine/engine/')[1]?.replace(/^[^/]+/, '') ?? '';
      if (path === '/task' || path.startsWith('/task/')) calls.push(`${request.method()} ${path}`);
    });
    const counted = answered(app, 'POST', /\/task\/workload$/);
    await open(app, 'cockpit/workload');
    expect((await counted).ok()).toBe(true);
    await expect(main(app).getByRole('table', { name: 'By person' })).toBeVisible();
    await expect(main(app).getByRole('link', { name: /^Open tasks/ }).first()).toBeVisible();
    // the totals and every row come with the aggregate: no task query per group or person
    expect(calls).toEqual(['POST /task/workload']);
  });

  test('offers variable columns on the history once a process is chosen', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_tasks', { e2eAmount: 815 }, `${PREFIX}-hcolumns`);
    await open(app, 'cockpit/history');
    await expect(main(app).getByText('Choose a process to add variable columns.')).toBeVisible();
    await main(app).getByRole('combobox', { name: 'Process', exact: true }).fill('e2e_tasks');
    await app.keyboard.press('Enter');
    await main(app).getByRole('button', { name: /^Variable columns/ }).click();
    const picker = main(app).getByRole('form', { name: 'Variables shown as columns' });
    const clear = picker.getByRole('button', { name: 'Remove all' });
    if (await clear.isVisible()) await clear.click();
    await picker.getByRole('combobox', { name: 'Variable name' }).fill('e2eAmount');
    await picker.getByRole('button', { name: 'Add column', exact: true }).click();
    await picker.getByRole('button', { name: 'Done' }).click();
    const row = main(app).getByRole('row').filter({ hasText: `${PREFIX}-hcolumns` });
    await expect(row).toContainText('815');
    await api.delete(`process-instance/${instance.id}?skipCustomListeners=true`);
  });

  test('finds a group on the workload board by search', async ({ app, api }) => {
    await api.post('group/create', { id: `${PREFIX}findme`, name: 'E2E findable crew', type: 'WORKFLOW' }).catch(() => undefined);
    await open(app, 'cockpit/workload');
    await main(app).getByRole('checkbox', { name: /Hide/ }).uncheck();
    await main(app).getByRole('searchbox', { name: 'Search groups' }).fill('findable');
    const groups = main(app).getByRole('table', { name: 'By candidate group' });
    await expect(groups.getByText('E2E findable crew')).toBeVisible();
    await expect(groups.getByRole('row')).toHaveCount(2);
  });

  test('finds every section in the command palette and gives focus back on close', async ({ app }) => {
    await open(app, 'cockpit');
    const opener = app.getByRole('button', { name: 'Find a command' });
    await opener.click();
    const palette = app.getByRole('dialog');
    await palette.getByRole('combobox').fill('workload');
    await expect(palette.getByRole('option', { name: /Team workload/ })).toBeVisible();
    await palette.getByRole('button', { name: 'Close' }).click();
    await expect(palette).toBeHidden();
    await expect(opener).toBeFocused();
  });

  test('keeps the diagram tools beneath a dialog of the modeler', async ({ app }) => {
    await open(app, 'cockpit/modeler');
    await app.locator('.djs-palette .entry.bpmn-icon-gateway-none').click();
    const box = await app.locator('.djs-container').boundingBox();
    if (!box) throw new Error('no canvas');
    await app.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await expect(app.locator('.djs-context-pad')).toBeVisible();
    await main(app).getByRole('button', { name: 'Decision', exact: true }).click();
    const confirm = dialog(app);
    const discard = confirm.getByRole('button', { name: /Discard/ });
    await expect(discard).toBeVisible();
    // the button is on top where it is drawn, not covered by the context pad
    const at = await discard.boundingBox();
    if (!at) throw new Error('no button');
    const hit = await app.evaluate(({ x, y }) => document.elementFromPoint(x, y)?.closest('button')?.textContent ?? '', {
      x: at.x + at.width / 2,
      y: at.y + at.height / 2,
    });
    expect(hit).toMatch(/Discard/);
    await confirm.getByRole('button', { name: 'Back' }).click();
  });
});

