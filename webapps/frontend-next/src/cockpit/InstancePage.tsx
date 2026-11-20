/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { settleLimited, throwIfAnyFailed } from '../lib/limit';
import { Suspense, lazy, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { DropdownMenu, Popover, Tabs } from 'radix-ui';
import {
  AlertTriangle,
  CheckCheck,
  CornerLeftUp,
  History,
  Shuffle,
  CopyPlus,
  Info,
  MoreHorizontal,
  Pause,
  Play,
  RotateCcw,
  CalendarClock,
  Send,
  Trash2,
  UserRound,
} from 'lucide-react';
import {
  deleteInstance,
  getActivityInstances,
  getCalledInstances,
  getDefinition,
  getExternalTasks,
  getInstance,
  getInstanceHistory,
  getInstanceTasks,
  getJobs,
  getScopedIncidents,
  getSuperInstance,
  setInstanceSuspended,
  setJobSuspended,
  setRetries,
  type ActivityInstance,
  type CalledInstance,
  type ExternalTask,
  type FailureKind,
  type IncidentSortBy,
  type Job,
  type ProcessInstance,
  type Sorting,
  getCalledInstance,
  getCopyableVariables,
  getJobDefinitions,
} from '../api/cockpit';
import { useSession } from '../app/session';
import { prefillStartForm } from '../forms/drafts';
import { getProcessXml, setTaskAssignee } from '../api/engine';
import { IdentityInput } from '../tasklist/IdentityInput';
import { isApiError } from '../api/http';
import type { Task } from '../api/types';
import type { ActivityBadge } from '../bpmn/BpmnDiagram';
import { useI18n } from '../i18n';
import { useToast } from '../ui/toast';
import { Button, EmptyState, ErrorState, Field, IconButton, Loading, Skeleton, cx } from '../ui';
import { MenuContent } from '../ui/menu';
import { COCKPIT, IncidentBadge, StateBadge, elementNames, totalIncidents, useCockpitAction, useRefreshInterval } from './common';
import { Checkbox, ConfirmDialog } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { pageOf, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { definitionPath } from './DefinitionsPage';
import { historyPath } from './paths';
import { FailureDetailsDialog, IncidentTable, RetryAllDialog, RetryDialog, instancePath, rollbacks } from './Incidents';
import { flowNodes } from './model';
import { ModifyDialog, runningActivities } from './ModifyDialog';
import { VariablesTab } from './Variables';
import { describeInstance, type InstanceState } from './summary';
import { InstanceTimeline } from './Timeline';
import { MessageDialog } from './MessageDialog';
import { JobDueDateDialog } from './JobDueDate';
import { OverflowRow } from '../app/OverflowMenu';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

// --- activity instances -----------------------------------------------------

interface TreeNode {
  id: string;
  activityId: string;
  name: string;
  /** a token that is about to enter or has just left the activity */
  transition: boolean;
  incidents: number;
  children: TreeNode[];
}

function toTree(instance: ActivityInstance, names: Map<string, string>, transition = false): TreeNode {
  return {
    id: instance.id,
    activityId: instance.activityId,
    name: instance.activityName ?? names.get(instance.activityId) ?? instance.activityId,
    transition,
    incidents: instance.incidents?.length ?? 0,
    children: [
      ...(instance.childActivityInstances ?? []).map((child) => toTree(child, names)),
      ...(instance.childTransitionInstances ?? []).map((child) => toTree(child, names, true)),
    ],
  };
}

function walk(node: TreeNode, visit: (node: TreeNode, root: boolean) => void, root = true): void {
  visit(node, root);
  node.children.forEach((child) => walk(child, visit, false));
}

function ActivityTree({
  node,
  selected,
  onSelect,
  root,
}: {
  node: TreeNode;
  selected: string | null;
  onSelect: (activityId: string | null) => void;
  root?: boolean;
}) {
  const { t } = useI18n();
  return (
    <li>
      <button
        type="button"
        className={cx('tree__node', !root && node.activityId === selected && 'is-selected')}
        aria-pressed={!root && node.activityId === selected}
        onClick={() => onSelect(root || node.activityId === selected ? null : node.activityId)}
      >
        <span className="tree__name">{node.name}</span>
        {node.transition && <span className="tree__hint">{t('cockpit.tree.transition')}</span>}
        {node.incidents > 0 && (
          <span className="tree__incident" title={t('cockpit.incidents.count', { count: node.incidents })}>
            <AlertTriangle aria-hidden size={13} />
            <span className="sr-only">{t('cockpit.incidents.count', { count: node.incidents })}</span>
          </span>
        )}
      </button>
      {node.children.length > 0 && (
        <ul className="tree__children">
          {node.children.map((child) => (
            <ActivityTree key={child.id} node={child} selected={selected} onSelect={onSelect} />
          ))}
        </ul>
      )}
    </li>
  );
}

// --- tabs -------------------------------------------------------------------

/** All incidents of an instance with their root causes, for actions on all of them. */
function loadAllIncidents(instanceId: string) {
  return async () =>
    (
      await getScopedIncidents({
        processInstanceId: instanceId,
        sorting: { sortBy: 'incidentTimestamp', sortOrder: 'asc' },
        paging: { firstResult: 0, maxResults: 1000 },
      })
    );
}

function InstanceIncidentsTab({
  instanceId,
  names,
  activity,
  superId,
}: {
  instanceId: string;
  names: Map<string, string>;
  activity: string | null;
  superId: string | null;
}) {
  const { t } = useI18n();
  const [retrying, setRetrying] = useState(false);
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<IncidentSortBy>>('incidents.sort', {
    sortBy: 'incidentTimestamp',
    sortOrder: 'desc',
  });
  const page = pageOf(params);
  const incidents = useQuery({
    queryKey: [COCKPIT, 'incidents', 'instance', instanceId, activity, sorting, page],
    queryFn: ({ signal }) =>
      getScopedIncidents({
        processInstanceId: instanceId,
        activityIdIn: activity ? [activity] : undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  return (
    <>
      <div className="toolbar">
        {superId && (
          <Link className="link link--subtle" to={`${instancePath(superId)}?tab=incidents`}>
            <CornerLeftUp aria-hidden size={13} />
            {t('incidents.up')}
          </Link>
        )}
        <span className="toolbar__spacer" />
        <Button
          size="sm"
          icon={RotateCcw}
          disabled={!incidents.data?.count}
          onClick={() => setRetrying(true)}
        >
          {t('incidents.retryAll')}
        </Button>
      </div>
      {retrying && (
        <RetryAllDialog scope={instanceId} load={loadAllIncidents(instanceId)} onClose={() => setRetrying(false)} />
      )}
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
      />
      <Pager
        page={page}
        count={incidents.data?.count ?? 0}
        onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
      />
    </>
  );
}

function CalledInstancesTab({ instanceId, names }: { instanceId: string; names: Map<string, string> }) {
  const { t } = useI18n();
  const [retrying, setRetrying] = useState<string | null>(null);
  const refetchInterval = useRefreshInterval();
  const called = useQuery({
    queryKey: [COCKPIT, 'called-instances', instanceId],
    queryFn: () => getCalledInstances(instanceId),
    refetchInterval,
  });
  const columns = useMemo<Column<CalledInstance>[]>(
    () => [
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
        id: 'definition',
        header: t('cockpit.called.definition'),
        cell: (instance) => (
          <Link className="cell-link" to={definitionPath(instance.processDefinitionId)}>
            {instance.processDefinitionName ?? instance.processDefinitionKey}
          </Link>
        ),
      },
      {
        id: 'activity',
        header: t('cockpit.called.from'),
        secondary: true,
        cell: (instance) => names.get(instance.callActivityId) ?? instance.callActivityId,
      },
      {
        id: 'businessKey',
        header: t('process.businessKey'),
        cell: (instance) => instance.businessKey ?? <span className="muted">—</span>,
      },
      {
        id: 'incidents',
        header: t('cockpit.incidents.title'),
        align: 'end',
        cell: (instance) => {
          const count = totalIncidents(instance.incidents);
          return count > 0 ? (
            <span className="row-actions">
              <Link className="cell-link" to={`${instancePath(instance.id)}?tab=incidents`}>
                <IncidentBadge count={count} />
              </Link>
              <Button size="sm" icon={RotateCcw} aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: instance.id })} onClick={() => setRetrying(instance.id)}>
                {t('cockpit.retry.action')}
              </Button>
            </span>
          ) : (
            <IncidentBadge count={0} />
          );
        },
      },
    ],
    [t, names],
  );
  return (
    <>
      <DataTable
        label={t('cockpit.instance.tab.called')}
        columns={columns}
        rows={called.data}
        rowKey={(instance) => instance.id}
        error={called.error}
        onRetry={() => void called.refetch()}
        empty={<EmptyState title={t('cockpit.called.instancesEmpty')} />}
      />
      {retrying && (
        <RetryAllDialog scope={retrying} load={loadAllIncidents(retrying)} onClose={() => setRetrying(null)} />
      )}
    </>
  );
}

function AssigneeButton({ task }: { task: Task }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [open, setOpen] = useState(false);
  const [userId, setUserId] = useState(task.assignee ?? '');
  const [busy, setBusy] = useState(false);

  const assign = (next: string | null) => {
    setBusy(true);
    void run(() => setTaskAssignee(task.id, next), {
      success: next ? t('task.assigned', { user: next }) : t('task.assigneeRemoved'),
      failure: t('task.assignFailed'),
    }).then((ok) => {
      setBusy(false);
      if (ok) setOpen(false);
    });
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (userId.trim()) {
      assign(userId.trim());
    }
  };

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger asChild>
        <button type="button" className="chip-btn" aria-label={t('cockpit.task.changeAssignee', { task: task.name ?? task.id })}>
          <UserRound aria-hidden size={14} />
          {task.assignee ?? t('task.unassigned')}
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content className="popover" align="start" sideOffset={6}>
          <form className="popover__form" onSubmit={submit}>
            <Field label={t('task.assignee.userId')} hint={t('task.assignee.hint')}>
              {(props) => <IdentityInput {...props} kind="user" autoFocus value={userId} onChange={setUserId} />}
            </Field>
            <div className="popover__actions">
              {task.assignee && (
                <Button size="sm" disabled={busy} onClick={() => assign(null)}>
                  {t('task.assignee.remove')}
                </Button>
              )}
              <Button type="submit" size="sm" variant="primary" busy={busy} disabled={!userId.trim()}>
                {t('action.save')}
              </Button>
            </div>
          </form>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

/** Says that a tab shows only what belongs to the activity chosen in the diagram. */
function ActivityFilter({ name, onClear }: { name: string; onClear: () => void }) {
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

function TasksTab({ instanceId, activity, names, onClear }: ActivityTabProps) {
  const { t } = useI18n();
  const refetchInterval = useRefreshInterval();
  const tasks = useQuery({
    queryKey: [COCKPIT, 'instance-tasks', instanceId],
    queryFn: () => getInstanceTasks(instanceId),
    refetchInterval,
  });
  const columns = useMemo<Column<Task>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.task.name'),
        cell: (task) => task.name ?? task.taskDefinitionKey ?? task.id,
      },
      { id: 'assignee', header: t('task.assignee'), cell: (task) => <AssigneeButton task={task} /> },
      {
        id: 'created',
        header: t('sort.created'),
        secondary: true,
        cell: (task) => <Time value={task.created} />,
      },
      { id: 'due', header: t('task.dueDate'), secondary: true, cell: (task) => <Time value={task.due} /> },
      {
        id: 'priority',
        header: t('task.priority'),
        align: 'end',
        secondary: true,
        cell: (task) => task.priority,
      },
    ],
    [t],
  );
  const rows = activity ? tasks.data?.filter((task) => task.taskDefinitionKey === activity) : tasks.data;
  return (
    <>
    {activity && <ActivityFilter name={names.get(activity) ?? activity} onClear={onClear} />}
    <DataTable
      label={t('cockpit.instance.tab.tasks')}
      columns={columns}
      rows={rows}
      rowKey={(task) => task.id}
      error={tasks.error}
      onRetry={() => void tasks.refetch()}
      empty={<EmptyState icon={CheckCheck} title={t('cockpit.task.empty')} />}
    />
    </>
  );
}

interface ActivityTabProps {
  instanceId: string;
  names: Map<string, string>;
  /** the activity chosen in the diagram or the tree */
  activity: string | null;
  onClear: () => void;
}

function JobsTab({ instanceId, names, activity: chosen, onClear, definitionId }: ActivityTabProps & { definitionId: string }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const refetchInterval = useRefreshInterval();
  const [details, setDetails] = useState<string | null>(null);
  const [retry, setRetry] = useState<{ id: string; what: string } | null>(null);
  const [due, setDue] = useState<Job | null>(null);
  const jobs = useQuery({
    queryKey: [COCKPIT, 'instance-jobs', instanceId],
    queryFn: () => getJobs({ processInstanceId: instanceId }),
    refetchInterval,
  });
  // a job belongs to the activity of its definition; one that never failed has no failed activity
  const definitions = useQuery({
    queryKey: [COCKPIT, 'job-definitions', definitionId],
    queryFn: () => getJobDefinitions(definitionId),
    enabled: !!chosen,
    staleTime: 60_000,
  });
  const shown = useMemo(() => {
    if (!chosen) return jobs.data;
    const activityOf = new Map((definitions.data ?? []).map((definition) => [definition.id, definition.activityId]));
    return jobs.data?.filter(
      (job) => (job.jobDefinitionId ? activityOf.get(job.jobDefinitionId) : null) === chosen || job.failedActivityId === chosen,
    );
  }, [chosen, jobs.data, definitions.data]);
  const failed = (shown ?? []).filter((job) => job.retries === 0);
  const [retryingAll, setRetryingAll] = useState(false);

  const columns = useMemo<Column<Job>[]>(() => {
    const activity = (job: Job) =>
      job.failedActivityId ? (names.get(job.failedActivityId) ?? job.failedActivityId) : null;
    return [
      {
        id: 'id',
        header: t('cockpit.job.id'),
        cell: (job) => <Id value={job.id} />,
      },
      { id: 'due', header: t('cockpit.job.due'), cell: (job) => <Time value={job.dueDate} /> },
      {
        id: 'retries',
        header: t('cockpit.retry.retries'),
        align: 'end',
        cell: (job) =>
          job.retries === 0 ? <span className="badge badge--danger">0</span> : job.retries,
      },
      {
        id: 'error',
        header: t('cockpit.job.error'),
        cell: (job) =>
          job.exceptionMessage ? (
            <div className="incident">
              <span className="incident__message">{job.exceptionMessage}</span>
              <span className="incident__links">
                <button type="button" className="link link--subtle" onClick={() => setDetails(job.id)}>
                  {t('cockpit.incident.details')}
                </button>
              </span>
            </div>
          ) : (
            <span className="muted">—</span>
          ),
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        secondary: true,
        cell: (job) => <StateBadge suspended={job.suspended} />,
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (job) => (
          <span className="row-actions">
            <Button
              size="sm"
              icon={RotateCcw}
              aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: activity(job) ?? job.id })}
              onClick={() => setRetry({ id: job.id, what: activity(job) ?? job.id })}
            >
              {t('cockpit.retry.action')}
            </Button>
            <Button
              size="sm"
              icon={job.suspended ? Play : Pause}
              aria-label={t('a11y.rowAction', { action: job.suspended ? t('cockpit.activate') : t('cockpit.suspend'), row: activity(job) ?? job.id })}
              onClick={() =>
                void run(() => setJobSuspended(job.id, !job.suspended), {
                  success: job.suspended ? t('cockpit.job.activate.done') : t('cockpit.job.suspend.done'),
                  failure: t('cockpit.job.stateFailed'),
                })
              }
            >
              {job.suspended ? t('cockpit.activate') : t('cockpit.suspend')}
            </Button>
            <Button size="sm" icon={CalendarClock} onClick={() => setDue(job)}>
              {t('jobDue.action')}
            </Button>
          </span>
        ),
      },
    ];
  }, [t, names, run]);

  return (
    <>
      {due && <JobDueDateDialog key={due.id} job={due} onClose={() => setDue(null)} />}
      {failed.length > 1 && (
        <div className="toolbar">
          <span className="toolbar__text">{t('cockpit.job.failedCount', { count: failed.length })}</span>
          <Button
            icon={RotateCcw}
            busy={retryingAll}
            onClick={() => {
              setRetryingAll(true);
              void run(() => settleLimited(failed, (job) => setRetries('job', job.id, 1)).then(throwIfAnyFailed), {
                success: t('cockpit.retry.done'),
                failure: t('cockpit.retry.failed'),
              }).then(() => setRetryingAll(false));
            }}
          >
            {t('cockpit.job.retryAll')}
          </Button>
        </div>
      )}
      {chosen && <ActivityFilter name={names.get(chosen) ?? chosen} onClear={onClear} />}
      <DataTable
        label={t('cockpit.instance.tab.jobs')}
        columns={columns}
        rows={chosen && definitions.isPending ? undefined : shown}
        rowKey={(job) => job.id}
        error={jobs.error ?? (chosen ? definitions.error : null)}
        onRetry={() => {
          void jobs.refetch();
          if (definitions.isError) void definitions.refetch();
        }}
        empty={<EmptyState title={t('cockpit.job.empty')}>{t('cockpit.job.emptyHint')}</EmptyState>}
      />
      <FailureDetailsDialog
        target={details ? { kind: 'job', id: details } : null}
        onClose={() => setDetails(null)}
      />
      <RetryDialog
        key={retry?.id ?? 'none'}
        target={retry ? { kind: 'job', id: retry.id } : null}
        what={retry?.what ?? ''}
        onClose={() => setRetry(null)}
      />
    </>
  );
}

