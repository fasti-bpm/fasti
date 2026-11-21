/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { Braces, CheckCheck, CircleCheck, CornerLeftUp, History, Lock, OctagonX, SearchX } from 'lucide-react';
import {
  caseInstanceActions,
  caseInstanceState,
  executionActions,
  executionState,
  getCaseActivityHistory,
  getCaseDefinition,
  getCaseExecutions,
  getCaseInstance,
  getCaseTasks,
  getCaseXml,
  getHistoricCaseInstance,
  getHistoricCaseVariables,
  historicActivityState,
  historicCaseState,
  runCaseInstanceAction,
  runExecutionAction,
  type CaseExecution,
  type CaseInstanceAction,
  type CaseInstanceState,
  type ExecutionAction,
  type HistoricCaseActivity,
  type HistoricCaseVariable,
} from '../api/cases';
import { isApiError } from '../api/http';
import type { Task } from '../api/types';
import { useSession } from '../app/session';
import { OverflowRow } from '../app/OverflowMenu';
import { useI18n, type MessageKey } from '../i18n';
import { Button, EmptyState, ErrorState, Loading, Notice, Skeleton, cx } from '../ui';
import { ConfirmDialog } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, type Column } from '../ui/table';
import {
  ACTION_DONE,
  ACTION_ICONS,
  ACTION_LABELS,
  CONFIRMED_ACTIONS,
  CaseLegend,
  CaseStateBadge,
  caseStateLabel,
  stateMarker,
  useTypeLabel,
  type AnyCaseState,
} from './caseCommon';
import { COCKPIT, useCockpitAction, useRefreshInterval } from './common';
import { instancePath } from './Incidents';
import { formatDuration } from './model';
import { CASES_PATH, caseDefinitionPath, caseInstancePath } from './paths';
import { VariablesTab, valueText } from './Variables';

const CmmnDiagram = lazy(() => import('../cmmn/CmmnDiagram'));

const TABS = ['items', 'tasks', 'variables', 'history'] as const;

/** Plan items in the order of the case: each one followed by what it contains. */
export function itemTree(executions: CaseExecution[]): { execution: CaseExecution; depth: number }[] {
  const children = new Map<string | null, CaseExecution[]>();
  const ids = new Set(executions.map((execution) => execution.id));
  for (const execution of executions) {
    const parent = execution.parentId && ids.has(execution.parentId) ? execution.parentId : null;
    children.set(parent, [...(children.get(parent) ?? []), execution]);
  }
  const result: { execution: CaseExecution; depth: number }[] = [];
  const visit = (parent: string | null, depth: number) => {
    for (const execution of children.get(parent) ?? []) {
      // the case instance itself is the root; its plan items start at the first level
      const own = execution.activityType === 'casePlanModel';
      if (!own) result.push({ execution, depth });
      visit(execution.id, own ? depth : depth + 1);
    }
  };
  visit(null, 0);
  return result;
}

/**
 * The state of each plan item for the diagram: what runs now, or else what
 * the history says happened last to it.
 */
export function itemStates(executions: CaseExecution[], activities: HistoricCaseActivity[]): Map<string, AnyCaseState> {
  const states = new Map<string, AnyCaseState>();
  for (const activity of activities) {
    states.set(activity.caseActivityId, historicActivityState(activity));
  }
  const rank: Record<string, number> = { active: 4, enabled: 3, disabled: 2, available: 1 };
  const running = new Map<string, AnyCaseState>();
  for (const execution of executions) {
    if (execution.activityType === 'casePlanModel') continue;
    const state = executionState(execution);
    const known = running.get(execution.activityId);
    if (!known || (rank[state] ?? 0) > (rank[known] ?? 0)) running.set(execution.activityId, state);
  }
  for (const [item, state] of running) states.set(item, state);
  return states;
}

