/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { BrowserContext, Page } from '@playwright/test';
import { expect, open, signIn, test } from '../support/test';
import { PREFIX } from '../support/env';
import type { Api, Task } from '../support/api';

const SECRET = 'Next-work-e2e-1';
const DAY = 24 * 60 * 60 * 1000;

/** An engine date, `days` from now. */
const inDays = (days: number) => new Date(Date.now() + days * DAY).toISOString().replace('Z', '+0000');

/** A user of its own, so that only the tasks of the test are offered to it. */
async function worker(api: Api, id: string): Promise<string> {
  await api.delete(`user/${id}`);
  await api.post('user/create', {
    profile: { id, firstName: 'Next', lastName: 'Worker', email: `${id}@example.org` },
    credentials: { password: SECRET },
  });
  // with authorization checks on (the default of the container image) the worker needs access
  // to the Tasklist and may read the filters and process definitions; tasks it is a candidate
  // for are granted by the engine. Deleting the user deletes these grants again.
  for (const [resourceType, resourceId, permissions] of [
    [0, 'tasklist', ['ACCESS']],
    [5, '*', ['READ']],
    [6, '*', ['READ']],
  ] as const) {
    await api.post('authorization/create', { type: 1, userId: id, resourceType, resourceId, permissions });
  }
  return id;
}

/** A task the worker is a candidate for, with a due date and a priority. */
async function offer(api: Api, userId: string, name: string, due: string | null, priority: number): Promise<Task> {
  const instance = await api.startProcess('e2e_tasks', {}, `${PREFIX}-next-${name}`);
  const [task] = await api.tasks(instance.id);
  if (!task) throw new Error(`no task in ${instance.id}`);
  const full = await api.get<Record<string, unknown>>(`task/${task.id}`);
  await api.put(`task/${task.id}`, { ...full, name, due, priority });
  await api.post(`task/${task.id}/identity-links`, { userId, type: 'candidate' });
  return { ...task, name };
}

async function signInAs(context: BrowserContext, page: Page, userId: string): Promise<void> {
  await signIn(context, userId, SECRET);
  await open(page, 'tasklist');
  await expect(page.getByRole('button', { name: 'Get next work' })).toBeVisible();
}

/** The browser logs the claim the test made fail; that one is expected, any other problem is not. */
function expectFailedClaim(problems: { messages: string[] }, status: number): void {
  const expected = `Failed to load resource: the server responded with a status of ${status}`;
  expect(problems.messages.filter((message) => message.includes(expected))).toHaveLength(1);
  problems.messages = problems.messages.filter((message) => !message.includes(expected));
}

const assigneeOf = async (api: Api, taskId: string) => (await api.get<{ assignee: string | null }>(`task/${taskId}`)).assignee;

