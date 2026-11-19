/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import {
  assignTasks,
  historicQueryBody,
  matchedRules,
  modificationBody,
  restartBody,
  setInstancesJobRetriesAsync,
  percentile,
  summariseAges,
  summariseDurations,
} from '../api/operations';
import { Providers, createQueryClient, routes } from '../app/App';
import { preloadRoutes } from '../test/routes';
import type { ActivityBadge, ActivityMarker } from '../bpmn/BpmnDiagram';
import { empty, fakeServer, json, type FakeHandler } from '../test/server';
import { executedPath, historicQueryOf } from './HistoryPage';
import { migrationDiff, mappingToInstructions, planToMapping } from './MigrationPage';
import { flowNodes, formatDuration } from './model';
import { describeInstruction } from './ModifyDialog';
import { heatMarkers } from './StatisticsTab';
import { rowsToVariables } from './VariableRows';
import { setTenantScope, tenantScope } from '../lib/userPrefs';

preloadRoutes(routes);

vi.mock('../bpmn/BpmnDiagram', () => ({
  default: ({ badges, markers, label }: { badges?: ActivityBadge[]; markers?: ActivityMarker[]; label?: string }) => (
    <div data-testid="diagram" aria-label={label}>
      {markers?.map((marker) => <span key={`${marker.activityId}-${marker.marker}`}>{`${marker.activityId}:${marker.marker}`}</span>)}
      {badges?.map((badge) => <span key={badge.activityId}>{`${badge.activityId}=${badge.instances}/${badge.finished ?? 0}`}</span>)}
    </div>
  ),
}));
vi.mock('../dmn/DmnViewer', () => ({
  default: ({ highlightRules }: { highlightRules?: string[] }) => <div data-testid="dmn">{`rules: ${(highlightRules ?? []).join(',')}`}</div>,
}));

const ENGINE = '/engine/engine/default';
const PLUGIN = '/cockpit/plugin/base/default';
const V1 = 'order:1:a';
const V2 = 'order:2:b';
const MODEL = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="order">
    <startEvent id="start" />
    <userTask id="review" name="Review order" />
    <serviceTask id="ship" name="Ship order" />
    <endEvent id="end" />
  </process>
