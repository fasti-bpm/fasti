/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Send, Workflow } from 'lucide-react';
import {
  getDefinitionStatistics,
  type DefinitionSortBy,
  type DefinitionStatistics,
  type Sorting,
} from '../api/cockpit';
import { useI18n } from '../i18n';
import { Button, EmptyState, NoMatch } from '../ui';
import { MessageDialog } from './MessageDialog';
import { COCKPIT, IncidentBadge, StateBadge, useRefreshInterval } from './common';
import { SearchInput } from '../ui/controls';
import { formatNumber } from '../ui/format';
import { pageOf, useUrlSearch, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';

export function definitionPath(id: string): string {
  return `/cockpit/processes/definition/${encodeURIComponent(id)}`;
}

/** Every process once, with the numbers of all its versions. */
export function DefinitionsPage() {
  const { t, locale } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<DefinitionSortBy>>('definitions.sort', {
    sortBy: 'name',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const [messaging, setMessaging] = useState(false);
  const page = pageOf(params);


  const definitions = useQuery({
    queryKey: [COCKPIT, 'definitions', term, sorting, page],
    queryFn: ({ signal }) =>
      getDefinitionStatistics({
        search: term || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
  });

  const columns = useMemo<Column<DefinitionStatistics>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.definition.name'),
        sortBy: 'name',
        cell: (definition) => (
          <Link className="cell-link" to={definitionPath(definition.id)}>
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
        align: 'end',
        secondary: true,
        cell: (definition) => definition.version,
      },
      {
        id: 'state',
        header: t('cockpit.state'),
        secondary: true,
        cell: (definition) => <StateBadge suspended={definition.suspensionState === 2} />,
      },
      {
        id: 'instances',
        header: t('cockpit.definition.instances'),
        sortBy: 'instances',
        align: 'end',
        cell: (definition) => formatNumber(definition.instances, locale),
      },
      {
        id: 'incidents',
        header: t('cockpit.incidents.title'),
        sortBy: 'incidents',
        align: 'end',
        cell: (definition) =>
          definition.incidents > 0 ? (
            <IncidentBadge count={definition.incidents} />
          ) : (
            <span className="muted">0</span>
          ),
      },
    ],
    [t, locale],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('cockpit.definitions.title')}
        subtitle={
          definitions.data ? t('cockpit.definitions.count', { count: definitions.data.count }) : undefined
        }
        actions={
          <Button icon={Send} onClick={() => setMessaging(true)}>
            {t('message.action')}
          </Button>
        }
      />
      {messaging && <MessageDialog onClose={() => setMessaging(false)} />}
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('cockpit.definitions.search')} />
        </div>
        <DataTable
          label={t('cockpit.definitions.title')}
          columns={columns}
          updating={definitions.isPlaceholderData}
          rows={definitions.data?.items}
          rowKey={(definition) => definition.id}
          error={definitions.error}
          onRetry={() => void definitions.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<DefinitionSortBy>);
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('cockpit.definitions.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={Workflow} title={t('cockpit.definitions.empty')}>
                {t('cockpit.definitions.emptyHint')}{' '}
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
