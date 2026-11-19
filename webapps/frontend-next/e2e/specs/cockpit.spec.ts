/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { join } from 'node:path';
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { dialog } from '../support/ui';
import { fixturesDir, waitFor, type Api } from '../support/api';

interface Definition {
  id: string;
  key: string;
  version: number;
}

async function definition(api: Api, key: string, version: number): Promise<Definition> {
  const [found] = await api.get<Definition[]>('process-definition', { key, version });
  if (!found) {
    throw new Error(`no version ${version} of ${key}`);
  }
  return found;
}

/** Waits until the job executor turned the failing job of an instance into an incident. */
async function incidentOf(api: Api, processInstanceId: string): Promise<{ id: string }> {
  return waitFor(
    async () => (await api.get<{ id: string }[]>('incident', { processInstanceId }))[0],
    `an incident in ${processInstanceId}`,
    60_000,
  );
}

async function startFailing(api: Api, businessKey: string): Promise<string> {
  const instance = await api.startProcess('e2e_incident', { ok: false }, businessKey);
  await incidentOf(api, instance.id);
  return instance.id;
}

/** The instance passed the failing activity once the user task after it exists. */
async function passedWork(api: Api, processInstanceId: string): Promise<void> {
  await waitFor(
    async () => (await api.tasks(processInstanceId)).some((task) => task.taskDefinitionKey === 'after'),
    `instance ${processInstanceId} to pass the failing activity`,
    60_000,
  );
}

function main(page: Page) {
  return page.getByRole('main');
}

