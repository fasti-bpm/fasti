/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import {
  DURATION_SAMPLE,
  getActivityAges,
  getActivityDurations,
  getHistoricActivityStatistics,
  orNull,
  type ActivityAge,
  type ActivityDuration,
  type HistoricActivityStatistics,
} from '../api/operations';
import type { ActivityBadge, ActivityMarker } from '../bpmn/BpmnDiagram';
import { useI18n } from '../i18n';
import { toEngineDate } from '../lib/dates';
import { EmptyState, ErrorState, Loading, cx } from '../ui';
import { COCKPIT, DataTable, formatNumber, useRefreshInterval, useUrlState, type Column } from './common';
import { formatDuration } from './model';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

const RANGES = ['7', '30', '90', 'all'] as const;
type Range = (typeof RANGES)[number];

export function rangeStart(range: Range, now: Date = new Date()): string | undefined {
  if (range === 'all') return undefined;
  return toEngineDate(new Date(now.getTime() - Number(range) * 86_400_000));
}

/** A value per activity in five steps relative to the largest one; exported for tests. */
export function heatSteps(values: { id: string; value: number }[]): ActivityMarker[] {
  const max = Math.max(0, ...values.map((entry) => entry.value));
  if (max === 0) return [];
  return values
    .filter((entry) => entry.value > 0)
    .map((entry) => ({ activityId: entry.id, marker: `heat-${Math.max(1, Math.ceil((entry.value / max) * 5))}` }));
}

/** How often each activity was reached, in five steps relative to the busiest one; exported for tests. */
export function heatMarkers(statistics: HistoricActivityStatistics[]): ActivityMarker[] {
  return heatSteps(statistics.map((entry) => ({ id: entry.id, value: entry.instances + entry.finished })));
}

/** What the colors of the diagram show: how often, how slow (nine in ten), or how long waiting now. */
const MEASURES = ['reached', 'p90', 'waiting'] as const;
type Measure = (typeof MEASURES)[number];
const MEASURE_LABELS = { reached: 'ops.stats.measure.reached', p90: 'ops.stats.measure.p90', waiting: 'ops.stats.measure.waiting' } as const;
const MEASURE_LEGENDS = { reached: 'ops.stats.legend', p90: 'ops.stats.legend.p90', waiting: 'ops.stats.legend.waiting' } as const;