test.describe('Get next work', () => {
  test('claims and opens the most urgent task, then says when nothing is left', async ({ page, context, api }) => {
    const me = await worker(api, `${PREFIX}nextorder`);
    const later = await offer(api, me, 'Due later', inDays(2), 50);
    const late = await offer(api, me, 'Overdue', inDays(-1), 10);
    const undated = await offer(api, me, 'Undated', null, 100);
    await signInAs(context, page, me);

    const details = page.getByRole('region', { name: 'Task details' });
    const status = page.getByRole('status').filter({ hasText: /is now yours|No work is waiting/ });
    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(details.getByRole('heading', { name: 'Overdue' })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`task=${late.id}`));
    await expect(status).toHaveText(/“Overdue” is now yours and open\. It is overdue\./);
    await expect.poll(() => assigneeOf(api, late.id)).toBe(me);

    // the shortcut does the same; the list is the same, the next due comes next
    await page.locator('body').click({ position: { x: 1, y: 1 } });
    await page.keyboard.press('n');
    await expect(details.getByRole('heading', { name: 'Due later' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, later.id)).toBe(me);

    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(details.getByRole('heading', { name: 'Undated' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, undated.id)).toBe(me);

    await page.getByRole('button', { name: 'Get next work' }).click();
    const empty = 'No work is waiting for you right now. New tasks will show up here.';
    await expect(page.locator('.notice').filter({ hasText: empty })).toBeVisible();
    await expect(status).toHaveText(empty);
  });

  test('takes the next task when another user claimed the first one just before', async ({ page, context, api, problems }) => {
    const me = await worker(api, `${PREFIX}nextrace`);
    const first = await offer(api, me, 'Raced', inDays(-2), 50);
    const second = await offer(api, me, 'Still free', inDays(1), 50);
    await signInAs(context, page, me);

    // someone else claims the task while the request of the worker is on its way
    await page.route(`**/task/${first.id}/claim`, async (route) => {
      await api.post(`task/${first.id}/claim`, { userId: 'demo' });
      await route.continue();
    });
    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(page.getByRole('region', { name: 'Task details' }).getByRole('heading', { name: 'Still free' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, second.id)).toBe(me);
    expect(await assigneeOf(api, first.id)).toBe('demo');
    expectFailedClaim(problems, 500);
  });

  test('skips a task the user may not claim and says so', async ({ page, context, api, problems }) => {
    const me = await worker(api, `${PREFIX}nextdenied`);
    const denied = await offer(api, me, 'Denied', inDays(-2), 50);
    const allowed = await offer(api, me, 'Allowed', inDays(1), 50);
    await signInAs(context, page, me);

    await page.route(`**/task/${denied.id}/claim`, (route) =>
      route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ type: 'AuthorizationException', message: 'The user may not claim this task.' }),
      }),
    );
    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(page.getByRole('region', { name: 'Task details' }).getByRole('heading', { name: 'Allowed' })).toBeVisible();
    await expect(page.locator('.notice').filter({ hasText: 'Skipped tasks you may not claim: 1.' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, allowed.id)).toBe(me);
    expect(await assigneeOf(api, denied.id)).toBeNull();
    expectFailedClaim(problems, 403);
  });

  test('takes next work from the selected filter only, also when the filter names a group', async ({ page, context, api }) => {
    const me = await worker(api, `${PREFIX}nextscope`);
    const group = `${PREFIX}nextteam`;
    await api.delete(`group/${group}`);
    await api.post('group/create', { id: group, name: 'Next team', type: 'WORKFLOW' });
    await api.put(`group/${group}/members/${me}`);
    const outside = await offer(api, me, 'Outside the filter', inDays(-3), 90);
    const inside = await offer(api, me, 'Team job', inDays(3), 10);
    await api.post(`task/${inside.id}/identity-links`, { groupId: group, type: 'candidate' });
    // a filter that names a candidate group cannot also be asked for a candidate user
    const filter = await api.post<{ id: string }>('filter/create', {
      resourceType: 'Task',
      name: `${PREFIX} next team ${Date.now()}`,
      owner: me,
      query: { candidateGroup: group },
      properties: { priority: -100 },
    });
    await signIn(context, me, SECRET);
    await open(page, `tasklist?filter=${filter.id}`);
    await page.getByRole('checkbox', { name: /Take next work from “.*next team.*” only/ }).check();
    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(page.getByRole('region', { name: 'Task details' }).getByRole('heading', { name: 'Team job' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, inside.id)).toBe(me);
    expect(await assigneeOf(api, outside.id)).toBeNull();
  });

  test('completes a task and gets the next one when the user chose so', async ({ page, context, api }) => {
    const me = await worker(api, `${PREFIX}nextcomplete`);
    const first = await offer(api, me, 'First job', inDays(-1), 50);
    const second = await offer(api, me, 'Second job', inDays(1), 50);
    await signInAs(context, page, me);

    const details = page.getByRole('region', { name: 'Task details' });
    await page.getByRole('button', { name: 'Get next work' }).click();
    await expect(details.getByRole('heading', { name: 'First job' })).toBeVisible();
    await details.getByRole('checkbox', { name: 'Complete and get next work' }).check();
    await details.getByRole('button', { name: 'Complete', exact: true }).click();
    await expect(details.getByRole('heading', { name: 'Second job' })).toBeVisible();
    await expect.poll(() => assigneeOf(api, second.id)).toBe(me);
    await expect.poll(async () => (await api.get<unknown[]>('task', { taskId: first.id })).length).toBe(0);
  });
});
