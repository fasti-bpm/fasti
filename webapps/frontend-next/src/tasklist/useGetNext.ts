/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { claimTask, getFilterTasks, getTask, queryTasks } from '../api/engine';
import type { Task } from '../api/types';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { useToast } from '../ui/toast';
import { refreshTasks } from './actions';
import { BATCH, MAX_PAGES, WIDE_BATCH, candidateQueries, claimOnce, getNextWork, isOverdue } from './getNext';

/**
 * Whether a run is under way anywhere: the Tasklist may be left and opened
 * again while one runs, and the new page must not start a second one.
 */
let inFlight = false;

/** Ends a run whose page was left before it claimed anything. */
class Stopped extends Error {}

/** What the last run found, shown and announced under the list. */
export interface NextWorkStatus {
  tone: 'info' | 'warning';
  text: string;
  /** a visible notice; a success is only announced, the opened task shows it */
  visible: boolean;
}

export interface NextWork {
  run: () => Promise<void>;
  running: boolean;
  status: NextWorkStatus | null;
  dismiss: () => void;
}

/**
 * Picks, claims and opens the next task for the user. One instance serves
 * the button, the shortcut, the command palette and "complete and get next",
 * so that two of them can never claim at the same time.
 */
export function useGetNext(
  scope: { filterId: string; name: string } | null,
  open: (taskId: string) => void): NextWork {
  const { t } = useI18n();
  const toast = useToast();
  const session = useSession();
  const queryClient = useQueryClient();
  const busy = useRef(false);
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState<NextWorkStatus | null>(null);
  // an answer that arrives after the user left the page opens nothing
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    if (busy.current || inFlight) return;
    busy.current = true;
    inFlight = true;
    setRunning(true);
    setStatus(null);
    const userId = session.userId;
    const read = async (): Promise<Task[]> => {
      const now = new Date();
      const lists = await Promise.all(
        candidateQueries(userId, now).map(async ({ query, sorting }) => {
          if (!scope) return queryTasks(query, { maxResults: BATCH, sorting });
          // only the tasks of the filter: the candidates of the user, best first, are looked up in
          // the filter by id, so that its own criteria (due dates, groups, ...) stay as they are
          const found: Task[] = [];
          for (let page = 0; page < MAX_PAGES && found.length < BATCH; page += 1) {
            const offered = await queryTasks(query, { firstResult: page * WIDE_BATCH, maxResults: WIDE_BATCH, sorting });
            if (offered.length > 0) {
              const listed = await getFilterTasks(scope.filterId, {
                firstResult: 0,
                maxResults: offered.length,
                sorting,
                query: { taskIdIn: offered.map((task) => task.id) },
              });
              const inFilter = new Set(listed.tasks.map((task) => task.id));
              found.push(...offered.filter((task) => inFilter.has(task.id)));
            }
            if (offered.length < WIDE_BATCH) break;
          }
          return found;
        }),
      );
      return lists.flat();
    };
    try {
      const outcome = await getNextWork(read, (taskId) => {
        // a page that was left claims nothing more
        if (!mounted.current) throw new Stopped();
        return claimOnce(
          taskId,
          userId,
          (id) => claimTask(id, userId),
          async (id) => (await getTask(id)).assignee,
        );
      });
      if (!mounted.current) return;
      if (outcome.kind === 'claimed') {
        const name = outcome.task.name ?? outcome.task.id;
        const text = [
          isOverdue(outcome.task) ? t('getNext.claimedOverdue', { name }) : t('getNext.claimed', { name }),
          outcome.forbidden > 0 ? t('getNext.skippedForbidden', { count: outcome.forbidden }) : '',
        ]
          .filter(Boolean)
          .join(' ');
        setStatus({ tone: outcome.forbidden > 0 ? 'warning' : 'info', text, visible: outcome.forbidden > 0 });
        refreshTasks(queryClient, outcome.task.id);
        open(outcome.task.id);
      } else if (outcome.kind === 'empty') {
        setStatus({
          tone: 'info',
          text: scope ? t('getNext.emptyFilter', { filter: scope.name }) : t('getNext.empty'),
          visible: true,
        });
      } else {
        refreshTasks(queryClient);
        setStatus({
          tone: 'warning',
          text:
            outcome.forbidden === outcome.tried
              ? t('getNext.forbidden')
              : outcome.failed === outcome.tried
                ? t('getNext.unavailable')
                : t('getNext.missed'),
          visible: true,
        });
      }
    } catch (error) {
      if (mounted.current && !(error instanceof Stopped)) toast.error(error, t('getNext.failed'));
    } finally {
      busy.current = false;
      inFlight = false;
      if (mounted.current) setRunning(false);
    }
  }, [scope, session.userId, open, queryClient, t, toast]);

  const dismiss = useCallback(() => setStatus(null), []);
  return { run, running, status, dismiss };
}
