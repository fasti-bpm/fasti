/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX } from '../support/env';
import { fixturesDir, waitFor, type Api, type Task } from '../support/api';
import { createFilter } from '../support/data';

/** The embedded HTML form runs in a sandboxed frame inside the task or start view. */
function formFrame(page: Page, region: string) {
  return page.getByRole('region', { name: region }).frameLocator('iframe');
}

async function invoiceTask(api: Api, invoiceNumber: string, key: string): Promise<Task> {
  return waitFor(async () => {
    const [instance] = await api.get<{ id: string }[]>('process-instance', {
      variables: `invoiceNumber_eq_${invoiceNumber}`,
    });
    if (!instance) {
      return null;
    }
    return (await api.tasks(instance.id)).find((task) => task.taskDefinitionKey === key);
  }, `task ${key} of invoice ${invoiceNumber}`);
}

/** Opens a task of the invoice, claims it and checks the download of the invoice document. */
async function openClaimAndDownload(page: Page, filterId: string, task: Task): Promise<void> {
  await open(page, `tasklist?filter=${filterId}&task=${task.id}`);
  const details = page.getByRole('region', { name: 'Task details' });
  await expect(details.getByRole('heading', { level: 1 })).toBeVisible();
  await details.getByRole('button', { name: 'Claim', exact: true }).first().click();
  await expect(details.getByRole('button', { name: 'Unclaim' })).toBeVisible();

  const frame = formFrame(page, 'Task details');
  const link = frame.locator('a[cam-file-download]');
  await expect(link).toBeVisible();
  const download = page.waitForEvent('download', { timeout: 30_000 });
  await link.click();
  const file = await download;
  expect(file.suggestedFilename()).toBe('invoice.pdf');
  const path = await file.path();
  expect(readFileSync(path)).toEqual(readFileSync(join(fixturesDir, 'invoice.pdf')));
}

/** Starts an invoice with the embedded start form, uploading the invoice document. */
async function startInvoice(app: Page, invoiceNumber: string): Promise<void> {
  await open(app, 'processes');
  await app.getByRole('list', { name: 'Start process' }).getByRole('button', { name: /^Invoice Receipt/ }).click();
  const start = app.getByRole('region', { name: 'Start' });
  await expect(start.getByRole('heading', { name: 'Invoice Receipt' })).toBeVisible();
  const startForm = formFrame(app, 'Start');
  await startForm.locator('#invoiceDocumentUpload').setInputFiles(join(fixturesDir, 'invoice.pdf'));
  await startForm.locator('#creditor').fill('E2E Supplies Ltd.');
  await startForm.locator('#amount').fill('30.00');
  await startForm.locator('select[cam-variable-name="invoiceCategory"]').selectOption('Misc');
  await startForm.locator('#invoiceNumber').fill(invoiceNumber);
  await start.getByRole('button', { name: 'Start' }).click();
}

test('the invoice process runs end to end with embedded forms', async ({ app, api }) => {
  test.slow();
  const invoiceNumber = `${PREFIX}-${Date.now()}`;
  const filterId = await createFilter(api, `${PREFIX} invoices ${Date.now()}`, 'invoice');
  await startInvoice(app, invoiceNumber);

  // approve
  const approve = await invoiceTask(api, invoiceNumber, 'approveInvoice');
  await openClaimAndDownload(app, filterId, approve);
  const approveForm = formFrame(app, 'Task details');
  await expect(approveForm.locator('input[cam-variable-name="creditor"]')).toHaveValue('E2E Supplies Ltd.');
  await approveForm.locator('input[cam-variable-name="approved"]').check();
  await app.getByRole('region', { name: 'Task details' }).getByRole('button', { name: 'Complete' }).click();

  // prepare the bank transfer
  const transfer = await invoiceTask(api, invoiceNumber, 'prepareBankTransfer');
  await openClaimAndDownload(app, filterId, transfer);
  await app.getByRole('region', { name: 'Task details' }).getByRole('button', { name: 'Complete' }).click();

  // the archive step runs asynchronously; then the instance ends
  const [history] = await api.get<{ id: string }[]>('history/process-instance', {
    variables: `invoiceNumber_eq_${invoiceNumber}`,
  });
  expect(history).toBeTruthy();
  await waitFor(
    async () => (await api.get<{ state: string }>(`history/process-instance/${history?.id}`)).state === 'COMPLETED',
    'the invoice instance to end',
    60_000,
  );
  const archived = await api.get<unknown[]>('history/activity-instance', {
    processInstanceId: history?.id ?? '',
    activityId: 'ServiceTask_1',
    finished: true,
  });
  expect(archived).toHaveLength(1);
});

test('a task claimed while its embedded form still loads can be completed', async ({ app, api }) => {
  test.slow();
  const invoiceNumber = `${PREFIX}-${Date.now()}`;
  const filterId = await createFilter(api, `${PREFIX} invoices ${Date.now()}`, 'invoice');
  await startInvoice(app, invoiceNumber);
  const approve = await invoiceTask(api, invoiceNumber, 'approveInvoice');

  // the frame of the form loads its customizations through this page; slowed down, the
  // task is claimed after the frame was told what to render and before its form runs
  await app.route('**/app/tasklist/scripts/config.js*', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 5000));
    await route.continue();
  });
  const frameReady = app.waitForRequest(/scripts\/config\.js/);
  await open(app, `tasklist?filter=${filterId}&task=${approve.id}`);
  await frameReady;
  const details = app.getByRole('region', { name: 'Task details' });
  await details.getByRole('button', { name: 'Claim', exact: true }).first().click();
  await expect(details.getByRole('button', { name: 'Unclaim' })).toBeVisible();

  const form = formFrame(app, 'Task details');
  await expect(form.locator('input[cam-variable-name="creditor"]')).toHaveValue('E2E Supplies Ltd.');
  await form.locator('input[cam-variable-name="approved"]').check();
  await details.getByRole('button', { name: 'Complete' }).click();
  await invoiceTask(api, invoiceNumber, 'prepareBankTransfer');
});
