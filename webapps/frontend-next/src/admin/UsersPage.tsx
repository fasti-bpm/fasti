/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { KeyRound, SearchX, Trash2, UserPlus, Users, X } from 'lucide-react';
import {
  addGroupMember,
  addTenantMember,
  changePassword,
  createUser,
  deleteUser,
  getGroups,
  getTenants,
  getUser,
  getUsers,
  removeGroupMember,
  removeTenantMember,
  updateUser,
  type Group,
  type Tenant,
  type User,
  type UserSortBy,
} from '../api/admin';
import { isApiError } from '../api/http';
import { fullName } from '../api/engine';
import type { SortOrder } from '../api/types';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, IconButton, Loading, NoMatch, Skeleton } from '../ui';
import { ConfirmDialog, SearchInput } from '../ui/controls';
import { pageOf, useUrlSearch, useRemembered, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, useClampedPage, Pager, type Column } from '../ui/table';
import {
  ADMIN,
  AddMembersButton,
  AddMembersDialog,
  AllowedFor,
  PasswordFields,
  ReadOnlyNotice,
  passwordProblems,
  useAccess,
  useAdminAction,
} from './common';

export function userPath(id: string): string {
  return `/admin/users/${encodeURIComponent(id)}`;
}

export function displayName(user: User): string {
  return fullName(user) || user.id;
}

/** `true` if the value looks like an e-mail address; an empty value is fine. */
export function validEmail(value: string): boolean {
  return !value.trim() || /^[^\s@]+@[^\s@]+$/.test(value.trim());
}

/** The ids the engine accepts by default: letters, digits and a few signs. */
export function validId(value: string): boolean {
  return /^[\w.@-]+$/.test(value.trim());
}

function ProfileFields({
  value,
  onChange,
  errors,
  disabled,
}: {
  value: { firstName: string; lastName: string; email: string };
  onChange: (value: { firstName: string; lastName: string; email: string }) => void;
  errors: { email?: string | null };
  disabled?: boolean;
}) {
  const { t } = useI18n();
  return (
    <>
      <div className="admin-pair">
        <Field label={t('admin.user.firstName')}>
          {(props) => (
            <input
              {...props}
              className="input"
              autoComplete="off"
              disabled={disabled}
              value={value.firstName}
              onChange={(event) => onChange({ ...value, firstName: event.target.value })}
            />
          )}
        </Field>
        <Field label={t('admin.user.lastName')}>
          {(props) => (
            <input
              {...props}
              className="input"
              autoComplete="off"
              disabled={disabled}
              value={value.lastName}
              onChange={(event) => onChange({ ...value, lastName: event.target.value })}
            />
          )}
        </Field>
      </div>
      <Field label={t('admin.user.email')} error={errors.email}>
        {(props) => (
          <input
            {...props}
            className="input"
            type="email"
            autoComplete="off"
            disabled={disabled}
            value={value.email}
            onChange={(event) => onChange({ ...value, email: event.target.value })}
          />
        )}
      </Field>
    </>
  );
}

