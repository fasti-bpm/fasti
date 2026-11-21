/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { usePacedInterval } from '../lib/activity';
import { Link } from 'react-router';
import { CircleAlert, CircleCheck, CircleHelp, TriangleAlert } from 'lucide-react';
import { EXECUTION_METRICS, getDiagnostics, getExecutionMetrics, getHealth, type ExecutionMetric, type Health } from '../api/admin';
import { getEngines } from '../api/engine';
import { env } from '../env';
import { useI18n, type MessageKey, type Translate } from '../i18n';
import { Button, ErrorState, Loading, Notice, Skeleton } from '../ui';
import { Time, formatNumber } from '../ui/format';
import { Card, PageHeader } from '../ui/page';
import { ADMIN } from './common';

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="admin-fact">
      <dt>{label}</dt>
      <dd>{children || <span className="muted">—</span>}</dd>
    </div>
  );
}

function vendorAndVersion(value: { vendor?: string; version?: string } | null | undefined): string {
  const vendor = value?.vendor ?? '';
  const version = value?.version ?? '';
  // servers often put their name into the version as well, e.g. "Apache Tomcat/11.0.27"
  if (vendor && version.toLowerCase().startsWith(vendor.toLowerCase())) return version;
  return [vendor, version].filter(Boolean).join(' ');
}

const INTEGRATIONS: Record<string, MessageKey> = {
  'spring-boot-starter': 'admin.system.integration.springBoot',
  'camunda-bpm-run': 'admin.system.integration.run',
  'wildfly-subsystem': 'admin.system.integration.wildfly',
  'jboss-subsystem': 'admin.system.integration.wildfly',
  'camunda-ejb-client': 'admin.system.integration.ejb',
};

/** A readable name for an integration the engine reports, e.g. the Spring Boot starter. */
function integrationLabel(value: string, t: Translate): string {
  const key = INTEGRATIONS[value];
  return key ? t(key) : value;
}

const METRIC_LABELS: Record<ExecutionMetric, MessageKey> = {
  'root-process-instance-start': 'admin.metrics.processInstances',
  'activity-instance-start': 'admin.metrics.activityInstances',
  'executed-decision-instances': 'admin.metrics.decisionInstances',
  'executed-decision-elements': 'admin.metrics.decisionElements',
  'task-users': 'admin.metrics.taskUsers',
};

type Period = 'month' | 'lastMonth' | 'year' | 'twelveMonths';
const PERIODS: Period[] = ['month', 'lastMonth', 'year', 'twelveMonths'];

/** Start and end of a period, counted in the time zone of the browser. */
function periodRange(period: Period, now: Date = new Date()): [Date, Date] {
  const year = now.getFullYear();
  const month = now.getMonth();
  switch (period) {
    case 'lastMonth':
      return [new Date(year, month - 1, 1), new Date(year, month, 1)];
    case 'year':
      return [new Date(year, 0, 1), now];
    case 'twelveMonths':
      return [new Date(year, month - 11, 1), now];
    default:
      return [new Date(year, month, 1), now];
  }
}

/** How much the engine ran in a period: read-only, from the metrics the engine records. */
function ExecutionMetrics() {
  const { t, locale } = useI18n();
  const [period, setPeriod] = useState<Period>('month');
  const metrics = useQuery({
    queryKey: [ADMIN, 'metrics', period],
    queryFn: ({ signal }) => {
      const [start, end] = periodRange(period);
      return getExecutionMetrics(start, end, signal);
    },
  });
  return (
    <Card title={t('admin.metrics.title')}>
      <div className="toolbar">
        <select
          className="input toolbar__select"
          aria-label={t('admin.metrics.period')}
          value={period}
          onChange={(event) => setPeriod(event.target.value as Period)}
        >
          {PERIODS.map((value) => (
            <option key={value} value={value}>
              {t(`admin.metrics.period.${value}`)}
            </option>
          ))}
        </select>
      </div>
      {metrics.isError ? (
        <ErrorState error={metrics.error} onRetry={() => void metrics.refetch()} />
      ) : metrics.isPending ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={5} />
        </Loading>
      ) : (
        <dl className="admin-facts">
          {EXECUTION_METRICS.map((name) => {
            const value = metrics.data[name];
            return (
              <Fact key={name} label={t(METRIC_LABELS[name])}>
                {value === null ? '' : formatNumber(value, locale)}
              </Fact>
            );
          })}
        </dl>
      )}
      <p className="muted admin-note">{t('admin.metrics.hint')}</p>
    </Card>
  );
}

