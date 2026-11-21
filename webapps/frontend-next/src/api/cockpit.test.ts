/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { empty, fakeServer, json, text } from '../test/server';
import * as api from './cockpit';
import type { VariableInstance } from './cockpit';

const ENGINE = '/engine/engine/default';
const PLUGIN = '/cockpit/plugin/base/default';
const TOKEN = { 'X-XSRF-TOKEN': 'csrf-1' };

function variable(overrides: Partial<VariableInstance>): VariableInstance {
  return {
    id: 'v1',
    name: 'amount',
    type: 'Long',
    value: 5,
    valueInfo: {},
    processInstanceId: 'p1',
    executionId: 'e1',
    taskId: null,
    activityInstanceId: 'p1',
    errorMessage: null,
    ...overrides,
  };
}

describe('dashboard', () => {
  it('collects the numbers of the engine', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/process-instance/count`]: () => json({ count: 12 }),
      [`GET ${ENGINE}/incident/count`]: () => json({ count: 3 }),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 7 }),
      [`GET ${ENGINE}/process-definition/count`]: () => json({ count: 4 }),
      [`GET ${ENGINE}/decision-definition/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/deployment/count`]: () => json({ count: 5 }),
    });

    expect(await api.getDashboardNumbers()).toEqual({
      instances: 12,
      incidents: 3,
      tasks: 7,
      processDefinitions: 4,
      decisionDefinitions: 2,
      deployments: 5,
    });
    // definitions are counted once, not once per version
    expect(server.find('GET', `${ENGINE}/process-definition/count`)[0]?.query.get('latestVersion')).toBe('true');
    expect(server.find('GET', `${ENGINE}/decision-definition/count`)[0]?.query.get('latestVersion')).toBe('true');
  });

  it('splits the open tasks by assignment', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/task/count`]: (request) =>
        json({
          count: request.query.get('assigned') ? 4 : request.query.get('withCandidateGroups') ? 2 : 1,
        }),
    });

    expect(await api.getTaskAssignment()).toEqual({ assigned: 4, withGroups: 2, unassigned: 1 });
    expect(server.requests).toHaveLength(3);
  });
});

