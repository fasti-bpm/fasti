/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { AlertTriangle, CircleCheck, CircleSlash, History, Play, RotateCw, SearchX, Trash2 } from 'lucide-react';
import { getProcessXml } from '../api/engine';
import { isApiError } from '../api/http';
import {
  deleteHistoricInstancesAsync,
  getHistoricActivities,
  getHistoricIncidents,
  getHistoricInstance,
  getHistoricInstances,
  getHistoricVariables,
  getJobLog,
  getVariableUpdates,
  orNull,
  type HistoricActivity,
  type HistoricIncident,
  type HistoricInstance,
  type HistoricQuery,
  type HistoricVariable,
  type JobLogEntry,
} from '../api/operations';
import type { SortOrder } from '../api/types';
import type { ActivityBadge, ActivityMarker } from '../bpmn/BpmnDiagram';
import { useI18n, type Translate } from '../i18n';
import { MoreFilters } from './MoreFilters';
import { fromDateTimeInput, toDateTimeInput, toEngineDate } from '../lib/dates';
import { tenantScope } from '../lib/userPrefs';
import { getLatestDefinitions } from '../api/cockpit';
import { Button, Dialog, EmptyState, ErrorState, Loading, Skeleton, cx } from '../ui';
import { OperationLogTable } from './AuditPage';
import { VariableColumnsError, VariableColumnsPicker, useColumnValues, useVariableColumns, variableColumnsOf } from './VariableColumns';
import { SavedViews, VIEW_PARAM, currentFilters, viewChanges, type BuiltInView } from './SavedViews';
import { InstanceTimeline } from './Timeline';
import { COCKPIT, elementNames, useCockpitAction } from './common';
import { ConfirmDialog } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { pageOf, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { definitionPath } from './DefinitionsPage';
import { instancePath } from './Incidents';
import { flowNodes, formatDuration, type FlowNode } from './model';
import { VariableFilterBuilder } from './VariableFilterBuilder';
import { decodeFilter, encodeFilter, toEngineQuery, type VariableFilter } from './variableFilter';
import { historyPath } from './paths';
import { RestartDialog } from './RestartDialog';
import { withReceipt } from './receipts';
import { OverflowRow } from '../app/OverflowMenu';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

const CaseTimeline = lazy(() => import('../tasklist/CaseTimeline').then((module) => ({ default: module.CaseTimeline })));

const STATES = ['', 'running', 'finished', 'completed', 'canceled'] as const;

/** The URL parameters that filter the list of historic instances. */
export const HISTORY_FILTERS = ['hkey', 'hstate', 'hbk', 'hvar', 'hinc', 'hsa', 'hsb', 'hfa', 'hfb'] as const;

const HOUR = 60 * 60 * 1000;

/** Views every user has: what fails now, what waits for more than a day, what started today. */
/** filters folded behind one button on phones */
const MORE_FILTERS = ['hbk', 'hvar', 'hinc', 'hsa', 'hsb', 'hfa', 'hfb'];

export function historyViews(t: Translate, now: () => Date = () => new Date()): BuiltInView[] {
  return [
    { id: 'failing', label: t('views.failing'), params: () => ({ hstate: 'running', hinc: 'open' }) },
    {
      id: 'stuck',
      label: t('views.stuck'),
      params: () => ({ hstate: 'running', hsb: toDateTimeInput(new Date(now().getTime() - 24 * HOUR)) }),
    },
    {
      id: 'today',
      label: t('views.today'),
      params: () => {
        const today = now();
        today.setHours(0, 0, 0, 0);
        return { hsa: toDateTimeInput(today) };
      },
    },
  ];
}

function stateLabel(state: HistoricInstance['state'], t: Translate): string {
  switch (state) {
    case 'ACTIVE':
      return t('ops.history.state.running');
    case 'SUSPENDED':
      return t('cockpit.state.suspended');
    case 'COMPLETED':
      return t('ops.history.state.completed');
    default:
      return t('ops.history.state.canceled');
  }
}

export function HistoricStateBadge({ state }: { state: HistoricInstance['state'] }) {
  const { t } = useI18n();
  const tone = state === 'COMPLETED' ? 'success' : state === 'ACTIVE' ? 'info' : 'warning';
  const Icon = state === 'COMPLETED' ? CircleCheck : state === 'ACTIVE' ? Play : CircleSlash;
  return (
    <span className={`badge badge--${tone}`}>
      <Icon aria-hidden size={12} />
      {stateLabel(state, t)}
    </span>
  );
}

/**
 * The process filter: the keys of the deployed processes are offered (with
 * their names), since variable columns belong to one process; typing the key
 * of a process that is no longer deployed still filters its history.
 */
function ProcessKeyFilter({ value, onChange }: { value: string; onChange: (key: string) => void }) {
  const { t } = useI18n();
  const definitions = useQuery({
    queryKey: [COCKPIT, 'latest-definitions', tenantScope()],
    queryFn: ({ signal }) => getLatestDefinitions(signal),
    staleTime: 60_000,
  });
  // the field follows the address, e.g. after Back, and keeps what is being typed
  const [draft, setDraft] = useState(value);
  const [shown, setShown] = useState(value);
  if (shown !== value) {
    setShown(value);
    setDraft(value);
  }
  return (
    <>
      <input
        className="input"
        list="history-process-keys"
        aria-label={t('ops.history.process')}
        placeholder={t('ops.history.anyProcess')}
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
          // picking a suggestion applies at once; typed text on Enter or when leaving the field
          const native = event.nativeEvent as InputEvent;
          if (native.inputType === 'insertReplacementText' || native.inputType === undefined) onChange(event.target.value.trim());
        }}
        onBlur={() => onChange(draft.trim())}
        onKeyDown={(event) => event.key === 'Enter' && onChange(draft.trim())}
      />
      <datalist id="history-process-keys">
        {(definitions.data ?? []).map((definition) => (
          <option key={definition.key} value={definition.key}>
            {definition.name && definition.name !== definition.key ? definition.name : undefined}
          </option>
        ))}
      </datalist>
    </>
  );
}

