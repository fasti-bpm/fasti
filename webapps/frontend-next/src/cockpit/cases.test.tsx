/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { Providers, createQueryClient, routes } from '../app/App';
import type { CaseExecution, HistoricCaseActivity } from '../api/cases';
import type { ShapeBadge, ShapeMarker } from '../cmmn/CmmnDiagram';
import { preloadRoutes } from '../test/routes';
import { empty, fakeServer, json, type FakeHandler } from '../test/server';
import { itemStates, itemTree } from './CaseInstancePage';

preloadRoutes(routes);

// the pages are tested with what they hand to the diagram; the diagram has its own tests
vi.mock('../cmmn/CmmnDiagram', () => ({
  default: ({
    markers,
    badges,
    onSelect,
  }: {
    markers?: ShapeMarker[];
    badges?: ShapeBadge[];
    onSelect?: (id: string | null) => void;
  }) => (
    <div data-testid="case-diagram">
      {markers?.map((marker) => (
        <button key={marker.id} type="button" onClick={() => onSelect?.(marker.id)}>
          {`${marker.id} ${marker.marker}`}
        </button>
      ))}
      {badges?.map((badge) => <span key={badge.id}>{`${badge.id}: ${badge.count}`}</span>)}
    </div>
  ),
}));

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';
const DEFINITION = 'claim:1:a';
const forbidden = () => json({ type: 'AuthorizationException', message: 'The user is not authorized' }, 403);

