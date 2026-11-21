/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ComponentType, ReactNode } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CircleCheck,
  Minus,
  GitBranch,
  ListChecks,
  PackageOpen,
  Table2,
  Users,
  Workflow,
} from 'lucide-react';
import type { LucideProps } from 'lucide-react';
import {
  getDashboardNumbers,
  getDefinitionNumbers,
  getDefinitionStatistics,
  getTasksByGroup,
  taskAssignment,
  type DefinitionStatistics,
} from '../api/cockpit';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { Button, EmptyState, ErrorState, Loading, Skeleton, cx } from '../ui';
import { BarList, PartsBar } from './charts';
import { HistoryReports } from './Reports';
import { COCKPIT, IncidentBadge, useRefreshInterval } from './common';
import { Time, formatNumber } from '../ui/format';
import { useRemembered } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { TREND_RANGES, getAttention, getTrends, trendPeriods, type Trend, type TrendRange } from '../api/cockpit';
import { toDateTimeInput } from '../lib/dates';
import { useTaskTotals } from './taskTotals';

const TOP = 8;

export function Tile({
  to,
  icon: Icon,
  label,
  value,
  tone,
  note,
}: {
  to: string;
  icon: ComponentType<LucideProps>;
  label: string;
  value: number | undefined;
  tone?: 'danger' | 'success';
  note?: string;
}) {
  const { locale } = useI18n();
  return (
    <Link to={to} className={cx('tile', tone && `tile--${tone}`)}>
      <span className="tile__label">
        <Icon aria-hidden size={15} />
        {label}
      </span>
      <span className="tile__value">
        {value === undefined ? <span className="tile__pending" aria-hidden /> : formatNumber(value, locale)}
      </span>
      {note && <span className="tile__note">{note}</span>}
      <ArrowRight className="tile__go" aria-hidden size={16} />
    </Link>
  );
}

const definitionLabel = (definition: DefinitionStatistics) => definition.name ?? definition.key;

function definitionBars(definitions: DefinitionStatistics[], measure: 'instances' | 'incidents') {
  return definitions
    .filter((definition) => definition[measure] > 0)
    .map((definition) => ({
      key: definition.id,
      name: definitionLabel(definition),
      label: (
        <Link className="bar-list__link" to={`/cockpit/processes/definition/${encodeURIComponent(definition.id)}`}>
          {definitionLabel(definition)}
        </Link>
      ),
      value: definition[measure],
      extra: measure === 'instances' ? <IncidentBadge count={definition.incidents} /> : undefined,
    }));
}

/**
 * What needs someone now, before the totals: the oldest open incident,
 * jobs out of retries, batches with failures and overdue tasks. Each line
 * opens the list where it can be handled.
 */