/** Reads the filters of the history list from the address; exported for tests. */
export function historicQueryOf(params: URLSearchParams, definitionId?: string): HistoricQuery {
  const date = (name: string) => {
    const parsed = fromDateTimeInput(params.get(name) ?? '');
    return parsed ? toEngineDate(parsed) : undefined;
  };
  const state = STATES.find((entry) => entry && entry === params.get('hstate'));
  const variables = toEngineQuery(historyVariableFilter(params.get('hvar')));
  return {
    processDefinitionId: definitionId,
    processDefinitionKey: definitionId ? undefined : params.get('hkey') || undefined,
    state: state || undefined,
    withIncidents: params.get('hinc') === '1' || undefined,
    openIncidents: params.get('hinc') === 'open' || undefined,
    businessKeyLike: params.get('hbk') || undefined,
    startedAfter: date('hsa'),
    startedBefore: date('hsb'),
    finishedAfter: date('hfa'),
    finishedBefore: date('hfb'),
    variables: variables.variables,
    orVariables: variables.orQueries?.[0]?.variables,
  };
}

/** The variable filter of the address; older links held one `name=value`. */
export function historyVariableFilter(raw: string | null): VariableFilter {
  const legacy = raw && !raw.startsWith('{') ? /^([^=]+)=(.*)$/.exec(raw) : null;
  if (legacy) {
    return { mode: 'and', conditions: [{ name: (legacy[1] ?? '').trim(), operator: 'eq', type: 'auto', value: legacy[2] ?? '' }] };
  }
  return decodeFilter(raw);
}

type HistoricSelection = { kind: 'ids'; ids: string[] } | { kind: 'query'; query: HistoricQuery };

/**
 * Instances of the history: running and finished, filterable. Within one
 * version finished instances can be restarted, and the history of chosen
 * instances removed.
 */
