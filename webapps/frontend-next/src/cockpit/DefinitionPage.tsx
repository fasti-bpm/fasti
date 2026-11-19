/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { DropdownMenu, Tabs } from 'radix-ui';
import { AlertTriangle, ArrowRightLeft, MoreHorizontal, PencilRuler, Pause, Play, RotateCcw, SearchX, SlidersHorizontal, Trash2, Workflow, X } from 'lucide-react';
import {
  deleteDefinition,
  getActivityStatistics,
  getCalledDefinitions,
  getDefinition,
  getDefinitionVersions,
  getInstances,
  getJobDefinitions,
  getLatestDefinition,
  getScopedIncidents,
  setDefinitionSuspended,
  setJobDefinitionPriority,
  setJobDefinitionRetries,
  setJobDefinitionSuspended,
  type CalledDefinition,
  type IncidentSortBy,
  type InstanceListItem,
  type JobDefinition,
  type ProcessDefinitionDetail,
  type Sorting,
} from '../api/cockpit';
import { getProcessXml } from '../api/engine';
import { isApiError } from '../api/http';
import type { SortOrder } from '../api/types';
import type { ActivityBadge } from '../bpmn/BpmnDiagram';
import { useI18n } from '../i18n';
import { fromDateTimeInput, toEngineDate } from '../lib/dates';
import { tenantScope } from '../lib/userPrefs';
import { Button, EmptyState, ErrorState, Field, IconButton, Loading, NoMatch, Skeleton, cx } from '../ui';
import { MenuContent } from '../ui/menu';
import {
  COCKPIT,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  Id,
  IncidentBadge,
  PAGE_SIZE,
  PageHeader,
  Pager,
  StateBadge,
  Time,
  elementNames,
  pageOf,
  totalIncidents,
  useCockpitAction,
  useRefreshInterval,
  useRemembered,
  useUrlState,
  type Column,
} from './common';
import { definitionPath } from './DefinitionsPage';
import { IncidentTable, instancePath } from './Incidents';
import { BulkBar } from './BulkActions';
import { HistoricInstanceList } from './HistoryPage';
import { VariableColumnsError, VariableColumnsPicker, useColumnValues, useVariableColumns, variableColumnsOf } from './VariableColumns';
import { flowNodes, parseConditionValue, type FlowNode } from './model';
import { VariableFilterBuilder } from './VariableFilterBuilder';
import { decodeFilter, encodeFilter, toEngineQuery, type VariableFilter } from './variableFilter';
import { SavedViews, VIEW_PARAM } from './SavedViews';

/** The URL parameters that filter the running instances of a version; saved views keep them. */
const INSTANCE_FILTERS = ['bk', 'inc', 'act', 'vars'] as const;
import { ModifyDialog } from './ModifyDialog';
import type { InstanceSelection } from '../api/operations';
import { migrationPath } from './paths';
import { StatisticsTab } from './StatisticsTab';
import { OverflowRow } from '../app/OverflowMenu';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

export { parseConditionValue };

/** The variable filter of the address; links from before the builder held a plain list of conditions. */
export function readVariableFilter(raw: string | null): VariableFilter {
  if (raw?.startsWith('[')) {
    try {
      const parsed: unknown = JSON.parse(raw);
      const rows = Array.isArray(parsed) ? (parsed as { name?: unknown; operator?: unknown; value?: unknown }[]) : [];
      return decodeFilter(
        JSON.stringify({
          m: 'and',
          c: rows
            .filter((row) => typeof row?.name === 'string')
            .map((row) => [row.name, row.operator, 'auto', typeof row.value === 'string' ? `"${row.value}"` : String(row.value)]),
        }),
      );
    } catch {
      return decodeFilter(null);
    }
  }
  return decodeFilter(raw);
}

