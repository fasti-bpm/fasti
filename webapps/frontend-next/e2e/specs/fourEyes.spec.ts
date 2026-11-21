/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, requireFeature, test } from '../support/test';
import { PREFIX, user } from '../support/env';
import { Api, waitFor, type Task } from '../support/api';
import { createFilter } from '../support/data';

const SECRET = 'Secret-e2e-1';

interface FourEyesStatus {
  enabled: boolean;
  guarded: boolean;
  userExcluded: boolean;
  blocked: boolean;
}

async function onlyTask(api: Api, instanceId: string, key: string): Promise<Task> {
  return waitFor(
    async () => (await api.tasks(instanceId)).find((task) => task.taskDefinitionKey === key),
    `task ${key} of ${instanceId}`,
  );
}

test.describe('Four-eyes control', () => {
  test('the person who drafted cannot approve; another person can', async ({ app, api }) => {
    const reviewer = `${PREFIX}reviewer${Date.now()}`;
    await api.deploy(`${PREFIX}-four-eyes`, ['e2e-four-eyes.bpmn']);
    const instance = await api.startProcess('e2e_four_eyes', { drafter: user, approver: user }, `${PREFIX}-four-eyes`);
    const draft = await onlyTask(api, instance.id, 'draft');
    const probe = await api.get<FourEyesStatus>(`task/${draft.id}/four-eyes`);
    requireFeature(probe.enabled, 'the four-eyes control is not enabled on this server (fasti.four-eyes.enabled)');
    let other: Api | null = null;
    const filterId = await createFilter(api, `${PREFIX} four eyes ${Date.now()}`, 'e2e_four_eyes');
    try {
      // the signed-in administrator drafts ...
      await api.post(`task/${draft.id}/complete`, {});
      const approve = await onlyTask(api, instance.id, 'approve');
      expect(await api.get<FourEyesStatus>(`task/${approve.id}/four-eyes`)).toMatchObject({
        guarded: true,
        userExcluded: true,
        blocked: true,
      });

      // ... and the tasklist says that someone else must approve
      await open(app, `tasklist?filter=${filterId}&task=${approve.id}`);
      const details = app.getByRole('region', { name: 'Task details' });
      const notice = details.getByRole('note').filter({ hasText: 'You completed an earlier step' });
      await expect(notice).toContainText('Four-eyes check');
      await expect(notice).toContainText(
        'You completed an earlier step of this case that this task checks (E2E draft contract), so another person must complete it.',
      );
      await expect(details.getByText('Another person must complete this task (four-eyes check).')).toBeVisible();
      await expect(details.getByRole('button', { name: 'Complete' })).toBeDisabled();

      // the engine refuses, not only the user interface
      await expect(api.post(`task/${approve.id}/complete`, {})).rejects.toThrow(/failed with 403: .*FourEyesViolationException/);

      // another person approves
      await api.post('user/create', {
        profile: { id: reviewer, firstName: 'Rita', lastName: 'Reviewer', email: `${reviewer}@example.org` },
        credentials: { password: SECRET },
      });
      await api.put(`group/camunda-admin/members/${reviewer}`);
      await api.post(`task/${approve.id}/assignee`, { userId: reviewer });
      other = await Api.login(reviewer, SECRET);
      await other.post(`task/${approve.id}/complete`, {});
      await expect.poll(async () => (await api.tasks(instance.id)).length).toBe(0);
    } finally {
      await other?.dispose();
      await api.delete(`user/${reviewer}`);
      await api.delete(`filter/${filterId}`);
    }
  });
});