export function HistoricInstanceList({
  definitionId,
  processKey,
  nodes,
}: {
  definitionId?: string;
  /** the key of the process of the version; else the one filtered for or the one of every shown row */
  processKey?: string;
  nodes?: FlowNode[];
}) {
  const { t, locale } = useI18n();
  const run = useCockpitAction();
  const [params, setParams] = useUrlState();
  const [sortOrder, setSortOrder] = useRemembered<SortOrder>('history.order', 'desc');
  const page = pageOf(params, 'hpage');
  const query = useMemo(() => historicQueryOf(params, definitionId), [params, definitionId]);
  const [picked, setPicked] = useState<string[]>([]);
  const [allMatching, setAllMatching] = useState(false);
  const [dialog, setDialog] = useState<{ kind: 'restart' | 'delete'; selection: HistoricSelection; count: number } | null>(null);
  const [busy, setBusy] = useState(false);

  const instances = useQuery({
    queryKey: [COCKPIT, 'historic-instances', query, sortOrder, page],
    queryFn: ({ signal }) =>
      orNull(
        getHistoricInstances({
          query,
          sortOrder,
          paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
          signal,
        }),
      ),
    placeholderData: keepPreviousData,
  });
  // a new filter or tenant starts a new selection, also when the address changes another way
  const filterKey = JSON.stringify([query, tenantScope()]);
  const [selectionKey, setSelectionKey] = useState(filterKey);
  if (selectionKey !== filterKey) {
    setSelectionKey(filterKey);
    setPicked([]);
    setAllMatching(false);
  }
  const pending = allMatching && (instances.isPlaceholderData || instances.isFetching);
  const items = instances.data?.items ?? [];
  const total = instances.data?.count ?? 0;
  const pageIds = items.map((item) => item.id);
  // variable columns belong to one process: the one of the version, filtered for, or shared by every row
  const rowKeys = [...new Set(items.map((item) => item.processDefinitionKey))];
  const columnsKey = processKey ?? (params.get('hkey') || (rowKeys.length === 1 ? rowKeys[0] : undefined));
  const [variableNames, setVariableNames] = useVariableColumns(columnsKey);
  const variableValues = useColumnValues(pageIds, variableNames, true);
  const allOnPage = pageIds.length > 0 && pageIds.every((id) => picked.includes(id));
  const selection: HistoricSelection = allMatching ? { kind: 'query', query } : { kind: 'ids', ids: picked };
  const count = allMatching ? total : picked.length;
  const finishedOnly = allMatching
    ? query.state === 'finished' || query.state === 'completed' || query.state === 'canceled'
    : picked.every((id) => items.find((item) => item.id === id)?.endTime);

  const columns = useMemo<Column<HistoricInstance>[]>(
    () => [
      {
        id: 'select',
        header: '',
        cell: (row) => (
          <input
            type="checkbox"
            className="row-check"
            aria-label={t('ops.bulk.selectRow', { id: row.id })}
            checked={allMatching || picked.includes(row.id)}
            onChange={() => {
              setAllMatching(false);
              setPicked((current) => (current.includes(row.id) ? current.filter((id) => id !== row.id) : [...current, row.id]));
            }}
          />
        ),
      },
      {
        id: 'id',
        header: t('cockpit.instance.id'),
        cell: (row) => (
          <Link className="cell-link" to={historyPath(row.id)}>
            <Id value={row.id} />
          </Link>
        ),
      },
      ...(definitionId
        ? []
        : [
            {
              id: 'definition',
              header: t('ops.history.definition'),
              cell: (row: HistoricInstance) => (
                <Link className="cell-link" to={definitionPath(row.processDefinitionId)}>
                  {(row.processDefinitionName ?? row.processDefinitionKey) +
                    (row.processDefinitionVersion ? ` · v${row.processDefinitionVersion}` : '')}
                </Link>
              ),
            },
          ]),
      { id: 'bk', header: t('process.businessKey'), cell: (row) => row.businessKey ?? <span className="muted">—</span> },
      { id: 'start', header: t('cockpit.instance.started'), sortBy: 'startTime', secondary: true, cell: (row) => <Time value={row.startTime} /> },
      { id: 'end', header: t('ops.history.ended'), secondary: true, cell: (row) => <Time value={row.endTime} /> },
      { id: 'duration', header: t('ops.history.duration'), align: 'end', secondary: true, cell: (row) => formatDuration(row.durationInMillis, locale) },
      ...variableColumnsOf<HistoricInstance>(variableNames, variableValues.data, (row) => row.id),
      { id: 'state', header: t('cockpit.state'), cell: (row) => <HistoricStateBadge state={row.state} /> },
    ],
    [t, locale, picked, allMatching, definitionId, variableNames, variableValues.data],
  );

  const set = (name: string, value: string | null) => {
    setParams({ [name]: value, hpage: null, [VIEW_PARAM]: null });
    setPicked([]);
    setAllMatching(false);
  };

  if (instances.data === null) {
    return (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
        {t('cockpit.history.unavailableHint')}
      </EmptyState>
    );
  }

  return (
    <>
      {!definitionId && (
        <SavedViews
          storageKey="history.views"
          keys={HISTORY_FILTERS}
          builtIns={historyViews(t)}
          params={params}
          onApply={(changes) => {
            setParams({ ...changes, hpage: null });
            setPicked([]);
            setAllMatching(false);
          }}
        />
      )}
      <div className="toolbar toolbar--filters">
        {!definitionId && <ProcessKeyFilter value={params.get('hkey') ?? ''} onChange={(key) => set('hkey', key || null)} />}
        <select
          className="input toolbar__select"
          aria-label={t('cockpit.state')}
          value={params.get('hstate') ?? ''}
          onChange={(event) => set('hstate', event.target.value || null)}
        >
          {STATES.map((state) => (
            <option key={state} value={state}>
              {state ? t(`ops.history.state.${state}`) : t('ops.history.state.all')}
            </option>
          ))}
        </select>
        <MoreFilters active={MORE_FILTERS.filter((name) => params.get(name)).length}>
          <input
            key={`hbk-${params.get('hbk') ?? ''}`}
            className="input"
            aria-label={t('process.businessKey')}
            placeholder={t('ops.history.businessKeyContains')}
            defaultValue={params.get('hbk') ?? ''}
            onBlur={(event) => set('hbk', event.target.value.trim() || null)}
            onKeyDown={(event) => event.key === 'Enter' && set('hbk', event.currentTarget.value.trim() || null)}
          />
          <VariableFilterBuilder
            key={params.get('hvar') ?? ''}
            filter={historyVariableFilter(params.get('hvar'))}
            onApply={(next) => set('hvar', encodeFilter(next))}
          />
          <button
            type="button"
            className={cx('toggle', !!params.get('hinc') && 'is-on')}
            aria-pressed={!!params.get('hinc')}
            onClick={() => set('hinc', params.get('hinc') ? null : '1')}
          >
            <AlertTriangle aria-hidden size={14} />
            {t('ops.history.withIncidents')}
          </button>
          <span className="toolbar__break" aria-hidden />
          {(
            [
              ['hsa', 'ops.history.startedAfter'],
              ['hsb', 'ops.history.startedBefore'],
              ['hfa', 'ops.history.finishedAfter'],
              ['hfb', 'ops.history.finishedBefore'],
            ] as const
          ).map(([name, label]) => (
            <label key={name} className="date-filter">
              <span className="field__label">{t(label)}</span>
              <input
                className="input"
                type="datetime-local"
                value={params.get(name) ?? ''}
                onChange={(event) => set(name, event.target.value || null)}
              />
            </label>
          ))}
        </MoreFilters>
        {columnsKey ? (
          <VariableColumnsPicker names={variableNames} onChange={setVariableNames} instanceIds={pageIds} history />
        ) : (
          !definitionId && <span className="muted toolbar__hint">{t('ops.history.columnsNeedProcess')}</span>
        )}
        <VariableColumnsError error={variableValues.error} onRetry={() => void variableValues.refetch()} />
      </div>
      {items.length > 0 && (
        <div className="toolbar toolbar--select">
          <label className="option option--inline">
            <input
              type="checkbox"
              checked={allMatching || allOnPage}
              onChange={() => {
                setAllMatching(false);
                setPicked(allOnPage ? picked.filter((id) => !pageIds.includes(id)) : [...new Set([...picked, ...pageIds])]);
              }}
            />
            <span className="option__label">{t('ops.bulk.selectPage')}</span>
          </label>
          {total > pageIds.length && (
            <button
              type="button"
              className={cx('toggle', allMatching && 'is-on')}
              aria-pressed={allMatching}
              disabled={instances.isPlaceholderData}
              onClick={() => setAllMatching(!allMatching)}
            >
              {t('ops.bulk.selectAll', { count: total })}
            </button>
          )}
        </div>
      )}
      {count > 0 && (
        <div className="toolbar bulk-bar" role="region" aria-label={t('ops.bulk.title')}>
          <span className="toolbar__text" aria-live="polite">
            {t('ops.bulk.selected', { count: count })}
          </span>
          {definitionId && nodes && (
            <Button variant="primary" icon={RotateCw} disabled={!finishedOnly || pending} onClick={() => setDialog({ kind: 'restart', selection, count })}>
              {t('ops.restart.action')}
            </Button>
          )}
          <Button variant="danger-ghost" icon={Trash2} disabled={!finishedOnly || pending} onClick={() => setDialog({ kind: 'delete', selection, count })}>
            {t('ops.history.delete')}
          </Button>
          {!finishedOnly && <span className="field__hint">{t('ops.history.finishedOnly')}</span>}
        </div>
      )}
      <DataTable
        label={t('ops.history.title')}
        columns={columns}
        updating={instances.isPlaceholderData}
        rows={instances.data?.items}
        rowKey={(row) => row.id}
        fetchAll={(paging) => getHistoricInstances({ query, sortOrder, paging }).then((result) => result.items)}
        error={instances.error}
        onRetry={() => void instances.refetch()}
        sorting={{ sortBy: 'startTime', sortOrder }}
        onSort={(next) => setSortOrder(next.sortOrder)}
        empty={
          Object.keys(currentFilters(params, HISTORY_FILTERS)).length > 0 ? (
            <EmptyState
              icon={SearchX}
              title={t('empty.noMatch')}
              action={
                <Button
                  size="sm"
                  onClick={() => {
                    setParams({ ...viewChanges(HISTORY_FILTERS, {}, ''), hpage: null });
                  }}
                >
                  {t('empty.clearFilters')}
                </Button>
              }
            >
              {t('empty.noMatchHint')}
            </EmptyState>
          ) : (
            <EmptyState icon={SearchX} title={t('ops.history.empty')} />
          )
        }
      />
      <Pager page={page} count={total} onPage={(next) => setParams({ hpage: next ? String(next + 1) : null }, { push: true })} />
      {dialog?.kind === 'restart' && definitionId && nodes && (
        <RestartDialog
          definitionId={definitionId}
          selection={dialog.selection}
          count={dialog.count}
          nodes={nodes}
          onClose={(done) => {
            setDialog(null);
            if (done) {
              setPicked([]);
              setAllMatching(false);
            }
          }}
        />
      )}
      {dialog?.kind === 'delete' && (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          title={t('ops.history.deleteTitle', { count: dialog.count })}
          text={t('ops.history.deleteText', { count: dialog.count })}
          confirmLabel={t('ops.bulk.deleteConfirm', { count: dialog.count })}
          danger
          busy={busy}
          onConfirm={() => {
            setBusy(true);
            void run(withReceipt(() => deleteHistoricInstancesAsync(dialog.selection), t('ops.history.deleteTitle', { count: dialog.count })), {
              success: t('ops.batchStarted'),
              failure: t('ops.bulk.failed'),
            }).then((ok) => {
              setBusy(false);
              if (ok) {
                setDialog(null);
                setPicked([]);
                setAllMatching(false);
              }
            });
          }}
        />
      )}
    </>
  );
}