/** The form for a new user; also used for the first administrator. */
export function NewUserForm({
  submitLabel,
  onSubmit,
  onCancel,
}: {
  submitLabel: string;
  onSubmit: (user: { id: string; firstName: string; lastName: string; email: string; password: string }) => Promise<boolean>;
  onCancel?: () => void;
}) {
  const { t } = useI18n();
  const [id, setId] = useState('');
  const [profile, setProfile] = useState({ firstName: '', lastName: '', email: '' });
  const [password, setPassword] = useState('');
  const [repeated, setRepeated] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const idError = !id.trim() ? t('validation.required') : validId(id) ? null : t('admin.validation.id');
  const emailError = validEmail(profile.email) ? null : t('admin.validation.email');
  return (
    <form
      className="confirm"
      onSubmit={(event) => {
        event.preventDefault();
        setSubmitted(true);
        if (idError || emailError) return;
        setBusy(true);
        void passwordProblems(password, repeated, t, { id: id.trim(), ...profile })
          .then(async (found) => {
            setProblems(found);
            if (found.length > 0) return;
            await onSubmit({ id: id.trim(), ...profile, email: profile.email.trim(), password });
          })
          .finally(() => setBusy(false));
      }}
    >
      <Field label={t('admin.user.id')} required error={submitted ? idError : null} hint={t('admin.user.idHint')}>
        {(props) => (
          <input
            {...props}
            className="input"
            autoFocus
            autoComplete="off"
            value={id}
            onChange={(event) => setId(event.target.value)}
          />
        )}
      </Field>
      <ProfileFields value={profile} onChange={setProfile} errors={{ email: submitted ? emailError : null }} />
      <PasswordFields
        password={password}
        repeated={repeated}
        onPassword={setPassword}
        onRepeated={setRepeated}
        problems={problems}
      />
      <div className="confirm__actions">
        {onCancel && <Button onClick={onCancel}>{t('action.cancel')}</Button>}
        <Button type="submit" variant="primary" icon={UserPlus} busy={busy}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function CreateUserDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const { t } = useI18n();
  const run = useAdminAction();
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('admin.users.create')}>
      <NewUserForm
        submitLabel={t('admin.users.createConfirm')}
        onCancel={onClose}
        onSubmit={async (user) => {
          const ok = await run(() => createUser(user), {
            success: t('admin.users.created', { id: user.id }),
            failure: t('admin.users.createFailed'),
          });
          if (ok) onCreated(user.id);
          return ok;
        }}
      />
    </Dialog>
  );
}

