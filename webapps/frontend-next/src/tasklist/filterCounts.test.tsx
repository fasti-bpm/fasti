/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getFilterTasks, getTaskFilterCounts, getTaskFilters, resetBusinessKeys } from '../api/engine';
import { Providers, createQueryClient, routes } from '../app/App';
import { writePreference } from '../lib/storage';
import { preloadRoutes } from '../test/routes';
import { fakeServer, json, type FakeHandler } from '../test/server';
import { refreshTasks } from './actions';
import { COUNTS_FOLLOW_UP, FILTER_COUNTS_KEY, refreshFilterCountsSoon } from './filterCounts';

preloadRoutes(routes);

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';

const FILTERS = [
  { id: 'f1', name: 'My tasks', resourceType: 'Task', owner: null, properties: { priority: 1 } },
  { id: 'f2', name: 'All tasks', resourceType: 'Task', owner: null, properties: { priority: 2 } },
];

const halTask = (id: string, processInstanceId: string | null) => ({
  id,
  name: `Task ${id}`,
  assignee: null,
  created: '2026-03-01T09:00:00.000+0000',
  due: null,
  followUp: null,
  priority: 50,
  processInstanceId,
  processDefinitionId: processInstanceId ? 'order:1:abc' : null,
  caseInstanceId: null,
  caseDefinitionId: null,
  taskDefinitionKey: 'work',
  _embedded: { variable: [] },
});

function backend(routes: Record<string, FakeHandler> = {}) {
  return fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: ['tasklist', 'welcome'] }),
    [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [] }),
    [`GET ${ENGINE}/filter`]: (request) =>
      json(request.query.get('itemCount') ? [{ id: 'f1', itemCount: 2 }, { id: 'f2', itemCount: 7 }] : FILTERS),
    [`POST ${ENGINE}/filter/f1/list`]: () =>
      json({ count: 3, _embedded: { task: [halTask('t1', 'p1'), halTask('t2', 'p1'), halTask('t3', 'p2')] } }),
    [`POST ${ENGINE}/filter/f2/list`]: () => json({ count: 7, _embedded: { task: [halTask('t9', null)] } }),
    [`GET ${ENGINE}/process-instance`]: (request) =>
      json((request.query.get('processInstanceIds') ?? '').split(',').map((id) => ({ id, businessKey: `key-${id}` }))),
    ...routes,
  });
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = createQueryClient();
  render(
    <Providers queryClient={queryClient} locale="en">
      <RouterProvider router={router} />
    </Providers>,
  );
  return queryClient;
}

const counted = (server: ReturnType<typeof backend>) =>
  server.find('GET', `${ENGINE}/filter`).filter((request) => request.query.get('itemCount') === 'true');
const listed = (server: ReturnType<typeof backend>) =>
  server.find('GET', `${ENGINE}/filter`).filter((request) => !request.query.has('itemCount'));