function execution(overrides: Partial<CaseExecution>): CaseExecution {
  return {
    id: 'x',
    caseInstanceId: 'c1',
    caseDefinitionId: DEFINITION,
    activityId: 'PI',
    activityName: null,
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

function activity(overrides: Partial<HistoricCaseActivity>): HistoricCaseActivity {
  return {
    id: 'h',
    parentCaseActivityInstanceId: 'c1',
    caseActivityId: 'PI',
    caseActivityName: null,
    caseActivityType: 'humanTask',
    caseExecutionId: 'x',
    taskId: null,
    calledProcessInstanceId: null,
    calledCaseInstanceId: null,
    createTime: '2026-03-01T09:30:00.000+0700',
    endTime: null,
    durationInMillis: null,
    required: false,
    available: false,
    enabled: false,
    disabled: false,
    active: false,
    completed: false,
    terminated: false,
    ...overrides,
  };
}

const EXECUTIONS = [
  execution({ id: 'c1', activityId: 'CasePlanModel', activityName: 'Claim', activityType: 'casePlanModel', parentId: null, active: true }),
  execution({ id: 'x1', activityId: 'PI_review', activityName: 'Review claim', active: true }),
  execution({ id: 'x2', activityId: 'PI_check', activityName: 'Check documents', enabled: true }),
  execution({ id: 'x3', activityId: 'PI_settle', activityName: 'Settlement', activityType: 'stage' }),
  execution({ id: 'x4', activityId: 'PI_pay', activityName: 'Pay out', parentId: 'x3', disabled: true }),
];

const definition = { id: DEFINITION, key: 'claim', name: 'Claim case', version: 1, category: null, resource: 'claim.cmmn', deploymentId: 'd1', tenantId: null, historyTimeToLive: 1 };

function backend(routes: Record<string, FakeHandler> = {}) {
  return fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: ['tasklist', 'cockpit', 'welcome'] }),
    [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    [`GET ${ENGINE}/filter`]: () => json([]),
    [`GET ${ENGINE}/batch/statistics`]: () => json([]),
    [`GET ${ENGINE}/case-definition/count`]: () => json({ count: 1 }),
    [`GET ${ENGINE}/case-definition`]: () => json([definition]),
    [`GET ${ENGINE}/case-definition/${DEFINITION}`]: () => json(definition),
    [`GET ${ENGINE}/case-definition/${DEFINITION}/xml`]: () => json({ id: DEFINITION, cmmnXml: '<definitions />' }),
    [`GET ${ENGINE}/case-instance/count`]: () => json({ count: 2 }),
    [`GET ${ENGINE}/case-instance`]: () =>
      json([
        { id: 'c1', caseDefinitionId: DEFINITION, businessKey: 'CL-1', tenantId: null, active: true, completed: false, terminated: false },
        { id: 'c2', caseDefinitionId: DEFINITION, businessKey: 'CL-2', tenantId: null, active: false, completed: true, terminated: false },
      ]),
    [`GET ${ENGINE}/case-instance/c1`]: () =>
      json({ id: 'c1', caseDefinitionId: DEFINITION, businessKey: 'CL-1', tenantId: null, active: true, completed: false, terminated: false }),
    [`GET ${ENGINE}/case-execution`]: () => json(EXECUTIONS),
    [`GET ${ENGINE}/history/case-instance/c1`]: () =>
      json({ id: 'c1', businessKey: 'CL-1', caseDefinitionId: DEFINITION, caseDefinitionKey: 'claim', caseDefinitionName: 'Claim case', createTime: '2026-03-01T09:30:00.000+0700', closeTime: null, createUserId: 'mary', superProcessInstanceId: 'p9', superCaseInstanceId: null, active: true, completed: false, terminated: false, closed: false }),
    [`GET ${ENGINE}/history/case-activity-instance`]: () =>
      json([
        activity({ id: 'h0', caseActivityId: 'PI_approved', caseActivityName: 'Claim approved', caseActivityType: 'milestone', completed: true }),
        activity({ id: 'h1', caseActivityId: 'PI_review', caseActivityName: 'Review claim', active: true }),
      ]),
    [`GET ${ENGINE}/task`]: () => json([]),
    [`GET ${ENGINE}/variable-instance/count`]: () => json({ count: 0 }),
    [`GET ${ENGINE}/variable-instance`]: () => json([]),
    ...routes,
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

describe('case definitions', () => {
  it('lists every case with its open instances and opens its version', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/cockpit/cases');

    const table = await screen.findByRole('table', { name: 'Case definitions' });
    const row = within(table).getByRole('row', { name: /Claim case/ });
    await waitFor(() => expect(within(row).getByText('2')).toBeInTheDocument());
    expect(screen.getByText('1 case definition')).toBeInTheDocument();
    // the section is in the navigation of the monitoring pages
    expect(screen.getByRole('link', { name: 'Cases (CMMN)' })).toHaveAttribute('aria-current', 'page');

    await user.click(within(row).getByRole('link', { name: 'Claim case' }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/cockpit/cases/definition/${encodeURIComponent(DEFINITION)}`));
  });

  it('says how to deploy a case when there is none', async () => {
    backend({
      [`GET ${ENGINE}/case-definition/count`]: () => json({ count: 0 }),
      [`GET ${ENGINE}/case-definition`]: () => json([]),
    });
    renderApp('/cockpit/cases');
    expect(await screen.findByText('No case definitions deployed')).toBeInTheDocument();
    const hint = screen.getByText(/deployed as .cmmn files/);
    expect(within(hint).getByRole('link', { name: 'Deployments' })).toHaveAttribute('href', '/cockpit/deployments');
  });

  it('shows a version with the open plan items on its diagram and creates an instance', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/case-definition/${DEFINITION}/create`]: () =>
        json({ id: 'c1', caseDefinitionId: DEFINITION, businessKey: 'CL-9', tenantId: null, active: true, completed: false, terminated: false }),
    });
    const router = renderApp(`/cockpit/cases/definition/${DEFINITION}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Claim case' })).toBeInTheDocument();
    // active and enabled plan items count, the plan model and other states do not
    const diagram = await screen.findByTestId('case-diagram');
    await waitFor(() => expect(within(diagram).getByText('PI_review: 1')).toBeInTheDocument());
    expect(within(diagram).getByText('PI_check: 1')).toBeInTheDocument();
    expect(within(diagram).queryByText(/PI_pay|CasePlanModel/)).toBeNull();
    const instances = await screen.findByRole('table', { name: 'Open instances of this version' });
    expect(within(instances).getByText('CL-2')).toBeInTheDocument();
    expect(within(instances).getByText('Completed')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Create case instance' }));
    const dialog = await screen.findByRole('dialog', { name: 'Create a case instance' });
    await user.type(within(dialog).getByLabelText('Business key'), 'CL-9');
    await user.click(within(dialog).getByRole('button', { name: 'Create case instance' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/cases/instance/c1'));
    expect(server.find('POST', `${ENGINE}/case-definition/${DEFINITION}/create`)[0]?.body).toMatchObject({ businessKey: 'CL-9' });
  });
});

describe('a case instance', () => {
  it('shows the plan items in the order of the case with what may be done with them', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`POST ${ENGINE}/case-execution/x2/manual-start`]: () => empty(),
      [`POST ${ENGINE}/case-execution/x1/terminate`]: () => empty(),
    });
    renderApp('/cockpit/cases/instance/c1');

    const items = await screen.findByRole('table', { name: 'Plan items' });
    const rows = within(items).getAllByRole('row').slice(1);
    // the case instance itself is the page, not a row; a stage is followed by what it contains
    expect(rows.map((row) => within(row).getAllByRole('cell')[0]?.textContent)).toEqual(['Review claim', 'Check documents', 'Settlement', 'Pay out']);
    expect(within(rows[1] as HTMLElement).getByText('Enabled')).toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByText('Available')).toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByText('—')).toBeInTheDocument();
    expect(within(rows[3] as HTMLElement).getByRole('button', { name: 'Enable again: Pay out' })).toBeInTheDocument();
    expect(screen.getByText('Started by a process instance')).toHaveAttribute('href', '/cockpit/processes/instance/p9');

    // starting is not confirmed
    await user.click(within(rows[1] as HTMLElement).getByRole('button', { name: 'Start: Check documents' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/case-execution/x2/manual-start`)).toHaveLength(1));
    expect(await screen.findByText('“Check documents” started')).toBeInTheDocument();

    // terminating is
    await user.click(within(rows[0] as HTMLElement).getByRole('button', { name: 'Terminate: Review claim' }));
    const dialog = await screen.findByRole('dialog', { name: 'Terminate the plan item' });
    expect(dialog).toHaveTextContent('“Review claim” and everything it contains is terminated');
    expect(server.find('POST', `${ENGINE}/case-execution/x1/terminate`)).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Terminate' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/case-execution/x1/terminate`)).toHaveLength(1));
  });

  it('reports a refused action with its reason and keeps the confirmation open', async () => {
    const user = userEvent.setup();
    backend({ [`POST ${ENGINE}/case-instance/c1/complete`]: forbidden });
    renderApp('/cockpit/cases/instance/c1');

    await user.click(await screen.findByRole('button', { name: 'Complete case' }));
    const dialog = await screen.findByRole('dialog', { name: 'Complete the case instance' });
    await user.click(within(dialog).getByRole('button', { name: 'Complete case' }));
    expect(await screen.findByText('The case instance could not be changed')).toBeInTheDocument();
    // the reason the engine gave, e.g. a missing permission
    expect(screen.getByText('The user is not authorized')).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Complete the case instance' })).toBeInTheDocument();
  });

  it('marks the plan items on the diagram and filters the list by the chosen one', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/cockpit/cases/instance/c1');

    const diagram = await screen.findByTestId('case-diagram');
    await waitFor(() => expect(within(diagram).getByText('PI_review is-active')).toBeInTheDocument());
    expect(within(diagram).getByText('PI_check is-enabled')).toBeInTheDocument();
    expect(within(diagram).getByText('PI_pay is-disabled')).toBeInTheDocument();
    // what only the history knows: the milestone that occurred
    expect(within(diagram).getByText('PI_approved is-done')).toBeInTheDocument();

    await user.click(within(diagram).getByText('PI_check is-enabled'));
    const items = await screen.findByRole('table', { name: 'Plan items' });
    await waitFor(() => expect(within(items).getAllByRole('row')).toHaveLength(2));
    expect(screen.getByText('Only what belongs to “Check documents”.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Show all' }));
    await waitFor(() => expect(within(items).getAllByRole('row')).toHaveLength(5));
  });

  it('shows a closed case instance from the history, read only', async () => {
    const user = userEvent.setup();
    backend({
      [`GET ${ENGINE}/case-instance/c1`]: () => json({ type: 'InvalidRequestException', message: 'not found' }, 404),
      [`GET ${ENGINE}/history/case-instance/c1`]: () =>
        json({ id: 'c1', businessKey: 'CL-1', caseDefinitionId: DEFINITION, caseDefinitionKey: 'claim', caseDefinitionName: 'Claim case', createTime: '2026-03-01T09:30:00.000+0700', closeTime: '2026-03-02T09:30:00.000+0700', createUserId: null, superProcessInstanceId: null, superCaseInstanceId: null, active: false, completed: false, terminated: false, closed: true }),
      [`GET ${ENGINE}/history/variable-instance`]: () =>
        json([{ id: 'v1', name: 'amount', type: 'Long', value: 1250, caseExecutionId: 'c1', errorMessage: null }]),
    });
    renderApp('/cockpit/cases/instance/c1');

    expect(await screen.findByText(/This case instance is closed/)).toBeInTheDocument();
    expect(screen.getByText('Closed', { selector: '.badge' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Complete case|Terminate case|Close case/ })).toBeNull();
    await user.click(screen.getByRole('tab', { name: 'Variables' }));
    expect(await screen.findByText('1250')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete/ })).toBeNull();
    await user.click(screen.getByRole('tab', { name: 'History' }));
    expect(await screen.findByRole('table', { name: 'History' })).toHaveTextContent('Claim approved');
  });

  it('says when a case instance does not exist', async () => {
    backend({
      [`GET ${ENGINE}/case-instance/c1`]: () => json({ type: 'InvalidRequestException', message: 'not found' }, 404),
      [`GET ${ENGINE}/history/case-instance/c1`]: () => json({ type: 'InvalidRequestException', message: 'not found' }, 404),
    });
    renderApp('/cockpit/cases/instance/c1');
    expect(await screen.findByText('This case instance does not exist')).toBeInTheDocument();
  });

  it('orders plan items as a tree and prefers what runs now over the history', () => {
    const tree = itemTree(EXECUTIONS);
    expect(tree.map((row) => [row.execution.activityId, row.depth])).toEqual([
      ['PI_review', 0],
      ['PI_check', 0],
      ['PI_settle', 0],
      ['PI_pay', 1],
    ]);
    const states = itemStates(EXECUTIONS, [activity({ caseActivityId: 'PI_review', completed: true })]);
    expect(states.get('PI_review')).toBe('active');
    expect(states.get('PI_settle')).toBe('available');
    expect(states.has('CasePlanModel')).toBe(false);
  });
});

