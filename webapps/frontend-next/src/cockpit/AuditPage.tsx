/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CheckCheck, Download, History, NotebookPen } from 'lucide-react';
import {
  getAllOperations,
  getTaskNames,
  findCases,
  getOperationLog,
  orNull,
  setOperationAnnotation,
  type OperationLogEntry,
  type OperationLogQuery,
} from '../api/operations';
import { useI18n } from '../i18n';
import { MoreFilters } from './MoreFilters';
import { formatDateTime, fromDateTimeInput, parseEngineDate, toEngineDate } from '../lib/dates';
import { downloadCsv } from '../lib/csv';
import { useToast } from '../ui/toast';
import { Button, Dialog, EmptyState, Field, NoMatch, cx } from '../ui';
import { COCKPIT, EngineWideNote, useCockpitAction } from './common';
import { Time } from '../ui/format';
import { pageOf, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { historyPath } from './paths';

const CATEGORIES = ['', 'Operator', 'TaskWorker', 'Admin'] as const;

function AnnotationDialog({ entry, onClose }: { entry: OperationLogEntry; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [text, setText] = useState(entry.annotation ?? '');
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('ops.audit.annotate')}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setBusy(true);
          void run(() => setOperationAnnotation(entry.operationId, text), {
            success: t('ops.audit.annotated'),
            failure: t('ops.audit.annotateFailed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) onClose();
          });
        }}
      >
        <Field label={t('ops.annotation')} hint={t('ops.audit.annotateHint')}>
          {(props) => (
            <textarea {...props} className="input" rows={4} maxLength={4000} value={text} onChange={(event) => setText(event.target.value)} />
          )}
        </Field>
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" busy={busy}>
            {t('action.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Entries of the log of user operations, newest first; one row per changed property. */
export function OperationLogTable({
  query,
  onClearFilters,
  exportable = true,
}: {
  query: OperationLogQuery;
  onClearFilters?: () => void;
  /** false where the page offers its own report */
  exportable?: boolean;
}) {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const page = pageOf(params, 'lpage');
  const [annotating, setAnnotating] = useState<OperationLogEntry | null>(null);
  const log = useQuery({
    queryKey: [COCKPIT, 'operation-log', query, page],
    queryFn: ({ signal }) =>
      orNull(getOperationLog({ query, paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal })),
    placeholderData: keepPreviousData,
  });
  // which task a row is about, e.g. who completed what
  const taskIds = (log.data?.items ?? []).flatMap((row) => (row.taskId ? [row.taskId] : []));
  const taskNames = useQuery({
    queryKey: [COCKPIT, 'operation-log-tasks', taskIds],
    queryFn: ({ signal }) => getTaskNames(taskIds, signal),
    enabled: taskIds.length > 0,
    staleTime: 60_000,
  });
  const names = taskNames.data;
  const columns = useMemo<Column<OperationLogEntry>[]>(
    () => [
      { id: 'time', header: t('ops.history.time'), cell: (row) => <Time value={row.timestamp} /> },
      { id: 'user', header: t('cockpit.user'), cell: (row) => row.userId ?? <span className="muted">—</span> },
      {
        id: 'operation',
        header: t('ops.audit.operation'),
        cell: (row) => {
          const task = row.taskId ? names?.get(row.taskId) : undefined;
          return (
            <span className="audit__operation">
              <span>
                <strong>{row.operationType}</strong> <span className="muted">{row.entityType}</span>
              </span>
              {task && <span className="audit__task">{task}</span>}
            </span>
          );
        },
      },
      {
        id: 'change',
        header: t('ops.audit.change'),
        cell: (row) =>
          row.property ? (
            <span className="value__text audit__change">
              {row.property}: {row.orgValue ?? '—'} → {row.newValue ?? '—'}
            </span>
          ) : (
            <span className="muted">—</span>
          ),
      },
      {
        id: 'instance',
        header: t('cockpit.instance'),
        secondary: true,
        cell: (row) =>
          row.processInstanceId ? (
            <Link className="cell-link" to={historyPath(row.processInstanceId)}>
              {row.processDefinitionKey ?? row.processInstanceId}
            </Link>
          ) : (
            <span className="muted">—</span>
          ),
      },
      {
        id: 'annotation',
        header: t('ops.annotation'),
        cell: (row) => (
          <span className="row-actions">
            {row.annotation && <span className="value__text">{row.annotation}</span>}
            {row.category === 'Operator' && (
              <Button
                size="sm"
                icon={NotebookPen}
                aria-label={t('a11y.rowAction', { action: row.annotation ? t('ops.edit') : t('ops.audit.annotate'), row: `${row.operationType} ${row.entityType}` })}
                onClick={() => setAnnotating(row)}
              >
                {row.annotation ? t('ops.edit') : t('ops.audit.annotate')}
              </Button>
            )}
          </span>
        ),
      },
    ],
    [t, names],
  );
  if (log.data === null) {
    return (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
        {t('cockpit.history.unavailableHint')}
      </EmptyState>
    );
  }
  return (
    <>
      <DataTable
        label={t('ops.audit.title')}
        columns={columns}
        updating={log.isPlaceholderData}
        rows={log.data?.items}
        rowKey={(row) => row.id}
        exportable={exportable}
        fetchAll={exportable ? (paging) => getOperationLog({ query, paging }).then((result) => result.items) : undefined}
        error={log.error}
        onRetry={() => void log.refetch()}
        empty={onClearFilters ? <NoMatch onClear={onClearFilters} /> : <EmptyState icon={History} title={t('ops.audit.empty')} />}
      />
      <Pager page={page} count={log.data?.count ?? 0} onPage={(next) => setParams({ lpage: next ? String(next + 1) : null }, { push: true })} />
      {annotating && <AnnotationDialog entry={annotating} onClose={() => setAnnotating(null)} />}
    </>
  );
}

const AUDIT_FILTERS = ['user', 'op', 'entity', 'cat', 'after', 'before', 'case', 'caseid'] as const;

/** The rows of an audit report, in the order of its columns; `taskNames` by task id. */
export function auditRows(entries: OperationLogEntry[], taskNames?: Map<string, string>): (string | null)[][] {
  return entries.map((entry) => [
    entry.timestamp,
    entry.userId,
    entry.operationType,
    entry.entityType,
    (entry.taskId && taskNames?.get(entry.taskId)) || null,
    entry.category,
    entry.property,
    entry.orgValue,
    entry.newValue,
    entry.processDefinitionKey,
    entry.processInstanceId,
    entry.operationId,
    entry.annotation,
  ]);
}

function formatCaseStart(value: string | null, locale: string): string {
  const date = value ? parseEngineDate(value) : null;
  return date ? formatDateTime(date, locale) : '';
}

function stateLabel(state: string | null, t: ReturnType<typeof useI18n>['t']): string {
  if (!state) return '';
  return state === 'ACTIVE' || state === 'SUSPENDED' ? t('ops.audit.caseRunning') : t('ops.audit.caseEnded');
}

/** What users did in the engine, filterable. */
export function AuditPage() {
  const { t, locale } = useI18n();
  const toast = useToast();
  const [params, setParams] = useUrlState();
  const [exporting, setExporting] = useState(false);
  // a case is named by its instance id or its business key
  const caseText = params.get('case') ?? '';
  const cases = useQuery({
    queryKey: [COCKPIT, 'audit-case', caseText],
    queryFn: ({ signal }) => findCases(caseText, signal),
    enabled: !!caseText,
    staleTime: 60_000,
  });
  const matches = cases.data?.matches ?? [];
  // a reused business key names several instances: the user picks one, the newest by default
  const chosen = params.get('caseid');
  // a chosen instance stays chosen, also when newer ones push it out of the list
  const resolvedId = (chosen && cases.data ? chosen : null) ?? matches[0]?.id ?? null;
  const resolved = { ...cases, data: cases.data ? resolvedId : undefined };
  const date = (name: string) => {
    const parsed = fromDateTimeInput(params.get(name) ?? '');
    return parsed ? toEngineDate(parsed) : undefined;
  };
  const query: OperationLogQuery = {
    userId: params.get('user') || undefined,
    operationType: params.get('op') || undefined,
    entityType: params.get('entity') || undefined,
    category: params.get('cat') || undefined,
    afterTimestamp: date('after'),
    beforeTimestamp: date('before'),
    // an unknown case matches nothing rather than everything
    processInstanceId: caseText ? (resolved.data ?? caseText) : undefined,
  };
  const completions = params.get('op') === 'Complete' && params.get('entity') === 'Task';
  const exportReport = async () => {
    setExporting(true);
    try {
      const { entries, truncated } = await getAllOperations(query);
      // a report without task names is still a report
      const taskNames = await getTaskNames(entries.flatMap((entry) => (entry.taskId ? [entry.taskId] : []))).catch(
        () => new Map<string, string>(),
      );
      downloadCsv(
        `audit-${new Date().toISOString().slice(0, 10)}.csv`,
        [
          t('ops.audit.col.time'),
          t('ops.audit.user'),
          t('ops.audit.operationType'),
          t('ops.audit.entityType'),
          t('ops.audit.col.task'),
          t('ops.audit.category'),
          t('ops.audit.col.property'),
          t('ops.audit.col.before'),
          t('ops.audit.col.after'),
          t('ops.audit.col.process'),
          t('ops.audit.col.instance'),
          t('ops.audit.col.operation'),
          t('ops.audit.col.annotation'),
        ],
        auditRows(entries, taskNames),
      );
      toast.show({ title: t(truncated ? 'ops.audit.exportedTruncated' : 'ops.audit.exported', { count: entries.length }) });
    } catch (error) {
      toast.error(error, t('ops.audit.exportFailed'));
    } finally {
      setExporting(false);
    }
  };
  const change = (name: string, value: string) => {
    const next = value.trim() || null;
    if (next === (params.get(name) || null)) return;
    // a new case text forgets the instance picked for the old one
    setParams({ [name]: next, lpage: null, ...(name === 'case' ? { caseid: null } : {}) });
  };
  const text = (name: string, label: string) => (
    <input
      className="input"
      aria-label={label}
      placeholder={label}
      // a new value from the address resets the field; the name keeps the fields apart
      key={`${name}:${params.get(name) ?? ''}`}
      defaultValue={params.get(name) ?? ''}
      onBlur={(event) => change(name, event.target.value)}
      onKeyDown={(event) => event.key === 'Enter' && change(name, event.currentTarget.value)}
    />
  );
  return (
    <div className="page">
      <PageHeader
        title={t('ops.audit.title')}
        subtitle={t('ops.audit.subtitle')}
        actions={
          <Button
            icon={Download}
            busy={exporting}
            // the case must be known first, or the report would be empty
            disabled={!!caseText && (resolved.isPending || resolved.isError)}
            onClick={() => void exportReport()}
          >
            {t('ops.audit.export')}
          </Button>
        }
      />
      <EngineWideNote />
      <Card flush>
        <div className="toolbar toolbar--filters">
          {text('case', t('ops.audit.case'))}
          {matches.length > 1 && (
            <label className="audit-case-pick">
              <span>{t(cases.data?.more ? 'ops.audit.caseMatchesMore' : 'ops.audit.caseMatches', { count: matches.length })}</span>
              <select
                className="input toolbar__select"
                value={resolvedId ?? ''}
                onChange={(event) => setParams({ caseid: event.target.value, lpage: null })}
              >
                {chosen && !matches.some((match) => match.id === chosen) && <option value={chosen}>{chosen}</option>}
                {matches.map((match) => (
                  <option key={match.id} value={match.id}>
                    {[formatCaseStart(match.startTime, locale), match.processDefinitionKey, stateLabel(match.state, t), match.id.slice(0, 8)]
                      .filter(Boolean)
                      .join(' · ')}
                  </option>
                ))}
              </select>
            </label>
          )}
          {text('user', t('ops.audit.user'))}
          {text('op', t('ops.audit.operationType'))}
          {text('entity', t('ops.audit.entityType'))}
          <select
            className="input toolbar__select"
            aria-label={t('ops.audit.category')}
            value={params.get('cat') ?? ''}
            onChange={(event) => setParams({ cat: event.target.value || null, lpage: null })}
          >
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {category || t('ops.audit.allCategories')}
              </option>
            ))}
          </select>
          <button
            type="button"
            className={cx('toggle', completions && 'is-on')}
            aria-pressed={completions}
            onClick={() =>
              setParams(completions ? { op: null, entity: null, lpage: null } : { op: 'Complete', entity: 'Task', lpage: null })
            }
          >
            <CheckCheck aria-hidden size={14} />
            {t('ops.audit.completions')}
          </button>
          <MoreFilters active={['after', 'before'].filter((name) => params.get(name)).length}>
            <div className="date-range">
              {(['after', 'before'] as const).map((name) => (
                <label key={name} className="date-filter">
                  <span className="field__label">{name === 'after' ? t('ops.audit.after') : t('ops.audit.before')}</span>
                  <input
                    className="input"
                    type="datetime-local"
                    value={params.get(name) ?? ''}
                    onChange={(event) => setParams({ [name]: event.target.value || null, lpage: null })}
                  />
                </label>
              ))}
            </div>
          </MoreFilters>
        </div>
        <OperationLogTable
          query={query}
          exportable={false}
          onClearFilters={
            AUDIT_FILTERS.some((name) => params.get(name))
              ? () => setParams({ ...Object.fromEntries(AUDIT_FILTERS.map((name) => [name, null])), lpage: null })
              : undefined
          }
        />
      </Card>
    </div>
  );
}