/** Every user of the engine, searchable by id, name and e-mail address. */
export function UsersPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const [sorting, setSorting] = useRemembered<{ sortBy: UserSortBy; sortOrder: SortOrder }>('admin.users.sort', {
    sortBy: 'userId',
    sortOrder: 'asc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);
  const [creating, setCreating] = useState(false);
  const { access, known } = useAccess('/user');


  const users = useQuery({
    queryKey: [ADMIN, 'users', term, sorting, page],
    queryFn: ({ signal }) =>
      getUsers({ search: term, ...sorting }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<User>[]>(
    () => [
      {
        id: 'id',
        header: t('admin.user.id'),
        sortBy: 'userId',
        cell: (user) => (
          <Link className="cell-link" to={userPath(user.id)}>
            {user.id}
          </Link>
        ),
      },
      { id: 'firstName', header: t('admin.user.firstName'), sortBy: 'firstName', cell: (user) => user.firstName },
      { id: 'lastName', header: t('admin.user.lastName'), sortBy: 'lastName', cell: (user) => user.lastName },
      {
        id: 'email',
        header: t('admin.user.email'),
        sortBy: 'email',
        secondary: true,
        cell: (user) => user.email ?? <span className="muted">—</span>,
      },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('admin.users.title')}
        subtitle={users.data ? t('admin.users.count', { count: users.data.count }) : undefined}
        actions={
          access.has('create') && (
            <Button variant="primary" icon={UserPlus} onClick={() => setCreating(true)}>
              {t('admin.users.create')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && access.has('list') && !access.has('create')} />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('admin.users.search')} />
        </div>
        <DataTable
          label={t('admin.users.title')}
          columns={columns}
          updating={users.isPlaceholderData}
          rows={users.data?.items}
          rowKey={(user) => user.id}
          error={users.error}
          onRetry={() => void users.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as { sortBy: UserSortBy; sortOrder: SortOrder });
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('admin.users.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={Users} title={t('admin.users.empty')} />
            )
          }
        />
        <Pager
          page={page}
          count={users.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      {creating && (
        <CreateUserDialog onClose={() => setCreating(false)} onCreated={(id) => void navigate(userPath(id))} />
      )}
    </div>
  );
}

// --- one user ------------------------------------------------------------------

/** The name and e-mail address of a user; also used for the own profile. */
export function ProfileTab({
  user,
  canEdit,
  onSaved,
}: {
  user: User;
  canEdit: boolean;
  onSaved?: () => void;
}) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [value, setValue] = useState({
    firstName: user.firstName ?? '',
    lastName: user.lastName ?? '',
    email: user.email ?? '',
  });
  const [busy, setBusy] = useState(false);
  const emailError = validEmail(value.email) ? null : t('admin.validation.email');
  const changed =
    value.firstName !== (user.firstName ?? '') ||
    value.lastName !== (user.lastName ?? '') ||
    value.email !== (user.email ?? '');
  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        if (emailError) return;
        setBusy(true);
        void run(
          () =>
            updateUser(user.id, {
              firstName: value.firstName.trim(),
              lastName: value.lastName.trim(),
              email: value.email.trim(),
            }),
          { success: t('admin.user.saved'), failure: t('admin.user.saveFailed') },
        )
          .then((ok) => {
            if (ok) onSaved?.();
          })
          .finally(() => setBusy(false));
      }}
    >
      <ProfileFields value={value} onChange={setValue} errors={{ email: emailError }} disabled={!canEdit} />
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

/**
 * A new password for a user, confirmed by the password of the user who is
 * logged in; `self` if both are the same user.
 */
export function PasswordTab({ user, self = false }: { user: User; self?: boolean }) {
  const { t } = useI18n();
  const session = useSession();
  const run = useAdminAction();
  const [own, setOwn] = useState('');
  const [password, setPassword] = useState('');
  const [repeated, setRepeated] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [ownError, setOwnError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <form
      className="admin-form"
      onSubmit={(event) => {
        event.preventDefault();
        setOwnError(own ? null : t('validation.required'));
        if (!own) return;
        setBusy(true);
        void passwordProblems(password, repeated, t, {
          id: user.id,
          firstName: user.firstName ?? undefined,
          lastName: user.lastName ?? undefined,
          email: user.email ?? undefined,
        })
          .then(async (found) => {
            setProblems(found);
            if (found.length > 0) return;
            const ok = await run(() => changePassword(user.id, password, own), {
              success: t('admin.password.changed'),
              failure: t('admin.password.changeFailed'),
            });
            if (ok) {
              setOwn('');
              setPassword('');
              setRepeated('');
            }
          })
          .finally(() => setBusy(false));
      }}
    >
      <p className="muted">{self ? t('profile.password.text') : t('admin.password.text')}</p>
      <PasswordFields
        label={t('admin.password.new')}
        password={password}
        repeated={repeated}
        onPassword={setPassword}
        onRepeated={setRepeated}
        problems={problems}
      />
      <Field
        label={self ? t('profile.password.current') : t('admin.password.own', { user: session.userId })}
        required
        error={ownError}
        hint={self ? t('profile.password.currentHint') : t('admin.password.ownHint')}
      >
        {(props) => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="current-password"
            value={own}
            onChange={(event) => setOwn(event.target.value)}
          />
        )}
      </Field>
      <div className="admin-form__actions">
        <Button type="submit" variant="primary" icon={KeyRound} busy={busy}>
          {t('admin.password.change')}
        </Button>
      </div>
    </form>
  );
}

/** The groups of a user, with the means to add and remove memberships. */
function UserGroupsTab({ user }: { user: User }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Group | null>(null);
  // the permission may be granted for single groups only: each group is asked on its own
  const { access } = useAccess('/group');
  const groups = useQuery({
    queryKey: [ADMIN, 'user-groups', user.id, page],
    queryFn: ({ signal }) =>
      getGroups({ member: user.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });
  // whether one membership may change is asked per group (a read-only identity
  // provider allows none); the buttons are offered if groups can be read at all
  const canAdd = access.has('list');
  const canRemove = canAdd;
  const membersOf = (groupId: string) => `/group/${encodeURIComponent(groupId)}/members`;
  const columns: Column<Group>[] = [
    {
      id: 'id',
      header: t('admin.group.id'),
      cell: (group) => (
        <Link className="cell-link" to={`/admin/groups/${encodeURIComponent(group.id)}`}>
          {group.id}
        </Link>
      ),
    },
    { id: 'name', header: t('admin.group.name'), cell: (group) => group.name },
    { id: 'type', header: t('admin.group.type'), secondary: true, cell: (group) => group.type },
    {
      id: 'actions',
      header: t('cockpit.actions'),
      align: 'end',
      cell: (group) =>
        canRemove && (
          <AllowedFor path={membersOf(group.id)} action="delete">
            <IconButton
              icon={X}
              label={t('admin.members.removeGroup', { name: group.name ?? group.id })}
              onClick={() => setRemoving(group)}
            />
          </AllowedFor>
        ),
    },
  ];
  useClampedPage(page, groups.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">{t('admin.user.groupsText')}</span>
        <AddMembersButton allowed={canAdd} label={t('admin.user.addGroups')} onClick={() => setAdding(true)} />
      </div>
      <DataTable
        label={t('admin.user.groups')}
        columns={columns}
        updating={groups.isPlaceholderData}
        rows={groups.data?.items}
        rowKey={(group) => group.id}
        error={groups.error}
        onRetry={() => void groups.refetch()}
        empty={<EmptyState icon={Users} title={t('admin.user.noGroups')} />}
      />
      <Pager page={page} count={groups.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.user.addGroups')}
          searchLabel={t('admin.groups.search')}
          exclude={new Set(groups.data?.items.map((group) => group.id))}
          accessPath={membersOf}
          search={async (term, signal) => {
            const found = await getGroups({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((group) => ({ id: group.id, label: group.name || group.id })) };
          }}
          onAdd={(groupId) =>
            run(() => addGroupMember(groupId, user.id), {
              success: t('admin.members.addedTo', { name: groupId }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeUserFromGroup', { user: user.id, group: removing.name ?? removing.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeGroupMember(removing.id, user.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

function UserTenantsTab({ user }: { user: User }) {
  const { t } = useI18n();
  const run = useAdminAction();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Tenant | null>(null);
  // as for groups, the permission may be granted for single tenants only
  const { access } = useAccess('/tenant');
  const membersOf = (tenantId: string) => `/tenant/${encodeURIComponent(tenantId)}/user-members`;
  const tenants = useQuery({
    queryKey: [ADMIN, 'user-tenants', user.id, page],
    queryFn: ({ signal }) =>
      getTenants({ userMember: user.id }, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
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
        access.has('list') && (
          <AllowedFor path={membersOf(tenant.id)} action="delete">
            <IconButton
              icon={X}
              label={t('admin.members.removeTenant', { name: tenant.name ?? tenant.id })}
              onClick={() => setRemoving(tenant)}
            />
          </AllowedFor>
        ),
    },
  ];
  useClampedPage(page, tenants.data?.count, setPage);
  return (
    <>
      <div className="toolbar">
        <span className="toolbar__text">{t('admin.user.tenantsText')}</span>
        <AddMembersButton
          allowed={access.has('list')}
          label={t('admin.user.addTenants')}
          onClick={() => setAdding(true)}
        />
      </div>
      <DataTable
        label={t('admin.user.tenants')}
        columns={columns}
        updating={tenants.isPlaceholderData}
        rows={tenants.data?.items}
        rowKey={(tenant) => tenant.id}
        error={tenants.error}
        onRetry={() => void tenants.refetch()}
        empty={<EmptyState title={t('admin.user.noTenants')} />}
      />
      <Pager page={page} count={tenants.data?.count ?? 0} onPage={setPage} />
      {adding && (
        <AddMembersDialog
          title={t('admin.user.addTenants')}
          searchLabel={t('admin.tenants.search')}
          exclude={new Set(tenants.data?.items.map((tenant) => tenant.id))}
          accessPath={membersOf}
          search={async (term, signal) => {
            const found = await getTenants({ search: term }, { firstResult: 0, maxResults: 50 }, signal);
            return { count: found.count, items: found.items.map((tenant) => ({ id: tenant.id, label: tenant.name || tenant.id })) };
          }}
          onAdd={(tenantId) =>
            run(() => addTenantMember(tenantId, 'user', user.id), {
              success: t('admin.members.addedTo', { name: tenantId }),
              failure: t('admin.members.addFailed'),
            })
          }
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <RemoveDialog
          text={t('admin.members.removeUserFromTenant', { user: user.id, tenant: removing.name ?? removing.id })}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            run(() => removeTenantMember(removing.id, 'user', user.id), {
              success: t('admin.members.removed'),
              failure: t('admin.members.removeFailed'),
            })
          }
        />
      )}
    </>
  );
}

/** Asks before a membership ends. */
export function RemoveDialog({
  text,
  onClose,
  onConfirm,
}: {
  text: string;
  onClose: () => void;
  onConfirm: () => Promise<boolean>;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('admin.members.removeTitle')}
      text={text}
      confirmLabel={t('admin.members.remove')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void onConfirm().then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    />
  );
}

/** Asks before something is deleted for good. */
export function DeleteDialog({
  title,
  text,
  onClose,
  onConfirm,
}: {
  title: string;
  text: string;
  onClose: () => void;
  onConfirm: () => Promise<boolean>;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={title}
      text={text}
      confirmLabel={t('admin.delete')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void onConfirm().then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    />
  );
}

const USER_TABS = ['profile', 'password', 'groups', 'tenants'] as const;

/** One user: profile, password and memberships. */
export function UserPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const session = useSession();
  const navigate = useNavigate();
  const run = useAdminAction();
  const [params, setParams] = useUrlState();
  const [deleting, setDeleting] = useState(false);
  const user = useQuery({ queryKey: [ADMIN, 'user', id], queryFn: () => getUser(id) });
  const { access, known } = useAccess(`/user/${encodeURIComponent(id)}`);
  const back = { to: '/admin/users', label: t('admin.users.title') };

  if (user.isError && !user.data) {
    return (
      <div className="page">
        <PageHeader title={t('admin.user.title')} back={back} />
        <Card>
          {isApiError(user.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('admin.user.notFound')} />
          ) : (
            <ErrorState error={user.error} onRetry={() => void user.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!user.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }

  const current = user.data;
  const canEdit = access.has('update');
  // the engine only lets users change the password of an account it stores itself
  const tabs = USER_TABS.filter((tab) => tab !== 'password' || canEdit);
  const tab = tabs.find((candidate) => candidate === params.get('tab')) ?? 'profile';
  const self = current.id === session.userId;

  return (
    <div className="page">
      <PageHeader
        title={displayName(current)}
        back={back}
        subtitle={<span className="mono">{current.id}</span>}
        actions={
          access.has('delete') && (
            <Button variant="danger-ghost" icon={Trash2} onClick={() => setDeleting(true)}>
              {t('admin.user.delete')}
            </Button>
          )
        }
      />
      <ReadOnlyNotice show={known && !canEdit} />
      <Card flush>
        <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setParams({ tab: next === 'profile' ? null : next })}>
          <Tabs.List className="ctabs__list" aria-label={t('admin.user.tabs')}>
            {tabs.map((name) => (
              <Tabs.Trigger key={name} className="ctabs__tab" value={name}>
                {t(`admin.user.tab.${name}`)}
              </Tabs.Trigger>
            ))}
          </Tabs.List>
          <Tabs.Content value="profile" className="ctabs__content">
            <ProfileTab key={JSON.stringify(current)} user={current} canEdit={canEdit} />
          </Tabs.Content>
          {canEdit && (
            <Tabs.Content value="password" className="ctabs__content">
              <PasswordTab user={current} self={self} />
            </Tabs.Content>
          )}
          <Tabs.Content value="groups" className="ctabs__content">
            <UserGroupsTab user={current} />
          </Tabs.Content>
          <Tabs.Content value="tenants" className="ctabs__content">
            <UserTenantsTab user={current} />
          </Tabs.Content>
        </Tabs.Root>
      </Card>
      {deleting && (
        <DeleteDialog
          title={t('admin.user.delete')}
          text={self ? t('admin.user.deleteSelf') : t('admin.user.deleteText', { id: current.id })}
          onClose={() => setDeleting(false)}
          onConfirm={async () => {
            const ok = await run(() => deleteUser(current.id), {
              success: t('admin.user.deleted', { id: current.id }),
              failure: t('admin.user.deleteFailed'),
            });
            if (ok) void navigate('/admin/users');
            return ok;
          }}
        />
      )}
    </div>
  );
}
