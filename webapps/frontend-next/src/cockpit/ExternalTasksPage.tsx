/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Download, Inbox, LockOpen, RotateCcw } from 'lucide-react';
import {
  getExternalTaskTopics,
  queryExternalTasks,
  setExternalTasksRetries,
  unlockExternalTasks,
  type ExternalTask,
  type ExternalTaskSortBy,
  type ExternalTaskState,
  type Sorting,
} from '../api/cockpit';
import { useI18n } from '../i18n';
import { downloadCsv } from '../lib/csv';
import { parseEngineDate } from '../lib/dates';
import { Button, EmptyState, Field, NoMatch } from '../ui';
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
  SearchInput,
  Time,
  pageOf,
  useCockpitAction,
  useDebounced,
  useRefreshInterval,
  useRemembered,
  useUrlState,
  type Column,
} from './common';
import { FailureDetailsDialog, instancePath } from './Incidents';

const STATES: ExternalTaskState[] = ['all', 'locked', 'notLocked', 'noRetriesLeft', 'withRetriesLeft'];

type Change = { kind: 'retry' | 'unlock'; ids: string[] };

function isLocked(task: ExternalTask): boolean {
  const until = parseEngineDate(task.lockExpirationTime);
  return !!until && until.getTime() > Date.now();
}

