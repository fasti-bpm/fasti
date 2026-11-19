/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { getHealth, type Health } from '../api/admin';
import { fakeServer, json } from '../test/server';
import { healthChecks, overallLevel } from './SystemPage';

const ENGINE = '/engine/engine/default';

const health = (changes: Partial<Health>): Health => ({
  responseMs: 12,
  incidents: 0,
  failedJobs: 0,
  lateJobs: 0,
  failedExternalTasks: 0,
  failingBatches: 0,
  users: 3,
  groups: 2,
  tenants: 0,
  authorizations: 9,
  deployments: 4,
  latestDeployment: null,
  ...changes,
});

describe('health', () => {
  it('is calm when nothing is open, needs a look for incidents and is a problem for stuck jobs', () => {
    expect(overallLevel(healthChecks(health({})))).toBe('ok');
    expect(overallLevel(healthChecks(health({ incidents: 2 })))).toBe('warn');
    expect(overallLevel(healthChecks(health({ incidents: 2, lateJobs: 1 })))).toBe('bad');
    const unreadable = healthChecks(health({ incidents: null, failedJobs: null, lateJobs: null, failedExternalTasks: null, failingBatches: null }));
    expect(overallLevel(unreadable)).toBe('unknown');
  });

  it('counts jobs due more than five minutes ago and keeps going when a count is refused', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/incident/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/job/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/external-task/count`]: () => json({ type: 'AuthorizationException', message: 'no' }, 403),
      [`GET ${ENGINE}/batch/statistics`]: () => json([{ failedJobs: 1 }, { failedJobs: 0 }]),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 5 }),
      [`GET ${ENGINE}/group/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/tenant/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/authorization/count`]: () => json({ count: 7 }),
      [`GET ${ENGINE}/deployment/count`]: () => json({ count: 3 }),
      [`GET ${ENGINE}/deployment`]: () => json([{ id: 'd1', name: 'invoice', deploymentTime: null }]),
    });
    const result = await getHealth(new Date('2026-05-01T10:00:00Z'));
    expect(result).toMatchObject({ incidents: 1, failedJobs: 2, lateJobs: 4, failedExternalTasks: null, failingBatches: 1, users: 5 });
    expect(result.latestDeployment?.name).toBe('invoice');
    const late = server.find('GET', `${ENGINE}/job/count`).filter((request) => request.query.get('executable') === 'true');
    expect(late.find((request) => request.query.get('timers'))?.query.get('dueDates')).toMatch(/^lt_2026-05-01T\d\d:55:00\.000/);
    expect(late.find((request) => request.query.get('messages'))?.query.get('createTimes')).toMatch(/^lt_2026-05-01T\d\d:55:00\.000/);
    expect(late.every((request) => request.query.get('active') === 'true')).toBe(true);
  });
});
