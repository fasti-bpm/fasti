/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo } from 'react';
import { Link, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { History, SearchX } from 'lucide-react';
import { getDecisionXml } from '../api/cockpit';
import { isApiError } from '../api/http';
import {
  getHistoricDecision,
  getHistoricDecisions,
  matchedRules,
  orNull,
  type HistoricDecision,
  type HistoricDecisionValue,
} from '../api/operations';
import { useI18n } from '../i18n';
import { EmptyState, ErrorState, Loading, NoMatch, Skeleton } from '../ui';
import { COCKPIT } from './common';
import { Id, Time } from '../ui/format';
import { pageOf, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { decisionInstancePath, historyPath } from './paths';

const DmnViewer = lazy(() => import('../dmn/DmnViewer'));

function shown(value: unknown): string {
  if (value === null || value === undefined) return 'null';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** Evaluations of decisions in the whole engine, newest first. */
export function DecisionHistoryPage() {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const page = pageOf(params);
  const key = params.get('key') ?? '';
  const decisions = useQuery({
    queryKey: [COCKPIT, 'decision-history', key, page],
    queryFn: ({ signal }) =>
      orNull(
        getHistoricDecisions({
          query: { decisionDefinitionKey: key || undefined },
          paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
          signal,
        }),
      ),
    placeholderData: keepPreviousData,
  });
  const columns = useMemo<Column<HistoricDecision>[]>(
    () => [
      {
        id: 'time',
        header: t('cockpit.decision.evaluated'),
        cell: (row) => (
          <Link className="cell-link" to={decisionInstancePath(row.id)}>
            <Time value={row.evaluationTime} />
          </Link>
        ),
      },
      { id: 'decision', header: t('ops.decisions.decision'), cell: (row) => row.decisionDefinitionName ?? row.decisionDefinitionKey },
      {
        id: 'instance',
        header: t('cockpit.instance'),
        secondary: true,
        cell: (row) =>
          row.processInstanceId ? (
            <Link className="cell-link" to={historyPath(row.processInstanceId)}>
              <Id value={row.processInstanceId} />
            </Link>
          ) : (
            <span className="muted">—</span>
          ),
      },
      { id: 'user', header: t('cockpit.user'), secondary: true, cell: (row) => row.userId ?? <span className="muted">—</span> },
    ],
    [t],
  );
  return (
    <div className="page">
      <PageHeader title={t('ops.decisions.title')} subtitle={t('ops.decisions.subtitle')} />
      <Card flush>
        <div className="toolbar toolbar--filters">
          <input
            className="input"
            aria-label={t('ops.decisions.key')}
            placeholder={t('ops.decisions.key')}
            key={key}
            defaultValue={key}
            onBlur={(event) => setParams({ key: event.target.value.trim() || null, page: null })}
            onKeyDown={(event) => event.key === 'Enter' && setParams({ key: event.currentTarget.value.trim() || null, page: null })}
          />
        </div>
        {decisions.data === null ? (
          <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
            {t('cockpit.history.unavailableHint')}
          </EmptyState>
        ) : (
          <>
            <DataTable
              label={t('ops.decisions.title')}
              columns={columns}
              updating={decisions.isPlaceholderData}
              rows={decisions.data?.items}
              rowKey={(row) => row.id}
              error={decisions.error}
              onRetry={() => void decisions.refetch()}
              empty={
                key ? (
                  <NoMatch onClear={() => setParams({ key: null, page: null })} />
                ) : (
                  <EmptyState icon={History} title={t('cockpit.decision.evaluationsEmpty')} />
                )
              }
            />
            <Pager page={page} count={decisions.data?.count ?? 0} onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })} />
          </>
        )}
      </Card>
    </div>
  );
}

function ValuesTable({ label, values, output }: { label: string; values: HistoricDecisionValue[]; output: boolean }) {
  const { t } = useI18n();
  const columns: Column<HistoricDecisionValue>[] = [
    { id: 'name', header: t('ops.name'), cell: (row) => (output ? (row.variableName ?? row.clauseName) : row.clauseName) ?? row.clauseId ?? '—' },
    { id: 'type', header: t('ops.history.type'), secondary: true, cell: (row) => row.type ?? '—' },
    { id: 'value', header: t('ops.value'), cell: (row) => <span className="value__text">{row.errorMessage ?? shown(row.value)}</span> },
    ...(output ? [{ id: 'rule', header: t('ops.decisions.rule'), secondary: true, cell: (row: HistoricDecisionValue) => row.ruleId ?? '—' }] : []),
  ];
  return (
    <Card flush title={label}>
      <DataTable label={label} columns={columns} rows={values} rowKey={(row) => row.id} empty={<EmptyState title={t('ops.history.nothing')} />} />
    </Card>
  );
}

/** One evaluation: what went in, what came out and which rules matched. */
export function DecisionInstancePage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const decision = useQuery({ queryKey: [COCKPIT, 'historic-decision', id], queryFn: () => getHistoricDecision(id) });
  const xml = useQuery({
    queryKey: ['decision-xml', decision.data?.decisionDefinitionId],
    queryFn: () => getDecisionXml(decision.data?.decisionDefinitionId ?? ''),
    enabled: !!decision.data,
    staleTime: Infinity,
  });
  if (decision.isError) {
    return (
      <div className="page">
        <PageHeader title={t('ops.decisions.instance')} back={{ to: '/cockpit/decision-history', label: t('ops.decisions.title') }} />
        <Card>
          {isApiError(decision.error, 'notFound') ? <EmptyState icon={SearchX} title={t('ops.history.notFound')} /> : <ErrorState error={decision.error} onRetry={() => void decision.refetch()} />}
        </Card>
      </div>
    );
  }
  if (!decision.data) {
    return (
      <Loading label={t('state.loading')}>
        <Skeleton lines={8} />
      </Loading>
    );
  }
  const current = decision.data;
  const rules = matchedRules(current);
  return (
    <div className="page">
      <PageHeader
        title={current.decisionDefinitionName ?? current.decisionDefinitionKey}
        back={{ to: '/cockpit/decision-history', label: t('ops.decisions.title') }}
        subtitle={
          <>
            <span>
              {t('cockpit.decision.evaluated')}: <Time value={current.evaluationTime} />
            </span>
            {current.processInstanceId && (
              <Link className="link link--subtle" to={historyPath(current.processInstanceId)}>
                {t('ops.decisions.openInstance')}
              </Link>
            )}
            <span>{t('ops.decisions.matched', { count: rules.length })}</span>
          </>
        }
      />
      <Card flush className="model">
        {xml.isError ? (
          <ErrorState error={xml.error} onRetry={() => void xml.refetch()} />
        ) : !xml.data ? (
          <Loading label={t('diagram.loading')} />
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <DmnViewer xml={xml.data} decisionKey={current.decisionDefinitionKey} highlightRules={rules} />
          </Suspense>
        )}
      </Card>
      <div className="instance-view instance-view--even">
        <ValuesTable label={t('cockpit.decision.inputs')} values={current.inputs ?? []} output={false} />
        <ValuesTable label={t('cockpit.decision.outputs')} values={current.outputs ?? []} output />
      </div>
    </div>
  );
}