describe('filters and their task counts', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('reads the filters without counting, and the counts apart from them', async () => {
    const server = backend();
    expect((await getTaskFilters()).map((filter) => [filter.id, filter.itemCount])).toEqual([
      ['f1', undefined],
      ['f2', undefined],
    ]);
    expect(await getTaskFilterCounts()).toEqual({ f1: 2, f2: 7 });
    expect(listed(server)).toHaveLength(1);
    expect(counted(server)).toHaveLength(1);
  });

  it('shows the counts in the open filter pane; the filter in use takes its count from the task list', async () => {
    const server = backend();
    renderApp('/tasklist?filter=f1');

    const pane = await screen.findByRole('complementary', { name: 'Filters' });
    // read apart: 7 tasks in the other filter
    expect(await within(pane).findByLabelText('7 tasks')).toBeVisible();
    // the list of the filter in use says 3, which is newer than the 2 that were counted
    expect(await within(pane).findByLabelText('3 tasks')).toBeVisible();
    expect(counted(server)).toHaveLength(1);
    expect(listed(server)).toHaveLength(1);
  });

  it('does not count behind a closed filter pane until the filter menu is used', async () => {
    writePreference('tasklist.demo', { filtersCollapsed: true });
    const server = backend();
    const user = userEvent.setup();
    const queryClient = renderApp('/tasklist?filter=f1');

    const menu = await screen.findByRole('combobox', { name: 'Filters' });
    await screen.findByText('Task t1');
    // the filter in use still shows its count, from the list
    expect(within(menu).getByRole('option', { name: 'My tasks (3)' })).toBeInTheDocument();
    expect(within(menu).getByRole('option', { name: 'All tasks' })).toBeInTheDocument();
    expect(counted(server)).toHaveLength(0);

    await user.click(menu);
    expect(await within(menu).findByRole('option', { name: 'All tasks (7)' })).toBeInTheDocument();
    expect(counted(server)).toHaveLength(1);

    // used again while the counts are fresh: no request; once they are old: read again
    await user.tab();
    await user.click(menu);
    expect(counted(server)).toHaveLength(1);
    await queryClient.invalidateQueries({ queryKey: FILTER_COUNTS_KEY, exact: true, refetchType: 'none' });
    await user.tab();
    await user.click(menu);
    await waitFor(() => expect(counted(server)).toHaveLength(2));
  });

  it('asks for the tasks of the filter used last beside the filters, not after them', async () => {
    writePreference('tasklist.demo', { filterId: 'f1' });
    let answerFilters: (() => void) | undefined;
    const server = backend({
      [`GET ${ENGINE}/filter`]: (request) =>
        new Promise<Response>((resolve) => {
          const body = request.query.get('itemCount') ? [{ id: 'f1', itemCount: 2 }, { id: 'f2', itemCount: 7 }] : FILTERS;
          if (request.query.get('itemCount')) resolve(json(body));
          else answerFilters = () => resolve(json(body));
        }),
    });
    renderApp('/tasklist');

    // the filters are still on their way
    await waitFor(() => expect(server.find('POST', `${ENGINE}/filter/f1/list`)).toHaveLength(1));
    expect(screen.queryByText('Task t1')).toBeNull();
    answerFilters?.();
    expect(await screen.findByText('Task t1')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/filter/f1/list`)).toHaveLength(1);
  });

  it('shows the first filter when the one used last is gone, without an error', async () => {
    writePreference('tasklist.demo', { filterId: 'gone' });
    const server = backend({
      [`POST ${ENGINE}/filter/gone/list`]: () => json({ type: 'InvalidRequestException', message: 'Filter with id gone does not exist' }, 404),
    });
    renderApp('/tasklist');

    expect(await screen.findByText('Task t1')).toBeVisible();
    expect(screen.queryByRole('alert')).toBeNull();
    // asked once, not again and again
    expect(server.find('POST', `${ENGINE}/filter/gone/list`)).toHaveLength(1);
  });

  it('does not read the counts of a menu that was left after task actions', async () => {
    writePreference('tasklist.demo', { filtersCollapsed: true });
    const server = backend();
    const user = userEvent.setup();
    const queryClient = renderApp('/tasklist?filter=f1');
    const menu = await screen.findByRole('combobox', { name: 'Filters' });
    await screen.findByText('Task t1');
    await user.click(menu);
    await waitFor(() => expect(counted(server)).toHaveLength(1));

    // the user turns away from the menu, then works on tasks
    await user.tab();
    refreshFilterCountsSoon(queryClient, 10);
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(counted(server)).toHaveLength(1);

    // back to the menu: the counts are old and read again
    await user.click(menu);
    await waitFor(() => expect(counted(server)).toHaveLength(2));
  });

  it('reads the task list at least every minute, also in the background, while notices are on', async () => {
    vi.stubGlobal('Notification', { permission: 'granted' });
    writePreference('tasks.notify', true);
    backend();
    const queryClient = renderApp('/tasklist?filter=f1');
    await screen.findByText('Task t1');

    const options = queryClient
      .getQueryCache()
      .findAll({ queryKey: ['tasks', 'f1'] })
      .flatMap((query) => query.observers.map((observer) => observer.options))
      .map(({ refetchInterval, refetchIntervalInBackground }) => ({ refetchInterval, refetchIntervalInBackground }));
    expect(options).toContainEqual({ refetchInterval: 30_000, refetchIntervalInBackground: true });
  });

  it('reads the counts once after a burst of task actions, and the filters not at all', async () => {
    vi.useFakeTimers();
    const server = backend();
    const queryClient = new QueryClient();
    const observer = new QueryObserver(queryClient, {
      queryKey: FILTER_COUNTS_KEY,
      queryFn: () => getTaskFilterCounts(),
      staleTime: 30_000,
    });
    const stop = observer.subscribe(() => undefined);
    const filters = new QueryObserver(queryClient, { queryKey: ['filters'], queryFn: () => getTaskFilters(), staleTime: 60_000 });
    const stopFilters = filters.subscribe(() => undefined);
    await vi.advanceTimersByTimeAsync(0);
    expect(counted(server)).toHaveLength(1);
    expect(listed(server)).toHaveLength(1);

    // three tasks completed one after the other
    refreshTasks(queryClient, 't1');
    await vi.advanceTimersByTimeAsync(1_000);
    refreshTasks(queryClient, 't2');
    await vi.advanceTimersByTimeAsync(1_000);
    refreshTasks(queryClient, 't3');
    await vi.advanceTimersByTimeAsync(COUNTS_FOLLOW_UP - 1);
    expect(counted(server)).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(counted(server)).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(counted(server)).toHaveLength(2);
    // the filters themselves did not change
    expect(listed(server)).toHaveLength(1);
    stop();
    stopFilters();
  });

  it('only marks counts nobody looks at as old', async () => {
    vi.useFakeTimers();
    const server = backend();
    const queryClient = new QueryClient();
    await queryClient.fetchQuery({ queryKey: FILTER_COUNTS_KEY, queryFn: () => getTaskFilterCounts() });
    expect(counted(server)).toHaveLength(1);

    refreshFilterCountsSoon(queryClient);
    await vi.advanceTimersByTimeAsync(COUNTS_FOLLOW_UP * 2);

    expect(counted(server)).toHaveLength(1);
    expect(queryClient.getQueryState(FILTER_COUNTS_KEY)?.isInvalidated).toBe(true);
  });

  it('reads a business key once, not with every refresh of the list', async () => {
    const server = backend();
    const options = { firstResult: 0, maxResults: 25, sorting: [{ sortBy: 'created' as const, sortOrder: 'desc' as const }] };

    const first = await getFilterTasks('f1', options);
    expect(first.tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p1', 'key-p2']);
    const second = await getFilterTasks('f1', options);
    expect(second.tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p1', 'key-p2']);

    const lookups = server.find('GET', `${ENGINE}/process-instance`);
    expect(lookups).toHaveLength(1);
    expect(lookups[0]?.query.get('processInstanceIds')).toBe('p1,p2');

    // a task of an instance not seen before: only that key is asked for
    server.on(`POST ${ENGINE}/filter/f1/list`, () => json({ count: 2, _embedded: { task: [halTask('t1', 'p1'), halTask('t4', 'p3')] } }));
    const third = await getFilterTasks('f1', options);
    expect(third.tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p3']);
    expect(server.find('GET', `${ENGINE}/process-instance`)[1]?.query.get('processInstanceIds')).toBe('p3');
  });

  it('remembers that a business key cannot be read, for a user without permission on the instance', async () => {
    // a task permission does not cover the process instance: the lookup answers nothing
    const server = backend({ [`GET ${ENGINE}/process-instance`]: () => json([]) });
    const options = { firstResult: 0, maxResults: 25, sorting: [{ sortBy: 'created' as const, sortOrder: 'desc' as const }] };

    const first = await getFilterTasks('f1', options);
    const second = await getFilterTasks('f1', options);

    expect(first.tasks.map((task) => task.businessKey)).toEqual([null, null, null]);
    expect(second.tasks.map((task) => task.businessKey)).toEqual([null, null, null]);
    expect(server.find('GET', `${ENGINE}/process-instance`)).toHaveLength(1);
  });

  it('asks again for business keys after a failed lookup', async () => {
    let fail = true;
    const server = backend({
      [`GET ${ENGINE}/process-instance`]: (request) =>
        fail
          ? json({ type: 'ProcessEngineException', message: 'down' }, 500)
          : json((request.query.get('processInstanceIds') ?? '').split(',').map((id) => ({ id, businessKey: `key-${id}` }))),
    });
    const options = { firstResult: 0, maxResults: 25, sorting: [{ sortBy: 'created' as const, sortOrder: 'desc' as const }] };

    expect((await getFilterTasks('f1', options)).tasks.map((task) => task.businessKey)).toEqual([null, null, null]);
    fail = false;
    expect((await getFilterTasks('f1', options)).tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p1', 'key-p2']);
    expect(server.find('GET', `${ENGINE}/process-instance`)).toHaveLength(2);
  });

  it('shows a known business key that expires while other keys are being read', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    const start = new Date('2026-03-01T10:00:00Z').getTime();
    vi.setSystemTime(start);
    const server = backend();
    const options = { firstResult: 0, maxResults: 25, sorting: [{ sortBy: 'created' as const, sortOrder: 'desc' as const }] };
    await getFilterTasks('f1', options);

    // the next list has an instance not seen before; while its key is read, the others expire
    server.on(`POST ${ENGINE}/filter/f1/list`, () => json({ count: 2, _embedded: { task: [halTask('t1', 'p1'), halTask('t4', 'p3')] } }));
    server.on(`GET ${ENGINE}/process-instance`, (request) => {
      vi.setSystemTime(start + 5 * 60_000 + 1);
      return json((request.query.get('processInstanceIds') ?? '').split(',').map((id) => ({ id, businessKey: `key-${id}` })));
    });
    const next = await getFilterTasks('f1', options);

    expect(next.tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p3']);
  });

  it('does not keep business keys answered after the session ended', async () => {
    let answer: () => void = () => undefined;
    const answered = new Promise<void>((resolve) => (answer = resolve));
    const server = backend({
      [`GET ${ENGINE}/process-instance`]: async (request) => {
        await answered;
        return json((request.query.get('processInstanceIds') ?? '').split(',').map((id) => ({ id, businessKey: `key-${id}` })));
      },
    });
    const options = { firstResult: 0, maxResults: 25, sorting: [{ sortBy: 'created' as const, sortOrder: 'desc' as const }] };

    const pending = getFilterTasks('f1', options);
    await waitFor(() => expect(server.find('GET', `${ENGINE}/process-instance`)).toHaveLength(1));
    resetBusinessKeys();
    answer();
    // the list asked for still shows the keys it read
    expect((await pending).tasks.map((task) => task.businessKey)).toEqual(['key-p1', 'key-p1', 'key-p2']);

    // but the next session asks for them anew
    await getFilterTasks('f1', options);
    expect(server.find('GET', `${ENGINE}/process-instance`)).toHaveLength(2);
  });
});
