/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { setTenantScope } from '../lib/userPrefs';
import { fakeServer, json } from '../test/server';
import {
  getDashboardNumbers,
  getDefinitionStatistics,
  getFailedJobs,
  getInstances,
  getLatestDefinition,
  getRunningBatches,
  queryExternalTasks,
  setFailedJobsRetriesAsync,
} from './cockpit';
import { getHistoricDecisions, getHistoricInstances, historicQueryBody, runtimeQuery } from './operations';

const ENGINE = '/engine/engine/default';
const PLUGIN = '/cockpit/plugin/base/default';

describe('tenant scope', () => {
  it('narrows every list that the engine can narrow, and the bulk queries over them', async () => {
    setTenantScope('acme');
    const server = fakeServer({
      [`GET ${PLUGIN}/process-definition/statistics-count`]: () => json({ count: 0 }),
      [`GET ${PLUGIN}/process-definition/statistics`]: () => json([]),
      [`POST ${PLUGIN}/process-instance/count`]: () => json({ count: 0 }),
      [`POST ${PLUGIN}/process-instance`]: () => json([]),
      [`GET ${ENGINE}/job/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/job`]: () => json([]),
      [`POST ${ENGINE}/job/retries`]: () => json({ id: 'b' }),
      [`GET ${ENGINE}/external-task/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/external-task`]: () => json([]),
      [`POST ${ENGINE}/history/process-instance/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/history/process-instance`]: () => json([]),
      [`GET ${ENGINE}/history/decision-instance/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/history/decision-instance`]: () => json([]),
      [`GET ${ENGINE}/batch/statistics`]: () => json([]),
      [`GET ${ENGINE}/process-instance/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/incident/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/process-definition/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/decision-definition/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/deployment/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/process-definition`]: () => json([]),
    });
    const paging = { firstResult: 0, maxResults: 10 };
    await getDefinitionStatistics({ sorting: { sortBy: 'name', sortOrder: 'asc' }, paging });
    await getInstances({ query: { processDefinitionId: 'd1' }, sortOrder: 'desc', paging });
    await getFailedJobs({ scope: 'noRetriesLeft', sorting: { sortBy: 'jobDueDate', sortOrder: 'asc' }, paging });
    await setFailedJobsRetriesAsync('noRetriesLeft', 1);
    await queryExternalTasks({ filter: { state: 'all' }, sorting: { sortBy: 'id', sortOrder: 'asc' }, paging });
    await getHistoricInstances({ query: {}, sortOrder: 'desc', paging });
    await getHistoricDecisions({ query: {}, paging });
    await getRunningBatches();
    await getDashboardNumbers();
    await getLatestDefinition('order');

    for (const path of [
      `${PLUGIN}/process-definition/statistics`,
      `${PLUGIN}/process-definition/statistics-count`,
      `${ENGINE}/job`,
      `${ENGINE}/job/count`,
      `${ENGINE}/external-task`,
      `${ENGINE}/history/decision-instance`,
      `${ENGINE}/batch/statistics`,
      `${ENGINE}/process-instance/count`,
      `${ENGINE}/incident/count`,
      `${ENGINE}/task/count`,
      `${ENGINE}/process-definition/count`,
      `${ENGINE}/decision-definition/count`,
      `${ENGINE}/deployment/count`,
      `${ENGINE}/process-definition`,
    ]) {
      expect(server.find('GET', path)[0]?.query.get('tenantIdIn'), path).toBe('acme');
    }
    for (const path of [`${PLUGIN}/process-instance`, `${PLUGIN}/process-instance/count`, `${ENGINE}/history/process-instance`]) {
      expect(server.find('POST', path)[0]?.body, path).toMatchObject({ tenantIdIn: ['acme'] });
    }
    expect(server.find('POST', `${ENGINE}/job/retries`)[0]?.body).toMatchObject({ jobQuery: { tenantIdIn: ['acme'] } });
    expect(runtimeQuery({ processDefinitionId: 'd1' })).toMatchObject({ tenantIdIn: ['acme'] });
    expect(historicQueryBody({ state: 'completed' })).toMatchObject({ tenantIdIn: ['acme'] });
  });

  it('leaves the lists engine-wide without a scope', () => {
    expect(runtimeQuery({ processDefinitionId: 'd1' })).not.toHaveProperty('tenantIdIn');
    expect(historicQueryBody({})).not.toHaveProperty('tenantIdIn');
  });
});
