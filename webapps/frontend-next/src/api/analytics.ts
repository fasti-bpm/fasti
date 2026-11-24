/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { tenantFilter } from '../lib/userPrefs';
import { isApiError, request, seg, type QueryValue } from './http';

/**
 * Process analytics computed by the engine over the whole history (Fasti 1.3 and later):
 * per activity, per process instance, per period and for user tasks.
 */
const analyticsApi = (key: string): string => `/engine/engine/${seg(env.engine)}/analytics/process-definition/key/${seg(key)}`;

export interface DurationBucket {
  lower: number;
  /** exclusive; null for the last bucket */
  upper: number | null;
  count: number;
}

export interface DurationStatistics {
  count: number;
  /** all durations added up: the time spent there */
  total: number;
  minimum: number | null;
  maximum: number | null;
  average: number | null;
  p50: number | null;
  p75: number | null;
  p90: number | null;
  p95: number | null;
  p99: number | null;
  /** percentiles from a histogram, within about 12 % */
  approximate: boolean;
  /** how many took at most the target; null without a target */
  withinTarget: number | null;
  buckets: DurationBucket[];
}

export interface ActivityAnalytics {
  activityId: string;
  activityType: string | null;
  activityName: string | null;
  finished: number;
  canceled: number;
  running: number;
  runningOverP90: number;
  oldestRunningStart: string | null;
  repeatedInstances: number;
  repeatedExecutions: number;
  openIncidents: number;
  incidents: number;
  timeAboveMedian: number | null;
  durations: DurationStatistics;
}

export interface InstanceAnalytics {
  completed: number;
  terminated: number;
  running: number;
  durations: DurationStatistics;
}

export interface PeriodAnalytics {
  start: string;
  end: string;
  started: number;
  durations: DurationStatistics;
}

export interface SlowInstance {
  activityInstanceId: string;
  processInstanceId: string;
  processDefinitionId: string;
  businessKey: string | null;
  startTime: string;
  endTime: string | null;
  duration: number | null;
}

export interface UserTaskTimes {
  key: string | null;
  name: string | null;
  count: number;
  unknownWaiting: number;
  waiting: DurationStatistics;
  working: DurationStatistics;
}

export interface UserTaskAnalytics {
  tasks: number;
  sampled: boolean;
  byTask: UserTaskTimes[];
  byAssignee: UserTaskTimes[];
}

export interface AnalyticsScope {
  processKey: string;
  /** only these versions; all versions when empty */
  definitionIds?: string[];
  /** engine dates */
  finishedAfter: string;
  finishedBefore: string;
  /** milliseconds */
  targetDuration?: number | null;
}

function scopeQuery(scope: AnalyticsScope): Record<string, QueryValue> {
  return {
    ...tenantFilter(),
    processDefinitionIdIn: scope.definitionIds?.length ? scope.definitionIds.join(',') : undefined,
    finishedAfter: scope.finishedAfter,
    finishedBefore: scope.finishedBefore,
    targetDuration: scope.targetDuration ?? undefined,
  };
}

/** A server without the analytics endpoints (before Fasti 1.3, or with a custom MyBatis mapping). */
export function isAnalyticsUnavailable(error: unknown): boolean {
  return isApiError(error, 'notFound') || (isApiError(error) && error.status === 501);
}

/** The activities; with `rework: false` without rework, the costly part on a long history (see {@link getRework}). */
export function getActivityAnalytics(scope: AnalyticsScope, signal?: AbortSignal, options: { rework?: boolean } = {}): Promise<ActivityAnalytics[]> {
  return request<ActivityAnalytics[]>(`${analyticsApi(scope.processKey)}/activities`, {
    query: { ...scopeQuery(scope), rework: options.rework === false ? false : undefined },
    signal,
  });
}

export interface ActivityRework {
  activityId: string;
  repeatedInstances: number;
  repeatedExecutions: number;
}

export function getRework(scope: AnalyticsScope, signal?: AbortSignal): Promise<ActivityRework[]> {
  return request<ActivityRework[]>(`${analyticsApi(scope.processKey)}/rework`, { query: scopeQuery(scope), signal });
}

export function getInstanceAnalytics(scope: AnalyticsScope, signal?: AbortSignal): Promise<InstanceAnalytics> {
  return request<InstanceAnalytics>(`${analyticsApi(scope.processKey)}/instances`, { query: scopeQuery(scope), signal });
}

export function getTrend(scope: AnalyticsScope, boundaries: string[], signal?: AbortSignal): Promise<PeriodAnalytics[]> {
  return request<PeriodAnalytics[]>(`${analyticsApi(scope.processKey)}/trend`, {
    query: { ...scopeQuery(scope), boundaries: boundaries.join(',') },
    signal,
  });
}

export function getActivityRuns(
  scope: AnalyticsScope,
  activityId: string,
  running: boolean,
  maxResults: number,
  signal?: AbortSignal,
): Promise<SlowInstance[]> {
  return request<SlowInstance[]>(`${analyticsApi(scope.processKey)}/activities/${seg(activityId)}/instances`, {
    query: { ...scopeQuery(scope), running: running || undefined, maxResults },
    signal,
  });
}

export function getUserTaskAnalytics(scope: AnalyticsScope, signal?: AbortSignal): Promise<UserTaskAnalytics> {
  return request<UserTaskAnalytics>(`${analyticsApi(scope.processKey)}/user-tasks`, { query: scopeQuery(scope), signal });
}

/** The boundaries of the trend periods: days, weeks or months in the local time zone, ending after now. */
export function periodBoundaries(rangeDays: number, now: Date = new Date()): { unit: 'day' | 'week' | 'month'; bounds: Date[] } {
  const unit = rangeDays <= 31 ? 'day' : rangeDays <= 120 ? 'week' : 'month';
  const end = new Date(now);
  if (unit === 'month') {
    end.setHours(0, 0, 0, 0);
    end.setDate(1);
    end.setMonth(end.getMonth() + 1);
  } else {
    end.setHours(0, 0, 0, 0);
    end.setDate(end.getDate() + 1);
    if (unit === 'week') {
      // weeks start on Monday
      end.setDate(end.getDate() + ((8 - end.getDay()) % 7));
    }
  }
  const periods = unit === 'day' ? rangeDays : unit === 'week' ? Math.ceil(rangeDays / 7) : Math.ceil(rangeDays / 30.4);
  const bounds: Date[] = [end];
  for (let index = 0; index < periods; index++) {
    const previous = new Date(bounds[0] ?? end);
    if (unit === 'month') previous.setMonth(previous.getMonth() - 1);
    else previous.setDate(previous.getDate() - (unit === 'week' ? 7 : 1));
    bounds.unshift(previous);
  }
  return { unit, bounds };
}