</definitions>`;

function definition(id: string, version: number) {
  return { id, key: 'order', name: 'Order', description: null, version, versionTag: null, tenantId: null, deploymentId: 'd1', resource: 'order.bpmn', suspended: false, historyTimeToLive: null };
}

function backend(routesOverride: Record<string, FakeHandler> = {}) {
  return fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
    [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    [`GET ${ENGINE}/process-definition/${V1}`]: () => json(definition(V1, 1)),
    [`GET ${ENGINE}/process-definition/${V2}`]: () => json(definition(V2, 2)),
    [`GET ${ENGINE}/process-definition`]: () => json([definition(V2, 2), definition(V1, 1)]),
    [`GET ${ENGINE}/process-definition/${V1}/xml`]: () => json({ id: V1, bpmn20Xml: MODEL }),
    [`GET ${ENGINE}/process-definition/${V2}/xml`]: () => json({ id: V2, bpmn20Xml: MODEL }),
    [`GET ${ENGINE}/process-definition/${V1}/statistics`]: () => json([{ id: 'review', instances: 2, failedJobs: 0, incidents: [] }]),
    [`POST ${PLUGIN}/process-instance/count`]: () => json({ count: 30 }),
    [`POST ${PLUGIN}/process-instance`]: () =>
      json([
        { id: 'p1', businessKey: 'A', startTime: '2026-03-01T09:30:00.000+0700', incidents: [], suspended: false },
        { id: 'p2', businessKey: 'B', startTime: '2026-03-01T09:31:00.000+0700', incidents: [], suspended: false },
      ]),
    [`GET ${ENGINE}/process-instance/p1`]: () => json({ id: 'p1', definitionId: V1, businessKey: 'A', suspended: false, tenantId: null }),
    [`GET ${ENGINE}/process-instance`]: () => json([]),
    [`GET ${ENGINE}/history/process-instance/p1`]: () =>
      json({ id: 'p1', businessKey: 'A', processDefinitionId: V1, processDefinitionKey: 'order', processDefinitionName: 'Order', processDefinitionVersion: 1, startTime: '2026-03-01T09:30:00.000+0700', endTime: '2026-03-01T10:30:00.000+0700', durationInMillis: 3_600_000, startUserId: 'mary', deleteReason: null, superProcessInstanceId: null, removalTime: null, tenantId: null, state: 'COMPLETED' }),
    [`GET ${ENGINE}/process-instance/p1/activity-instances`]: () =>
      json({ id: 'p1', activityId: 'order', activityName: 'Order', activityType: 'processDefinition', childActivityInstances: [{ id: 'review:1', activityId: 'review', activityName: 'Review order', activityType: 'userTask', childActivityInstances: [], childTransitionInstances: [] }], childTransitionInstances: [] }),
    [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: 0 }),
    [`GET ${ENGINE}/variable-instance`]: () => json([]),
    [`POST ${PLUGIN}/incident/count`]: () => json({ count: 0 }),
    [`POST ${PLUGIN}/incident`]: () => json([]),
    ...routesOverride,
  });
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers queryClient={createQueryClient()} locale="en">
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe('request bodies', () => {
  it('builds a modification with variables and an exact cancellation', () => {
    const body = modificationBody(
      [
        { type: 'startBeforeActivity', activityId: 'ship', variables: { amount: { value: 3, type: 'Integer' } } },
        { type: 'cancel', activityId: 'review', activityInstanceId: 'review:1' },
        { type: 'cancel', activityId: 'ship', transitionInstanceId: 'ship:t1' },
        { type: 'cancel', activityId: 'review' },
      ],
      { skipCustomListeners: true, skipIoMappings: false, annotation: '' },
    );
    expect(body).toEqual({
      skipCustomListeners: true,
      skipIoMappings: false,
      annotation: undefined,
      instructions: [
        { type: 'startBeforeActivity', activityId: 'ship', variables: { amount: { value: 3, type: 'Integer', local: false } } },
        { type: 'cancel', activityInstanceId: 'review:1' },
        { type: 'cancel', transitionInstanceId: 'ship:t1' },
        { type: 'cancel', activityId: 'review' },
      ],
    });
  });

  it('turns history filters into the query of the engine', () => {
    expect(historicQueryBody({ state: 'canceled', businessKeyLike: 'A', withIncidents: true })).toMatchObject({
      orQueries: [{ externallyTerminated: true, internallyTerminated: true }],
      processInstanceBusinessKeyLike: '%A%',
      withIncidents: true,
      finished: undefined,
    });
    const params = new URLSearchParams({ hstate: 'finished', hvar: 'amount=5', hkey: 'order' });
    expect(historicQueryOf(params)).toMatchObject({ state: 'finished', processDefinitionKey: 'order', variables: [{ name: 'amount', operator: 'eq', value: 5 }] });
    expect(historicQueryOf(params, V1).processDefinitionKey).toBeUndefined();
    expect(historicQueryOf(new URLSearchParams({ hvar: 'done=true' })).variables).toEqual([{ name: 'done', operator: 'eq', value: true }]);
    expect(historicQueryOf(new URLSearchParams({ hvar: 'code="5"' })).variables).toEqual([{ name: 'code', operator: 'eq', value: '5' }]);
  });

  it('counts internally and externally terminated instances as canceled, also in batches', () => {
    const canceled = historicQueryBody({ state: 'canceled' });
    expect(canceled).toEqual(expect.objectContaining({ orQueries: [{ externallyTerminated: true, internallyTerminated: true }] }));
    expect(canceled.externallyTerminated).toBeUndefined();
    const common = { instructions: [], initialVariables: true, withoutBusinessKey: false, skipCustomListeners: false, skipIoMappings: true };
    expect(restartBody({ ...common, selection: { kind: 'query', query: { state: 'canceled' } } }).historicProcessInstanceQuery).toEqual(canceled);
  });

  it('restarts by ids or by a history query', () => {
    const common = { instructions: [], initialVariables: true, withoutBusinessKey: false, skipCustomListeners: false, skipIoMappings: true };
    expect(restartBody({ ...common, selection: { kind: 'ids', ids: ['h1'] } })).toMatchObject({ processInstanceIds: ['h1'], initialVariables: true, skipIoMappings: true });
    expect(restartBody({ ...common, selection: { kind: 'query', query: { processDefinitionId: V1, state: 'completed' } } })).toMatchObject({
      historicProcessInstanceQuery: { processDefinitionId: V1, completed: true },
    });
  });
});

describe('job retries of instances', () => {
  it('sends picked ids as processInstances, the name the engine reads', async () => {
    const server = fakeServer({ [`POST ${ENGINE}/process-instance/job-retries`]: () => json({ id: 'b9', type: 'set-job-retries' }) });
    await setInstancesJobRetriesAsync({ kind: 'ids', ids: ['p1', 'p2'] }, 3);
    await setInstancesJobRetriesAsync({ kind: 'query', query: { processDefinitionId: V1 }, count: 30 }, 1);
    expect(server.find('POST', `${ENGINE}/process-instance/job-retries`).map((request) => request.body)).toEqual([
      { processInstances: ['p1', 'p2'], retries: 3 },
      { processInstanceQuery: { processDefinitionId: V1 }, retries: 1 },
    ]);
  });
});

describe('task assignment', () => {
  it('claims with /claim and reports a conflict, gives back with /unclaim, assigns with /assignee', async () => {
    const server = fakeServer({
      [`POST ${ENGINE}/task/t1/claim`]: () => empty(),
      [`POST ${ENGINE}/task/t2/claim`]: () => json({ type: 'TaskAlreadyClaimedException', message: 'already claimed' }, 500),
      [`POST ${ENGINE}/task/t1/unclaim`]: () => empty(),
      [`POST ${ENGINE}/task/t1/assignee`]: () => empty(),
    });
    expect(await assignTasks(['t1', 't2'], { kind: 'claim', userId: 'demo' })).toEqual(['t2']);
    expect(server.find('POST', `${ENGINE}/task/t1/claim`)[0]?.body).toEqual({ userId: 'demo' });
    expect(await assignTasks(['t1'], { kind: 'unclaim' })).toEqual([]);
    expect(await assignTasks(['t1'], { kind: 'assign', userId: 'john' })).toEqual([]);
    expect(server.find('POST', `${ENGINE}/task/t1/assignee`)[0]?.body).toEqual({ userId: 'john' });
    expect(server.find('POST', `${ENGINE}/task/t1/assignee`)).toHaveLength(1);
  });
});

describe('pure helpers', () => {
  it('summarises durations per activity, slowest first', () => {
    expect(
      summariseDurations([
        { activityId: 'a', durationInMillis: 100 },
        { activityId: 'a', durationInMillis: 300 },
        { activityId: 'b', durationInMillis: 1000 },
        { activityId: 'b', durationInMillis: null },
      ]),
    ).toEqual([
      { activityId: 'b', count: 1, average: 1000, minimum: 1000, maximum: 1000, p90: 1000 },
      { activityId: 'a', count: 2, average: 200, minimum: 100, maximum: 300, p90: 300 },
    ]);
  });

  it('finds the time nine in ten runs stay within and how long active ones have waited', () => {
    const values = Array.from({ length: 20 }, (_, index) => (index + 1) * 10);
    expect(percentile(values, 0.9)).toBe(180);
    expect(percentile([5], 0.9)).toBe(5);
    expect(percentile([], 0.9)).toBe(0);
    const now = Date.parse('2026-03-02T00:00:00.000Z');
    expect(
      summariseAges(
        [
          { activityId: 'review', startTime: '2026-03-01T00:00:00.000Z' },
          { activityId: 'review', startTime: '2026-03-01T12:00:00.000Z' },
          { activityId: 'ship', startTime: '2026-03-01T23:00:00.000Z' },
          { activityId: 'ship', startTime: '' },
        ],
        now,
      ),
    ).toEqual([
      { activityId: 'review', count: 2, oldest: 86_400_000 },
      { activityId: 'ship', count: 1, oldest: 3_600_000 },
    ]);
  });

  it('puts activities into five heat steps', () => {
    const base = { canceled: 0, completeScope: 0, openIncidents: 0, resolvedIncidents: 0, deletedIncidents: 0 };
    expect(
      heatMarkers([
        { ...base, id: 'a', instances: 0, finished: 100 },
        { ...base, id: 'b', instances: 1, finished: 9 },
        { ...base, id: 'c', instances: 0, finished: 0 },
      ]),
    ).toEqual([
      { activityId: 'a', marker: 'heat-5' },
      { activityId: 'b', marker: 'heat-1' },
    ]);
  });

  it('marks the path of an instance', () => {
    const activity = { activityName: null, activityType: 'userTask', startTime: '', durationInMillis: 1, assignee: null, completeScope: false, calledProcessInstanceId: null };
    const path = executedPath([
      { ...activity, id: '1', activityId: 'review', endTime: 'x', canceled: false },
      { ...activity, id: '2', activityId: 'review', endTime: 'x', canceled: false },
      { ...activity, id: '3', activityId: 'ship', endTime: 'x', canceled: true },
      { ...activity, id: '4', activityId: 'end', endTime: null, canceled: false },
    ]);
    expect(path.markers).toEqual([
      { activityId: 'review', marker: 'is-done' },
      { activityId: 'ship', marker: 'is-canceled' },
      { activityId: 'end', marker: 'is-done' },
    ]);
    expect(path.badges[0]).toEqual({ activityId: 'review', instances: 0, finished: 2, incidents: 0 });
  });

  it('keeps the rules that matched in the order of the table', () => {
    const output = (ruleId: string, ruleOrder: number) => ({ id: ruleId + ruleOrder, clauseId: null, clauseName: null, ruleId, ruleOrder, type: 'String', value: 'x' });
    expect(matchedRules({ outputs: [output('r3', 3), output('r1', 1), output('r3', 3)] })).toEqual(['r1', 'r3']);
  });

  it('converts a migration plan to a mapping and back', () => {
    const mapping = planToMapping({
      instructions: [{ sourceActivityIds: ['review'], targetActivityIds: ['review2'], updateEventTrigger: true }],
    });
    expect(mapping).toEqual({ review: { target: 'review2', updateEventTrigger: true } });
    expect(mappingToInstructions({ ...mapping, ship: { target: '', updateEventTrigger: false } })).toEqual([
      { sourceActivityIds: ['review'], targetActivityIds: ['review2'], updateEventTrigger: true },
    ]);
  });

  it('checks variable rows', () => {
    expect(rowsToVariables([{ name: 'a', type: 'Integer', value: '4' }, { name: '', type: 'String', value: 'ignored' }])).toEqual({
      ok: true,
      variables: { a: { value: 4, type: 'Integer' } },
    });
    expect(rowsToVariables([{ name: 'a', type: 'String', value: '' }, { name: 'a', type: 'String', value: '' }])).toMatchObject({ ok: false, row: 1 });
    expect(rowsToVariables([{ name: 'j', type: 'Json', value: '{' }])).toMatchObject({ ok: false, error: 'cockpit.variable.invalidJson' });
  });

  it('reads flow nodes and formats durations', () => {
    expect(flowNodes(MODEL).map((node) => node.id)).toEqual(['start', 'review', 'ship', 'end']);
    expect(formatDuration(850)).toBe('850 ms');
    expect(formatDuration(3_723_000)).toBe('1 hr 2 min');
    expect(formatDuration(90_000, 'de')).toBe('1 Min. 30 Sek.');
    expect(formatDuration(null)).toBe('—');
    const t = (key: string, params?: Record<string, string | number>) => `${key}:${JSON.stringify(params)}`;
    expect(describeInstruction({ type: 'cancel', activityId: 'review' }, (id) => id.toUpperCase(), t as never)).toContain('REVIEW');
  });
});

describe('instance modification', () => {
  it('reviews the instructions and then modifies the instance', async () => {
    const user = userEvent.setup();
    const server = backend({ [`POST ${ENGINE}/process-instance/p1/modification`]: () => empty() });
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'Modify' }));
    const dialog = await screen.findByRole('dialog', { name: 'Modify instance' });
    await user.selectOptions(within(dialog).getByLabelText('Activity'), 'ship');
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));
    await user.selectOptions(within(dialog).getByLabelText('Instruction'), 'cancel');
    await user.selectOptions(within(dialog).getByLabelText('Activity'), 'review');
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));
    await user.click(within(dialog).getByRole('button', { name: 'Add variable' }));
    await user.type(within(dialog).getByLabelText('Name of variable 1'), 'priority');
    await user.type(within(dialog).getByLabelText('Value of variable 1'), 'high');
    await user.click(within(dialog).getByRole('button', { name: 'Review' }));

    expect(within(dialog).getByText('Start before “Ship order”')).toBeVisible();
    expect(within(dialog).getByText('Cancel all instances of “Review order”')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/process-instance/p1/modification`)).toHaveLength(0);

    await user.click(within(dialog).getByRole('button', { name: 'Modify instance' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-instance/p1/modification`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/process-instance/p1/modification`)[0]?.body).toMatchObject({
      instructions: [
        { type: 'startBeforeActivity', activityId: 'ship', variables: { priority: { value: 'high', type: 'String', local: false } } },
        { type: 'cancel', activityId: 'review' },
      ],
    });
  });
});