function Attention({ tasklist }: { tasklist: boolean }) {
  const { t } = useI18n();
  const refetchInterval = useRefreshInterval();
  const attention = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'attention'],
    queryFn: ({ signal }) => getAttention(signal),
    refetchInterval,
  });
  // the overdue tasks come with the totals the page reads anyway
  const totals = useTaskTotals();
  const overdueTasks = totals.data?.overdue;
  const data = attention.data;
  // overdue tasks are often the only item: "nothing needs attention" waits for their count
  const overduePending = tasklist && totals.isPending;
  if (!data || (overduePending && !attention.isError)) {
    // a check that failed is no sign that nothing needs attention
    return attention.isError ? (
      <div className="table-stale" role="alert">
        <span>{t('attention.failed')}</span>
        <Button size="sm" variant="ghost" onClick={() => void attention.refetch()}>
          {t('action.retry')}
        </Button>
      </div>
    ) : null;
  }
  const items: { key: string; to: string; text: ReactNode }[] = [];
  if (data.oldestIncident) {
    items.push({
      key: 'incident',
      to: '/cockpit/incidents?group=1',
      text: (
        <>
          {t('attention.incident')} <Time value={data.oldestIncident.time} />
          {data.oldestIncident.message && <span className="attention__detail"> · {data.oldestIncident.message}</span>}
        </>
      ),
    });
  }
  if (data.failedJobs) {
    items.push({ key: 'jobs', to: '/cockpit/jobs', text: t('attention.jobs', { count: data.failedJobs }) });
  }
  if (data.failingBatches) {
    items.push({ key: 'batches', to: '/cockpit/batches', text: t('attention.batches', { count: data.failingBatches }) });
  }
  if (overdueTasks && tasklist) {
    items.push({ key: 'tasks', to: '/tasklist', text: t('attention.tasks', { count: overdueTasks }) });
  }
  // a part that could not be read is not a part without problems
  const partial =
    (totals.isError && overdueTasks === undefined) || data.failedJobs === null || data.failingBatches === null || data.incidentsUnknown;
  // a failed refresh leaves the last answer, which may be out of date
  const stale = attention.isError || (totals.isError && overdueTasks !== undefined);
  const retry = () => {
    void attention.refetch();
    void totals.refetch();
  };
  const calm = items.length === 0 && !partial && !stale;
  return (
    <section className={cx('attention', calm && 'attention--calm')} aria-labelledby="attention-title">
      <h2 id="attention-title" className="attention__title">
        {calm ? <CircleCheck aria-hidden size={16} /> : <AlertTriangle aria-hidden size={16} />}
        {calm ? t('attention.none') : t('attention.title')}
      </h2>
      {stale && (
        <div className="table-stale" role="alert">
          <span>{t('attention.failed')}</span>
          <Button size="sm" variant="ghost" onClick={retry}>
            {t('action.retry')}
          </Button>
        </div>
      )}
      {partial && !stale && (
        <p className="attention__detail">
          {t('attention.partial')}{' '}
          <Button size="sm" variant="ghost" onClick={retry}>
            {t('action.retry')}
          </Button>
        </p>
      )}
      {items.length > 0 && (
        <ul className="attention__list">
          {items.map((item) => (
            <li key={item.key}>
              <Link className="attention__item" to={item.to}>
                <span className="attention__text">{item.text}</span>
                <ArrowRight aria-hidden size={14} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const TREND_LABELS = {
  started: 'trends.started',
  completed: 'trends.completed',
  incidents: 'trends.incidents',
  tasksDone: 'trends.tasksDone',
} as const;

/** Where a measure leads: the list that shows what was counted, from the start of the range. */
function trendLink(measure: Trend['measure'], from: Date): string | null {
  const since = encodeURIComponent(toDateTimeInput(from));
  switch (measure) {
    case 'started':
      return `/cockpit/history?hsa=${since}`;
    case 'completed':
      return `/cockpit/history?hstate=completed&hfa=${since}`;
    case 'incidents':
      return '/cockpit/incidents';
    default:
      return null;
  }
}

/** The change against the period before, and whether it is good news: more incidents are not. */
export function trendChange(trend: Trend): { delta: number; percent: number | null; tone: 'good' | 'bad' | 'flat' } | null {
  if (trend.current === null || trend.previous === null) {
    return null;
  }
  const delta = trend.current - trend.previous;
  const percent = trend.previous > 0 ? Math.round((delta / trend.previous) * 100) : null;
  const better = trend.measure === 'incidents' ? delta < 0 : delta > 0;
  return { delta, percent, tone: delta === 0 ? 'flat' : better ? 'good' : 'bad' };
}

/** How the work went in a chosen range, each number beside the one of the range before. */
function Trends() {
  const { t, locale } = useI18n();
  // counts over days of history: they hardly move within minutes and cost the most to read
  const refetchInterval = useRefreshInterval('slow');
  const [range, setRange] = useRemembered<TrendRange>('dashboard.range', '7d');
  const current = TREND_RANGES.includes(range) ? range : '7d';
  const trends = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'trends', current],
    queryFn: async ({ signal }) => {
      const now = new Date();
      return { from: trendPeriods(current, now).from, items: await getTrends(current, now, signal) };
    },
    refetchInterval,
  });
  const unreadable = trends.data?.items.every((trend) => trend.current === null);
  return (
    <Card
      title={t('trends.title')}
      label={t('trends.title')}
      action={
        <div className="segmented" role="group" aria-label={t('trends.range')}>
          {TREND_RANGES.map((value) => (
            <button
              key={value}
              type="button"
              className="segmented__item"
              aria-pressed={value === current}
              onClick={() => setRange(value)}
            >
              {t(`trends.range.${value}`)}
            </button>
          ))}
        </div>
      }
    >
      {trends.isError && !trends.data ? (
        <ErrorState error={trends.error} onRetry={() => void trends.refetch()} />
      ) : !trends.data ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={2} />
        </Loading>
      ) : unreadable ? (
        <EmptyState title={t('cockpit.history.unavailable')}>{t('cockpit.history.unavailableHint')}</EmptyState>
      ) : (
        <ul className="trends" aria-live="polite">
          {trends.data.items.map((trend) => {
            const change = trendChange(trend);
            const Icon = !change || change.tone === 'flat' ? Minus : change.delta > 0 ? ArrowUpRight : ArrowDownRight;
            const to = trendLink(trend.measure, trends.data.from);
            const content = (
              <>
                  <span className="trend__label">{t(TREND_LABELS[trend.measure])}</span>
                  <span className="trend__value">{trend.current === null ? '–' : formatNumber(trend.current, locale)}</span>
                  <span className={cx('trend__change', change && `trend__change--${change.tone}`)}>
                    <Icon aria-hidden size={14} />
                    {change
                      ? t('trends.change', {
                          delta: `${change.delta > 0 ? '+' : ''}${formatNumber(change.delta, locale)}`,
                          percent: change.percent === null ? '' : ` (${change.percent > 0 ? '+' : ''}${change.percent}%)`,
                          previous: formatNumber(trend.previous ?? 0, locale),
                        })
                      : t('trends.noPrevious')}
                  </span>
              </>
            );
            return (
              <li key={trend.measure}>
                {to ? (
                  <Link className="trend" to={to}>
                    {content}
                  </Link>
                ) : (
                  <div className="trend">{content}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

/** The state of the engine at a glance, with links into the lists. */
export function DashboardPage() {
  const { t } = useI18n();
  const session = useSession();
  const refetchInterval = useRefreshInterval();
  const numbers = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'numbers'],
    queryFn: ({ signal }) => getDashboardNumbers(signal),
    refetchInterval,
  });
  // definitions and deployments only change with a deployment
  const slowInterval = useRefreshInterval('slow');
  const definitions = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'definitions'],
    queryFn: ({ signal }) => getDefinitionNumbers(signal),
    staleTime: 60_000,
    refetchInterval: slowInterval,
  });
  const totals = useTaskTotals();
  const assignment = totals.data ? taskAssignment(totals.data) : undefined;
  const busiest = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'busiest'],
    queryFn: ({ signal }) =>
      getDefinitionStatistics({
        sorting: { sortBy: 'instances', sortOrder: 'desc' },
        paging: { firstResult: 0, maxResults: TOP },
        // a top list: how many processes there are in all is not shown
        total: false,
        signal,
      }),
    refetchInterval,
  });
  const failing = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'failing'],
    queryFn: ({ signal }) =>
      getDefinitionStatistics({
        sorting: { sortBy: 'incidents', sortOrder: 'desc' },
        paging: { firstResult: 0, maxResults: TOP },
        total: false,
        signal,
      }),
    refetchInterval,
  });
  const byGroup = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'tasks-by-group'],
    queryFn: ({ signal }) => getTasksByGroup(signal),
    refetchInterval,
  });

  const data = numbers.data;
  const groupBars = byGroup.data
    ?.filter((entry) => entry.taskCount > 0)
    .sort((a, b) => b.taskCount - a.taskCount)
    .map((entry) => {
      const name = entry.groupName ?? t('cockpit.dashboard.noGroup');
      return {
        key: entry.groupName === null ? 'none' : `group:${entry.groupName}`,
        name,
        label: entry.groupName ? <span className="mono">{name}</span> : <span className="muted">{name}</span>,
        value: entry.taskCount,
      };
    });
  const instanceBars = busiest.data ? definitionBars(busiest.data.items, 'instances') : undefined;
  const incidentBars = failing.data ? definitionBars(failing.data.items, 'incidents') : undefined;

  return (
    <div className="page">
      <PageHeader title={t('cockpit.dashboard.title')} subtitle={t('cockpit.dashboard.subtitle')} />
      <Attention tasklist={session.authorizedApps.includes('tasklist')} />
      <Trends />
      {numbers.isError && !data ? (
        <Card>
          <ErrorState error={numbers.error} onRetry={() => void numbers.refetch()} />
        </Card>
      ) : (
        <div className="tiles">
          <Tile
            to="/cockpit/processes"
            icon={Workflow}
            label={t('cockpit.dashboard.instances')}
            value={data?.instances}
          />
          <Tile
            to="/cockpit/incidents"
            icon={data?.incidents === 0 ? CircleCheck : AlertTriangle}
            label={t('cockpit.dashboard.incidents')}
            value={data?.incidents}
            tone={data ? (data.incidents > 0 ? 'danger' : 'success') : undefined}
            note={
              data
                ? data.incidents > 0
                  ? t('cockpit.dashboard.incidentsOpen')
                  : t('cockpit.dashboard.incidentsNone')
                : undefined
            }
          />
          <Tile
            to={session.authorizedApps.includes('tasklist') ? '/tasklist' : '/cockpit/processes'}
            icon={ListChecks}
            label={t('cockpit.dashboard.tasks')}
            value={totals.data?.open}
          />
          <Tile
            to="/cockpit/processes"
            icon={GitBranch}
            label={t('cockpit.dashboard.processDefinitions')}
            value={definitions.data?.processDefinitions}
          />
          <Tile
            to="/cockpit/decisions"
            icon={Table2}
            label={t('cockpit.dashboard.decisionDefinitions')}
            value={definitions.data?.decisionDefinitions}
          />
          <Tile
            to="/cockpit/deployments"
            icon={PackageOpen}
            label={t('cockpit.dashboard.deployments')}
            value={definitions.data?.deployments}
          />
        </div>
      )}
      {/* the tiles of the totals and of the definitions are read apart from the numbers above */}
      {!(numbers.isError && !data) && ((totals.isError && !totals.data) || (definitions.isError && !definitions.data)) && (
        <Card>
          <ErrorState
            error={totals.isError && !totals.data ? totals.error : definitions.error}
            onRetry={() => {
              if (totals.isError) void totals.refetch();
              if (definitions.isError) void definitions.refetch();
            }}
          />
        </Card>
      )}

      <div className="dashboard">
        <Card
          title={t('cockpit.dashboard.busiest')}
          action={
            <Link className="link link--subtle" to="/cockpit/processes">
              {t('cockpit.dashboard.allProcesses')}
            </Link>
          }
        >
          {busiest.isError && !instanceBars ? (
            <ErrorState error={busiest.error} onRetry={() => void busiest.refetch()} />
          ) : !instanceBars ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={5} />
            </Loading>
          ) : instanceBars.length === 0 ? (
            <EmptyState icon={Workflow} title={t('cockpit.dashboard.noInstances')} />
          ) : (
            <BarList
              items={instanceBars}
              unit={(count) => t('cockpit.instances.count', { count })}
            />
          )}
        </Card>

        <Card
          title={t('cockpit.dashboard.failing')}
          action={
            <Link className="link link--subtle" to="/cockpit/incidents">
              {t('cockpit.dashboard.allIncidents')}
            </Link>
          }
        >
          {failing.isError && !incidentBars ? (
            <ErrorState error={failing.error} onRetry={() => void failing.refetch()} />
          ) : !incidentBars ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={5} />
            </Loading>
          ) : incidentBars.length === 0 ? (
            <EmptyState icon={CircleCheck} title={t('cockpit.dashboard.incidentsNone')}>
              {t('cockpit.dashboard.incidentsNoneHint')}
            </EmptyState>
          ) : (
            <BarList
              items={incidentBars}
              tone="danger"
              unit={(count) => t('cockpit.incidents.count', { count })}
            />
          )}
        </Card>

        <Card title={t('cockpit.dashboard.assignment')} className="dashboard__wide">
          {totals.isError && !assignment ? (
            <ErrorState error={totals.error} onRetry={() => void totals.refetch()} />
          ) : !assignment ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={3} />
            </Loading>
          ) : assignment.assigned + assignment.withGroups + assignment.unassigned === 0 ? (
            <EmptyState icon={ListChecks} title={t('cockpit.dashboard.noTasks')} />
          ) : (
            <PartsBar
              label={t('cockpit.dashboard.assignment')}
              parts={[
                {
                  key: 'assigned',
                  label: t('cockpit.dashboard.tasksAssigned'),
                  value: assignment.assigned,
                  series: 1,
                },
                {
                  key: 'groups',
                  label: t('cockpit.dashboard.tasksWithGroups'),
                  value: assignment.withGroups,
                  series: 2,
                },
                {
                  key: 'unassigned',
                  label: t('cockpit.dashboard.tasksUnassigned'),
                  value: assignment.unassigned,
                  series: 3,
                },
              ]}
            />
          )}
        </Card>

        <Card title={t('cockpit.dashboard.byGroup')} className="dashboard__wide">
          {byGroup.isError && !groupBars ? (
            <ErrorState error={byGroup.error} onRetry={() => void byGroup.refetch()} />
          ) : !groupBars ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={4} />
            </Loading>
          ) : groupBars.length === 0 ? (
            <EmptyState icon={Users} title={t('cockpit.dashboard.byGroupEmpty')} />
          ) : (
            <>
              <p className="muted card__hint">{t('cockpit.dashboard.byGroupHint')}</p>
              <BarList items={groupBars} unit={(count) => t('cockpit.dashboard.tasksCount', { count })} />
            </>
          )}
        </Card>

        <HistoryReports />
      </div>
    </div>
  );
}
