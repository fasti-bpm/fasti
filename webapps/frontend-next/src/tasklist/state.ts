/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import type { TaskSorting } from '../api/types';
import type { QueueView } from './queue';
import { readPreference, writePreference } from '../lib/storage';
import type { Criterion } from './search';
import type { SavedSearch } from './searchLinks';

export const PAGE_SIZE = 25;

export type TaskTab = string;

/** What is open in the Tasklist; kept in the URL so that it can be shared. */
export interface TasklistLocation {
  filterId: string | null;
  taskId: string | null;
  tab: TaskTab;
  page: number;
}

export function useTasklistLocation(): [
  TasklistLocation,
  (changes: Partial<TasklistLocation>, options?: { replace?: boolean }) => void,
] {
  const [params, setParams] = useSearchParams();
  const location: TasklistLocation = {
    filterId: params.get('filter'),
    taskId: params.get('task'),
    tab: params.get('tab') ?? 'form',
    page: Math.max(0, Number(params.get('page') ?? '1') - 1 || 0),
  };

  const update = useCallback(
    (changes: Partial<TasklistLocation>, options?: { replace?: boolean }) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          const set = (name: string, value: string | null) => {
            if (value) next.set(name, value);
            else next.delete(name);
          };
          if ('filterId' in changes) set('filter', changes.filterId ?? null);
          if ('taskId' in changes) set('task', changes.taskId ?? null);
          if ('tab' in changes) set('tab', changes.tab && changes.tab !== 'form' ? changes.tab : null);
          if ('page' in changes) set('page', changes.page ? String(changes.page + 1) : null);
          return next;
        },
        { replace: options?.replace },
      );
    },
    [setParams],
  );

  return [location, update];
}

/** What a user last chose in the Tasklist; remembered in the browser. */
export interface TasklistPreferences {
  filterId: string | null;
  sorting: TaskSorting;
  criteria: Criterion[];
  onlyMine: boolean;
  filtersCollapsed: boolean;
  /** searches saved under a name */
  savedSearches: SavedSearch[];
  /** process variables shown on every task card */
  cardVariables: string[];
  /** a quick view by urgency */
  queue: QueueView;
  /** "get next work" takes tasks of the selected filter only */
  nextFromFilter: boolean;
  /** completing a task also gets the next work */
  completeAndNext: boolean;
}

const DEFAULTS: TasklistPreferences = {
  filterId: null,
  sorting: { sortBy: 'created', sortOrder: 'desc' },
  criteria: [],
  onlyMine: false,
  filtersCollapsed: false,
  savedSearches: [],
  cardVariables: [],
  queue: 'all',
  nextFromFilter: false,
  completeAndNext: false,
};

export function useTasklistPreferences(
  userId: string,
): [TasklistPreferences, (changes: Partial<TasklistPreferences>) => void] {
  const key = `tasklist.${userId}`;
  const [preferences, setPreferences] = useState<TasklistPreferences>(() => ({
    ...DEFAULTS,
    ...readPreference<Partial<TasklistPreferences>>(key, {}),
  }));

  useEffect(() => {
    writePreference(key, preferences);
  }, [key, preferences]);

  const update = useCallback((changes: Partial<TasklistPreferences>) => {
    setPreferences((current) => ({ ...current, ...changes }));
  }, []);

  return [preferences, update];
}
