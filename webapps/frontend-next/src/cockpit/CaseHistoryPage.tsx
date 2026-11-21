/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import {
  getHistoricCaseInstances,
  historicCaseState,
  type CaseHistoryFilter,
  type CaseHistorySortBy,
  type HistoricCaseInstance,
} from '../api/cases';
import type { Sorting } from '../api/cockpit';
import { useI18n } from '../i18n';
import { EmptyState, NoMatch } from '../ui';
import { SearchInput } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { pageOf, useRemembered, useUrlSearch, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { CaseStateBadge } from './caseCommon';
import { COCKPIT, useRefreshInterval } from './common';
import { formatDuration } from './model';
import { CASES_PATH, caseDefinitionPath, caseInstancePath } from './paths';

const FILTERS: readonly CaseHistoryFilter[] = ['all', 'open', 'closed'];

/** Case instances as the history knows them, open and closed. */
export function CaseHistoryPage() {
  const { t, locale } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<CaseHistorySortBy>>('caseHistory.sort', {
    sortBy: 'createTime',
    sortOrder: 'desc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const filter = FILTERS.find((candidate) => candidate === params.get('state')) ?? 'all';
  const page = pageOf(params);

  const instances = useQuery({
    queryKey: [COCKPIT, 'case-history', filter, term, sorting, page],
    queryFn: ({ signal }) =>
      getHistoricCaseInstances({
        filter,
        search: term || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const columns = useMemo<Column<HistoricCaseInstance>[]>(
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
        id: 'definition',
        header: t('cmmn.history.case'),
        cell: (instance) => (
          <Link className="cell-link" to={caseDefinitionPath(instance.caseDefinitionId)}>
            {instance.caseDefinitionName ?? instance.caseDefinitionKey ?? instance.caseDefinitionId}
          </Link>
        ),
      },
      {
        id: 'businessKey',
        header: t('process.businessKey'),
        sortBy: 'businessKey',
        cell: (instance) => instance.businessKey ?? <span className="muted">—</span>,
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        cell: (instance) => <CaseStateBadge state={historicCaseState(instance)} />,
      },
      {
        id: 'created',
        header: t('cmmn.instance.created'),
        sortBy: 'createTime',
        secondary: true,
        cell: (instance) => <Time value={instance.createTime} />,
      },
      {
        id: 'closed',
        header: t('cmmn.instance.closedAt'),
        sortBy: 'closeTime',
        secondary: true,
        cell: (instance) => (instance.closeTime ? <Time value={instance.closeTime} /> : <span className="muted">—</span>),
      },
      {
        id: 'duration',
        header: t('ops.history.duration'),
        sortBy: 'duration',
        align: 'end',
        secondary: true,
        cell: (instance) =>
          instance.durationInMillis == null ? <span className="muted">—</span> : formatDuration(instance.durationInMillis, locale),
      },
    ],
    [t, locale],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('cmmn.history.title')}
        back={{ to: CASES_PATH, label: t('cmmn.definitions.title') }}
        subtitle={instances.data ? t('cmmn.history.count', { count: instances.data.count }) : undefined}
      />
      <Card flush>
        {instances.data === null ? (
          <EmptyState icon={History} title={t('cockpit.history.unavailable')}>
            {t('cockpit.history.unavailableHint')}
          </EmptyState>
        ) : (
          <>
            <div className="toolbar">
              <SearchInput value={search} onChange={setSearch} label={t('cmmn.history.search')} />
              <div className="segmented" role="group" aria-label={t('cockpit.state')}>
                {FILTERS.map((value) => (
                  <button
                    key={value}
                    type="button"
                    className="segmented__item"
                    aria-pressed={value === filter}
                    onClick={() => setParams({ state: value === 'all' ? null : value, page: null })}
                  >
                    {t(`cmmn.history.filter.${value}`)}
                  </button>
                ))}
              </div>
            </div>
            <DataTable
              label={t('cmmn.history.title')}
              columns={columns}
              updating={instances.isPlaceholderData}
              rows={instances.data?.items}
              rowKey={(instance) => instance.id}
              error={instances.error}
              onRetry={() => void instances.refetch()}
              sorting={sorting}
              onSort={(next) => {
                setSorting(next as Sorting<CaseHistorySortBy>);
                setParams({ page: null });
              }}
              empty={
                term || filter !== 'all' ? (
                  <NoMatch
                    search={!!term}
                    title={t('cmmn.history.noMatch')}
                    onClear={() => {
                      setSearch('');
                      setParams({ state: null, q: null, page: null });
                    }}
                  />
                ) : (
                  <EmptyState icon={History} title={t('cmmn.history.empty')} />
                )
              }
            />
            <Pager
              page={page}
              count={instances.data?.count ?? 0}
              onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
            />
          </>
        )}
      </Card>
    </div>
  );
}