test.describe('Cockpit', () => {
  test('shows the dashboard', async ({ app }) => {
    await open(app, 'cockpit');
    await expect(main(app).getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
    await expect(main(app).getByRole('link', { name: /Running instances \d+/ })).toBeVisible();
    await expect(main(app).getByRole('link', { name: /Deployed processes \d+/ })).toBeVisible();
    await main(app).getByRole('link', { name: /Deployed processes/ }).click();
    await expect(main(app).getByRole('table', { name: 'Processes' })).toBeVisible();
  });

  test('shows a process definition with its diagram and versions', async ({ app }) => {
    await open(app, 'cockpit/processes');
    await main(app).getByRole('searchbox', { name: /Search processes/ }).fill('E2E migrate');
    // the list settles on the search first; a link of the list before it may be replaced while clicked
    await expect(main(app).getByText('1 process', { exact: true })).toBeVisible();
    await main(app).getByRole('link', { name: 'E2E migrate' }).click();
    await expect(main(app).getByRole('heading', { name: 'E2E migrate', level: 1 })).toBeVisible();
    const diagram = main(app).getByRole('img', { name: /Process diagram/ });
    await expect(diagram).toBeVisible();
    await expect(diagram.locator('svg [data-element-id="stepC"]').first()).toBeAttached();
    await main(app).getByRole('combobox', { name: 'Version' }).selectOption({ label: '1' });
    await expect(diagram.locator('svg [data-element-id="stepC"]')).toHaveCount(0);
    await expect(diagram.locator('svg [data-element-id="stepB"]').first()).toBeAttached();
  });

  test('shows an instance and modifies it', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_tasks', { e2eAmount: 42 }, `${PREFIX}-modify`);
    await open(app, `cockpit/processes/instance/${instance.id}`);
    await expect(main(app).getByRole('heading', { name: 'E2E tasks', level: 1 })).toBeVisible();
    await expect(main(app).getByRole('img', { name: /Process diagram/ })).toBeVisible();
    await expect(main(app).getByRole('list', { name: 'Running activities' })).toContainText('E2E review');
    await expect(main(app).getByRole('tabpanel', { name: 'Variables' })).toContainText('e2eAmount');

    await main(app).getByRole('button', { name: 'Modify' }).click();
    const modify = dialog(app);
    await modify.getByRole('combobox', { name: 'Instruction' }).selectOption({ index: 0 });
    await modify.getByRole('combobox', { name: 'Activity' }).selectOption('end');
    await modify.getByRole('button', { name: 'Add', exact: true }).click();
    await modify.getByRole('combobox', { name: 'Instruction' }).selectOption({ index: 2 });
    await modify.getByRole('combobox', { name: 'Activity' }).selectOption('review');
    await modify.getByRole('button', { name: 'Add', exact: true }).click();
    await modify.getByRole('textbox', { name: 'Annotation' }).fill(`${PREFIX} modification`);
    await modify.getByRole('button', { name: 'Review' }).click();
    await modify.getByRole('button', { name: /Modify|Apply|Run/ }).last().click();
    await expect(modify).toBeHidden();
    // the token was started before the end event and the task was cancelled
    await expect.poll(async () => (await api.tasks(instance.id)).length).toBe(0);
    const ended = await api.get<unknown[]>('history/activity-instance', { processInstanceId: instance.id, activityId: 'end' });
    expect(ended).toHaveLength(1);
    expect(await api.get<unknown[]>('process-instance', { processInstanceIds: instance.id })).toHaveLength(0);
  });

  test('migrates instances to another version', async ({ app, api }) => {
    const v1 = await definition(api, 'e2e_migrate', 1);
    const v2 = await definition(api, 'e2e_migrate', 2);
    expect(await api.get<unknown[]>('process-instance', { processDefinitionId: v1.id })).not.toHaveLength(0);

    await open(app, `cockpit/processes/definition/${v1.id}`);
    await main(app).getByRole('link', { name: 'Migrate' }).click();
    await expect(main(app).getByRole('heading', { name: 'Migrate instances', level: 1 })).toBeVisible();
    await main(app).getByRole('combobox', { name: 'Source version' }).selectOption(v1.id);
    await main(app).getByRole('combobox', { name: 'Target version' }).selectOption(v2.id);
    await main(app).getByRole('combobox', { name: 'Target of Step A' }).selectOption('stepA');
    await main(app).getByRole('combobox', { name: 'Target of Step B' }).selectOption('stepB');
    await main(app).getByRole('button', { name: 'Review' }).click();
    await dialog(app).getByRole('button', { name: /Migrate/ }).last().click();
    await expect
      .poll(async () => (await api.get<unknown[]>('process-instance', { processDefinitionId: v1.id })).length, {
        timeout: 60_000,
      })
      .toBe(0);
    expect(await api.get<unknown[]>('process-instance', { processDefinitionId: v2.id })).not.toHaveLength(0);
  });

  test('restarts a finished instance from the history', async ({ app, api }) => {
    // restarting works on one version: it is offered in the history of a definition
    const v1 = await definition(api, 'e2e_tasks', 1);
    const [done] = await api.get<{ id: string }[]>('history/process-instance', {
      processDefinitionId: v1.id,
      processInstanceBusinessKey: `${PREFIX}-done`,
      finished: true,
    });
    expect(done).toBeTruthy();
    await open(app, `cockpit/processes/definition/${v1.id}`);
    await main(app).getByRole('tab', { name: 'History' }).click();
    await main(app).getByRole('textbox', { name: 'Business key' }).fill(`${PREFIX}-done`);
    await main(app).getByRole('textbox', { name: 'Business key' }).press('Enter');
    const row = main(app).getByRole('row', { name: new RegExp(done?.id ?? '-') });
    // changing the filters clears the selection; the business key filter applies with a delay
    await expect(main(app).getByRole('table', { name: 'History' }).getByRole('row')).toHaveCount(2);
    await expect(async () => {
      await row.getByRole('checkbox').check({ timeout: 2_000 });
    }).toPass();
    await main(app).getByRole('button', { name: /Restart/ }).click();
    const restart = dialog(app);
    await expect(restart).toBeVisible();
    // one instance reads in the singular (counts used to be passed as text)
    await expect(restart).toHaveAccessibleName(/1 instance$/);
    // the dialog proposes to start before the start event
    await expect(restart.getByRole('list', { name: 'Instructions' }).getByRole('listitem')).toHaveCount(1);
    await restart.getByRole('checkbox', { name: /Run as a batch/ }).check();
    await restart.getByRole('button', { name: 'Review' }).click();
    await restart.getByRole('button', { name: /^Restart 1 instance/ }).click();
    await expect(restart).toBeHidden();
    await expect
      .poll(async () => (await api.get<unknown[]>('process-instance', { processDefinitionId: v1.id, businessKey: `${PREFIX}-done` })).length, {
        timeout: 60_000,
      })
      .toBe(1);
  });

  test('retries an incident', async ({ app, api }) => {
    const instanceId = await startFailing(api, `${PREFIX}-incident`);
    await open(app, 'cockpit/incidents');
    await main(app).getByRole('textbox', { name: 'Process key' }).fill('e2e_incident');
    const row = main(app).getByRole('row').filter({ has: app.getByRole('link', { name: instanceId.slice(0, 8), exact: false }) });
    await expect(row).toContainText('e2eUndefinedValue');
    // fix the cause, then retry
    await api.put(`process-instance/${instanceId}/variables/ok`, { value: true, type: 'Boolean' });
    await row.getByRole('button', { name: 'Retry' }).click();
    const confirm = dialog(app);
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', { name: /Retry/ }).last().click();
    await expect(confirm).toBeHidden();
    await passedWork(api, instanceId);
    expect(await api.get<unknown[]>('incident', { processInstanceId: instanceId })).toHaveLength(0);
  });

  test('retries a failed job', async ({ app, api }) => {
    const instanceId = await startFailing(api, `${PREFIX}-job`);
    await api.put(`process-instance/${instanceId}/variables/ok`, { value: true, type: 'Boolean' });
    await open(app, 'cockpit/jobs');
    const row = main(app).getByRole('row').filter({ has: app.getByRole('link', { name: instanceId.slice(0, 8), exact: false }) });
    await expect(row).toBeVisible();
    await row.getByRole('checkbox').check();
    await main(app).getByRole('button', { name: /Retry selected \(1\)/ }).click();
    const confirm = dialog(app);
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', { name: /Retry/ }).last().click();
    await expect(confirm).toBeHidden();
    await passedWork(api, instanceId);
  });

  test('lists external tasks', async ({ app, api }) => {
    const instance = await api.startProcess('e2e_external', {}, `${PREFIX}-external`);
    await open(app, 'cockpit/external-tasks');
    await main(app).getByRole('combobox', { name: 'Topic' }).selectOption('e2e-topic');
    const row = main(app).getByRole('row').filter({ has: app.getByRole('link', { name: instance.id.slice(0, 8), exact: false }) });
    await expect(row).toContainText('e2e-topic');
    await expect(row.getByRole('checkbox')).toBeVisible();
  });

  test('shows the history of instances and the heat map', async ({ app, api }) => {
    const v1 = await definition(api, 'e2e_tasks', 1);
    await open(app, 'cockpit/history');
    await main(app).getByRole('combobox', { name: 'Process', exact: true }).fill('e2e_tasks');
    await app.keyboard.press('Enter');
    await main(app).getByRole('combobox', { name: 'State' }).selectOption({ index: 2 });
    const table = main(app).getByRole('table', { name: 'History' });
    await expect(table.getByRole('row').filter({ hasText: 'E2E tasks' }).first()).toBeVisible();
    await expect(table.getByRole('row').filter({ hasText: 'E2E migrate' })).toHaveCount(0);

    await open(app, `cockpit/processes/definition/${v1.id}`);
    await main(app).getByRole('tab', { name: 'Statistics' }).click();
    await expect(main(app).getByRole('tabpanel', { name: 'Statistics' })).toBeVisible();
    // the heat map colours the activities by how often they ran
    await expect(main(app).locator('[data-element-id="review"][class*="heat-"]').first()).toBeAttached();
  });

  test('shows the decision history', async ({ app }) => {
    await open(app, 'cockpit/decision-history');
    await main(app).getByRole('textbox', { name: 'Decision key' }).fill('e2e_decision');
    const table = main(app).getByRole('table', { name: 'Decision history' });
    await expect(table.getByRole('row').filter({ hasText: 'E2E discount' })).toHaveCount(2);
    await table.getByRole('row').filter({ hasText: 'E2E discount' }).first().getByRole('link').click();
    await expect(main(app)).toContainText(/high|low/);
  });

  test('lists batches and the operation log', async ({ app, api }) => {
    // a batch of its own: suspend one instance in the background
    const instance = await api.startProcess('e2e_external', {}, `${PREFIX}-batch`);
    const batch = await api.post<{ id: string; type: string }>('process-instance/suspended-async', {
      processInstanceIds: [instance.id],
      suspended: true,
    });
    await expect
      .poll(async () => (await api.get<{ suspended: boolean }>(`process-instance/${instance.id}`)).suspended, { timeout: 60_000 })
      .toBe(true);
    await open(app, 'cockpit/batches');
    await expect(main(app).getByRole('heading', { name: 'Batches', level: 1 })).toBeVisible();
    await expect(main(app).getByRole('heading', { name: 'Finished', level: 2 })).toBeVisible();
    await expect(main(app).getByText(batch.id.slice(0, 8)).first()).toBeVisible();

    await open(app, 'cockpit/audit');
    await expect(main(app).getByRole('heading', { level: 1 })).toBeVisible();
    await expect(main(app).getByRole('table').first()).toContainText(user);
  });

  test('deploys a BPMN model from the modeler', async ({ app, api }) => {
    await open(app, 'cockpit/modeler');
    await expect(main(app).getByRole('application', { name: 'Model editor' })).toBeVisible();
    const chooser = app.waitForEvent('filechooser');
    await main(app).getByRole('button', { name: 'Open file' }).click();
    await (await chooser).setFiles(join(fixturesDir, 'e2e-modeler.bpmn'));
    await expect(main(app).locator('[data-element-id="start"]').first()).toBeAttached();
    await main(app).getByRole('button', { name: 'Deploy', exact: true }).click();
    const deploy = dialog(app);
    const name = deploy.getByRole('textbox').first();
    await name.fill(`${PREFIX}-modeler`);
    await deploy.getByRole('button', { name: 'Deploy' }).last().click();
    await expect(deploy).toBeHidden();
    await expect
      .poll(async () => (await api.get<unknown[]>('process-definition', { key: 'e2e_modeler' })).length)
      .toBe(1);
  });
});
