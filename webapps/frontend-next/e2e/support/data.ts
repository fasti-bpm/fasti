/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { PREFIX, user } from './env';
import type { Api } from './api';

/** Removes everything earlier runs of the tests left behind. */
export async function cleanUp(api: Api): Promise<void> {
  const deployments = await api.get<{ id: string; name: string; source: string | null }[]>('deployment', {
    nameLike: `${PREFIX}%`,
  });
  for (const deployment of deployments) {
    await api.delete(`deployment/${deployment.id}?cascade=true&skipCustomListeners=true&skipIoMappings=true`);
  }
  // the invoice test uses the example process; its instances carry an invoice
  // number with the prefix
  const invoices = { processDefinitionKey: 'invoice', variables: `invoiceNumber_like_${PREFIX}-%` };
  for (const instance of await api.get<{ id: string }[]>('process-instance', invoices)) {
    await api.delete(`process-instance/${instance.id}?skipCustomListeners=true&skipIoMappings=true`);
  }
  for (const instance of await api.get<{ id: string }[]>('history/process-instance', invoices)) {
    await api.delete(`history/process-instance/${instance.id}`);
  }
  for (const filter of await api.get<{ id: string }[]>('filter', { nameLike: `${PREFIX}%` })) {
    await api.delete(`filter/${filter.id}`);
  }
  // the identity queries have no `idLike`: filter here, never delete blindly;
  // identity ids allow no dashes, so they are `e2e` followed by letters
  const ours = (entity: { id: string }) => entity.id.startsWith(PREFIX);
  for (const user of (await api.get<{ id: string }[]>('user')).filter(ours)) {
    await api.delete(`user/${user.id}`);
  }
  for (const group of (await api.get<{ id: string }[]>('group')).filter(ours)) {
    await api.delete(`group/${group.id}`);
  }
  for (const tenant of (await api.get<{ id: string }[]>('tenant')).filter(ours)) {
    await api.delete(`tenant/${tenant.id}`);
  }
  const authorizations = await api.get<{ id: string; userId: string | null; groupId: string | null }[]>('authorization');
  for (const authorization of authorizations) {
    if (authorization.userId?.startsWith(PREFIX) || authorization.groupId?.startsWith(PREFIX)) {
      await api.delete(`authorization/${authorization.id}`);
    }
  }
}

/** Deploys the test resources and starts the instances the tests work with. */
export async function seed(api: Api): Promise<void> {
  await api.deploy(`${PREFIX}-resources`, [
    'e2e-tasks.bpmn',
    'e2e-form.bpmn',
    'e2e-form.form',
    'e2e-incident.bpmn',
    'e2e-external.bpmn',
    'e2e-decision.dmn',
  ]);
  await api.deploy(`${PREFIX}-migrate-v1`, ['e2e-migrate-v1.bpmn']);
  for (let i = 0; i < 2; i++) {
    await api.startProcess('e2e_migrate', {}, `${PREFIX}-migrate-${i}`);
  }
  await api.deploy(`${PREFIX}-migrate-v2`, ['e2e-migrate-v2.bpmn']);

  // a completed instance, for history and restart
  const done = await api.startProcess('e2e_tasks', {}, `${PREFIX}-done`);
  const [task] = await api.tasks(done.id);
  if (task) {
    await api.post(`task/${task.id}/complete`, { variables: {} });
  }

  for (const decisionAmount of [100, 5000]) {
    await api.post('decision-definition/key/e2e_decision/evaluate', {
      variables: { amount: { value: decisionAmount, type: 'Double' } },
    });
  }
}

/** A task filter of the administrator that shows the tasks of one process. */
export async function createFilter(api: Api, name: string, processDefinitionKey: string): Promise<string> {
  const filter = await api.post<{ id: string }>('filter/create', {
    resourceType: 'Task',
    name,
    owner: user,
    query: { processDefinitionKey },
    properties: { priority: -100 },
  });
  return filter.id;
}