/** Redirects a link by process key to the latest version of the process. */
export function DefinitionByKeyPage() {
  const { key = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const { search } = useLocation();
  const latest = useQuery({
    queryKey: [COCKPIT, 'latest-definition', key, tenantScope()],
    queryFn: async () => {
      const definition = await getLatestDefinition(key);
      if (definition) {
        // the tab and the filters of the link are kept
        void navigate(`${definitionPath(definition.id)}${search}`, { replace: true });
      }
      return definition;
    },
  });
  if (latest.isError) {
    return <ErrorState error={latest.error} onRetry={() => void latest.refetch()} />;
  }
  if (latest.data === null) {
    return (
      <EmptyState icon={SearchX} title={t('cockpit.definition.notFound')}>
        <span className="mono">{key}</span>
      </EmptyState>
    );
  }
  return <Loading label={t('state.loading')} />;
}

// --- instances --------------------------------------------------------------

function InstancesTab({
  definitionId,
  processKey,
  names,
  nodes,
  activities,
}: {
  definitionId: string;
  /** the chosen variable columns are kept per process key */
  processKey: string | undefined;
  names: Map<string, string>;
  nodes: FlowNode[];
  activities: string[];
}) {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sortOrder, setSortOrder] = useRemembered<SortOrder>('instances.order', 'desc');
  const businessKey = params.get('bk') ?? '';
  const withIncident = params.get('inc') === '1';
  const rawConditions = params.get('vars');
  const variableFilter = useMemo(() => readVariableFilter(rawConditions), [rawConditions]);
  const engineFilter = useMemo(() => toEngineQuery(variableFilter), [variableFilter]);
  const page = pageOf(params);

  const [keyInput, setKeyInput] = useState(businessKey);
  const [picked, setPicked] = useState<string[]>([]);
  const [allMatching, setAllMatching] = useState(false);
  const [modifying, setModifying] = useState<InstanceSelection | null>(null);

  const instances = useQuery({
    queryKey: [COCKPIT, 'instances', definitionId, businessKey, withIncident, activities, engineFilter, sortOrder, page],
    queryFn: ({ signal }) =>
      getInstances({
        query: {
          processDefinitionId: definitionId,
          businessKey: businessKey || undefined,
          withIncident,
          activityIdIn: activities,
          ...engineFilter,
        },
        sortOrder,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const setVariableFilter = (next: VariableFilter) => setParams({ vars: encodeFilter(next), page: null, [VIEW_PARAM]: null });

  // a new filter or tenant starts a new selection: "all matching" and picked rows belong to the old one
  const filterKey = JSON.stringify([definitionId, businessKey, withIncident, activities, engineFilter, tenantScope()]);
  const [selectionKey, setSelectionKey] = useState(filterKey);
  if (selectionKey !== filterKey) {
    setSelectionKey(filterKey);
    setPicked([]);
    setAllMatching(false);
  }
  const pending = instances.isPlaceholderData || instances.isFetching;
  // the rows of the old filter stay visible while the new ones load, but cannot be picked
  const stale = instances.isPlaceholderData;

  const pageIds = (instances.data?.items ?? []).map((instance) => instance.id);
  const [variableNames, setVariableNames] = useVariableColumns(processKey);
  const variableValues = useColumnValues(pageIds, variableNames, false);
  const allOnPage = pageIds.length > 0 && pageIds.every((entry) => picked.includes(entry));
  const query = {
    processDefinitionId: definitionId,
    businessKey: businessKey || undefined,
    withIncident,
    activityIdIn: activities,
    ...engineFilter,
  };
  const selection = allMatching
    ? ({ kind: 'query', query, count: instances.data?.count ?? 0 } as const)
    : ({ kind: 'ids', ids: picked } as const);

  const columns = useMemo<Column<InstanceListItem>[]>(
    () => [
      {
        id: 'select',
        header: '',
        cell: (instance) => (
          <input
            type="checkbox"
            className="row-check"
            aria-label={t('ops.bulk.selectRow', { id: instance.id })}
            checked={allMatching || picked.includes(instance.id)}
            disabled={stale}
            onChange={() => {
              setAllMatching(false);
              setPicked((current) =>
                current.includes(instance.id)
                  ? current.filter((entry) => entry !== instance.id)
                  : [...current, instance.id],
              );
            }}
          />
        ),
      },
      {
        id: 'id',
        header: t('cockpit.instance.id'),
        cell: (instance) => (
          <Link className="cell-link" to={instancePath(instance.id)}>
            <Id value={instance.id} />
          </Link>
        ),
      },
      {
        id: 'businessKey',
        header: t('process.businessKey'),
        cell: (instance) => instance.businessKey ?? <span className="muted">—</span>,
      },
      {
        id: 'start',
        header: t('cockpit.instance.started'),
        sortBy: 'startTime',
        secondary: true,
        cell: (instance) => <Time value={instance.startTime} />,
      },
      ...variableColumnsOf<InstanceListItem>(variableNames, variableValues.data, (instance) => instance.id),
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: (instance) => (
          <span className="badges">
            {instance.suspended && <StateBadge suspended />}
            <IncidentBadge count={totalIncidents(instance.incidents)} />
            {!instance.suspended && totalIncidents(instance.incidents) === 0 && (
              <StateBadge suspended={false} />
            )}
          </span>
        ),
      },
    ],
    [t, picked, allMatching, stale, variableNames, variableValues.data],
  );

  const filtered = !!businessKey || withIncident || activities.length > 0 || variableFilter.conditions.length > 0;

  return (
    <>
      <SavedViews
        storageKey="instances.views"
        keys={INSTANCE_FILTERS}
        builtIns={[]}
        params={params}
        onApply={(changes) => {
          setParams({ ...changes, page: null });
          if ('bk' in changes) setKeyInput(changes.bk ?? '');
        }}
      />
      <div className="toolbar toolbar--filters">
        <form
          className="filter-group"
          onSubmit={(event) => {
            event.preventDefault();
            setParams({ bk: keyInput.trim() || null, page: null });
          }}
        >
          <input
            className="input"
            aria-label={t('process.businessKey')}
            placeholder={t('cockpit.instances.businessKey')}
            value={keyInput}
            onChange={(event) => setKeyInput(event.target.value)}
            onBlur={() => keyInput.trim() !== businessKey && setParams({ bk: keyInput.trim() || null, page: null })}
          />
        </form>
        <VariableFilterBuilder key={rawConditions ?? ''} filter={variableFilter} onApply={setVariableFilter} />
        <button
          type="button"
          className={cx('toggle', withIncident && 'is-on')}
          aria-pressed={withIncident}
          onClick={() => setParams({ inc: withIncident ? null : '1', page: null })}
        >
          <AlertTriangle aria-hidden size={14} />
          {t('cockpit.instances.onlyIncidents')}
        </button>
        {processKey && (
          <VariableColumnsPicker names={variableNames} onChange={setVariableNames} instanceIds={pageIds} history={false} />
        )}
        <VariableColumnsError error={variableValues.error} onRetry={() => void variableValues.refetch()} />
      </div>
      {activities.length > 0 && (
        <ul className="chips toolbar__chips" aria-label={t('search.active')}>
          {activities.map((activity) => (
            <li key={`a-${activity}`} className="chip">
              <span className="chip__label">{t('cockpit.activity')}: </span>
              {names.get(activity) ?? activity}
              <button
                type="button"
                className="chip__remove"
                aria-label={t('search.remove', { criterion: names.get(activity) ?? activity })}
                onClick={() =>
                  setParams({
                    act: activities.filter((entry) => entry !== activity).join(',') || null,
                    page: null,
                  })
                }
              >
                <X aria-hidden size={14} />
              </button>
            </li>
          ))}

        </ul>
      )}
      {(instances.data?.items.length ?? 0) > 0 && (
        <div className="toolbar toolbar--select">
          <label className="option option--inline">
            <input
              type="checkbox"
              checked={allMatching || allOnPage}
              disabled={stale}
              onChange={() => {
                setAllMatching(false);
                setPicked(allOnPage ? picked.filter((entry) => !pageIds.includes(entry)) : [...new Set([...picked, ...pageIds])]);
              }}
            />
            <span className="option__label">{t('ops.bulk.selectPage')}</span>
          </label>
          {(instances.data?.count ?? 0) > pageIds.length && (
            <button
              type="button"
              className={cx('toggle', allMatching && 'is-on')}
              aria-pressed={allMatching}
              disabled={instances.isPlaceholderData}
              onClick={() => setAllMatching(!allMatching)}
            >
              {t('ops.bulk.selectAll', { count: instances.data?.count ?? 0 })}
            </button>
          )}
        </div>
      )}
      {(allMatching || picked.length > 0) && (
        <BulkBar
          selection={selection}
          definitionId={definitionId}
          onClear={() => {
            setPicked([]);
            setAllMatching(false);
          }}
          onModify={() => setModifying(selection)}
          pending={allMatching && pending}
        />
      )}
      {modifying && (
        <ModifyDialog
          target={{ kind: 'batch', definitionId, selection: modifying }}
          nodes={nodes}
          onClose={() => setModifying(null)}
        />
      )}
      <DataTable
        label={t('cockpit.instances.title')}
        columns={columns}
        updating={instances.isPlaceholderData}
        rows={instances.data?.items}
        rowKey={(instance) => instance.id}
        error={instances.error}
        onRetry={() => void instances.refetch()}
        sorting={{ sortBy: 'startTime', sortOrder }}
        onSort={(next) => {
          setSortOrder(next.sortOrder);
          setParams({ page: null });
        }}
        empty={
          filtered ? (
            <NoMatch
              title={t('cockpit.instances.noMatch')}
              onClear={() => {
                setKeyInput('');
                setParams({ bk: null, inc: null, vars: null, act: null, page: null });
              }}
            />
          ) : (
            <EmptyState icon={Workflow} title={t('cockpit.instances.empty')}>
              {t('cockpit.instances.emptyHint')}
            </EmptyState>
          )
        }
      />
      <Pager
        page={page}
        count={instances.data?.count ?? 0}
        onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
      />
    </>
  );
}

// --- incidents --------------------------------------------------------------

function DefinitionIncidentsTab({
  definitionId,
  names,
  activities,
}: {
  definitionId: string;
  names: Map<string, string>;
  activities: string[];
}) {
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<IncidentSortBy>>('incidents.sort', {
    sortBy: 'incidentTimestamp',
    sortOrder: 'desc',
  });
  const page = pageOf(params);
  const incidents = useQuery({
    queryKey: [COCKPIT, 'incidents', 'definition', definitionId, activities, sorting, page],
    queryFn: ({ signal }) =>
      getScopedIncidents({
        processDefinitionId: definitionId,
        activityIdIn: activities,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  return (
    <>
      <IncidentTable
        page={incidents.data}
        error={incidents.error}
        onRetry={() => void incidents.refetch()}
        sorting={sorting}
        onSort={(next) => {
          setSorting(next as Sorting<IncidentSortBy>);
          setParams({ page: null });
        }}
        names={names}
        showInstance
      />
      <Pager
        page={page}
        count={incidents.data?.count ?? 0}
        onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
      />
    </>
  );
}

// --- called definitions -----------------------------------------------------

function CalledDefinitionsTab({ definitionId, names }: { definitionId: string; names: Map<string, string> }) {
  const { t } = useI18n();
  const called = useQuery({
    queryKey: [COCKPIT, 'called-definitions', definitionId],
    queryFn: () => getCalledDefinitions(definitionId),
  });
  const columns = useMemo<Column<CalledDefinition>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.called.definition'),
        cell: (definition) => (
          <Link className="cell-link" to={definitionPath(definition.id)}>
            {definition.name ?? definition.key}
          </Link>
        ),
      },
      {
        id: 'version',
        header: t('cockpit.definition.version'),
        align: 'end',
        cell: (definition) => definition.version,
      },
      {
        id: 'activities',
        header: t('cockpit.called.from'),
        cell: (definition) =>
          definition.calledFromActivityIds.map((id) => names.get(id) ?? id).join(', '),
      },
    ],
    [t, names],
  );
  return (
    <DataTable
      label={t('cockpit.definition.tab.called')}
      columns={columns}
      rows={called.data}
      rowKey={(definition) => definition.id}
      error={called.error}
      onRetry={() => void called.refetch()}
      empty={
        <EmptyState title={t('cockpit.called.definitionsEmpty')}>
          {t('cockpit.called.definitionsEmptyHint')}
        </EmptyState>
      }
    />
  );
}

// --- job definitions --------------------------------------------------------

type JobDialog =
  | { kind: 'suspend'; job: JobDefinition }
  | { kind: 'priority'; job: JobDefinition }
  | { kind: 'retries'; job: JobDefinition };

function JobDefinitionDialog({
  dialog,
  what,
  onClose,
}: {
  dialog: JobDialog;
  what: string;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const { job, kind } = dialog;
  const [includeJobs, setIncludeJobs] = useState(true);
  const [number, setNumber] = useState(
    kind === 'priority' ? String(job.overridingJobPriority ?? '') : '1',
  );
  const [busy, setBusy] = useState(false);
  const parsed = Number(number);
  const validNumber = number.trim() !== '' && Number.isInteger(parsed) && (kind !== 'retries' || parsed >= 0);
  const clearing = kind === 'priority' && number.trim() === '';

  const submit = (call: () => Promise<void>, success: string, failure: string) => {
    setBusy(true);
    void run(call, { success, failure }).then((ok) => {
      setBusy(false);
      if (ok) onClose();
    });
  };

  if (kind === 'suspend') {
    const suspend = !job.suspended;
    return (
      <ConfirmDialog
        open
        onOpenChange={(open) => !open && onClose()}
        title={suspend ? t('cockpit.job.suspend.title') : t('cockpit.job.activate.title')}
        text={t(suspend ? 'cockpit.job.suspend.text' : 'cockpit.job.activate.text', { what })}
        confirmLabel={suspend ? t('cockpit.suspend') : t('cockpit.activate')}
        busy={busy}
        onConfirm={() =>
          submit(
            () => setJobDefinitionSuspended(job.id, suspend, includeJobs),
            suspend ? t('cockpit.job.suspend.done') : t('cockpit.job.activate.done'),
            t('cockpit.job.stateFailed'),
          )
        }
      >
        <Checkbox
          label={t('cockpit.job.includeJobs')}
          hint={t('cockpit.job.includeJobsHint')}
          checked={includeJobs}
          onChange={setIncludeJobs}
        />
      </ConfirmDialog>
    );
  }
  if (kind === 'priority') {
    return (
      <ConfirmDialog
        open
        onOpenChange={(open) => !open && onClose()}
        title={t('cockpit.job.priority.title')}
        text={t('cockpit.job.priority.text', { what })}
        confirmLabel={t('action.save')}
        busy={busy}
        disabled={!clearing && !validNumber}
        onConfirm={() =>
          submit(
            () => setJobDefinitionPriority(job.id, clearing ? null : parsed, includeJobs),
            t('cockpit.job.priority.done'),
            t('cockpit.job.priority.failed'),
          )
        }
      >
        <Field
          label={t('task.priority')}
          hint={t('cockpit.job.priority.hint')}
          error={clearing || validNumber ? null : t('validation.integer')}
        >
          {(props) => (
            <input
              {...props}
              className="input"
              type="number"
              step={1}
              value={number}
              onChange={(event) => setNumber(event.target.value)}
            />
          )}
        </Field>
        <Checkbox
          label={t('cockpit.job.priority.includeJobs')}
          checked={includeJobs && !clearing}
          disabled={clearing}
          onChange={setIncludeJobs}
        />
      </ConfirmDialog>
    );
  }
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.job.retries.title')}
      text={t('cockpit.job.retries.text', { what })}
      confirmLabel={t('cockpit.retry.confirm')}
      busy={busy}
      disabled={!validNumber}
      onConfirm={() =>
        submit(
          () => setJobDefinitionRetries(job.id, parsed),
          t('cockpit.retry.done'),
          t('cockpit.retry.failed'),
        )
      }
    >
      <Field
        label={t('cockpit.retry.retries')}
        hint={t('cockpit.retry.hint')}
        error={validNumber ? null : t('validation.integer')}
      >
        {(props) => (
          <input
            {...props}
            className="input"
            type="number"
            min={0}
            step={1}
            value={number}
            onChange={(event) => setNumber(event.target.value)}
          />
        )}
      </Field>
    </ConfirmDialog>
  );
}

function JobDefinitionsTab({ definitionId, names }: { definitionId: string; names: Map<string, string> }) {
  const { t } = useI18n();
  const refetchInterval = useRefreshInterval();
  const [dialog, setDialog] = useState<JobDialog | null>(null);
  const jobs = useQuery({
    queryKey: [COCKPIT, 'job-definitions', definitionId],
    queryFn: () => getJobDefinitions(definitionId),
    refetchInterval,
  });
  const columns = useMemo<Column<JobDefinition>[]>(() => {
    const activity = (job: JobDefinition) => (job.activityId ? (names.get(job.activityId) ?? job.activityId) : '—');
    return [
      { id: 'activity', header: t('cockpit.activity'), cell: activity },
      {
        id: 'type',
        header: t('cockpit.job.type'),
        cell: (job) => <span className="mono">{job.jobType}</span>,
      },
      {
        id: 'configuration',
        header: t('cockpit.job.configuration'),
        secondary: true,
        cell: (job) => job.jobConfiguration ?? <span className="muted">—</span>,
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: (job) => <StateBadge suspended={job.suspended} />,
      },
      {
        id: 'priority',
        header: t('cockpit.job.overridingPriority'),
        align: 'end',
        secondary: true,
        cell: (job) => job.overridingJobPriority ?? <span className="muted">—</span>,
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (job) => (
          <span className="row-actions">
            <Button
              size="sm"
              icon={job.suspended ? Play : Pause}
              aria-label={t('a11y.rowAction', { action: job.suspended ? t('cockpit.activate') : t('cockpit.suspend'), row: job.activityId ?? job.id })}
              onClick={() => setDialog({ kind: 'suspend', job })}
            >
              {job.suspended ? t('cockpit.activate') : t('cockpit.suspend')}
            </Button>
            <Button
              size="sm"
              icon={SlidersHorizontal}
              aria-label={t('a11y.rowAction', { action: t('task.priority'), row: job.activityId ?? job.id })}
              onClick={() => setDialog({ kind: 'priority', job })}
            >
              {t('task.priority')}
            </Button>
            <Button
              size="sm"
              icon={RotateCcw}
              aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: job.activityId ?? job.id })}
              onClick={() => setDialog({ kind: 'retries', job })}
            >
              {t('cockpit.retry.action')}
            </Button>
          </span>
        ),
      },
    ];
  }, [t, names]);
  return (
    <>
      <DataTable
        label={t('cockpit.definition.tab.jobs')}
        columns={columns}
        rows={jobs.data}
        rowKey={(job) => job.id}
        error={jobs.error}
        onRetry={() => void jobs.refetch()}
        empty={
          <EmptyState title={t('cockpit.job.definitionsEmpty')}>
            {t('cockpit.job.definitionsEmptyHint')}
          </EmptyState>
        }
      />
      {dialog && (
        <JobDefinitionDialog
          key={`${dialog.kind}-${dialog.job.id}`}
          dialog={dialog}
          what={dialog.job.activityId ? (names.get(dialog.job.activityId) ?? dialog.job.activityId) : dialog.job.jobType}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  );
}

// --- actions of the definition ----------------------------------------------

function SuspendDefinitionDialog({
  definition,
  name,
  onClose,
}: {
  definition: ProcessDefinitionDetail;
  name: string;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const suspend = !definition.suspended;
  const [includeInstances, setIncludeInstances] = useState(true);
  const [later, setLater] = useState(false);
  const [date, setDate] = useState('');
  const [busy, setBusy] = useState(false);
  const executionDate = later ? fromDateTimeInput(date) : null;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={suspend ? t('cockpit.definition.suspend.title') : t('cockpit.definition.activate.title')}
      text={t(suspend ? 'cockpit.definition.suspend.text' : 'cockpit.definition.activate.text', {
        name,
        version: definition.version,
      })}
      confirmLabel={suspend ? t('cockpit.suspend') : t('cockpit.activate')}
      busy={busy}
      disabled={later && !executionDate}
      onConfirm={() => {
        setBusy(true);
        void run(
          () =>
            setDefinitionSuspended(definition.id, {
              suspended: suspend,
              includeProcessInstances: includeInstances,
              executionDate: executionDate ? toEngineDate(executionDate) : null,
            }),
          {
            success: executionDate
              ? t('cockpit.definition.stateScheduled')
              : suspend
                ? t('cockpit.definition.suspend.done')
                : t('cockpit.definition.activate.done'),
            failure: t('cockpit.definition.stateFailed'),
          },
        ).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    >
      <Checkbox
        label={t('cockpit.definition.includeInstances')}
        hint={
          suspend
            ? t('cockpit.definition.includeInstancesSuspendHint')
            : t('cockpit.definition.includeInstancesActivateHint')
        }
        checked={includeInstances}
        onChange={setIncludeInstances}
      />
      <Checkbox label={t('cockpit.definition.later')} checked={later} onChange={setLater} />
      {later && (
        <Field label={t('cockpit.definition.executionDate')}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="datetime-local"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
          )}
        </Field>
      )}
    </ConfirmDialog>
  );
}

function DeleteDefinitionDialog({
  definition,
  name,
  onClose,
  onDeleted,
}: {
  definition: ProcessDefinitionDetail;
  name: string;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [cascade, setCascade] = useState(false);
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [busy, setBusy] = useState(false);
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.definition.delete.title')}
      text={t('cockpit.definition.delete.text', { name, version: definition.version })}
      confirmLabel={t('cockpit.definition.delete.confirm')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(
          () =>
            deleteDefinition(definition.id, {
              cascade,
              skipCustomListeners: skipListeners,
              skipIoMappings: skipMappings,
            }),
          {
            success: t('cockpit.definition.delete.done', { name, version: definition.version }),
            failure: t('cockpit.definition.delete.failed'),
          },
        ).then((ok) => {
          setBusy(false);
          if (ok) onDeleted();
        });
      }}
    >
      <Checkbox
        label={t('cockpit.delete.cascade')}
        hint={t('cockpit.definition.delete.cascadeHint')}
        checked={cascade}
        onChange={setCascade}
      />
      <Checkbox
        label={t('cockpit.delete.skipListeners')}
        hint={t('cockpit.delete.skipListenersHint')}
        checked={skipListeners}
        onChange={setSkipListeners}
      />
      <Checkbox
        label={t('cockpit.delete.skipMappings')}
        hint={t('cockpit.delete.skipMappingsHint')}
        checked={skipMappings}
        onChange={setSkipMappings}
      />
    </ConfirmDialog>
  );
}

// --- page -------------------------------------------------------------------

const TABS = ['instances', 'history', 'statistics', 'incidents', 'called', 'jobs'] as const;

/** One version of a process: its diagram with live numbers and what runs in it. */
export function DefinitionPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [dialog, setDialog] = useState<'suspend' | 'delete' | null>(null);

  const definition = useQuery({
    queryKey: [COCKPIT, 'definition', id],
    queryFn: () => getDefinition(id),
    refetchInterval,
  });
  const key = definition.data?.key;
  const tenantId = definition.data?.tenantId ?? null;
  const versions = useQuery({
    queryKey: [COCKPIT, 'definition-versions', key, tenantId],
    queryFn: () => getDefinitionVersions(key ?? '', tenantId),
    enabled: !!key,
  });
  const xml = useQuery({
    queryKey: ['process-xml', id],
    queryFn: () => getProcessXml(id),
    staleTime: Infinity,
  });
  const statistics = useQuery({
    queryKey: [COCKPIT, 'activity-statistics', id],
    queryFn: ({ signal }) => getActivityStatistics(id, signal),
    refetchInterval,
  });

  const names = useMemo(() => elementNames(xml.data), [xml.data]);
  const nodes = useMemo(() => flowNodes(xml.data), [xml.data]);
  const badges = useMemo<ActivityBadge[] | undefined>(
    () =>
      statistics.data?.map((activity) => ({
        activityId: activity.id,
        instances: activity.instances,
        incidents: totalIncidents(activity.incidents),
      })),
    [statistics.data],
  );
  const rawActivities = params.get('act');
  const activities = useMemo(() => (rawActivities ? rawActivities.split(',') : []), [rawActivities]);
  const tab = TABS.find((candidate) => candidate === params.get('tab')) ?? 'instances';

  if (definition.isError && !definition.data) {
    return (
      <div className="page">
        <PageHeader
          title={t('cockpit.definition.title')}
          back={{ to: '/cockpit/processes', label: t('cockpit.definitions.title') }}
        />
        <Card>
          {isApiError(definition.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('cockpit.definition.notFound')}>
              <Link className="link" to="/cockpit/processes">
                {t('cockpit.definitions.title')}
              </Link>
            </EmptyState>
          ) : (
            <ErrorState error={definition.error} onRetry={() => void definition.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!definition.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }

  const current = definition.data;
  const name = current.name ?? current.key;
  const toggleActivity = (activityId: string | null) => {
    const next =
      activityId === null
        ? []
        : activities.includes(activityId)
          ? activities.filter((entry) => entry !== activityId)
          : [...activities, activityId];
    setParams({ act: next.join(',') || null, page: null });
  };

  return (
    <div className="page">
      <PageHeader
        title={name}
        back={{ to: '/cockpit/processes', label: t('cockpit.definitions.title') }}
        badges={<StateBadge suspended={current.suspended} />}
        subtitle={
          <>
            <span className="mono">{current.key}</span>
            {current.tenantId && <span>{t('cockpit.tenantOf', { tenant: current.tenantId })}</span>}
            <label className="version-select">
              {t('cockpit.definition.version')}
              <select
                className="input"
                value={current.id}
                onChange={(event) => void navigate(`${definitionPath(event.target.value)}?tab=${tab}`)}
              >
                {(versions.data ?? [current]).map((version) => (
                  <option key={version.id} value={version.id}>
                    {version.version}
                    {version.versionTag ? ` (${version.versionTag})` : ''}
                  </option>
                ))}
              </select>
            </label>
          </>
        }
        actions={
          <>
            <Link className="btn btn--secondary btn--md" to={`/cockpit/modeler?definition=${encodeURIComponent(current.id)}`}>
              <PencilRuler aria-hidden size={16} className="btn__icon" />
              {t('modeler.edit')}
            </Link>
            <Link className="btn btn--secondary btn--md" to={migrationPath(current.id)}>
              <ArrowRightLeft aria-hidden size={16} className="btn__icon" />
              {t('ops.migrate.action')}
            </Link>
            <Button icon={current.suspended ? Play : Pause} onClick={() => setDialog('suspend')}>
              {current.suspended ? t('cockpit.activate') : t('cockpit.suspend')}
            </Button>
            <DropdownMenu.Root>
              <DropdownMenu.Trigger asChild>
                <IconButton icon={MoreHorizontal} label={t('common.moreActions')} className="icon-btn--bordered" />
              </DropdownMenu.Trigger>
              <DropdownMenu.Portal>
                <MenuContent>
                  <DropdownMenu.Item className="menu__item menu__item--danger" onSelect={() => setDialog('delete')}>
                    <Trash2 aria-hidden size={16} />
                    {t('cockpit.delete')}
                  </DropdownMenu.Item>
                </MenuContent>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          </>
        }
      />

      <Card flush className="model">
        {xml.isError ? (
          <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />
        ) : !xml.data ? (
          <Loading label={t('diagram.loading')}>
            <Skeleton lines={6} />
          </Loading>
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <BpmnDiagram
              xml={xml.data}
              processKey={current.key}
              badges={badges}
              selected={activities}
              onSelect={toggleActivity}
              label={t('cockpit.definition.diagramLabel')}
            />
          </Suspense>
        )}
        <div className="model__footer">
          <p className="model__hint">{t('cockpit.definition.diagramHint')}</p>
          {statistics.data && statistics.data.length > 0 && (
            // the same choice as a click on the diagram, for the keyboard and screen readers
            <select
              className="input toolbar__select"
              aria-label={t('cockpit.definition.activityPicker')}
              value=""
              onChange={(event) => event.target.value && toggleActivity(event.target.value)}
            >
              <option value="">{t('cockpit.definition.activityPicker')}</option>
              {[...statistics.data]
                .sort((a, b) => (names.get(a.id) ?? a.id).localeCompare(names.get(b.id) ?? b.id))
                .map((activity) => (
                  <option key={activity.id} value={activity.id}>
                    {(activities.includes(activity.id) ? '✓ ' : '') +
                      t('cockpit.definition.activityOption', {
                        name: names.get(activity.id) ?? activity.id,
                        instances: activity.instances,
                        incidents: totalIncidents(activity.incidents),
                      })}
                  </option>
                ))}
            </select>
          )}
        </div>
      </Card>

      <Card flush>
        <Tabs.Root
          className="ctabs"
          value={tab}
          onValueChange={(next) => setParams({ tab: next === 'instances' ? null : next, page: null })}
        >
          <OverflowRow className="tabrow" selector='[role="tab"]' label={t('nav.more')}>

          <Tabs.List className="ctabs__list" aria-label={t('cockpit.definition.tabs')}>
            <Tabs.Trigger className="ctabs__tab" value="instances">
              {t('cockpit.instances.title')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="history">
              {t('ops.history.tab')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="statistics">
              {t('ops.stats.tab')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="incidents">
              {t('cockpit.incidents.title')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="called">
              {t('cockpit.definition.tab.called')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="jobs">
              {t('cockpit.definition.tab.jobs')}
            </Tabs.Trigger>
          </Tabs.List>
          </OverflowRow>
          <Tabs.Content className="ctabs__content" value="instances">
            <InstancesTab definitionId={id} processKey={current.key} names={names} nodes={nodes} activities={activities} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="history">
            <HistoricInstanceList definitionId={id} processKey={current.key} nodes={nodes} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="statistics">
            {xml.data && <StatisticsTab definitionId={id} processKey={current.key} xml={xml.data} names={names} />}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="incidents">
            <DefinitionIncidentsTab definitionId={id} names={names} activities={activities} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="called">
            <CalledDefinitionsTab definitionId={id} names={names} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="jobs">
            <JobDefinitionsTab definitionId={id} names={names} />
          </Tabs.Content>
        </Tabs.Root>
      </Card>

      {dialog === 'suspend' && (
        <SuspendDefinitionDialog definition={current} name={name} onClose={() => setDialog(null)} />
      )}
      {dialog === 'delete' && (
        <DeleteDefinitionDialog
          definition={current}
          name={name}
          onClose={() => setDialog(null)}
          onDeleted={() => void navigate('/cockpit/processes')}
        />
      )}
    </div>
  );
}