describe('case history', () => {
  it('lists case instances and filters the closed ones', async () => {
    const user = userEvent.setup();
    const server = backend({
      [`GET ${ENGINE}/history/case-instance/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/history/case-instance`]: () =>
        json([{ id: 'c7', businessKey: 'CL-7', caseDefinitionId: DEFINITION, caseDefinitionKey: 'claim', caseDefinitionName: 'Claim case', createTime: '2026-03-01T09:30:00.000+0700', closeTime: '2026-03-01T10:30:00.000+0700', durationInMillis: 3_600_000, createUserId: null, superProcessInstanceId: null, superCaseInstanceId: null, active: false, completed: false, terminated: false, closed: true }]),
    });
    renderApp('/cockpit/case-history');

    const table = await screen.findByRole('table', { name: 'Case history' });
    expect(within(table).getByText('CL-7')).toBeInTheDocument();
    expect(within(table).getByRole('link', { name: 'Claim case' })).toHaveAttribute('href', `/cockpit/cases/definition/${encodeURIComponent(DEFINITION)}`);
    const filters = screen.getByRole('group', { name: 'State' });
    await user.click(within(filters).getByRole('button', { name: 'Closed' }));
    await waitFor(() =>
      expect(server.find('GET', `${ENGINE}/history/case-instance`).some((request) => request.query.get('closed') === 'true')).toBe(true),
    );
    expect(within(filters).getByRole('button', { name: 'Closed' })).toHaveAttribute('aria-pressed', 'true');
  });
});
