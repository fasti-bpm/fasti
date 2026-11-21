/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useSyncExternalStore } from 'react';
import { useQuery, type QueryClient } from '@tanstack/react-query';
import { getTaskFilterCounts, getTaskFilters } from '../api/engine';
import type { TaskFilter } from '../api/types';
import { usePacedInterval } from '../lib/activity';

/**
 * The filters and their task counts are two queries. The list of filters
 * rarely changes and costs one request; the counts cost the server a task
 * query per filter, so they are read
 *
 * - only while they are shown (the filter pane is open, or the filter menu of a
 *   narrow screen is in use),
 * - every two minutes instead of with every refresh of the task list,
 * - once after a burst of task actions, not after each of them.
 *
 * The count of the filter in use needs no request at all: the task list
 * returns it with every page.
 */
export const FILTERS_KEY = ['filters'] as const;
export const FILTER_COUNTS_KEY = ['filters', 'counts'] as const;

const FILTERS_INTERVAL = 5 * 60_000;
const COUNTS_INTERVAL = 2 * 60_000;
/** How long after the last task action the counts are read again. */
export const COUNTS_FOLLOW_UP = 4_000;

/** the filter pane is hidden below this width, see tasklist.css */
const PANE_HIDDEN = '(max-width: 1180px)';

function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (notify) => {
      const media = window.matchMedia(query);
      media.addEventListener('change', notify);
      return () => media.removeEventListener('change', notify);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

export function useTaskFilters(options: {
  /** the user closed the filter pane */
  collapsed: boolean;
  /** the filter menu that replaces the pane is in use (has the focus) */
  menuUsed: boolean;
}) {
  const paneHidden = useMediaQuery(PANE_HIDDEN);
  const paneShown = !options.collapsed && !paneHidden;
  const filters = useQuery({
    queryKey: FILTERS_KEY,
    queryFn: ({ signal }) => getTaskFilters(signal),
    staleTime: 60_000,
    refetchInterval: usePacedInterval(FILTERS_INTERVAL),
  });
  const countsInterval = usePacedInterval(COUNTS_INTERVAL);
  const counts = useQuery({
    queryKey: FILTER_COUNTS_KEY,
    queryFn: ({ signal }) => getTaskFilterCounts(signal),
    enabled: paneShown || options.menuUsed,
    staleTime: 30_000,
    // behind the closed menu the counts are read when it is opened, not on a timer
    refetchInterval: paneShown ? countsInterval : false,
    // the list works without them
    retry: false,
  });
  return { filters, counts };
}

/**
 * The filters with their task counts. `listed` is the filter in use with the
 * number of tasks its list reported, which is newer than any count read apart.
 */
export function useCountedFilters(
  filters: TaskFilter[] | undefined,
  counts: Record<string, number> | undefined,
  listed: { id: string | null; count: number | null },
): TaskFilter[] | undefined {
  const { id, count: listedCount } = listed;
  return useMemo(
    () =>
      filters?.map((filter) => {
        const count = filter.id === id && listedCount !== null ? listedCount : counts?.[filter.id];
        return count === undefined ? filter : { ...filter, itemCount: count };
      }),
    [filters, counts, id, listedCount],
  );
}

const followUps = new WeakMap<QueryClient, ReturnType<typeof setTimeout>>();

/**
 * After a task changed: the counts of the filters are out of date, but they
 * are read again once, a moment after the last change, instead of after every
 * claim or completion of a row of them. Counts nobody looks at are only marked
 * old and read when they are shown again.
 */
export function refreshFilterCountsSoon(queryClient: QueryClient, delay: number = COUNTS_FOLLOW_UP): void {
  void queryClient.invalidateQueries({ queryKey: FILTER_COUNTS_KEY, exact: true, refetchType: 'none' });
  clearTimeout(followUps.get(queryClient));
  followUps.set(
    queryClient,
    setTimeout(() => {
      followUps.delete(queryClient);
      void queryClient.refetchQueries({ queryKey: FILTER_COUNTS_KEY, exact: true, type: 'active' });
    }, delay),
  );
}
