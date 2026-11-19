/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, ListX, Receipt as ReceiptIcon, RotateCcw, X } from 'lucide-react';
import { getBatchStatistics, setJobDefinitionRetries, type Batch } from '../api/cockpit';
import { useI18n } from '../i18n';
import { Button, IconButton } from '../ui';
import { FailedJobsDialog } from './BatchFailedJobs';
import { Progress } from './charts';
import { COCKPIT, useCockpitAction } from './common';
import { dismissReceipt, useReceipts, type Receipt } from './receipts';

const POLL = 2_000;

function ReceiptItem({ receipt }: { receipt: Receipt }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [failedOf, setFailedOf] = useState<Batch | null>(null);
  const [busy, setBusy] = useState(false);
  const batch = useQuery({
    queryKey: [COCKPIT, 'receipt', receipt.batchId],
    queryFn: ({ signal }) => getBatchStatistics(receipt.batchId, signal),
    // a batch with failures waits for someone; one that runs is followed closely
    refetchInterval: (query) => (query.state.data === null ? false : POLL),
  });
  const data = batch.data;
  const ended = data === null;
  const failed = data?.failedJobs ?? 0;
  const status = batch.isError
    ? t('ops.receipt.unknown')
    : ended
      ? t('ops.receipt.ended')
      : !data
        ? t('state.loading')
        : failed > 0 && data.remainingJobs === failed
          ? t('ops.receipt.stuck', { failed, done: data.completedJobs, total: data.totalJobs })
          : t('ops.receipt.running', { done: data.completedJobs, total: data.totalJobs, failed });

  return (
    <li className={`receipt${failed > 0 ? ' receipt--failed' : ended ? ' receipt--done' : ''}`}>
      <span className="receipt__icon" aria-hidden>
        {failed > 0 ? <ListX size={16} /> : ended ? <CheckCircle2 size={16} /> : <ReceiptIcon size={16} />}
      </span>
      <div className="receipt__body">
        <p className="receipt__title">{receipt.label}</p>
        {data && (
          <Progress
            done={data.completedJobs}
            failed={failed}
            total={data.totalJobs}
            label={t('cockpit.batch.progressOf', { done: data.completedJobs, total: data.totalJobs })}
          />
        )}
        <p className="receipt__status" role="status">
          {status}
        </p>
        <div className="receipt__actions">
          {data && failed > 0 && (
            <>
              <Button
                size="sm"
                icon={RotateCcw}
                busy={busy}
                disabled={!data.batchJobDefinitionId}
                onClick={() => {
                  setBusy(true);
                  void run(() => setJobDefinitionRetries(data.batchJobDefinitionId ?? '', 1), {
                    success: t('ops.receipt.retried'),
                    failure: t('cockpit.retry.failed'),
                  }).then(() => {
                    setBusy(false);
                    void batch.refetch();
                  });
                }}
              >
                {t('ops.receipt.retry', { count: failed })}
              </Button>
              <Button size="sm" onClick={() => setFailedOf(data)}>
                {t('ops.receipt.showFailed')}
              </Button>
            </>
          )}
          <Link className="link link--subtle" to="/cockpit/batches">
            {t('ops.bulk.batches')}
          </Link>
        </div>
      </div>
      <IconButton
        icon={X}
        label={t('ops.receipt.dismiss', { label: receipt.label })}
        onClick={() => dismissReceipt(receipt.batchId)}
      />
      {failedOf && <FailedJobsDialog batch={failedOf} onClose={() => setFailedOf(null)} />}
    </li>
  );
}

/** Receipts of the bulk changes started in this session, until the user dismisses them. */
export function BatchReceipts() {
  const { t } = useI18n();
  const receipts = useReceipts();
  if (receipts.length === 0) {
    return null;
  }
  return (
    <section className="receipts" aria-label={t('ops.receipt.title')}>
      <ul className="receipts__list">
        {receipts.map((receipt) => (
          <ReceiptItem key={receipt.batchId} receipt={receipt} />
        ))}
      </ul>
    </section>
  );
}