/** The history of all processes. */
export function HistoryPage() {
  const { t } = useI18n();
  return (
    <div className="page">
      <PageHeader title={t('ops.history.title')} subtitle={t('ops.history.subtitle')} />
      <Card flush>
        <HistoricInstanceList />
      </Card>
    </div>
  );
}

// --- one instance -------------------------------------------------------------

/** Badges and marks of the path an instance took; exported for tests. */
export function executedPath(activities: HistoricActivity[]): { badges: ActivityBadge[]; markers: ActivityMarker[] } {
  const byActivity = new Map<string, ActivityBadge & { canceled: number }>();
  for (const activity of activities) {
    const entry = byActivity.get(activity.activityId) ?? {
      activityId: activity.activityId,
      instances: 0,
      finished: 0,
      incidents: 0,
      canceled: 0,
    };
    if (!activity.endTime) entry.instances += 1;
    else entry.finished = (entry.finished ?? 0) + 1;
    if (activity.canceled) entry.canceled += 1;
    byActivity.set(activity.activityId, entry);
  }
  const entries = [...byActivity.values()];
  return {
    badges: entries.map((entry) => ({
      activityId: entry.activityId,
      instances: entry.instances,
      finished: entry.finished,
      incidents: entry.incidents,
    })),
    markers: entries.map((entry) => ({
      activityId: entry.activityId,
      marker: entry.canceled > 0 && entry.canceled === (entry.finished ?? 0) ? 'is-canceled' : 'is-done',
    })),
  };
}

