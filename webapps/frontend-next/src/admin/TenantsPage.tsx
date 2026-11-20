/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { Building2, Plus, SearchX, Trash2, Users, X } from 'lucide-react';
import {
  addTenantMember,
  createTenant,
  deleteTenant,
  getGroups,
  getTenant,
  getTenants,
  getUsers,
  removeTenantMember,
  updateTenant,
  type Group,
  type Tenant,
  type TenantSortBy,
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
import { groupPath } from './GroupsPage';
import { DeleteDialog, RemoveDialog, displayName, userPath, validId } from './UsersPage';

export function tenantPath(id: string): string {
  return `/admin/tenants/${encodeURIComponent(id)}`;
}

function CreateTenantDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const idError = !id.trim() ? t('validation.required') : validId(id) ? null : t('admin.validation.id');
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('admin.tenants.create')}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (idError) return;
          setBusy(true);
          const tenant = { id: id.trim(), name: name.trim() || null };
          void run(() => createTenant(tenant), {
            success: t('admin.tenants.created', { id: tenant.id }),
            failure: t('admin.tenants.createFailed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) onCreated(tenant.id);
          });
        }}
      >
        <Field label={t('admin.tenant.id')} required error={submitted ? idError : null}>
          {(props) => (
            <input {...props} className="input" autoFocus value={id} onChange={(event) => setId(event.target.value)} />
          )}
        </Field>
        <Field label={t('admin.tenant.name')}>
          {(props) => (
            <input {...props} className="input" value={name} onChange={(event) => setName(event.target.value)} />
          )}
        </Field>
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" icon={Plus} busy={busy}>
            {t('admin.tenants.createConfirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Every tenant of the engine. */
export function TenantsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const [sorting, setSorting] = useRemembered<{ sortBy: TenantSortBy; sortOrder: SortOrder }>('admin.tenants.sort', {
    sortBy: 'id',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);
  const [creating, setCreating] = useState(false);
  const { access, known } = useAccess('/tenant');


  const tenants = useQuery({
    queryKey: [ADMIN, 'tenants', term, sorting, page],
    queryFn: ({ signal }) =>
      getTenants({ search: term, ...sorting }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<Tenant>[]>(
    () => [
      {
        id: 'id',
        header: t('admin.tenant.id'),
        sortBy: 'id',
        cell: (tenant) => (
          <Link className="cell-link" to={tenantPath(tenant.id)}>
            {tenant.id}
          </Link>
        ),
      },
      { id: 'name', header: t('admin.tenant.name'), sortBy: 'name', cell: (tenant) => tenant.name },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('admin.tenants.title')}
        subtitle={tenants.data ? t('admin.tenants.count', { count: tenants.data.count }) : undefined}
        actions={
          access.has('create') && (
            <Button variant="primary" icon={Plus} onClick={() => setCreating(true)}>
              {t('admin.tenants.create')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && access.has('list') && !access.has('create')} />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('admin.tenants.search')} />
        </div>
        <DataTable
          label={t('admin.tenants.title')}
          columns={columns}
          updating={tenants.isPlaceholderData}
          rows={tenants.data?.items}
          rowKey={(tenant) => tenant.id}
          error={tenants.error}
          onRetry={() => void tenants.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as { sortBy: TenantSortBy; sortOrder: SortOrder });
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('admin.tenants.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={Building2} title={t('admin.tenants.empty')}>
                {t('admin.tenants.emptyHint')}
              </EmptyState>
            )
          }
        />
        <Pager
          page={page}
          count={tenants.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      {creating && (
        <CreateTenantDialog onClose={() => setCreating(false)} onCreated={(id) => void navigate(tenantPath(id))} />
      )}
    </div>
  );
}

// --- one tenant ----------------------------------------------------------------

function TenantDetailsTab({ tenant, canEdit }: { tenant: Tenant; canEdit: boolean }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [name, setName] = useState(tenant.name ?? '');
  const [busy, setBusy] = useState(false);
  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        setBusy(true);
        void run(() => updateTenant(tenant.id, { name: name.trim() || null }), {
          success: t('admin.tenant.saved'),
          failure: t('admin.tenant.saveFailed'),
        }).finally(() => setBusy(false));
      }}
    >
      <Field label={t('admin.tenant.name')}>
        {(props) => (
          <input
            {...props}
            className="input"
            disabled={!canEdit}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        )}
      </Field>
      {canEdit && (
        <div className="admin-form__actions">
          <Button type="submit" variant="primary" busy={busy} disabled={name === (tenant.name ?? '')}>
            {t('action.save')}
          </Button>
        </div>
      )}
    </form>
  );
}

function TenantUsersTab({ tenant }: { tenant: Tenant }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<User | null>(null);
  const { access } = useAccess(`/tenant/${encodeURIComponent(tenant.id)}/user-members`);
  const users = useQuery({
    queryKey: [ADMIN, 'tenant-users', tenant.id, page],
    queryFn: ({ signal }) =>
      getUsers({ memberOfTenant: tenant.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
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
  useClampedPage(page, users.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">{users.data ? t('admin.users.count', { count: users.data.count }) : null}</span>
        <AddMembersButton allowed={access.has('create')} label={t('admin.tenant.addUsers')} onClick={() => setAdding(true)} />
      </div>
      <DataTable
        label={t('admin.tenant.tab.users')}
        columns={columns}
        updating={users.isPlaceholderData}
        rows={users.data?.items}
        rowKey={(user) => user.id}
        error={users.error}
        onRetry={() => void users.refetch()}
        empty={<EmptyState icon={Users} title={t('admin.tenant.noUsers')} />}
      />
      <Pager page={page} count={users.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.tenant.addUsers')}
          searchLabel={t('admin.users.search')}
          exclude={new Set(users.data?.items.map((user) => user.id))}
          search={async (term, signal) => {
            const found = await getUsers({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((user) => ({ id: user.id, label: displayName(user) })) };
          }}
          onAdd={(userId) =>
            run(() => addTenantMember(tenant.id, 'user', userId), {
              success: t('admin.members.addedTo', { name: tenant.name ?? tenant.id }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeUserFromTenant', { user: removing.id, tenant: tenant.name ?? tenant.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeTenantMember(tenant.id, 'user', removing.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

function TenantGroupsTab({ tenant }: { tenant: Tenant }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Group | null>(null);
  const { access } = useAccess(`/tenant/${encodeURIComponent(tenant.id)}/group-members`);
  const groups = useQuery({
    queryKey: [ADMIN, 'tenant-groups', tenant.id, page],
    queryFn: ({ signal }) =>
      getGroups({ memberOfTenant: tenant.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });
  const columns: Column<Group>[] = [
    {
      id: 'id',
      header: t('admin.group.id'),
      cell: (group) => (
        <Link className="cell-link" to={groupPath(group.id)}>
          {group.id}
        </Link>
      ),
    },
    { id: 'name', header: t('admin.group.name'), cell: (group) => group.name },
    {
      id: 'actions',
      header: t('cockpit.actions'),
      align: 'end',
      cell: (group) =>
        access.has('delete') && (
          <IconButton
            icon={X}
            label={t('admin.members.removeGroup', { name: group.name ?? group.id })}
            onClick={() => setRemoving(group)}
          />
        ),
    },
  ];
  useClampedPage(page, groups.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">{t('admin.tenant.groupsText')}</span>
        <AddMembersButton allowed={access.has('create')} label={t('admin.tenant.addGroups')} onClick={() => setAdding(true)} />
      </div>
      <DataTable
        label={t('admin.tenant.tab.groups')}
        columns={columns}
        updating={groups.isPlaceholderData}
        rows={groups.data?.items}
        rowKey={(group) => group.id}
        error={groups.error}
        onRetry={() => void groups.refetch()}
        empty={<EmptyState icon={Users} title={t('admin.tenant.noGroups')} />}
      />
      <Pager page={page} count={groups.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.tenant.addGroups')}
          searchLabel={t('admin.groups.search')}
          exclude={new Set(groups.data?.items.map((group) => group.id))}
          search={async (term, signal) => {
            const found = await getGroups({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((group) => ({ id: group.id, label: group.name || group.id })) };
          }}
          onAdd={(groupId) =>
            run(() => addTenantMember(tenant.id, 'group', groupId), {
              success: t('admin.members.addedTo', { name: tenant.name ?? tenant.id }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeGroupFromTenant', { group: removing.id, tenant: tenant.name ?? tenant.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeTenantMember(tenant.id, 'group', removing.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

const TENANT_TABS = ['details', 'users', 'groups'] as const;

/** One tenant: its name, its users and its groups. */
export function TenantPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const run = useAdminAction();
  const [params, setParams] = useUrlState();
  const [deleting, setDeleting] = useState(false);
  const tenant = useQuery({ queryKey: [ADMIN, 'tenant', id], queryFn: () => getTenant(id) });
  const { access, known } = useAccess(`/tenant/${encodeURIComponent(id)}`);
  const back = { to: '/admin/tenants', label: t('admin.tenants.title') };

  if (tenant.isError && !tenant.data) {
    return (
      <div className="page">
        <PageHeader title={t('admin.tenant.title')} back={back} />
        <Card>
          {isApiError(tenant.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('admin.tenant.notFound')} />
          ) : (
            <ErrorState error={tenant.error} onRetry={() => void tenant.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!tenant.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }
  const current = tenant.data;
  const tab = TENANT_TABS.find((candidate) => candidate === params.get('tab')) ?? 'details';
  return (
    <div className="page">
      <PageHeader
        title={current.name || current.id}
        back={back}
        subtitle={<span className="mono">{current.id}</span>}
        actions={
          access.has('delete') && (
            <Button variant="danger-ghost" icon={Trash2} onClick={() => setDeleting(true)}>
              {t('admin.tenant.delete')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && !access.has('update')} />
      <Card flush>
        <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setParams({ tab: next === 'details' ? null : next })}>
          <Tabs.List className="ctabs__list" aria-label={t('admin.tenant.tabs')}>
            {TENANT_TABS.map((name) => (
              <Tabs.Trigger key={name} className="ctabs__tab" value={name}>
                {t(`admin.tenant.tab.${name}`)}
              </Tabs.Trigger>
            ))}
          </Tabs.List>
          <Tabs.Content value="details" className="ctabs__content">
            <TenantDetailsTab key={JSON.stringify(current)} tenant={current} canEdit={access.has('update')} />
          </Tabs.Content>
          <Tabs.Content value="users" className="ctabs__content">
            <TenantUsersTab tenant={current} />
          </Tabs.Content>
          <Tabs.Content value="groups" className="ctabs__content">
            <TenantGroupsTab tenant={current} />
          </Tabs.Content>
        </Tabs.Root>
      </Card>
      {deleting && (
        <DeleteDialog
          title={t('admin.tenant.delete')}
          text={t('admin.tenant.deleteText', { id: current.id })}
          onClose={() => setDeleting(false)}
          onConfirm={async () => {
            const ok = await run(() => deleteTenant(current.id), {
              success: t('admin.tenant.deleted', { id: current.id }),
              failure: t('admin.tenant.deleteFailed'),
            });
            if (ok) void navigate('/admin/tenants');
            return ok;
          }}
        />
      )}
    </div>
  );
}
