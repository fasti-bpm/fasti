/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { TaskListItem } from '../api/types';
import { I18nProvider } from '../i18n';
import { ToastProvider } from '../ui/toast';
import { NOTIFY_INTERVAL, taskListRefresh, useNewTaskNotices } from './notifications';

const base = { interval: 30_000, minute: 60_000, refreshOff: false, urgency: false, notify: false, notifyInBackground: false };

describe('refresh of the task list', () => {
  it('follows the page without notices and does not read in the background', () => {
    expect(taskListRefresh(base)).toEqual({ refetchInterval: 30_000, refetchIntervalInBackground: false });
    // nobody at the screen: the page reads less often
    expect(taskListRefresh({ ...base, interval: 300_000 })).toEqual({ refetchInterval: 300_000, refetchIntervalInBackground: false });
  });

  it('reads a list left alone at least every minute while notices are on, also hidden if notifications are allowed', () => {
    expect(taskListRefresh({ ...base, interval: 300_000, notify: true })).toEqual({
      refetchInterval: NOTIFY_INTERVAL,
      refetchIntervalInBackground: false,
    });
    expect(taskListRefresh({ ...base, notify: true, notifyInBackground: true })).toEqual({
      refetchInterval: 30_000,
      refetchIntervalInBackground: true,
    });
    expect(taskListRefresh({ ...base, interval: 600_000, notify: true, notifyInBackground: true })).toEqual({
      refetchInterval: NOTIFY_INTERVAL,
      refetchIntervalInBackground: true,
    });
  });

  it('keeps a filter with refresh off still, except that an urgency view moves along once a minute', () => {
    expect(taskListRefresh({ ...base, refreshOff: true, notify: true, notifyInBackground: true })).toEqual({
      refetchInterval: false,
      refetchIntervalInBackground: false,
    });
    expect(taskListRefresh({ ...base, refreshOff: true, urgency: true })).toEqual({
      refetchInterval: 60_000,
      refetchIntervalInBackground: false,
    });
  });
});

describe('notices about new tasks', () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <I18nProvider initial="en">
      <ToastProvider>{children}</ToastProvider>
    </I18nProvider>
  );
  const task = (id: string): TaskListItem => ({ id, name: `Task ${id}`, created: new Date().toISOString() }) as unknown as TaskListItem;

  afterEach(() => {
    Object.defineProperty(document, 'hidden', { value: false, configurable: true });
  });

  it('shows a system notification for a task that arrives while the tab is hidden', () => {
    const shown = vi.fn();
    // a constructor with the permission of the page, as the browser has it
    const FakeNotification = Object.assign(
      function FakeNotification(title: string, options: { body: string }) {
        shown(title, options.body);
      },
      { permission: 'granted' },
    );
    vi.stubGlobal('Notification', FakeNotification);
    const { rerender } = renderHook(({ tasks }) => useNewTaskNotices('list', tasks, true), {
      wrapper,
      initialProps: { tasks: [task('a')] },
    });
    Object.defineProperty(document, 'hidden', { value: true, configurable: true });

    rerender({ tasks: [task('b'), task('a')] });

    expect(shown).toHaveBeenCalledWith('1 new tasks', 'Task b');
  });
});
