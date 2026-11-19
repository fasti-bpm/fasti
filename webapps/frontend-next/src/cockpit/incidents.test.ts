/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it } from 'vitest';
import { getIncidents, type Incident } from '../api/cockpit';
import { setTenantScope } from '../lib/userPrefs';
import { fakeServer, json } from '../test/server';
import { rootFailures } from './Incidents';

const incident = (part: Partial<Incident>): Incident => ({
  id: 'i',
  incidentTimestamp: '',
  incidentType: 'failedJob',
  incidentMessage: null,
  activityId: null,
  failedActivityId: null,
  processInstanceId: 'p',
  processDefinitionId: null,
  causeIncidentId: null,
  rootCauseIncidentId: null,
  configuration: null,
  annotation: null,
  ...part,
});

describe('retry across call activities', () => {
  it('retries the root cause once, also when several incidents point to it', () => {
    expect(
      rootFailures([
        incident({ id: 'parent', configuration: null, rootCauseIncidentConfiguration: 'job-child' }),
        incident({ id: 'child', configuration: 'job-child' }),
        incident({ id: 'ext', incidentType: 'failedExternalTask', configuration: 'ext-1' }),
        incident({ id: 'custom', incidentType: 'custom', configuration: 'x' }),
      ]),
    ).toEqual([
      { kind: 'job', id: 'job-child' },
      { kind: 'externalTask', id: 'ext-1' },
    ]);
  });
});

describe('incident list filters', () => {
  afterEach(() => setTenantScope(null));

  it('asks the engine for activity, process key and the tenant in scope', async () => {
    setTenantScope('acme');
    const server = fakeServer({
      'GET /engine/engine/default/incident/count': () => json({ count: 0 }),
      'GET /engine/engine/default/incident': () => json([]),
    });
    await getIncidents({
      activityId: 'Task_1',
      processDefinitionKey: 'order',
      messageLike: 'timeout',
      sorting: { sortBy: 'incidentTimestamp', sortOrder: 'desc' },
      paging: { firstResult: 0, maxResults: 25 },
    });
    const query = server.find('GET', '/engine/engine/default/incident')[0]?.query;
    expect(query?.get('activityId')).toBe('Task_1');
    expect(query?.get('processDefinitionKeyIn')).toBe('order');
    expect(query?.get('incidentMessageLike')).toBe('%timeout%');
    expect(query?.get('tenantIdIn')).toBe('acme');
  });
});
