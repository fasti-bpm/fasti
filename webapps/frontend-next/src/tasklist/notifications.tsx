/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import { Bell, BellOff } from 'lucide-react';
import type { TaskListItem } from '../api/types';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { onPreferencesReplaced, readPreference, writePreference } from '../lib/storage';
import { IconButton } from '../ui';
import { useToast } from '../ui/toast';

/** outside `tasklist.`, where the entries of other users of the browser lie */
const PREFERENCE = 'tasks.notify';

function supported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window;
}

/** Allowed difference between the clocks of browser and server. */
const SKEW = 120_000;

/**
 * Tasks that were not in the list before and were created since the last look;
 * a task that only moved up from a later page is not new. Nothing is new on
 * the first look at a list.
 */
export function newTasks(
  seen: { ids: Set<string>; at: number } | null,
  current: TaskListItem[],
): TaskListItem[] {
  if (!seen) {
    return [];
  }
  return current.filter((task) => {
    const created = parseEngineDate(task.created)?.getTime() ?? 0;
    return !seen.ids.has(task.id) && created >= seen.at - SKEW;
  });
}

/**
 * Tells the user about tasks that appeared in the open filter since the last
 * refresh: a browser notification while the page is in the background (if the
 * user allowed it), a toast while it is in front.
 */
export function useNewTaskNotices(listKey: string, tasks: TaskListItem[] | undefined, enabled: boolean): void {
  const { t } = useI18n();
  const toast = useToast();
  const seen = useRef<{ key: string; ids: Set<string>; at: number } | null>(null);

  useEffect(() => {
    if (!tasks) {
      return;
    }
    const previous = seen.current?.key === listKey ? seen.current : null;
    const fresh = newTasks(previous, tasks);
    seen.current = {
      key: listKey,
      ids: new Set([...(previous?.ids ?? []), ...tasks.map((task) => task.id)]),
      at: Date.now(),
    };
    if (!enabled || fresh.length === 0) {
      return;
    }
    const first = fresh[0];
    const title = t('notify.newTasks', { count: fresh.length });
    const body = first?.name ?? '';
    if (document.hidden && supported() && Notification.permission === 'granted') {
      try {
        new Notification(title, { body, tag: 'fasti-new-tasks' });
      } catch {
        // some browsers only allow notifications from a service worker
      }
    } else {
      toast.show({ title, detail: body || undefined });
    }
  }, [listKey, tasks, enabled, t, toast]);
}

/** Whether the browser shows notifications of this page. */
export function notificationsAllowed(): boolean {
  return supported() && Notification.permission === 'granted';
}

/**
 * The switch for notices about new tasks; turning it on asks the browser for permission. The
 * third value says whether system notifications are allowed, so that the list is also read while
 * the tab is in the background.
 */
export function useNotifyPreference(): [boolean, () => void, boolean] {
  const [on, setOn] = useState(() => readPreference<boolean>(PREFERENCE, false));
  // the copy of the server may arrive after the first look
  useEffect(() => onPreferencesReplaced(() => setOn(readPreference<boolean>(PREFERENCE, false))), []);
  const [allowed, setAllowed] = useState(notificationsAllowed);
  const toggle = () => {
    const next = !on;
    setOn(next);
    writePreference(PREFERENCE, next);
    if (next && supported() && Notification.permission === 'default') {
      void Notification.requestPermission().then(
        () => setAllowed(notificationsAllowed()),
        () => undefined,
      );
    }
  };
  return [on, toggle, on && allowed];
}

/** With notices on, a new task is reported within this time, also by a list left alone. */
export const NOTIFY_INTERVAL = 60_000;

/**
 * How often the task list is read again by itself, and whether also while the tab is hidden.
 *
 * - `interval` is the refresh of the page, already stretched while nobody is at the screen;
 *   `minute` the same for once a minute.
 * - A filter with refresh off is not read again, except for an urgency view (overdue, due
 *   soon): it compares with now, and moves along once a minute as it always did.
 * - With notices on, a list left alone is still read at least every minute, else a new task
 *   would be reported minutes late; with system notifications allowed also while the tab is
 *   hidden, which is when they are shown.
 */
export function taskListRefresh(options: {
  interval: number | false;
  minute: number | false;
  refreshOff: boolean;
  urgency: boolean;
  notify: boolean;
  notifyInBackground: boolean;
}): { refetchInterval: number | false; refetchIntervalInBackground: boolean } {
  if (options.refreshOff) {
    return { refetchInterval: options.urgency ? options.minute : false, refetchIntervalInBackground: false };
  }
  const interval = options.interval;
  if (!options.notify || interval === false) {
    return { refetchInterval: interval, refetchIntervalInBackground: false };
  }
  return {
    refetchInterval: Math.min(interval, NOTIFY_INTERVAL),
    refetchIntervalInBackground: options.notifyInBackground,
  };
}

export function NotifyToggle({ on, onToggle }: { on: boolean; onToggle: () => void }) {
  const { t } = useI18n();
  return (
    <IconButton
      icon={on ? Bell : BellOff}
      label={on ? t('notify.on') : t('notify.off')}
      aria-pressed={on}
      onClick={onToggle}
    />
  );
}
