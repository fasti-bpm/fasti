/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ListFilter, RotateCcw } from 'lucide-react';
import type { Incident, Page } from '../api/cockpit';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { Button, EmptyState } from '../ui';
import { COCKPIT, DataTable, Time, useRefreshInterval, type Column, type TableSorting } from './common';
import { IncidentTable, RetryAllDialog } from './Incidents';

/** Incidents that share a cause: the same failure at the same activity of the same process. */
export interface IncidentGroup {
  key: string;
  incidentType: string;
  processKey: string;
  activityId: string;
  /** the message of the oldest incident, as an example */
  message: string;
  incidents: Incident[];
  instances: number;
  oldest: string;
  newest: string;
}

/**
 * The message of an incident without what differs from one occurrence to the
 * next: ids, numbers and quoted values. Two incidents with the same pattern
 * most likely have the same cause.
 */
export function messagePattern(message: string | null): string {
  return (message ?? '')
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, '…')
    .replace(/'[^']*'|"[^"]*"/g, '…')
    .replace(/\d+/g, '#')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 200);
}

/** Groups incidents by process, activity and message pattern; the largest group comes first. */
export function groupIncidents(incidents: Incident[]): IncidentGroup[] {
  const groups = new Map<string, IncidentGroup>();
  const time = (value: string) => parseEngineDate(value)?.getTime() ?? 0;
  for (const incident of incidents) {
    const processKey = incident.processDefinitionId?.split(':')[0] ?? '';
    const activityId = incident.activityId ?? '';
    const key = JSON.stringify([processKey, activityId, incident.incidentType, messagePattern(incident.incidentMessage)]);
    const group = groups.get(key);
    if (!group) {
      groups.set(key, {
        key,
        incidentType: incident.incidentType,
        processKey,
        activityId,
        message: incident.incidentMessage ?? '',
        incidents: [incident],
        instances: 0,
        oldest: incident.incidentTimestamp,
        newest: incident.incidentTimestamp,
      });
      continue;
    }
    group.incidents.push(incident);
    if (time(incident.incidentTimestamp) < time(group.oldest)) {
      group.oldest = incident.incidentTimestamp;
      group.message = incident.incidentMessage ?? group.message;
    }
    if (time(incident.incidentTimestamp) > time(group.newest)) group.newest = incident.incidentTimestamp;
  }
  for (const group of groups.values()) {
    group.instances = new Set(group.incidents.map((incident) => incident.processInstanceId)).size;
  }
  return [...groups.values()].sort((a, b) => b.incidents.length - a.incidents.length || time(a.oldest) - time(b.oldest));
}

/** The incidents of a group in the order of a column of the table. */
function sortIncidents(incidents: Incident[], sorting: TableSorting): Incident[] {
  const key = sorting.sortBy as keyof Incident;
  const sorted = [...incidents].sort((a, b) => String(a[key] ?? '').localeCompare(String(b[key] ?? '')));
  return sorting.sortOrder === 'desc' ? sorted.reverse() : sorted;
}

/**
 * The open incidents grouped by their likely cause, so that one problem is
 * handled once instead of once per instance.
 */
export function IncidentGroups({
  scope,
  load,
}: {
  /** names the filters the incidents were read with */
  scope: string;
  load: () => Promise<Page<Incident>>;
}) {
  const { t, number } = useI18n();
  const [retrying, setRetrying] = useState<IncidentGroup | null>(null);
  const [shownKey, setShownKey] = useState<string | null>(null);
  const [sorting, setSorting] = useState<TableSorting>({ sortBy: 'incidentTimestamp', sortOrder: 'asc' });
  const refetchInterval = useRefreshInterval();
  const data = useQuery({ queryKey: [COCKPIT, 'incident-groups', scope], queryFn: load, refetchInterval });
  const groups = useMemo(() => (data.data ? groupIncidents(data.data.items) : undefined), [data.data]);
  const shown = groups?.find((group) => group.key === shownKey);

  const columns = useMemo<Column<IncidentGroup>[]>(
    () => [
      {
        id: 'problem',
        header: t('incidents.groups.problem'),
        cell: (group) => <span className="incident__message">{group.message || '—'}</span>,
      },
      {
        id: 'where',
        header: t('incidents.groups.where'),
        cell: (group) => (
          <span>
            {group.activityId || '—'}
            <span className="muted"> · {group.processKey || '—'}</span>
          </span>
        ),
      },
      {
        id: 'count',
        header: t('incidents.groups.count'),
        align: 'end',
        cell: (group) => t('incidents.groups.instances', { count: group.instances, incidents: number(group.incidents.length) }),
      },
      { id: 'oldest', header: t('incidents.groups.oldest'), secondary: true, cell: (group) => <Time value={group.oldest} /> },
      { id: 'newest', header: t('incidents.groups.newest'), secondary: true, cell: (group) => <Time value={group.newest} /> },
      {
        id: 'actions',
        header: '',
        cell: (group) => (
          <span className="row-actions">
            <Button
              size="sm"
              icon={ListFilter}
              aria-label={t('a11y.rowAction', { action: t('incidents.groups.show'), row: `${group.activityId} · ${group.processKey}` })}
              aria-expanded={shownKey === group.key}
              aria-controls="incident-group-items"
              onClick={() => setShownKey(shownKey === group.key ? null : group.key)}
            >
              {t('incidents.groups.show')}
            </Button>
            <Button size="sm" icon={RotateCcw} aria-label={t('a11y.rowAction', { action: t('incidents.groups.retry'), row: `${group.activityId} · ${group.processKey}` })} onClick={() => setRetrying(group)}>
              {t('incidents.groups.retry')}
            </Button>
          </span>
        ),
      },
    ],
    [t, number, shownKey],
  );

  return (
    <>
      {data.data && data.data.count > data.data.items.length && (
        <p className="toolbar__text incident-groups__capped">
          {t('incidents.groups.capped', { read: number(data.data.items.length), total: number(data.data.count) })}
        </p>
      )}
      <DataTable
        label={t('incidents.groups.title')}
        columns={columns}
        rows={groups}
        rowKey={(group) => group.key}
        error={data.error}
        onRetry={() => void data.refetch()}
        empty={<EmptyState title={t('incidents.groups.empty')} />}
      />
      {shown && (
        <section id="incident-group-items" className="incident-group" aria-label={t('incidents.groups.items')}>
          <h3 className="incident-group__title">
            {t('incidents.groups.items')}: <span className="incident__message">{shown.message || '—'}</span>
          </h3>
          <IncidentTable
            page={{ items: sortIncidents(shown.incidents, sorting), count: shown.incidents.length }}
            error={null}
            onRetry={() => void data.refetch()}
            sorting={sorting}
            onSort={setSorting}
            showInstance
          />
        </section>
      )}
      {!shown && <span id="incident-group-items" hidden />}
      {retrying && (
        <RetryAllDialog
          scope={`${scope}:${retrying.key}`}
          load={() => Promise.resolve({ items: retrying.incidents, count: retrying.incidents.length })}
          onClose={() => {
            setRetrying(null);
            void data.refetch();
          }}
        />
      )}
    </>
  );
}
