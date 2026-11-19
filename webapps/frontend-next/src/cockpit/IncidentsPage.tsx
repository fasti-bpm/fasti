/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getIncidents, type IncidentSortBy, type Sorting } from '../api/cockpit';
import { useI18n } from '../i18n';
import { MoreFilters } from './MoreFilters';
import {
  COCKPIT,
  Card,
  PAGE_SIZE,
  PageHeader,
  Pager,
  SearchInput,
  pageOf,
  useUrlSearch,
  useRefreshInterval,
  useRemembered,
  useUrlState,
} from './common';
import { AnnotateAllDialog, IncidentTable, RetryAllDialog } from './Incidents';
import { IncidentGroups } from './IncidentGroups';
import { Button, cx } from '../ui';
import { ChevronDown, Group, ListChecks, MessageSquarePlus, RotateCcw } from 'lucide-react';
import { DropdownMenu } from 'radix-ui';
import { MenuContent } from '../ui/menu';

const TYPES = ['failedJob', 'failedExternalTask'] as const;

/** At most this many incidents are changed at once. */
const MAX_BULK = 1000;

/** The open incidents of the whole engine. */
export function IncidentsPage() {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sorting, setSorting] = useRemembered<Sorting<IncidentSortBy>>('incidents.sort', {
    sortBy: 'incidentTimestamp',
    sortOrder: 'desc',
  });
  const type = TYPES.find((candidate) => candidate === params.get('type')) ?? '';
  const term = params.get('q') ?? '';
  const activity = params.get('activity') ?? '';
  const processKey = params.get('process') ?? '';
  const grouped = params.get('group') === '1';
  const [bulk, setBulk] = useState<'retry' | 'annotate' | null>(null);
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);


  const incidents = useQuery({
    queryKey: [COCKPIT, 'incidents', 'all', type, term, activity, processKey, sorting, page],
    queryFn: ({ signal }) =>
      getIncidents({
        incidentType: type || undefined,
        messageLike: term || undefined,
        activityId: activity || undefined,
        processDefinitionKey: processKey || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    refetchInterval,
    enabled: !grouped,
  });

  // the bulk actions work on every matching incident, not only the shown page
  const scope = JSON.stringify([type, term, activity, processKey]);
  const loadMatching = () =>
      getIncidents({
        incidentType: type || undefined,
        messageLike: term || undefined,
        activityId: activity || undefined,
        processDefinitionKey: processKey || undefined,
        sorting: { sortBy: 'incidentTimestamp', sortOrder: 'asc' },
        paging: { firstResult: 0, maxResults: MAX_BULK },
      });

  return (
    <div className="page">
      <PageHeader
        title={t('cockpit.incidents.title')}
        subtitle={incidents.data && !grouped ? t('cockpit.incidents.open', { count: incidents.data.count }) : undefined}
      />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('cockpit.incidents.search')} />
          <select
            className="input toolbar__select"
            aria-label={t('cockpit.incident.type')}
            value={type}
            onChange={(event) => setParams({ type: event.target.value || null, page: null })}
          >
            <option value="">{t('cockpit.incidents.allTypes')}</option>
            <option value="failedJob">{t('cockpit.incident.type.failedJob')}</option>
            <option value="failedExternalTask">{t('cockpit.incident.type.failedExternalTask')}</option>
          </select>
          <MoreFilters active={[activity, processKey].filter(Boolean).length}>
            <input
              key={`activity-${activity}`}
              className="input toolbar__select"
              aria-label={t('incidents.activity')}
              placeholder={t('incidents.activity')}
              defaultValue={activity}
              onBlur={(event) => setParams({ activity: event.target.value.trim() || null, page: null })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') setParams({ activity: event.currentTarget.value.trim() || null, page: null });
              }}
            />
            <input
              key={`process-${processKey}`}
              className="input toolbar__select"
              aria-label={t('incidents.processKey')}
              placeholder={t('incidents.processKey')}
              defaultValue={processKey}
              onBlur={(event) => setParams({ process: event.target.value.trim() || null, page: null })}
              onKeyDown={(event) => {
                if (event.key === 'Enter') setParams({ process: event.currentTarget.value.trim() || null, page: null });
              }}
            />
          </MoreFilters>
          <button
            type="button"
            className={cx('toggle', grouped && 'is-on')}
            aria-pressed={grouped}
            onClick={() => setParams({ group: grouped ? null : '1', page: null })}
          >
            <Group aria-hidden size={14} />
            {t('incidents.groups.toggle')}
          </button>
          <span className="toolbar__spacer" />
          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <Button size="sm" icon={ListChecks} disabled={!grouped && !incidents.data?.count}>
                {t('incidents.matchingActions')}
                <ChevronDown aria-hidden size={14} />
              </Button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <MenuContent>
                <DropdownMenu.Item className="menu__item" onSelect={() => setBulk('retry')}>
                  <RotateCcw aria-hidden size={16} />
                  {t('incidents.retryMatching')}
                </DropdownMenu.Item>
                <DropdownMenu.Item className="menu__item" onSelect={() => setBulk('annotate')}>
                  <MessageSquarePlus aria-hidden size={16} />
                  {t('incidents.annotateMatching')}
                </DropdownMenu.Item>
              </MenuContent>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        </div>
        {bulk === 'retry' && <RetryAllDialog scope={scope} load={loadMatching} onClose={() => setBulk(null)} />}
        {bulk === 'annotate' && <AnnotateAllDialog scope={scope} load={loadMatching} onClose={() => setBulk(null)} />}
        {grouped ? (
          <IncidentGroups scope={scope} load={loadMatching} />
        ) : (
          <>
        <IncidentTable
          page={incidents.data}
          updating={incidents.isPlaceholderData}
          error={incidents.error}
          onRetry={() => void incidents.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<IncidentSortBy>);
            setParams({ page: null });
          }}
          showInstance
          onClearFilters={
            type || term || activity || processKey
              ? () => setParams({ type: null, q: null, activity: null, process: null, page: null })
              : undefined
          }
        />
        <Pager
          page={page}
          count={incidents.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
          </>
        )}
      </Card>
    </div>
  );
}
