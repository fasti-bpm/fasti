/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Activity, Clock, Flame, Gauge, History, RotateCcw, Target, Timer, TriangleAlert } from 'lucide-react';
import {
  getActivityAnalytics,
  getActivityRuns,
  getInstanceAnalytics,
  getTrend,
  getUserTaskAnalytics,
  isAnalyticsUnavailable,
  periodBoundaries,
  type ActivityAnalytics,
  type AnalyticsScope,
  type DurationStatistics,
  type PeriodAnalytics,
  type SlowInstance,
  type UserTaskTimes,
} from '../api/analytics';
import type { ActivityBadge, ActivityMarker } from '../bpmn/BpmnDiagram';
import { useI18n, type MessageKey } from '../i18n';
import { toEngineDate } from '../lib/dates';
import { EmptyState, ErrorState, Loading, cx } from '../ui';
import { formatNumber } from '../ui/format';
import { useUrlState } from '../ui/hooks';
import { DataTable, type Column } from '../ui/table';
import { COCKPIT, useRefreshInterval } from './common';
import { formatDuration } from './model';
import { historyPath } from './paths';
import { StatisticsTab, heatSteps } from './StatisticsTab';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

const RANGES = ['7', '30', '90', '365'] as const;
type Range = (typeof RANGES)[number];

/** What the colours of the diagram show. */
export const MEASURES = ['reached', 'p50', 'p90', 'p99', 'total', 'waiting', 'rework', 'incidents'] as const;
export type Measure = (typeof MEASURES)[number];

const MEASURE_LABELS: Record<Measure, MessageKey> = {
  reached: 'an.measure.reached',
  p50: 'an.measure.p50',
  p90: 'an.measure.p90',
  p99: 'an.measure.p99',
  total: 'an.measure.total',
  waiting: 'an.measure.waiting',
  rework: 'an.measure.rework',
  incidents: 'an.measure.incidents',
};
const MEASURE_LEGENDS: Record<Measure, MessageKey> = {
  reached: 'an.legend.reached',
  p50: 'an.legend.p50',
  p90: 'an.legend.p90',
  p99: 'an.legend.p99',
  total: 'an.legend.total',
  waiting: 'an.legend.waiting',
  rework: 'an.legend.rework',
  incidents: 'an.legend.incidents',
};
const DURATION_MEASURES = new Set<Measure>(['p50', 'p90', 'p99', 'total', 'waiting']);

/** Activities of the diagram only: the multi-instance bodies (`task#multiInstanceBody`) are left out. */
export function diagramActivities(activities: ActivityAnalytics[]): ActivityAnalytics[] {
  return activities.filter((activity) => !activity.activityId.includes('#'));
}

/** The value of an activity for a measure; exported for tests. */
export function measureValue(activity: ActivityAnalytics, measure: Measure, now: number): number {
  switch (measure) {
    case 'reached':
      return activity.finished + activity.canceled + activity.running;
    case 'p50':
      return activity.durations.p50 ?? 0;
    case 'p90':
      return activity.durations.p90 ?? 0;
    case 'p99':
      return activity.durations.p99 ?? 0;
    case 'total':
      return activity.durations.total;
    case 'waiting':
      return activity.oldestRunningStart ? Math.max(0, now - Date.parse(activity.oldestRunningStart)) : 0;
    case 'rework':
      return activity.repeatedExecutions;
    case 'incidents':
      return activity.openIncidents + activity.incidents;
  }
}

/** Parses a target such as `90m`, `4h`, `2d` or `1.5h` to milliseconds; exported for tests. */
export function parseTarget(text: string): number | null {
  const match = /^\s*(\d+(?:[.,]\d+)?)\s*(m|min|h|d)?\s*$/i.exec(text);
  if (!match) return null;
  const value = Number((match[1] ?? '').replace(',', '.'));
  const unit = (match[2] ?? 'h').toLowerCase();
  const factor = unit === 'd' ? 86_400_000 : unit === 'h' ? 3_600_000 : 60_000;
  const millis = Math.round(value * factor);
  return millis > 0 ? millis : null;
}

/** Share of `part` in `whole` as a whole percentage, or null. */
function percent(part: number | null | undefined, whole: number): number | null {
  if (part === null || part === undefined || whole <= 0) return null;
  return Math.round((part / whole) * 100);
}