function ExternalTasksTab({ instanceId, names, activity: chosen, onClear }: ActivityTabProps) {
  const { t } = useI18n();
  const refetchInterval = useRefreshInterval();
  const [details, setDetails] = useState<string | null>(null);
  const [retry, setRetry] = useState<{ kind: FailureKind; id: string; what: string } | null>(null);
  const tasks = useQuery({
    queryKey: [COCKPIT, 'instance-external-tasks', instanceId],
    queryFn: () => getExternalTasks(instanceId),
    refetchInterval,
  });
  const columns = useMemo<Column<ExternalTask>[]>(() => {
    const activity = (task: ExternalTask) =>
      task.activityId ? (names.get(task.activityId) ?? task.activityId) : '—';
    return [
      { id: 'activity', header: t('cockpit.activity'), cell: activity },
      {
        id: 'topic',
        header: t('cockpit.external.topic'),
        cell: (task) => <span className="mono">{task.topicName ?? '—'}</span>,
      },
      {
        id: 'worker',
        header: t('cockpit.external.worker'),
        secondary: true,
        cell: (task) => task.workerId ?? <span className="muted">—</span>,
      },
      {
        id: 'lock',
        header: t('cockpit.external.lockedUntil'),
        secondary: true,
        cell: (task) => <Time value={task.lockExpirationTime} />,
      },
      {
        id: 'retries',
        header: t('cockpit.retry.retries'),
        align: 'end',
        cell: (task) =>
          task.retries === 0 ? (
            <span className="badge badge--danger">0</span>
          ) : (
            (task.retries ?? <span className="muted">—</span>)
          ),
      },
      {
        id: 'error',
        header: t('cockpit.job.error'),
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
            <span className="muted">—</span>
          ),
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (task) => (
          <Button
            size="sm"
            icon={RotateCcw}
            aria-label={t('a11y.rowAction', { action: t('cockpit.retry.action'), row: activity(task) ?? task.id })}
            onClick={() => setRetry({ kind: 'externalTask', id: task.id, what: activity(task) })}
          >
            {t('cockpit.retry.action')}
          </Button>
        ),
      },
    ];
  }, [t, names]);
  return (
    <>
      {chosen && <ActivityFilter name={names.get(chosen) ?? chosen} onClear={onClear} />}
      <DataTable
        label={t('cockpit.instance.tab.external')}
        columns={columns}
        rows={chosen ? tasks.data?.filter((task) => task.activityId === chosen) : tasks.data}
        rowKey={(task) => task.id}
        error={tasks.error}
        onRetry={() => void tasks.refetch()}
        empty={<EmptyState title={t('cockpit.external.empty')}>{t('cockpit.external.emptyHint')}</EmptyState>}
      />
      <FailureDetailsDialog
        target={details ? { kind: 'externalTask', id: details } : null}
        onClose={() => setDetails(null)}
      />
      <RetryDialog
        key={retry?.id ?? 'none'}
        target={retry}
        what={retry?.what ?? ''}
        onClose={() => setRetry(null)}
      />
    </>
  );
}

