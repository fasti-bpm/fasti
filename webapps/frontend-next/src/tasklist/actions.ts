/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useMemo, useSyncExternalStore } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Query, QueryClient } from '@tanstack/react-query';
import * as api from '../api/engine';
import type { Task, TaskPage } from '../api/types';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { useToast } from '../ui/toast';

/** Shows a change in every cached copy of a task before the server confirmed it. */
function patchTask(queryClient: QueryClient, taskId: string, patch: Partial<Task>): void {
  queryClient.setQueryData<Task>(['task', taskId], (task) => (task ? { ...task, ...patch } : task));
  queryClient.setQueriesData<TaskPage>({ queryKey: ['tasks'] }, (page) =>
    page
      ? {
          ...page,
          tasks: page.tasks.map((task) =>
            task.id === taskId
              ? {
                  ...task,
                  ...patch,
                  assigneeName: 'assignee' in patch ? undefined : task.assigneeName,
                }
              : task,
          ),
        }
      : page,
  );
}

/** The task as it is shown: the open one, or else its copy in a list. */
function shownTask(queryClient: QueryClient, taskId: string): Task | undefined {
  const open = queryClient.getQueryData<Task>(['task', taskId]);
  if (open) {
    return open;
  }
  for (const [, page] of queryClient.getQueriesData<TaskPage>({ queryKey: ['tasks'] })) {
    const listed = page?.tasks.find((task) => task.id === taskId);
    if (listed) {
      return listed;
    }
  }
  return undefined;
}

/** What the task shows for the properties of a patch, to go back to if the change is refused. */
function shownValues(queryClient: QueryClient, taskId: string, patch: Partial<Task>): Partial<Task> | null {
  const task = shownTask(queryClient, taskId);
  if (!task) {
    return null;
  }
  const values: Record<string, unknown> = {};
  for (const property of Object.keys(patch)) {
    values[property] = (task as unknown as Record<string, unknown>)[property];
  }
  return values as Partial<Task>;
}

/**
 * The changes of tasks that are on their way to the server, by query client. A change is shown
 * before the server confirmed it, but the task itself is not what it shows until then: completing
 * it meanwhile would run into the change (an optimistic locking failure of the engine) or be
 * refused for a task that is not claimed yet. So what completes a task waits for these.
 */
interface PendingChanges {
  /** per task: the changes under way, each answering whether it was made */
  tasks: Map<string, Set<Promise<boolean>>>;
  listeners: Set<() => void>;
}

const pendingChanges = new WeakMap<QueryClient, PendingChanges>();

function pendingOf(queryClient: QueryClient): PendingChanges {
  let pending = pendingChanges.get(queryClient);
  if (!pending) {
    pending = { tasks: new Map(), listeners: new Set() };
    pendingChanges.set(queryClient, pending);
  }
  return pending;
}

/** Keeps a change of a task as under way until it is settled. */
function trackChange(queryClient: QueryClient, taskId: string, change: Promise<boolean>): Promise<boolean> {
  const pending = pendingOf(queryClient);
  const notify = () => pending.listeners.forEach((listener) => listener());
  let changes = pending.tasks.get(taskId);
  if (!changes) {
    changes = new Set();
    pending.tasks.set(taskId, changes);
  }
  changes.add(change);
  notify();
  const settle = () => {
    const current = pending.tasks.get(taskId);
    current?.delete(change);
    if (current?.size === 0) {
      pending.tasks.delete(taskId);
    }
    notify();
  };
  change.then(settle, settle);
  return change;
}

/** Whether a change of the task (claim, unclaim, assignment, ...) is on its way to the server. */
export function isTaskChangePending(queryClient: QueryClient, taskId: string): boolean {
  return (pendingChanges.get(queryClient)?.tasks.get(taskId)?.size ?? 0) > 0;
}

/**
 * Waits for the changes of the task that are under way, those started meanwhile included.
 * Answers whether all of them were made: after a refused one the task is not what was shown.
 */
export async function taskChangesSettled(queryClient: QueryClient, taskId: string): Promise<boolean> {
  let made = true;
  for (;;) {
    const changes = pendingChanges.get(queryClient)?.tasks.get(taskId);
    if (!changes || changes.size === 0) {
      return made;
    }
    const results = await Promise.all([...changes].map((change) => change.catch(() => false)));
    made = made && results.every(Boolean);
  }
}

