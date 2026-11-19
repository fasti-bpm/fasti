/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '../i18n';
import { fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { CaseTimeline } from './CaseTimeline';
import { caseEntries, type CaseData } from './caseData';

const ENGINE = '/engine/engine/default';

const DATA: CaseData = {
  instances: [
    { id: 'root', processDefinitionName: 'Loan', processDefinitionKey: 'loan', superProcessInstanceId: null },
    { id: 'child', processDefinitionName: 'Credit check', processDefinitionKey: 'check', superProcessInstanceId: 'root' },
  ],
  tasks: [
    {
      id: 't1',
      name: 'Review',
      taskDefinitionKey: 'review',
      processInstanceId: 'root',
      assignee: 'anna',
      startTime: '2026-03-01T09:00:00.000+0000',
      endTime: '2026-03-01T10:00:00.000+0000',
      deleteReason: 'completed',
    },
    {
      id: 't2',
      name: null,
      taskDefinitionKey: 'score',
      processInstanceId: 'child',
      assignee: 'binh',
      startTime: '2026-03-01T10:05:00.000+0000',
      endTime: '2026-03-01T11:00:00.000+0000',
      deleteReason: 'Customer withdrew',
    },
  ],
  comments: [
    { id: 'c1', userId: 'anna', time: '2026-03-01T09:30:00.000+0000', message: 'Missing payslip', taskId: 't1' },
    // the same comment, listed again for the instance
    { id: 'c1', userId: 'anna', time: '2026-03-01T09:30:00.000+0000', message: 'Missing payslip', taskId: 't1', processInstanceId: 'root' },
    { id: 'c2', userId: 'lead', time: '2026-03-01T12:00:00.000+0000', message: 'Escalated', taskId: null, processInstanceId: 'root' },
  ],
  truncated: false,
};

describe('case entries', () => {
  it('merges comments and steps of the whole call chain, newest first, each comment once', () => {
    const entries = caseEntries(DATA, 'root');
    expect(entries.map((entry) => [entry.kind, entry.taskName, entry.processName, entry.userId])).toEqual([
      ['comment', null, null, 'lead'],
      ['canceled', 'score', 'Credit check', 'binh'],
      ['created', 'score', 'Credit check', null],
      ['completed', 'Review', null, 'anna'],
      ['comment', 'Review', null, 'anna'],
      ['created', 'Review', null, null],
    ]);
    // the reason a step ended early is kept, a plain completion has none
    expect(entries[1]?.message).toBe('Customer withdrew');
    expect(entries[3]?.message).toBeNull();
  });
});

describe('case timeline', () => {
  const renderTimeline = (canComment = true) =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <I18nProvider initial="en">
          <ToastProvider>
            <CaseTimeline processInstanceId="root" currentTaskId="t1" canComment={canComment} />
          </ToastProvider>
        </I18nProvider>
      </QueryClientProvider>,
    );

  const routes = () => ({
    [`GET ${ENGINE}/history/process-instance/root`]: () => json({ id: 'root', rootProcessInstanceId: 'root' }),
    [`GET ${ENGINE}/history/process-instance`]: () => json(DATA.instances),
    [`GET ${ENGINE}/history/task`]: (request: { query: URLSearchParams }) =>
      json(DATA.tasks.filter((task) => task.processInstanceId === request.query.get('processInstanceId'))),
    [`GET ${ENGINE}/process-instance/root/comment`]: () => json([DATA.comments[1], DATA.comments[2]]),
    [`GET ${ENGINE}/process-instance/child/comment`]: () => json([]),
    [`GET ${ENGINE}/task/t1/comment`]: () => json([DATA.comments[0]]),
    [`GET ${ENGINE}/task/t2/comment`]: () => json([]),
    [`POST ${ENGINE}/process-instance/root/comment/create`]: () =>
      json({ id: 'c3', userId: 'me', time: '2026-03-01T13:00:00.000+0000', message: 'Done', taskId: null }),
  });

  it('shows who did what in which step and marks the open task', async () => {
    fakeServer(routes());
    renderTimeline();
    const list = await screen.findByRole('list', { name: 'Case history' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(6);
    expect(items[0]).toHaveTextContent('lead');
    expect(items[0]).toHaveTextContent('whole case');
    expect(items[1]).toHaveTextContent('Credit check');
    expect(items[1]).toHaveTextContent('Customer withdrew');
    expect(within(list).getAllByText('this task').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByLabelText('Comments only'));
    expect(within(screen.getByRole('list', { name: 'Case history' })).getAllByRole('listitem')).toHaveLength(2);
  });

  it('adds a comment to the whole case', async () => {
    const server = fakeServer(routes());
    renderTimeline();
    await screen.findByRole('list', { name: 'Case history' });
    await userEvent.type(screen.getByLabelText('Comment on the whole case'), 'Done');
    await userEvent.click(screen.getByRole('button', { name: 'Add comment' }));
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/process-instance/root/comment/create`)[0]?.body).toEqual({ message: 'Done' }),
    );
  });

  it('shows a comment added while the first load of the case is still running', async () => {
    // the first load reads the comments, then waits; the comment is written meanwhile
    let posted = false;
    let release: () => void = () => undefined;
    const held = new Promise<void>((resolve) => (release = resolve));
    const newComment = { id: 'c3', userId: 'me', time: '2026-03-01T13:00:00.000+0000', message: 'Fresh note', taskId: null, processInstanceId: 'root' };
    fakeServer({
      ...routes(),
      [`GET ${ENGINE}/process-instance/root/comment`]: () => json(posted ? [newComment] : []),
      [`GET ${ENGINE}/task/t1/comment`]: async () => {
        if (!posted) await held;
        return json([]);
      },
      [`POST ${ENGINE}/process-instance/root/comment/create`]: () => {
        posted = true;
        release();
        return json(newComment);
      },
    });
    renderTimeline();
    await userEvent.type(screen.getByLabelText('Comment on the whole case'), 'Fresh note');
    await userEvent.click(screen.getByRole('button', { name: 'Add comment' }));
    const list = await screen.findByRole('list', { name: 'Case history' });
    expect(await within(list).findByText('Fresh note')).toBeVisible();
  });

  it('offers no comment field for a finished case', async () => {
    fakeServer(routes());
    renderTimeline(false);
    await screen.findByRole('list', { name: 'Case history' });
    expect(screen.queryByLabelText('Comment on the whole case')).toBeNull();
  });
});