function ChangeDialog({ change, onClose, onDone }: { change: Change | null; onClose: () => void; onDone: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [retries, setRetries] = useState('1');
  const [busy, setBusy] = useState(false);
  const value = Number(retries);
  const valid = change?.kind !== 'retry' || (retries.trim() !== '' && Number.isInteger(value) && value >= 0);
  const count = change?.ids.length ?? 0;
  return (
    <ConfirmDialog
      open={!!change}
      onOpenChange={(open) => !open && onClose()}
      title={change?.kind === 'unlock' ? t('ops.external.unlock.title') : t('ops.external.retry.title')}
      text={change?.kind === 'unlock' ? t('ops.external.unlock.text', { count }) : t('ops.external.retry.text', { count })}
      confirmLabel={change?.kind === 'unlock' ? t('ops.external.unlock') : t('cockpit.retry.confirm')}
      busy={busy}
      disabled={!valid}
      onConfirm={() => {
        if (!change) return;
        setBusy(true);
        void run(
          async () => {
            const failed =
              change.kind === 'unlock'
                ? await unlockExternalTasks(change.ids)
                : await setExternalTasksRetries(change.ids, value);
            if (failed.length > 0) {
              throw new Error(t('ops.external.partial', { count: failed.length }));
            }
          },
          {
            success: change.kind === 'unlock' ? t('ops.external.unlock.done', { count }) : t('cockpit.retry.done'),
            failure: change.kind === 'unlock' ? t('ops.external.unlock.failed') : t('cockpit.retry.failed'),
          },
        ).then((ok) => {
          setBusy(false);
          if (ok) {
            onDone();
            onClose();
          }
        });
      }}
    >
      {change?.kind === 'retry' && (
        <Field label={t('cockpit.retry.retries')} hint={t('cockpit.retry.hint')} error={valid ? null : t('validation.integer')}>
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
      )}
    </ConfirmDialog>
  );
}

/** The external tasks of the whole engine: who works on them, their retries and their failures. */
export function ExternalTasksPage() {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<ExternalTaskSortBy>>('external.sort', {
    sortBy: 'lockExpirationTime',
    sortOrder: 'asc',
  });
  const stateParam = params.get('state') as ExternalTaskState | null;
  const state: ExternalTaskState = stateParam && STATES.includes(stateParam) ? stateParam : 'all';
  const topicName = params.get('topic') ?? '';
  const [worker, setWorker] = useState(params.get('worker') ?? '');
  const workerId = useDebounced(worker);
  const page = pageOf(params);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [change, setChange] = useState<Change | null>(null);
  const [details, setDetails] = useState<string | null>(null);

  const topics = useQuery({ queryKey: [COCKPIT, 'external', 'topics'], queryFn: getExternalTaskTopics, staleTime: 60_000 });
  const filter = { state, topicName, workerId };
  const tasks = useQuery({
    queryKey: [COCKPIT, 'external', filter, sorting, page],
    queryFn: ({ signal }) =>
      queryExternalTasks({ filter, sorting, paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const rows = tasks.data?.items;
  const visible = useMemo(() => new Set(rows?.map((task) => task.id) ?? []), [rows]);
  const chosen = [...selected].filter((id) => visible.has(id));
  const allChosen = !!rows?.length && chosen.length === rows.length;
  const lockedChosen = (rows ?? []).filter((task) => selected.has(task.id) && isLocked(task)).map((task) => task.id);

  const columns = useMemo<Column<ExternalTask>[]>(
    () => [
      {
        id: 'select',
        header: t('cockpit.jobs.select'),
        cell: (task) => (
          <input
            type="checkbox"
            aria-label={t('ops.external.selectTask', { id: task.id })}
            checked={selected.has(task.id)}
            onChange={(event) =>
              setSelected((current) => {
                const next = new Set(current);
                if (event.target.checked) next.add(task.id);
                else next.delete(task.id);
                return next;
              })
            }
          />
        ),
      },
      { id: 'topic', header: t('ops.external.topic'), cell: (task) => task.topicName ?? '—' },
      {
        id: 'instance',
        header: t('cockpit.instance'),
        sortBy: 'processInstanceId',
        cell: (task) =>
          task.processInstanceId ? (
            <div className="incident">
              <Link className="cell-link" to={instancePath(task.processInstanceId)}>
                <Id value={task.processInstanceId} />
              </Link>
              <span className="muted">{[task.processDefinitionKey, task.activityId].filter(Boolean).join(' · ')}</span>
            </div>
          ) : (
            '—'
          ),
      },
      {
        id: 'worker',
        header: t('ops.external.worker'),
        cell: (task) => (isLocked(task) ? task.workerId ?? '—' : <span className="muted">{t('ops.external.free')}</span>),
      },
      {
        id: 'lock',
        header: t('ops.external.lockUntil'),
        sortBy: 'lockExpirationTime',
        secondary: true,
        cell: (task) => (isLocked(task) ? <Time value={task.lockExpirationTime} /> : '—'),
      },
      { id: 'retries', header: t('cockpit.retry.retries'), align: 'end', cell: (task) => task.retries ?? '—' },
      { id: 'priority', header: t('ops.external.priority'), sortBy: 'taskPriority', align: 'end', secondary: true, cell: (task) => task.priority },
      {
        id: 'error',
        header: t('cockpit.incident.message'),
        secondary: true,
        cell: (task) =>
          task.errorMessage ? (
            <div className="incident">
              <span className="incident__message">{task.errorMessage}</span>
              <span className="incident__links">
                <button type="button" className="link link--subtle" onClick={() => setDetails(task.id)}>
                  {t('cockpit.incident.details')}
                </button>
              </span>
            </div>
          ) : (
            '—'
          ),
      },
    ],
    [t, selected],
  );

  const total = tasks.data?.count ?? 0;

  const exportCsv = () => {
    downloadCsv(
      'external-tasks.csv',
      ['id', 'topic', 'processInstanceId', 'processDefinitionKey', 'activityId', 'workerId', 'lockExpirationTime', 'retries', 'priority', 'errorMessage'],
      (rows ?? []).map((task) => [
        task.id,
        task.topicName,
        task.processInstanceId,
        task.processDefinitionKey,
        task.activityId,
        task.workerId,
        task.lockExpirationTime,
        task.retries,
        task.priority,
        task.errorMessage,
      ]),
    );
  };

  return (
    <div className="page">
      <PageHeader
        title={t('ops.external.title')}
        subtitle={tasks.data ? t('ops.external.count', { count: total }) : t('ops.external.subtitle')}
      />
      <Card flush>
        <div className="toolbar">
          <select
            className="input toolbar__select"
            aria-label={t('ops.external.state')}
            value={state}
            onChange={(event) => {
              setSelected(new Set());
              setParams({ state: event.target.value === 'all' ? null : event.target.value, page: null });
            }}
          >
            {STATES.map((entry) => (
              <option key={entry} value={entry}>
                {t(`ops.external.state.${entry}`)}
              </option>
            ))}
          </select>
          <select
            className="input toolbar__select"
            aria-label={t('ops.external.topic')}
            value={topicName}
            onChange={(event) => {
              setSelected(new Set());
              setParams({ topic: event.target.value || null, page: null });
            }}
          >
            <option value="">{t('ops.external.allTopics')}</option>
            {(topics.data ?? []).map((topic) => (
              <option key={topic} value={topic}>
                {topic}
              </option>
            ))}
          </select>
          <SearchInput
            label={t('ops.external.workerSearch')}
            value={worker}
            onChange={(value) => {
              setWorker(value);
              setParams({ worker: value || null, page: null });
            }}
          />
          {!!rows?.length && (
            <Checkbox
              label={t('cockpit.jobs.selectPage')}
              checked={allChosen}
              onChange={(on) => setSelected(on ? new Set(visible) : new Set())}
            />
          )}
          <Button size="sm" icon={RotateCcw} disabled={chosen.length === 0} onClick={() => setChange({ kind: 'retry', ids: chosen })}>
            {t('cockpit.jobs.retrySelected', { count: chosen.length })}
          </Button>
          <Button
            size="sm"
            icon={LockOpen}
            disabled={lockedChosen.length === 0}
            onClick={() => setChange({ kind: 'unlock', ids: lockedChosen })}
          >
            {t('ops.external.unlockSelected', { count: lockedChosen.length })}
          </Button>
          <Button size="sm" variant="ghost" icon={Download} disabled={!rows?.length} onClick={exportCsv}>
            {t('ops.export.csv')}
          </Button>
        </div>
        <DataTable
          exportable={false}
          label={t('ops.external.title')}
          columns={columns}
          rows={rows}
          rowKey={(task) => task.id}
          updating={tasks.isPlaceholderData}
          error={tasks.error}
          onRetry={() => void tasks.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<ExternalTaskSortBy>);
            setParams({ page: null });
          }}
          empty={
            state !== 'all' || topicName || workerId ? (
              <NoMatch
                onClear={() => {
                  setWorker('');
                  setParams({ state: null, topic: null, worker: null, page: null });
                }}
              />
            ) : (
              <EmptyState icon={Inbox} title={t('ops.external.empty')} />
            )
          }
        />
        <Pager
          page={page}
          count={total}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      <ChangeDialog
        key={change ? JSON.stringify(change) : 'none'}
        change={change}
        onClose={() => setChange(null)}
        onDone={() => setSelected(new Set())}
      />
      <FailureDetailsDialog
        target={details ? { kind: 'externalTask', id: details } : null}
        onClose={() => setDetails(null)}
      />
    </div>
  );
}