describe('cancelling a transition instance', () => {
  it('cancels a token at an asynchronous continuation by its transition instance id', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`GET ${ENGINE}/process-instance/p1/activity-instances`]: () =>
        json({
          id: 'p1', activityId: 'order', activityName: 'Order', activityType: 'processDefinition',
          childActivityInstances: [{ id: 'ship:1', activityId: 'ship', activityName: 'Ship order', activityType: 'serviceTask', childActivityInstances: [], childTransitionInstances: [] }],
          childTransitionInstances: [{ id: 'ship:t1', activityId: 'ship', activityName: 'Ship order', activityType: 'serviceTask' }],
        }),
      [`POST ${ENGINE}/process-instance/p1/modification`]: () => empty(),
    });
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'Modify' }));
    const dialog = await screen.findByRole('dialog', { name: 'Modify instance' });
    await user.selectOptions(within(dialog).getByLabelText('Instruction'), 'cancel');
    await user.selectOptions(within(dialog).getByLabelText('Activity'), 'ship');
    await user.selectOptions(within(dialog).getByLabelText('Which'), 'ship:t1');
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));
    await user.click(within(dialog).getByRole('button', { name: 'Review' }));
    await user.click(within(dialog).getByRole('button', { name: 'Modify instance' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-instance/p1/modification`)).toHaveLength(1));
    expect((server.find('POST', `${ENGINE}/process-instance/p1/modification`)[0]?.body as { instructions: unknown[] }).instructions).toEqual([
      { type: 'cancel', transitionInstanceId: 'ship:t1' },
    ]);
  });
});

describe('bulk operations', () => {
  it('suspends every matching instance in a batch after a confirmation with the count', async () => {
    const user = userEvent.setup();
    const server = backend({ [`POST ${ENGINE}/process-instance/suspended-async`]: () => json({ id: 'b1', type: 'suspend' }) });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance p1' }));
    expect(screen.getByText('1 selected')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Select all 30 matching' }));
    expect(screen.getByText('30 selected')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Suspend' }));
    const dialog = await screen.findByRole('dialog', { name: 'Suspend 30 instances' });
    await user.click(within(dialog).getByRole('button', { name: 'Apply to 30 instances' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-instance/suspended-async`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/process-instance/suspended-async`)[0]?.body).toEqual({
      processInstanceQuery: { processDefinitionId: V1 },
      suspended: true,
    });
  });

  it('drops the selection when the filter of the list changes', async () => {
    const user = userEvent.setup();
    backend();
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance p1' }));
    await user.click(screen.getByRole('button', { name: 'Select all 30 matching' }));
    expect(screen.getByText('30 selected')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'With incidents only' }));
    await waitFor(() => expect(screen.queryByText('30 selected')).toBeNull());
    expect(screen.queryByRole('region', { name: 'Selected instances' })).toBeNull();
  });

  it('does not let the rows of the old filter be picked while the new ones load', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}`);
    const row = await screen.findByRole('checkbox', { name: 'Select instance p1' });
    expect(row).toBeEnabled();

    server.on(`POST ${PLUGIN}/process-instance`, () => new Promise<Response>(() => {}));
    await user.click(screen.getByRole('button', { name: 'With incidents only' }));
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Select instance p1' })).toBeDisabled());
  });

  it('drops the selection when the tenant changes', async () => {
    const user = userEvent.setup();
    backend({
      [`GET ${ENGINE}/tenant`]: () => json([{ id: 'ta', name: 'Tenant A' }, { id: 'tb', name: 'Tenant B' }]),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance p1' }));
    expect(screen.getByRole('region', { name: 'Selected instances' })).toBeVisible();
    await user.click(await screen.findByRole('button', { name: /^Tenant: / }));
    await user.click(await screen.findByRole('menuitemradio', { name: 'Tenant B' }));
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Selected instances' })).toBeNull());
    setTenantScope(null);
  });

  it('drops the history selection when the tenant changes', async () => {
    const user = userEvent.setup();
    const historic = {
      id: 'h1', businessKey: 'A', processDefinitionId: V1, processDefinitionKey: 'order', processDefinitionName: 'Order',
      processDefinitionVersion: 1, startTime: '2026-03-01T09:30:00.000+0700', endTime: '2026-03-01T10:30:00.000+0700',
      durationInMillis: 3_600_000, startUserId: 'mary', deleteReason: null, superProcessInstanceId: null, removalTime: null,
      tenantId: null, state: 'COMPLETED',
    };
    backend({
      [`GET ${ENGINE}/tenant`]: () => json([{ id: 'ta', name: 'Tenant A' }, { id: 'tb', name: 'Tenant B' }]),
      [`POST ${ENGINE}/history/process-instance/count`]: () => json({ count: 1 }),
      [`POST ${ENGINE}/history/process-instance`]: () => json([historic]),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?tab=history`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance h1' }));
    expect(screen.getByRole('checkbox', { name: 'Select instance h1' })).toBeChecked();
    await user.click(await screen.findByRole('button', { name: /^Tenant: / }));
    await user.click(await screen.findByRole('menuitemradio', { name: 'Tenant B' }));
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Select instance h1' })).not.toBeChecked());
    setTenantScope(null);
  });

  it('deletes selected instances with the chosen options', async () => {
    const user = userEvent.setup();
    const server = backend({ [`POST ${ENGINE}/process-instance/delete`]: () => json({ id: 'b2', type: 'instance-deletion' }) });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select this page' }));
    await user.click(screen.getByRole('button', { name: 'Actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Cancel and delete…' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete 2 instances' });
    expect(await within(dialog).findByText('For example')).toBeVisible();
    expect(within(dialog).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['p1', 'p2']);
    await user.click(within(dialog).getByRole('checkbox', { name: /Skip custom listeners|listeners/i }));
    await user.click(within(dialog).getByRole('button', { name: 'Delete 2 instances' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-instance/delete`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/process-instance/delete`)[0]?.body).toMatchObject({
      processInstanceIds: ['p1', 'p2'],
      skipCustomListeners: true,
    });
  });
});

describe('views of the history', () => {
  it('shows a built-in view, saves the filters as a view of the user and removes it', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/history/process-instance/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/history/process-instance`]: () => json([]),
    });
    const router = renderApp('/cockpit/history');

    await user.click(await screen.findByRole('button', { name: 'Failing now' }));
    await waitFor(() => expect(router.state.location.search).toContain('view=failing'));
    expect(router.state.location.search).toContain('hinc=open');
    expect(screen.getByRole('button', { name: 'Failing now' })).toHaveAttribute('aria-pressed', 'true');
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/history/process-instance`).at(-1)?.body).toMatchObject({ withIncidents: true, incidentStatus: 'open', unfinished: true }),
    );

    // another filter leaves the view
    await user.selectOptions(screen.getByRole('combobox', { name: 'State' }), 'finished');
    await waitFor(() => expect(router.state.location.search).not.toContain('view='));

    await user.click(screen.getByRole('button', { name: 'Save view' }));
    await user.type(screen.getByRole('textbox', { name: 'Name of the view' }), 'Finished with errors');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    const saved = await screen.findByRole('button', { name: 'Finished with errors' });
    expect(saved).toHaveAttribute('aria-pressed', 'true');
    expect(JSON.parse(window.localStorage.getItem('fasti.next.cockpit.history.views') ?? '[]')).toEqual([
      { name: 'Finished with errors', params: { hstate: 'finished', hinc: 'open' } },
    ]);

    await user.click(screen.getByRole('button', { name: 'Started today' }));
    await waitFor(() => expect(router.state.location.search).not.toContain('hinc'));
    await user.click(screen.getByRole('button', { name: 'Finished with errors' }));
    await waitFor(() => expect(router.state.location.search).toContain('hinc=open'));

    await user.click(screen.getByRole('button', { name: 'Remove the view Finished with errors' }));
    expect(screen.queryByRole('button', { name: 'Finished with errors' })).not.toBeInTheDocument();
  });
});

describe('migration', () => {
  it('proposes a mapping, validates it and migrates the chosen instances', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/migration/generate`]: () =>
        json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2, instructions: [{ sourceActivityIds: ['review'], targetActivityIds: ['review'] }] }),
      [`POST ${ENGINE}/migration/validate`]: () => json({ instructionReports: [], variableReports: {} }),
      [`POST ${ENGINE}/migration/executeAsync`]: () => json({ id: 'b3', type: 'instance-migration' }),
    });
    renderApp(`/cockpit/migration?source=${encodeURIComponent(V1)}&ids=p1,p2`);

    expect(await screen.findByText('2 selected instances')).toBeVisible();
    const target = await screen.findByLabelText('Target of Ship order');
    await waitFor(() => expect(screen.getByLabelText('Target of Review order')).toHaveValue('review'));
    expect(server.find('POST', `${ENGINE}/migration/generate`)[0]?.body).toMatchObject({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2 });
    await user.selectOptions(target, 'ship');

    await user.click(screen.getByRole('button', { name: 'Validate' }));
    expect(await screen.findByText('The plan is valid')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Review' }));
    const dialog = await screen.findByRole('dialog', { name: 'Migrate 2 instances' });
    await user.click(within(dialog).getByRole('button', { name: 'Migrate 2 instances' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/migration/executeAsync`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/migration/executeAsync`)[0]?.body).toMatchObject({
      processInstanceIds: ['p1', 'p2'],
      migrationPlan: {
        instructions: [
          { sourceActivityIds: ['review'], targetActivityIds: ['review'] },
          { sourceActivityIds: ['ship'], targetActivityIds: ['ship'] },
        ],
      },
    });
  });
});

