/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Page } from '@playwright/test';
import { expect, open, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { dialog } from '../support/ui';
import { createFilter } from '../support/data';
import { waitFor, type Api, type Task } from '../support/api';

const SECRET = 'Secret-e2e-1';

/** `yyyy-MM-ddTHH:mm` of an `<input type="datetime-local">`, in the time zone of the browser (UTC). */
function inputValue(date: Date): string {
  return date.toISOString().substring(0, 16);
}

/** The REST date format, in UTC. */
function engineDate(date: Date): string {
  return date.toISOString().replace('Z', '+0000');
}

async function createUser(api: Api, id: string, firstName: string, lastName: string): Promise<void> {
  await api.post('user/create', {
    profile: { id, firstName, lastName, email: `${id}@example.org` },
    credentials: { password: SECRET },
  });
}

async function onlyTask(api: Api, instanceId: string): Promise<Task> {
  return waitFor(async () => (await api.tasks(instanceId))[0], `the task of ${instanceId}`);
}

async function noHorizontalScroll(page: Page): Promise<boolean> {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
}

test.describe('Absence', () => {
  test.beforeEach(async ({ api }) => {
    const status = await api.get<{ enabled: boolean }>(`user/${user}/absence`);
    test.skip(!status.enabled, 'absences are not enabled on this server (fasti.absence.enabled)');
  });

  test('a user records an absence in the preferences and sees it in the tasklist', async ({ app, api }) => {
    const substitute = `${PREFIX}sub${Date.now()}`;
    await createUser(api, substitute, 'Sam', 'Substitute');
    try {
      await open(app, 'preferences');
      const section = app.getByRole('region', { name: 'Absence' });
      await expect(section).toBeVisible();
      // nothing to save without an end and a substitute
      await section.getByRole('button', { name: 'Save absence' }).click();
      await expect(section.getByText('Choose when the absence ends.')).toBeVisible();

      const until = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
      await section.getByLabel(/^Until/).fill(inputValue(until));
      await section.getByRole('combobox', { name: /^Substitute/ }).fill(substitute);
      await section.getByLabel('Note for your substitute').fill('Handover notes in the team wiki');
      await section.getByRole('button', { name: 'Save absence' }).click();
      await expect(app.getByText('Absence saved')).toBeVisible();
      await expect(section.getByText(/Tasks go to Sam Substitute/)).toBeVisible();
      const stored = await api.get<{ status: string; substituteId: string; note: string }>(`user/${user}/absence`);
      expect(stored).toMatchObject({ status: 'active', substituteId: substitute, note: 'Handover notes in the team wiki' });

      // the form fits a phone
      await app.setViewportSize({ width: 375, height: 800 });
      await expect.poll(() => noHorizontalScroll(app)).toBe(true);
      await app.setViewportSize({ width: 1440, height: 900 });

      // the tasklist says so and leads back to the settings
      await open(app, 'tasklist');
      const banner = app.getByRole('status').filter({ hasText: 'You are away until' });
      await expect(banner).toContainText('Sam Substitute');
      await banner.getByRole('link', { name: 'Absence settings' }).click();
      await expect(app.getByRole('region', { name: 'Absence' })).toBeVisible();

      await app.getByRole('region', { name: 'Absence' }).getByRole('button', { name: 'End absence' }).click();
      await expect(app.getByText('Absence ended')).toBeVisible();
      await expect.poll(async () => (await api.get<{ status: string }>(`user/${user}/absence`)).status).toBe('none');
    } finally {
      await api.delete(`user/${user}/absence`);
      await api.delete(`user/${substitute}`);
    }
  });

  test('an administrator sets the absence of a user and hands the open tasks to the substitute', async ({ app, api }) => {
    const absent = `${PREFIX}absent${Date.now()}`;
    await createUser(api, absent, 'Anna', 'Absent');
    await api.deploy(`${PREFIX}-absence`, ['e2e-absence.bpmn']);
    const filterId = await createFilter(api, `${PREFIX} absence ${Date.now()}`, 'e2e_absence');
    try {
      // a task of the user from before the absence
      const before = await api.startProcess('e2e_absence', { assignee: absent }, `${PREFIX}-absence-before`);
      expect((await onlyTask(api, before.id)).assignee).toBe(absent);

      await open(app, `admin/users/${absent}`);
      await app.getByRole('tab', { name: 'Absence' }).click();
      const section = app.getByRole('region', { name: 'Absence' });
      await expect(section.getByText(/While Anna Absent is away/)).toBeVisible();
      await section.getByLabel(/^Until/).fill(inputValue(new Date(Date.now() + 2 * 24 * 60 * 60 * 1000)));
      await section.getByRole('combobox', { name: /^Substitute/ }).fill(user);
      await section.getByRole('button', { name: 'Save absence' }).click();
      await expect(app.getByText('Absence saved')).toBeVisible();

      // the task from before is handed over on request
      await section.getByRole('button', { name: 'Hand over the open task now' }).click();
      const confirm = dialog(app);
      await expect(confirm).toContainText('Anna Absent');
      await confirm.getByRole('button', { name: 'Hand over' }).click();
      await expect(app.getByText(/1 task handed over to/)).toBeVisible();
      await expect.poll(async () => (await onlyTask(api, before.id)).assignee).toBe(user);

      // a new task for the absent user goes to the substitute right away
      const during = await api.startProcess('e2e_absence', { assignee: absent }, `${PREFIX}-absence-during`);
      const task = await onlyTask(api, during.id);
      expect(task.assignee).toBe(user);
      const comments = await api.get<{ message: string }[]>(`task/${task.id}/comment`);
      expect(comments.map((comment) => comment.message)).toEqual([`Assigned to ${user} as substitute for ${absent}, who is absent.`]);

      // the substitute sees for whom they work
      await open(app, `tasklist?filter=${filterId}`);
      await expect(app.getByRole('status').filter({ hasText: 'You stand in for Anna Absent' })).toBeVisible();
      const card = app.getByRole('listbox', { name: 'Tasks' }).getByRole('option').first();
      await expect(card).toContainText('For Anna Absent');
      await open(app, `tasklist?filter=${filterId}&task=${task.id}`);
      const details = app.getByRole('region', { name: 'Task details' });
      await expect(details.getByText('Assigned to you as substitute for Anna Absent.')).toBeVisible();

      // once the substitute passes the task on, it is no longer held for the absent user
      await api.post(`task/${task.id}/assignee`, { userId: null });
      await expect.poll(async () => (await api.get<unknown[]>('variable-instance', { taskIdIn: task.id })).length).toBe(0);
    } finally {
      // deleting the user removes the absence as well
      await api.delete(`user/${absent}`);
      await api.delete(`filter/${filterId}`);
    }
  });

  test('refuses a substitute that leads back to the absent user', async ({ api }) => {
    const first = `${PREFIX}cyclea${Date.now()}`;
    const second = `${PREFIX}cycleb${Date.now()}`;
    await createUser(api, first, 'Cycle', 'First');
    await createUser(api, second, 'Cycle', 'Second');
    try {
      const until = engineDate(new Date(Date.now() + 24 * 60 * 60 * 1000));
      await api.put(`user/${first}/absence`, { until, substituteId: second });
      await expect(api.put(`user/${second}/absence`, { until, substituteId: first })).rejects.toThrow(/failed with 400: .*lead back/);
    } finally {
      await api.delete(`user/${first}`);
      await api.delete(`user/${second}`);
    }
  });
});
