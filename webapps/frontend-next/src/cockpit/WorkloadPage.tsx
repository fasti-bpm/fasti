/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { AlarmClock, Inbox, ListChecks, Lock, UserX, Users } from 'lucide-react';
import { fullName } from '../api/engine';
import { isApiError } from '../api/http';
import {
  WORKLOAD_LIMIT,
  getOpenTaskFilter,
  getWorkload,
  getWorkloadGroups,
  getWorkloadUsers,
  type WorkloadRow,
} from '../api/workload';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { minuteOf, type QueueView } from '../tasklist/queue';
import type { Criterion } from '../tasklist/search';
import { tasklistPath } from '../tasklist/searchLinks';
import { EmptyState, ErrorState, cx } from '../ui';
import { COCKPIT, useRefreshInterval } from './common';
import { SearchInput } from '../ui/controls';
import { formatNumber } from '../ui/format';
import { useDebounced, useRemembered } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, Pager, useClampedPage, type Column } from '../ui/table';
import { Tile } from './DashboardPage';

/** how long a page of groups or users is taken as current; the refresh button reads it again */
const IDENTITIES_FRESH = 5 * 60_000;

/** where a count leads: the task list, narrowed like the count, if the user may open it */
type LinkTo = (criteria: Criterion[], view?: QueueView) => string | null;

function CountLink({ value, to, tone, label }: { value: number; to: string | null; tone?: 'danger'; label: string }) {
  const { locale } = useI18n();
  const text = formatNumber(value, locale);
  if (value === 0) return <span className="muted">0</span>;
  return to ? (
    <Link className={cx('cell-link workload__count', tone && `workload__count--${tone}`)} to={to} aria-label={label}>
      {text}
    </Link>
  ) : (
    <span className={cx('workload__count', tone && `workload__count--${tone}`)}>{text}</span>
  );
}

/** How long the oldest task waits, e.g. "3 days ago", with the exact time as a tooltip. */
function Age({ value }: { value: string | null }) {
  const { relative, dateTime } = useI18n();
  const date = parseEngineDate(value);
  if (!date) return <span className="muted">—</span>;
  return (
    <time dateTime={date.toISOString()} title={dateTime(date)} className="nowrap">
      {relative(date)}
    </time>
  );
}

function LoadTable({
  label,
  rows,
  error,
  onRetry,
  kind,
  linkTo,
  hideEmpty,
  updating,
}: {
  label: string;
  rows: WorkloadRow[] | undefined;
  error: unknown;
  onRetry: () => void;
  kind: 'group' | 'user';
  linkTo: LinkTo;
  hideEmpty: boolean;
  updating: boolean;
}) {
  const { t } = useI18n();
  const columns = useMemo<Column<WorkloadRow>[]>(() => {
    const criterion = (row: WorkloadRow): Criterion[] => [
      { id: 'workload', field: kind === 'group' ? 'candidateGroup' : 'assigneeId', value: row.id },
    ];
    const list: Column<WorkloadRow>[] = [
      {
        id: 'name',
        header: kind === 'group' ? t('workload.group') : t('workload.user'),
        cell: (row) => (
          <span className="workload__who">
            <span>{row.name}</span>
            {row.name !== row.id && <span className="muted mono workload__id">{row.id}</span>}
          </span>
        ),
      },
      {
        id: 'open',
        header: t('workload.open'),
        align: 'end',
        cell: (row) => (
          <CountLink value={row.open} to={linkTo(criterion(row))} label={t('workload.openOf', { name: row.name, count: row.open })} />
        ),
      },
    ];
    if (kind === 'group') {
      list.push({
        id: 'unassigned',
        header: t('workload.unassigned'),
        align: 'end',
        cell: (row) => (
          <CountLink
            value={row.unassigned ?? 0}
            to={linkTo(criterion(row), 'unclaimed')}
            label={t('workload.unassignedOf', { name: row.name, count: row.unassigned ?? 0 })}
          />
        ),
      });
    }
    list.push(
      {
        id: 'overdue',
        header: t('workload.overdue'),
        align: 'end',
        cell: (row) => (
          <CountLink
            value={row.overdue}
            tone="danger"
            to={linkTo(criterion(row), 'overdue')}
            label={t('workload.overdueOf', { name: row.name, count: row.overdue })}
          />
        ),
      },
      {
        id: 'oldest',
        header: t('workload.oldest'),
        secondary: true,
        cell: (row) => <Age value={row.oldest} />,
      },
    );
    return list;
  }, [t, kind, linkTo]);
  const shown = rows && (hideEmpty ? rows.filter((row) => row.open > 0) : rows);
  return (
    <DataTable
      label={label}
      columns={columns}
      rows={shown}
      rowKey={(row) => row.id}
      updating={updating}
      error={error}
      onRetry={onRetry}
      empty={
        <EmptyState icon={Inbox} title={rows?.length ? t('workload.noneOpen') : t('workload.nobody')}>
          {rows?.length ? t('workload.noneOpenHint') : undefined}
        </EmptyState>
      }
    />
  );
}

