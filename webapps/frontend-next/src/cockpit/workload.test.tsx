/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getColumnValues } from '../api/cockpit';
import { WORKLOAD_SUPPORT_FRESH, byLoad, getTaskTotals, getWorkload, mapLimited, type WorkloadRow } from '../api/workload';
import { Providers, createQueryClient, routes } from '../app/App';
import { decodeSearch, encodeSearch } from '../tasklist/searchLinks';
import { toTaskQuery } from '../tasklist/search';
import { setTenantScope } from '../lib/userPrefs';
import { preloadRoutes } from '../test/routes';
import { fakeServer, json, type FakeHandler } from '../test/server';
import { compactValue, readVariableColumns, writeVariableColumns } from './VariableColumns';

preloadRoutes(routes);

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';

function backend(routes: Record<string, FakeHandler>) {
  return fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: ['tasklist', 'cockpit', 'welcome'] }),
    [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [] }),
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

const row = (id: string, open: number, overdue: number): WorkloadRow => ({ id, name: id, open, overdue, unassigned: null, oldest: null });

describe('team workload', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('counts per group and per person and links every count to the task list', async () => {
    const server = backend({
      [`GET ${ENGINE}/filter`]: () =>
        json([
          { id: 'mine', owner: 'demo', query: { assignee: 'demo' } },
          { id: 'all', owner: null, query: { taskVariables: [], processVariables: [] } },
        ]),
      [`GET ${ENGINE}/group`]: () => json([{ id: 'sales', name: 'Sales' }, { id: 'idle', name: 'Idle' }]),
      [`GET ${ENGINE}/user`]: () => json([{ id: 'anna', firstName: 'Anna', lastName: 'Le' }]),
      [`GET ${ENGINE}/group/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/task/count`]: (request) => {
        const q = request.query;
        if (q.get('candidateGroup') === 'idle') return json({ count: 0 });
        if (q.get('candidateGroup') === 'sales') return json({ count: q.get('unassigned') ? 3 : q.get('dueBefore') ? 2 : 5 });
        if (q.get('assignee') === 'anna') return json({ count: q.get('dueBefore') ? 1 : 4 });
        return json({ count: q.get('unassigned') ? (q.get('withoutCandidateGroups') ? 1 : 6) : q.get('dueBefore') ? 3 : 9 });
      },
      [`GET ${ENGINE}/task`]: () => json([{ created: '2026-01-01T09:00:00.000+0000' }]),
    });
    renderApp('/cockpit/workload');

    const groups = await screen.findByRole('table', { name: 'By candidate group' });
    const sales = await within(groups).findByRole('link', { name: 'Open tasks of Sales: 5' });
    const link = new URL(sales.getAttribute('href') ?? '', 'http://x');
    expect(link.pathname).toBe('/tasklist');
    // the filter without criteria, so the list shows what was counted
    expect(link.searchParams.get('filter')).toBe('all');
    expect(decodeSearch(link.searchParams.get('q'))?.criteria).toMatchObject([{ field: 'candidateGroup', value: 'sales' }]);
    const overdue = within(groups).getByRole('link', { name: 'Overdue tasks of Sales: 2' });
    expect(decodeSearch(new URL(overdue.getAttribute('href') ?? '', 'http://x').searchParams.get('q'))?.view).toBe('overdue');
    expect(within(groups).getByRole('link', { name: 'Unassigned tasks of Sales: 3' })).toBeVisible();
    // a group without open tasks is hidden by default
    expect(within(groups).queryByText('Idle')).toBeNull();

    const people = screen.getByRole('table', { name: 'By person' });
    expect(within(people).getByText('Anna Le')).toBeVisible();
    expect(within(people).getByRole('link', { name: 'Overdue tasks of Anna Le: 1' })).toBeVisible();
    expect(await screen.findByText(/1 unassigned tasks have no candidate group/)).toBeVisible();
    // the group query asks for claimed tasks too
    expect(server.requests.some((r) => r.query.get('candidateGroup') === 'sales' && r.query.get('includeAssignedTasks') === 'true')).toBe(true);
  });

  it('says which part the user may not see and keeps the rest', async () => {
    const forbidden = () => json({ type: 'AuthorizationException', message: 'not allowed' }, 403);
    backend({
      [`GET ${ENGINE}/filter`]: () => json([]),
      [`GET ${ENGINE}/group`]: forbidden,
      [`GET ${ENGINE}/group/count`]: forbidden,
      [`GET ${ENGINE}/user`]: () => json([{ id: 'anna', firstName: 'Anna', lastName: 'Le' }]),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/task`]: () => json([]),
    });
    renderApp('/cockpit/workload');

    expect(await screen.findByText('You may not list groups')).toBeVisible();
    expect(await screen.findByRole('table', { name: 'By person' })).toBeVisible();
    expect(screen.getByText(/You have no task list without criteria/)).toBeVisible();
  });

  it('shows a failure of the numbers also when neither groups nor people may be listed', async () => {
    const forbidden = () => json({ type: 'AuthorizationException', message: 'not allowed' }, 403);
    backend({
      [`GET ${ENGINE}/filter`]: () => json([]),
      [`GET ${ENGINE}/group`]: forbidden,
      [`GET ${ENGINE}/group/count`]: forbidden,
      [`GET ${ENGINE}/user`]: forbidden,
      [`GET ${ENGINE}/user/count`]: forbidden,
      [`POST ${ENGINE}/task/workload`]: () => json({ type: 'AuthorizationException', message: 'numbers refused' }, 403),
    });
    renderApp('/cockpit/workload');

    expect(await screen.findByText('You may not list groups')).toBeVisible();
    expect(await screen.findByText('numbers refused')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible();
  });

  it('pages through many groups and searches people by id and name', async () => {
    const server = backend({
      [`GET ${ENGINE}/filter`]: () => json([]),
      [`GET ${ENGINE}/group`]: (request) => {
        const first = Number(request.query.get('firstResult'));
        if (request.query.get('nameLike')) return json([{ id: 'ops', name: 'Operations' }]);
        return json(Array.from({ length: 50 }, (_, i) => ({ id: `g${first + i}`, name: `Group ${first + i}` })));
      },
      [`GET ${ENGINE}/group/count`]: (request) => json({ count: request.query.get('nameLike') ? 1 : 120 }),
      [`GET ${ENGINE}/user`]: (request) =>
        json(request.query.get('lastNameLike') === '%le%' ? [{ id: 'anna', firstName: 'Anna', lastName: 'Le' }] : []),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 3 }),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/task`]: () => json([]),
    });
    const user = userEvent.setup();
    renderApp('/cockpit/workload');
    expect(await screen.findByText('1–50 of 120')).toBeVisible();
    expect(screen.getByText(/Sorted by load within each page of 50/)).toBeVisible();
    await user.click(screen.getAllByRole('button', { name: 'Next page' })[0] as HTMLElement);
    await waitFor(() => expect(server.requests.some((r) => r.path.endsWith('/group') && r.query.get('firstResult') === '50')).toBe(true));
    await user.type(screen.getByRole('searchbox', { name: 'Search groups' }), 'oper');
    const groups = screen.getByRole('table', { name: 'By candidate group' });
    expect(await within(groups).findByText('Operations')).toBeVisible();
    await user.type(screen.getByRole('searchbox', { name: 'Search people by id or name' }), 'le');
    const people = await screen.findByRole('table', { name: 'By person' });
    expect(within(people).getByText('Anna Le')).toBeVisible();
    expect(server.requests.some((r) => r.query.get('id') === 'le')).toBe(true);
    expect(screen.getByText(/up to 50 each by first and last name/)).toBeVisible();
  });

  it('reads the whole board with one request where the server counts', async () => {
    const server = backend({
      [`GET ${ENGINE}/filter`]: () => json([]),
      [`GET ${ENGINE}/group`]: () => json([{ id: 'sales', name: 'Sales' }, { id: 'idle', name: 'Idle' }]),
      [`GET ${ENGINE}/user`]: () => json([{ id: 'anna', firstName: 'Anna', lastName: 'Le' }, { id: 'bob', firstName: 'Bob', lastName: 'Ng' }]),
      [`GET ${ENGINE}/group/count`]: () => json({ count: 2 }),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 2 }),
      [`POST ${ENGINE}/task/workload`]: () =>
        json({
          totals: { id: null, open: 9, unassigned: 6, overdue: 3, oldest: '2026-01-01T09:00:00.000+0000' },
          orphaned: 1,
          groups: [
            { id: 'sales', open: 5, unassigned: 3, overdue: 2, oldest: '2026-01-01T09:00:00.000+0000' },
            { id: 'idle', open: 0, unassigned: 0, overdue: 0, oldest: null },
          ],
          users: [
            { id: 'anna', open: 1, unassigned: 0, overdue: 0, oldest: '2026-01-02T09:00:00.000+0000' },
            { id: 'bob', open: 2, unassigned: 0, overdue: 1, oldest: '2026-01-03T09:00:00.000+0000' },
          ],
        }),
    });
    renderApp('/cockpit/workload');

    const groups = await screen.findByRole('table', { name: 'By candidate group' });
    expect(await within(groups).findByRole('link', { name: 'Open tasks of Sales: 5' })).toBeVisible();
    expect(within(groups).getByRole('link', { name: 'Unassigned tasks of Sales: 3' })).toBeVisible();
    expect(within(groups).getByRole('link', { name: 'Overdue tasks of Sales: 2' })).toBeVisible();
    expect(within(groups).queryByText('Idle')).toBeNull();
    // the busiest person first
    const people = screen.getByRole('table', { name: 'By person' });
    expect(within(people).getAllByRole('row').slice(1).map((line) => line.textContent)).toEqual([
      expect.stringContaining('Bob Ng'),
      expect.stringContaining('Anna Le'),
    ]);
    expect(document.querySelector('.workload__tiles')?.textContent).toMatch(/9.*6.*3/);
    expect(await screen.findByText(/1 unassigned tasks have no candidate group/)).toBeVisible();

    // one request for all the numbers, and no task query per row
    const calls = server.find('POST', `${ENGINE}/task/workload`);
    expect(calls).toHaveLength(1);
    expect(calls[0]?.body).toMatchObject({ groupIdIn: ['sales', 'idle'], userIdIn: ['anna', 'bob'] });
    expect((calls[0]?.body as { overdueBefore: string }).overdueBefore).toMatch(/^\d{4}-\d\d-\d\dT/);
    expect(server.requests.filter((r) => r.path.endsWith('/task/count') || r.path.endsWith('/task'))).toHaveLength(0);
  });

  it('asks an older server per group and person, and for the aggregate only once', async () => {
    const server = backend({
      [`GET ${ENGINE}/filter`]: () => json([]),
      [`GET ${ENGINE}/group`]: () => json([{ id: 'sales', name: 'Sales' }]),
      [`GET ${ENGINE}/user`]: () => json([{ id: 'anna', firstName: 'Anna', lastName: 'Le' }]),
      [`GET ${ENGINE}/group/count`]: () => json({ count: 1 }),
      [`GET ${ENGINE}/user/count`]: () => json({ count: 1 }),
      // the task resource of an older server takes "workload" for a task id
      [`POST ${ENGINE}/task/workload`]: () => json({ type: 'NotAllowedException', message: 'HTTP 405 Method Not Allowed' }, 405),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 4 }),
      [`GET ${ENGINE}/task`]: () => json([]),
    });
    const now = new Date('2026-03-01T10:00:00Z');

    const first = await getWorkload([{ id: 'sales', name: 'Sales' }], [{ id: 'anna', name: 'Anna Le' }], now);
    expect(first.summary).toEqual({ open: 4, unassigned: 4, overdue: 4, orphaned: 4, assigned: 4 });
    expect(first.groups).toEqual([{ id: 'sales', name: 'Sales', open: 4, unassigned: 4, overdue: 4, oldest: null }]);
    expect(first.users).toEqual([{ id: 'anna', name: 'Anna Le', open: 4, unassigned: null, overdue: 4, oldest: null }]);
    await getWorkload([{ id: 'sales', name: 'Sales' }], [], now);
    await getTaskTotals(now);

    expect(server.find('POST', `${ENGINE}/task/workload`)).toHaveLength(1);
    // 5 totals + 3 of the group + 2 of the person, then 5 + 3, then 5
    expect(server.find('GET', `${ENGINE}/task/count`)).toHaveLength(23);
  });

  it('reads the totals alone and passes the tenant in view', async () => {
    const server = backend({
      [`POST ${ENGINE}/task/workload`]: () =>
        json({ totals: { id: null, open: 7, unassigned: 2, overdue: 1, oldest: null }, orphaned: 0, groups: [], users: [] }),
    });
    setTenantScope('acme');

    const totals = await getTaskTotals(new Date('2026-03-01T10:00:00Z'));

    expect(totals).toEqual({ open: 7, unassigned: 2, overdue: 1, orphaned: 0, assigned: 5 });
    expect(server.find('POST', `${ENGINE}/task/workload`)[0]?.body).toEqual({
      overdueBefore: expect.stringMatching(/^2026-03-01T/),
      tenantIdIn: ['acme'],
    });
  });

  it('falls back when the endpoint goes away after it answered, and asks for it again later', async () => {
    // a rolling update or a rollback: the node that answers changes while the page stays open
    let status = 200;
    const server = backend({
      [`POST ${ENGINE}/task/workload`]: () =>
        status === 200
          ? json({ totals: { id: null, open: 7, unassigned: 2, overdue: 1, oldest: null }, orphaned: 0, groups: [], users: [] })
          : json({ type: 'NotFoundException', message: 'gone' }, status),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 3 }),
    });
    vi.useFakeTimers({ toFake: ['Date'] });
    const now = new Date('2026-03-01T10:00:00Z');
    vi.setSystemTime(now);

    expect((await getTaskTotals(now)).open).toBe(7);
    status = 404;
    expect((await getTaskTotals(now)).open).toBe(3);
    // known to be missing: not asked again at once
    expect((await getTaskTotals(now)).open).toBe(3);
    expect(server.find('POST', `${ENGINE}/task/workload`)).toHaveLength(2);

    status = 200;
    vi.setSystemTime(now.getTime() + WORKLOAD_SUPPORT_FRESH);
    expect((await getTaskTotals(now)).open).toBe(7);
    expect(server.find('POST', `${ENGINE}/task/workload`)).toHaveLength(3);
  });

  it('falls back when the server has the endpoint but not its statements (501)', async () => {
    const server = backend({
      [`POST ${ENGINE}/task/workload`]: () =>
        json({ type: 'RestException', message: 'The workload statements are not mapped' }, 501),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 2 }),
    });

    expect((await getTaskTotals(new Date())).open).toBe(2);
    expect(server.find('GET', `${ENGINE}/task/count`)).toHaveLength(5);
  });

  it('fails when the server fails, instead of falling back to the task queries', async () => {
    const server = backend({
      [`POST ${ENGINE}/task/workload`]: () => json({ type: 'ProcessEngineException', message: 'down' }, 500),
    });

    await expect(getTaskTotals(new Date())).rejects.toMatchObject({ status: 500 });
    expect(server.find('GET', `${ENGINE}/task/count`)).toHaveLength(0);
  });

  it('orders the busiest first and runs a few requests at a time', async () => {
    expect([row('a', 1, 0), row('b', 5, 0), row('c', 2, 2)].sort(byLoad).map((entry) => entry.id)).toEqual(['c', 'b', 'a']);
    let running = 0;
    let most = 0;
    const result = await mapLimited([1, 2, 3, 4, 5, 6], 2, async (value) => {
      running++;
      most = Math.max(most, running);
      await new Promise((resolve) => setTimeout(resolve, 5));
      running--;
      return value * 2;
    });
    expect(result).toEqual([2, 4, 6, 8, 10, 12]);
    expect(most).toBe(2);
  });

  it('opens the task list of a group, with claimed tasks, or of one person', () => {
    expect(toTaskQuery([{ id: '1', field: 'candidateGroup', value: ' sales ' }], null)).toEqual({
      candidateGroup: 'sales',
      includeAssignedTasks: true,
    });
    expect(toTaskQuery([{ id: '1', field: 'assigneeId', value: 'anna' }], null)).toEqual({ assignee: 'anna' });
    const text = encodeSearch([{ id: '1', field: 'assigneeId', value: 'anna' }], false, 'overdue');
    expect(decodeSearch(text)).toMatchObject({ view: 'overdue', criteria: [{ field: 'assigneeId', value: 'anna' }] });
    expect(decodeSearch(JSON.stringify({ c: [], v: 'everything' }))?.view).toBeUndefined();
  });
});

