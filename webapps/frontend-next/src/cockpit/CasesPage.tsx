/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import { FolderKanban, History, Plus, SearchX } from 'lucide-react';
import {
  countCaseInstances,
  createCaseInstance,
  caseInstanceState,
  executionState,
  getCaseDefinition,
  getCaseDefinitionVersions,
  getCaseDefinitions,
  getCaseInstances,
  getCaseXml,
  getDefinitionExecutions,
  EXECUTION_LIMIT,
  type CaseDefinition,
  type CaseDefinitionSortBy,
  type CaseInstance,
} from '../api/cases';
import type { Sorting } from '../api/cockpit';
import { isApiError } from '../api/http';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, Loading, NoMatch, Notice, Skeleton } from '../ui';
import { SearchInput } from '../ui/controls';
import { Id, formatNumber } from '../ui/format';
import { pageOf, useRemembered, useUrlSearch, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { useToast } from '../ui/toast';
import { CaseStateBadge } from './caseCommon';
import { COCKPIT, useRefreshInterval } from './common';
import { CASE_HISTORY_PATH, CASES_PATH, caseDefinitionPath, caseInstancePath } from './paths';

const CmmnDiagram = lazy(() => import('../cmmn/CmmnDiagram'));

/** Running instances of a case, all versions together; shown once it is counted. */
function useInstanceCounts(definitions: CaseDefinition[] | undefined, refetchInterval: number | false) {
  return useQueries({
    queries: (definitions ?? []).map((definition) => ({
      queryKey: [COCKPIT, 'case-instance-count', definition.key, definition.tenantId],
      queryFn: ({ signal }: { signal: AbortSignal }) => countCaseInstances(definition.key, definition.tenantId, signal),
      refetchInterval,
    })),
  });
}

/** Every case once, in its latest version, with its open instances. */
export function CaseDefinitionsPage() {
  const { t, locale } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<CaseDefinitionSortBy>>('cases.sort', {
    sortBy: 'name',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);

  const definitions = useQuery({
    queryKey: [COCKPIT, 'case-definitions', term, sorting, page],
    queryFn: ({ signal }) =>
      getCaseDefinitions({
        search: term || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const counts = useInstanceCounts(definitions.data?.items, refetchInterval);
  const countOf = useMemo(() => {
    const byId = new Map<string, number | undefined>();
    (definitions.data?.items ?? []).forEach((definition, index) => byId.set(definition.id, counts[index]?.data));
    return byId;
  }, [definitions.data, counts]);

  const columns = useMemo<Column<CaseDefinition>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.definition.name'),
        sortBy: 'name',
        cell: (definition) => (
          <Link className="cell-link" to={caseDefinitionPath(definition.id)}>
            {definition.name ?? definition.key}
          </Link>
        ),
      },
      {
        id: 'key',
        header: t('cockpit.definition.key'),
        sortBy: 'key',
        secondary: true,
        cell: (definition) => <span className="mono">{definition.key}</span>,
      },
      {
        id: 'tenant',
        header: t('cockpit.tenant'),
        sortBy: 'tenantId',
        secondary: true,
        cell: (definition) => definition.tenantId ?? <span className="muted">—</span>,
      },
      {
        id: 'version',
        header: t('cockpit.definition.latestVersion'),
        sortBy: 'version',
        align: 'end',
        secondary: true,
        cell: (definition) => definition.version,
      },
      {
        id: 'instances',
        header: t('cmmn.definitions.open'),
        align: 'end',
        cell: (definition) => {
          const value = countOf.get(definition.id);
          return value === undefined ? <span className="muted">…</span> : formatNumber(value, locale);
        },
      },
    ],
    [t, locale, countOf],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('cmmn.definitions.title')}
        subtitle={definitions.data ? t('cmmn.definitions.count', { count: definitions.data.count }) : undefined}
        actions={
          <Link className="btn btn--secondary btn--md" to={CASE_HISTORY_PATH}>
            <History aria-hidden size={16} className="btn__icon" />
            {t('cmmn.nav.history')}
          </Link>
        }
      />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('cmmn.definitions.search')} />
        </div>
        <DataTable
          label={t('cmmn.definitions.title')}
          columns={columns}
          updating={definitions.isPlaceholderData}
          rows={definitions.data?.items}
          rowKey={(definition) => definition.id}
          error={definitions.error}
          onRetry={() => void definitions.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<CaseDefinitionSortBy>);
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('cmmn.definitions.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={FolderKanban} title={t('cmmn.definitions.empty')}>
                {t('cmmn.definitions.emptyHint')}{' '}
                <Link className="link" to="/cockpit/deployments">
                  {t('cockpit.nav.deployments')}
                </Link>
              </EmptyState>
            )
          }
        />
        <Pager
          page={page}
          count={definitions.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
    </div>
  );
}

function CreateCaseDialog({ definition, onClose }: { definition: CaseDefinition; onClose: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [businessKey, setBusinessKey] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      const created = await createCaseInstance(definition.id, businessKey.trim());
      toast.show({ title: t('cmmn.create.done') });
      onClose();
      void navigate(caseInstancePath(created.id));
    } catch (error) {
      toast.error(error, t('cmmn.create.failed'));
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cmmn.create.title')}
      description={t('cmmn.create.text', { name: definition.name ?? definition.key, version: definition.version })}
    >
      <form className="confirm" onSubmit={(event) => void submit(event)}>
        <Field label={t('process.businessKey')}>
          {(props) => (
            <input {...props} className="input" value={businessKey} autoFocus onChange={(event) => setBusinessKey(event.target.value)} />
          )}
        </Field>
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" busy={busy}>
            {t('cmmn.create.action')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function InstancesCard({ definitionId }: { definitionId: string }) {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const page = pageOf(params);
  const key = params.get('bk') ?? '';
  const [search, setSearch] = useUrlSearch(key, setParams, 'bk');
  const instances = useQuery({
    queryKey: [COCKPIT, 'case-instances', definitionId, key, page],
    queryFn: ({ signal }) =>
      getCaseInstances({
        caseDefinitionId: definitionId,
        businessKey: key || undefined,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const columns = useMemo<Column<CaseInstance>[]>(
    () => [
      {
        id: 'id',
        header: t('cmmn.instance'),
        cell: (instance) => (
          <Link className="cell-link" to={caseInstancePath(instance.id)}>
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
        id: 'state',
        header: t('cockpit.state'),
        cell: (instance) => <CaseStateBadge state={caseInstanceState(instance)} />,
      },
      {
        id: 'tenant',
        header: t('cockpit.tenant'),
        secondary: true,
        cell: (instance) => instance.tenantId ?? <span className="muted">—</span>,
      },
    ],
    [t],
  );
  return (
    <Card flush title={t('cmmn.definition.instances')}>
      <div className="toolbar">
        <SearchInput value={search} onChange={setSearch} label={t('cmmn.definition.businessKeySearch')} />
      </div>
      <DataTable
        label={t('cmmn.definition.instances')}
        columns={columns}
        updating={instances.isPlaceholderData}
        rows={instances.data?.items}
        rowKey={(instance) => instance.id}
        error={instances.error}
        onRetry={() => void instances.refetch()}
        empty={
          key ? (
            <NoMatch search title={t('cmmn.definition.noMatch')} onClear={() => setSearch('')} />
          ) : (
            <EmptyState icon={FolderKanban} title={t('cmmn.definition.instancesEmpty')} />
          )
        }
      />
      <Pager
        page={page}
        count={instances.data?.count ?? 0}
        onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
      />
    </Card>
  );
}

/** One version of a case: its diagram with the open plan items, and its open instances. */
export function CaseDefinitionPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const refetchInterval = useRefreshInterval();
  const [creating, setCreating] = useState(false);
  const definition = useQuery({
    queryKey: [COCKPIT, 'case-definition', id],
    queryFn: () => getCaseDefinition(id),
  });
  const key = definition.data?.key;
  const tenantId = definition.data?.tenantId ?? null;
  const versions = useQuery({
    queryKey: [COCKPIT, 'case-definition-versions', key, tenantId],
    queryFn: () => getCaseDefinitionVersions(key ?? '', tenantId),
    enabled: !!key,
  });
  const xml = useQuery({
    queryKey: ['case-xml', id],
    queryFn: () => getCaseXml(id),
    staleTime: Infinity,
  });
  const executions = useQuery({
    queryKey: [COCKPIT, 'case-definition-executions', id],
    queryFn: ({ signal }) => getDefinitionExecutions(id, signal),
    refetchInterval,
  });
  const badges = useMemo(() => {
    const byItem = new Map<string, number>();
    for (const execution of executions.data?.items ?? []) {
      const state = executionState(execution);
      if (execution.activityType !== 'casePlanModel' && (state === 'active' || state === 'enabled')) {
        byItem.set(execution.activityId, (byItem.get(execution.activityId) ?? 0) + 1);
      }
    }
    return [...byItem].map(([item, count]) => ({ id: item, count }));
  }, [executions.data]);

  if (definition.isError && !definition.data) {
    return (
      <div className="page">
        <PageHeader title={t('cmmn.definition.title')} back={{ to: CASES_PATH, label: t('cmmn.definitions.title') }} />
        <Card>
          {isApiError(definition.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('cmmn.definition.notFound')} />
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
  return (
    <div className="page">
      <PageHeader
        title={current.name ?? current.key}
        back={{ to: CASES_PATH, label: t('cmmn.definitions.title') }}
        subtitle={
          <>
            <span className="mono">{current.key}</span>
            {current.tenantId && <span>{t('cockpit.tenantOf', { tenant: current.tenantId })}</span>}
            <label className="version-select">
              {t('cockpit.definition.version')}
              <select className="input" value={current.id} onChange={(event) => void navigate(caseDefinitionPath(event.target.value))}>
                {(versions.data ?? [current]).map((version) => (
                  <option key={version.id} value={version.id}>
                    {version.version}
                  </option>
                ))}
              </select>
            </label>
            {current.deploymentId && (
              <Link className="link link--subtle" to={`/cockpit/deployments/${encodeURIComponent(current.deploymentId)}`}>
                {t('cockpit.deployment')}
              </Link>
            )}
          </>
        }
        actions={
          <Button icon={Plus} onClick={() => setCreating(true)}>
            {t('cmmn.create.action')}
          </Button>
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
            <CmmnDiagram xml={xml.data} caseKey={current.key} badges={badges} label={t('cmmn.definition.diagramLabel')} />
          </Suspense>
        )}
        <p className="model__hint">{t('cmmn.definition.diagramHint')}</p>
      </Card>
      {executions.data?.truncated && <Notice tone="info">{t('cmmn.definition.truncated', { count: EXECUTION_LIMIT })}</Notice>}
      <InstancesCard definitionId={id} />
      {creating && <CreateCaseDialog definition={current} onClose={() => setCreating(false)} />}
    </div>
  );
}