/** A section the user may not list: says so instead of failing the whole board. */
function Unavailable({ error, onRetry, what }: { error: unknown; onRetry: () => void; what: 'groups' | 'users' }) {
  const { t } = useI18n();
  if (isApiError(error, 'forbidden')) {
    return (
      <EmptyState icon={Lock} title={t(what === 'groups' ? 'workload.noGroupAccess' : 'workload.noUserAccess')}>
        {t('workload.noAccessHint')}
      </EmptyState>
    );
  }
  return <ErrorState error={error} onRetry={onRetry} />;
}

/**
 * Pages through the identities; the load is read per page, so the order is
 * by load within the page and the note says so whenever there is more.
 */
function PageNote({ count, page, onPage }: { count: number | null | undefined; page: number; onPage: (page: number) => void }) {
  const { t } = useI18n();
  if (count === undefined) return null;
  if (count === null) return <p className="card__hint workload__more">{t('workload.searchCapped', { count: WORKLOAD_LIMIT })}</p>;
  if (count <= WORKLOAD_LIMIT) return null;
  return (
    <div className="workload__pager">
      <p className="card__hint workload__more">{t('workload.paged', { count: WORKLOAD_LIMIT })}</p>
      <Pager page={page} count={count} pageSize={WORKLOAD_LIMIT} onPage={onPage} />
    </div>
  );
}

/**
 * The open tasks of the team: per candidate group and per person, with what
 * is unclaimed, what is late and how long the oldest task waits. Every count
 * opens the task list narrowed the same way.
 */
