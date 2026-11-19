/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Eraser } from 'lucide-react';
import {
  batchWindow,
  getCleanableProcesses,
  getCleanupConfiguration,
  getCleanupJobs,
  runCleanupNow,
  type CleanableCount,
  type CleanupJob,
} from '../api/maintenance';
import { useI18n } from '../i18n';
import { Button, EmptyState, ErrorState, Loading, Notice, Skeleton } from '../ui';
import {
  COCKPIT,
  Card,
  ConfirmDialog,
  DataTable,
  Id,
  PageHeader,
  StateBadge,
  Time,
  formatNumber,
  useCockpitAction,
  useRefreshInterval,
  type Column,
} from './common';

/**
 * The history cleanup: when it may run, its jobs, what it may remove per
 * process, and a way to run it now.
 */
export function CleanupPage() {
  const { t, locale } = useI18n();
  const run = useCockpitAction();
  const refetchInterval = useRefreshInterval();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const config = useQuery({ queryKey: [COCKPIT, 'cleanup-config'], queryFn: getCleanupConfiguration });
  const jobs = useQuery({ queryKey: [COCKPIT, 'cleanup-jobs'], queryFn: getCleanupJobs, refetchInterval });
  const cleanable = useQuery({ queryKey: [COCKPIT, 'cleanable'], queryFn: getCleanableProcesses });
  const total = (cleanable.data ?? []).reduce((sum, entry) => sum + entry.cleanable, 0);
  const withoutTtl = (cleanable.data ?? []).filter((entry) => entry.historyTimeToLive === null).length;
  const time = (date: Date) => date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });

  const jobColumns = useMemo<Column<CleanupJob>[]>(
    () => [
      { id: 'id', header: t('cockpit.job.id'), cell: (job) => <Id value={job.id} /> },
      { id: 'due', header: t('cockpit.job.due'), cell: (job) => <Time value={job.dueDate} /> },
      { id: 'retries', header: t('cockpit.retry.retries'), align: 'end', cell: (job) => job.retries },
      {
        id: 'error',
        header: t('cockpit.job.error'),
        secondary: true,
        cell: (job) => job.exceptionMessage ?? <span className="muted">—</span>,
      },
      { id: 'state', header: t('cockpit.state'), cell: (job) => <StateBadge suspended={job.suspended} /> },
    ],
    [t],
  );
  const cleanableColumns = useMemo<Column<CleanableCount>[]>(
    () => [
      { id: 'name', header: t('cleanup.process'), cell: (entry) => entry.name ?? entry.key },
      { id: 'version', header: t('cockpit.definition.version'), align: 'end', secondary: true, cell: (entry) => entry.version },
      {
        id: 'ttl',
        header: t('cleanup.ttl'),
        align: 'end',
        cell: (entry) =>
          entry.historyTimeToLive === null ? (
            <span className="badge badge--warning">{t('cleanup.noTtl')}</span>
          ) : (
            t('cleanup.days', { count: entry.historyTimeToLive })
          ),
      },
      { id: 'finished', header: t('cleanup.finished'), align: 'end', cell: (entry) => formatNumber(entry.finished, locale) },
      { id: 'cleanable', header: t('cleanup.cleanable'), align: 'end', cell: (entry) => formatNumber(entry.cleanable, locale) },
    ],
    [t, locale],
  );

  const windowText = config.data ? batchWindow(config.data, time) : null;

  return (
    <div className="page">
      <PageHeader
        title={t('cleanup.title')}
        subtitle={t('cleanup.subtitle')}
        actions={
          <Button icon={Eraser} variant="primary" disabled={!config.data} onClick={() => setConfirming(true)}>
            {t('cleanup.runNow')}
          </Button>
        }
      />
      <Card title={t('cleanup.configuration')}>
        {config.isError ? (
          <ErrorState error={config.error} onRetry={() => void config.refetch()} />
        ) : !config.data ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={2} />
          </Loading>
        ) : (
          <dl className="facts">
            <div>
              <dt>{t('cleanup.enabled')}</dt>
              <dd>{config.data.enabled ? t('value.true') : t('value.false')}</dd>
            </div>
            <div>
              <dt>{t('cleanup.window')}</dt>
              <dd>{windowText ?? t('cleanup.noWindow')}</dd>
            </div>
          </dl>
        )}
        <Notice title={t('cleanup.removalTitle')}>{t('cleanup.removalText')}</Notice>
      </Card>
      <Card flush title={t('cleanup.jobs')}>
        <DataTable
          label={t('cleanup.jobs')}
          columns={jobColumns}
          rows={jobs.data}
          rowKey={(job) => job.id}
          error={jobs.error}
          onRetry={() => void jobs.refetch()}
          exportable={false}
          empty={<EmptyState title={t('cleanup.noJobs')}>{t('cleanup.noJobsHint')}</EmptyState>}
        />
      </Card>
      <Card flush title={t('cleanup.byProcess')}>
        {withoutTtl > 0 && (
          <div className="card__notice">
            <Notice tone="warning">{t('cleanup.withoutTtl', { count: withoutTtl })}</Notice>
          </div>
        )}
        <DataTable
          label={t('cleanup.byProcess')}
          columns={cleanableColumns}
          rows={cleanable.data}
          rowKey={(entry) => `${entry.key}:${entry.version}`}
          error={cleanable.error}
          onRetry={() => void cleanable.refetch()}
          empty={<EmptyState title={t('cleanup.nothing')} />}
        />
      </Card>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t('cleanup.runNow')}
        text={t('cleanup.confirm', { count: total })}
        confirmLabel={t('cleanup.runNow')}
        danger
        busy={busy}
        onConfirm={() => {
          setBusy(true);
          void run(runCleanupNow, { success: t('cleanup.started'), failure: t('cleanup.failed') }).then((ok) => {
            setBusy(false);
            if (ok) setConfirming(false);
          });
        }}
      />
    </div>
  );
}