/** Whether a change of the task is under way, kept up to date for a component. */
export function useTaskChangePending(taskId: string): boolean {
  const queryClient = useQueryClient();
  const subscribe = useCallback(
    (listener: () => void) => {
      const pending = pendingOf(queryClient);
      pending.listeners.add(listener);
      return () => {
        pending.listeners.delete(listener);
      };
    },
    [queryClient],
  );
  return useSyncExternalStore(subscribe, () => isTaskChangePending(queryClient, taskId));
}

/**
 * The tasks known to be completed, by query client. A completed task is gone for good (its id is
 * not given again), and what is read per task answers with an error for it: a local variable of
 * a task that no longer exists is an HTTP 500 of the REST API. So nothing is read for these.
 */
const completedTasks = new WeakMap<QueryClient, Set<string>>();

/** What is read for one task and its assignee; the task id is the second part of the key. */
const PER_TASK_QUERIES = ['four-eyes', 'substitution-mark'] as const;

/** Whether the task was completed in this session, so that nothing is to be read for it any more. */
export function isTaskCompleted(queryClient: QueryClient, taskId: string | null | undefined): boolean {
  return !!taskId && (completedTasks.get(queryClient)?.has(taskId) ?? false);
}

/** Gives up what is being read for the completed tasks and keeps it from being read again. */
function forgetCompleted(queryClient: QueryClient, taskIds: string[]): void {
  let known = completedTasks.get(queryClient);
  if (!known) {
    known = new Set();
    completedTasks.set(queryClient, known);
  }
  for (const taskId of taskIds) {
    known.add(taskId);
    for (const name of PER_TASK_QUERIES) {
      // a read under way would reach the server after the task is gone
      void queryClient.cancelQueries({ queryKey: [name, taskId] });
    }
  }
}

/**
 * Reads again what was read for an assignee. A read still under way may have asked the server
 * before the change and would be handed back as the new answer (a first read is not started
 * anew by an invalidation), so it is given up first. What belongs to a completed task is left out.
 */
function readAgain(queryClient: QueryClient, queryKey: unknown[]): void {
  const predicate = (query: Query): boolean => !isTaskCompleted(queryClient, query.queryKey[1] as string | undefined);
  void queryClient
    .cancelQueries({ queryKey, predicate })
    .then(() => queryClient.invalidateQueries({ queryKey, predicate }));
}

export interface RefreshOptions {
  /** the tasks completed by the change: they are gone, so nothing is read for them again */
  completed?: string[];
}

export function refreshTasks(queryClient: QueryClient, taskId?: string, options?: RefreshOptions): void {
  if (options?.completed?.length) {
    forgetCompleted(queryClient, options.completed);
  }
  // a change of several tasks may concern the open one as well
  void queryClient.invalidateQueries({ queryKey: taskId ? ['task', taskId] : ['task'] });
  void queryClient.invalidateQueries({ queryKey: ['tasks'] });
  // the variables shown on the cards change with the tasks, not with their key
  void queryClient.invalidateQueries({ queryKey: ['task-card-variables'] });
  void queryClient.invalidateQueries({ queryKey: ['filters'] });
  // who may complete a task depends on its assignee and on the steps completed meanwhile
  readAgain(queryClient, taskId ? ['four-eyes', taskId] : ['four-eyes']);
  // a task assigned anew is no longer held for an absent user
  void queryClient.invalidateQueries({ queryKey: ['substitution-marks'] });
  readAgain(queryClient, taskId ? ['substitution-mark', taskId] : ['substitution-mark']);
}

export interface TaskActions {
  claim: (taskId: string, options?: { silent?: boolean }) => Promise<void>;
  unclaim: (taskId: string, options?: { silent?: boolean }) => Promise<void>;
  assign: (taskId: string, userId: string | null) => Promise<void>;
  delegate: (taskId: string, userId: string) => Promise<void>;
  setDate: (taskId: string, property: 'due' | 'followUp', value: string | null) => Promise<void>;
  addGroup: (taskId: string, groupId: string) => Promise<void>;
  removeGroup: (taskId: string, groupId: string) => Promise<void>;
  comment: (taskId: string, message: string) => Promise<boolean>;
}

