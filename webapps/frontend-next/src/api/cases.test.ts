/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { writePreference } from '../lib/storage';
import { empty, fakeServer, json } from '../test/server';
import * as api from './cases';
import type { CaseExecution } from './cases';

const ENGINE = '/engine/engine/default';
const TOKEN = { 'X-XSRF-TOKEN': 'csrf-1' };

function execution(overrides: Partial<CaseExecution>): CaseExecution {
  return {
    id: 'x1',
    caseInstanceId: 'c1',
    caseDefinitionId: 'claim:1:a',
    activityId: 'PI_review',
    activityName: 'Review',
    activityType: 'humanTask',
    activityDescription: null,
    parentId: 'c1',
    tenantId: null,
    required: false,
    enabled: false,
    active: false,
    disabled: false,
    ...overrides,
  };
}

describe('case definitions', () => {
  it('lists every case once and finds cases without a name by their key', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/case-definition/count`]: (request) => json({ count: request.query.get('keyLike') ? 1 : 0 }),
      [`GET ${ENGINE}/case-definition`]: (request) =>
        json(request.query.get('keyLike') ? [{ id: 'claim:1:a', key: 'claim', name: null, version: 1 }] : []),
    });
    writePreference('tenant', 'acme');

    const page = await api.getCaseDefinitions({
      search: 'cla',
      sorting: { sortBy: 'name', sortOrder: 'asc' },
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(page).toEqual({ count: 1, items: [expect.objectContaining({ key: 'claim' })] });
    const [byName, byKey] = server.find('GET', `${ENGINE}/case-definition`);
    expect(byName?.query.get('nameLike')).toBe('%cla%');
    expect(byName?.query.get('latestVersion')).toBe('true');
    expect(byKey?.query.get('keyLike')).toBe('%cla%');
    // the tenant chosen in the header narrows the list
    expect(byKey?.query.get('tenantIdIn')).toBe('acme');
  });

  it('reads the versions of a case of its own tenant and the model', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/case-definition`]: () => json([]),
      [`GET ${ENGINE}/case-definition/claim:1:a/xml`]: () => json({ id: 'claim:1:a', cmmnXml: '<definitions />' }),
    });
    await api.getCaseDefinitionVersions('claim', null);
    await api.getCaseDefinitionVersions('claim', 'acme');
    expect(await api.getCaseXml('claim:1:a')).toBe('<definitions />');

    const [withoutTenant, withTenant] = server.find('GET', `${ENGINE}/case-definition`);
    expect(withoutTenant?.query.get('withoutTenantId')).toBe('true');
    expect(withTenant?.query.get('tenantIdIn')).toBe('acme');
    expect(withTenant?.query.get('sortBy')).toBe('version');
  });

  it('counts the open instances of all versions of a case', async () => {
    const server = fakeServer({ [`GET ${ENGINE}/case-instance/count`]: () => json({ count: 4 }) });
    expect(await api.countCaseInstances('claim', null)).toBe(4);
    const [request] = server.find('GET', `${ENGINE}/case-instance/count`);
    expect(request?.query.get('caseDefinitionKey')).toBe('claim');
    expect(request?.query.get('withoutTenantId')).toBe('true');
  });
});

describe('plan items', () => {
  it('derives the state of a plan item: what is none of the reported states is available', () => {
    expect(api.executionState(execution({ active: true }))).toBe('active');
    expect(api.executionState(execution({ enabled: true }))).toBe('enabled');
    expect(api.executionState(execution({ disabled: true }))).toBe('disabled');
    expect(api.executionState(execution({}))).toBe('available');
  });

  it('offers the transitions the engine allows in each state', () => {
    expect(api.executionActions(execution({ enabled: true }))).toEqual(['manual-start', 'disable']);
    expect(api.executionActions(execution({ disabled: true }))).toEqual(['reenable']);
    expect(api.executionActions(execution({ active: true }))).toEqual(['complete', 'terminate']);
    expect(api.executionActions(execution({}))).toEqual([]);
    // a milestone occurs by itself; while it waits it can only be terminated
    expect(api.executionActions(execution({ activityType: 'milestone' }))).toEqual(['terminate']);
    expect(api.executionActions(execution({ activityType: 'milestone', active: true }))).toEqual([]);
    // the case instance itself has actions of its own
    expect(api.executionActions(execution({ activityType: 'casePlanModel', active: true }))).toEqual([]);
  });

  it('runs a transition of a plan item and of the case instance', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`POST ${ENGINE}/case-execution/x1/manual-start`]: () => empty(),
      [`POST ${ENGINE}/case-instance/c1/close`]: () => empty(),
    });
    await api.runExecutionAction('x1', 'manual-start');
    await api.runCaseInstanceAction('c1', 'close');
    expect(server.find('POST', `${ENGINE}/case-execution/x1/manual-start`)[0]?.body).toEqual({ variables: {} });
    expect(server.find('POST', `${ENGINE}/case-instance/c1/close`)).toHaveLength(1);
  });

  it('offers completion and termination while a case runs and closing once it is finished', () => {
    expect(api.caseInstanceActions('active')).toEqual(['complete', 'terminate']);
    expect(api.caseInstanceActions('completed')).toEqual(['close']);
    expect(api.caseInstanceActions('terminated')).toEqual(['close']);
    expect(api.caseInstanceActions('closed')).toEqual([]);
  });

  it('says when the numbers on the diagram of a version are cut', async () => {
    fakeServer({
      [`GET ${ENGINE}/case-execution`]: (request) =>
        json(Array.from({ length: Number(request.query.get('maxResults')) }, (_, index) => execution({ id: `x${index}` }))),
    });
    const result = await api.getDefinitionExecutions('claim:1:a');
    expect(result.items).toHaveLength(api.EXECUTION_LIMIT);
    expect(result.truncated).toBe(true);
  });
});

