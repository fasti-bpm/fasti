/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CircleCheck, Download, RotateCcw } from 'lucide-react';
import {
  getFailedJobs,
  setFailedJobsRetriesAsync,
  setJobsRetries,
  type FailedJobScope,
  type FailedJobSortBy,
  type Job,
  type Sorting,
} from '../api/cockpit';
import { useI18n } from '../i18n';
import { downloadCsv } from '../lib/csv';
import { Button, EmptyState, Field } from '../ui';
import {
  COCKPIT,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  Id,
  PAGE_SIZE,
  PageHeader,
  Pager,
  Time,
  pageOf,
  useCockpitAction,
  useRefreshInterval,
  useRemembered,
  useUrlState,
  type Column,
} from './common';
import { FailureDetailsDialog, instancePath } from './Incidents';
import { withReceipt } from './receipts';

/** What the retries are set for: some jobs of the page or every job of the list. */
type RetryTarget = { ids: string[] } | { all: true; count: number };

function RetriesDialog({
  target,
  scope,
  onClose,
  onDone,
}: {
  target: RetryTarget | null;
  scope: FailedJobScope;
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [retries, setRetries] = useState('1');
  const [busy, setBusy] = useState(false);
  const value = Number(retries);
  const valid = retries.trim() !== '' && Number.isInteger(value) && value >= 0;
  const all = target && 'all' in target;
  return (
    <ConfirmDialog
      open={!!target}
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.jobs.retry.title')}
      text={
        all
          ? t('cockpit.jobs.retry.textAll', { count: target.count })
          : t('cockpit.jobs.retry.text', { count: target?.ids.length ?? 0 })
      }
      confirmLabel={t('cockpit.retry.confirm')}
      busy={busy}
      disabled={!valid}
      onConfirm={() => {
        if (!target) return;
        setBusy(true);
        const call =
          'all' in target
            ? withReceipt(() => setFailedJobsRetriesAsync(scope, value), t('cockpit.jobs.retry.textAll', { count: target.count }))
            : async () => {
                const failed = await setJobsRetries(target.ids, value);
                if (failed.length > 0) {
                  throw new Error(t('cockpit.jobs.retry.partial', { count: failed.length }));
                }
              };
        void run(call, {
          success: all ? t('cockpit.jobs.retry.started') : t('cockpit.retry.done'),
          failure: t('cockpit.retry.failed'),
        }).then((ok) => {
          setBusy(false);
          if (ok) {
            onDone();
            onClose();
          }
        });
      }}
    >
      <Field
        label={t('cockpit.retry.retries')}
        hint={t('cockpit.retry.hint')}
        error={valid ? null : t('validation.integer')}
      >
        {(props) => (
          <input
            {...props}
            className="input"
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            value={retries}
            onChange={(event) => setRetries(event.target.value)}
          />
        )}
      </Field>
    </ConfirmDialog>
  );
}

/** The failed jobs of the whole engine, with retries for single jobs, a selection or all. */
export function FailedJobsPage() {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<FailedJobSortBy>>('jobs.sort', {
    sortBy: 'jobDueDate',
    sortOrder: 'asc',
  });
  const scope: FailedJobScope = params.get('scope') === 'withException' ? 'withException' : 'noRetriesLeft';
  const page = pageOf(params);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [retry, setRetry] = useState<RetryTarget | null>(null);
  const [details, setDetails] = useState<string | null>(null);

  const jobs = useQuery({
    queryKey: [COCKPIT, 'jobs', 'failed', scope, sorting, page],
    queryFn: ({ signal }) =>
      getFailedJobs({
        scope,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const rows = jobs.data?.items;
  const visible = useMemo(() => new Set(rows?.map((job) => job.id) ?? []), [rows]);
  // only what is on screen stays selected
  const chosen = [...selected].filter((id) => visible.has(id));
  const allChosen = !!rows?.length && chosen.length === rows.length;

  const toggle = (id: string, on: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });

  const columns = useMemo<Column<Job>[]>(
    () => [
      {
        id: 'select',
        header: t('cockpit.jobs.select'),
        cell: (job) => (
          <input
            type="checkbox"
            aria-label={t('cockpit.jobs.selectJob', { id: job.id })}
            checked={selected.has(job.id)}
            onChange={(event) => toggle(job.id, event.target.checked)}
          />
        ),
      },
      {
        id: 'message',
        header: t('cockpit.incident.message'),
        cell: (job) => (
          <div className="incident">
            <span className="incident__message">
              {job.exceptionMessage || t('cockpit.incident.noMessage')}
            </span>
            <span className="incident__links">
              <button type="button" className="link link--subtle" onClick={() => setDetails(job.id)}>
                {t('cockpit.incident.details')}
              </button>
            </span>
          </div>
        ),
      },
      {
        id: 'activity',
        header: t('cockpit.activity'),
        secondary: true,
        cell: (job) => job.failedActivityId ?? '—',
      },
      {
        id: 'instance',
        header: t('cockpit.instance'),
        sortBy: 'processInstanceId',
        cell: (job) =>
          job.processInstanceId ? (
            <div className="incident">
              <Link className="cell-link" to={instancePath(job.processInstanceId)}>
                <Id value={job.processInstanceId} />
              </Link>
              {job.processDefinitionKey && (
                <span className="muted">{job.processDefinitionKey}</span>
              )}
            </div>
          ) : (
            '—'
          ),
      },
      {
        id: 'retries',
        header: t('cockpit.retry.retries'),
        sortBy: 'jobRetries',
        align: 'end',
        secondary: true,
        cell: (job) => job.retries,
      },
      {
        id: 'due',
        header: t('cockpit.jobs.due'),
        sortBy: 'jobDueDate',
        secondary: true,
        cell: (job) => <Time value={job.dueDate} />,
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (job) => (
          <Button size="sm" icon={RotateCcw} aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: job.id })} onClick={() => setRetry({ ids: [job.id] })}>
            {t('cockpit.retry.action')}
          </Button>
        ),
      },
    ],
    [t, selected],
  );

  const total = jobs.data?.count ?? 0;

  return (
    <div className="page">
      <PageHeader
        title={t('cockpit.jobs.title')}
        subtitle={jobs.data ? t('cockpit.jobs.count', { count: total }) : undefined}
      />
      <Card flush>
        <div className="toolbar">
          <select
            className="input toolbar__select"
            aria-label={t('cockpit.jobs.scope')}
            value={scope}
            onChange={(event) => {
              setSelected(new Set());
              setParams({ scope: event.target.value === 'withException' ? 'withException' : null, page: null });
            }}
          >
            <option value="noRetriesLeft">{t('cockpit.jobs.scope.noRetriesLeft')}</option>
            <option value="withException">{t('cockpit.jobs.scope.withException')}</option>
          </select>
          {!!rows?.length && (
            <Checkbox
              label={t('cockpit.jobs.selectPage')}
              checked={allChosen}
              onChange={(on) => setSelected(on ? new Set(visible) : new Set())}
            />
          )}
          <Button
            size="sm"
            icon={RotateCcw}
            disabled={chosen.length === 0}
            onClick={() => setRetry({ ids: chosen })}
          >
            {t('cockpit.jobs.retrySelected', { count: chosen.length })}
          </Button>
          <Button
            size="sm"
            disabled={total === 0}
            onClick={() => setRetry({ all: true, count: total })}
          >
            {t('cockpit.jobs.retryAll')}
          </Button>
          <Button
            size="sm"
            variant="ghost"
            icon={Download}
            disabled={!rows?.length}
            onClick={() =>
              downloadCsv(
                'failed-jobs.csv',
                ['id', 'processInstanceId', 'processDefinitionKey', 'activityId', 'retries', 'dueDate', 'exceptionMessage'],
                (rows ?? []).map((job) => [
                  job.id,
                  job.processInstanceId,
                  job.processDefinitionKey,
                  job.failedActivityId,
                  job.retries,
                  job.dueDate,
                  job.exceptionMessage,
                ]),
              )
            }
          >
            {t('ops.export.csv')}
          </Button>
        </div>
        <DataTable
          exportable={false}
          label={t('cockpit.jobs.title')}
          columns={columns}
          rows={rows}
          rowKey={(job) => job.id}
          updating={jobs.isPlaceholderData}
          error={jobs.error}
          onRetry={() => void jobs.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<FailedJobSortBy>);
            setParams({ page: null });
          }}
          empty={
            <EmptyState icon={CircleCheck} title={t('cockpit.jobs.empty')}>
              {t('cockpit.jobs.emptyHint')}
            </EmptyState>
          }
        />
        <Pager
          page={page}
          count={total}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      <RetriesDialog
        key={retry ? JSON.stringify(retry) : 'none'}
        target={retry}
        scope={scope}
        onClose={() => setRetry(null)}
        onDone={() => setSelected(new Set())}
      />
      <FailureDetailsDialog
        target={details ? { kind: 'job', id: details } : null}
        onClose={() => setDetails(null)}
      />
    </div>
  );
}
