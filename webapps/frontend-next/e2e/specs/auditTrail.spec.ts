/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync } from 'node:fs';
import { expect, open, requireFeature, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { waitFor } from '../support/api';

interface AuditStatus {
  enabled: boolean;
}

interface AuditEvent {
  type: string;
  action: string | null;
  actor: string | null;
  hash: string;
  previousHash: string;
}

test.describe('Audit trail', () => {
  test('records who did what to a case, verifies it and exports it', async ({ app, api }) => {
    const status = await api.get<AuditStatus>('audit');
    requireFeature(status.enabled, 'the audit trail is not enabled on this server (fasti.audit.enabled)');
    const businessKey = `${PREFIX}-audit-${Date.now()}`;
    await api.deploy(`${PREFIX}-audit`, ['e2e-audit.bpmn']);
    const instance = await api.startProcess('e2e_audit', { assignee: user }, businessKey);
    const task = await waitFor(async () => (await api.tasks(instance.id))[0], `the task of ${instance.id}`);
    await api.post(`task/${task.id}/complete`, {});

    // the engine recorded the start, the task and its completion by the signed-in user
    const records = await api.get<AuditEvent[]>(`audit/event?businessKey=${businessKey}&sortOrder=asc&maxResults=100`);
    expect(records.map((record) => `${record.type}/${record.action}`)).toEqual(
      expect.arrayContaining(['process/start', 'task/create', 'task/complete', 'process/end']),
    );
    expect(records.find((record) => record.type === 'task' && record.action === 'complete')?.actor).toBe(user);

    // the page shows them, filtered by the business key
    await open(app, `cockpit/audit-trail?bk=${businessKey}`);
    await expect(app.getByRole('heading', { name: 'Audit trail', level: 1 })).toBeVisible();
    const table = app.getByRole('table', { name: 'Records of the audit trail' });
    await expect(table.getByRole('row').filter({ hasText: 'E2E approve payment' }).first()).toBeVisible();
    await expect(table.getByRole('cell', { name: 'complete E2E approve payment' })).toBeVisible();
    const completed = table.getByRole('row').filter({ hasText: 'complete' }).filter({ hasText: 'E2E approve payment' });
    await completed.getByText('Show').click();
    await expect(completed.getByText(/^Record \d+ of chain /)).toBeVisible();

    // verifying finds the trail intact
    await app.getByRole('button', { name: 'Verify integrity' }).click();
    const intact = /^Intact: [\d,]+ records? in [\d,]+ chains? verified$/;
    await expect(app.getByRole('note').getByText(intact)).toBeVisible();
    // and it is announced: by a live region, next to the notice
    await expect(app.getByRole('status').getByText(intact)).toHaveCount(1);

    // the export is a file of the server and is itself recorded
    const download = app.waitForEvent('download');
    await app.getByRole('button', { name: 'Export CSV' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toMatch(/^audit-trail-\d{8}-\d{6}\.csv$/);
    const csv = readFileSync(await file.path(), 'utf8');
    expect(csv).toContain(businessKey);
    expect(csv.split('\r\n')[0]).toContain('id,chainId,sequence,timestamp');
    await expect(app.getByText('Exported; the export is on record in the audit trail.', { exact: true })).toBeVisible();
    const exports = await api.get<AuditEvent[]>('audit/event?type=audit&action=export&maxResults=5');
    expect(exports[0]?.actor).toBe(user);

    // the history of the instance has the records in a tab of its own
    await open(app, `cockpit/history/instance/${instance.id}`);
    await app.getByRole('tab', { name: 'Audit trail' }).click();
    const tab = app.getByRole('tabpanel', { name: 'Audit trail' });
    await expect(tab.getByRole('table', { name: 'Records of the audit trail' }).getByRole('row').filter({ hasText: 'start' }).first()).toBeVisible();
  });
});
