/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { History, PencilRuler, SearchX, Table2 } from 'lucide-react';
import {
  getDecisionDefinition,
  getDecisionDefinitions,
  getDecisionInstances,
  getDecisionVersions,
  getDecisionXml,
  type DecisionDefinition,
  type DecisionInstance,
  type DecisionSortBy,
  type DecisionValue,
  type Sorting,
} from '../api/cockpit';
import { isApiError } from '../api/http';
import { useI18n } from '../i18n';
import { EmptyState, ErrorState, Loading, NoMatch, Skeleton } from '../ui';
import { COCKPIT, useRefreshInterval } from './common';
import { SearchInput } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { pageOf, useUrlSearch, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { instancePath } from './Incidents';

import { decisionInstancePath } from './paths';

const DmnViewer = lazy(() => import('../dmn/DmnViewer'));

function decisionPath(id: string): string {
  return `/cockpit/decisions/definition/${encodeURIComponent(id)}`;
}

/** Every decision once, in its latest version. */
export function DecisionsPage() {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const [sorting, setSorting] = useRemembered<Sorting<DecisionSortBy>>('decisions.sort', {
    sortBy: 'name',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);


  const decisions = useQuery({
    queryKey: [COCKPIT, 'decisions', term, sorting, page],
    queryFn: ({ signal }) =>
      getDecisionDefinitions({
        search: term || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<DecisionDefinition>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.definition.name'),
        sortBy: 'name',
        cell: (decision) => (
          <Link className="cell-link" to={decisionPath(decision.id)}>
            {decision.name ?? decision.key}
          </Link>
        ),
      },
      {
        id: 'key',
        header: t('cockpit.definition.key'),
        sortBy: 'key',
        secondary: true,
        cell: (decision) => <span className="mono">{decision.key}</span>,
      },
      {
        id: 'tenant',
        header: t('cockpit.tenant'),
        sortBy: 'tenantId',
        secondary: true,
        cell: (decision) => decision.tenantId ?? <span className="muted">—</span>,
      },
      {
        id: 'requirements',
        header: t('cockpit.decision.requirements'),
        secondary: true,
        cell: (decision) =>
          decision.decisionRequirementsDefinitionKey ? (
            <span className="mono">{decision.decisionRequirementsDefinitionKey}</span>
          ) : (
            <span className="muted">—</span>
          ),
      },
      {
        id: 'version',
        header: t('cockpit.definition.latestVersion'),
        align: 'end',
        cell: (decision) => decision.version,
      },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('cockpit.decisions.title')}
        subtitle={decisions.data ? t('cockpit.decisions.count', { count: decisions.data.count }) : undefined}
      />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('cockpit.decisions.search')} />
        </div>
        <DataTable
          label={t('cockpit.decisions.title')}
          columns={columns}
          updating={decisions.isPlaceholderData}
          rows={decisions.data?.items}
          rowKey={(decision) => decision.id}
          error={decisions.error}
          onRetry={() => void decisions.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<DecisionSortBy>);
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('cockpit.decisions.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={Table2} title={t('cockpit.decisions.empty')}>
                {t('cockpit.decisions.emptyHint')}
              </EmptyState>
            )
          }
        />
        <Pager
          page={page}
          count={decisions.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
    </div>
  );
}

function valueSummary(values: DecisionValue[] | undefined, output: boolean): string {
  return (values ?? [])
    .map((entry) => {
      const name = (output ? entry.variableName : null) ?? entry.clauseName ?? '?';
      const value = typeof entry.value === 'object' && entry.value !== null ? JSON.stringify(entry.value) : String(entry.value);
      return `${name} = ${value}`;
    })
    .join('; ');
}

function EvaluationsCard({ decisionDefinitionId }: { decisionDefinitionId: string }) {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const page = pageOf(params);
  const evaluations = useQuery({
    queryKey: [COCKPIT, 'decision-instances', decisionDefinitionId, page],
    queryFn: ({ signal }) =>
      getDecisionInstances({
        decisionDefinitionId,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const columns = useMemo<Column<DecisionInstance>[]>(
    () => [
      {
        id: 'time',
        header: t('cockpit.decision.evaluated'),
        cell: (evaluation) => (
          <Link className="cell-link" to={decisionInstancePath(evaluation.id)}>
            <Time value={evaluation.evaluationTime} />
          </Link>
        ),
      },
      {
        id: 'inputs',
        header: t('cockpit.decision.inputs'),
        cell: (evaluation) => <span className="value__text">{valueSummary(evaluation.inputs, false) || '—'}</span>,
      },
      {
        id: 'outputs',
        header: t('cockpit.decision.outputs'),
        cell: (evaluation) => <span className="value__text">{valueSummary(evaluation.outputs, true) || '—'}</span>,
      },
      {
        id: 'instance',
        header: t('cockpit.instance'),
        secondary: true,
        cell: (evaluation) =>
          evaluation.processInstanceId ? (
            <Link className="cell-link" to={instancePath(evaluation.processInstanceId)}>
              <Id value={evaluation.processInstanceId} />
            </Link>
          ) : (
            <span className="muted">—</span>
          ),
      },
      {
        id: 'user',
        header: t('cockpit.user'),
        secondary: true,
        cell: (evaluation) => evaluation.userId ?? <span className="muted">—</span>,
      },
    ],
    [t],
  );
  return (
    <Card flush title={t('cockpit.decision.evaluations')}>
      {evaluations.data === null ? (
        <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
          {t('cockpit.history.unavailableHint')}
        </EmptyState>
      ) : (
        <>
          <DataTable
            label={t('cockpit.decision.evaluations')}
            columns={columns}
            updating={evaluations.isPlaceholderData}
            rows={evaluations.data?.items}
            rowKey={(evaluation) => evaluation.id}
            error={evaluations.error}
            onRetry={() => void evaluations.refetch()}
            empty={<EmptyState icon={History} title={t('cockpit.decision.evaluationsEmpty')} />}
          />
          <Pager
            page={page}
            count={evaluations.data?.count ?? 0}
            onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
          />
        </>
      )}
    </Card>
  );
}

/** One version of a decision: its table and its recent evaluations. */
export function DecisionPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const decision = useQuery({
    queryKey: [COCKPIT, 'decision', id],
    queryFn: () => getDecisionDefinition(id),
  });
  const key = decision.data?.key;
  const tenantId = decision.data?.tenantId ?? null;
  const versions = useQuery({
    queryKey: [COCKPIT, 'decision-versions', key, tenantId],
    queryFn: () => getDecisionVersions(key ?? '', tenantId),
    enabled: !!key,
  });
  const xml = useQuery({
    queryKey: ['decision-xml', id],
    queryFn: () => getDecisionXml(id),
    staleTime: Infinity,
  });

  if (decision.isError && !decision.data) {
    return (
      <div className="page">
        <PageHeader
          title={t('cockpit.decision.title')}
          back={{ to: '/cockpit/decisions', label: t('cockpit.decisions.title') }}
        />
        <Card>
          {isApiError(decision.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('cockpit.decision.notFound')} />
          ) : (
            <ErrorState error={decision.error} onRetry={() => void decision.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!decision.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }
  const current = decision.data;
  return (
    <div className="page">
      <PageHeader
        title={current.name ?? current.key}
        back={{ to: '/cockpit/decisions', label: t('cockpit.decisions.title') }}
        actions={
          <Link className="btn btn--secondary btn--md" to={`/cockpit/modeler?decision=${encodeURIComponent(current.id)}`}>
            <PencilRuler aria-hidden size={16} className="btn__icon" />
            {t('modeler.editDecision')}
          </Link>
        }
        subtitle={
          <>
            <span className="mono">{current.key}</span>
            {current.tenantId && <span>{t('cockpit.tenantOf', { tenant: current.tenantId })}</span>}
            <label className="version-select">
              {t('cockpit.definition.version')}
              <select
                className="input"
                value={current.id}
                onChange={(event) => void navigate(decisionPath(event.target.value))}
              >
                {(versions.data ?? [current]).map((version) => (
                  <option key={version.id} value={version.id}>
                    {version.version}
                    {version.versionTag ? ` (${version.versionTag})` : ''}
                  </option>
                ))}
              </select>
            </label>
            {current.deploymentId && (
              <Link
                className="link link--subtle"
                to={`/cockpit/deployments/${encodeURIComponent(current.deploymentId)}`}
              >
                {t('cockpit.deployment')}
              </Link>
            )}
          </>
        }
      />
      <Card flush className="model model--decision">
        {xml.isError ? (
          <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />
        ) : !xml.data ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={6} />
          </Loading>
        ) : (
          <Suspense fallback={<Loading label={t('state.loading')} />}>
            <DmnViewer xml={xml.data} decisionKey={current.key} />
          </Suspense>
        )}
      </Card>
      <EvaluationsCard decisionDefinitionId={id} />
    </div>
  );
}