type Level = 'ok' | 'warn' | 'bad' | 'unknown';

interface Check {
  id: string;
  label: MessageKey;
  value: number | null;
  level: Level;
  hint: MessageKey;
  to: string;
}

const LEVEL_ICONS = { ok: CircleCheck, warn: TriangleAlert, bad: CircleAlert, unknown: CircleHelp } as const;

/** The checks of the health card in a fixed order; a count that could not be read is unknown. */
export function healthChecks(health: Health): Check[] {
  const check = (id: string, value: number | null, worst: 'warn' | 'bad', to: string, label: MessageKey, hint: MessageKey): Check => ({
    id,
    label,
    value,
    level: value === null ? 'unknown' : value > 0 ? worst : 'ok',
    hint,
    to,
  });
  return [
    check('incidents', health.incidents, 'warn', '/cockpit/incidents', 'admin.health.incidents', 'admin.health.incidentsHint'),
    check('failedJobs', health.failedJobs, 'bad', '/cockpit/jobs', 'admin.health.failedJobs', 'admin.health.failedJobsHint'),
    check('lateJobs', health.lateJobs, 'bad', '/cockpit/jobs', 'admin.health.lateJobs', 'admin.health.lateJobsHint'),
    check(
      'externalTasks',
      health.failedExternalTasks,
      'warn',
      '/cockpit/external-tasks',
      'admin.health.externalTasks',
      'admin.health.externalTasksHint',
    ),
    check('batches', health.failingBatches, 'warn', '/cockpit/batches', 'admin.health.batches', 'admin.health.batchesHint'),
  ];
}

/** The worst level of the checks: one problem makes the whole engine need attention. */
export function overallLevel(checks: { level: Level }[]): Level {
  for (const level of ['bad', 'warn'] as const) if (checks.some((check) => check.level === level)) return level;
  // a check that could not be read leaves the verdict open: calm only when every check was read
  return checks.length === 0 || checks.some((check) => check.level === 'unknown') ? 'unknown' : 'ok';
}

function useHealth() {
  return useQuery({
    queryKey: [ADMIN, 'health'],
    queryFn: ({ signal }) => getHealth(new Date(), signal),
    refetchInterval: usePacedInterval(60_000),
  });
}