const INSTANCE_ACTION_LABELS: Record<CaseInstanceAction, MessageKey> = {
  complete: 'cmmn.instance.complete',
  terminate: 'cmmn.instance.terminate',
  close: 'cmmn.instance.close',
};
const INSTANCE_ACTION_TITLES: Record<CaseInstanceAction, MessageKey> = {
  complete: 'cmmn.instance.completeTitle',
  terminate: 'cmmn.instance.terminateTitle',
  close: 'cmmn.instance.closeTitle',
};
const INSTANCE_ACTION_TEXTS: Record<CaseInstanceAction, MessageKey> = {
  complete: 'cmmn.instance.completeText',
  terminate: 'cmmn.instance.terminateText',
  close: 'cmmn.instance.closeText',
};
const INSTANCE_ACTION_DONE: Record<CaseInstanceAction, MessageKey> = {
  complete: 'cmmn.instance.done.complete',
  terminate: 'cmmn.instance.done.terminate',
  close: 'cmmn.instance.done.close',
};
const INSTANCE_ACTION_ICONS = { complete: CircleCheck, terminate: OctagonX, close: Lock };

function ItemFilter({ name, onClear }: { name: string; onClear: () => void }) {
  const { t } = useI18n();
  return (
    <p className="activity-filter" role="status">
      <span>{t('cockpit.instance.filteredBy', { name })}</span>
      <button type="button" className="link" onClick={onClear}>
        {t('cockpit.instance.showAll')}
      </button>
    </p>
  );
}

