/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { fakeServer, empty, json } from '../test/server';
import { batchWindow, getCleanableProcesses, getCleanupJobs, recalculateJobDueDate, runCleanupNow, setJobDueDate } from './maintenance';

describe('job due dates', () => {
  it('sets a due date with or without moving later cycles', async () => {
    const server = fakeServer({ 'PUT /engine/engine/default/job/j1/duedate': () => empty() });
    await setJobDueDate('j1', '2026-12-01T10:00:00.000+0100', true);
    await setJobDueDate('j1', null, false);
    expect(server.find('PUT', '/engine/engine/default/job/j1/duedate').map((request) => request.body)).toEqual([
      { duedate: '2026-12-01T10:00:00.000+0100', cascade: true },
      { duedate: null, cascade: false },
    ]);
  });

  it('computes the due date again from creation or from now', async () => {
    const server = fakeServer({ 'POST /engine/engine/default/job/j1/duedate/recalculate': () => empty() });
    await recalculateJobDueDate('j1', true);
    await recalculateJobDueDate('j1', false);
    expect(
      server.find('POST', '/engine/engine/default/job/j1/duedate/recalculate').map((request) => request.query.get('creationDateBased')),
    ).toEqual(['true', 'false']);
  });
});

describe('history cleanup', () => {
  it('reads no cleanup job as an empty list', async () => {
    fakeServer({ 'GET /engine/engine/default/history/cleanup/jobs': () => json({ type: 'RestException', message: 'History cleanup jobs are empty' }, 404) });
    expect(await getCleanupJobs()).toEqual([]);
  });

  it('runs the cleanup at once', async () => {
    const server = fakeServer({ 'POST /engine/engine/default/history/cleanup': () => json({ id: 'c1', retries: 3 }) });
    expect((await runCleanupNow()).id).toBe('c1');
    expect(server.find('POST', '/engine/engine/default/history/cleanup')[0]?.query.get('immediatelyDue')).toBe('true');
  });

  it('reads the cleanable instances per definition', async () => {
    fakeServer({
      'GET /engine/engine/default/history/process-definition/cleanable-process-instance-report': () =>
        json([
          {
            processDefinitionKey: 'order',
            processDefinitionName: 'Order',
            processDefinitionVersion: 2,
            historyTimeToLive: 30,
            finishedProcessInstanceCount: 10,
            cleanableProcessInstanceCount: 4,
          },
        ]),
    });
    expect(await getCleanableProcesses()).toEqual([
      { key: 'order', name: 'Order', version: 2, historyTimeToLive: 30, finished: 10, cleanable: 4 },
    ]);
  });

  it('describes the batch window', () => {
    const hhmm = (date: Date) => date.toISOString().slice(11, 16);
    expect(
      batchWindow({ batchWindowStartTime: '2026-11-16T22:00:00.000Z', batchWindowEndTime: '2026-11-17T06:00:00.000Z' }, hhmm),
    ).toBe('22:00–06:00');
    expect(batchWindow({ batchWindowStartTime: null, batchWindowEndTime: null }, hhmm)).toBeNull();
  });
});

describe('cleanable report', () => {
  it('reads every page, so that the total covers the whole engine', async () => {
    const row = (index: number) => ({
      processDefinitionId: `p${index}`,
      processDefinitionKey: `p${index}`,
      processDefinitionName: null,
      processDefinitionVersion: 1,
      historyTimeToLive: 1,
      finishedProcessInstanceCount: 1,
      cleanableProcessInstanceCount: 1,
    });
    fakeServer({
      'GET /engine/engine/default/history/process-definition/cleanable-process-instance-report': (request) => {
        const first = Number(request.query.get('firstResult'));
        const size = first === 0 ? 200 : 5;
        return json(Array.from({ length: size }, (_, index) => row(first + index)));
      },
    });
    expect(await getCleanableProcesses()).toHaveLength(205);
  });
});