function VariableHistoryDialog({ variable, onClose }: { variable: HistoricVariable; onClose: () => void }) {
  const { t } = useI18n();
  const updates = useQuery({
    queryKey: [COCKPIT, 'variable-updates', variable.id],
    queryFn: () => getVariableUpdates(variable.id),
  });
  return (
    <Dialog open wide onOpenChange={(open) => !open && onClose()} title={t('ops.history.variableHistory', { name: variable.name })}>
      {updates.isError ? (
        <ErrorState error={updates.error} onRetry={() => void updates.refetch()} />
      ) : !updates.data ? (
        <Loading label={t('state.loading')} />
      ) : updates.data.length === 0 ? (
        <EmptyState title={t('ops.history.noUpdates')} />
      ) : (
        <ol className="timeline">
          {updates.data.map((update) => (
            <li key={update.id} className="timeline__item">
              <Time value={update.time} />
              <span className="muted"> · {update.variableType ?? '—'}</span>
              <pre className="value__text">{valueText(update.value)}</pre>
            </li>
          ))}
        </ol>
      )}
    </Dialog>
  );
}

function valueText(value: unknown): string {
  if (value === null || value === undefined) return 'null';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

function useList<T>(key: string, id: string, load: (id: string) => Promise<T[]>) {
  return useQuery({ queryKey: [COCKPIT, key, id], queryFn: () => orNull(load(id)) });
}

/** One instance as the history knows it: the path it took and what happened on the way. */
export function HistoricInstancePage() {
  const { id = '' } = useParams();
  const { t, locale } = useI18n();
  const [params, setParams] = useUrlState();
  const [variable, setVariable] = useState<HistoricVariable | null>(null);
  const instance = useQuery({ queryKey: [COCKPIT, 'historic-instance', id], queryFn: () => getHistoricInstance(id) });
  const definitionId = instance.data?.processDefinitionId;
  const xml = useQuery({
    queryKey: ['process-xml', definitionId],
    queryFn: () => getProcessXml(definitionId ?? ''),
    enabled: !!definitionId,
    staleTime: Infinity,
  });
  const activities = useQuery({
    queryKey: [COCKPIT, 'historic-activities', id],
    queryFn: ({ signal }) => getHistoricActivities(id, signal),
    enabled: !!instance.data,
  });
  const variables = useList('historic-variables', id, (key) => getHistoricVariables(key));
  const incidents = useList('historic-incidents', id, getHistoricIncidents);
  const jobLog = useList('job-log', id, getJobLog);
  const names = useMemo(() => elementNames(xml.data), [xml.data]);
  const path = useMemo(() => executedPath(activities.data ?? []), [activities.data]);
  // the activity instances by id, to name the scope of a variable
  const scopeNames = useMemo(
    () =>
      new Map(
        (activities.data ?? []).map((activity) => [
          activity.id,
          activity.activityName ?? names.get(activity.activityId) ?? activity.activityId,
        ]),
      ),
    [activities.data, names],
  );
  const nodes = useMemo(() => flowNodes(xml.data), [xml.data]);
  const tab = params.get('tab') ?? 'activities';

  const activityColumns = useMemo<Column<HistoricActivity>[]>(
    () => [
      { id: 'name', header: t('cockpit.activity'), cell: (row) => row.activityName ?? names.get(row.activityId) ?? row.activityId },
      { id: 'type', header: t('ops.history.type'), secondary: true, cell: (row) => <span className="mono">{row.activityType}</span> },
      { id: 'start', header: t('cockpit.instance.started'), cell: (row) => <Time value={row.startTime} /> },
      { id: 'end', header: t('ops.history.ended'), secondary: true, cell: (row) => <Time value={row.endTime} /> },
      { id: 'duration', header: t('ops.history.duration'), align: 'end', cell: (row) => formatDuration(row.durationInMillis, locale) },
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: (row) =>
          !row.endTime ? (
            <span className="badge badge--info">{t('ops.history.state.running')}</span>
          ) : row.canceled ? (
            <span className="badge badge--warning">{t('ops.history.state.canceled')}</span>
          ) : (
            <span className="badge badge--success">{t('ops.history.state.completed')}</span>
          ),
      },
    ],
    [t, locale, names],
  );
  const variableColumns = useMemo<Column<HistoricVariable>[]>(
    () => [
      { id: 'name', header: t('ops.name'), cell: (row) => <span className="mono">{row.name}</span> },
      { id: 'type', header: t('ops.history.type'), secondary: true, cell: (row) => row.type },
      { id: 'value', header: t('ops.value'), cell: (row) => <span className="value__text">{valueText(row.value)}</span> },
      {
        id: 'scope',
        header: t('cockpit.variable.scope'),
        secondary: true,
        cell: (row) =>
          row.taskId
            ? t('cockpit.variable.scopeTask')
            : row.activityInstanceId && row.activityInstanceId !== id
              ? (scopeNames.get(row.activityInstanceId) ?? row.activityInstanceId)
              : t('cockpit.variable.scopeInstance'),
      },
      { id: 'state', header: t('cockpit.state'), secondary: true, cell: (row) => row.state ?? '—' },
      {
        id: 'history',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (row) => (
          <Button size="sm" icon={History} aria-label={t('a11y.rowAction', { action: t('ops.history.changes'), row: row.name })} onClick={() => setVariable(row)}>
            {t('ops.history.changes')}
          </Button>
        ),
      },
    ],
    [t, id, scopeNames],
  );
  const incidentColumns = useMemo<Column<HistoricIncident>[]>(
    () => [
      { id: 'time', header: t('ops.history.created'), cell: (row) => <Time value={row.createTime} /> },
      { id: 'activity', header: t('cockpit.activity'), cell: (row) => (row.activityId ? (names.get(row.activityId) ?? row.activityId) : '—') },
      { id: 'message', header: t('cockpit.job.error'), cell: (row) => <span className="incident__message">{row.incidentMessage ?? '—'}</span> },
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: (row) =>
          row.open ? t('ops.history.incident.open') : row.resolved ? t('ops.history.incident.resolved') : t('ops.history.incident.deleted'),
      },
      { id: 'end', header: t('ops.history.ended'), secondary: true, cell: (row) => <Time value={row.endTime} /> },
    ],
    [t, names],
  );
  const jobColumns = useMemo<Column<JobLogEntry>[]>(
    () => [
      { id: 'time', header: t('ops.history.time'), cell: (row) => <Time value={row.timestamp} /> },
      { id: 'activity', header: t('cockpit.activity'), cell: (row) => (row.activityId ? (names.get(row.activityId) ?? row.activityId) : '—') },
      {
        id: 'kind',
        header: t('ops.history.event'),
        cell: (row) =>
          row.failureLog
            ? t('ops.history.job.failed')
            : row.successLog
              ? t('ops.history.job.succeeded')
              : row.deletionLog
                ? t('ops.history.job.deleted')
                : t('ops.history.job.created'),
      },
      { id: 'retries', header: t('cockpit.retry.retries'), align: 'end', secondary: true, cell: (row) => row.jobRetries ?? '—' },
      { id: 'error', header: t('cockpit.job.error'), cell: (row) => <span className="incident__message">{row.jobExceptionMessage ?? '—'}</span> },
    ],
    [t, names],
  );

  if (instance.isError) {
    return (
      <div className="page">
        <PageHeader title={t('ops.history.instance')} back={{ to: '/cockpit/history', label: t('ops.history.title') }} />
        <Card>
          {isApiError(instance.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('ops.history.notFound')} />
          ) : (
            <ErrorState error={instance.error} onRetry={() => void instance.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!instance.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }
  const current = instance.data;
  const list = <T,>(
    label: string,
    data: { data: T[] | null | undefined; error: unknown; refetch: () => unknown },
    columns: Column<T>[],
    key: (row: T) => string,
  ) =>
    data.data === null ? (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')} />
    ) : (
      <DataTable
        label={label}
        columns={columns}
        rows={data.data}
        rowKey={key}
        error={data.error}
        onRetry={() => void data.refetch()}
        empty={<EmptyState title={t('ops.history.nothing')} />}
      />
    );

  return (
    <div className="page">
      <PageHeader
        title={current.processDefinitionName ?? current.processDefinitionKey}
        back={{ to: definitionPath(current.processDefinitionId), label: t('cockpit.instance.backToDefinition') }}
        badges={<HistoricStateBadge state={current.state} />}
        subtitle={
          <>
            <span>
              {t('cockpit.instance')} <Id value={current.id} copy />
            </span>
            {current.businessKey && (
              <span>
                {t('process.businessKey')}: <strong>{current.businessKey}</strong>
              </span>
            )}
            <span>
              {t('cockpit.instance.started')}: <Time value={current.startTime} />
              {current.startUserId && ` · ${current.startUserId}`}
            </span>
            {current.endTime && (
              <span>
                {t('ops.history.ended')}: <Time value={current.endTime} /> · {formatDuration(current.durationInMillis, locale)}
              </span>
            )}
            {current.deleteReason && <span>{t('ops.history.reason', { reason: current.deleteReason })}</span>}
            {current.removalTime && (
              <span>
                {t('ops.history.removal')}: <Time value={current.removalTime} />
              </span>
            )}
          </>
        }
        actions={
          !current.endTime && (
            <Link className="btn btn--secondary btn--md" to={instancePath(current.id)}>
              {t('ops.history.openRunning')}
            </Link>
          )
        }
      />
      <Card flush className="model">
        {xml.isError ? (
          <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />
        ) : !xml.data ? (
          <Loading label={t('diagram.loading')} />
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <BpmnDiagram xml={xml.data} processKey={current.processDefinitionKey} badges={path.badges} markers={path.markers} label={t('ops.history.diagramLabel')} />
          </Suspense>
        )}
        <p className="model__hint">{t('ops.history.legend', { count: nodes.length })}</p>
      </Card>
      <Card flush>
        <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setParams({ tab: next === 'activities' ? null : next })}>
          <OverflowRow className="tabrow" selector='[role="tab"]' label={t('nav.more')}>

          <Tabs.List className="ctabs__list" aria-label={t('cockpit.instance.tabs')}>
            <Tabs.Trigger className="ctabs__tab" value="activities">{t('ops.history.activities')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="timeline">{t('timeline.title')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="variables">{t('cockpit.instance.tab.variables')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="log">{t('ops.audit.title')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="incidents">{t('cockpit.incidents.title')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="jobs">{t('ops.history.jobLog')}</Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="case">{t('task.tab.case')}</Tabs.Trigger>
          </Tabs.List>
          </OverflowRow>
          <Tabs.Content className="ctabs__content" value="case">
            <Suspense fallback={<Loading label={t('case.loading')} />}>
              <div className="case-pane">
              <CaseTimeline processInstanceId={current.id} canComment={!current.endTime} />
            </div>
            </Suspense>
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="activities">
            {activities.isError ? (
              <ErrorState error={activities.error} onRetry={() => void activities.refetch()} />
            ) : (
              <DataTable
                label={t('ops.history.activities')}
                columns={activityColumns}
                rows={activities.data}
                rowKey={(row) => row.id}
                empty={<EmptyState title={t('ops.history.nothing')} />}
              />
            )}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="timeline">
            <InstanceTimeline instanceId={id} names={names} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="variables">
            {list(t('cockpit.instance.tab.variables'), variables, variableColumns, (row) => row.id)}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="log">
            <OperationLogTable query={{ processInstanceId: id }} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="incidents">
            {list(t('cockpit.incidents.title'), incidents, incidentColumns, (row) => row.id)}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="jobs">
            {list(t('ops.history.jobLog'), jobLog, jobColumns, (row) => row.id)}
          </Tabs.Content>
        </Tabs.Root>
      </Card>
      {variable && <VariableHistoryDialog variable={variable} onClose={() => setVariable(null)} />}
    </div>
  );
}
