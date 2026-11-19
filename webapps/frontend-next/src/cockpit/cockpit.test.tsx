/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { env } from '../env';
import { formDraftKey, readDraft } from '../forms/drafts';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { Providers, createQueryClient, routes } from '../app/App';
import { preloadRoutes } from '../test/routes';
import type { ActivityBadge } from '../bpmn/BpmnDiagram';
import { empty, fakeServer, json, text, type FakeHandler } from '../test/server';
import { elementNames } from './common';
import { parseConditionValue } from './DefinitionPage';
import { resourceKind } from './DeploymentsPage';
import { prettyValue, toVariableValue, validateText, variableKind } from './Variables';

preloadRoutes(routes);

// the model viewers need a real browser; the pages are tested with what they hand to them
vi.mock('../bpmn/BpmnDiagram', () => ({
  default: ({
    badges,
    onSelect,
  }: {
    badges?: ActivityBadge[];
    onSelect?: (activityId: string | null) => void;
  }) => (
    <div data-testid="diagram">
      {badges?.map((badge) => (
        <button key={badge.activityId} type="button" onClick={() => onSelect?.(badge.activityId)}>
          {`${badge.activityId}: ${badge.instances} running, ${badge.incidents} incidents`}
        </button>
      ))}
    </div>
  ),
}));
vi.mock('../dmn/DmnViewer', () => ({
  default: ({ decisionKey }: { decisionKey?: string }) => <div data-testid="decision-model">{decisionKey}</div>,
}));

const ENGINE = '/engine/engine/default';
const PLUGIN = '/cockpit/plugin/base/default';
const AUTH = '/admin/auth/user/default';
const DEFINITION = 'invoice:2:abc';

const MODEL = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="invoice" name="Invoice Receipt">
    <userTask id="approve" name="Approve Invoice" />
    <serviceTask id="archive" name="Archive Invoice" />
  </process>
