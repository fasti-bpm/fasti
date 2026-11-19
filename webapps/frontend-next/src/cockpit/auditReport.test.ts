/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { findCases, getAllOperations, resolveCase, type OperationLogEntry } from '../api/operations';
import { toCsv } from '../lib/csv';
import { fakeServer, json } from '../test/server';
import { auditRows } from './AuditPage';

const ENGINE = '/engine/engine/default';

const entry = (id: number): OperationLogEntry => ({
  id: `e${id}`,
  operationId: `op${id}`,
  operationType: 'Complete',
  entityType: 'Task',
  category: 'TaskWorker',
  property: null,
  orgValue: null,
  newValue: null,
  userId: id === 0 ? '=HYPERLINK("x")' : 'anna',
  timestamp: '2026-03-01T09:00:00.000+0000',
  processInstanceId: 'pi',
  processDefinitionKey: 'loan',
  annotation: null,
});

describe('audit report', () => {
  it('reads every matching entry in pages, oldest first, with the filters', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/history/user-operation`]: (request) => {
        const first = Number(request.query.get('firstResult'));
        return json(first === 0 ? [entry(1), entry(2)] : first === 2 ? [entry(3)] : []);
      },
    });
    const result = await getAllOperations({ userId: 'anna', operationType: 'Complete' }, undefined, 2);
    expect(result).toEqual({ entries: [entry(1), entry(2), entry(3)], truncated: false });
    const [request] = server.find('GET', `${ENGINE}/history/user-operation`);
    expect(Object.fromEntries(request?.query ?? [])).toMatchObject({ userId: 'anna', operationType: 'Complete', sortOrder: 'asc' });
  });

  it('finds a case by instance id or else by business key', async () => {
    fakeServer({
      [`GET ${ENGINE}/history/process-instance`]: (request) =>
        json(request.query.get('processInstanceBusinessKey') === 'HS-1' ? [{ id: 'pi-7' }] : []),
    });
    expect(await resolveCase('HS-1')).toBe('pi-7');
    expect(await resolveCase('unknown')).toBeNull();
    expect(await resolveCase('  ')).toBeNull();
  });

  it('offers every instance of a reused business key, newest first, and says when there are more', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/history/process-instance`]: (request) =>
        json(
          request.query.get('processInstanceBusinessKey') === 'HS-2'
            ? Array.from({ length: 21 }, (_, i) => ({ id: `pi-${i}`, processDefinitionKey: 'loan', startTime: null, state: 'COMPLETED' }))
            : [],
        ),
    });
    const found = await findCases('HS-2');
    expect(found.matches).toHaveLength(20);
    expect(found.matches[0]?.id).toBe('pi-0');
    expect(found.more).toBe(true);
    const byKey = server.find('GET', `${ENGINE}/history/process-instance`).at(-1);
    expect(byKey?.query.get('sortOrder')).toBe('desc');
  });

  it('keeps values that look like formulas from running in a spreadsheet', () => {
    const csv = toCsv(['user'], auditRows([entry(0)]).map((row) => [row[1]]));
    expect(csv).toContain(`"'=HYPERLINK(""x"")"`);
  });
});