describe('case history', () => {
  it('filters closed and open instances and searches the business key', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/history/case-instance/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/history/case-instance`]: () => json([]),
    });
    await api.getHistoricCaseInstances({
      filter: 'closed',
      search: 'CL-1',
      sorting: { sortBy: 'createTime', sortOrder: 'desc' },
      paging: { firstResult: 0, maxResults: 25 },
    });
    await api.getHistoricCaseInstances({
      filter: 'open',
      sorting: { sortBy: 'createTime', sortOrder: 'desc' },
      paging: { firstResult: 0, maxResults: 25 },
    });
    const [closed, open] = server.find('GET', `${ENGINE}/history/case-instance`);
    expect(closed?.query.get('closed')).toBe('true');
    expect(closed?.query.get('caseInstanceBusinessKeyLike')).toBe('%CL-1%');
    expect(open?.query.get('notClosed')).toBe('true');
    expect(open?.query.has('closed')).toBe(false);
  });

  it('answers null without history or the right to read it', async () => {
    fakeServer({
      [`GET ${ENGINE}/history/case-instance/c1`]: () => json({ type: 'AuthorizationException', message: 'no' }, 403),
      [`GET ${ENGINE}/history/case-activity-instance`]: () => json({ type: 'InvalidRequestException', message: 'off' }, 404),
    });
    expect(await api.getHistoricCaseInstance('c1')).toBeNull();
    expect(await api.getCaseActivityHistory('c1')).toBeNull();
  });

  it('names cases by their business keys in one request', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/history/case-instance`]: () => json([{ id: 'c1', businessKey: 'CL-1' }, { id: 'c2', businessKey: null }]),
    });
    const keys = await api.getCaseBusinessKeys(['c1', 'c2']);
    expect(keys.get('c1')).toBe('CL-1');
    expect(keys.get('c2')).toBeNull();
    expect(server.find('GET', `${ENGINE}/history/case-instance`)[0]?.query.get('caseInstanceIds')).toBe('c1,c2');
    expect(await api.getCaseBusinessKeys([])).toEqual(new Map());
  });

  it('derives the state of a plan item from the history', () => {
    const base = {
      id: 'h1',
      parentCaseActivityInstanceId: null,
      caseActivityId: 'PI_review',
      caseActivityName: 'Review',
      caseActivityType: 'humanTask',
      caseExecutionId: 'x1',
      taskId: null,
      calledProcessInstanceId: null,
      calledCaseInstanceId: null,
      createTime: null,
      endTime: null,
      durationInMillis: null,
      required: false,
      available: false,
      enabled: false,
      disabled: false,
      active: false,
      completed: false,
      terminated: false,
    };
    expect(api.historicActivityState({ ...base, completed: true })).toBe('completed');
    expect(api.historicActivityState({ ...base, terminated: true })).toBe('terminated');
    expect(api.historicActivityState({ ...base, active: true })).toBe('active');
    expect(api.historicActivityState({ ...base, available: true })).toBe('available');
    expect(api.historicCaseState({ active: false, completed: true, terminated: false, closed: true })).toBe('closed');
    expect(api.caseInstanceState({ active: false, completed: false, terminated: true })).toBe('terminated');
  });
});
