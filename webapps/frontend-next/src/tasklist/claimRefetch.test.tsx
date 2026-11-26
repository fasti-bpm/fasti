/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { TaskPage } from '../api/types';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { empty, fakeServer } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { useTaskActions } from './actions';

const page = (assignee: string | null) =>
  ({ count: 1, tasks: [{ id: 't1', name: 'Approve', assignee }] }) as unknown as TaskPage;

describe('a claim while the list is read again', () => {
  it('is not undone by the answer of a read that started before it', async () => {
    fakeServer({ 'POST /engine/engine/default/task/t1/claim': () => empty() });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const session = { userId: 'demo', displayName: 'Demo', authorizedApps: ['tasklist'] };
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <I18nProvider initial="en">
          <SessionContext.Provider value={session}>
            <ToastProvider>{children}</ToastProvider>
          </SessionContext.Provider>
        </I18nProvider>
      </QueryClientProvider>
    );
    const { result } = renderHook(() => useTaskActions(), { wrapper });
    const key = ['tasks', 'f1', 0];
    queryClient.setQueryData(key, page(null));
    // a refresh of the list asked the server before the claim, and answers after it
    let answer: (value: TaskPage) => void = () => undefined;
    const reading = queryClient
      .fetchQuery({ queryKey: key, queryFn: () => new Promise<TaskPage>((resolve) => (answer = resolve)), staleTime: 0 })
      .catch(() => undefined);

    await act(() => result.current.claim('t1', { silent: true }));
    answer(page(null));
    await reading;

    expect(queryClient.getQueryData<TaskPage>(key)?.tasks[0]?.assignee).toBe('demo');
  });
});
