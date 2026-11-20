/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { getJobs, setJobDefinitionRetries, type Batch, type Job } from '../api/cockpit';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState } from '../ui';
import { COCKPIT, useCockpitAction, useRefreshInterval } from './common';
import { Id } from '../ui/format';
import { DataTable, type Column } from '../ui/table';
import { FailureDetailsDialog, RetryDialog } from './Incidents';

const FAILED_JOBS_SHOWN = 50;

/** The jobs of a batch that ran out of retries, each with its error, retried one by one or together. */
export function FailedJobsDialog({ batch, onClose }: { batch: Batch; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const refetchInterval = useRefreshInterval();
  const [details, setDetails] = useState<string | null>(null);
  const [retry, setRetry] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const jobs = useQuery({
    queryKey: [COCKPIT, 'batch-failed-jobs', batch.id],
    queryFn: () =>
      getJobs({
        jobDefinitionId: batch.batchJobDefinitionId ?? '',
        noRetriesLeft: true,
        maxResults: FAILED_JOBS_SHOWN,
      }),
    enabled: !!batch.batchJobDefinitionId,
    refetchInterval,
  });
  const columns = useMemo<Column<Job>[]>(
    () => [
      { id: 'id', header: t('cockpit.job.id'), cell: (job) => <Id value={job.id} /> },
      {
        id: 'error',
        header: t('cockpit.job.error'),
        cell: (job) => (
          <div className="incident">
            <span className="incident__message">{job.exceptionMessage ?? t('cockpit.incident.noMessage')}</span>
            <span className="incident__links">
              <button type="button" className="link link--subtle" onClick={() => setDetails(job.id)}>
                {t('cockpit.incident.details')}
              </button>
            </span>
          </div>
        ),
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (job) => (
          <Button size="sm" icon={RotateCcw} aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: job.id })} onClick={() => setRetry(job.id)}>
            {t('cockpit.retry.action')}
          </Button>
        ),
      },
    ],
    [t],
  );
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('cockpit.batch.failedJobs')} wide>
      <div className="dialog-table">
        <div className="toolbar">
          <span className="toolbar__text">{t('cockpit.job.failedCount', { count: batch.failedJobs })}</span>
          <Button
            icon={RotateCcw}
            busy={busy}
            disabled={!batch.batchJobDefinitionId}
            onClick={() => {
              setBusy(true);
              void run(() => setJobDefinitionRetries(batch.batchJobDefinitionId ?? '', 1), {
                success: t('cockpit.retry.done'),
                failure: t('cockpit.retry.failed'),
              }).then(() => setBusy(false));
            }}
          >
            {t('cockpit.job.retryAll')}
          </Button>
        </div>
        <DataTable
          label={t('cockpit.batch.failedJobs')}
          columns={columns}
          rows={jobs.data}
          rowKey={(job) => job.id}
          error={jobs.error}
          onRetry={() => void jobs.refetch()}
          empty={<EmptyState title={t('cockpit.batch.noFailedJobs')} />}
        />
      </div>
      <FailureDetailsDialog
        target={details ? { kind: 'job', id: details } : null}
        onClose={() => setDetails(null)}
      />
      <RetryDialog
        key={retry ?? 'none'}
        target={retry ? { kind: 'job', id: retry } : null}
        what={retry ?? ''}
        onClose={() => setRetry(null)}
      />
    </Dialog>
  );
}