describe('migration differences', () => {
  it('compares the versions under a mapping', () => {
    const node = (id: string) => ({ id, name: id, type: 'userTask' });
    expect(
      migrationDiff([node('a'), node('b'), node('c')], [node('a'), node('x')], { a: { target: 'a', updateEventTrigger: false } }, new Map([['b', 3]])),
    ).toEqual({ mapped: [node('a')], unmapped: [node('b'), node('c')], blocked: [node('b')], added: [node('x')] });
  });

  it('shows where waiting instances have no target and what is new, side by side', async () => {
    const user = userEvent.setup();
    backend({
      [`POST ${ENGINE}/migration/generate`]: () =>
        json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2, instructions: [{ sourceActivityIds: ['ship'], targetActivityIds: ['ship'] }] }),
    });
    renderApp(`/cockpit/migration?source=${encodeURIComponent(V1)}&ids=p1`);

    const diff = await screen.findByRole('region', { name: 'Differences between the versions' });
    await waitFor(() => expect(within(diff).getByText('Mapped: 1')).toBeVisible());
    expect(within(diff).getByText('Not mapped, with instances waiting: 1')).toBeVisible();
    expect(within(diff).getByText('Only in the new version: 0')).toBeVisible();
    // the old version marks the activity whose instances would be left behind, the new one what is new
    expect(screen.getAllByText('review:is-blocked').length).toBeGreaterThan(0);
    expect(screen.getAllByText('ship:is-mapped').length).toBe(2);
    await user.click(within(diff).getByRole('button', { name: 'Review order' }));
    expect(screen.getByLabelText('Target of Review order').closest('tr')).toHaveClass('is-selected');
  });
});