</definitions>`;

const forbidden = () => json({ type: 'AuthorizationException', message: 'The user is not authorized' }, 403);

function backend(options: { apps?: string[]; routes?: Record<string, FakeHandler> } = {}) {
  const apps = options.apps ?? ['tasklist', 'cockpit', 'welcome'];
  const state = {
    suspended: false,
    variables: [
      { id: 'v1', name: 'amount', type: 'Long', value: 900, valueInfo: {}, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
      { id: 'v2', name: 'note', type: 'String', value: '<img src=x onerror=alert(1)>', valueInfo: {}, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
      { id: 'v3', name: 'invoice', type: 'Json', value: '{"lines":[1,2]}', valueInfo: {}, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
    ],
  };
  const definition = () => ({
    id: DEFINITION,
    key: 'invoice',
    name: 'Invoice Receipt',
    description: null,
    version: 2,
    versionTag: null,
    tenantId: null,
    deploymentId: 'd1',
    resource: 'invoice.bpmn',
    suspended: state.suspended,
    historyTimeToLive: null,
  });
  const incident = {
    id: 'i1',
    incidentTimestamp: '2026-03-01T09:30:00.000+0700',
    incidentType: 'failedJob',
    incidentMessage: 'Archive is down',
    activityId: 'archive',
    failedActivityId: 'archive',
    processInstanceId: 'p1',
    processDefinitionId: DEFINITION,
    causeIncidentId: 'i1',
    rootCauseIncidentId: 'i1',
    configuration: 'job1',
    annotation: null,
  };
  const server = fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: apps }),
    [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    [`GET ${ENGINE}/filter`]: () => json([]),
    [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [] }),

    [`GET ${ENGINE}/process-instance/count`]: () => json({ count: 12 }),
    [`GET ${ENGINE}/incident/count`]: () => json({ count: 3 }),
    [`GET ${ENGINE}/task/count`]: (request) => json({ count: request.query.get('assigned') ? 4 : 2 }),
    [`GET ${ENGINE}/process-definition/count`]: () => json({ count: 4 }),
    [`GET ${ENGINE}/decision-definition/count`]: () => json({ count: 2 }),
    [`GET ${ENGINE}/deployment/count`]: () => json({ count: 5 }),
    [`GET ${PLUGIN}/process-definition/statistics-count`]: () => json({ count: 1 }),
    [`GET ${PLUGIN}/process-definition/statistics`]: () =>
      json([
        { id: DEFINITION, key: 'invoice', name: 'Invoice Receipt', version: 2, tenantId: null, suspensionState: 1, instances: 12, incidents: 3 },
      ]),

    [`GET ${ENGINE}/process-definition/${DEFINITION}`]: () => json(definition()),
    [`GET ${ENGINE}/process-definition`]: () => json([definition(), { ...definition(), id: 'invoice:1:old', version: 1 }]),
    [`GET ${ENGINE}/process-definition/${DEFINITION}/xml`]: () => json({ id: DEFINITION, bpmn20Xml: MODEL }),
    [`GET ${ENGINE}/process-definition/${DEFINITION}/statistics`]: () =>
      json([
        { id: 'approve', instances: 9, failedJobs: 0, incidents: [] },
        { id: 'archive', instances: 3, failedJobs: 0, incidents: [{ incidentType: 'failedJob', incidentCount: 3 }] },
      ]),
    [`PUT ${ENGINE}/process-definition/${DEFINITION}/suspended`]: (request) => {
      state.suspended = (request.body as { suspended: boolean }).suspended;
      return empty();
    },
    [`DELETE ${ENGINE}/process-definition/${DEFINITION}`]: () => empty(),
    [`POST ${PLUGIN}/process-instance/count`]: () => json({ count: 1 }),
    [`POST ${PLUGIN}/process-instance`]: () =>
      json([
        { id: 'p1', businessKey: 'INV-1', startTime: '2026-03-01T09:30:00.000+0700', incidents: [{ incidentType: 'failedJob', incidentCount: 1 }], suspended: false },
      ]),
    [`POST ${PLUGIN}/incident/count`]: () => json({ count: 1 }),
    [`POST ${PLUGIN}/incident`]: () => json([incident]),

    [`GET ${ENGINE}/process-instance/p1`]: () =>
      json({ id: 'p1', definitionId: DEFINITION, businessKey: 'INV-1', suspended: false, tenantId: null }),
    [`GET ${ENGINE}/process-instance`]: () => json([]),
    [`GET ${ENGINE}/history/process-instance/p1`]: () =>
      json({ startTime: '2026-03-01T09:30:00.000+0700', startUserId: 'mary', superProcessInstanceId: null }),
    [`GET ${ENGINE}/process-instance/p1/activity-instances`]: () =>
      json({
        id: 'p1',
        activityId: 'invoice',
        activityName: 'Invoice Receipt',
        activityType: 'processDefinition',
        childActivityInstances: [
          { id: 'a1', activityId: 'archive', activityName: 'Archive Invoice', activityType: 'serviceTask', incidents: [{ id: 'i1', activityId: 'archive' }] },
        ],
        childTransitionInstances: [],
      }),
    [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: state.variables.length }),
    [`GET ${ENGINE}/variable-instance`]: () => json(state.variables),
    [`PUT ${ENGINE}/execution/p1/localVariables/amount`]: (request) => {
      const found = state.variables.find((variable) => variable.name === 'amount');
      if (found) found.value = (request.body as { value: number }).value;
      return empty();
    },
    [`DELETE ${ENGINE}/execution/p1/localVariables/note`]: () => {
      state.variables = state.variables.filter((variable) => variable.name !== 'note');
      return empty();
    },
    [`DELETE ${ENGINE}/process-instance/p1`]: () => empty(),

    [`GET ${ENGINE}/incident`]: () => json([incident]),
    [`GET ${ENGINE}/job/job1/stacktrace`]: () =>
      text('java.lang.IllegalStateException: <script>alert(1)</script>\n\tat Archive.run', 'text/plain'),
    [`PUT ${ENGINE}/job/job1/retries`]: () => empty(),

    [`GET ${ENGINE}/deployment`]: () =>
      json([{ id: 'd1', name: 'invoice', source: 'process application', deploymentTime: '2026-03-01T09:30:00.000+0700', tenantId: null }]),
    [`GET ${ENGINE}/deployment/d1`]: () =>
      json({ id: 'd1', name: 'invoice', source: null, deploymentTime: '2026-03-01T09:30:00.000+0700', tenantId: null }),
    [`GET ${ENGINE}/deployment/d1/resources`]: () =>
      json([
        { id: 'r1', name: 'invoice.bpmn', deploymentId: 'd1' },
        { id: 'r2', name: 'notes.txt', deploymentId: 'd1' },
      ]),
    [`GET ${ENGINE}/deployment/d1/resources/r1/data`]: () => text(MODEL, 'application/xml'),
    [`GET ${ENGINE}/deployment/d1/resources/r2/data`]: () => text('<b>plain</b> notes', 'text/plain'),
    [`POST ${ENGINE}/deployment/create`]: () => json({ id: 'd1', name: 'loan' }),
    [`DELETE ${ENGINE}/deployment/d1`]: () => empty(),

    [`GET ${ENGINE}/batch/statistics`]: () =>
      json([
        { id: 'b1', type: 'instance-deletion', totalJobs: 10, suspended: false, createUserId: 'demo', startTime: null, batchJobDefinitionId: 'bj1', remainingJobs: 6, completedJobs: 4, failedJobs: 0 },
      ]),
    [`GET ${ENGINE}/history/batch/count`]: forbidden,
    [`GET ${ENGINE}/history/batch`]: forbidden,
    [`PUT ${ENGINE}/batch/b1/suspended`]: () => empty(),
    ...options.routes,
  });
  return Object.assign(server, { state });
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

describe('access', () => {
  it('is closed to users who may not use the monitoring application', async () => {
    const server = backend({ apps: ['tasklist', 'welcome'] });
    const router = renderApp('/cockpit/incidents');

    // back to the welcome page
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(screen.queryByRole('link', { name: 'Cockpit' })).toBeNull();
    expect(server.requests.some((request) => request.path.includes('/incident'))).toBe(false);
  });

  it('shows what the engine refuses instead of breaking the page', async () => {
    backend({
      routes: {
        [`GET ${PLUGIN}/process-definition/statistics-count`]: forbidden,
        [`GET ${PLUGIN}/process-definition/statistics`]: forbidden,
      },
    });
    renderApp('/cockpit/processes');

    expect(await screen.findByText('You are not allowed to do this.')).toBeVisible();
    expect(screen.getByText('Ask an administrator for the permission if you need it.')).toBeVisible();
    // the message of the server is folded away
    await userEvent.click(screen.getByText('Technical details'));
    expect(screen.getByText('The user is not authorized')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible();
    // the frame of the section still works
    expect(screen.getByRole('link', { name: 'Deployments' })).toBeVisible();
    // grouped by what the user wants to do
    expect(within(screen.getByRole('group', { name: 'Build and maintain' })).getByRole('link', { name: 'Deployments' })).toBeVisible();
    expect(within(screen.getByRole('group', { name: 'Monitor' })).getByRole('link', { name: 'Incidents' })).toBeVisible();
  });
});

describe('dashboard', () => {
  it('shows the numbers of the engine with links into the lists', async () => {
    backend();
    renderApp('/cockpit');

    const incidents = await screen.findByRole('link', { name: /Open incidents/ });
    await waitFor(() => expect(incidents).toHaveTextContent('3'));
    expect(incidents).toHaveAttribute('href', '/cockpit/incidents');
    expect(incidents).toHaveTextContent('Need attention');
    expect(screen.getByRole('link', { name: /Running instances/ })).toHaveTextContent('12');
    expect(screen.getByRole('link', { name: /Deployments5/ })).toHaveAttribute('href', '/cockpit/deployments');

    // the busiest processes lead to their definition
    const links = await screen.findAllByRole('link', { name: 'Invoice Receipt' });
    expect(links[0]).toHaveAttribute('href', `/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);
    // every part of the task chart is named with its value
    expect(await screen.findByText('Assigned to a user')).toBeVisible();
  });

  it('puts what needs attention first and leads to where it is handled', async () => {
    backend({
      routes: {
        [`GET ${ENGINE}/job/count`]: () => json({ count: 2 }),
        [`GET ${ENGINE}/task/count`]: () => json({ count: 1 }),
        [`GET ${ENGINE}/batch/statistics`]: () => json([{ failedJobs: 0 }]),
      },
    });
    renderApp('/cockpit');
    const panel = await screen.findByRole('region', { name: 'Needs attention' });
    expect(await within(panel).findByRole('link', { name: /Oldest open incident, since .*Archive is down/ })).toHaveAttribute(
      'href',
      '/cockpit/incidents?group=1',
    );
    expect(within(panel).getByRole('link', { name: '2 jobs are out of retries' })).toHaveAttribute('href', '/cockpit/jobs');
    expect(within(panel).getByRole('link', { name: '1 task is overdue' })).toHaveAttribute('href', '/tasklist');
    expect(within(panel).queryByText(/batch/)).toBeNull();
  });

  it('compares the work of a chosen time range with the range before', async () => {
    const user = userEvent.setup();
    const after = (request: { query: URLSearchParams }, name: string) => new Date(request.query.get(name) ?? '').getTime();
    const server = backend({
      routes: {
        // the current range has twice as many as the one before
        [`GET ${ENGINE}/history/process-instance/count`]: (request) =>
          json({ count: Date.now() - after(request, request.query.has('startedAfter') ? 'startedAfter' : 'finishedAfter') < 26 * 3_600_000 ? 8 : 4 }),
        [`GET ${ENGINE}/history/incident/count`]: (request) =>
          json({ count: Date.now() - after(request, 'createTimeAfter') < 26 * 3_600_000 ? 3 : 1 }),
        [`GET ${ENGINE}/history/task/count`]: forbidden,
      },
    });
    renderApp('/cockpit');

    const trends = await screen.findByRole('region', { name: 'Trends' });
    const week = within(trends).getByRole('button', { name: '7 days' });
    expect(week).toHaveAttribute('aria-pressed', 'true');
    await user.click(within(trends).getByRole('button', { name: '24 hours' }));
    expect(within(trends).getByRole('button', { name: '24 hours' })).toHaveAttribute('aria-pressed', 'true');

    const started = await within(trends).findByRole('link', { name: /Instances started8\+4 \(\+100%\) vs\. 4/ });
    expect(started.getAttribute('href')).toMatch(/^\/cockpit\/history\?hsa=/);
    // more incidents are bad news, more completed instances good news
    expect(within(trends).getByText(/\+2 \(\+200%\)/)).toHaveClass('trend__change--bad');
    expect(within(trends).getAllByText(/\+4 \(\+100%\)/)[1]).toHaveClass('trend__change--good');
    // a measure that cannot be read is not compared
    expect(within(trends).getByText('No comparison')).toBeVisible();

    const counts = server.find('GET', `${ENGINE}/history/process-instance/count`);
    const last = counts[counts.length - 1];
    expect(last?.query.has('finishedBefore') || last?.query.has('startedBefore')).toBe(true);
    // completed means completed: canceled instances are not counted
    expect(counts.filter((request) => request.query.has('finishedAfter')).every((request) => request.query.get('completed') === 'true')).toBe(true);
    expect(within(trends).getByRole('link', { name: /Instances completed/ }).getAttribute('href')).toMatch(/hstate=completed&hfa=/);
    expect(window.localStorage.getItem('fasti.next.cockpit.dashboard.range')).toBe('"24h"');
  });

  it('pauses and resumes the automatic refresh', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/cockpit');

    const toggle = await screen.findByRole('button', { name: /Refreshes every 30 s/ });
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await user.click(toggle);

    expect(await screen.findByRole('button', { name: /Automatic refresh paused/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(window.localStorage.getItem('fasti.next.cockpit.refreshPaused')).toBe('true');
  });
});