export function AnalyticsTab({
  definitionId,
  processKey,
  xml,
  names,
}: {
  definitionId: string;
  processKey: string;
  xml: string;
  names: Map<string, string>;
}) {
  const { t, locale } = useI18n();
  const refetchInterval = useRefreshInterval();
  const [params, setParams] = useUrlState();
  const range: Range = RANGES.find((entry) => entry === params.get('range')) ?? '30';
  const measure: Measure = MEASURES.find((entry) => entry === params.get('heat')) ?? 'p90';
  const allVersions = params.get('versions') === 'all';
  const target = parseTarget(params.get('target') ?? '');
  const selected = params.get('activity');
  const [targetText, setTargetText] = useState(params.get('target') ?? '');

  // the range ends now; minutes are enough for the cache key, so the queries do not repeat on every render
  const minute = Math.floor(Date.now() / 60_000);
  const scope = useMemo<AnalyticsScope>(() => {
    const now = minute * 60_000 + 60_000;
    return {
      processKey,
      definitionIds: allVersions ? undefined : [definitionId],
      finishedAfter: toEngineDate(new Date(now - Number(range) * 86_400_000)),
      finishedBefore: toEngineDate(new Date(now)),
      targetDuration: target,
    };
  }, [processKey, definitionId, allVersions, range, target, minute]);
  const scopeKey = [processKey, allVersions ? 'all' : definitionId, range, target ?? ''];

  const activities = useQuery({
    queryKey: [COCKPIT, 'analytics', 'activities', ...scopeKey],
    queryFn: ({ signal }) => getActivityAnalytics(scope, signal),
    refetchInterval,
    staleTime: 30_000,
  });
  const instances = useQuery({
    queryKey: [COCKPIT, 'analytics', 'instances', ...scopeKey],
    queryFn: ({ signal }) => getInstanceAnalytics(scope, signal),
    enabled: !activities.isError,
    staleTime: 30_000,
  });
  const periods = useMemo(() => periodBoundaries(Number(range)), [range]);
  const trend = useQuery({
    queryKey: [COCKPIT, 'analytics', 'trend', ...scopeKey],
    queryFn: ({ signal }) => getTrend(scope, periods.bounds.map((date) => toEngineDate(date)), signal),
    enabled: !activities.isError,
    staleTime: 60_000,
  });
  const userTasks = useQuery({
    queryKey: [COCKPIT, 'analytics', 'user-tasks', ...scopeKey],
    queryFn: ({ signal }) => getUserTaskAnalytics(scope, signal),
    enabled: !activities.isError,
    staleTime: 60_000,
  });

  const now = Date.now();
  const shown = useMemo(() => diagramActivities(activities.data ?? []), [activities.data]);
  const markers = useMemo<ActivityMarker[]>(
    () => heatSteps(shown.map((activity) => ({ id: activity.activityId, value: measureValue(activity, measure, now) }))),
    // `now` moves with every refresh of the data
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [shown, measure, activities.dataUpdatedAt],
  );
  const badges = useMemo<ActivityBadge[]>(
    () =>
      shown.map((activity) => {
        const value = measureValue(activity, measure, now);
        const text = value <= 0 ? undefined : DURATION_MEASURES.has(measure) ? formatDuration(value, locale) : formatNumber(value, locale);
        return {
          activityId: activity.activityId,
          instances: activity.running,
          incidents: activity.openIncidents,
          value: measure === 'reached' || measure === 'incidents' ? undefined : text,
          valueTitle: text ? `${t(MEASURE_LABELS[measure])}: ${text}` : undefined,
          finished: measure === 'reached' ? activity.finished : undefined,
        };
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [shown, measure, locale, t, activities.dataUpdatedAt],
  );

  if (activities.isError && isAnalyticsUnavailable(activities.error)) {
    // an older server: the statistics computed in the browser from a sample
    return <StatisticsTab definitionId={definitionId} processKey={processKey} xml={xml} names={names} />;
  }
  if (activities.isError) {
    return <ErrorState error={activities.error} onRetry={() => void activities.refetch()} />;
  }

  const selectedActivity = shown.find((activity) => activity.activityId === selected) ?? null;
  const name = (activity: { activityId: string; activityName: string | null }) =>
    names.get(activity.activityId) ?? activity.activityName ?? activity.activityId;

  return (
    <div className="stats analytics">
      <div className="analytics__controls">
        <div className="toolbar" role="group" aria-label={t('an.range')}>
          <span className="toolbar__text">{t('an.range')}</span>
          {RANGES.map((entry) => (
            <button
              key={entry}
              type="button"
              className={cx('toggle', entry === range && 'is-on')}
              aria-pressed={entry === range}
              onClick={() => setParams({ range: entry === '30' ? null : entry })}
            >
              {t('ops.stats.rangeDays', { count: Number(entry) })}
            </button>
          ))}
        </div>
        <div className="toolbar" role="group" aria-label={t('an.versions')}>
          <span className="toolbar__text">{t('an.versions')}</span>
          <button type="button" className={cx('toggle', !allVersions && 'is-on')} aria-pressed={!allVersions} onClick={() => setParams({ versions: null })}>
            {t('an.versions.this')}
          </button>
          <button type="button" className={cx('toggle', allVersions && 'is-on')} aria-pressed={allVersions} onClick={() => setParams({ versions: 'all' })}>
            {t('an.versions.all')}
          </button>
        </div>
        <form
          className="toolbar analytics__target"
          onSubmit={(event) => {
            event.preventDefault();
            const parsed = parseTarget(targetText);
            setParams({ target: parsed ? targetText.trim() : null });
          }}
        >
          <label className="toolbar__text" htmlFor="analytics-target">
            <Target size={14} aria-hidden /> {t('an.target')}
          </label>
          <input
            id="analytics-target"
            className="input input--sm"
            value={targetText}
            placeholder={t('an.target.placeholder')}
            onChange={(event) => setTargetText(event.target.value)}
            onBlur={() => setParams({ target: parseTarget(targetText) ? targetText.trim() : null })}
            aria-describedby="analytics-target-hint"
            size={8}
          />
          <span id="analytics-target-hint" className="field__hint">
            {t('an.target.hint')}
          </span>
        </form>
      </div>

      <InstanceTiles instances={instances.data} error={instances.error} target={target} />

      <div className="toolbar" role="group" aria-label={t('an.measure')}>
        <span className="toolbar__text">
          <Flame size={14} aria-hidden /> {t('an.measure')}
        </span>
        {MEASURES.map((entry) => (
          <button
            key={entry}
            type="button"
            className={cx('toggle', entry === measure && 'is-on')}
            aria-pressed={entry === measure}
            onClick={() => setParams({ heat: entry === 'p90' ? null : entry })}
          >
            {t(MEASURE_LABELS[entry])}
          </button>
        ))}
      </div>
      <div className="model stats__diagram">
        {activities.isPending ? (
          <Loading label={t('state.loading')} />
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <BpmnDiagram
              xml={xml}
              processKey={processKey}
              badges={badges}
              markers={markers}
              selected={selected ? [selected] : []}
              onSelect={(id) => setParams({ activity: id && id !== selected ? id : null })}
              label={t('an.diagramLabel')}
            />
          </Suspense>
        )}
        <p className="model__hint" aria-live="polite">
          {t(MEASURE_LEGENDS[measure])} {t('an.legend.click')}
        </p>
      </div>

      {selectedActivity && (
        <ActivityPanel activity={selectedActivity} name={name(selectedActivity)} scope={scope} scopeKey={scopeKey} onClose={() => setParams({ activity: null })} />
      )}

      <Bottlenecks activities={shown} name={name} onSelect={(id) => setParams({ activity: id })} />

      <TrendChart trend={trend.data} error={trend.error} unit={periods.unit} target={target} />

      <UserTasks data={userTasks.data} error={userTasks.error} names={names} />
    </div>
  );
}

function Tile({ icon, label, value, note, tone }: { icon: ReactNode; label: string; value: ReactNode; note?: ReactNode; tone?: 'danger' | 'success' }) {
  return (
    <div className={cx('tile analytics__tile', tone && `analytics__tile--${tone}`)}>
      <span className="tile__label">
        {icon}
        {label}
      </span>
      <span className="tile__value">{value}</span>
      {note && <span className="tile__note">{note}</span>}
    </div>
  );
}

function InstanceTiles({
  instances,
  error,
  target,
}: {
  instances: Awaited<ReturnType<typeof getInstanceAnalytics>> | undefined;
  error: unknown;
  target: number | null;
}) {
  const { t, locale } = useI18n();
  if (error) {
    return <ErrorState error={error} />;
  }
  const pending = <span className="tile__pending" />;
  const durations = instances?.durations;
  const within = percent(durations?.withinTarget, durations?.count ?? 0);
  return (
    <div className="tiles analytics__tiles" aria-live="polite">
      <Tile icon={<Activity size={14} aria-hidden />} label={t('an.tile.completed')} value={instances ? formatNumber(instances.completed, locale) : pending}
        note={instances && instances.terminated > 0 ? t('an.tile.terminated', { count: instances.terminated }) : undefined} />
      <Tile icon={<Clock size={14} aria-hidden />} label={t('an.tile.median')} value={instances ? formatDuration(durations?.p50, locale) : pending}
        note={durations?.approximate ? t('an.approximate') : undefined} />
      <Tile icon={<Timer size={14} aria-hidden />} label={t('an.tile.p90')} value={instances ? formatDuration(durations?.p90, locale) : pending}
        note={durations?.p99 ? t('an.tile.p99', { value: formatDuration(durations.p99, locale) }) : undefined} />
      <Tile
        icon={<Target size={14} aria-hidden />}
        label={t('an.tile.withinTarget')}
        value={!instances ? pending : target && within !== null ? `${within} %` : '—'}
        note={target ? t('an.tile.targetIs', { value: formatDuration(target, locale) }) : t('an.tile.noTarget')}
        tone={target && within !== null ? (within >= 90 ? 'success' : within < 70 ? 'danger' : undefined) : undefined}
      />
      <Tile icon={<Gauge size={14} aria-hidden />} label={t('an.tile.running')} value={instances ? formatNumber(instances.running, locale) : pending} />
    </div>
  );
}

/** A small histogram of durations: one bar per bucket, log scale on the x axis. */
function Histogram({ statistics, label }: { statistics: DurationStatistics; label: string }) {
  const { locale } = useI18n();
  if (statistics.buckets.length === 0) return null;
  const max = Math.max(...statistics.buckets.map((bucket) => bucket.count));
  return (
    <div className="analytics__histogram" role="img" aria-label={label}>
      {statistics.buckets.map((bucket) => (
        <span
          key={bucket.lower}
          className={cx('analytics__bar', statistics.p90 !== null && bucket.lower >= statistics.p90 && 'is-slow')}
          style={{ height: `${Math.max(4, (bucket.count / max) * 100)}%` }}
          title={`${formatDuration(bucket.lower, locale)} – ${bucket.upper === null ? '∞' : formatDuration(bucket.upper, locale)}: ${formatNumber(bucket.count, locale)}`}
        />
      ))}
    </div>
  );
}

function Percentiles({ statistics }: { statistics: DurationStatistics }) {
  const { t, locale } = useI18n();
  const entries: [string, number | null][] = [
    ['P50', statistics.p50],
    ['P75', statistics.p75],
    ['P90', statistics.p90],
    ['P95', statistics.p95],
    ['P99', statistics.p99],
    [t('ops.stats.average'), statistics.average],
    [t('ops.stats.maximum'), statistics.maximum],
  ];
  return (
    <dl className="analytics__percentiles">
      {entries.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{formatDuration(value, locale)}</dd>
        </div>
      ))}
    </dl>
  );
}

