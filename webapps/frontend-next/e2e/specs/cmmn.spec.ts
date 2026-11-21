/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { dialog } from '../support/ui';
import { waitFor, type Api } from '../support/api';

interface CaseInstance {
  id: string;
  caseDefinitionId: string;
}

interface CaseExecution {
  id: string;
  activityId: string;
  active: boolean;
  enabled: boolean;
  disabled: boolean;
}

function main(page: Page) {
  return page.getByRole('main');
}

/** Deploys the case of the tests once per run; the teardown removes it with the other deployments. */
async function deployCase(api: Api): Promise<void> {
  const [existing] = await api.get<{ id: string }[]>('deployment', { name: `${PREFIX}-case` });
  if (!existing) await api.deploy(`${PREFIX}-case`, ['e2e-case.cmmn']);
}

async function createCase(api: Api, businessKey: string): Promise<CaseInstance> {
  return api.post<CaseInstance>('case-definition/key/e2e_case/create', { businessKey, variables: { amount: { value: 1250, type: 'Long' } } });
}

async function planItem(api: Api, caseInstanceId: string, activityId: string): Promise<CaseExecution | undefined> {
  const executions = await api.get<CaseExecution[]>('case-execution', { caseInstanceId });
  return executions.find((execution) => execution.activityId === activityId);
}

/** The row of a plan item in the list of plan items. */
function itemRow(page: Page, name: string) {
  return main(page).getByRole('table', { name: 'Plan items' }).getByRole('row').filter({ hasText: name });
}

