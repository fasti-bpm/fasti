/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
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
  type DurationBucket,
  type DurationStatistics,
  type InstanceAnalytics,
  type PeriodAnalytics,
  type SlowInstance,
  type UserTaskAnalytics,
  type UserTaskTimes,
} from '../api/analytics';
import type { ActivityBadge, ActivityMarker } from '../bpmn/BpmnDiagram';
import { useI18n, type MessageKey } from '../i18n';
import { formatDateTime, parseEngineDate, toEngineDate } from '../lib/dates';
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

/**
 * Activities where work waits or happens, for the bottleneck ranking: not the containers (their
 * time is the time of what they hold), not start, end or throw events, not the routing gateways.
 * Calls of other processes stay: from this process, their time is waiting. Exported for tests.
 */
export function isBottleneckCandidate(type: string | null): boolean {
  const kind = type ?? '';
  if (['subProcess', 'transaction', 'adHocSubProcess', 'multiInstanceBody'].includes(kind)) return false;
  if (/^start/.test(kind) || /End(Event)?$/.test(kind) || /Throw(Event)?$/.test(kind)) return false;
  if (kind.endsWith('Gateway') && kind !== 'eventBasedGateway') return false;
  return true;
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
    case 'waiting': {
      const since = parseEngineDate(activity.oldestRunningStart);
      return since ? Math.max(0, now - since.getTime()) : 0;
    }
    case 'rework':
      return activity.repeatedExecutions;
    case 'incidents':
      // created in the range; the badge shows those open now
      return activity.incidents;
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

/**
 * The bars of a histogram with the empty buckets between the non-empty ones the server sends
 * (twenty per power of ten), so that the x axis stays logarithmic; exported for tests.
 */
export function histogramBars(buckets: DurationBucket[]): (DurationBucket | null)[] {
  const bars: (DurationBucket | null)[] = [];
  buckets.forEach((bucket, index) => {
    const previous = index > 0 ? buckets[index - 1] : undefined;
    if (previous && previous.upper !== null && previous.upper > 0 && bucket.lower > previous.upper) {
      const gap = Math.min(40, Math.round(20 * Math.log10(bucket.lower / previous.upper)));
      for (let missing = 0; missing < gap; missing++) bars.push(null);
    }
    bars.push(bucket);
  });
  return bars;
}

function shareOf(part: number | null | undefined, whole: number): number | null {
  if (part === null || part === undefined || whole <= 0) return null;
  return part / whole;
}

/** The scope of a query, its range ending now: computed when the query runs, not when the page rendered. */
function scopeNow(processKey: string, definitionIds: string[] | undefined, range: Range, target?: number | null): AnalyticsScope {
  const now = Date.now() + 1000;
  return {
    processKey,
    definitionIds,
    finishedAfter: toEngineDate(new Date(now - Number(range) * 86_400_000)),
    finishedBefore: toEngineDate(new Date(now)),
    targetDuration: target ?? undefined,
  };
}

/** No retries for a server without the endpoints: the tab falls back at once. */
const retry = (count: number, error: unknown) => !isAnalyticsUnavailable(error) && count < 2;

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
  const refetchInterval = useRefreshInterval('slow');
  const [params, setParams] = useUrlState();
  const range: Range = RANGES.find((entry) => entry === params.get('range')) ?? '30';
  const measure: Measure = MEASURES.find((entry) => entry === params.get('heat')) ?? 'p90';
  const allVersions = params.get('versions') === 'all';
  const targetParam = params.get('target') ?? '';
  const target = parseTarget(targetParam);
  const selected = params.get('activity');
  const definitionIds = allVersions ? undefined : [definitionId];
  const scopeKey = [processKey, allVersions ? 'all' : definitionId, range];
  const unit = Number(range) <= 31 ? 'day' : Number(range) <= 120 ? 'week' : 'month';

  const activities = useQuery({
    queryKey: [COCKPIT, 'analytics', 'activities', ...scopeKey],
    queryFn: ({ signal }) => getActivityAnalytics(scopeNow(processKey, definitionIds, range), signal),
    refetchInterval,
    placeholderData: keepPreviousData,
    retry,
  });
  const unavailable = activities.isError && isAnalyticsUnavailable(activities.error);
  const instances = useQuery({
    queryKey: [COCKPIT, 'analytics', 'instances', ...scopeKey, target ?? ''],
    queryFn: ({ signal }) => getInstanceAnalytics(scopeNow(processKey, definitionIds, range, target), signal),
    enabled: !unavailable,
    refetchInterval,
    placeholderData: keepPreviousData,
    retry,
  });
  const trend = useQuery({
    queryKey: [COCKPIT, 'analytics', 'trend', ...scopeKey],
    queryFn: ({ signal }) =>
      getTrend(
        scopeNow(processKey, definitionIds, range),
        periodBoundaries(Number(range)).bounds.map((date) => toEngineDate(date)),
        signal,
      ),
    enabled: !unavailable,
    refetchInterval,
    placeholderData: keepPreviousData,
    retry,
  });
  const userTasks = useQuery({
    queryKey: [COCKPIT, 'analytics', 'user-tasks', ...scopeKey],
    queryFn: ({ signal }) => getUserTaskAnalytics(scopeNow(processKey, definitionIds, range), signal),
    enabled: !unavailable,
    refetchInterval,
    placeholderData: keepPreviousData,
    retry,
  });

  const now = activities.dataUpdatedAt || 0;
  const shown = useMemo(() => diagramActivities(activities.data ?? []), [activities.data]);
  const markers = useMemo<ActivityMarker[]>(
    () => heatSteps(shown.map((activity) => ({ id: activity.activityId, value: measureValue(activity, measure, now) }))),
    [shown, measure, now],
  );
  const badges = useMemo<ActivityBadge[]>(
    () =>
      shown.map((activity) => {
        const value = measureValue(activity, measure, now);
        const shownValue = value > 0 && (isBottleneckCandidate(activity.activityType) || measure === 'incidents');
        const text = !shownValue ? undefined : DURATION_MEASURES.has(measure) ? formatDuration(value, locale) : formatNumber(value, locale);
        return {
          activityId: activity.activityId,
          instances: activity.running,
          incidents: activity.openIncidents,
          value: measure === 'reached' ? undefined : text,
          valueTitle: text ? `${t(MEASURE_LABELS[measure])}: ${text}` : undefined,
          finished: measure === 'reached' ? activity.finished + activity.canceled : undefined,
        };
      }),
    [shown, measure, locale, t, now],
  );

  if (unavailable && activities.data === undefined) {
    // an older server: the statistics computed in the browser from a sample
    return <StatisticsTab definitionId={definitionId} processKey={processKey} xml={xml} names={names} />;
  }
  if (activities.isError && activities.data === undefined) {
    return <ErrorState error={activities.error} onRetry={() => void activities.refetch()} />;
  }

  const selectedActivity = shown.find((activity) => activity.activityId === selected) ?? null;
  const name = (activity: { activityId: string; activityName: string | null }) =>
    names.get(activity.activityId) ?? activity.activityName ?? activity.activityId;
  const picker = [...shown].sort((a, b) => name(a).localeCompare(name(b), locale));

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
        <TargetInput value={targetParam} onChange={(text) => setParams({ target: text || null })} />
      </div>

      <InstanceTiles instances={instances.data} error={instances.data === undefined ? instances.error : null} target={target} />

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
        {activities.data === undefined ? (
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
        <label className="toolbar analytics__pick">
          <span className="toolbar__text">{t('an.pick')}</span>
          <select className="input input--sm" value={selectedActivity?.activityId ?? ''} onChange={(event) => setParams({ activity: event.target.value || null })}>
            <option value="">{t('an.pick.none')}</option>
            {picker.map((activity) => (
              <option key={activity.activityId} value={activity.activityId}>
                {name(activity)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {selectedActivity && (
        <ActivityPanel
          activity={selectedActivity}
          name={name(selectedActivity)}
          scope={() => scopeNow(processKey, definitionIds, range)}
          scopeKey={scopeKey}
          onClose={() => setParams({ activity: null })}
        />
      )}

      <Bottlenecks activities={shown} name={name} onSelect={(id) => setParams({ activity: id })} />

      <TrendChart trend={trend.data} error={trend.data === undefined ? trend.error : null} unit={unit} target={target} />

      <UserTasks data={userTasks.data} error={userTasks.data === undefined ? userTasks.error : null} names={names} />
    </div>
  );
}

/** The target duration: typed freely, applied when valid, said so when not. */
function TargetInput({ value, onChange }: { value: string; onChange: (text: string) => void }) {
  const { t } = useI18n();
  const [text, setText] = useState(value);
  // the address may change the target (back, a link): the field follows it
  useEffect(() => setText(value), [value]);
  const invalid = text.trim() !== '' && parseTarget(text) === null;
  const apply = () => {
    const next = parseTarget(text) ? text.trim() : '';
    if (!invalid && next !== value) onChange(next);
  };
  return (
    <form
      className="toolbar analytics__target"
      onSubmit={(event) => {
        event.preventDefault();
        apply();
      }}
    >
      <label className="toolbar__text" htmlFor="analytics-target">
        <Target size={14} aria-hidden /> {t('an.target')}
      </label>
      <input
        id="analytics-target"
        className="input input--sm"
        value={text}
        placeholder={t('an.target.placeholder')}
        onChange={(event) => setText(event.target.value)}
        onBlur={apply}
        aria-invalid={invalid || undefined}
        aria-describedby="analytics-target-hint"
        size={8}
      />
      <span id="analytics-target-hint" className={cx('field__hint', invalid && 'is-danger')} aria-live="polite">
        {invalid ? t('an.target.invalid') : t('an.target.hint')}
      </span>
    </form>
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

function InstanceTiles({ instances, error, target }: { instances: InstanceAnalytics | undefined; error: unknown; target: number | null }) {
  const { t, locale } = useI18n();
  if (error) {
    return <ErrorState error={error} />;
  }
  const pending = <span className="tile__pending" />;
  const durations = instances?.durations;
  const within = shareOf(durations?.withinTarget, durations?.count ?? 0);
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 });
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
        value={!instances ? pending : target && within !== null ? percent.format(within) : '—'}
        note={target ? t('an.tile.targetIs', { value: formatDuration(target, locale) }) : t('an.tile.noTarget')}
        tone={target && within !== null ? (within >= 0.9 ? 'success' : within < 0.7 ? 'danger' : undefined) : undefined}
      />
      <Tile icon={<Gauge size={14} aria-hidden />} label={t('an.tile.running')} value={instances ? formatNumber(instances.running, locale) : pending} />
    </div>
  );
}

/** A histogram of durations, logarithmic on the x axis; red from the P90 on. */
function Histogram({ statistics, label }: { statistics: DurationStatistics; label: string }) {
  const { t, locale } = useI18n();
  if (statistics.buckets.length === 0) return null;
  const max = Math.max(...statistics.buckets.map((bucket) => bucket.count));
  const range = (bucket: DurationBucket) =>
    `${formatDuration(bucket.lower, locale)} – ${bucket.upper === null ? '∞' : formatDuration(bucket.upper, locale)}`;
  return (
    <figure className="analytics__figure">
      <div className="analytics__histogram" aria-hidden>
        {histogramBars(statistics.buckets).map((bucket, index) =>
          bucket ? (
            <span
              key={bucket.lower}
              className={cx('analytics__bar', statistics.p90 !== null && bucket.lower >= statistics.p90 && 'is-slow')}
              style={{ height: `${Math.max(4, (bucket.count / max) * 100)}%` }}
              title={`${range(bucket)}: ${formatNumber(bucket.count, locale)}`}
            />
          ) : (
            <span key={`gap-${index}`} className="analytics__bar analytics__bar--empty" />
          ),
        )}
      </div>
      <figcaption className="analytics__trend-legend">
        <span>{label}</span>
        <span className="analytics__key analytics__key--slow" /> {t('an.histogram.slow')}
      </figcaption>
      <div className="sr-only">
        <table>
          <caption>{label}</caption>
          <tbody>
            {statistics.buckets.map((bucket) => (
              <tr key={bucket.lower}>
                <th scope="row">{range(bucket)}</th>
                <td>{formatNumber(bucket.count, locale)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
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
  scope: () => AnalyticsScope;
  scopeKey: (string | number)[];
  onClose: () => void;
}) {
  const { t, locale } = useI18n();
  const heading = useRef<HTMLHeadingElement>(null);
  // opened from the table below or the picker: bring it into view and to the keyboard
  useEffect(() => {
    heading.current?.scrollIntoView?.({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
    heading.current?.focus({ preventScroll: true });
  }, [activity.activityId]);
  const slowest = useQuery({
    queryKey: [COCKPIT, 'analytics', 'slowest', activity.activityId, ...scopeKey],
    queryFn: ({ signal }) => getActivityRuns(scope(), activity.activityId, false, 10, signal),
  });
  const running = useQuery({
    queryKey: [COCKPIT, 'analytics', 'running', activity.activityId, ...scopeKey],
    queryFn: ({ signal }) => getActivityRuns(scope(), activity.activityId, true, 10, signal),
    enabled: activity.running > 0,
  });
  const durations = activity.durations;
  const started = (row: SlowInstance) => {
    const date = parseEngineDate(row.startTime);
    return date ? formatDateTime(date, locale) : '—';
  };
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
    { id: 'started', header: t('an.run.started'), secondary: true, cell: started },
    { id: 'duration', header: live ? t('an.run.age') : t('an.run.duration'), align: 'end', cell: (row) => formatDuration(row.duration, locale) },
  ];
  return (
    <section className="card analytics__panel" aria-label={t('an.panel.label', { name })}>
      <header className="card__header">
        <h3 className="card__title" ref={heading} tabIndex={-1}>
          {name}
        </h3>
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
  const candidates = useMemo(
    () => activities.filter((activity) => activity.durations.total > 0 && isBottleneckCandidate(activity.activityType)),
    [activities],
  );
  // the share is of all candidates, not only of those shown
  const all = candidates.reduce((sum, activity) => sum + activity.durations.total, 0);
  const ranked = useMemo(() => [...candidates].sort((a, b) => b.durations.total - a.durations.total).slice(0, 15), [candidates]);
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 });
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
        const share = shareOf(row.durations.total, all) ?? 0;
        return (
          <span className="analytics__share" title={formatDuration(row.durations.total, locale)}>
            <span className="analytics__share-bar" style={{ width: `${Math.round(share * 100)}%` }} />
            <span className="analytics__share-text">{percent.format(share)}</span>
          </span>
        );
      },
    },
    { id: 'runs', header: t('an.bottleneck.runs'), align: 'end', secondary: true, cell: (row) => formatNumber(row.finished, locale) },
    { id: 'p50', header: 'P50', align: 'end', cell: (row) => formatDuration(row.durations.p50, locale) },
    { id: 'p90', header: 'P90', align: 'end', cell: (row) => formatDuration(row.durations.p90, locale) },
    {
      id: 'above',
      header: t('an.bottleneck.above'),
      align: 'end',
      secondary: true,
      cell: (row) => {
        const share = shareOf(row.timeAboveMedian, row.durations.total);
        return share === null ? '—' : <span title={formatDuration(row.timeAboveMedian, locale)}>{percent.format(share)}</span>;
      },
    },
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

/** Median and P90 per period as bars, with a dashed target line. */
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
  const label = (period: PeriodAnalytics) => {
    const start = parseEngineDate(period.start);
    return start ? format.format(start) : '';
  };
  const describe = (period: PeriodAnalytics) =>
    t('an.trend.tooltip', {
      completed: period.durations.count,
      started: period.started,
      p50: formatDuration(period.durations.p50, locale),
      p90: formatDuration(period.durations.p90, locale),
    });
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
        <>
          <div className="analytics__trend" aria-hidden>
            {target ? <span className="analytics__trend-target" style={{ bottom: `${(target / max) * 100}%` }} /> : null}
            {trend.map((period) => (
              <div key={period.start} className="analytics__trend-period" title={`${label(period)}: ${describe(period)}`}>
                <span className="analytics__trend-p90" style={{ height: `${((period.durations.p90 ?? 0) / max) * 100}%` }} />
                <span className="analytics__trend-p50" style={{ height: `${((period.durations.p50 ?? 0) / max) * 100}%` }} />
              </div>
            ))}
          </div>
          <p className="analytics__trend-legend">
            <span className="analytics__key analytics__key--p50" /> P50 <span className="analytics__key analytics__key--p90" /> P90
            {target ? (
              <>
                <span className="analytics__key analytics__key--target" /> {t('an.target')} {formatDuration(target, locale)}
              </>
            ) : null}
            <span className="analytics__trend-scale">↑ {formatDuration(max, locale)}</span>
            {first && last && (
              <span className="analytics__trend-range">
                {label(first)} – {label(last)}
              </span>
            )}
          </p>
          <div className="sr-only">
            <table>
              <caption>{t('an.trend.title')}</caption>
              <tbody>
                {trend.map((period) => (
                  <tr key={period.start}>
                    <th scope="row">{label(period)}</th>
                    <td>{describe(period)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}

function UserTasks({ data, error, names }: { data: UserTaskAnalytics | undefined; error: unknown; names: Map<string, string> }) {
  const { t, locale } = useI18n();
  const [view, setView] = useState<'task' | 'assignee'>('task');
  if (error) return <ErrorState error={error} />;
  if (!data) return <Loading label={t('state.loading')} />;
  if (data.tasks === 0) return null;
  const rows = view === 'task' ? data.byTask : data.byAssignee;
  const percent = new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 });
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
        const share = shareOf(waiting, waiting + working);
        if (share === null) return '—';
        const waitingPercent = Math.round(share * 100);
        return (
          <span className="analytics__split" role="img" aria-label={t('an.tasks.splitTitle', { waiting: waitingPercent, working: 100 - waitingPercent })}>
            <span className="analytics__split-waiting" style={{ width: `${share * 100}%` }} />
            <span className="analytics__split-working" style={{ width: `${(1 - share) * 100}%` }} />
          </span>
        );
      },
    },
    { id: 'waitP50', header: t('an.tasks.waitP50'), align: 'end', cell: (row) => formatDuration(row.waiting.p50, locale) },
    { id: 'waitP90', header: t('an.tasks.waitP90'), align: 'end', secondary: true, cell: (row) => formatDuration(row.waiting.p90, locale) },
    { id: 'workP50', header: t('an.tasks.workP50'), align: 'end', cell: (row) => formatDuration(row.working.p50, locale) },
    { id: 'workP90', header: t('an.tasks.workP90'), align: 'end', secondary: true, cell: (row) => formatDuration(row.working.p90, locale) },
    {
      id: 'unknown',
      header: t('an.tasks.unknown'),
      align: 'end',
      secondary: true,
      cell: (row) => (row.unknownWaiting > 0 ? percent.format(row.unknownWaiting / Math.max(1, row.count)) : '—'),
    },
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