/** Where instances of a version went over a time range, and how long the activities took. */
export function StatisticsTab({
  definitionId,
  processKey,
  xml,
  names,
}: {
  definitionId: string;
  processKey?: string;
  xml: string;
  names: Map<string, string>;
}) {
  const { t, locale } = useI18n();
  const refetchInterval = useRefreshInterval();
  const [params, setParams] = useUrlState();
  const range = RANGES.find((entry) => entry === params.get('range')) ?? '30';
  const measure: Measure = MEASURES.find((entry) => entry === params.get('heat')) ?? 'reached';
  const startedAfter = useMemo(() => rangeStart(range), [range]);

  const statistics = useQuery({
    queryKey: [COCKPIT, 'historic-statistics', definitionId, range],
    queryFn: ({ signal }) => orNull(getHistoricActivityStatistics(definitionId, { startedAfter }, signal)),
  });
  const durations = useQuery({
    queryKey: [COCKPIT, 'activity-durations', definitionId, range],
    queryFn: ({ signal }) => orNull(getActivityDurations(definitionId, { startedAfter }, signal)),
  });

  const ages = useQuery({
    queryKey: [COCKPIT, 'activity-ages', definitionId],
    queryFn: ({ signal }) => orNull(getActivityAges(definitionId, signal)),
    enabled: measure === 'waiting',
    refetchInterval,
  });

  const markers = useMemo(
    () =>
      measure === 'p90'
        ? heatSteps((durations.data?.durations ?? []).map((entry) => ({ id: entry.activityId, value: entry.p90 })))
        : measure === 'waiting'
          ? heatSteps((ages.data?.ages ?? []).map((entry) => ({ id: entry.activityId, value: entry.oldest })))
          : heatMarkers(statistics.data ?? []),
    [measure, statistics.data, durations.data, ages.data],
  );
  const badges = useMemo<ActivityBadge[]>(
    () =>
      (statistics.data ?? []).map((entry) => ({
        activityId: entry.id,
        instances: entry.instances,
        finished: entry.finished,
        incidents: entry.openIncidents,
      })),
    [statistics.data],
  );
  const columns = useMemo<Column<ActivityDuration>[]>(
    () => [
      { id: 'activity', header: t('cockpit.activity'), cell: (row) => names.get(row.activityId) ?? row.activityId },
      { id: 'count', header: t('ops.stats.count'), align: 'end', cell: (row) => formatNumber(row.count, locale) },
      { id: 'avg', header: t('ops.stats.average'), align: 'end', cell: (row) => formatDuration(row.average, locale) },
      { id: 'p90', header: t('ops.stats.p90'), align: 'end', cell: (row) => formatDuration(row.p90, locale) },
      { id: 'min', header: t('ops.stats.minimum'), align: 'end', secondary: true, cell: (row) => formatDuration(row.minimum, locale) },
      { id: 'max', header: t('ops.stats.maximum'), align: 'end', secondary: true, cell: (row) => formatDuration(row.maximum, locale) },
    ],
    [t, names, locale],
  );
  const ageColumns = useMemo<Column<ActivityAge>[]>(
    () => [
      { id: 'activity', header: t('cockpit.activity'), cell: (row) => names.get(row.activityId) ?? row.activityId },
      { id: 'count', header: t('ops.stats.waitingCount'), align: 'end', cell: (row) => formatNumber(row.count, locale) },
      { id: 'oldest', header: t('ops.stats.oldest'), align: 'end', cell: (row) => formatDuration(row.oldest, locale) },
    ],
    [t, names, locale],
  );

  if (statistics.data === null) {
    return (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
        {t('cockpit.history.unavailableHint')}
      </EmptyState>
    );
  }

  return (
    <div className="stats">
      <div className="toolbar" role="group" aria-label={t('ops.stats.range')}>
        <span className="toolbar__text">{t('ops.stats.range')}</span>
        {RANGES.map((entry) => (
          <button
            key={entry}
            type="button"
            className={cx('toggle', entry === range && 'is-on')}
            aria-pressed={entry === range}
            onClick={() => setParams({ range: entry === '30' ? null : entry })}
          >
            {entry === 'all' ? t('ops.stats.rangeAll') : t('ops.stats.rangeDays', { count: Number(entry) })}
          </button>
        ))}
      </div>
      <div className="toolbar" role="group" aria-label={t('ops.stats.measure')}>
        <span className="toolbar__text">{t('ops.stats.measure')}</span>
        {MEASURES.map((entry) => (
          <button
            key={entry}
            type="button"
            className={cx('toggle', entry === measure && 'is-on')}
            aria-pressed={entry === measure}
            onClick={() => setParams({ heat: entry === 'reached' ? null : entry })}
          >
            {t(MEASURE_LABELS[entry])}
          </button>
        ))}
      </div>
      <div className="model stats__diagram">
        {/* a failed request must not look like a process that nothing ran through */}
        {statistics.isError ? (
          <ErrorState error={statistics.error} onRetry={() => void statistics.refetch()} />
        ) : statistics.isPending ? (
          <Loading label={t('state.loading')} />
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <BpmnDiagram xml={xml} processKey={processKey} badges={badges} markers={markers} label={t('ops.stats.diagramLabel')} />
          </Suspense>
        )}
        <p className="model__hint" aria-live="polite">
          {t(MEASURE_LEGENDS[measure])}
        </p>
      </div>
      {measure === 'waiting' && (
        <>
          <h3 className="stats__title">{t('ops.stats.waiting')}</h3>
          {ages.data && ages.data.sample >= DURATION_SAMPLE && <p className="field__hint">{t('ops.stats.waitingSampled', { count: DURATION_SAMPLE })}</p>}
          <DataTable
            label={t('ops.stats.waiting')}
            columns={ageColumns}
            rows={ages.data === null ? [] : ages.data?.ages}
            rowKey={(row) => row.activityId}
            error={ages.error}
            onRetry={() => void ages.refetch()}
            empty={<EmptyState icon={History} title={t('ops.stats.waitingEmpty')} />}
          />
        </>
      )}
      <h3 className="stats__title">{t('ops.stats.durations')}</h3>
      {durations.data && durations.data.sample >= DURATION_SAMPLE && (
        <p className="field__hint">{t('ops.stats.sampled', { count: DURATION_SAMPLE })}</p>
      )}
      <DataTable
        label={t('ops.stats.durations')}
        columns={columns}
        rows={durations.data === null ? [] : durations.data?.durations}
        rowKey={(row) => row.activityId}
        error={durations.error}
        onRetry={() => void durations.refetch()}
        empty={<EmptyState icon={History} title={t('ops.stats.empty')} />}
      />
    </div>
  );
}