describe('process definition', () => {
  it('lists the processes and remembers how they are sorted', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/processes');

    const table = await screen.findByRole('table', { name: 'Processes' });
    expect(within(table).getByRole('link', { name: 'Invoice Receipt' })).toBeVisible();
    expect(within(table).getByText('3 incidents')).toBeVisible();

    await user.click(within(table).getByRole('button', { name: 'Instances' }));

    await waitFor(() =>
      expect(
        server
          .find('GET', `${PLUGIN}/process-definition/statistics`)
          .some((request) => request.query.get('sortBy') === 'instances'),
      ).toBe(true),
    );
    expect(window.localStorage.getItem('fasti.next.cockpit.definitions.sort')).toContain('instances');
  });

  it('puts the numbers on the diagram and filters the instances by a clicked activity', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    expect(await screen.findByRole('heading', { name: 'Invoice Receipt' })).toBeVisible();
    const activity = await screen.findByRole('button', { name: 'archive: 3 running, 3 incidents' });
    expect(await screen.findByRole('link', { name: /p1/ })).toHaveAttribute(
      'href',
      '/cockpit/processes/instance/p1',
    );

    await user.click(activity);

    // the filter is named after the activity and is part of the address
    expect(await screen.findByText('Archive Invoice')).toBeVisible();
    expect(router.state.location.search).toContain('act=archive');
    await waitFor(() =>
      expect(
        server
          .find('POST', `${PLUGIN}/process-instance`)
          .some((request) => (request.body as { activityIdIn?: string[] }).activityIdIn?.[0] === 'archive'),
      ).toBe(true),
    );
  });

  it('filters by an activity chosen from the list instead of the diagram', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    const picker = await screen.findByRole('combobox', { name: 'Filter by activity…' });
    await user.selectOptions(picker, 'Archive Invoice (3 running, 3 incidents)');
    await waitFor(() => expect(router.state.location.search).toContain('act=archive'));
    expect(within(picker).getByRole('option', { name: /✓ Archive Invoice/ })).toBeInTheDocument();
  });

  it('filters the instances by a variable', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    await user.click(await screen.findByRole('button', { name: 'Variables' }));
    await user.type(screen.getByLabelText('Variable name, row 1'), 'amount');
    await user.selectOptions(screen.getByLabelText('Comparison, row 1'), 'gteq');
    await user.type(screen.getByLabelText('Value, row 1'), '100');
    await user.click(screen.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText('amount ≥ 100')).toBeVisible();
    await waitFor(() =>
      expect(
        server
          .find('POST', `${PLUGIN}/process-instance`)
          .some(
            (request) =>
              JSON.stringify((request.body as { variables?: unknown }).variables) ===
              JSON.stringify([{ name: 'amount', operator: 'gteq', value: 100 }]),
          ),
      ).toBe(true),
    );
  });

  it('asks the engine API for instances matching any of several variable conditions', async () => {
    const server = backend();
    server.on(`POST ${ENGINE}/process-instance/count`, () => json({ count: 1 }));
    server.on(`POST ${ENGINE}/process-instance`, (request) =>
      json((request.body as { withIncident?: boolean }).withIncident ? [] : [{ id: 'pi-or', businessKey: 'B-1', suspended: false }]),
    );
    const filter = JSON.stringify({ m: 'or', c: [['amount', 'gt', 'number', '100'], ['vip', 'eq', 'boolean', 'true']] });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}?vars=${encodeURIComponent(filter)}`);

    expect(await screen.findByText('B-1')).toBeVisible();
    const [count] = server.find('POST', `${ENGINE}/process-instance/count`);
    expect((count?.body as { orQueries?: unknown }).orQueries).toEqual([
      {
        variables: [
          { name: 'amount', operator: 'gt', value: 100 },
          { name: 'vip', operator: 'eq', value: true },
        ],
      },
    ]);
    expect(screen.getByRole('button', { name: 'Variables (2)' })).toBeVisible();
  });

  it('suspends a definition after asking, with the chosen options', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    await user.click(await screen.findByRole('button', { name: 'Suspend' }));
    const dialog = await screen.findByRole('dialog', { name: 'Suspend process' });
    // the question names the object and the consequence
    expect(within(dialog).getByText(/version 2 of the process “Invoice Receipt”/)).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/process-definition/${DEFINITION}/suspended`)).toHaveLength(0);

    await user.click(within(dialog).getByLabelText(/Apply to the existing instances/));
    await user.click(within(dialog).getByRole('button', { name: 'Suspend' }));

    expect(await screen.findByText('Process suspended')).toBeVisible();
    const [request] = server.find('PUT', `${ENGINE}/process-definition/${DEFINITION}/suspended`);
    expect(request?.body).toEqual({ suspended: true, includeProcessInstances: false, executionDate: null });
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
    // the page shows the new state and offers the way back
    expect(await screen.findByRole('button', { name: 'Activate' })).toBeVisible();
  });

  it('deletes a definition only after confirmation and returns to the list', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    await user.click(await screen.findByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete process version' });
    await user.click(within(dialog).getByRole('button', { name: 'Back' }));
    expect(server.find('DELETE', `${ENGINE}/process-definition/${DEFINITION}`)).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const again = await screen.findByRole('dialog', { name: 'Delete process version' });
    await user.click(within(again).getByLabelText(/Also delete the instances/));
    await user.click(within(again).getByRole('button', { name: 'Delete version' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/processes'));
    const [request] = server.find('DELETE', `${ENGINE}/process-definition/${DEFINITION}`);
    expect(request?.query.get('cascade')).toBe('true');
    expect(request?.query.get('skipCustomListeners')).toBe('false');
  });

  it('reports a failed action with the message of the engine', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`DELETE ${ENGINE}/process-definition/${DEFINITION}`]: () =>
          json({ type: 'ProcessEngineException', message: 'Deletion of process definition without cascading failed.' }, 500),
      },
    });
    renderApp(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`);

    await user.click(await screen.findByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete process version' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete version' }));

    expect(await screen.findByText('The process could not be deleted')).toBeVisible();
    expect(screen.getByText('Deletion of process definition without cascading failed.')).toBeVisible();
    // the dialog stays open, so that the options can be changed
    expect(screen.getByRole('dialog', { name: 'Delete process version' })).toBeVisible();
  });
});

describe('process instance', () => {
  it('shows where the instance stands and what it carries', async () => {
    backend();
    renderApp('/cockpit/processes/instance/p1');

    expect(await screen.findByRole('heading', { name: 'Invoice Receipt' })).toBeVisible();
    expect(await screen.findByText('INV-1')).toBeVisible();
    // the token and the incident are marked on the diagram and in the tree
    expect(await screen.findByRole('button', { name: 'archive: 1 running, 1 incidents' })).toBeVisible();
    const tree = screen.getByRole('list', { name: 'Running activities' });
    expect(within(tree).getByRole('button', { name: /Archive Invoice/ })).toHaveTextContent('1 incident');
    // and said in plain words
    expect(screen.getByText(/Stopped by an error at “Archive Invoice”/)).toBeVisible();

    const table = await screen.findByRole('table', { name: 'Variables' });
    expect(within(table).getByText('amount')).toBeVisible();
    expect(within(table).getByText('900')).toBeVisible();
  });

  it('goes from the error summary straight to the incidents', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'Show incidents' }));
    expect(await screen.findByRole('tab', { name: /Incidents/, selected: true })).toBeVisible();
  });

  it('shows the activities of the instance on a timeline', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`GET ${ENGINE}/history/activity-instance`]: () =>
          json([
            { id: 'h1', activityId: 'approve', activityName: 'Approve Invoice', activityType: 'userTask', startTime: '2026-03-01T08:00:00.000+0000', endTime: '2026-03-01T10:00:00.000+0000', durationInMillis: 7200000, assignee: 'demo', canceled: false, completeScope: false, calledProcessInstanceId: null },
            { id: 'h2', activityId: 'archive', activityName: 'Archive Invoice', activityType: 'serviceTask', startTime: '2026-03-01T10:00:00.000+0000', endTime: null, durationInMillis: null, assignee: null, canceled: false, completeScope: false, calledProcessInstanceId: null },
          ]),
        [`GET ${ENGINE}/history/incident`]: () => json([]),
      },
    });
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('tab', { name: 'Timeline' }));
    const chart = await screen.findByRole('list', { name: 'Timeline' });
    expect(within(chart).getByText('Approve Invoice')).toBeVisible();
    expect(within(chart).getByText(/waited 2 hr for a person · demo/)).toBeVisible();
    await user.click(screen.getByRole('checkbox', { name: 'Only user tasks' }));
    expect(within(chart).queryByText('Archive Invoice')).not.toBeInTheDocument();
  });

  it('narrows the tabs to the activity chosen in the tree and shows all again', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/cockpit/processes/instance/p1?tab=jobs');

    const tree = await screen.findByRole('list', { name: 'Running activities' });
    await user.click(await within(tree).findByRole('button', { name: /Archive Invoice/ }));
    await waitFor(() => expect(router.state.location.search).toContain('act=archive'));
    expect(await screen.findByText('Only what belongs to “Archive Invoice”.')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Show all' }));
    await waitFor(() => expect(router.state.location.search).not.toContain('act='));
    expect(screen.queryByText('Only what belongs to “Archive Invoice”.')).not.toBeInTheDocument();
  });

  it('starts another instance like this one with its simple variables in the start form', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`GET ${ENGINE}/process-instance/p1/variables`]: () =>
          json({
            amount: { type: 'Long', value: 900, valueInfo: {} },
            invoice: { type: 'Object', value: 'rO0AB', valueInfo: { objectTypeName: 'x.Invoice' } },
          }),
      },
    });
    const router = renderApp('/cockpit/processes/instance/p1');

    // rarer actions are in the menu of the header
    await user.click(await screen.findByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Start like this one' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/processes'));
    expect(router.state.location.search).toBe(`?definition=${encodeURIComponent(DEFINITION)}`);
    const draft = readDraft(formDraftKey(env.engine, 'demo', { type: 'start', processDefinitionId: DEFINITION }));
    expect(draft).toMatchObject({ kind: 'variables', values: { amount: { type: 'Long', value: 900 } } });
    expect(Object.keys(draft?.values as object)).toEqual(['amount']);
  });

  it('never renders a value as markup', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/cockpit/processes/instance/p1');

    const table = await screen.findByRole('table', { name: 'Variables' });
    expect(within(table).getByText('<img src=x onerror=alert(1)>')).toBeVisible();
    expect(table.querySelector('img')).toBeNull();

    // structured values are printed as text in the viewer
    const row = within(table).getByText('invoice').closest('tr') as HTMLElement;
    await user.click(within(row).getByRole('button', { name: 'View' }));
    const dialog = await screen.findByRole('dialog', { name: 'invoice' });
    expect(dialog.querySelector('pre')?.textContent).toBe('{\n  "lines": [\n    1,\n    2\n  ]\n}');
  });

  it('edits a simple variable in place and validates what was typed', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'Edit variable amount' }));
    const input = screen.getByLabelText('Value of amount');
    await user.clear(input);
    await user.type(input, '12.5{Enter}');
    expect(await screen.findByText('Enter a whole number.')).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/execution/p1/localVariables/amount`)).toHaveLength(0);

    await user.clear(input);
    await user.type(input, '1200{Enter}');

    expect(await screen.findByText('Variable “amount” saved')).toBeVisible();
    const [request] = server.find('PUT', `${ENGINE}/execution/p1/localVariables/amount`);
    expect(request?.body).toEqual({ value: 1200, type: 'Long' });
    expect(await screen.findByText('1200')).toBeVisible();
  });

  it('edits an object variable as JSON only if it is serialized as JSON and replaces files', async () => {
    const user = userEvent.setup();
    const objects = [
      { id: 'v4', name: 'customer', type: 'Object', value: '{"name":"Ann"}', valueInfo: { objectTypeName: 'com.example.Customer', serializationDataFormat: 'application/json' }, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
      { id: 'v5', name: 'legacy', type: 'Object', value: 'rO0AB', valueInfo: { objectTypeName: 'com.example.Old', serializationDataFormat: 'application/x-java-serialized-object' }, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
      { id: 'v6', name: 'scan', type: 'File', value: null, valueInfo: { filename: 'scan.pdf' }, processInstanceId: 'p1', executionId: 'p1', taskId: null, activityInstanceId: 'p1', errorMessage: null },
    ];
    const server = backend({
      routes: {
        [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: objects.length }),
        [`GET ${ENGINE}/variable-instance`]: () => json(objects),
        [`PUT ${ENGINE}/execution/p1/localVariables/customer`]: () => empty(),
        [`POST ${ENGINE}/execution/p1/localVariables/scan/data`]: () => empty(),
      },
    });
    renderApp('/cockpit/processes/instance/p1');

    expect(await screen.findByRole('button', { name: 'Edit variable customer' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Edit variable legacy' })).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Edit variable customer' }));
    const dialog = await screen.findByRole('dialog', { name: /customer/ });
    const editor = within(dialog).getByRole('textbox');
    await user.clear(editor);
    await user.type(editor, '{{"name":');
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.type(editor, '"Bo"}');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/execution/p1/localVariables/customer`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/execution/p1/localVariables/customer`)[0]?.body).toEqual({
      value: '{"name":"Bo"}',
      type: 'Object',
      valueInfo: { objectTypeName: 'com.example.Customer', serializationDataFormat: 'application/json' },
    });

    await user.click(screen.getByRole('button', { name: 'Replace the file of scan' }));
    const upload = await screen.findByRole('dialog', { name: 'Replace the file of scan' });
    await user.upload(within(upload).getByLabelText(/File to attach/), new File(['%PDF'], 'new.pdf', { type: 'application/pdf' }));
    await user.click(within(upload).getByRole('button', { name: 'Upload' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/execution/p1/localVariables/scan/data`)).toHaveLength(1));
    const parts = server.find('POST', `${ENGINE}/execution/p1/localVariables/scan/data`)[0]?.body as FormData;
    expect(parts.get('valueType')).toBe('File');
    expect((parts.get('data') as File).name).toBe('new.pdf');
  });

  it('deletes a variable after confirmation', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'Delete variable note' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete variable' });
    expect(within(dialog).getByText(/Delete the variable “note”/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await screen.findByText('Variable “note” deleted')).toBeVisible();
    expect(server.find('DELETE', `${ENGINE}/execution/p1/localVariables/note`)).toHaveLength(1);
  });

  it('shows the error of an incident as text and retries the job', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/processes/instance/p1?tab=incidents');

    expect(await screen.findByText('Archive is down')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Error details' }));
    const details = await screen.findByRole('dialog', { name: 'Error details' });
    await waitFor(() =>
      expect(details.querySelector('pre')?.textContent).toContain('<script>alert(1)</script>'),
    );
    expect(details.querySelector('script')).toBeNull();
    await user.click(within(details).getByRole('button', { name: 'Close' }));

    await user.click(screen.getByRole('button', { name: 'Retry' }));
    const dialog = await screen.findByRole('dialog', { name: 'Retry' });
    expect(within(dialog).getByText(/Run the job of “Archive Invoice” again/)).toBeVisible();
    const retries = within(dialog).getByLabelText('Retries');
    await user.clear(retries);
    await user.type(retries, '2');
    await user.click(within(dialog).getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Retries reset')).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/job/job1/retries`)[0]?.body).toEqual({ retries: 2 });
  });

  it('cancels an instance with options and returns to its process', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/cockpit/processes/instance/p1');

    await user.click(await screen.findByRole('button', { name: 'More actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Cancel instance' }));
    const dialog = await screen.findByRole('dialog', { name: 'Cancel instance' });
    expect(within(dialog).getByText(/Cancel the instance p1 \(INV-1\)/)).toBeVisible();
    await user.click(within(dialog).getByLabelText(/Skip listeners/));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel instance' }));

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`),
    );
    const [request] = server.find('DELETE', `${ENGINE}/process-instance/p1`);
    expect(request?.query.get('skipCustomListeners')).toBe('true');
    expect(request?.query.get('skipSubprocesses')).toBe('false');
  });

  it('explains an instance that has ended', async () => {
    backend({
      routes: {
        [`GET ${ENGINE}/process-instance/gone`]: () =>
          json({ type: 'InvalidRequestException', message: 'Process instance with id gone does not exist' }, 404),
      },
    });
    renderApp('/cockpit/processes/instance/gone');

    expect(await screen.findByText('This instance is not running any more')).toBeVisible();
  });
});

describe('deployments', () => {
  it('uploads files and opens the new deployment', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/cockpit/deployments');

    await user.click(await screen.findByRole('button', { name: 'Deploy files' }));
    const dialog = await screen.findByRole('dialog', { name: 'Deploy files' });
    // nothing is sent without a file
    await user.click(within(dialog).getByRole('button', { name: 'Deploy' }));
    expect(await within(dialog).findByText('Choose at least one file.')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/deployment/create`)).toHaveLength(0);

    const file = new File(['<definitions/>'], 'loan.bpmn', { type: 'text/xml' });
    await user.upload(within(dialog).getByLabelText(/Files/), file);
    // the name is proposed from the file
    expect(within(dialog).getByLabelText(/Deployment name/)).toHaveValue('loan');
    await user.click(within(dialog).getByRole('button', { name: 'Deploy' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/deployments/d1'));
    const [request] = server.find('POST', `${ENGINE}/deployment/create`);
    const parts = request?.body as FormData;
    expect(parts.get('deployment-name')).toBe('loan');
    expect((parts.get('loan.bpmn') as File).name).toBe('loan.bpmn');
    expect(request?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
  });

  it('shows the resources of a deployment and deletes it with cascade', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/cockpit/deployments/d1?resource=r2');

    // a text resource is printed, not interpreted
    const content = await screen.findByText('<b>plain</b> notes');
    expect(content.tagName).toBe('PRE');
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute(
      'href',
      '/fasti/api/engine/engine/default/deployment/d1/resources/r2/data',
    );
    // the model of the deployment leads to its definition
    expect(await screen.findByRole('link', { name: /Open the process “Invoice Receipt”/ })).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Delete deployment' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete deployment' });
    expect(within(dialog).getByText(/Delete the deployment “invoice”/)).toBeVisible();
    await user.click(within(dialog).getByLabelText(/Also delete the instances/));
    await user.click(within(dialog).getByRole('button', { name: 'Delete deployment' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/deployments'));
    expect(server.find('DELETE', `${ENGINE}/deployment/d1`)[0]?.query.get('cascade')).toBe('true');
  });
});