describe('tenant scope', () => {
  it('drops a chosen tenant the user no longer belongs to', async () => {
    setTenantScope('gone');
    backend({
      [`GET ${ENGINE}/tenant`]: () => json([{ id: 'ta', name: 'Tenant A' }]),
    });
    renderApp('/cockpit/processes');
    await waitFor(() => expect(tenantScope()).toBeNull());
    expect(await screen.findByRole('button', { name: /^Tenant: / })).toBeVisible();
  });
});

describe('statistics of a process', () => {
  it('shows a failed request of the statistics instead of an empty diagram', async () => {
    backend({
      [`GET ${ENGINE}/history/process-definition/${V1}/statistics`]: () => json({ type: 'InvalidRequestException', message: 'bad' }, 400),
      [`GET ${ENGINE}/history/activity-instance`]: () => json([]),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?tab=statistics`);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeVisible();
  });
});

describe('heat map of a process', () => {
  it('colors the activities by their p90 duration or by how long instances wait there now', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`GET ${ENGINE}/history/process-definition/${V1}/statistics`]: () => json([]),
      [`GET ${ENGINE}/history/activity-instance`]: (request) =>
        json(
          request.query.get('unfinished') === 'true'
            ? [{ id: 'w1', activityId: 'review', startTime: new Date(Date.now() - 7_200_000).toISOString(), endTime: null, durationInMillis: null }]
            : [
                ...Array.from({ length: 10 }, (_, index) => ({ id: `r${index}`, activityId: 'review', durationInMillis: 1000 * (index + 1) })),
                { id: 's1', activityId: 'ship', durationInMillis: 60_000 },
              ],
        ),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?tab=statistics`);

    const measure = await screen.findByRole('group', { name: 'Colors show' });
    expect(within(measure).getByRole('button', { name: 'How often reached' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(within(measure).getByRole('button', { name: 'Duration (p90)' }));
    expect(await screen.findByText('ship:heat-5')).toBeInTheDocument();
    expect(screen.getByText('review:heat-1')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'p90' })).toBeVisible();

    await user.click(within(measure).getByRole('button', { name: 'Waiting now' }));
    expect(await screen.findByText('review:heat-5')).toBeInTheDocument();
    const waiting = await screen.findByRole('table', { name: 'Waiting now' });
    expect(within(waiting).getByText(/^2 hr/)).toBeVisible();
    expect(server.find('GET', `${ENGINE}/history/activity-instance`).some((request) => request.query.get('unfinished') === 'true')).toBe(true);
  });
});

describe('migration plan checks', () => {
  it('drops the result of a validation when the plan changes', async () => {
    const user = userEvent.setup();
    backend({
      [`POST ${ENGINE}/migration/generate`]: () =>
        json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2, instructions: [{ sourceActivityIds: ['review'], targetActivityIds: ['review'] }] }),
      [`POST ${ENGINE}/migration/validate`]: () => json({ instructionReports: [], variableReports: {} }),
    });
    renderApp(`/cockpit/migration?source=${encodeURIComponent(V1)}&ids=p1`);
    await waitFor(() => expect(screen.getByLabelText('Target of Review order')).toHaveValue('review'));
    await user.click(screen.getByRole('button', { name: 'Validate' }));
    expect(await screen.findByText('The plan is valid')).toBeVisible();

    await user.click(screen.getByLabelText('Update the event trigger of Review order'));
    expect(screen.queryByText('The plan is valid')).toBeNull();
  });

  it('shows a failed diagram with a retry instead of loading forever', async () => {
    backend({
      [`GET ${ENGINE}/process-definition/${V2}/xml`]: () => json({ type: 'AuthorizationException', message: 'not allowed' }, 403),
      [`POST ${ENGINE}/migration/generate`]: () => json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2, instructions: [] }),
    });
    renderApp(`/cockpit/migration?source=${encodeURIComponent(V1)}&ids=p1`);
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeVisible();
  });
});