describe('process definitions', () => {
  it('reads the statistics of the base plugin with search, sorting and paging', async () => {
    const server = fakeServer({
      [`GET ${PLUGIN}/process-definition/statistics-count`]: () => json({ count: 1 }),
      [`GET ${PLUGIN}/process-definition/statistics`]: () =>
        json([{ id: 'invoice:2:x', key: 'invoice', name: 'Invoice', version: 2, tenantId: null, suspensionState: 1, instances: 9, incidents: 1 }]),
    });

    const page = await api.getDefinitionStatistics({
      search: 'inv',
      sorting: { sortBy: 'instances', sortOrder: 'desc' },
      paging: { firstResult: 25, maxResults: 25 },
    });

    expect(page.count).toBe(1);
    expect(page.items[0]?.instances).toBe(9);
    const [list] = server.find('GET', `${PLUGIN}/process-definition/statistics`);
    expect(Object.fromEntries(list?.query ?? [])).toEqual({
      nameLike: '%inv%',
      sortBy: 'instances',
      sortOrder: 'desc',
      firstResult: '25',
      maxResults: '25',
    });
  });

  it('searches by key if no name matches', async () => {
    const server = fakeServer({
      [`GET ${PLUGIN}/process-definition/statistics-count`]: (request) =>
        json({ count: request.query.get('keyLike') ? 1 : 0 }),
      [`GET ${PLUGIN}/process-definition/statistics`]: (request) =>
        json(request.query.get('keyLike') ? [{ id: 'a:1:x', key: 'a', name: null }] : []),
    });

    const page = await api.getDefinitionStatistics({
      search: 'a',
      sorting: { sortBy: 'name', sortOrder: 'asc' },
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(page).toEqual({ count: 1, items: [{ id: 'a:1:x', key: 'a', name: null }] });
    expect(server.find('GET', `${PLUGIN}/process-definition/statistics`)).toHaveLength(2);
  });

  it('suspends a definition with its instances at a given time', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`PUT ${ENGINE}/process-definition/invoice:1:x/suspended`]: () => empty(),
    });

    await api.setDefinitionSuspended('invoice:1:x', {
      suspended: true,
      includeProcessInstances: true,
      executionDate: '2026-12-01T08:00:00.000+0700',
    });

    const [request] = server.find('PUT', `${ENGINE}/process-definition/invoice:1:x/suspended`);
    expect(request?.body).toEqual({
      suspended: true,
      includeProcessInstances: true,
      executionDate: '2026-12-01T08:00:00.000+0700',
    });
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
  });

  it('deletes a definition with the chosen options', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`DELETE ${ENGINE}/process-definition/invoice:1:x`]: () => empty(),
    });

    await api.deleteDefinition('invoice:1:x', { cascade: true, skipCustomListeners: false, skipIoMappings: true });

    const [request] = server.find('DELETE', `${ENGINE}/process-definition/invoice:1:x`);
    expect(Object.fromEntries(request?.query ?? [])).toEqual({
      cascade: 'true',
      skipCustomListeners: 'false',
      skipIoMappings: 'true',
    });
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
  });

  it('changes and removes the overriding priority of a job definition', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`PUT ${ENGINE}/job-definition/j1/jobPriority`]: () => empty(),
    });

    await api.setJobDefinitionPriority('j1', 70, true);
    await api.setJobDefinitionPriority('j1', null, true);

    const requests = server.find('PUT', `${ENGINE}/job-definition/j1/jobPriority`);
    expect(requests[0]?.body).toEqual({ priority: 70, includeJobs: true });
    // the engine refuses to reset the priority together with the jobs
    expect(requests[1]?.body).toEqual({ priority: null, includeJobs: false });
  });
});

describe('process instances', () => {
  it('queries the instances of a definition and leaves out empty criteria', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`POST ${PLUGIN}/process-instance/count`]: () => json({ count: 40 }),
      [`POST ${PLUGIN}/process-instance`]: () =>
        json([{ id: 'p1', businessKey: 'INV-1', startTime: null, incidents: [], suspended: false }]),
    });

    const page = await api.getInstances({
      query: { processDefinitionId: 'invoice:1:x', activityIdIn: [], variables: [], withIncident: false },
      sortOrder: 'desc',
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(page.count).toBe(40);
    const [list] = server.find('POST', `${PLUGIN}/process-instance`);
    expect(list?.body).toEqual({ processDefinitionId: 'invoice:1:x', sortBy: 'startTime', sortOrder: 'desc' });
    expect(list?.query.get('maxResults')).toBe('25');
    expect(server.find('POST', `${PLUGIN}/process-instance/count`)[0]?.body).toEqual({
      processDefinitionId: 'invoice:1:x',
    });
  });

  it('passes the criteria of a search', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`POST ${PLUGIN}/process-instance/count`]: () => json({ count: 0 }),
      [`POST ${PLUGIN}/process-instance`]: () => json([]),
    });

    await api.getInstances({
      query: {
        processDefinitionId: 'invoice:1:x',
        businessKey: 'INV-1',
        activityIdIn: ['approve'],
        withIncident: true,
        variables: [{ name: 'amount', operator: 'gt', value: 100 }],
      },
      sortOrder: 'asc',
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(server.find('POST', `${PLUGIN}/process-instance/count`)[0]?.body).toEqual({
      processDefinitionId: 'invoice:1:x',
      businessKey: 'INV-1',
      activityIdIn: ['approve'],
      withIncident: true,
      variables: [{ name: 'amount', operator: 'gt', value: 100 }],
    });
  });

  it('cancels an instance with the chosen options', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`DELETE ${ENGINE}/process-instance/p1`]: () => empty(),
    });

    await api.deleteInstance('p1', { skipCustomListeners: true, skipIoMappings: false, skipSubprocesses: true });

    const [request] = server.find('DELETE', `${ENGINE}/process-instance/p1`);
    expect(Object.fromEntries(request?.query ?? [])).toEqual({
      skipCustomListeners: 'true',
      skipIoMappings: 'false',
      skipSubprocesses: 'true',
    });
  });

  it('reads the start of an instance from the history if there is one', async () => {
    fakeServer({
      [`GET ${ENGINE}/history/process-instance/p1`]: () => json({ type: 'x', message: 'no history' }, 404),
      [`GET ${ENGINE}/history/process-instance/p2`]: () => json({ startTime: 't', startUserId: 'demo' }),
    });

    expect(await api.getInstanceHistory('p1')).toBeNull();
    expect(await api.getInstanceHistory('p2')).toMatchObject({ startUserId: 'demo' });
  });
});

