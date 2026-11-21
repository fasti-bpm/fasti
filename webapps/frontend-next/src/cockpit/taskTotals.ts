/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQuery } from '@tanstack/react-query';
import { getTaskTotals } from '../api/workload';
import { minuteOf } from '../tasklist/queue';
import { COCKPIT, useRefreshInterval } from './common';

/**
 * The totals of the open tasks: how many there are, how many are unclaimed,
 * overdue, claimed, or unclaimed without a group. One request serves every
 * part of a page that shows one of them (the tile, the assignment chart and
 * the overdue line of the dashboard), instead of a task count for each.
 */
export function useTaskTotals() {
  const refetchInterval = useRefreshInterval();
  return useQuery({
    queryKey: [COCKPIT, 'task-totals'],
    // overdue is before now, taken when the totals are read
    queryFn: ({ signal }) => getTaskTotals(minuteOf(new Date()), signal),
    refetchInterval,
  });
}
