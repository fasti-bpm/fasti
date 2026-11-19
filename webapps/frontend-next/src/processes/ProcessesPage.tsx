/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { Activity, ArrowLeft, CheckCircle2, ChevronLeft, ChevronRight, ListChecks, MousePointerClick, Play, Search, Workflow } from 'lucide-react';
import { canCreateInstances, getFirstTaskOfInstance, getProcessDefinition, getStartableDefinitions } from '../api/engine';
import type { ProcessDefinition, ProcessInstanceRef } from '../api/types';
import { useSession } from '../app/session';
import { historyPath } from '../cockpit/paths';
import { FormHost } from '../forms/FormHost';
import '../forms/registry';
import { useI18n } from '../i18n';
import { Button, EmptyState, ErrorState, IconButton, Loading, NoMatch, Skeleton, cx } from '../ui';
import { useToast } from '../ui/toast';
import { refreshTasks } from '../tasklist/actions';
import '../tasklist/tasklist.css';

const PAGE_SIZE = 20;

function definitionName(definition: ProcessDefinition): string {
  return definition.name ?? definition.key;
}

/**
 * What happened after a start: the new instance, its first task if the user
 * may see one, and the ways on from there.
 */
function StartReceipt({
  name,
  instance,
  onAgain,
  onClose,
}: {
  name: string;
  instance: ProcessInstanceRef;
  onAgain: () => void;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const session = useSession();
  // the form with the focused button is gone: the receipt takes the focus
  const container = useRef<HTMLDivElement>(null);
  useEffect(() => container.current?.focus(), []);
  const task = useQuery({
    queryKey: ['start-receipt-task', instance.id],
    queryFn: ({ signal }) => getFirstTaskOfInstance(instance.id, signal),
    retry: false,
  });
  return (
    <div ref={container} className="start-receipt" role="status" tabIndex={-1}>
      <p className="start-receipt__title">
        <CheckCircle2 aria-hidden size={20} />
        {t('process.started', { name })}
      </p>
      <dl className="start-receipt__facts">
        {instance.businessKey && (
          <>
            <dt>{t('process.businessKey')}</dt>
            <dd>{instance.businessKey}</dd>
          </>
        )}
        <dt>{t('start.receipt.instance')}</dt>
        <dd className="mono">{instance.id}</dd>
      </dl>
      <div className="start-receipt__actions">
        {task.data && (
          <Link className="btn btn--primary btn--md" to={`/tasklist?task=${encodeURIComponent(task.data.id)}`}>
            <ListChecks aria-hidden size={16} className="btn__icon" />
            {t('start.receipt.openTask', { name: task.data.name ?? task.data.taskDefinitionKey ?? '' })}
          </Link>
        )}
        {session.authorizedApps.includes('cockpit') && (
          <Link className="btn btn--secondary btn--md" to={historyPath(instance.id)}>
            <Activity aria-hidden size={16} className="btn__icon" />
            {t('start.receipt.track')}
          </Link>
        )}
        <Button icon={Play} onClick={onAgain}>
          {t('start.receipt.again')}
        </Button>
        <Button onClick={onClose}>{t('process.backToList')}</Button>
      </div>
      {task.isSuccess && !task.data && <p className="muted">{t('start.receipt.noTask')}</p>}
    </div>
  );
}

function StartPanel({ definitionId, onClose }: { definitionId: string; onClose: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [started, setStarted] = useState<ProcessInstanceRef | null>(null);
  const title = useRef<HTMLHeadingElement>(null);
  const definition = useQuery({
    queryKey: ['process-definition', definitionId],
    queryFn: () => getProcessDefinition(definitionId),
    staleTime: Infinity,
  });

  if (definition.isPending) {
    return (
      <Loading label={t('state.loading')}>
        <Skeleton lines={6} />
      </Loading>
    );
  }
  if (definition.isError) {
    return <ErrorState error={definition.error} onRetry={() => void definition.refetch()} />;
  }
  const name = definitionName(definition.data);
  return (
    <section className="detail" aria-label={t('process.start')}>
      <header className="detail__header">
        <div className="detail__back">
          <IconButton icon={ArrowLeft} label={t('process.backToList')} onClick={onClose} />
        </div>
        <div className="detail__titles">
          <h1 ref={title} className="detail__title" tabIndex={-1}>
            {name}
          </h1>
          <p className="detail__process">
            {t('process.version', { version: definition.data.version })}
            {definition.data.tenantId && ` · ${definition.data.tenantId}`}
          </p>
        </div>
      </header>
      {definition.data.description && <p className="description">{definition.data.description}</p>}
      <div className="tabs__panel">
        {started ? (
          <StartReceipt name={name} instance={started} onAgain={() => {
              setStarted(null);
              window.setTimeout(() => title.current?.focus());
            }} onClose={onClose} />
        ) : (
          <FormHost
            target={{ type: 'start', processDefinitionId: definitionId }}
            disabled={false}
            submitLabel={t('process.start')}
            onSubmitted={(result) => {
              refreshTasks(queryClient);
              if (result) {
                setStarted(result);
              } else {
                toast.show({ title: t('process.started', { name }) });
                onClose();
              }
            }}
          />
        )}
      </div>
    </section>
  );
}

/** The processes a user may start, with the start form of the chosen one. */
export function ProcessesPage() {
  const { t } = useI18n();
  const [params, setParams] = useSearchParams();
  const definitionId = params.get('definition');
  const [search, setSearch] = useState('');
  const [term, setTerm] = useState('');
  const [page, setPage] = useState(0);

  // the list follows the input with a short delay
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTerm(search.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const definitions = useQuery({
    queryKey: ['startable-definitions', term, page],
    queryFn: ({ signal }) =>
      getStartableDefinitions({
        firstResult: page * PAGE_SIZE,
        maxResults: PAGE_SIZE,
        search: term || undefined,
        signal,
      }),
    placeholderData: keepPreviousData,
  });

  // only processes the user can really start are offered
  const creating = useQuery({ queryKey: ['can-create-instances'], queryFn: ({ signal }) => canCreateInstances(signal), staleTime: 60_000 });

  const select = (id: string | null) => {
    setParams(id ? { definition: id } : {});
  };
  const count = definitions.data?.count ?? 0;

  return (
    <div className={cx('tasklist tasklist--two', definitionId && 'tasklist--task-open')}>
      <section className="tasklist__list" aria-label={t('process.title')}>
        <header className="pane-header">
          <Link to="/tasklist" className="icon-btn" aria-label={t('nav.tasklist')}>
            <ArrowLeft aria-hidden size={18} />
          </Link>
          <h2 className="pane-title">{t('process.title')}</h2>
        </header>
        <div className="tasks">
          <div className="tasks__toolbar">
            <div className="search__value search__value--single">
              <Search aria-hidden size={15} />
              <input
                className="input"
                type="search"
                aria-label={t('process.search')}
                placeholder={t('process.search')}
                value={search}
                autoFocus
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
          </div>
          <div className="tasks__body">
            {creating.data === false ? (
              <EmptyState icon={Workflow} title={t('process.notAllowed')}>
                {t('process.notAllowedHint')}
              </EmptyState>
            ) : definitions.isError ? (
              <ErrorState error={definitions.error} onRetry={() => void definitions.refetch()} />
            ) : !definitions.data ? (
              <Loading label={t('state.loading')}>
                <Skeleton lines={6} className="skeleton--cards" />
              </Loading>
            ) : definitions.data.definitions.length === 0 ? (
              term ? (
                <NoMatch search title={t('process.noMatch')} onClear={() => setSearch('')} />
              ) : (
                <EmptyState icon={Workflow} title={t('process.empty')}>
                  {t('process.emptyHint')}
                </EmptyState>
              )
            ) : (
              <ul className="task-list" aria-label={t('process.title')}>
                {definitions.data.definitions.map((definition) => (
                  <li key={definition.id}>
                    <button
                      type="button"
                      className={cx('task-row task-row--button', definition.id === definitionId && 'is-selected')}
                      aria-current={definition.id === definitionId ? 'true' : undefined}
                      onClick={() => select(definition.id)}
                    >
                      <span className="task-row__name">{definitionName(definition)}</span>
                      <span className="task-row__process">
                        {definition.key} · {t('process.version', { version: definition.version })}
                        {definition.tenantId && ` · ${definition.tenantId}`}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {count > PAGE_SIZE && (
            <nav className="pager" aria-label={t('pager.title')}>
              <IconButton
                icon={ChevronLeft}
                label={t('pager.previous')}
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              />
              <span className="pager__text">
                {t('pager.range', {
                  from: page * PAGE_SIZE + 1,
                  to: Math.min((page + 1) * PAGE_SIZE, count),
                  total: count,
                })}
              </span>
              <IconButton
                icon={ChevronRight}
                label={t('pager.next')}
                disabled={(page + 1) * PAGE_SIZE >= count}
                onClick={() => setPage(page + 1)}
              />
            </nav>
          )}
        </div>
      </section>
      <div className="tasklist__detail">
        {definitionId ? (
          <StartPanel key={definitionId} definitionId={definitionId} onClose={() => select(null)} />
        ) : (
          <EmptyState icon={MousePointerClick} title={t('process.choose')} />
        )}
      </div>
    </div>
  );
}