describe('variables', () => {
  it('asks for serialized values', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/variable-instance`]: () => json([variable({})]),
    });

    const page = await api.getVariables({
      processInstanceId: 'p1',
      nameLike: 'amo',
      sortOrder: 'asc',
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(page.items).toHaveLength(1);
    const [list] = server.find('GET', `${ENGINE}/variable-instance`);
    expect(list?.query.get('deserializeValues')).toBe('false');
    expect(list?.query.get('processInstanceIdIn')).toBe('p1');
    expect(list?.query.get('variableNameLike')).toBe('%amo%');
  });

  it('writes a variable in the scope it lives in', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`PUT ${ENGINE}/execution/e1/localVariables/amount`]: () => empty(),
      [`PUT ${ENGINE}/task/t1/localVariables/note a`]: () => empty(),
      [`DELETE ${ENGINE}/execution/e1/localVariables/amount`]: () => empty(),
      [`PUT ${ENGINE}/process-instance/p1/variables/new`]: () => empty(),
    });

    await api.updateVariable(variable({}), { value: 9, type: 'Long' });
    await api.updateVariable(variable({ name: 'note a', taskId: 't1', type: 'String' }), {
      value: 'x',
      type: 'String',
    });
    await api.deleteVariable(variable({}));
    await api.addInstanceVariable('p1', 'new', { value: true, type: 'Boolean' });

    expect(server.find('PUT', `${ENGINE}/execution/e1/localVariables/amount`)[0]?.body).toEqual({
      value: 9,
      type: 'Long',
    });
    // names are encoded as one path segment
    expect(server.find('PUT', `${ENGINE}/task/t1/localVariables/note a`)[0]?.url).toContain('note%20a');
    expect(server.find('DELETE', `${ENGINE}/execution/e1/localVariables/amount`)).toHaveLength(1);
    expect(server.find('PUT', `${ENGINE}/process-instance/p1/variables/new`)[0]?.body).toEqual({
      value: true,
      type: 'Boolean',
    });
  });

  it('reads and writes the variables of a case instance where they live', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/variable-instance`]: () => json([]),
      [`PUT ${ENGINE}/case-execution/x1/localVariables/amount`]: () => empty(),
      [`PUT ${ENGINE}/case-instance/c1/variables/new`]: () => empty(),
    });

    await api.getVariables({ caseInstanceId: 'c1', sortOrder: 'asc', paging: { firstResult: 0, maxResults: 25 } });
    const caseVariable = variable({ processInstanceId: null, executionId: null, activityInstanceId: null, caseInstanceId: 'c1', caseExecutionId: 'x1' });
    await api.updateVariable(caseVariable, { value: 9, type: 'Long' });
    await api.addCaseVariable('c1', 'new', { value: 'x', type: 'String' });

    const [list] = server.find('GET', `${ENGINE}/variable-instance`);
    expect(list?.query.get('caseInstanceIdIn')).toBe('c1');
    expect(list?.query.has('processInstanceIdIn')).toBe(false);
    expect(server.find('PUT', `${ENGINE}/case-execution/x1/localVariables/amount`)).toHaveLength(1);
    expect(server.find('PUT', `${ENGINE}/case-instance/c1/variables/new`)[0]?.body).toEqual({ value: 'x', type: 'String' });
  });
});