describe('variable columns', () => {
  it('remembers the names per process and drops anything malformed', () => {
    writeVariableColumns('order', ['amount', ' customer ', 'amount', '']);
    writeVariableColumns('loan', ['score']);
    expect(readVariableColumns('order')).toEqual(['amount', 'customer']);
    expect(readVariableColumns('loan')).toEqual(['score']);
    writeVariableColumns('loan', []);
    expect(readVariableColumns('loan')).toEqual([]);
    expect(readVariableColumns('order')).toEqual(['amount', 'customer']);
    window.localStorage.setItem('fasti.next.cockpit.variableColumns', '{"order": "nope"}');
    expect(readVariableColumns('order')).toEqual([]);
    window.localStorage.setItem('fasti.next.cockpit.variableColumns', 'not json');
    expect(readVariableColumns('order')).toEqual([]);
  });

  it('shows values compactly', () => {
    const date = (value: Date) => value.toISOString().slice(0, 10);
    expect(compactValue(undefined, date)).toMatchObject({ text: '—', muted: true });
    expect(compactValue({ type: 'Long', value: 1250 }, date).text).toBe('1250');
    expect(compactValue({ type: 'Boolean', value: false }, date).text).toBe('false');
    expect(compactValue({ type: 'Json', value: '{"a":1}' }, date)).toMatchObject({ text: '{Json}', muted: true });
    expect(compactValue({ type: 'Date', value: '2026-03-01T09:00:00.000+0000' }, date).text).toBe('2026-03-01');
    const long = compactValue({ type: 'String', value: 'x'.repeat(80) }, date);
    expect(long.text.length).toBeLessThanOrEqual(40);
    expect(long.full).toHaveLength(80);
  });

  it('reads one query per name for all shown instances; the instance scope wins', async () => {
    const server = backend({
      [`POST ${ENGINE}/variable-instance`]: (request) => {
        const body = request.body as { variableName: string; processInstanceIdIn: string[] };
        return json(
          body.variableName === 'amount'
            ? [
                { name: 'amount', type: 'Long', value: 5, processInstanceId: 'p1', activityInstanceId: 'task:1' },
                { name: 'amount', type: 'Long', value: 7, processInstanceId: 'p1', activityInstanceId: 'p1' },
                { name: 'amount', type: 'Long', value: 9, processInstanceId: 'p2', activityInstanceId: 'p2' },
              ]
            : [],
        );
      },
    });
    const values = await getColumnValues({ instanceIds: ['p1', 'p2', 'p1'], names: ['amount', 'customer'] });
    expect(values.get('p1')?.get('amount')?.value).toBe(7);
    expect(values.get('p2')?.get('amount')?.value).toBe(9);
    const calls = server.find('POST', `${ENGINE}/variable-instance`);
    expect(calls).toHaveLength(2);
    expect(calls[0]?.body).toEqual({ processInstanceIdIn: ['p1', 'p2'], variableName: 'amount' });
    expect(calls[0]?.query.get('deserializeValues')).toBe('false');
  });
});