test.describe('Cases (CMMN)', () => {
  test.beforeEach(async ({ api }) => {
    await deployCase(api);
  });

  test('a case from the list to its history: plan items started and completed by hand, a task completed in the Tasklist', async ({ app, api }) => {
    test.setTimeout(150_000);
    const businessKey = `${PREFIX}-case-${Date.now()}`;
    const instance = await createCase(api, businessKey);

    // list -> definition: the case, its diagram and the new instance
    await open(app, 'cockpit/cases');
    await expect(main(app).getByRole('heading', { name: 'Case definitions', level: 1 })).toBeVisible();
    await main(app).getByRole('table', { name: 'Case definitions' }).getByRole('link', { name: 'E2E claim case' }).click();
    await expect(main(app).getByRole('heading', { name: 'E2E claim case', level: 1 })).toBeVisible();
    await expect(main(app).getByRole('img', { name: /Case diagram with the number of open plan items/ })).toBeVisible();
    await expect(main(app).locator('.cmmn-shape--humanTask', { hasText: 'Review claim' })).toBeVisible();
    const instances = main(app).getByRole('table', { name: 'Open instances of this version' });
    await main(app).getByRole('searchbox', { name: 'Business key (exact)' }).fill(businessKey);
    // the header and the one instance with the key, once the search is in the address
    await expect(app).toHaveURL(/bk=/);
    await expect(instances.getByRole('row')).toHaveCount(2);
    await expect(main(app).locator('[aria-busy="true"]')).toHaveCount(0);
    await instances.getByRole('row').filter({ hasText: businessKey }).getByRole('link').click();
    await expect(app).toHaveURL(new RegExp(`cockpit/cases/instance/${instance.id}`));

    // the instance: its plan items in their states
    await expect(main(app).getByText(businessKey)).toBeVisible();
    await expect(itemRow(app, 'Review claim')).toContainText('Active');
    await expect(itemRow(app, 'Check documents')).toContainText('Enabled');
    await expect(itemRow(app, 'Settlement')).toContainText('Available');
    await expect(itemRow(app, 'Claim approved')).toContainText('Available');
    await expect(main(app).locator('[data-element-id="PI_review"]')).toHaveClass(/is-active/);

    // the manual plan item is disabled and enabled again, started, then completed after a
    // confirmation (the active review keeps the case from completing meanwhile)
    await itemRow(app, 'Check documents').getByRole('button', { name: 'Disable: Check documents' }).click();
    await expect(itemRow(app, 'Check documents')).toContainText('Disabled');
    await itemRow(app, 'Check documents').getByRole('button', { name: 'Enable again: Check documents' }).click();
    await expect(itemRow(app, 'Check documents')).toContainText('Enabled');
    await itemRow(app, 'Check documents').getByRole('button', { name: 'Start: Check documents' }).click();
    await expect(itemRow(app, 'Check documents')).toContainText('Active');
    await itemRow(app, 'Check documents').getByRole('button', { name: 'Complete: Check documents' }).click();
    await expect(dialog(app)).toContainText('“Check documents” is completed without a form');
    await dialog(app).getByRole('button', { name: 'Complete' }).click();
    await expect(itemRow(app, 'Check documents')).toHaveCount(0);
    await expect.poll(async () => planItem(api, instance.id, 'PI_check')).toBeUndefined();

    // the human task of the case is completed in the Tasklist
    await main(app).getByRole('tab', { name: 'User tasks' }).click();
    await main(app).getByRole('link', { name: 'Review claim' }).click();
    const details = app.getByRole('region', { name: 'Task details' });
    await expect(details.getByRole('heading', { name: 'Review claim' })).toBeVisible();
    await expect(details.getByText('E2E claim case')).toBeVisible();
    await expect(details.getByRole('link', { name: 'Open the case in Cockpit' })).toBeVisible();
    await details.getByRole('button', { name: 'Claim' }).first().click();
    await expect(details.getByRole('button', { name: 'Complete' })).toBeEnabled();
    await details.getByRole('button', { name: 'Complete' }).click();
    await expect.poll(async () => (await api.get<unknown[]>('task', { caseInstanceId: instance.id, assignee: user })).length).toBe(0);

    // back in the case: the stage started and the milestone occurred; the stage, whose manual
    // task waits to be started, is terminated after a confirmation
    await open(app, `cockpit/cases/instance/${instance.id}`);
    await expect(itemRow(app, 'Settlement')).toContainText('Active');
    await expect(itemRow(app, 'Pay out')).toContainText('Enabled');
    await expect(itemRow(app, 'Claim approved')).toHaveCount(0);
    await itemRow(app, 'Settlement').getByRole('button', { name: 'Terminate: Settlement' }).click();
    await expect(dialog(app)).toContainText('This cannot be undone');
    await dialog(app).getByRole('button', { name: 'Terminate' }).click();

    // nothing is left open: the case completed by itself and is closed now
    await expect(main(app).locator('.page-header').getByText('Completed', { exact: true })).toBeVisible();
    await expect(main(app).getByRole('button', { name: 'Complete case' })).toHaveCount(0);
    await main(app).getByRole('button', { name: 'Close case' }).click();
    await dialog(app).getByRole('button', { name: 'Close case' }).click();
    await expect(main(app).getByText(/This case instance is closed/)).toBeVisible();
    await expect(main(app).locator('[data-element-id="PI_approved"]')).toHaveClass(/is-done/);
    await expect(main(app).locator('[data-element-id="PI_settle"]')).toHaveClass(/is-terminated/);

    // the history of the case and of its plan items
    await main(app).getByRole('tab', { name: 'History' }).click();
    const activities = main(app).getByRole('table', { name: 'History' });
    await expect(activities.getByRole('row').filter({ hasText: 'Check documents' })).toContainText('Completed');
    await expect(activities.getByRole('row').filter({ hasText: 'Claim approved' })).toContainText('Completed');
    await expect(activities.getByRole('row').filter({ hasText: 'Settlement' })).toContainText('Terminated');
    await main(app).getByRole('tab', { name: 'Variables' }).click();
    await expect(main(app).getByRole('table', { name: 'Variables' })).toContainText('1250');

    await open(app, 'cockpit/case-history');
    await main(app).getByRole('searchbox', { name: 'Search by business key' }).fill(businessKey);
    const history = main(app).getByRole('table', { name: 'Case history' });
    await expect(history.getByRole('row').filter({ hasText: businessKey })).toContainText('Closed');
  });

  test('finds a case instance from the search and shows a case model of a deployment', async ({ app, api }) => {
    const businessKey = `${PREFIX}-case-find-${Date.now()}`;
    const instance = await createCase(api, businessKey);
    await waitFor(async () => (await planItem(api, instance.id, 'PI_check')) ?? null, 'the plan items of the new case');

    await open(app, 'cockpit');
    await app.getByRole('button', { name: 'Find a command' }).click();
    const palette = app.getByRole('dialog');
    await palette.getByRole('combobox').fill(instance.id);
    const hit = palette.getByRole('option', { name: new RegExp(businessKey) });
    await expect(hit).toBeVisible();
    await hit.click();
    await expect(app).toHaveURL(new RegExp(`cockpit/cases/instance/${instance.id}`));
    await expect(itemRow(app, 'Check documents')).toBeVisible();

    const [deployment] = await api.get<{ id: string }[]>('deployment', { name: `${PREFIX}-case` });
    await open(app, `cockpit/deployments/${deployment?.id}`);
    await expect(main(app).locator('.cmmn-shape--stage', { hasText: 'Settlement' })).toBeVisible();
    await main(app).getByRole('link', { name: /E2E claim case/ }).click();
    await expect(main(app).getByRole('heading', { name: 'E2E claim case', level: 1 })).toBeVisible();
  });
});