describe('migration target change', () => {
  it('drops the old mapping and blocks execution while the new plan fails', async () => {
    const user = userEvent.setup();
    const V3 = 'order:3:c';
    backend({
      [`GET ${ENGINE}/process-definition`]: () => json([definition(V3, 3), definition(V2, 2), definition(V1, 1)]),
      [`GET ${ENGINE}/process-definition/${V3}/xml`]: () => json({ id: V3, bpmn20Xml: MODEL }),
      [`POST ${ENGINE}/migration/generate`]: (request) =>
        (request.body as { targetProcessDefinitionId: string }).targetProcessDefinitionId === V3
          ? json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V3, instructions: [{ sourceActivityIds: ['review'], targetActivityIds: ['review'] }] })
          : json({ type: 'BadUserRequestException', message: 'no plan' }, 400),
    });
    renderApp(`/cockpit/migration?source=${encodeURIComponent(V1)}&ids=p1`);

    await waitFor(() => expect(screen.getByLabelText('Target of Review order')).toHaveValue('review'));
    expect(screen.getByRole('button', { name: 'Review' })).toBeEnabled();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Target version' }), V2);
    await waitFor(() => expect(screen.getByLabelText('Target of Review order')).toHaveValue(''));
    expect(screen.getByRole('button', { name: 'Review' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Validate' })).toBeDisabled();
  });
});

describe('migration of a filtered list', () => {
  it('migrates only the instances the filter finds and confirms their count', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/process-instance/count`]: () => json({ count: 7 }),
      [`POST ${ENGINE}/migration/generate`]: () =>
        json({ sourceProcessDefinitionId: V1, targetProcessDefinitionId: V2, instructions: [{ sourceActivityIds: ['review'], targetActivityIds: ['review'] }] }),
      [`POST ${ENGINE}/migration/executeAsync`]: () => json({ id: 'b4', type: 'instance-migration' }),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?bk=A&inc=1`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance p1' }));
    await user.click(screen.getByRole('button', { name: 'Select all 30 matching' }));
    await user.click(screen.getByRole('button', { name: 'Actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Migrate' }));

    expect(await screen.findByText('7 running instances that match the filter of the list')).toBeVisible();
    const expected = { processDefinitionId: V1, businessKey: 'A', withIncident: true };
    expect(server.find('POST', `${ENGINE}/process-instance/count`)[0]?.body).toEqual(expected);
    await waitFor(() => expect(screen.getByLabelText('Target of Review order')).toHaveValue('review'));
    await user.click(screen.getByRole('button', { name: 'Review' }));
    const dialog = await screen.findByRole('dialog', { name: 'Migrate 7 instances' });
    await user.click(within(dialog).getByRole('button', { name: 'Migrate 7 instances' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/migration/executeAsync`)).toHaveLength(1));
    const body = server.find('POST', `${ENGINE}/migration/executeAsync`)[0]?.body as Record<string, unknown>;
    expect(body.processInstanceQuery).toEqual(expected);
    expect(body.processInstanceIds).toBeUndefined();
  });
});

describe('history', () => {
  const historic = {
    id: 'h1', businessKey: 'Z', processDefinitionId: V1, processDefinitionKey: 'order', processDefinitionName: 'Order', processDefinitionVersion: 1,
    startTime: '2026-03-01T09:30:00.000+0700', endTime: '2026-03-01T10:30:00.000+0700', durationInMillis: 3_600_000, startUserId: null,
    deleteReason: null, superProcessInstanceId: null, removalTime: null, tenantId: null, state: 'COMPLETED',
  };

  it('restarts a finished instance from the history of a version', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/history/process-instance/count`]: () => json({ count: 1 }),
      [`POST ${ENGINE}/history/process-instance`]: () => json([historic]),
      [`POST ${ENGINE}/process-definition/${V1}/restart`]: () => empty(),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?tab=history`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance h1' }));
    await user.click(screen.getByRole('button', { name: 'Restart…' }));
    const dialog = await screen.findByRole('dialog', { name: 'Restart 1 instance' });
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
    await user.selectOptions(within(dialog).getByLabelText('Activity'), 'review');
    await user.click(within(dialog).getByRole('button', { name: 'Add' }));
    await user.click(within(dialog).getByRole('button', { name: 'Review' }));
    await user.click(within(dialog).getByRole('button', { name: 'Restart 1 instance' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-definition/${V1}/restart`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/process-definition/${V1}/restart`)[0]?.body).toMatchObject({
      processInstanceIds: ['h1'],
      instructions: [{ type: 'startBeforeActivity', activityId: 'review' }],
      initialVariables: true,
    });
  });

  it('restarts at the start event of the process unless told otherwise, never without an instruction', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/history/process-instance/count`]: () => json({ count: 1 }),
      [`POST ${ENGINE}/history/process-instance`]: () => json([historic]),
      [`POST ${ENGINE}/process-definition/${V1}/restart`]: () => empty(),
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(V1)}?tab=history`);

    await user.click(await screen.findByRole('checkbox', { name: 'Select instance h1' }));
    await user.click(screen.getByRole('button', { name: 'Restart…' }));
    const dialog = await screen.findByRole('dialog', { name: 'Restart 1 instance' });
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));
    expect(within(dialog).getByRole('button', { name: 'Review' })).toBeDisabled();
    expect(within(dialog).getByText(/Add where the instances start again/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Back' }));

    await user.click(screen.getByRole('button', { name: 'Restart…' }));
    const again = await screen.findByRole('dialog', { name: 'Restart 1 instance' });
    await user.click(within(again).getByRole('button', { name: 'Review' }));
    await user.click(within(again).getByRole('button', { name: 'Restart 1 instance' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/process-definition/${V1}/restart`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/process-definition/${V1}/restart`)[0]?.body).toMatchObject({
      instructions: [{ type: 'startBeforeActivity', activityId: 'start' }],
    });
  });

  it('shows the path, the variables and their changes of a finished instance', async () => {
    const user = userEvent.setup();
    backend({
      [`GET ${ENGINE}/history/process-instance/h1`]: () => json(historic),
      [`GET ${ENGINE}/history/activity-instance`]: () =>
        json([
          { id: 'x1', activityId: 'review', activityName: 'Review order', activityType: 'userTask', startTime: '2026-03-01T09:30:00.000+0700', endTime: '2026-03-01T09:40:00.000+0700', durationInMillis: 600_000, assignee: null, canceled: false, completeScope: false, calledProcessInstanceId: null },
        ]),
      [`GET ${ENGINE}/history/variable-instance`]: () => json([{ id: 'hv1', name: 'amount', type: 'Integer', value: 5, activityInstanceId: 'h1', createTime: null, state: 'CREATED', errorMessage: null }]),
      [`GET ${ENGINE}/history/detail`]: () =>
        json([
          { id: 'd1', time: '2026-03-01T09:30:00.000+0700', variableName: 'amount', variableType: 'Integer', value: 3, revision: 0, userOperationId: null, errorMessage: null },
          { id: 'd2', time: '2026-03-01T09:35:00.000+0700', variableName: 'amount', variableType: 'Integer', value: 5, revision: 1, userOperationId: null, errorMessage: null },
        ]),
      [`GET ${ENGINE}/history/incident`]: () => json([]),
      [`GET ${ENGINE}/history/job-log`]: () => json([]),
    });
    renderApp('/cockpit/history/instance/h1');

    expect(await screen.findByText('review:is-done')).toBeInTheDocument();
    expect(screen.getAllByText('Completed')[0]).toBeVisible();
    expect(await screen.findByText('10 min')).toBeVisible();

    await user.click(screen.getByRole('tab', { name: 'Variables' }));
    await user.click(await screen.findByRole('button', { name: /^Changes: / }));
    const dialog = await screen.findByRole('dialog', { name: 'Changes of amount' });
    expect(within(dialog).getByText('3')).toBeVisible();
    expect(within(dialog).getByText('5')).toBeVisible();
  });
});

describe('decision history', () => {
  it('shows inputs, outputs and the rules that matched', async () => {
    backend({
      [`GET ${ENGINE}/history/decision-instance/e1`]: () =>
        json({
          id: 'e1', decisionDefinitionId: 'dish:1', decisionDefinitionKey: 'dish', decisionDefinitionName: 'Dish', evaluationTime: '2026-03-01T09:30:00.000+0700',
          processDefinitionKey: null, processInstanceId: null, activityId: null, userId: 'demo', rootDecisionInstanceId: null, collectResultValue: null,
          inputs: [{ id: 'i1', clauseId: 'c1', clauseName: 'Season', type: 'String', value: 'Winter' }],
          outputs: [{ id: 'o1', clauseId: 'c2', clauseName: 'Dish', variableName: 'desiredDish', ruleId: 'rule4', ruleOrder: 4, type: 'String', value: 'Roastbeef' }],
        }),
      [`GET ${ENGINE}/decision-definition/dish:1/xml`]: () => json({ dmnXml: '<definitions />' }),
    });
    renderApp('/cockpit/decision-history/e1');

    expect(await screen.findByText('Winter')).toBeVisible();
    expect(screen.getByText('Roastbeef')).toBeVisible();
    expect(screen.getByText('1 rule matched')).toBeVisible();
    expect(await screen.findByText('rules: rule4')).toBeInTheDocument();
  });
});

describe('operation log', () => {
  it('filters the log and edits an annotation', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`GET ${ENGINE}/history/user-operation/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/history/user-operation`]: () =>
        json([
          { id: 'l1', operationId: 'op1', operationType: 'Suspend', entityType: 'ProcessInstance', category: 'Operator', property: 'suspensionState', orgValue: null, newValue: 'suspended', userId: 'demo', timestamp: '2026-03-01T09:30:00.000+0700', processInstanceId: 'p1', processDefinitionKey: 'order', annotation: null },
        ]),
      [`PUT ${ENGINE}/history/user-operation/op1/set-annotation`]: () => empty(),
    });
    renderApp('/cockpit/audit?user=demo');

    expect(await screen.findByText('Suspend')).toBeVisible();
    await waitFor(() => expect(server.find('GET', `${ENGINE}/history/user-operation`)[0]?.query.get('userId')).toBe('demo'));
    await user.click(screen.getByRole('button', { name: /^Annotate: / }));
    const dialog = await screen.findByRole('dialog', { name: 'Annotate' });
    await user.type(within(dialog).getByLabelText(/Annotation/), 'ticket 42');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/history/user-operation/op1/set-annotation`)[0]?.body).toEqual({ annotation: 'ticket 42' }));
  });

  it('tells an empty result of the filters from an empty log and clears the filters', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`GET ${ENGINE}/history/user-operation/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/history/user-operation`]: () => json([]),
    });
    const router = renderApp('/cockpit/audit?user=nobody&cat=Operator');

    expect(await screen.findByText('Nothing matches the filters')).toBeVisible();
    expect(screen.getByLabelText('User')).toHaveValue('nobody');
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(router.state.location.search).toBe(''));
    // the typed filter is cleared as well
    expect(screen.getByLabelText('User')).toHaveValue('');
    expect(await screen.findByText('No operations logged')).toBeVisible();
    const last = server.find('GET', `${ENGINE}/history/user-operation`).at(-1);
    expect(last?.query.has('userId')).toBe(false);
  });
});