/** Whether the engine runs well: open problems at a glance, each with the page that handles it. */
function HealthCard() {
  const { t, locale } = useI18n();
  const health = useHealth();
  const checks = health.data ? healthChecks(health.data) : [];
  const overall = overallLevel(checks);
  const OverallIcon = LEVEL_ICONS[overall];
  return (
    <Card
      title={t('admin.health.title')}
      action={
        <Button size="sm" variant="ghost" busy={health.isFetching} onClick={() => void health.refetch()}>
          {t('admin.health.check')}
        </Button>
      }
    >
      {health.isError && !health.data ? (
        <ErrorState error={health.error} onRetry={() => void health.refetch()} />
      ) : !health.data ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={5} />
        </Loading>
      ) : (
        <>
          {health.isError && <Notice tone="warning">{t('admin.health.stale')}</Notice>}
          <p className={`health-verdict health--${overall}`}>
            <OverallIcon aria-hidden size={20} />
            <span>
              <strong>{t(`admin.health.verdict.${overall}`)}</strong>{' '}
              <span className="muted">{t('admin.health.response', { ms: formatNumber(health.data.responseMs, locale) })}</span>
            </span>
          </p>
          <ul className="health-list" aria-label={t('admin.health.title')}>
            {checks.map((check) => {
              const Icon = LEVEL_ICONS[check.level];
              return (
                <li key={check.id} className={`health-item health--${check.level}`}>
                  <Icon aria-hidden size={16} />
                  <span className="health-item__text">
                    <Link to={check.to} className="health-item__label">
                      {t(check.label)}
                    </Link>
                    <span className="muted health-item__hint">{check.value === null ? t('admin.health.unknown') : t(check.hint)}</span>
                  </span>
                  <span className="health-item__value">
                    <span className="sr-only">{t(`admin.health.level.${check.level}`)}: </span>
                    {check.value === null ? '—' : formatNumber(check.value, locale)}
                  </span>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Card>
  );
}

/** Who and what the engine holds, each with the page that manages it. */
function OverviewCard() {
  const { t, locale } = useI18n();
  const health = useHealth();
  const data = health.data;
  if (!data) return null;
  const linked = (to: string, value: number | null, label: string) =>
    value === null ? (
      ''
    ) : (
      <Link to={to} aria-label={`${label}: ${formatNumber(value, locale)}`}>
        {formatNumber(value, locale)}
      </Link>
    );
  const latest = data.latestDeployment;
  return (
    <Card title={t('admin.overview.title')}>
      <dl className="admin-facts">
        <Fact label={t('admin.nav.users')}>{linked('/admin/users', data.users, t('admin.nav.users'))}</Fact>
        <Fact label={t('admin.nav.groups')}>{linked('/admin/groups', data.groups, t('admin.nav.groups'))}</Fact>
        <Fact label={t('admin.nav.tenants')}>{linked('/admin/tenants', data.tenants, t('admin.nav.tenants'))}</Fact>
        <Fact label={t('admin.nav.authorizations')}>
          {linked('/admin/authorizations', data.authorizations, t('admin.nav.authorizations'))}
        </Fact>
        <Fact label={t('admin.overview.deployments')}>
          {linked('/cockpit/deployments', data.deployments, t('admin.overview.deployments'))}
        </Fact>
        <Fact label={t('admin.overview.latestDeployment')}>
          {latest && (
            <>
              <Link to="/cockpit/deployments">{latest.name || latest.id}</Link>{' '}
              <span className="muted">
                <Time value={latest.deploymentTime} />
              </span>
            </>
          )}
        </Fact>
      </dl>
      <p className="muted admin-note">{t('admin.overview.hint')}</p>
    </Card>
  );
}

/** What the engine and its surroundings are: for support requests and upgrades. */

export function SystemPage() {
  const { t } = useI18n();
  const engines = useQuery({ queryKey: [ADMIN, 'engines'], queryFn: getEngines, staleTime: Infinity });
  const diagnostics = useQuery({ queryKey: [ADMIN, 'diagnostics'], queryFn: getDiagnostics });
  const product = diagnostics.data?.product;
  const internals = product?.internals;

  return (
    <div className="page">
      <PageHeader title={t('admin.system.title')} subtitle={t('admin.system.subtitle')} />
      <HealthCard />
      <OverviewCard />
      <Card title={t('admin.system.application')}>
        <dl className="admin-facts">
          <Fact label={t('admin.system.product')}>Fasti BPM</Fact>
          <Fact label={t('admin.system.uiVersion')}>{__APP_VERSION__}</Fact>
          <Fact label={t('admin.system.engine')}>{env.engine}</Fact>
          <Fact label={t('admin.system.engines')}>{engines.data?.map((engine) => engine.name).join(', ')}</Fact>
        </dl>
      </Card>
      <Card title={t('admin.system.environment')}>
        {diagnostics.isError ? (
          <ErrorState error={diagnostics.error} onRetry={() => void diagnostics.refetch()} />
        ) : diagnostics.isPending ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={5} />
          </Loading>
        ) : !diagnostics.data ? (
          <Notice>{t('admin.system.noDiagnostics')}</Notice>
        ) : (
          <dl className="admin-facts">
            <Fact label={t('admin.system.engineVersion')}>{product?.version}</Fact>
            <Fact label={t('admin.system.edition')}>{product?.edition}</Fact>
            <Fact label={t('admin.system.database')}>{vendorAndVersion(internals?.database)}</Fact>
            <Fact label={t('admin.system.server')}>{vendorAndVersion(internals?.['application-server'])}</Fact>
            <Fact label={t('admin.system.jdk')}>{vendorAndVersion(internals?.jdk)}</Fact>
            <Fact label={t('admin.system.webapps')}>{internals?.webapps?.join(', ')}</Fact>
            <Fact label={t('admin.system.integrations')}>
              {internals?.['camunda-integration']?.map((value) => integrationLabel(value, t)).join(', ')}
            </Fact>
            <Fact label={t('admin.system.installation')}>
              {diagnostics.data.installation && <span className="mono">{diagnostics.data.installation}</span>}
            </Fact>
          </dl>
        )}
        <p className="muted admin-note">{t('admin.system.privacy')}</p>
      </Card>
      <ExecutionMetrics />
    </div>
  );
}