describe('failed jobs', () => {
  const job = (id: string) => ({
    id,
    jobDefinitionId: 'jd1',
    dueDate: null,
    createTime: null,
    retries: 0,
    exceptionMessage: `Archive is down (${id})`,
    failedActivityId: 'archive',
    suspended: false,
    priority: 0,
    processInstanceId: 'p1',
    processDefinitionKey: 'invoice',
  });

  it('retries a selection of jobs and all jobs of the list', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`GET ${ENGINE}/job/count`]: () => json({ count: 2 }),
        [`GET ${ENGINE}/job`]: () => json([job('j1'), job('j2')]),
        [`PUT ${ENGINE}/job/j2/retries`]: () => empty(),
        [`POST ${ENGINE}/job/retries`]: () => json({ id: 'b9' }),
      },
    });
    renderApp('/cockpit/jobs');

    expect(await screen.findByText('2 failed jobs')).toBeVisible();
    expect(server.find('GET', `${ENGINE}/job`)[0]?.query.get('noRetriesLeft')).toBe('true');
    await user.click(screen.getByRole('checkbox', { name: 'Select job j2' }));
    await user.click(screen.getByRole('button', { name: 'Retry selected (1)' }));
    let dialog = await screen.findByRole('dialog', { name: 'Set retries' });
    await user.click(within(dialog).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Retries reset')).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/job/j2/retries`)[0]?.body).toEqual({ retries: 1 });
    expect(server.find('PUT', `${ENGINE}/job/j1/retries`)).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Retry all' }));
    dialog = await screen.findByRole('dialog', { name: 'Set retries' });
    expect(within(dialog).getByText(/all 2 jobs/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('The engine sets the retries in a batch')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/job/retries`)[0]?.body).toEqual({
      retries: 1,
      jobQuery: { withException: true, noRetriesLeft: true },
    });
  });
});

describe('batches', () => {
  it('shows the progress of a batch, suspends it and copes without history', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/batches');

    const table = await screen.findByRole('table', { name: 'Running' });
    // the kind of batch is said in words; the type of the engine stays in the title
    expect(within(table).getByText('Delete instances')).toHaveAttribute('title', 'instance-deletion');
    expect(within(table).getByText('4 of 10 done')).toBeVisible();
    expect(await screen.findByText('No history data')).toBeVisible();

    await user.click(within(table).getByRole('button', { name: /^Suspend: / }));

    expect(await screen.findByText('Batch suspended')).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/batch/b1/suspended`)[0]?.body).toEqual({ suspended: true });
  });
});