// --- actions of the instance ------------------------------------------------

function DeleteInstanceDialog({
  instance,
  onClose,
  onDeleted,
}: {
  instance: ProcessInstance;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [skipSubprocesses, setSkipSubprocesses] = useState(false);
  const [busy, setBusy] = useState(false);
  const what = instance.businessKey ? `${instance.id} (${instance.businessKey})` : instance.id;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.instance.delete.title')}
      text={t('cockpit.instance.delete.text', { what })}
      confirmLabel={t('cockpit.instance.delete.confirm')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(
          () =>
            deleteInstance(instance.id, {
              skipCustomListeners: skipListeners,
              skipIoMappings: skipMappings,
              skipSubprocesses,
            }),
          { success: t('cockpit.instance.delete.done'), failure: t('cockpit.instance.delete.failed') },
        ).then((ok) => {
          setBusy(false);
          if (ok) onDeleted();
        });
      }}
    >
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
      <Checkbox
        label={t('cockpit.delete.skipSubprocesses')}
        hint={t('cockpit.delete.skipSubprocessesHint')}
        checked={skipSubprocesses}
        onChange={setSkipSubprocesses}
      />
    </ConfirmDialog>
  );
}

function SuspendInstanceDialog({ instance, onClose }: { instance: ProcessInstance; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [busy, setBusy] = useState(false);
  const suspend = !instance.suspended;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={suspend ? t('cockpit.instance.suspend.title') : t('cockpit.instance.activate.title')}
      text={t(suspend ? 'cockpit.instance.suspend.text' : 'cockpit.instance.activate.text', {
        what: instance.id,
      })}
      confirmLabel={suspend ? t('cockpit.suspend') : t('cockpit.activate')}
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(() => setInstanceSuspended(instance.id, suspend), {
          success: suspend ? t('cockpit.instance.suspend.done') : t('cockpit.instance.activate.done'),
          failure: t('cockpit.instance.stateFailed'),
        }).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    />
  );
}