function ActivityPanel({
  activity,
  name,
  scope,
  scopeKey,
  onClose,
}: {
  activity: ActivityAnalytics;
  name: string;
  scope: AnalyticsScope;
  scopeKey: (string | number)[];
  onClose: () => void;
}) {
  const { t, locale } = useI18n();
  const slowest = useQuery({
    queryKey: [COCKPIT, 'analytics', 'slowest', activity.activityId, ...scopeKey],
    queryFn: ({ signal }) => getActivityRuns(scope, activity.activityId, false, 10, signal),
  });
  const running = useQuery({
    queryKey: [COCKPIT, 'analytics', 'running', activity.activityId, ...scopeKey],
    queryFn: ({ signal }) => getActivityRuns(scope, activity.activityId, true, 10, signal),
    enabled: activity.running > 0,
  });
  const durations = activity.durations;
  const runColumns = (live: boolean): Column<SlowInstance>[] => [
    {
      id: 'instance',
      header: t('an.run.instance'),
      cell: (row) => (
        <Link to={live ? `/cockpit/processes/instance/${encodeURIComponent(row.processInstanceId)}` : historyPath(row.processInstanceId)}>
          {row.businessKey ?? row.processInstanceId}
        </Link>
      ),
    },
    { id: 'started', header: t('an.run.started'), secondary: true, cell: (row) => new Date(row.startTime).toLocaleString(locale) },
    { id: 'duration', header: live ? t('an.run.age') : t('an.run.duration'), align: 'end', cell: (row) => formatDuration(row.duration, locale) },
  ];
  return (
    <section className="card analytics__panel" aria-label={t('an.panel.label', { name })}>
      <header className="card__header">
        <h3 className="card__title">{name}</h3>
        <button type="button" className="btn btn--ghost btn--sm" onClick={onClose}>
          {t('an.panel.close')}
        </button>
      </header>
      <div className="card__body analytics__panel-body">
        <div className="analytics__facts">
          <span>{t('an.fact.finished', { count: activity.finished })}</span>
          {activity.canceled > 0 && <span>{t('an.fact.canceled', { count: activity.canceled })}</span>}
          <span>{t('an.fact.running', { count: activity.running })}</span>
          {activity.runningOverP90 > 0 && <span className="is-danger">{t('an.fact.overP90', { count: activity.runningOverP90 })}</span>}
          {activity.repeatedInstances > 0 && (
            <span className="is-warning">
              <RotateCcw size={12} aria-hidden /> {t('an.fact.rework', { count: activity.repeatedInstances, extra: activity.repeatedExecutions })}
            </span>
          )}
          {activity.openIncidents + activity.incidents > 0 && (
            <span className="is-danger">
              <TriangleAlert size={12} aria-hidden /> {t('an.fact.incidents', { count: activity.incidents, open: activity.openIncidents })}
            </span>
          )}
          {activity.timeAboveMedian ? <span>{t('an.fact.aboveMedian', { value: formatDuration(activity.timeAboveMedian, locale) })}</span> : null}
        </div>
        {durations.count > 0 ? (
          <>
            <Percentiles statistics={durations} />
            <Histogram statistics={durations} label={t('an.histogram', { name })} />
            {durations.approximate && <p className="field__hint">{t('an.approximateHint')}</p>}
          </>
        ) : (
          <p className="field__hint">{t('an.panel.noFinished')}</p>
        )}
        <div className="analytics__runs">
          <div>
            <h4>{t('an.panel.slowest')}</h4>
            <DataTable label={t('an.panel.slowest')} columns={runColumns(false)} rows={slowest.data} rowKey={(row) => row.activityInstanceId}
              error={slowest.error} onRetry={() => void slowest.refetch()} empty={<EmptyState icon={History} title={t('an.panel.noFinished')} />} />
          </div>
          {activity.running > 0 && (
            <div>
              <h4>{t('an.panel.oldest')}</h4>
              <DataTable label={t('an.panel.oldest')} columns={runColumns(true)} rows={running.data} rowKey={(row) => row.activityInstanceId}
                error={running.error} onRetry={() => void running.refetch()} empty={<EmptyState icon={History} title={t('an.panel.noRunning')} />} />
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function Bottlenecks({
  activities,
  name,
  onSelect,
}: {
  activities: ActivityAnalytics[];
  name: (activity: ActivityAnalytics) => string;
  onSelect: (activityId: string) => void;
}) {
  const { t, locale } = useI18n();
  const ranked = useMemo(
    () =>
      activities
        .filter((activity) => activity.durations.total > 0 && !/Gateway$|Event$/.test(activity.activityType ?? '') )
        .sort((a, b) => b.durations.total - a.durations.total)
        .slice(0, 15),
    [activities],
  );
  const all = ranked.reduce((sum, activity) => sum + activity.durations.total, 0);
  const columns: Column<ActivityAnalytics>[] = [
    {
      id: 'activity',
      header: t('cockpit.activity'),
      cell: (row) => (
        <button type="button" className="link" onClick={() => onSelect(row.activityId)}>
          {name(row)}
        </button>
      ),
    },
    {
      id: 'share',
      header: t('an.bottleneck.share'),
      cell: (row) => {
        const share = percent(row.durations.total, all) ?? 0;
        return (
          <span className="analytics__share" title={formatDuration(row.durations.total, locale)}>
            <span className="analytics__share-bar" style={{ width: `${share}%` }} />
            <span className="analytics__share-text">{share} %</span>
          </span>
        );
      },
    },
    { id: 'runs', header: t('an.bottleneck.runs'), align: 'end', secondary: true, cell: (row) => formatNumber(row.finished, locale) },
    { id: 'p50', header: 'P50', align: 'end', cell: (row) => formatDuration(row.durations.p50, locale) },
    { id: 'p90', header: 'P90', align: 'end', cell: (row) => formatDuration(row.durations.p90, locale) },
    { id: 'above', header: t('an.bottleneck.above'), align: 'end', secondary: true, cell: (row) => formatDuration(row.timeAboveMedian, locale) },
    {
      id: 'now',
      header: t('an.bottleneck.now'),
      align: 'end',
      cell: (row) =>
        row.running > 0 ? (
          <span className={cx(row.runningOverP90 > 0 && 'is-danger')}>
            {formatNumber(row.running, locale)}
            {row.runningOverP90 > 0 ? ` (${formatNumber(row.runningOverP90, locale)} > P90)` : ''}
          </span>
        ) : (
          '—'
        ),
    },
    { id: 'rework', header: t('an.bottleneck.rework'), align: 'end', secondary: true, cell: (row) => (row.repeatedExecutions > 0 ? formatNumber(row.repeatedExecutions, locale) : '—') },
  ];
  return (
    <section className="analytics__section">
      <h3 className="stats__title">{t('an.bottleneck.title')}</h3>
      <p className="field__hint analytics__lead">{t('an.bottleneck.hint')}</p>
      <DataTable label={t('an.bottleneck.title')} columns={columns} rows={ranked} rowKey={(row) => row.activityId}
        empty={<EmptyState icon={History} title={t('an.empty')} />} />
    </section>
  );
}

/** Median and P90 per period as bars, with the number of instances completed. */
function TrendChart({
  trend,
  error,
  unit,
  target,
}: {
  trend: PeriodAnalytics[] | undefined;
  error: unknown;
  unit: 'day' | 'week' | 'month';
  target: number | null;
}) {
  const { t, locale } = useI18n();
  if (error) return <ErrorState error={error} />;
  if (!trend) return <Loading label={t('state.loading')} />;
  const max = Math.max(1, ...trend.map((period) => period.durations.p90 ?? 0), target ?? 0);
  const format = new Intl.DateTimeFormat(locale, unit === 'month' ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' });
  const completed = trend.reduce((sum, period) => sum + period.durations.count, 0);
  const first = trend[0];
  const last = trend[trend.length - 1];
  return (
    <section className="analytics__section">
      <h3 className="stats__title">{t('an.trend.title')}</h3>
      <p className="field__hint analytics__lead">{t(unit === 'day' ? 'an.trend.byDay' : unit === 'week' ? 'an.trend.byWeek' : 'an.trend.byMonth')}</p>
      {completed === 0 ? (
        <EmptyState icon={History} title={t('an.trend.empty')} />
      ) : (
        <div className="analytics__trend" role="img" aria-label={t('an.trend.title')}>
          {target ? <span className="analytics__trend-target" style={{ bottom: `${(target / max) * 100}%` }} title={t('an.tile.targetIs', { value: formatDuration(target, locale) })} /> : null}
          {trend.map((period) => (
            <div key={period.start} className="analytics__trend-period" title={`${format.format(new Date(period.start))}: ${t('an.trend.tooltip', {
              completed: period.durations.count,
              started: period.started,
              p50: formatDuration(period.durations.p50, locale),
              p90: formatDuration(period.durations.p90, locale),
            })}`}>
              <span className="analytics__trend-p90" style={{ height: `${((period.durations.p90 ?? 0) / max) * 100}%` }} />
              <span className="analytics__trend-p50" style={{ height: `${((period.durations.p50 ?? 0) / max) * 100}%` }} />
            </div>
          ))}
        </div>
      )}
      {completed > 0 && (
        <p className="analytics__trend-legend">
          <span className="analytics__key analytics__key--p50" /> P50 <span className="analytics__key analytics__key--p90" /> P90
          {target ? <><span className="analytics__key analytics__key--target" /> {t('an.target')}</> : null}
          {first && last && (
            <span className="analytics__trend-range">
              {format.format(new Date(first.start))} – {format.format(new Date(last.start))}
            </span>
          )}
        </p>
      )}
    </section>
  );
}

function UserTasks({ data, error, names }: { data: Awaited<ReturnType<typeof getUserTaskAnalytics>> | undefined; error: unknown; names: Map<string, string> }) {
  const { t, locale } = useI18n();
  const [view, setView] = useState<'task' | 'assignee'>('task');
  if (error) return <ErrorState error={error} />;
  if (!data) return <Loading label={t('state.loading')} />;
  if (data.tasks === 0) return null;
  const rows = view === 'task' ? data.byTask : data.byAssignee;
  const columns: Column<UserTaskTimes>[] = [
    {
      id: 'key',
      header: view === 'task' ? t('an.tasks.task') : t('an.tasks.assignee'),
      cell: (row) => (view === 'task' ? names.get(row.key ?? '') ?? row.name ?? row.key : row.key ?? t('an.tasks.noAssignee')),
    },
    { id: 'count', header: t('an.tasks.count'), align: 'end', cell: (row) => formatNumber(row.count, locale) },
    {
      id: 'split',
      header: t('an.tasks.split'),
      cell: (row) => {
        const waiting = row.waiting.total;
        const working = row.working.total;
        const share = percent(waiting, waiting + working);
        return share === null ? (
          '—'
        ) : (
          <span className="analytics__split" title={t('an.tasks.splitTitle', { waiting: share, working: 100 - share })}>
            <span className="analytics__split-waiting" style={{ width: `${share}%` }} />
            <span className="analytics__split-working" style={{ width: `${100 - share}%` }} />
          </span>
        );
      },
    },
    { id: 'waitP50', header: t('an.tasks.waitP50'), align: 'end', cell: (row) => formatDuration(row.waiting.p50, locale) },
    { id: 'waitP90', header: t('an.tasks.waitP90'), align: 'end', secondary: true, cell: (row) => formatDuration(row.waiting.p90, locale) },
    { id: 'workP50', header: t('an.tasks.workP50'), align: 'end', cell: (row) => formatDuration(row.working.p50, locale) },
    { id: 'workP90', header: t('an.tasks.workP90'), align: 'end', secondary: true, cell: (row) => formatDuration(row.working.p90, locale) },
  ];
  return (
    <section className="analytics__section">
      <h3 className="stats__title">{t('an.tasks.title')}</h3>
      <p className="field__hint analytics__lead">
        {t('an.tasks.hint')} {data.sampled ? t('an.tasks.sampled', { count: data.tasks }) : ''}
      </p>
      <div className="toolbar" role="group" aria-label={t('an.tasks.title')}>
        <button type="button" className={cx('toggle', view === 'task' && 'is-on')} aria-pressed={view === 'task'} onClick={() => setView('task')}>
          {t('an.tasks.byTask')}
        </button>
        <button type="button" className={cx('toggle', view === 'assignee' && 'is-on')} aria-pressed={view === 'assignee'} onClick={() => setView('assignee')}>
          {t('an.tasks.byAssignee')}
        </button>
        <span className="analytics__trend-legend">
          <span className="analytics__key analytics__key--waiting" /> {t('an.tasks.waiting')} <span className="analytics__key analytics__key--working" /> {t('an.tasks.working')}
        </span>
      </div>
      <DataTable label={t('an.tasks.title')} columns={columns} rows={rows} rowKey={(row) => `${view}:${row.key ?? ''}`}
        empty={<EmptyState icon={History} title={t('an.empty')} />} />
    </section>
  );
}
