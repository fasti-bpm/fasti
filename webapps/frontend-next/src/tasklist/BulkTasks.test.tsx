/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tooltip } from 'radix-ui';
import { describe, expect, it, vi } from 'vitest';
import type { TaskListItem } from '../api/types';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { ToastProvider } from '../ui/toast';
import { BulkTaskBar, tasksFor } from './BulkTasks';
import { refreshTasks } from './actions';

const assignTasks = vi.fn<(ids: string[], change: unknown) => Promise<string[]>>();
vi.mock('../api/operations', () => ({
  assignTasks: (ids: string[], change: unknown) => assignTasks(ids, change),
}));

function task(id: string, assignee: string | null): TaskListItem {
  return { id, name: `Task ${id}`, assignee, variables: [] } as unknown as TaskListItem;
}

const TASKS = [task('a', null), task('b', 'mary'), task('c', null)];

function renderBar(onClear = vi.fn(), onKeep?: (ids: string[]) => void) {
  const session = { userId: 'demo' } as unknown as React.ContextType<typeof SessionContext>;
  render(
    <QueryClientProvider client={new QueryClient()}>
      <I18nProvider initial="en">
        <SessionContext.Provider value={session}>
          <ToastProvider>
            <Tooltip.Provider>
              <BulkTaskBar tasks={TASKS} onClear={onClear} onKeep={onKeep} />
            </Tooltip.Provider>
          </ToastProvider>
        </SessionContext.Provider>
      </I18nProvider>
    </QueryClientProvider>,
  );
  return onClear;
}

describe('bulk task changes', () => {
  it('picks the tasks each change applies to', () => {
    expect(tasksFor('claim', TASKS).map((t) => t.id)).toEqual(['a', 'c']);
    expect(tasksFor('unclaim', TASKS).map((t) => t.id)).toEqual(['b']);
    expect(tasksFor('assign', TASKS)).toHaveLength(3);
  });

  it('claims only the free tasks after a confirmation with the count', async () => {
    assignTasks.mockResolvedValueOnce([]);
    const onClear = renderBar();
    expect(screen.getByText('3 selected')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Claim' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Claim 2 unassigned tasks. 1 tasks already have an assignee');
    // the tasks it changes are named
    expect(within(dialog).getByRole('list')).toHaveTextContent('Task aTask c');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Claim' }));
    await waitFor(() => expect(assignTasks).toHaveBeenCalledWith(['a', 'c'], { kind: 'claim', userId: 'demo' }));
    await waitFor(() => expect(onClear).toHaveBeenCalled());
  });

  it('assigns all tasks to a user and reports tasks that failed', async () => {
    assignTasks.mockResolvedValueOnce(['b']);
    const onKeep = vi.fn();
    const onClear = renderBar(vi.fn(), onKeep);
    await userEvent.click(screen.getByRole('button', { name: 'Assign' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: 'Assign' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('User'), 'john');
    await userEvent.click(confirm);
    await waitFor(() => expect(assignTasks).toHaveBeenCalledWith(['a', 'b', 'c'], { kind: 'assign', userId: 'john' }));
    expect(await screen.findByText(/2 tasks changed, 1 could not be changed/)).toBeInTheDocument();
    // the failed task stays selected and is named
    expect(onKeep).toHaveBeenCalledWith(['b']);
    expect(onClear).not.toHaveBeenCalled();
    expect(screen.getByText(/Not changed: Task b/)).toBeVisible();
  });
});

describe('refreshing tasks', () => {
  it('reads the open tasks and the card variables again after a change of several tasks', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    refreshTasks(client);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['task'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['task-card-variables'] });
    // and what goes by the assignee: the four-eyes status and the absent users tasks are held for
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['substitution-marks'] });
    // without what belongs to a completed task, hence the predicate
    await waitFor(() => expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['four-eyes'] })));
    refreshTasks(client, 't1');
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['four-eyes', 't1'] })),
    );
    expect(invalidate).toHaveBeenCalledWith(expect.objectContaining({ queryKey: ['substitution-mark', 't1'] }));
  });
});