// --- page -------------------------------------------------------------------

const CaseTimeline = lazy(() => import('../tasklist/CaseTimeline').then((module) => ({ default: module.CaseTimeline })));

const TABS = ['variables', 'incidents', 'called', 'tasks', 'jobs', 'external', 'timeline', 'case'] as const;

/** A running instance: where it stands, what it carries and what went wrong. */
function InstanceSummary({
  state,
  onShowIncidents,
  rolledBack = [],
}: {
  state: InstanceState;
  onShowIncidents?: () => void;
  /** names of where an error was thrown and where the instance waits since */
  rolledBack?: { failed: string; stopped: string }[];
}) {
  const { t, locale } = useI18n();
  const { tone, text } = describeInstance(state, locale, t);
  const SummaryIcon = tone === 'danger' ? AlertTriangle : tone === 'warning' ? Pause : Info;
  return (
    <div className={cx('instance-summary', `instance-summary--${tone}`)}>
      <SummaryIcon aria-hidden size={16} />
      <div className="instance-summary__text">
        <p>{text}</p>
        {rolledBack.map((entry) => (
          <p key={`${entry.failed} ${entry.stopped}`} className="instance-summary__rollback">
            {t('cockpit.summary.rolledBack', { failed: entry.failed, stopped: entry.stopped })}
          </p>
        ))}
      </div>
      {tone === 'danger' && onShowIncidents && (
        <Button variant="secondary" size="sm" onClick={onShowIncidents}>
          {t('cockpit.summary.showIncidents')}
        </Button>
      )}
    </div>
  );
}

