/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { getTask } from '../api/engine';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { empty, fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { refreshTasks, useTaskActions } from './actions';
import { useFourEyes } from './FourEyesNotice';

const ENGINE = '/engine/engine/default';

/** The task, its four-eyes status and a way to take the task over, as the task detail has them. */
function Probe() {
  const task = useQuery({ queryKey: ['task', 't1'], queryFn: () => getTask('t1') });
  const fourEyes = useFourEyes(task.data);
  const actions = useTaskActions();
  return (
    <>
      <p data-testid="assignee">{task.data?.assignee ?? '-'}</p>
      <p data-testid="status">
        {fourEyes.data ? `${fourEyes.data.assignee}:${fourEyes.data.blocked ? 'blocked' : 'free'}` : 'unknown'}
      </p>
      <button type="button" onClick={() => void actions.assign('t1', 'demo')}>
        Take over
      </button>
    </>
  );
}

function renderProbe() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const session = { userId: 'demo', displayName: 'Demo User', authorizedApps: ['tasklist'] } as unknown as React.ContextType<
    typeof SessionContext
  >;
  render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider initial="en">
        <SessionContext.Provider value={session}>
          <ToastProvider>
            <Probe />
          </ToastProvider>
        </SessionContext.Provider>
      </I18nProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

/** A server whose four-eyes answer goes by the stored assignee: mary completed the checked step. */
function backend() {
  const state = { assignee: 'mary' as string | null };
  const server = fakeServer({
    [`GET ${ENGINE}/task/t1`]: () => json({ id: 't1', name: 'Approve', assignee: state.assignee, processInstanceId: 'p1' }),
    [`GET ${ENGINE}/task/t1/four-eyes`]: () =>
      json({
        taskId: 't1',
        enabled: true,
        guarded: true,
        earlierSteps: [{ id: 'prepare', name: 'Prepare' }],
        userId: 'demo',
        userExcluded: false,
        userSteps: [],
        assignee: state.assignee,
        assigneeExcluded: state.assignee === 'mary',
        assigneeSteps: state.assignee === 'mary' ? ['prepare'] : [],
        blocked: state.assignee === 'mary',
      }),
  });
  return Object.assign(server, { state });
}

describe('four-eyes status after an assignment', () => {
  it('does not keep the status of the former assignee read while the assignment was under way', async () => {
    const server = backend();
    // the assignment reaches the server only when the test lets it
    let arrive: () => void = () => undefined;
    const arrived = new Promise<void>((resolve) => {
      arrive = resolve;
    });
    server.on(`POST ${ENGINE}/task/t1/assignee`, async () => {
      await arrived;
      server.state.assignee = 'demo';
      return empty();
    });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('mary:blocked'));

    await userEvent.click(screen.getByRole('button', { name: 'Take over' }));
    // the new assignee is shown at once, and the status is read for it before the server knows
    await waitFor(() => expect(screen.getByTestId('assignee')).toHaveTextContent('demo'));
    await waitFor(() => expect(server.find('GET', `${ENGINE}/task/t1/four-eyes`)).toHaveLength(2));
    expect(server.find('POST', `${ENGINE}/task/t1/assignee`)).toHaveLength(1);
    // that answer is about mary, so it says nothing about the task of demo
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('unknown'));

    arrive();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('demo:free'));
    expect(server.find('GET', `${ENGINE}/task/t1/four-eyes`).length).toBeGreaterThanOrEqual(3);
  });

  it('does not take an answer that was asked for before the assignment and arrives after it', async () => {
    const server = backend();
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('mary:blocked'));

    // the status read for the new assignee is answered for mary, but only after the assignment
    let answer: () => void = () => undefined;
    const held = new Promise<void>((resolve) => {
      answer = resolve;
    });
    const fourEyes = (assignee: string) =>
      json({
        taskId: 't1',
        enabled: true,
        guarded: true,
        earlierSteps: [{ id: 'prepare', name: 'Prepare' }],
        userId: 'demo',
        userExcluded: false,
        userSteps: [],
        assignee,
        assigneeExcluded: assignee === 'mary',
        assigneeSteps: assignee === 'mary' ? ['prepare'] : [],
        blocked: assignee === 'mary',
      });
    let asked = 0;
    server.on(`GET ${ENGINE}/task/t1/four-eyes`, async () => {
      asked += 1;
      if (asked === 1) {
        await held;
        return fourEyes('mary');
      }
      return fourEyes('demo');
    });
    let arrive: () => void = () => undefined;
    const arrived = new Promise<void>((resolve) => {
      arrive = resolve;
    });
    server.on(`POST ${ENGINE}/task/t1/assignee`, async () => {
      await arrived;
      server.state.assignee = 'demo';
      return empty();
    });
    await userEvent.click(screen.getByRole('button', { name: 'Take over' }));
    await waitFor(() => expect(asked).toBe(1));
    arrive();
    // the read under way is given up for one that asks the server after the change
    await waitFor(() => expect(asked).toBe(2));
    answer();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('demo:free'));
  });

  it('reads the status again with the tasks, e.g. after a step of a parallel branch completed', async () => {
    const server = backend();
    const queryClient = renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('mary:blocked'));

    refreshTasks(queryClient);
    await waitFor(() => expect(server.find('GET', `${ENGINE}/task/t1/four-eyes`)).toHaveLength(2));
    refreshTasks(queryClient, 't1');
    await waitFor(() => expect(server.find('GET', `${ENGINE}/task/t1/four-eyes`)).toHaveLength(3));
  });
});