export function useTaskActions(): TaskActions {
  const queryClient = useQueryClient();
  const session = useSession();
  const toast = useToast();
  const { t } = useI18n();

  return useMemo<TaskActions>(() => {
    const change = (
      taskId: string,
      patch: Partial<Task>,
      call: () => Promise<unknown>,
      success: Parameters<typeof toast.show>[0] | null,
      failure: string,
    ): Promise<void> => {
      const run = async (): Promise<boolean> => {
        await queryClient.cancelQueries({ queryKey: ['task', taskId] });
        const before = shownValues(queryClient, taskId, patch);
        patchTask(queryClient, taskId, patch);
        try {
          await call();
          if (success) {
            toast.show(success);
          }
          return true;
        } catch (error) {
          // back to what was shown before: the task is read again, but until then it must not
          // pass for changed. A later change of the task is shown by now and stays.
          if (before && pendingOf(queryClient).tasks.get(taskId)?.size === 1) {
            patchTask(queryClient, taskId, before);
          }
          toast.error(error, failure);
          return false;
        } finally {
          refreshTasks(queryClient, taskId);
        }
      };
      // under way from here on, before anything is shown
      return trackChange(queryClient, taskId, run()).then(() => undefined);
    };

    const actions: TaskActions = {
      claim: (taskId, options) =>
        change(
          taskId,
          { assignee: session.userId },
          () => api.claimTask(taskId, session.userId),
          options?.silent
            ? null
            : {
                title: t('task.claimed'),
                action: {
                  label: t('action.undo'),
                  run: () => void actions.unclaim(taskId, { silent: true }),
                },
              },
          t('task.claimFailed'),
        ),
      unclaim: (taskId, options) =>
        change(
          taskId,
          { assignee: null },
          () => api.unclaimTask(taskId),
          options?.silent
            ? null
            : {
                title: t('task.unclaimed'),
                action: {
                  label: t('action.undo'),
                  run: () => void actions.claim(taskId, { silent: true }),
                },
              },
          t('task.unclaimFailed'),
        ),
      assign: (taskId, userId) =>
        change(
          taskId,
          { assignee: userId },
          () => api.setTaskAssignee(taskId, userId),
          { title: userId ? t('task.assigned', { user: userId }) : t('task.assigneeRemoved') },
          t('task.assignFailed'),
        ),
      delegate: (taskId, userId) =>
        change(
          taskId,
          { assignee: userId },
          () => api.delegateTask(taskId, userId),
          { title: t('task.delegated', { user: userId }) },
          t('task.delegateFailed'),
        ),
      setDate: (taskId, property, value) =>
        change(
          taskId,
          { [property]: value },
          () => api.updateTask(taskId, { [property]: value }),
          { title: t('task.dateSaved') },
          t('task.dateFailed'),
        ),
      addGroup: async (taskId, groupId) => {
        try {
          await api.addCandidateGroup(taskId, groupId);
        } catch (error) {
          toast.error(error, t('task.groupsFailed'));
        } finally {
          void queryClient.invalidateQueries({ queryKey: ['task-groups', taskId] });
        }
      },
      removeGroup: async (taskId, groupId) => {
        try {
          await api.removeCandidateGroup(taskId, groupId);
        } catch (error) {
          toast.error(error, t('task.groupsFailed'));
        } finally {
          void queryClient.invalidateQueries({ queryKey: ['task-groups', taskId] });
        }
      },
      comment: async (taskId, message) => {
        try {
          await api.addTaskComment(taskId, message);
          return true;
        } catch (error) {
          toast.error(error, t('comment.failed'));
          return false;
        } finally {
          // a load still running (e.g. the first one) would otherwise be handed back without the comment
          await queryClient.cancelQueries({ queryKey: ['task-history', taskId] });
          await queryClient.cancelQueries({ queryKey: ['case'] });
          void queryClient.invalidateQueries({ queryKey: ['task-history', taskId] });
          void queryClient.invalidateQueries({ queryKey: ['case'] });
        }
      },
    };
    return actions;
  }, [queryClient, session.userId, toast, t]);
}
