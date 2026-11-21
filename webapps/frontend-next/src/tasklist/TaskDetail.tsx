/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { ArrowLeft, BookOpen, CheckCheck, FileText, FolderKanban, Hand, MessageSquare, Undo2, Workflow } from 'lucide-react';
import { Link } from 'react-router';
import { getProcessDefinition, getProcessXml, getTask, getTaskComments, getTaskOperations } from '../api/engine';
import { getCaseDefinition, getCaseXml } from '../api/cases';
import { isApiError } from '../api/http';
import type { Task, TaskListItem } from '../api/types';
import { useSession } from '../app/session';
import { FormHost } from '../forms/FormHost';
import '../forms/registry';
import { useI18n, type MessageKey } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { pluginsFor } from '../plugins/registry';
import { Button, EmptyState, ErrorState, IconButton, Loading, Skeleton } from '../ui';
import { useToast } from '../ui/toast';
import { refreshTasks, type TaskActions } from './actions';
import { AttachmentsTab } from './Attachments';
import { TaskMeta } from './TaskMeta';
import { CaseTimeline } from './CaseTimeline';
import { DocumentationText, modelDocumentation } from '../lib/documentation';

import { keyOfDefinitionId } from '../bpmn/diagrams';
import { OverflowRow } from '../app/OverflowMenu';
import { SubstituteNotice } from '../absence/SubstituteNotice';
import { FourEyesNotice, useFourEyes } from './FourEyesNotice';
import { caseInstancePath } from '../cockpit/paths';
import { planItemsOf, parseCaseDiagram } from '../cmmn/model';
const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));
const CmmnDiagram = lazy(() => import('../cmmn/CmmnDiagram'));

const OPERATIONS: Record<string, MessageKey> = {
  Claim: 'history.op.claim',
  Assign: 'history.op.assign',
  Complete: 'history.op.complete',
  Create: 'history.op.create',
  Update: 'history.op.update',
  Delegate: 'history.op.delegate',
  Resolve: 'history.op.resolve',
  SetOwner: 'history.op.setOwner',
  SetPriority: 'history.op.setPriority',
  AddGroupLink: 'history.op.addGroup',
  DeleteGroupLink: 'history.op.removeGroup',
  AddUserLink: 'history.op.addUser',
  DeleteUserLink: 'history.op.removeUser',
  AddAttachment: 'history.op.addAttachment',
  DeleteAttachment: 'history.op.deleteAttachment',
};

