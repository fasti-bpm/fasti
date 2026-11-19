/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Download } from 'lucide-react';
import {
  getHistoricActivities,
  getHistoricIncidents,
  type HistoricActivity,
  type HistoricIncident,
} from '../api/operations';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { Button, EmptyState, ErrorState, Loading, Notice, Skeleton, cx } from '../ui';

/** The engine returns at most this many activities and incidents of an instance; see the API. */
const ACTIVITY_LIMIT = 2000;
const INCIDENT_LIMIT = 500;
import { COCKPIT, useRefreshInterval } from './common';
import { formatDuration } from './model';

/** Activities that take no time of their own and would only clutter the timeline. */
const INSTANT = /Gateway$|^sequenceFlow$|^multiInstanceBody$/i;

export interface TimelineRow {
  id: string;
  name: string;
  type: string;
  /** position and length on the axis, in percent */
  offset: number;
  width: number;
  duration: number;
  state: 'running' | 'done' | 'canceled';
  /** a user task: its time is time spent waiting for a person */
  waiting: boolean;
  incidents: number;
  assignee: string | null;
}

export interface TimelineLayout {
  start: number;
  end: number;
  rows: TimelineRow[];
}

/** Places the activities of an instance on a common time axis. */
export function layoutTimeline(
  activities: HistoricActivity[],
  incidents: HistoricIncident[],
  names: Map<string, string>,
  now: number = Date.now(),
): TimelineLayout | null {
  const times = activities
    .filter((activity) => !INSTANT.test(activity.activityType))
    .map((activity) => ({
      activity,
      from: parseEngineDate(activity.startTime)?.getTime() ?? now,
      to: parseEngineDate(activity.endTime)?.getTime() ?? now,
    }));
  if (times.length === 0) {
    return null;
  }
  const start = Math.min(...times.map((time) => time.from));
  const end = Math.max(start + 1, ...times.map((time) => time.to));
  const span = end - start;
  return {
    start,
    end,
    rows: times.map(({ activity, from, to }): TimelineRow => {
      const offset = ((from - start) / span) * 100;
      return {
        id: activity.id,
        name: activity.activityName ?? names.get(activity.activityId) ?? activity.activityId,
        type: activity.activityType,
        offset,
        width: Math.min(Math.max(((to - from) / span) * 100, 0.5), 100 - offset),
        duration: to - from,
        state: activity.endTime === null ? 'running' : activity.canceled ? 'canceled' : 'done',
        waiting: activity.activityType === 'userTask',
        assignee: activity.assignee,
        incidents: incidents.filter((incident) => {
          const created = parseEngineDate(incident.createTime)?.getTime() ?? Number.NaN;
          // parallel instances of one activity are told apart by their execution
          const sameExecution = !incident.executionId || !activity.executionId || incident.executionId === activity.executionId;
          return incident.activityId === activity.activityId && sameExecution && created >= from && created <= to;
        }).length,
      };
    }),
  };
}

/** Hands data to the browser as a JSON file. */
export function downloadJson(fileName: string, data: unknown): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

/**
 * The activities of an instance as bars on a time axis: how long each one
 * took, which ones still run, where incidents happened and how long user
 * tasks waited for a person. The bars are decoration; every row says the
 * same in words.
 */
export function InstanceTimeline({ instanceId, names }: { instanceId: string; names: Map<string, string> }) {
  const { t, locale, dateTime } = useI18n();
  const [onlyWaiting, setOnlyWaiting] = useState(false);
  const refetchInterval = useRefreshInterval();
  const data = useQuery({
    queryKey: [COCKPIT, 'timeline', instanceId],
    queryFn: async ({ signal }) => {
      const [activities, incidents] = await Promise.all([
        getHistoricActivities(instanceId, signal),
        getHistoricIncidents(instanceId).then(
          (list) => ({ list, failed: false }),
          () => ({ list: [] as HistoricIncident[], failed: true }),
        ),
      ]);
      return { activities, incidents: incidents.list, incidentsFailed: incidents.failed, readAt: Date.now() };
    },
    refetchInterval,
  });
  const layout = useMemo(
    () => (data.data ? layoutTimeline(data.data.activities, data.data.incidents, names, data.data.readAt) : null),
    [data.data, names],
  );

  if (data.isError) {
    return <ErrorState error={data.error} onRetry={() => void data.refetch()} />;
  }
  if (!data.data) {
    return (
      <Loading label={t('state.loading')}>
        <Skeleton lines={5} />
      </Loading>
    );
  }
  if (!layout) {
    return <EmptyState title={t('timeline.empty')}>{t('timeline.emptyHint')}</EmptyState>;
  }
  const rows = onlyWaiting ? layout.rows.filter((row) => row.waiting) : layout.rows;
  const describe = (row: TimelineRow) => {
    const duration = formatDuration(row.duration, locale);
    if (row.state === 'running') return t('timeline.running', { duration });
    if (row.state === 'canceled') return t('timeline.canceled', { duration });
    return row.waiting ? t('timeline.waited', { duration }) : duration;
  };
  return (
    <div className="timeline-view">
      <div className="timeline-view__tools">
        <span className="muted">
          {dateTime(new Date(layout.start))} – {dateTime(new Date(layout.end))} · {formatDuration(layout.end - layout.start, locale)}
        </span>
        <label className="option">
          <input type="checkbox" checked={onlyWaiting} onChange={(event) => setOnlyWaiting(event.target.checked)} />
          {t('timeline.onlyWaiting')}
        </label>
        <Button size="sm" icon={Download} onClick={() =>
            downloadJson(`instance-${instanceId}.json`, {
              instanceId,
              activities: data.data.activities,
              incidents: data.data.incidentsFailed ? null : data.data.incidents,
            })
          }>
          {t('timeline.download')}
        </Button>
      </div>
      {data.data.incidentsFailed && <Notice tone="warning">{t('timeline.incidentsUnavailable')}</Notice>}
      {data.data.activities.length >= ACTIVITY_LIMIT && <Notice tone="warning">{t('timeline.capped', { count: ACTIVITY_LIMIT })}</Notice>}
      {data.data.incidents.length >= INCIDENT_LIMIT && (
        <Notice tone="warning">{t('timeline.incidentsCapped', { count: INCIDENT_LIMIT })}</Notice>
      )}
      {rows.length === 0 ? (
        <p className="muted">{t('timeline.noWaiting')}</p>
      ) : (
        <ol className="timeline-chart" aria-label={t('timeline.title')}>
          {rows.map((row) => (
            <li key={row.id} className="timeline-chart__row">
              <span className="timeline-chart__label">
                <span className="timeline-chart__name">{row.name}</span>
                <span className="timeline-chart__meta">
                  {describe(row)}
                  {row.assignee && ` · ${row.assignee}`}
                  {row.incidents > 0 && (
                    <span className="timeline-chart__incident">
                      <AlertTriangle aria-hidden size={12} />
                      {t('cockpit.incidents.count', { count: row.incidents })}
                    </span>
                  )}
                </span>
              </span>
              <span className="timeline-chart__track" aria-hidden>
                <span
                  className={cx(
                    'timeline-chart__bar',
                    `timeline-chart__bar--${row.state}`,
                    row.waiting && 'timeline-chart__bar--waiting',
                    row.incidents > 0 && 'timeline-chart__bar--incident',
                  )}
                  style={{ insetInlineStart: `${row.offset}%`, width: `${row.width}%` }}
                />
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
