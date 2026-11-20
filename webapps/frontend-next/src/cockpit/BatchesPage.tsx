/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { AlertTriangle, History, Layers, Pause, Play, Trash2 } from 'lucide-react';
import {
  deleteBatch,
  deleteFinishedBatch,
  getFinishedBatches,
  getRunningBatches,
  setBatchSuspended,
  type Batch,
  type FinishedBatch,
} from '../api/cockpit';
import { useI18n, type MessageKey } from '../i18n';
import { Button, EmptyState, IconButton } from '../ui';
import { Progress } from './charts';
import { COCKPIT, StateBadge, useCockpitAction, useRefreshInterval } from './common';
import { Checkbox, ConfirmDialog } from '../ui/controls';
import { Id, Time, formatNumber } from '../ui/format';
import { pageOf, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { FailedJobsDialog } from './BatchFailedJobs';

function DeleteBatchDialog({ batch, onClose }: { batch: Batch; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [cascade, setCascade] = useState(false);
  const [busy, setBusy] = useState(false);
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.batch.delete.title')}
      text={t('cockpit.batch.delete.text', { type: batch.type, id: batch.id, remaining: batch.remainingJobs })}
      confirmLabel={t('cockpit.batch.delete.confirm')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(() => deleteBatch(batch.id, cascade), {
          success: t('cockpit.batch.delete.done'),
          failure: t('cockpit.batch.delete.failed'),
        }).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    >
      <Checkbox
        label={t('cockpit.batch.delete.cascade')}
        hint={t('cockpit.batch.delete.cascadeHint')}
        checked={cascade}
        onChange={setCascade}
      />
    </ConfirmDialog>
  );
}

/** Batches: operations on many instances that the engine works off in the background. */
const BATCH_KINDS = new Set([
  'instance-migration',
  'instance-modification',
  'instance-restart',
  'instance-deletion',
  'instance-update-suspension-state',
  'set-job-retries',
  'set-external-task-retries',
  'historic-instance-deletion',
  'historic-decision-instance-deletion',
  'process-set-removal-time',
  'decision-set-removal-time',
  'batch-set-removal-time',
  'set-variables',
  'correlate-message',
]);

/** What a batch does in words; the type of the engine stays readable below for other kinds. */
function BatchType({ type }: { type: string }) {
  const { t } = useI18n();
  if (!BATCH_KINDS.has(type)) return <span className="mono">{type}</span>;
  return (
    <span title={type}>
      {t(`cockpit.batch.kind.${type}` as MessageKey)}
    </span>
  );
}

export function BatchesPage() {
  const { t, locale } = useI18n();
  const run = useCockpitAction();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const page = pageOf(params);
  const [failedOf, setFailedOf] = useState<Batch | null>(null);
  const [deleted, setDeleted] = useState<Batch | null>(null);
  const [removedHistory, setRemovedHistory] = useState<FinishedBatch | null>(null);
  const [removing, setRemoving] = useState(false);

  const running = useQuery({
    queryKey: [COCKPIT, 'batches', 'running'],
    queryFn: ({ signal }) => getRunningBatches(signal),
    refetchInterval,
  });
  const finished = useQuery({
    queryKey: [COCKPIT, 'batches', 'finished', page],
    queryFn: ({ signal }) =>
      getFinishedBatches({ firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const runningColumns = useMemo<Column<Batch>[]>(
    () => [
      {
        id: 'type',
        header: t('cockpit.batch.type'),
        cell: (batch) => (
          <div className="incident">
            <BatchType type={batch.type} />
            <Id value={batch.id} />
          </div>
        ),
      },
      {
        id: 'progress',
        header: t('cockpit.batch.progress'),
        cell: (batch) => {
          const label = t('cockpit.batch.progressOf', {
            done: formatNumber(batch.completedJobs, locale),
            total: formatNumber(batch.totalJobs, locale),
          });
          return (
            <div className="batch-progress">
              <Progress
                done={batch.completedJobs}
                failed={batch.failedJobs}
                total={batch.totalJobs}
                label={label}
              />
              <span className="batch-progress__text">{label}</span>
            </div>
          );
        },
      },
      {
        id: 'failed',
        header: t('cockpit.batch.failed'),
        align: 'end',
        cell: (batch) =>
          batch.failedJobs > 0 ? (
            <button type="button" className="badge badge--danger badge--button" onClick={() => setFailedOf(batch)}>
              <AlertTriangle aria-hidden size={12} />
              {t('cockpit.job.failedCount', { count: batch.failedJobs })}
            </button>
          ) : (
            <span className="muted">0</span>
          ),
      },
      {
        id: 'start',
        header: t('cockpit.instance.started'),
        secondary: true,
        cell: (batch) => <Time value={batch.startTime} />,
      },
      {
        id: 'user',
        header: t('cockpit.user'),
        secondary: true,
        cell: (batch) => batch.createUserId ?? <span className="muted">—</span>,
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        secondary: true,
        cell: (batch) => <StateBadge suspended={batch.suspended} />,
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (batch) => (
          <span className="row-actions">
            <Button
              size="sm"
              icon={batch.suspended ? Play : Pause}
              aria-label={t('a11y.rowAction', { action: batch.suspended ? t('cockpit.activate') : t('cockpit.suspend'), row: batch.type ?? batch.id })}
              onClick={() =>
                void run(() => setBatchSuspended(batch.id, !batch.suspended), {
                  success: batch.suspended ? t('cockpit.batch.activated') : t('cockpit.batch.suspended'),
                  failure: t('cockpit.batch.stateFailed'),
                })
              }
            >
              {batch.suspended ? t('cockpit.activate') : t('cockpit.suspend')}
            </Button>
            <IconButton
              className="icon-btn--sm"
              icon={Trash2}
              label={t('cockpit.batch.delete.title')}
              onClick={() => setDeleted(batch)}
            />
          </span>
        ),
      },
    ],
    [t, locale, run],
  );

  const finishedColumns = useMemo<Column<FinishedBatch>[]>(
    () => [
      {
        id: 'type',
        header: t('cockpit.batch.type'),
        cell: (batch) => (
          <div className="incident">
            <BatchType type={batch.type} />
            <Id value={batch.id} />
          </div>
        ),
      },
      {
        id: 'jobs',
        header: t('cockpit.batch.jobs'),
        align: 'end',
        cell: (batch) => formatNumber(batch.totalJobs, locale),
      },
      {
        id: 'start',
        header: t('cockpit.instance.started'),
        secondary: true,
        cell: (batch) => <Time value={batch.startTime} />,
      },
      { id: 'end', header: t('cockpit.batch.ended'), cell: (batch) => <Time value={batch.endTime} /> },
      {
        id: 'user',
        header: t('cockpit.user'),
        secondary: true,
        cell: (batch) => batch.createUserId ?? <span className="muted">—</span>,
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (batch) => (
          <IconButton
            className="icon-btn--sm"
            icon={Trash2}
            label={t('a11y.rowAction', { action: t('cockpit.batch.history.delete'), row: batch.id })}
            onClick={() => setRemovedHistory(batch)}
          />
        ),
      },
    ],
    [t, locale],
  );

  return (
    <div className="page">
      <PageHeader title={t('cockpit.batch.title')} subtitle={t('cockpit.batch.subtitle')} />
      <Card flush title={t('cockpit.batch.running')}>
        <DataTable
          label={t('cockpit.batch.running')}
          columns={runningColumns}
          rows={running.data}
          rowKey={(batch) => batch.id}
          error={running.error}
          onRetry={() => void running.refetch()}
          empty={
            <EmptyState icon={Layers} title={t('cockpit.batch.runningEmpty')}>
              {t('cockpit.batch.runningEmptyHint')}
            </EmptyState>
          }
        />
      </Card>
      <Card flush title={t('cockpit.batch.finished')}>
        {finished.data === null ? (
          <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
            {t('cockpit.history.unavailableHint')}
          </EmptyState>
        ) : (
          <>
            <DataTable
              label={t('cockpit.batch.finished')}
              columns={finishedColumns}
              updating={finished.isPlaceholderData}
              rows={finished.data?.items}
              rowKey={(batch) => batch.id}
              error={finished.error}
              onRetry={() => void finished.refetch()}
              empty={<EmptyState icon={History} title={t('cockpit.batch.finishedEmpty')} />}
            />
            <Pager
              page={page}
              count={finished.data?.count ?? 0}
              onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
            />
          </>
        )}
      </Card>

      {failedOf && <FailedJobsDialog batch={failedOf} onClose={() => setFailedOf(null)} />}
      {deleted && <DeleteBatchDialog key={deleted.id} batch={deleted} onClose={() => setDeleted(null)} />}
      <ConfirmDialog
        open={!!removedHistory}
        onOpenChange={(open) => !open && setRemovedHistory(null)}
        title={t('cockpit.batch.history.delete')}
        text={t('cockpit.batch.history.deleteText', {
          type: removedHistory?.type ?? '',
          id: removedHistory?.id ?? '',
        })}
        confirmLabel={t('cockpit.delete')}
        danger
        busy={removing}
        onConfirm={() => {
          if (!removedHistory) return;
          setRemoving(true);
          void run(() => deleteFinishedBatch(removedHistory.id), {
            success: t('cockpit.batch.history.deleted'),
            failure: t('cockpit.batch.delete.failed'),
          }).then((ok) => {
            setRemoving(false);
            if (ok) setRemovedHistory(null);
          });
        }}
      />
    </div>
  );
}