describe('links', () => {
  it('opens the latest version of a process by its key and keeps the tab of the link', async () => {
    backend();
    const router = renderApp('/cockpit/processes/key/invoice?tab=jobs');

    await waitFor(() =>
      expect(router.state.location.pathname).toBe(
        `/cockpit/processes/definition/${encodeURIComponent(DEFINITION)}`,
      ),
    );
    expect(router.state.location.search).toBe('?tab=jobs');
  });

  it('says when only the first incidents of many are annotated and reports failures as annotation failures', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`GET ${ENGINE}/incident/count`]: () => json({ count: 1500 }),
        [`PUT ${ENGINE}/incident/i1/annotation`]: () => json({ type: 'RestException', message: 'no' }, 500),
      },
    });
    renderApp('/cockpit/incidents');
    await screen.findByText('Archive is down');

    await user.click(screen.getByRole('button', { name: 'All matching' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Annotate all matching' }));
    let dialog = await screen.findByRole('dialog', { name: 'Annotate incidents' });
    expect(await within(dialog).findByText(/Only the first 1 of 1500 matching incidents are handled now/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Back' }));
    const reads = server.find('GET', `${ENGINE}/incident`).length;

    // every opening reads the incidents again
    await user.click(screen.getByRole('button', { name: 'All matching' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Annotate all matching' }));
    dialog = await screen.findByRole('dialog', { name: 'Annotate incidents' });
    await waitFor(() => expect(server.find('GET', `${ENGINE}/incident`).length).toBeGreaterThan(reads));
    await user.type(within(dialog).getByRole('textbox'), 'ticket 7');
    const confirm = within(dialog).getByRole('button', { name: 'Annotate 1' });
    await waitFor(() => expect(confirm).toBeEnabled());
    await user.click(confirm);
    expect(await screen.findByText('1 of 1 incidents could not be annotated')).toBeVisible();
  });

  it('says when filters hide every incident and clears them', async () => {
    const user = userEvent.setup();
    backend({ routes: { [`GET ${ENGINE}/incident`]: () => json([]), [`GET ${ENGINE}/incident/count`]: () => json({ count: 0 }) } });
    const router = renderApp('/cockpit/incidents?activity=nope');

    expect(await screen.findByText('Nothing matches the filters')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(router.state.location.search).not.toContain('activity='));
    expect(await screen.findByText('No incidents')).toBeVisible();
  });

  it('groups the incidents by cause and shows the incidents of a group', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/cockpit/incidents');

    await user.click(await screen.findByRole('button', { name: 'Group by cause' }));
    const table = await screen.findByRole('table', { name: 'Incidents grouped by cause' });
    expect(await within(table).findByText('Archive is down')).toBeVisible();
    expect(within(table).getByText('1 instance · incidents: 1')).toBeVisible();

    await user.click(within(table).getByRole('button', { name: /^Show: / }));
    const items = await screen.findByRole('region', { name: 'Incidents of this group' });
    expect(within(items).getAllByText('Archive is down').length).toBeGreaterThan(0);
    expect(within(table).getByRole('button', { name: /^Show: / })).toHaveAttribute('aria-expanded', 'true');
  });

  it('keeps the data it has when a refresh fails and says how old it is', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/incidents');
    await screen.findByText('Archive is down');

    server.on(`GET ${ENGINE}/incident`, () => json({ type: 'ProcessEngineException', message: 'down' }, 500));
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    expect(await screen.findByText(/Could not refresh; shown as of/)).toBeVisible();
    expect(screen.getByText('Archive is down')).toBeVisible();
  });

  it('loads the numbers again when a page is opened again', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/cockpit/incidents');
    await screen.findByText('Archive is down');
    await user.click(screen.getByRole('link', { name: 'Batches' }));
    await screen.findByRole('table', { name: 'Running' });
    const before = server.find('GET', `${ENGINE}/incident`).length;

    await user.click(screen.getByRole('link', { name: 'Incidents' }));

    // what is known is shown at once, the current state is fetched
    expect(await screen.findByText('Archive is down')).toBeVisible();
    await waitFor(() => expect(server.find('GET', `${ENGINE}/incident`).length).toBeGreaterThan(before));
  });
});

describe('command palette', () => {
  it('offers the monitoring pages and opens an instance by its id', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/cockpit');

    await user.click(await screen.findByRole('button', { name: 'Find a command' }));
    const input = await screen.findByRole('combobox');
    expect(await screen.findByRole('option', { name: 'Cockpit: Incidents' })).toBeVisible();

    await user.type(input, 'p1');
    await user.click(await screen.findByRole('option', { name: 'Open the instance with id “p1”' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/processes/instance/p1'));
  });

  it('finds an incident and a finished instance by their ids and offers them again as recent', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`GET ${ENGINE}/incident`]: (request) =>
          json(
            request.query.get('incidentId') === 'i-42'
              ? [{ id: 'i-42', incidentMessage: 'Connection refused', activityId: 'charge', processInstanceId: 'p1' }]
              : [],
          ),
        [`GET ${ENGINE}/history/process-instance`]: (request) =>
          json(
            request.query.get('processInstanceId') === 'old-1'
              ? [{ id: 'old-1', businessKey: 'INV-1', processDefinitionKey: 'invoice', state: 'COMPLETED' }]
              : [],
          ),
      },
    });
    const router = renderApp('/cockpit');

    await user.click(await screen.findByRole('button', { name: 'Find a command' }));
    await user.type(await screen.findByRole('combobox'), 'i-42');
    await user.click(await screen.findByRole('option', { name: 'Connection refused · charge' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/processes/instance/p1'));
    expect(router.state.location.search).toBe('?tab=incidents');

    await user.click(await screen.findByRole('button', { name: 'Find a command' }));
    await user.type(await screen.findByRole('combobox'), 'old-1');
    await user.click(await screen.findByRole('option', { name: 'INV-1 · invoice' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/history/instance/old-1'));

    await user.click(await screen.findByRole('button', { name: 'Find a command' }));
    expect(await screen.findByRole('option', { name: 'INV-1 · Finished instance' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'Connection refused · Incident' })).toBeVisible();
  });
});

describe('helpers', () => {
  it('reads the value of a variable condition', () => {
    expect(parseConditionValue('12')).toBe(12);
    expect(parseConditionValue('-1.5')).toBe(-1.5);
    expect(parseConditionValue('true')).toBe(true);
    expect(parseConditionValue('null')).toBeNull();
    expect(parseConditionValue('"12"')).toBe('12');
    expect(parseConditionValue(' abc ')).toBe('abc');
  });

  it('decides how a resource is shown by its name', () => {
    expect(resourceKind('process/invoice.v2.bpmn')).toBe('bpmn');
    expect(resourceKind('legacy.bpmn20.xml')).toBe('bpmn');
    expect(resourceKind('rules.dmn')).toBe('dmn');
    expect(resourceKind('start.form')).toBe('text');
    expect(resourceKind('logo.svg')).toBe('image');
    expect(resourceKind('archive.zip')).toBe('other');
  });

  it('classifies and converts variable values', () => {
    expect(variableKind({ type: 'Long' })).toBe('inline');
    expect(variableKind({ type: 'Json' })).toBe('text');
    expect(variableKind({ type: 'Object' })).toBe('object');
    expect(variableKind({ type: 'File' })).toBe('binary');
    expect(toVariableValue('Integer', '7')).toEqual({ ok: true, value: { value: 7, type: 'Integer' } });
    expect(toVariableValue('Integer', 'x')).toEqual({ ok: false, error: 'validation.integer' });
    expect(toVariableValue('Json', '{"a":1}')).toEqual({ ok: true, value: { value: '{"a":1}', type: 'Json' } });
    expect(toVariableValue('Json', '{a')).toEqual({ ok: false, error: 'cockpit.variable.invalidJson' });
    expect(toVariableValue('Null', '')).toEqual({ ok: true, value: { value: null, type: 'Null' } });
    expect(validateText('Xml', '<a><b/></a>')).toBeNull();
    expect(validateText('Xml', '<a>')).toBe('cockpit.variable.invalidXml');
    expect(prettyValue({ type: 'Object', value: '{"a":1}', valueInfo: { serializationDataFormat: 'application/json' } })).toBe(
      '{\n  "a": 1\n}',
    );
    expect(prettyValue({ type: 'Xml', value: '<a/>', valueInfo: {} })).toBe('<a/>');
  });

  it('reads the names of model elements', () => {
    const names = elementNames(MODEL);
    expect(names.get('approve')).toBe('Approve Invoice');
    expect(names.get('invoice')).toBe('Invoice Receipt');
    expect(elementNames(undefined).size).toBe(0);
    expect(elementNames('not xml').size).toBe(0);
  });
});
