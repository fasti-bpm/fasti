/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tooltip } from 'radix-ui';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { empty, fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { isTaskChangePending, useTaskActions } from './actions';
import { TaskDetail } from './TaskDetail';

const ENGINE = '/engine/engine/default';
const CLAIM = `${ENGINE}/task/t1/claim`;
const SUBMIT = `${ENGINE}/task/t1/submit-form`;

function Detail({ onCompleted }: { onCompleted: () => void }) {
  const actions = useTaskActions();
  return (
    <TaskDetail
      taskId="t1"
      preview={undefined}
      tab="form"
      onTab={() => undefined}
      onClose={() => undefined}
      onCompleted={onCompleted}
      actions={actions}
      completeAndNext={false}
      onCompleteAndNext={() => undefined}
    />
  );
}

function renderDetail() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const session = { userId: 'demo', displayName: 'Demo User', authorizedApps: ['tasklist'] } as unknown as React.ContextType<
    typeof SessionContext
  >;
  const completed = vi.fn();
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <I18nProvider initial="en">
          <SessionContext.Provider value={session}>
            <ToastProvider>
              <Tooltip.Provider>
                <Detail onCompleted={completed} />
              </Tooltip.Provider>
            </ToastProvider>
          </SessionContext.Provider>
        </I18nProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return { queryClient, completed };
}

/** A server with an unassigned task without a form, whose claim arrives only when the test lets it. */
function backend(claimAnswer: () => Response) {
  const state = { assignee: null as string | null };
  let arrive: () => void = () => undefined;
  const arrived = new Promise<void>((resolve) => {
    arrive = resolve;
  });
  const server = fakeServer({
    [`GET ${ENGINE}/task/t1`]: () => json({ id: 't1', name: 'Approve', assignee: state.assignee }),
    [`GET ${ENGINE}/task/t1/form`]: () => json({ key: null, contextPath: null }),
    [`GET ${ENGINE}/task/t1/form-variables`]: () => json({}),
    [`POST ${CLAIM}`]: async () => {
      await arrived;
      const answer = claimAnswer();
      if (answer.ok) {
        state.assignee = 'demo';
      }
      return answer;
    },
    [`POST ${SUBMIT}`]: () =>
      // as the engine answers a completion that runs into the claim, or for a task not claimed
      state.assignee === 'demo' ? empty() : json({ type: 'RestException', message: 'ENGINE-03005' }, 500),
  });
  return Object.assign(server, { arrive: () => arrive() });
}

const completeButton = () => screen.getByRole('button', { name: 'Complete' });
const form = () => completeButton().closest('form') as HTMLFormElement;
/** The task is shown with its form, which is not to be completed by who has not claimed it. */
const openTask = async () => expect(await screen.findByRole('button', { name: 'Complete' })).toBeDisabled();
/** The button of the task; the hint of its form offers the claim as well. */
const claimButton = () => screen.getAllByRole('button', { name: 'Claim' })[0] as HTMLElement;
const pause = () => new Promise((resolve) => setTimeout(resolve, 50));

describe('completing a task whose claim is under way', () => {
  it('does not send the form before the claim is made, and sends it after', async () => {
    const server = backend(() => empty());
    const user = userEvent.setup();
    const { queryClient, completed } = renderDetail();

    await openTask();
    await user.click(claimButton());
    // the claim is shown at once: the task can be released, its form filled in
    expect(await screen.findByRole('button', { name: 'Unclaim' })).toBeInTheDocument();
    await waitFor(() => expect(server.find('POST', CLAIM)).toHaveLength(1));
    expect(isTaskChangePending(queryClient, 't1')).toBe(true);
    expect(screen.getByRole('button', { name: 'Add variable' })).toBeEnabled();

    // but the server has not made it yet: no way to complete the task leads to a request
    expect(completeButton()).toBeDisabled();
    await user.click(completeButton());
    fireEvent.keyDown(document.body, { key: 'Enter', ctrlKey: true });
    // submitting the form itself (Enter in a field) waits for the claim
    fireEvent.submit(form());
    await pause();
    expect(server.find('POST', SUBMIT)).toHaveLength(0);

    server.arrive();
    // the waiting submit goes out now, after the claim, and only once
    await waitFor(() => expect(completed).toHaveBeenCalledTimes(1));
    expect(server.find('POST', SUBMIT)).toHaveLength(1);
    const order = server.requests.filter((request) => request.method === 'POST').map((request) => request.path);
    expect(order).toEqual([CLAIM, SUBMIT]);
    await waitFor(() => expect(isTaskChangePending(queryClient, 't1')).toBe(false));
  });

  it('enables Complete once the claim is made', async () => {
    const server = backend(() => empty());
    const user = userEvent.setup();
    const { completed } = renderDetail();

    await openTask();
    await user.click(claimButton());
    await screen.findByRole('button', { name: 'Unclaim' });
    expect(completeButton()).toBeDisabled();

    server.arrive();
    await waitFor(() => expect(completeButton()).toBeEnabled());
    await user.click(completeButton());
    await waitFor(() => expect(completed).toHaveBeenCalledTimes(1));
    expect(server.find('POST', SUBMIT)).toHaveLength(1);
  });

  it('goes back to the unclaimed task when the claim is refused, without sending the form', async () => {
    const server = backend(() => json({ type: 'RestException', message: 'Task is already claimed' }, 500));
    const user = userEvent.setup();
    const { completed } = renderDetail();

    await openTask();
    await user.click(claimButton());
    await screen.findByRole('button', { name: 'Unclaim' });
    fireEvent.submit(form());

    server.arrive();
    // the task is offered for claiming again and its form cannot be completed
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Unclaim' })).not.toBeInTheDocument());
    expect(completeButton()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Add variable' })).toBeDisabled();
    await pause();
    expect(server.find('POST', SUBMIT)).toHaveLength(0);
    expect(completed).not.toHaveBeenCalled();
  });
});