export function WorkloadPage() {
  const { t, locale } = useI18n();
  const session = useSession();
  const refetchInterval = useRefreshInterval();
  const identitiesInterval = useRefreshInterval('slow');
  const [hideEmpty, setHideEmpty] = useRemembered('workload.hideEmpty', true);
  // overdue is before now, taken when the counts are read
  const at = (): Date => minuteOf(new Date());
  const [groupSearch, setGroupSearch] = useState('');
  const [groupPage, setGroupPage] = useState(0);
  const [userSearch, setUserSearch] = useState('');
  const [userPage, setUserPage] = useState(0);
  const groupTerm = useDebounced(groupSearch.trim());
  const userTerm = useDebounced(userSearch.trim());
  // who is on the board: the groups and users change far less often than their tasks, so their
  // pages are not read again with every refresh of the numbers
  const groups = useQuery({
    queryKey: [COCKPIT, 'workload', 'groups', groupTerm, groupPage],
    queryFn: ({ signal }) => getWorkloadGroups({ search: groupTerm, page: groupPage }, signal),
    placeholderData: keepPreviousData,
    staleTime: IDENTITIES_FRESH,
    refetchInterval: identitiesInterval,
    retry: (count, error) => !isApiError(error, 'forbidden') && count < 2,
  });
  const users = useQuery({
    queryKey: [COCKPIT, 'workload', 'users', userTerm, userPage],
    queryFn: ({ signal }) => getWorkloadUsers({ search: userTerm, page: userPage }, signal),
    placeholderData: keepPreviousData,
    staleTime: IDENTITIES_FRESH,
    refetchInterval: identitiesInterval,
    retry: (count, error) => !isApiError(error, 'forbidden') && count < 2,
  });
  // a section the user may not list stays empty; the rest of the board is still counted
  const groupItems = groups.isError ? undefined : groups.data?.items;
  const userItems = users.isError ? undefined : users.data?.items;
  const members = useMemo(
    () => ({
      groups: (groupItems ?? []).map((group) => ({ id: group.id, name: group.name })),
      users: (userItems ?? []).map((user) => ({ id: user.id, name: fullName(user, locale) })),
    }),
    [groupItems, userItems, locale],
  );
  // the numbers of the whole board in one request: the totals and a row per group and user
  const load = useQuery({
    queryKey: [COCKPIT, 'workload', 'load', members],
    queryFn: ({ signal }) => getWorkload(members.groups, members.users, at(), signal),
    enabled: !groups.isPending && !users.isPending,
    placeholderData: keepPreviousData,
    refetchInterval,
  });
  const summary = load.data?.summary;
  // fewer identities than before, e.g. after some were removed: back to the last page there is
  useClampedPage(groupPage, groups.data?.count ?? undefined, setGroupPage, WORKLOAD_LIMIT);
  useClampedPage(userPage, users.data?.count ?? undefined, setUserPage, WORKLOAD_LIMIT);
  const tasklist = session.authorizedApps.includes('tasklist');
  const openFilter = useQuery({
    queryKey: [COCKPIT, 'workload', 'filter'],
    queryFn: ({ signal }) => getOpenTaskFilter(signal),
    enabled: tasklist,
    staleTime: 60_000,
  });
  const filterId = openFilter.data ?? null;
  const linkTo = useMemo<LinkTo>(
    () => (criteria, view) => (tasklist ? tasklistPath(filterId, criteria, false, view ?? 'all') : null),
    [tasklist, filterId],
  );
  // claimed tasks of people the user may not list, or who are no users of the engine
  const unlisted =
    users.data && !users.isPlaceholderData && !userTerm && users.data.count !== null && users.data.count <= WORKLOAD_LIMIT &&
    load.data && !load.isPlaceholderData
      ? load.data.summary.assigned - load.data.users.reduce((sum, row) => sum + row.open, 0)
      : 0;
  const tile = (criteria: Criterion[], view?: QueueView) => linkTo(criteria, view) ?? '/cockpit/workload';

  return (
    <div className="page">
      <PageHeader title={t('workload.title')} subtitle={t('workload.subtitle')} />
      <div className="tiles workload__tiles">
        <Tile to={tile([])} icon={ListChecks} label={t('workload.tile.open')} value={summary?.open} />
        <Tile to={tile([], 'unclaimed')} icon={UserX} label={t('workload.tile.unassigned')} value={summary?.unassigned} />
        <Tile
          to={tile([], 'overdue')}
          icon={AlarmClock}
          label={t('workload.tile.overdue')}
          value={summary?.overdue}
          tone={summary?.overdue ? 'danger' : undefined}
        />
      </div>
      {/* the tables show a failure of the numbers; without either table, it is shown here */}
      {load.isError && !load.data && groups.isError && users.isError && (
        <ErrorState error={load.error} onRetry={() => void load.refetch()} />
      )}
      <p className="workload__scope muted">
        {t('workload.scope')}
        {tasklist && !openFilter.isPending && !filterId && <> {t('workload.noOpenFilter')}</>}
      </p>
      {unlisted > 0 && (
        <p className="workload__orphaned" role="note">
          <UserX aria-hidden size={14} />
          {t('workload.unlisted', { count: unlisted })}
        </p>
      )}
      {!!summary?.orphaned && (
        <p className="workload__orphaned" role="note">
          <Users aria-hidden size={14} />
          {t('workload.orphaned', { count: summary.orphaned })}
        </p>
      )}
      <label className="option option--inline workload__hide">
        <input type="checkbox" checked={hideEmpty} onChange={(event) => setHideEmpty(event.target.checked)} />
        <span className="option__label">{t('workload.hideEmpty')}</span>
      </label>
      <Card title={t('workload.byGroup')} flush>
        <div className="workload__find">
          <SearchInput
            label={t('workload.searchGroups')}
            value={groupSearch}
            onChange={(value) => {
              setGroupSearch(value);
              setGroupPage(0);
            }}
          />
        </div>
        {groups.isError ? (
          <Unavailable error={groups.error} onRetry={() => void groups.refetch()} what="groups" />
        ) : (
          <LoadTable
            label={t('workload.byGroup')}
            kind="group"
            rows={load.data?.groups}
            updating={groups.isPlaceholderData || load.isPlaceholderData}
            error={load.error}
            onRetry={() => void load.refetch()}
            linkTo={linkTo}
            hideEmpty={hideEmpty}
          />
        )}
        <PageNote count={groups.data?.count} page={groupPage} onPage={setGroupPage} />
      </Card>
      <Card title={t('workload.byUser')} flush>
        <div className="workload__find">
          <SearchInput
            label={t('workload.searchUsers')}
            value={userSearch}
            onChange={(value) => {
              setUserSearch(value);
              setUserPage(0);
            }}
          />
        </div>
        {users.isError ? (
          <Unavailable error={users.error} onRetry={() => void users.refetch()} what="users" />
        ) : (
          <LoadTable
            label={t('workload.byUser')}
            kind="user"
            rows={load.data?.users}
            updating={users.isPlaceholderData || load.isPlaceholderData}
            error={load.error}
            onRetry={() => void load.refetch()}
            linkTo={linkTo}
            hideEmpty={hideEmpty}
          />
        )}
        <PageNote count={users.data?.count} page={userPage} onPage={setUserPage} />
      </Card>
    </div>
  );
}