export function InstancePage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const toast = useToast();
  const session = useSession();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [dialog, setDialog] = useState<'suspend' | 'delete' | 'modify' | 'message' | null>(null);

  const instance = useQuery({
    queryKey: [COCKPIT, 'instance', id],
    queryFn: () => getInstance(id),
    refetchInterval,
  });
  const definitionId = instance.data?.definitionId;
  const definition = useQuery({
    queryKey: [COCKPIT, 'definition', definitionId],
    queryFn: () => getDefinition(definitionId ?? ''),
    enabled: !!definitionId,
  });
  const history = useQuery({
    queryKey: [COCKPIT, 'instance-history', id],
    queryFn: () => getInstanceHistory(id),
    staleTime: Infinity,
  });
  const parent = useQuery({
    queryKey: [COCKPIT, 'super-instance', id],
    queryFn: () => getSuperInstance(id),
    staleTime: Infinity,
  });
  const xml = useQuery({
    queryKey: ['process-xml', definitionId],
    queryFn: () => getProcessXml(definitionId ?? ''),
    enabled: !!definitionId,
    staleTime: Infinity,
  });
  const activities = useQuery({
    queryKey: [COCKPIT, 'activity-instances', id],
    queryFn: ({ signal }) => getActivityInstances(id, signal),
    enabled: !!instance.data,
    refetchInterval,
  });

  const names = useMemo(() => elementNames(xml.data), [xml.data]);
  const nodes = useMemo(() => flowNodes(xml.data), [xml.data]);
  const tree = useMemo(() => (activities.data ? toTree(activities.data, names) : null), [activities.data, names]);
  const { badges, scopeNames, incidentCount, failing, waiting } = useMemo(() => {
    const byActivity = new Map<string, ActivityBadge>();
    const scopes = new Map<string, string>();
    const failingNames: string[] = [];
    const waitingNames: string[] = [];
    let incidents = 0;
    if (tree) {
      walk(tree, (node, root) => {
        scopes.set(node.id, node.name);
        incidents += node.incidents;
        if (node.incidents > 0) failingNames.push(node.name);
        if (root) {
          return;
        }
        if (node.children.length === 0) waitingNames.push(node.name);
        const entry = byActivity.get(node.activityId) ?? {
          activityId: node.activityId,
          instances: 0,
          incidents: 0,
        };
        // a scope counts through its children, a leaf is a token of its own
        if (node.children.length === 0) {
          entry.instances += 1;
        }
        entry.incidents += node.incidents;
        byActivity.set(node.activityId, entry);
      });
    }
    return {
      badges: [...byActivity.values()],
      scopeNames: scopes,
      incidentCount: incidents,
      failing: failingNames,
      waiting: waitingNames,
    };
  }, [tree]);

  // where the errors were thrown, when that is not where the instance stopped
  const failedAt = useQuery({
    queryKey: [COCKPIT, 'instance-failed-at', id, incidentCount],
    queryFn: ({ signal }) =>
      getScopedIncidents({
        processInstanceId: id,
        sorting: { sortBy: 'incidentTimestamp', sortOrder: 'desc' },
        paging: { firstResult: 0, maxResults: 50 },
        signal,
      }),
    enabled: incidentCount > 0,
    refetchInterval,
  });
  const rolledBack = useMemo(() => rollbacks(failedAt.data?.items ?? []), [failedAt.data]);
  const failedMarkers = useMemo(
    () => rolledBack.map((entry) => ({ activityId: entry.failed, marker: 'is-failed' })),
    [rolledBack],
  );

  const selected = params.get('act');
  const selectedNode = nodes.find((node) => node.id === selected);
  // a new instance of the same version, with the simple variables of this one in its start form
  const startLikeThis = async (definitionId: string) => {
    try {
      prefillStartForm(session.userId, definitionId, await getCopyableVariables(id));
      void navigate(`/processes?definition=${encodeURIComponent(definitionId)}`);
    } catch (error) {
      toast.error(error, t('cockpit.instance.startLikeFailed'));
    }
  };
  const openCalled = async (activityId: string) => {
    try {
      const called = await getCalledInstance(id, activityId);
      if (called) void navigate(instancePath(called));
      else toast.show({ title: t('cockpit.instance.noCalled') });
    } catch (error) {
      toast.error(error, t('cockpit.instance.noCalled'));
    }
  };
  const tab = TABS.find((candidate) => candidate === params.get('tab')) ?? 'variables';
  const select = (activityId: string | null) =>
    setParams({ act: activityId === selected ? null : activityId, page: null });

  if (instance.isError && !instance.data) {
    return (
      <div className="page">
        <PageHeader
          title={t('cockpit.instance')}
          back={{ to: '/cockpit/processes', label: t('cockpit.definitions.title') }}
        />
        <Card>
          {isApiError(instance.error, 'notFound') ? (
            <EmptyState icon={CheckCheck} title={t('cockpit.instance.gone')}>
              {t('cockpit.instance.goneHint')}
            </EmptyState>
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
  const definitionName = definition.data?.name ?? definition.data?.key ?? t('cockpit.instance');
  const superId = parent.data?.id ?? history.data?.superProcessInstanceId ?? null;
  const rootId = history.data?.rootProcessInstanceId ?? null;

  return (
    <div className="page">
      <PageHeader
        title={definitionName}
        back={{ to: definitionPath(current.definitionId), label: t('cockpit.instance.backToDefinition') }}
        badges={
          <>
            {/* "active" beside an incident reads as all is well; as in the list, the incidents say it */}
            {(current.suspended || incidentCount === 0) && <StateBadge suspended={current.suspended} />}
            <IncidentBadge count={incidentCount} />
          </>
        }
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
            {history.data?.startTime && (
              <span>
                {t('cockpit.instance.started')}: <Time value={history.data.startTime} />
                {history.data.startUserId && ` · ${history.data.startUserId}`}
              </span>
            )}
            {definition.data && (
              <span>{t('process.version', { version: definition.data.version })}</span>
            )}
            {superId && (
              <Link className="link link--subtle" to={instancePath(superId)}>
                <CornerLeftUp aria-hidden size={13} />
                {t('cockpit.instance.parent')}
              </Link>
            )}
            {rootId && rootId !== current.id && rootId !== superId && (
              <Link className="link link--subtle" to={instancePath(rootId)}>
                <CornerLeftUp aria-hidden size={13} />
                {t('cockpit.instance.root')}
              </Link>
            )}
          </>
        }
        actions={
          <>
            <Button icon={Shuffle} disabled={!xml.data} onClick={() => setDialog('modify')}>
              {t('ops.modify.action')}
            </Button>
            <Link className="btn btn--secondary btn--md" to={historyPath(current.id)}>
              <History aria-hidden size={16} className="btn__icon" />
              {t('ops.history.action')}
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
                  {session.authorizedApps.includes('tasklist') && (
                    <DropdownMenu.Item className="menu__item" onSelect={() => void startLikeThis(current.definitionId)}>
                      <CopyPlus aria-hidden size={16} />
                      {t('cockpit.instance.startLike')}
                    </DropdownMenu.Item>
                  )}
                  <DropdownMenu.Item className="menu__item" onSelect={() => setDialog('message')}>
                    <Send aria-hidden size={16} />
                    {t('message.action')}
                  </DropdownMenu.Item>
                  <DropdownMenu.Separator className="menu__separator" />
                  <DropdownMenu.Item className="menu__item menu__item--danger" onSelect={() => setDialog('delete')}>
                    <Trash2 aria-hidden size={16} />
                    {t('cockpit.instance.delete.action')}
                  </DropdownMenu.Item>
                </MenuContent>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          </>
        }
      />

      {tree && (
        <InstanceSummary
          state={{ suspended: current.suspended, failing, waiting }}
          rolledBack={rolledBack.map((entry) => ({
            failed: names.get(entry.failed) ?? entry.failed,
            stopped: names.get(entry.stopped) ?? entry.stopped,
          }))}
          onShowIncidents={() => {
            setParams({ tab: 'incidents', page: null });
            document.getElementById('instance-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }}
        />
      )}

      <p className="model__hint instance-hint">
        {t('cockpit.instance.diagramHint')}
        {selectedNode?.type === 'callActivity' && (
          <>
            {' '}
            <button type="button" className="link" onClick={() => void openCalled(selectedNode.id)}>
              {t('cockpit.instance.openCalled', { name: selectedNode.name })}
            </button>
          </>
        )}
      </p>

      <div className="instance-view">
        <Card title={t('cockpit.tree.title')} className="instance-view__tree">
          {activities.isError && !tree ? (
            <ErrorState error={activities.error} onRetry={() => void activities.refetch()} />
          ) : !tree ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={4} />
            </Loading>
          ) : (
            <ul className="tree" aria-label={t('cockpit.tree.title')}>
              <ActivityTree node={tree} selected={selected} onSelect={select} root />
            </ul>
          )}
        </Card>
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
                processKey={definition.data?.key}
                badges={badges}
                markers={failedMarkers}
                selected={selected ? [selected] : []}
                onSelect={select}
                onOpen={(activityId, type) => {
                  if (type === 'bpmn:CallActivity') void openCalled(activityId);
                }}
                label={t('cockpit.instance.diagramLabel')}
              />
            </Suspense>
          )}
        </Card>
      </div>

      <Card flush id="instance-tabs">
        <Tabs.Root
          className="ctabs"
          value={tab}
          onValueChange={(next) => setParams({ tab: next === 'variables' ? null : next, page: null })}
        >
          <OverflowRow className="tabrow" selector='[role="tab"]' label={t('nav.more')}>

          <Tabs.List className="ctabs__list" aria-label={t('cockpit.instance.tabs')}>
            <Tabs.Trigger className="ctabs__tab" value="variables">
              {t('cockpit.instance.tab.variables')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="incidents">
              {t('cockpit.incidents.title')}
              {incidentCount > 0 && <span className="count count--danger">{incidentCount}</span>}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="called">
              {t('cockpit.instance.tab.called')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="tasks">
              {t('cockpit.instance.tab.tasks')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="jobs">
              {t('cockpit.instance.tab.jobs')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="external">
              {t('cockpit.instance.tab.external')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="timeline">
              {t('timeline.title')}
            </Tabs.Trigger>
            <Tabs.Trigger className="ctabs__tab" value="case">
              {t('task.tab.case')}
            </Tabs.Trigger>
          </Tabs.List>
          </OverflowRow>
          <Tabs.Content className="ctabs__content" value="variables">
            <VariablesTab processInstanceId={id} scopeNames={scopeNames} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="incidents">
            <InstanceIncidentsTab instanceId={id} names={names} activity={selected} superId={superId} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="called">
            <CalledInstancesTab instanceId={id} names={names} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="tasks">
            <TasksTab instanceId={id} names={names} activity={selected} onClear={() => select(null)} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="jobs">
            <JobsTab
              instanceId={id}
              definitionId={current.definitionId}
              names={names}
              activity={selected}
              onClear={() => select(null)}
            />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="external">
            <ExternalTasksTab instanceId={id} names={names} activity={selected} onClear={() => select(null)} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="timeline">
            <InstanceTimeline instanceId={id} names={names} />
          </Tabs.Content>
          <Tabs.Content className="ctabs__content" value="case">
            <Suspense fallback={<Loading label={t('case.loading')} />}>
              <div className="case-pane">
              <CaseTimeline processInstanceId={id} />
            </div>
            </Suspense>
          </Tabs.Content>
        </Tabs.Root>
      </Card>

      {dialog === 'modify' && (
        <ModifyDialog
          target={{ kind: 'instance', id: current.id }}
          nodes={nodes}
          running={runningActivities(activities.data, names)}
          initialActivity={selected}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'message' && (
        <MessageDialog processInstanceId={current.id} onClose={() => setDialog(null)} />
      )}
      {dialog === 'suspend' && <SuspendInstanceDialog instance={current} onClose={() => setDialog(null)} />}
      {dialog === 'delete' && (
        <DeleteInstanceDialog
          instance={current}
          onClose={() => setDialog(null)}
          onDeleted={() => void navigate(definitionPath(current.definitionId))}
        />
      )}
    </div>
  );
}