describe('search', () => {
  it('finds a case instance by its id or business key, open or closed, once', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/case-instance`]: (request) =>
        json(request.query.get('businessKey') === 'CL-1' || request.query.get('caseInstanceId') === 'CL-1' ? [{ id: 'c1', businessKey: 'CL-1', caseDefinitionId: 'claim:1:a' }] : []),
      [`GET ${ENGINE}/history/case-instance`]: () => json([{ id: 'c1', businessKey: 'CL-1', caseDefinitionKey: 'claim' }]),
    });
    const hits = await api.findById('CL-1', { instances: true, tasks: false });
    expect(hits.filter((hit) => hit.kind === 'case')).toEqual([{ kind: 'case', id: 'c1', title: 'CL-1', detail: 'claim' }]);
    expect(server.find('GET', `${ENGINE}/history/case-instance`)[0]?.query.get('caseInstanceId')).toBe('CL-1');
  });
});

describe('incidents', () => {
  it('reads the incidents of an instance from the base plugin', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`POST ${PLUGIN}/incident/count`]: () => json({ count: 1 }),
      [`POST ${PLUGIN}/incident`]: () => json([{ id: 'i1' }]),
    });

    const page = await api.getScopedIncidents({
      processInstanceId: 'p1',
      sorting: { sortBy: 'incidentTimestamp', sortOrder: 'desc' },
      paging: { firstResult: 0, maxResults: 25 },
    });

    expect(page).toEqual({ count: 1, items: [{ id: 'i1' }] });
    expect(server.find('POST', `${PLUGIN}/incident`)[0]?.body).toEqual({
      processInstanceIdIn: ['p1'],
      sortBy: 'incidentTimestamp',
      sortOrder: 'desc',
    });
  });

  it('reads error details as plain text and resets retries of jobs and external tasks', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`GET ${ENGINE}/job/j1/stacktrace`]: () => text('java.lang.IllegalStateException: <b>boom</b>', 'text/plain'),
      [`GET ${ENGINE}/external-task/x1/errorDetails`]: () => empty(),
      [`PUT ${ENGINE}/job/j1/retries`]: () => empty(),
      [`PUT ${ENGINE}/external-task/x1/retries`]: () => empty(),
    });

    expect(await api.getFailureDetails('job', 'j1')).toBe('java.lang.IllegalStateException: <b>boom</b>');
    expect(await api.getFailureDetails('externalTask', 'x1')).toBe('');
    await api.setRetries('job', 'j1', 1);
    await api.setRetries('externalTask', 'x1', 3);

    expect(server.find('PUT', `${ENGINE}/job/j1/retries`)[0]?.body).toEqual({ retries: 1 });
    expect(server.find('PUT', `${ENGINE}/external-task/x1/retries`)[0]?.body).toEqual({ retries: 3 });
    expect(api.failureKind('failedJob')).toBe('job');
    expect(api.failureKind('failedExternalTask')).toBe('externalTask');
    expect(api.failureKind('custom')).toBeNull();
  });

  it('sets and clears the annotation of an incident', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`PUT ${ENGINE}/incident/i1/annotation`]: () => empty(),
      [`DELETE ${ENGINE}/incident/i1/annotation`]: () => empty(),
    });

    await api.setIncidentAnnotation('i1', 'looking into it');
    await api.setIncidentAnnotation('i1', null);

    expect(server.find('PUT', `${ENGINE}/incident/i1/annotation`)[0]?.body).toEqual({
      annotation: 'looking into it',
    });
    expect(server.find('DELETE', `${ENGINE}/incident/i1/annotation`)).toHaveLength(1);
  });
});

describe('deployments', () => {
  it('uploads files as multipart with the CSRF token', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`POST ${ENGINE}/deployment/create`]: () => json({ id: 'd1', name: 'loan' }),
    });
    const file = new File(['<definitions/>'], 'loan.bpmn', { type: 'text/xml' });

    const deployment = await api.createDeployment({
      name: 'loan',
      files: [file],
      tenantId: 'north',
      duplicateFiltering: true,
    });

    expect(deployment.id).toBe('d1');
    const [request] = server.find('POST', `${ENGINE}/deployment/create`);
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
    // the browser sets the content type together with the boundary
    expect(request?.headers.get('Content-Type')).toBeNull();
    const parts = request?.body as FormData;
    expect(parts.get('deployment-name')).toBe('loan');
    expect(parts.get('enable-duplicate-filtering')).toBe('true');
    expect(parts.get('deploy-changed-only')).toBe('true');
    expect(parts.get('tenant-id')).toBe('north');
    expect((parts.get('loan.bpmn') as File).name).toBe('loan.bpmn');
  });

  it('deletes a deployment with cascade', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`DELETE ${ENGINE}/deployment/d1`]: () => empty(),
    });

    await api.deleteDeployment('d1', { cascade: true, skipCustomListeners: true, skipIoMappings: false });

    expect(server.find('DELETE', `${ENGINE}/deployment/d1`)[0]?.query.get('cascade')).toBe('true');
  });

  it('returns a resource as text, whatever the server calls it', async () => {
    fakeServer({
      [`GET ${ENGINE}/deployment/d1/resources/r1/data`]: () => text('<definitions/>', 'application/xml'),
      [`GET ${ENGINE}/deployment/d1/resources/r2/data`]: () => json({ components: [] }),
    });

    expect(await api.getResourceText('d1', 'r1')).toBe('<definitions/>');
    expect(await api.getResourceText('d1', 'r2')).toBe('{\n  "components": []\n}');
    expect(api.resourceDataUrl('d1', 'r 1')).toBe(
      '/fasti/api/engine/engine/default/deployment/d1/resources/r%201/data',
    );
  });
});

describe('history and batches', () => {
  it('reports missing history instead of failing', async () => {
    fakeServer({
      [`GET ${ENGINE}/history/decision-instance/count`]: () =>
        json({ type: 'AuthorizationException', message: 'no' }, 403),
      [`GET ${ENGINE}/history/decision-instance`]: () =>
        json({ type: 'AuthorizationException', message: 'no' }, 403),
      [`GET ${ENGINE}/history/batch/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/history/batch`]: () => json([{ id: 'b0', type: 'instance-deletion' }]),
    });

    expect(
      await api.getDecisionInstances({ decisionDefinitionId: 'd:1:x', paging: { firstResult: 0, maxResults: 25 } }),
    ).toBeNull();
    expect(await api.getFinishedBatches({ firstResult: 0, maxResults: 25 })).toEqual({
      count: 1,
      items: [{ id: 'b0', type: 'instance-deletion' }],
    });
  });

  it('suspends and deletes a batch', async () => {
    const server = fakeServer({
      'GET /engine/engine': () => json([], 200, TOKEN),
      [`PUT ${ENGINE}/batch/b1/suspended`]: () => empty(),
      [`DELETE ${ENGINE}/batch/b1`]: () => empty(),
    });

    await api.setBatchSuspended('b1', true);
    await api.deleteBatch('b1', true);

    expect(server.find('PUT', `${ENGINE}/batch/b1/suspended`)[0]?.body).toEqual({ suspended: true });
    expect(server.find('DELETE', `${ENGINE}/batch/b1`)[0]?.query.get('cascade')).toBe('true');
  });
});
