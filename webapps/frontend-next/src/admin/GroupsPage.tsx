/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { Plus, SearchX, Trash2, Users, X } from 'lucide-react';
import {
  addGroupMember,
  addTenantMember,
  createGroup,
  deleteGroup,
  getGroup,
  getGroups,
  getTenants,
  getUsers,
  removeGroupMember,
  removeTenantMember,
  updateGroup,
  type Group,
  type GroupSortBy,
  type Tenant,
  type User,
} from '../api/admin';
import { isApiError } from '../api/http';
import type { SortOrder } from '../api/types';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, IconButton, Loading, NoMatch, Skeleton } from '../ui';
import { SearchInput } from '../ui/controls';
import { pageOf, useUrlSearch, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, useClampedPage, Pager, type Column } from '../ui/table';
import { ADMIN, AddMembersButton, AddMembersDialog, ReadOnlyNotice, useAccess, useAdminAction } from './common';
import { DeleteDialog, RemoveDialog, displayName, userPath, validId } from './UsersPage';

export function groupPath(id: string): string {
  return `/admin/groups/${encodeURIComponent(id)}`;
}

function GroupFields({
  value,
  onChange,
  disabled,
}: {
  value: { name: string; type: string };
  onChange: (value: { name: string; type: string }) => void;
  disabled?: boolean;
}) {
  const { t } = useI18n();
  return (
    <>
      <Field label={t('admin.group.name')}>
        {(props) => (
          <input
            {...props}
            className="input"
            disabled={disabled}
            value={value.name}
            onChange={(event) => onChange({ ...value, name: event.target.value })}
          />
        )}
      </Field>
      <Field label={t('admin.group.type')} hint={t('admin.group.typeHint')}>
        {(props) => (
          <input
            {...props}
            className="input"
            disabled={disabled}
            value={value.type}
            onChange={(event) => onChange({ ...value, type: event.target.value })}
          />
        )}
      </Field>
    </>
  );
}

function CreateGroupDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [id, setId] = useState('');
  const [value, setValue] = useState({ name: '', type: 'WORKFLOW' });
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const idError = !id.trim() ? t('validation.required') : validId(id) ? null : t('admin.validation.id');
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('admin.groups.create')}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (idError) return;
          setBusy(true);
          const group = { id: id.trim(), name: value.name.trim() || null, type: value.type.trim() || null };
          void run(() => createGroup(group), {
            success: t('admin.groups.created', { id: group.id }),
            failure: t('admin.groups.createFailed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) onCreated(group.id);
          });
        }}
      >
        <Field label={t('admin.group.id')} required error={submitted ? idError : null}>
          {(props) => (
            <input {...props} className="input" autoFocus value={id} onChange={(event) => setId(event.target.value)} />
          )}
        </Field>
        <GroupFields value={value} onChange={setValue} />
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" icon={Plus} busy={busy}>
            {t('admin.groups.createConfirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Every group of the engine. */
export function GroupsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const [sorting, setSorting] = useRemembered<{ sortBy: GroupSortBy; sortOrder: SortOrder }>('admin.groups.sort', {
    sortBy: 'id',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);
  const [creating, setCreating] = useState(false);
  const { access, known } = useAccess('/group');


  const groups = useQuery({
    queryKey: [ADMIN, 'groups', term, sorting, page],
    queryFn: ({ signal }) =>
      getGroups({ search: term, ...sorting }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<Group>[]>(
    () => [
      {
        id: 'id',
        header: t('admin.group.id'),
        sortBy: 'id',
        cell: (group) => (
          <Link className="cell-link" to={groupPath(group.id)}>
            {group.id}
          </Link>
        ),
      },
      { id: 'name', header: t('admin.group.name'), sortBy: 'name', cell: (group) => group.name },
      { id: 'type', header: t('admin.group.type'), sortBy: 'type', secondary: true, cell: (group) => group.type },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('admin.groups.title')}
        subtitle={groups.data ? t('admin.groups.count', { count: groups.data.count }) : undefined}
        actions={
          access.has('create') && (
            <Button variant="primary" icon={Plus} onClick={() => setCreating(true)}>
              {t('admin.groups.create')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && access.has('list') && !access.has('create')} />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('admin.groups.search')} />
        </div>
        <DataTable
          label={t('admin.groups.title')}
          columns={columns}
          updating={groups.isPlaceholderData}
          rows={groups.data?.items}
          rowKey={(group) => group.id}
          error={groups.error}
          onRetry={() => void groups.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as { sortBy: GroupSortBy; sortOrder: SortOrder });
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('admin.groups.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={Users} title={t('admin.groups.empty')} />
            )
          }
        />
        <Pager
          page={page}
          count={groups.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      {creating && (
        <CreateGroupDialog onClose={() => setCreating(false)} onCreated={(id) => void navigate(groupPath(id))} />
      )}
    </div>
  );
}

// --- one group -----------------------------------------------------------------

function GroupDetailsTab({ group, canEdit }: { group: Group; canEdit: boolean }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [value, setValue] = useState({ name: group.name ?? '', type: group.type ?? '' });
  const [busy, setBusy] = useState(false);
  const changed = value.name !== (group.name ?? '') || value.type !== (group.type ?? '');
  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        setBusy(true);
        void run(() => updateGroup(group.id, { name: value.name.trim() || null, type: value.type.trim() || null }), {
          success: t('admin.group.saved'),
          failure: t('admin.group.saveFailed'),
        }).finally(() => setBusy(false));
      }}
    >
      <GroupFields value={value} onChange={setValue} disabled={!canEdit} />
      {canEdit && (
        <div className="admin-form__actions">
          <Button type="submit" variant="primary" busy={busy} disabled={!changed}>
            {t('action.save')}
          </Button>
        </div>
      )}
    </form>
  );
}

function GroupMembersTab({ group }: { group: Group }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<User | null>(null);
  const { access } = useAccess(`/group/${encodeURIComponent(group.id)}/members`);
  const members = useQuery({
    queryKey: [ADMIN, 'group-members', group.id, page],
    queryFn: ({ signal }) =>
      getUsers({ memberOfGroup: group.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });
  const columns: Column<User>[] = [
    {
      id: 'id',
      header: t('admin.user.id'),
      cell: (user) => (
        <Link className="cell-link" to={userPath(user.id)}>
          {user.id}
        </Link>
      ),
    },
    { id: 'name', header: t('admin.user.name'), cell: (user) => displayName(user) },
    {
      id: 'email',
      header: t('admin.user.email'),
      secondary: true,
      cell: (user) => user.email ?? <span className="muted">—</span>,
    },
    {
      id: 'actions',
      header: t('cockpit.actions'),
      align: 'end',
      cell: (user) =>
        access.has('delete') && (
          <IconButton
            icon={X}
            label={t('admin.members.removeUser', { name: displayName(user) })}
            onClick={() => setRemoving(user)}
          />
        ),
    },
  ];
  useClampedPage(page, members.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">
          {members.data ? t('admin.users.count', { count: members.data.count }) : null}
        </span>
        <AddMembersButton
          allowed={access.has('create')}
          label={t('admin.group.addMembers')}
          onClick={() => setAdding(true)}
        />
      </div>
      <DataTable
        label={t('admin.group.tab.members')}
        columns={columns}
        updating={members.isPlaceholderData}
        rows={members.data?.items}
        rowKey={(user) => user.id}
        error={members.error}
        onRetry={() => void members.refetch()}
        empty={<EmptyState icon={Users} title={t('admin.group.noMembers')} />}
      />
      <Pager page={page} count={members.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.group.addMembers')}
          searchLabel={t('admin.users.search')}
          exclude={new Set(members.data?.items.map((user) => user.id))}
          search={async (term, signal) => {
            const found = await getUsers({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((user) => ({ id: user.id, label: displayName(user) })) };
          }}
          onAdd={(userId) =>
            run(() => addGroupMember(group.id, userId), {
              success: t('admin.members.addedTo', { name: group.name ?? group.id }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeUserFromGroup', { user: removing.id, group: group.name ?? group.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeGroupMember(group.id, removing.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

function GroupTenantsTab({ group }: { group: Group }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Tenant | null>(null);
  const { access } = useAccess('/tenant/_/group-members');
  const tenants = useQuery({
    queryKey: [ADMIN, 'group-tenants', group.id, page],
    queryFn: ({ signal }) =>
      getTenants({ groupMember: group.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });
  const columns: Column<Tenant>[] = [
    {
      id: 'id',
      header: t('admin.tenant.id'),
      cell: (tenant) => (
        <Link className="cell-link" to={`/admin/tenants/${encodeURIComponent(tenant.id)}`}>
          {tenant.id}
        </Link>
      ),
    },
    { id: 'name', header: t('admin.tenant.name'), cell: (tenant) => tenant.name },
    {
      id: 'actions',
      header: t('cockpit.actions'),
      align: 'end',
      cell: (tenant) =>
        access.has('delete') && (
          <IconButton
            icon={X}
            label={t('admin.members.removeTenant', { name: tenant.name ?? tenant.id })}
            onClick={() => setRemoving(tenant)}
          />
        ),
    },
  ];
  useClampedPage(page, tenants.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">{t('admin.group.tenantsText')}</span>
        <AddMembersButton
          allowed={access.has('create')}
          label={t('admin.user.addTenants')}
          onClick={() => setAdding(true)}
        />
      </div>
      <DataTable
        label={t('admin.group.tab.tenants')}
        columns={columns}
        updating={tenants.isPlaceholderData}
        rows={tenants.data?.items}
        rowKey={(tenant) => tenant.id}
        error={tenants.error}
        onRetry={() => void tenants.refetch()}
        empty={<EmptyState title={t('admin.group.noTenants')} />}
      />
      <Pager page={page} count={tenants.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.user.addTenants')}
          searchLabel={t('admin.tenants.search')}
          exclude={new Set(tenants.data?.items.map((tenant) => tenant.id))}
          search={async (term, signal) => {
            const found = await getTenants({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((tenant) => ({ id: tenant.id, label: tenant.name || tenant.id })) };
          }}
          onAdd={(tenantId) =>
            run(() => addTenantMember(tenantId, 'group', group.id), {
              success: t('admin.members.addedTo', { name: tenantId }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeGroupFromTenant', { group: group.id, tenant: removing.name ?? removing.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeTenantMember(removing.id, 'group', group.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

const GROUP_TABS = ['details', 'members', 'tenants'] as const;

/** One group: its name and type, its members and its tenants. */
export function GroupPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const run = useAdminAction();
  const [params, setParams] = useUrlState();
  const [deleting, setDeleting] = useState(false);
  const group = useQuery({ queryKey: [ADMIN, 'group', id], queryFn: () => getGroup(id) });
  const { access, known } = useAccess(`/group/${encodeURIComponent(id)}`);
  const back = { to: '/admin/groups', label: t('admin.groups.title') };

  if (group.isError && !group.data) {
    return (
      <div className="page">
        <PageHeader title={t('admin.group.title')} back={back} />
        <Card>
          {isApiError(group.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('admin.group.notFound')} />
          ) : (
            <ErrorState error={group.error} onRetry={() => void group.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!group.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }
  const current = group.data;
  const tab = GROUP_TABS.find((candidate) => candidate === params.get('tab')) ?? 'details';
  return (
    <div className="page">
      <PageHeader
        title={current.name || current.id}
        back={back}
        subtitle={<span className="mono">{current.id}</span>}
        actions={
          access.has('delete') && (
            <Button variant="danger-ghost" icon={Trash2} onClick={() => setDeleting(true)}>
              {t('admin.group.delete')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && !access.has('update')} />
      <Card flush>
        <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setParams({ tab: next === 'details' ? null : next })}>
          <Tabs.List className="ctabs__list" aria-label={t('admin.group.tabs')}>
            {GROUP_TABS.map((name) => (
              <Tabs.Trigger key={name} className="ctabs__tab" value={name}>
                {t(`admin.group.tab.${name}`)}
              </Tabs.Trigger>
            ))}
          </Tabs.List>
          <Tabs.Content value="details" className="ctabs__content">
            <GroupDetailsTab key={JSON.stringify(current)} group={current} canEdit={access.has('update')} />
          </Tabs.Content>
          <Tabs.Content value="members" className="ctabs__content">
            <GroupMembersTab group={current} />
          </Tabs.Content>
          <Tabs.Content value="tenants" className="ctabs__content">
            <GroupTenantsTab group={current} />
          </Tabs.Content>
        </Tabs.Root>
      </Card>
      {deleting && (
        <DeleteDialog
          title={t('admin.group.delete')}
          text={t('admin.group.deleteText', { id: current.id })}
          onClose={() => setDeleting(false)}
          onConfirm={async () => {
            const ok = await run(() => deleteGroup(current.id), {
              success: t('admin.group.deleted', { id: current.id }),
              failure: t('admin.group.deleteFailed'),
            });
            if (ok) void navigate('/admin/groups');
            return ok;
          }}
        />
      )}
    </div>
  );
}