function ItemsTab({
  executions,
  error,
  onRetry,
  item,
  onClear,
}: {
  executions: CaseExecution[] | undefined;
  error: unknown;
  onRetry: () => void;
  item: string | null;
  onClear: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const typeLabel = useTypeLabel();
  const [asked, setAsked] = useState<{ execution: CaseExecution; action: ExecutionAction } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const nameOf = (execution: CaseExecution) => execution.activityName ?? execution.activityId;

  const perform = async (execution: CaseExecution, action: ExecutionAction) => {
    setBusy(`${execution.id} ${action}`);
    const ok = await run(() => runExecutionAction(execution.id, action), {
      success: t(ACTION_DONE[action], { name: nameOf(execution) }),
      failure: t('cmmn.item.failed', { name: nameOf(execution) }),
    });
    setBusy(null);
    return ok;
  };

  const rows = useMemo(() => {
    const tree = itemTree(executions ?? []);
    return item ? tree.filter((row) => row.execution.activityId === item) : tree;
  }, [executions, item]);

  const columns = useMemo<Column<{ execution: CaseExecution; depth: number }>[]>(
    () => [
      {
        id: 'name',
        header: t('cmmn.item.name'),
        cell: ({ execution, depth }) => (
          <span className="cmmn-item" style={{ paddingInlineStart: `${depth * 1.25}rem` }}>
            {nameOf(execution)}
          </span>
        ),
      },
      {
        id: 'type',
        header: t('cmmn.item.type'),
        secondary: true,
        cell: ({ execution }) => typeLabel(execution.activityType),
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: ({ execution }) => (
          <span className="badges">
            <CaseStateBadge state={executionState(execution)} />
            {execution.required && <span className="badge">{t('cmmn.item.required')}</span>}
          </span>
        ),
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: ({ execution }) => {
          const actions = executionActions(execution);
          if (actions.length === 0) return <span className="muted">—</span>;
          return (
            <span className="row-actions">
              {actions.map((action) => (
                <Button
                  key={action}
                  size="sm"
                  variant={action === 'terminate' ? 'danger-ghost' : action === 'manual-start' ? 'primary' : 'secondary'}
                  icon={ACTION_ICONS[action]}
                  busy={busy === `${execution.id} ${action}`}
                  disabled={!!busy && busy !== `${execution.id} ${action}`}
                  aria-label={t('cmmn.action.of', { action: t(ACTION_LABELS[action]), name: nameOf(execution) })}
                  onClick={() => (CONFIRMED_ACTIONS.has(action) ? setAsked({ execution, action }) : void perform(execution, action))}
                >
                  {t(ACTION_LABELS[action])}
                </Button>
              ))}
            </span>
          );
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- perform and nameOf only read the current state
    [t, typeLabel, busy],
  );

  const selectedName = item ? (rows[0]?.execution.activityName ?? item) : null;
  return (
    <>
      {item && selectedName && <ItemFilter name={selectedName} onClear={onClear} />}
      <DataTable
        label={t('cmmn.instance.tab.items')}
        columns={columns}
        rows={executions ? rows : undefined}
        rowKey={(row) => row.execution.id}
        error={error}
        onRetry={onRetry}
        exportable={false}
        empty={<EmptyState icon={CheckCheck} title={t('cmmn.items.empty')} />}
      />
      <ConfirmDialog
        open={!!asked}
        onOpenChange={(open) => !open && setAsked(null)}
        title={asked ? t(asked.action === 'complete' ? 'cmmn.item.completeTitle' : 'cmmn.item.terminateTitle') : ''}
        text={
          asked
            ? t(asked.action === 'complete' ? 'cmmn.item.completeText' : 'cmmn.item.terminateText', { name: nameOf(asked.execution) })
            : ''
        }
        confirmLabel={asked ? t(ACTION_LABELS[asked.action]) : ''}
        danger={asked?.action === 'terminate'}
        busy={!!busy}
        onConfirm={() => {
          if (!asked) return;
          void perform(asked.execution, asked.action).then((ok) => ok && setAsked(null));
        }}
      />
    </>
  );
}

function TasksTab({ caseInstanceId, open }: { caseInstanceId: string; open: boolean }) {
  const { t } = useI18n();
  const session = useSession();
  const refetchInterval = useRefreshInterval();
  const tasks = useQuery({
    queryKey: [COCKPIT, 'case-tasks', caseInstanceId],
    queryFn: ({ signal }) => getCaseTasks(caseInstanceId, signal),
    enabled: open,
    refetchInterval,
  });
  const tasklist = session.authorizedApps.includes('tasklist');
  const columns = useMemo<Column<Task>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.task.name'),
        cell: (task) => {
          const name = task.name ?? task.taskDefinitionKey ?? task.id;
          return tasklist ? (
            <Link className="cell-link" to={`/tasklist?task=${encodeURIComponent(task.id)}`}>
              {name}
            </Link>
          ) : (
            name
          );
        },
      },
      { id: 'assignee', header: t('task.assignee'), cell: (task) => task.assignee ?? <span className="muted">—</span> },
      { id: 'created', header: t('sort.created'), secondary: true, cell: (task) => <Time value={task.created} /> },
      { id: 'due', header: t('task.dueDate'), secondary: true, cell: (task) => <Time value={task.due} /> },
    ],
    [t, tasklist],
  );
  return (
    <DataTable
      label={t('cockpit.instance.tab.tasks')}
      columns={columns}
      rows={open ? tasks.data : []}
      rowKey={(task) => task.id}
      error={tasks.error}
      onRetry={() => void tasks.refetch()}
      empty={<EmptyState icon={CheckCheck} title={t('cmmn.tasks.empty')} />}
    />
  );
}

function HistoryTab({ activities, error, onRetry }: { activities: HistoricCaseActivity[] | null | undefined; error: unknown; onRetry: () => void }) {
  const { t, locale } = useI18n();
  const typeLabel = useTypeLabel();
  const columns = useMemo<Column<HistoricCaseActivity>[]>(
    () => [
      { id: 'name', header: t('cmmn.item.name'), cell: (activity) => activity.caseActivityName ?? activity.caseActivityId },
      { id: 'type', header: t('cmmn.item.type'), secondary: true, cell: (activity) => typeLabel(activity.caseActivityType) },
      { id: 'state', header: t('cockpit.state'), cell: (activity) => <CaseStateBadge state={historicActivityState(activity)} /> },
      { id: 'created', header: t('cmmn.instance.created'), cell: (activity) => <Time value={activity.createTime} /> },
      { id: 'ended', header: t('ops.history.ended'), secondary: true, cell: (activity) => <Time value={activity.endTime} /> },
      {
        id: 'duration',
        header: t('ops.history.duration'),
        align: 'end',
        secondary: true,
        cell: (activity) => (activity.durationInMillis == null ? <span className="muted">—</span> : formatDuration(activity.durationInMillis, locale)),
      },
    ],
    [t, typeLabel, locale],
  );
  if (activities === null) {
    return (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
        {t('cockpit.history.unavailableHint')}
      </EmptyState>
    );
  }
  return (
    <DataTable
      label={t('cmmn.instance.tab.history')}
      columns={columns}
      rows={activities}
      rowKey={(activity) => activity.id}
      error={error}
      onRetry={onRetry}
      empty={<EmptyState icon={History} title={t('cmmn.history.itemsEmpty')} />}
    />
  );
}

/** The variables of a closed case instance, read only, as the history kept them. */
function ClosedVariables({ caseInstanceId }: { caseInstanceId: string }) {
  const { t } = useI18n();
  const variables = useQuery({
    queryKey: [COCKPIT, 'case-variables-history', caseInstanceId],
    queryFn: ({ signal }) => getHistoricCaseVariables(caseInstanceId, signal),
  });
  const columns = useMemo<Column<HistoricCaseVariable>[]>(
    () => [
      { id: 'name', header: t('form.variable.name'), cell: (variable) => <span className="variable-name">{variable.name}</span> },
      { id: 'type', header: t('form.variable.type'), secondary: true, cell: (variable) => <span className="badge">{variable.type}</span> },
      {
        id: 'value',
        header: t('form.variable.value'),
        cell: (variable) => <span className="value__text">{variable.errorMessage ?? valueText(variable)}</span>,
      },
    ],
    [t],
  );
  if (variables.data === null) {
    return (
      <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
        {t('cockpit.history.unavailableHint')}
      </EmptyState>
    );
  }
  return (
    <>
      <p className="field__hint cmmn-note">{t('cmmn.variables.fromHistory')}</p>
      <DataTable
        label={t('cockpit.instance.tab.variables')}
        columns={columns}
        rows={variables.data}
        rowKey={(variable) => variable.id}
        error={variables.error}
        onRetry={() => void variables.refetch()}
        empty={<EmptyState icon={Braces} title={t('cockpit.variable.empty')} />}
      />
    </>
  );
}

/**
 * One case instance: its state, the plan items with what may be done with
 * them, its tasks, variables and history. A closed instance, which only the
 * history knows, is shown read only.
 */
export function CaseInstancePage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const run = useCockpitAction();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [asked, setAsked] = useState<CaseInstanceAction | null>(null);
  const [busy, setBusy] = useState(false);

  const runtime = useQuery({
    queryKey: [COCKPIT, 'case-instance', id],
    queryFn: ({ signal }) => getCaseInstance(id, signal),
    refetchInterval,
  });
  const history = useQuery({
    queryKey: [COCKPIT, 'case-instance-history', id],
    queryFn: ({ signal }) => getHistoricCaseInstance(id, signal),
    refetchInterval,
  });
  const closed = runtime.isError && isApiError(runtime.error, 'notFound');
  // a refetch that finds the instance closed keeps the data read before: it is not used any more
  const live = closed ? undefined : runtime.data;
  const definitionId = live?.caseDefinitionId ?? history.data?.caseDefinitionId;
  const definition = useQuery({
    queryKey: [COCKPIT, 'case-definition', definitionId],
    queryFn: () => getCaseDefinition(definitionId ?? ''),
    enabled: !!definitionId,
  });
  const xml = useQuery({
    queryKey: ['case-xml', definitionId],
    queryFn: () => getCaseXml(definitionId ?? ''),
    enabled: !!definitionId,
    staleTime: Infinity,
  });
  const executions = useQuery({
    queryKey: [COCKPIT, 'case-executions', id],
    queryFn: ({ signal }) => getCaseExecutions(id, signal),
    enabled: !!live,
    refetchInterval,
  });
  const activities = useQuery({
    queryKey: [COCKPIT, 'case-activities', id],
    queryFn: ({ signal }) => getCaseActivityHistory(id, signal),
    refetchInterval,
  });

  const states = useMemo(
    () => itemStates(live ? (executions.data ?? []) : [], activities.data ?? []),
    [live, executions.data, activities.data],
  );
  const names = useMemo(() => scopeNames(executions.data), [executions.data]);
  const markers = useMemo(() => [...states].map(([item, state]) => ({ id: item, marker: stateMarker(state) })), [states]);
  const item = params.get('item');
  const tab = TABS.find((candidate) => candidate === params.get('tab')) ?? 'items';
  const select = (next: string | null) => setParams({ item: next === item ? null : next });

  if ((runtime.isError && !closed) || (closed && history.isError && !history.data)) {
    return (
      <div className="page">
        <PageHeader title={t('cmmn.instance')} back={{ to: CASES_PATH, label: t('cmmn.definitions.title') }} />
        <Card>
          <ErrorState error={runtime.isError && !closed ? runtime.error : history.error} onRetry={() => void runtime.refetch()} />
        </Card>
      </div>
    );
  }
  if (closed && history.data === null) {
    return (
      <div className="page">
        <PageHeader title={t('cmmn.instance')} back={{ to: CASES_PATH, label: t('cmmn.definitions.title') }} />
        <Card>
          <EmptyState icon={SearchX} title={t('cmmn.instance.gone')}>
            {t('cmmn.instance.goneHint')}
          </EmptyState>
        </Card>
      </div>
    );
  }
  if (!live && !(closed && history.data)) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }

  const past = history.data ?? null;
  const state: CaseInstanceState = live ? caseInstanceState(live) : past ? historicCaseState(past) : 'closed';
  const businessKey = live?.businessKey ?? past?.businessKey ?? null;
  const tenantId = live?.tenantId ?? past?.tenantId ?? null;
  const name = definition.data?.name ?? definition.data?.key ?? past?.caseDefinitionName ?? past?.caseDefinitionKey ?? t('cmmn.instance');
  const actions = live ? caseInstanceActions(state) : [];
  const describe = (planItem: string) => {
    const known = states.get(planItem);
    return known ? t(caseStateLabel(known)) : null;
  };

  return (
    <div className="page">
      <PageHeader
        title={name}
        back={definitionId ? { to: caseDefinitionPath(definitionId), label: t('cmmn.instance.backToDefinition') } : { to: CASES_PATH, label: t('cmmn.definitions.title') }}
        badges={<CaseStateBadge state={state} />}
        subtitle={
          <>
            <span>
              {t('cmmn.instance')} <Id value={id} copy />
            </span>
            {businessKey && (
              <span>
                {t('process.businessKey')}: <strong>{businessKey}</strong>
              </span>
            )}
            {past?.createTime && (
              <span>
                {t('cmmn.instance.created')}: <Time value={past.createTime} />
                {past.createUserId && ` · ${past.createUserId}`}
              </span>
            )}
            {past?.closeTime && (
              <span>
                {t('cmmn.instance.closedAt')}: <Time value={past.closeTime} />
              </span>
            )}
            {definition.data && <span>{t('process.version', { version: definition.data.version })}</span>}
            {tenantId && <span>{t('cockpit.tenantOf', { tenant: tenantId })}</span>}
            {past?.superProcessInstanceId && (
              <Link className="link link--subtle" to={instancePath(past.superProcessInstanceId)}>
                <CornerLeftUp aria-hidden size={13} />
                {t('cmmn.instance.parentProcess')}
              </Link>
            )}
            {past?.superCaseInstanceId && (
              <Link className="link link--subtle" to={caseInstancePath(past.superCaseInstanceId)}>
                <CornerLeftUp aria-hidden size={13} />
                {t('cmmn.instance.parentCase')}
              </Link>
            )}
          </>
        }
        actions={
          actions.length > 0 ? (
            <>
              {actions.map((action) => (
                <Button
                  key={action}
                  icon={INSTANCE_ACTION_ICONS[action]}
                  variant={action === 'terminate' ? 'danger-ghost' : 'secondary'}
                  onClick={() => setAsked(action)}
                >
                  {t(INSTANCE_ACTION_LABELS[action])}
                </Button>
              ))}
            </>
          ) : undefined
        }
      />

      {!live && <Notice tone="info">{t('cmmn.instance.closedNote')}</Notice>}

      <Card flush className="model">
        {xml.isError ? (
          <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />
        ) : !xml.data ? (
          <Loading label={t('diagram.loading')}>
            <Skeleton lines={6} />
          </Loading>
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <CmmnDiagram
              xml={xml.data}
              caseKey={definition.data?.key ?? past?.caseDefinitionKey}
              markers={markers}
              selected={item ? [item] : []}
              onSelect={(next) => setParams({ item: next === item ? null : next, tab: null })}
              describe={describe}
              label={t('cmmn.instance.diagramLabel')}
            />
          </Suspense>
        )}
        <CaseLegend />
      </Card>
      <p className="model__hint instance-hint">{t('cmmn.instance.diagramHint')}</p>

      <Card flush id="case-tabs">
        <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setParams({ tab: next === 'items' ? null : next })}>
          <OverflowRow className="tabrow" selector='[role="tab"]' label={t('nav.more')}>
            <Tabs.List className="ctabs__list" aria-label={t('cmmn.instance.tabs')}>
              <Tabs.Trigger className="ctabs__tab" value="items">
                {t('cmmn.instance.tab.items')}
              </Tabs.Trigger>
              <Tabs.Trigger className="ctabs__tab" value="tasks">
                {t('cockpit.instance.tab.tasks')}
              </Tabs.Trigger>
              <Tabs.Trigger className="ctabs__tab" value="variables">
                {t('cockpit.instance.tab.variables')}
              </Tabs.Trigger>
              <Tabs.Trigger className="ctabs__tab" value="history">
                {t('cmmn.instance.tab.history')}
              </Tabs.Trigger>
            </Tabs.List>
          </OverflowRow>
          <Tabs.Content className="ctabs__content" value="items">
            {live ? (
              <ItemsTab
                executions={executions.data}
                error={executions.error}
                onRetry={() => void executions.refetch()}
                item={item}
                onClear={() => select(null)}
              />
            ) : (
              <EmptyState icon={Lock} title={t('cmmn.items.closed')} />
            )}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="tasks">
            <TasksTab caseInstanceId={id} open={!!live} />
          </Tabs.Content>
          <Tabs.Content className={cx('ctabs__content')} value="variables">
            {live ? <VariablesTab caseInstanceId={id} scopeNames={names} /> : <ClosedVariables caseInstanceId={id} />}
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="history">
            <HistoryTab activities={activities.data} error={activities.error} onRetry={() => void activities.refetch()} />
          </Tabs.Content>
        </Tabs.Root>
      </Card>

      <ConfirmDialog
        open={!!asked}
        onOpenChange={(open) => !open && setAsked(null)}
        title={asked ? t(INSTANCE_ACTION_TITLES[asked]) : ''}
        text={asked ? t(INSTANCE_ACTION_TEXTS[asked], { name }) : ''}
        confirmLabel={asked ? t(INSTANCE_ACTION_LABELS[asked]) : ''}
        danger={asked === 'terminate'}
        busy={busy}
        onConfirm={() => {
          if (!asked) return;
          setBusy(true);
          void run(() => runCaseInstanceAction(id, asked), {
            success: t(INSTANCE_ACTION_DONE[asked]),
            failure: t('cmmn.instance.failed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) setAsked(null);
          });
        }}
      />
    </div>
  );
}

/** Names of the plan items by the id of their case execution, for the scope of variables. */
function scopeNames(executions: CaseExecution[] | undefined): Map<string, string> {
  return new Map((executions ?? []).map((execution) => [execution.id, execution.activityName ?? execution.activityId]));
}
