/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { SubstituteNotice } from '../absence/SubstituteNotice';
import { getTask } from '../api/engine';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { empty, fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { isTaskCompleted, refreshTasks, useTaskActions } from './actions';
import { useFourEyes } from './FourEyesNotice';

const ENGINE = '/engine/engine/default';
const FOUR_EYES = `${ENGINE}/task/t1/four-eyes`;
const MARK = `${ENGINE}/task/t1/localVariables/fasti_absentAssignee`;

/** What the task detail reads per task: the task, its four-eyes status and whom it is held for. */
function Probe() {
  const task = useQuery({ queryKey: ['task', 't1'], queryFn: () => getTask('t1'), retry: false });
  const fourEyes = useFourEyes(task.data);
  const actions = useTaskActions();
  return (
    <>
      <p data-testid="assignee">{task.data?.assignee ?? '-'}</p>
      <p data-testid="status">{fourEyes.data ? `${fourEyes.data.assignee}` : 'unknown'}</p>
      {task.data && <SubstituteNotice task={task.data} />}
      <button type="button" onClick={() => void actions.assign('t1', 'mary')}>
        Hand on
      </button>
    </>
  );
}

function renderProbe() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const session = { userId: 'demo', displayName: 'Demo User', authorizedApps: ['tasklist'] } as unknown as React.ContextType<
    typeof SessionContext
  >;
  const tree = (
    <QueryClientProvider client={queryClient}>
      <I18nProvider initial="en">
        <SessionContext.Provider value={session}>
          <ToastProvider>
            <Probe />
          </ToastProvider>
        </SessionContext.Provider>
      </I18nProvider>
    </QueryClientProvider>
  );
  const view = render(tree);
  return { queryClient, again: () => view.rerender(tree) };
}

/** A server that answers for a task that is gone as the engine does: its variable is an HTTP 500. */
function backend() {
  const state = { assignee: 'demo' as string | null, completed: false };
  const gone = (status: number) => json({ type: 'RestException', message: "task t1 doesn't exist" }, status);
  const server = fakeServer({
    [`GET ${ENGINE}/user/demo/absence`]: () => json({ userId: 'demo', enabled: true, status: 'none' }),
    [`GET ${ENGINE}/user/anna/profile`]: () => json({ id: 'anna', firstName: 'Anna', lastName: 'Absent' }),
    [`GET ${ENGINE}/task/t1`]: () =>
      state.completed
        ? gone(404)
        : json({ id: 't1', name: 'Approve', assignee: state.assignee, processInstanceId: 'p1' }),
    [`GET ${FOUR_EYES}`]: () =>
      state.completed
        ? gone(404)
        : json({
            taskId: 't1',
            enabled: true,
            guarded: true,
            earlierSteps: [],
            userId: 'demo',
            userExcluded: false,
            userSteps: [],
            assignee: state.assignee,
            assigneeExcluded: false,
            assigneeSteps: [],
            blocked: false,
          }),
    [`GET ${MARK}`]: () => (state.completed ? gone(500) : json({ type: 'String', value: 'anna' })),
    [`POST ${ENGINE}/task/t1/assignee`]: (request) => {
      state.assignee = (request.body as { userId: string }).userId;
      return empty();
    },
  });
  return Object.assign(server, { state });
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 50));

describe('what is read per task', () => {
  it('is not read again for a task that was completed', async () => {
    const server = backend();
    const { queryClient, again } = renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('demo'));
    expect(await screen.findByText(/as substitute for Anna Absent/)).toBeVisible();
    expect(server.find('GET', FOUR_EYES)).toHaveLength(1);
    expect(server.find('GET', MARK)).toHaveLength(1);

    // the form was submitted: the task is gone, while its detail is still shown
    server.state.completed = true;
    refreshTasks(queryClient, undefined, { completed: ['t1'] });
    expect(isTaskCompleted(queryClient, 't1')).toBe(true);
    // the tasks are read again, so the refresh is through
    await waitFor(() => expect(server.find('GET', `${ENGINE}/task/t1`)).toHaveLength(2));
    await settle();
    expect(server.find('GET', FOUR_EYES)).toHaveLength(1);
    expect(server.find('GET', MARK)).toHaveLength(1);

    // nor by a later refresh of all tasks or of this one, or when the detail is drawn again
    refreshTasks(queryClient);
    refreshTasks(queryClient, 't1');
    again();
    await queryClient.refetchQueries({ queryKey: ['four-eyes'] });
    await queryClient.refetchQueries({ queryKey: ['substitution-mark'] });
    await settle();
    expect(server.find('GET', FOUR_EYES)).toHaveLength(1);
    expect(server.find('GET', MARK)).toHaveLength(1);
  });

  it('gives up a read under way for the completed task', async () => {
    const server = backend();
    let aborted = false;
    server.on(`GET ${MARK}`, () => new Promise<Response>(() => undefined));
    const { queryClient } = renderProbe();
    await waitFor(() => expect(server.find('GET', MARK)).toHaveLength(1));
    const query = queryClient.getQueryCache().find({ queryKey: ['substitution-mark', 't1', 'demo'] });
    expect(query?.state.fetchStatus).toBe('fetching');
    queryClient.getQueryCache().subscribe((event) => {
      if (event.query === query && event.query.state.fetchStatus === 'idle') aborted = true;
    });

    server.state.completed = true;
    refreshTasks(queryClient, undefined, { completed: ['t1'] });
    await waitFor(() => expect(aborted).toBe(true));
    await settle();
    expect(server.find('GET', MARK)).toHaveLength(1);
  });

  it('is still read again for an open task that is assigned anew', async () => {
    const server = backend();
    const { queryClient } = renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('demo'));
    await waitFor(() => expect(server.find('GET', MARK)).toHaveLength(1));
    // another task was completed meanwhile
    refreshTasks(queryClient, undefined, { completed: ['t0'] });
    await waitFor(() => expect(server.find('GET', FOUR_EYES)).toHaveLength(2));
    await waitFor(() => expect(server.find('GET', MARK)).toHaveLength(2));

    await userEvent.click(screen.getByRole('button', { name: 'Hand on' }));
    await waitFor(() => expect(screen.getByTestId('assignee')).toHaveTextContent('mary'));
    // the status and the mark are read for the new assignee, after the assignment reached the server
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('mary'));
    expect(server.find('GET', FOUR_EYES).length).toBeGreaterThanOrEqual(3);
    await waitFor(() => expect(server.find('GET', MARK).length).toBeGreaterThanOrEqual(3));
    expect(isTaskCompleted(queryClient, 't1')).toBe(false);
  });
});