function HistoryTab({ task, actions }: { task: Task; actions: TaskActions }) {
  const { t, relative, dateTime } = useI18n();
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const history = useQuery({
    queryKey: ['task-history', task.id],
    queryFn: async () => {
      const [comments, operations] = await Promise.all([
        getTaskComments(task.id),
        getTaskOperations(task.id),
      ]);
      const entries = [
        ...comments.map((comment) => ({
          id: `c-${comment.id}`,
          time: comment.time,
          userId: comment.userId,
          comment: comment.message as string | null,
          operation: null as string | null,
          detail: null as string | null,
        })),
        ...operations
          // a comment is listed with its text, not as an operation
          .filter((operation) => operation.operationType !== 'Comment')
          .map((operation) => ({
            id: `o-${operation.id}`,
            time: operation.timestamp,
            userId: operation.userId,
            comment: null,
            operation: operation.operationType,
            detail:
              operation.property && (operation.orgValue || operation.newValue)
                ? `${operation.property}: ${operation.orgValue ?? '—'} → ${operation.newValue ?? '—'}`
                : null,
          })),
      ];
      return entries.sort(
        (a, b) =>
          (parseEngineDate(b.time)?.getTime() ?? 0) - (parseEngineDate(a.time)?.getTime() ?? 0),
      );
    },
  });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const text = message.trim();
    if (!text) {
      return;
    }
    setSaving(true);
    if (await actions.comment(task.id, text)) {
      setMessage('');
    }
    setSaving(false);
  };

  return (
    <div className="history">
      <form className="comment-form" onSubmit={(event) => void submit(event)}>
        <label className="field__label" htmlFor="task-comment">
          {t('comment.label')}
        </label>
        <textarea
          id="task-comment"
          className="input"
          rows={2}
          maxLength={4000}
          value={message}
          placeholder={t('comment.placeholder')}
          onChange={(event) => setMessage(event.target.value)}
        />
        <div className="comment-form__actions">
          <Button
            type="submit"
            size="sm"
            icon={MessageSquare}
            busy={saving}
            disabled={!message.trim()}
          >
            {t('comment.add')}
          </Button>
        </div>
      </form>
      {history.isPending ? (
        <Loading label={t('history.loading')} />
      ) : history.isError ? (
        <ErrorState error={history.error} onRetry={() => void history.refetch()} />
      ) : history.data.length === 0 ? (
        <EmptyState title={t('history.empty')} />
      ) : (
        <ol className="timeline">
          {history.data.map((entry) => {
            const date = parseEngineDate(entry.time);
            const operation = entry.operation ? OPERATIONS[entry.operation] : undefined;
            return (
              <li key={entry.id} className="timeline__item">
                <div className="timeline__head">
                  <span className="timeline__user">{entry.userId ?? t('history.system')}</span>
                  <span className="timeline__what">
                    {entry.comment !== null
                      ? t('history.commented')
                      : operation
                        ? t(operation)
                        : entry.operation}
                  </span>
                  {date && (
                    <time className="timeline__time" dateTime={date.toISOString()} title={dateTime(date)}>
                      {relative(date)}
                    </time>
                  )}
                </div>
                {entry.comment !== null && <p className="timeline__comment">{entry.comment}</p>}
                {entry.detail && <p className="timeline__detail">{entry.detail}</p>}
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

const firstLine = (text: string): string => text.split(/\r?\n/)[0] ?? '';

/** The case diagram of a task of a case (CMMN), its plan item marked. */
function CaseDiagramTab({ task }: { task: Task }) {
  const { t } = useI18n();
  const definitionId = task.caseDefinitionId ?? '';
  const xml = useQuery({
    queryKey: ['case-xml', definitionId],
    queryFn: () => getCaseXml(definitionId),
    staleTime: Infinity,
  });
  const selected = useMemo(() => {
    if (!xml.data) return [];
    try {
      return planItemsOf(parseCaseDiagram(xml.data, keyOfDefinitionId(definitionId)), task.taskDefinitionKey);
    } catch {
      return [];
    }
  }, [xml.data, definitionId, task.taskDefinitionKey]);
  if (xml.isPending) {
    return <Loading label={t('diagram.loading')} />;
  }
  if (xml.isError) {
    return <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />;
  }
  return (
    <Suspense fallback={<Loading label={t('diagram.loading')} />}>
      <CmmnDiagram xml={xml.data} caseKey={keyOfDefinitionId(definitionId)} selected={selected} label={t('cmmn.task.diagramLabel')} />
    </Suspense>
  );
}

function DiagramTab({ task }: { task: Task }) {
  const { t } = useI18n();
  const definitionId = task.processDefinitionId;
  const xml = useQuery({
    queryKey: ['process-xml', definitionId],
    queryFn: () => getProcessXml(definitionId ?? ''),
    enabled: !!definitionId,
    staleTime: Infinity,
  });
  if (!definitionId) {
    return task.caseDefinitionId ? <CaseDiagramTab task={task} /> : <EmptyState icon={Workflow} title={t('diagram.none')} />;
  }
  if (xml.isPending) {
    return <Loading label={t('diagram.loading')} />;
  }
  if (xml.isError) {
    return <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />;
  }
  return (
    <Suspense fallback={<Loading label={t('diagram.loading')} />}>
      <BpmnDiagram xml={xml.data} processKey={keyOfDefinitionId(definitionId)} highlight={task.taskDefinitionKey} />
    </Suspense>
  );
}

export interface TaskDetailProps {
  taskId: string;
  /** what the list already knows about the task, shown until it is loaded */
  preview: TaskListItem | undefined;
  tab: string;
  onTab: (tab: string) => void;
  onClose: () => void;
  /** called after the task was completed, or resolved and handed back to its owner */
  onCompleted: (taskId: string, resolved: boolean) => void;
  actions: TaskActions;
  /** whether completing the task also gets the next work */
  completeAndNext: boolean;
  onCompleteAndNext: (on: boolean) => void;
}

/** An open task: its properties and the tabs form, history, diagram and description. */
export function TaskDetail({
  taskId,
  preview,
  tab,
  onTab,
  onClose,
  onCompleted,
  actions,
  completeAndNext,
  onCompleteAndNext,
}: TaskDetailProps) {
  const { t, locale } = useI18n();
  const session = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => getTask(taskId),
    placeholderData: preview,
    refetchInterval: 60_000,
  });
  const task = query.data;
  const definition = useQuery({
    queryKey: ['process-definition', task?.processDefinitionId],
    queryFn: () => getProcessDefinition(task?.processDefinitionId ?? ''),
    enabled: !!task?.processDefinitionId && !preview?.processDefinitionName,
    staleTime: Infinity,
  });
  // a task of a case (CMMN) is named by its case definition
  const caseDefinition = useQuery({
    queryKey: ['case-definition', task?.caseDefinitionId],
    queryFn: () => getCaseDefinition(task?.caseDefinitionId ?? ''),
    enabled: !!task?.caseDefinitionId && !task?.processDefinitionId && !preview?.processDefinitionName,
    staleTime: Infinity,
  });
  const tabPlugins = pluginsFor('tasklist.task.tab');
  // what the modeler wrote about the step and the process, shared with the diagram tab
  const xml = useQuery({
    queryKey: ['process-xml', task?.processDefinitionId],
    queryFn: () => getProcessXml(task?.processDefinitionId ?? ''),
    enabled: !!task?.processDefinitionId,
    staleTime: Infinity,
  });
  const documentation = useMemo(
    () => modelDocumentation(xml.data, task?.taskDefinitionKey),
    [xml.data, task?.taskDefinitionKey],
  );
  // whether the four-eyes control would refuse the completion by the signed-in user
  const fourEyes = useFourEyes(task);

  // a task completed or deleted elsewhere is gone, even with the data read before
  if (query.isError && (!task || isApiError(query.error, 'notFound'))) {
    return (
      <section className="detail" aria-label={t('task.title')}>
        <div className="detail__back">
          <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={onClose}>
            {t('task.backToList')}
          </Button>
        </div>
        {isApiError(query.error, 'notFound') ? (
          <EmptyState icon={CheckCheck} title={t('task.gone')}>
            {t('task.goneHint')}
          </EmptyState>
        ) : (
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
        )}
      </section>
    );
  }
  if (!task) {
    return (
      <section className="detail" aria-label={t('task.title')}>
        <Loading label={t('task.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </section>
    );
  }

  const mine = task.assignee === session.userId;
  const resolving = task.delegationState === 'PENDING';
  // resolving a delegated task hands it back to its owner, which the control allows
  const fourEyesBlocked = !!fourEyes.data?.blocked && !resolving;
  const processName =
    preview?.processDefinitionName ??
    definition.data?.name ??
    definition.data?.key ??
    caseDefinition.data?.name ??
    caseDefinition.data?.key ??
    null;
  // the case instance of a task of a case, for those who may open the monitoring pages
  const caseLink = task.caseInstanceId && session.authorizedApps.includes('cockpit') ? caseInstancePath(task.caseInstanceId) : null;
  // the description tab only when there is something to read; kept while the model loads for a link to it
  const describable =
    !!(task.description || documentation.step || documentation.process) || (tab === 'description' && xml.isPending);
  const tabs = [
    'form',
    'history',
    ...(task.processInstanceId ? ['case'] : []),
    'attachments',
    'diagram',
    ...(describable ? ['description'] : []),
    ...tabPlugins.map((plugin) => plugin.id),
  ];
  const activeTab = tabs.includes(tab) ? tab : 'form';

  return (
    <section className="detail" aria-label={t('task.title')}>
      <header className="detail__header">
        <div className="detail__back">
          <IconButton icon={ArrowLeft} label={t('task.backToList')} shortcut="Esc" onClick={onClose} />
        </div>
        <div className="detail__titles">
          <h1 className="detail__title" id="task-detail-title" tabIndex={-1}>{task.name ?? task.taskDefinitionKey ?? task.id}</h1>
          {processName && <p className="detail__process">{processName}</p>}
          {caseLink && (
            <Link className="link link--subtle detail__case" to={caseLink}>
              <FolderKanban aria-hidden size={13} />
              {t('cmmn.task.openCase')}
            </Link>
          )}
        </div>
        <div className="detail__actions">
          {mine ? (
            <Button icon={Undo2} onClick={() => void actions.unclaim(task.id)}>
              {t('task.unclaim')}
            </Button>
          ) : (
            !task.assignee && (
              <Button variant="primary" icon={Hand} onClick={() => void actions.claim(task.id)}>
                {t('task.claim')}
              </Button>
            )
          )}
        </div>
      </header>

      <TaskMeta task={task} actions={actions} />
      <SubstituteNotice task={task} />
      {fourEyes.data && <FourEyesNotice status={fourEyes.data} />}

      {documentation.step && (
        <details className="step-doc">
          <summary className="step-doc__summary">
            <BookOpen aria-hidden size={15} />
            <span className="step-doc__title">{t('task.doc.step')}</span>
            <span className="step-doc__preview">{firstLine(documentation.step)}</span>
          </summary>
          <DocumentationText text={documentation.step} />
        </details>
      )}

      <Tabs.Root className="tabs" value={activeTab} onValueChange={onTab}>
        <OverflowRow className="tabrow" selector='[role="tab"]' label={t('nav.more')}>

        <Tabs.List className="tabs__list" aria-label={t('task.tabs')}>
          <Tabs.Trigger className="tabs__tab" value="form">
            {t('task.tab.form')}
          </Tabs.Trigger>
          <Tabs.Trigger className="tabs__tab" value="history">
            {t('task.tab.history')}
          </Tabs.Trigger>
          {task.processInstanceId && (
            <Tabs.Trigger className="tabs__tab" value="case">
              {t('task.tab.case')}
            </Tabs.Trigger>
          )}
          <Tabs.Trigger className="tabs__tab" value="attachments">
            {t('task.tab.attachments')}
          </Tabs.Trigger>
          <Tabs.Trigger className="tabs__tab" value="diagram">
            {t('task.tab.diagram')}
          </Tabs.Trigger>
          {describable && (
            <Tabs.Trigger className="tabs__tab" value="description">
              {t('task.tab.description')}
            </Tabs.Trigger>
          )}
          {tabPlugins.map((plugin) => (
            <Tabs.Trigger key={plugin.id} className="tabs__tab" value={plugin.id}>
              {plugin.label(locale)}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
        </OverflowRow>

        <Tabs.Content className="tabs__panel" value="form">
          {mine && !resolving && !fourEyesBlocked && (
            <label className="checkbox get-next__after">
              <input
                type="checkbox"
                checked={completeAndNext}
                onChange={(event) => onCompleteAndNext(event.target.checked)}
              />
              <span>{t('getNext.completeAndNext')}</span>
            </label>
          )}
          <FormHost
            target={{ type: 'task', taskId: task.id }}
            disabled={!mine || fourEyesBlocked}
            disabledHint={
              mine && fourEyesBlocked ? (
                t('fourEyes.formHint')
              ) : task.assignee ? (
                t('task.form.assignedToOther', { user: task.assignee })
              ) : (
                <>
                  {t('task.form.claimFirst')}{' '}
                  <button type="button" className="link" onClick={() => void actions.claim(task.id)}>
                    {t('task.claim')}
                  </button>
                </>
              )
            }
            // a delegated task is resolved by its form and goes back to its owner, not completed
            submitLabel={resolving ? t('task.resolve') : t('task.complete')}
            onSubmitted={() => {
              toast.show({
                title: resolving
                  ? t('task.resolved', { name: task.name ?? task.id, owner: task.owner ?? '' })
                  : t('task.completed', { name: task.name ?? task.id }),
              });
              // a completed task is gone, and nothing is read for it again; a resolved one stays open
              refreshTasks(queryClient, undefined, resolving ? undefined : { completed: [task.id] });
              onCompleted(task.id, resolving);
            }}
          />
        </Tabs.Content>
        <Tabs.Content className="tabs__panel" value="history">
          <HistoryTab task={task} actions={actions} />
        </Tabs.Content>
        {task.processInstanceId && (
          <Tabs.Content className="tabs__panel" value="case">
            <CaseTimeline processInstanceId={task.processInstanceId} currentTaskId={task.id} />
          </Tabs.Content>
        )}
        <Tabs.Content className="tabs__panel" value="attachments">
          <AttachmentsTab taskId={task.id} />
        </Tabs.Content>
        <Tabs.Content className="tabs__panel tabs__panel--flush" value="diagram">
          <DiagramTab task={task} />
        </Tabs.Content>
        <Tabs.Content className="tabs__panel" value="description">
          {task.description || documentation.step || documentation.process ? (
            <div className="descriptions">
              {task.description && <p className="description">{task.description}</p>}
              {documentation.step && (
                <section className="description-doc">
                  <h2 className="description-doc__title">{t('task.doc.step')}</h2>
                  <DocumentationText text={documentation.step} />
                </section>
              )}
              {documentation.process && (
                <section className="description-doc">
                  <h2 className="description-doc__title">{t('task.doc.process')}</h2>
                  <DocumentationText text={documentation.process} />
                </section>
              )}
            </div>
          ) : (
            <EmptyState icon={FileText} title={t('task.noDescription')} />
          )}
        </Tabs.Content>
        {tabPlugins.map((plugin) => {
          const PluginComponent = plugin.component;
          return (
            <Tabs.Content key={plugin.id} className="tabs__panel" value={plugin.id}>
              <PluginComponent task={task} />
            </Tabs.Content>
          );
        })}
      </Tabs.Root>
    </section>
  );
}
