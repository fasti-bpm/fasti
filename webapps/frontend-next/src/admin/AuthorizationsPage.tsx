/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useMemo, useState } from 'react';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { Ban, CircleCheck, Globe, Pencil, Plus, ShieldCheck, ShieldQuestion, Trash2, User, Users } from 'lucide-react';
import {
  checkAccess,
  createAuthorization,
  deleteAuthorization,
  getAuthorizations,
  updateAuthorization,
  type Authorization,
  type AuthorizationInput,
  type AuthorizationType,
} from '../api/admin';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, IconButton, NoMatch, cx } from '../ui';
import { IdentityInput } from '../tasklist/IdentityInput';
import { Checkbox, SearchInput } from '../ui/controls';
import { pageOf, useUrlSearch, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';
import { ADMIN, useAccess, useAdminAction } from './common';
import { RESOURCE_TYPES, permissionSummary, resourceType, type ResourceType } from './resources';
import { DeleteDialog } from './UsersPage';

function TypeBadge({ type }: { type: AuthorizationType }) {
  const { t } = useI18n();
  if (type === 0) {
    return (
      <span className="badge">
        <Globe aria-hidden size={12} />
        {t('admin.auth.type.global')}
      </span>
    );
  }
  return type === 1 ? (
    <span className="badge badge--success">
      <CircleCheck aria-hidden size={12} />
      {t('admin.auth.type.grant')}
    </span>
  ) : (
    <span className="badge badge--danger">
      <Ban aria-hidden size={12} />
      {t('admin.auth.type.revoke')}
    </span>
  );
}

function Permissions({ permissions }: { permissions: string[] }) {
  const { t } = useI18n();
  const summary = permissionSummary(permissions);
  if (summary === 'all') {
    return <strong>{t('admin.auth.all')}</strong>;
  }
  if (summary === 'none') {
    return <span className="muted">{t('admin.auth.none')}</span>;
  }
  return <span className="admin-permissions">{summary.join(', ')}</span>;
}

/** Edit and delete of one authorization, offered only if the engine allows them for the user. */
function RowActions({ id, onEdit, onDelete }: { id: string; onEdit: () => void; onDelete: () => void }) {
  const { t } = useI18n();
  const { access } = useAccess(`/authorization/${encodeURIComponent(id)}`);
  return (
    <span className="row-actions">
      {access.has('update') && <IconButton icon={Pencil} label={t('admin.auth.editOne')} onClick={onEdit} />}
      {access.has('delete') && <IconButton icon={Trash2} label={t('admin.auth.deleteOne')} onClick={onDelete} />}
    </span>
  );
}

/** Grants or revokes permissions on one resource type; also edits an existing authorization. */
function AuthorizationDialog({
  type,
  existing,
  onClose,
}: {
  type: ResourceType;
  existing?: Authorization;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useAdminAction();
  const listId = useId();
  const [kind, setKind] = useState<AuthorizationType>(existing?.type ?? 1);
  const [identity, setIdentity] = useState<'user' | 'group'>(existing?.groupId ? 'group' : 'user');
  const [identityId, setIdentityId] = useState(existing?.userId ?? existing?.groupId ?? '');
  const [resourceId, setResourceId] = useState(existing?.resourceId ?? '*');
  const initial = existing ? permissionSummary(existing.permissions) : 'all';
  const [all, setAll] = useState(initial === 'all');
  const [chosen, setChosen] = useState<Set<string>>(new Set(Array.isArray(initial) ? initial : []));
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  const identityError = kind !== 0 && !identityId.trim() ? t('validation.required') : null;
  const resourceError = !resourceId.trim() ? t('validation.required') : null;
  // an authorization with no permission ("NONE") is valid and is kept when nothing is picked
  const keepsNone = initial === 'none';
  const permissionsError = !all && chosen.size === 0 && !keepsNone ? t('admin.auth.noPermission') : null;

  return (
    <Dialog
      open
      wide
      onOpenChange={(open) => !open && onClose()}
      title={existing ? t('admin.auth.edit') : t('admin.auth.create')}
      description={t('admin.auth.dialogText', { type: t(type.label) })}
    >
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (identityError || resourceError || permissionsError) return;
          const input: AuthorizationInput = {
            type: kind,
            permissions: all
              ? ['ALL']
              : chosen.size === 0
                ? ['NONE']
                : type.permissions.filter((permission) => chosen.has(permission)),
            // a global authorization applies to every user
            userId: kind === 0 ? '*' : identity === 'user' ? identityId.trim() : null,
            groupId: kind !== 0 && identity === 'group' ? identityId.trim() : null,
            resourceType: type.id,
            resourceId: resourceId.trim(),
          };
          setBusy(true);
          void run(() => (existing ? updateAuthorization(existing.id, input) : createAuthorization(input)), {
            success: existing ? t('admin.auth.saved') : t('admin.auth.created'),
            failure: existing ? t('admin.auth.saveFailed') : t('admin.auth.createFailed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) onClose();
          });
        }}
      >
        {/* the engine keeps the type of an authorization for its lifetime */}
        <fieldset className="admin-choice" disabled={!!existing}>
          <legend className="field__label">
            {t('admin.auth.type')}
            {existing && <span className="option__hint">{t('admin.auth.typeFixed')}</span>}
          </legend>
          {([1, 2, 0] as const).map((value) => (
            <label key={value} className={cx('admin-choice__item', kind === value && 'is-on')}>
              <input type="radio" name="type" checked={kind === value} onChange={() => setKind(value)} />
              <span>
                <span className="option__label">
                  {t(value === 1 ? 'admin.auth.type.grant' : value === 2 ? 'admin.auth.type.revoke' : 'admin.auth.type.global')}
                </span>
                <span className="option__hint">
                  {t(value === 1 ? 'admin.auth.type.grantHint' : value === 2 ? 'admin.auth.type.revokeHint' : 'admin.auth.type.globalHint')}
                </span>
              </span>
            </label>
          ))}
        </fieldset>
        {kind !== 0 && (
          <div className="admin-identity">
            <fieldset className="admin-segment">
              <legend className="sr-only">{t('admin.auth.identity')}</legend>
              {(['user', 'group'] as const).map((value) => (
                <label key={value} className={cx('admin-segment__item', identity === value && 'is-on')}>
                  <input type="radio" name="identity" checked={identity === value} onChange={() => setIdentity(value)} />
                  {value === 'user' ? <User aria-hidden size={14} /> : <Users aria-hidden size={14} />}
                  {t(value === 'user' ? 'admin.auth.user' : 'admin.auth.group')}
                </label>
              ))}
            </fieldset>
            <Field
              label={identity === 'user' ? t('admin.auth.userId') : t('admin.auth.groupId')}
              required
              error={submitted ? identityError : null}
            >
              {(props) => (
                <input
                  {...props}
                  className="input"
                  value={identityId}
                  onChange={(event) => setIdentityId(event.target.value)}
                />
              )}
            </Field>
          </div>
        )}
        <Field label={t('admin.auth.resourceId')} required hint={t(type.idHint)} error={submitted ? resourceError : null}>
          {(props) => (
            <>
              <input
                {...props}
                className="input"
                list={type.suggestions ? listId : undefined}
                value={resourceId}
                onChange={(event) => setResourceId(event.target.value)}
              />
              {type.suggestions && (
                <datalist id={listId}>
                  {type.suggestions.map((suggestion) => (
                    <option key={suggestion} value={suggestion} />
                  ))}
                </datalist>
              )}
            </>
          )}
        </Field>
        <fieldset className="admin-permission-picker">
          <legend className="field__label">{t('admin.auth.permissions')}</legend>
          <Checkbox label={t('admin.auth.all')} hint={t('admin.auth.allHint')} checked={all} onChange={setAll} />
          {!all && (
            <>
              <div className="admin-permission-picker__tools">
                <Button size="sm" variant="ghost" onClick={() => setChosen(new Set(type.permissions))}>
                  {t('admin.auth.selectAll')}
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setChosen(new Set())}>
                  {t('admin.auth.selectNone')}
                </Button>
              </div>
              <div className="admin-permission-picker__grid">
                {type.permissions.map((permission) => (
                  <label key={permission} className="option">
                    <input
                      type="checkbox"
                      checked={chosen.has(permission)}
                      onChange={(event) => {
                        const next = new Set(chosen);
                        if (event.target.checked) next.add(permission);
                        else next.delete(permission);
                        setChosen(next);
                      }}
                    />
                    <span className="mono">{permission}</span>
                  </label>
                ))}
              </div>
            </>
          )}
          {submitted && permissionsError && (
            <p className="field__error" role="alert">
              {permissionsError}
            </p>
          )}
        </fieldset>
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" icon={ShieldCheck} busy={busy}>
            {existing ? t('action.save') : t('admin.auth.createConfirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** The authorizations of one resource type, with who they apply to and what they allow. */
/**
 * Answers "may this user do that?" with the decision of the engine and the
 * rules behind it, instead of leaving administrators to reason through
 * grants, revokes and groups by hand.
 */
function CheckAccessDialog({ initialType, onClose }: { initialType: ResourceType; onClose: () => void }) {
  const { t } = useI18n();
  const [userId, setUserId] = useState('');
  const [typeId, setTypeId] = useState(initialType.id);
  const type = resourceType(typeId);
  const [permission, setPermission] = useState(type.permissions[0] ?? 'READ');
  const [resourceId, setResourceId] = useState('*');
  const [asked, setAsked] = useState<{ userId: string; resourceType: number; permission: string; resourceId: string } | null>(null);
  // asked anew on every click, also for the same question: permissions may have changed meanwhile
  const result = useMutation({ mutationFn: (question: NonNullable<typeof asked>) => checkAccess(question) });
  const who = (rule: Authorization & { via: string }) =>
    rule.via === 'global' ? t('admin.auth.everyone') : rule.via === 'group' ? `${t('admin.check.group')} ${rule.groupId ?? ''}` : (rule.userId ?? '');
  return (
    <Dialog open wide onOpenChange={(open) => !open && onClose()} title={t('admin.check.title')} description={t('admin.check.text')}>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          if (!userId.trim()) return;
          const question = { userId: userId.trim(), resourceType: type.id, permission, resourceId };
          setAsked(question);
          result.mutate(question);
        }}
      >
        <Field label={t('admin.check.user')} required>
          {(props) => <IdentityInput {...props} kind="user" autoFocus value={userId} onChange={setUserId} />}
        </Field>
        <Field label={t('admin.auth.resourceTypes')}>
          {(props) => (
            <select
              {...props}
              className="input"
              value={type.id}
              onChange={(event) => {
                const next = resourceType(Number(event.target.value));
                setTypeId(next.id);
                setPermission(next.permissions[0] ?? 'READ');
              }}
            >
              {RESOURCE_TYPES.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {t(candidate.label)}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label={t('admin.check.permission')}>
          {(props) => (
            <select {...props} className="input" value={permission} onChange={(event) => setPermission(event.target.value)}>
              {type.permissions.map((candidate) => (
                <option key={candidate}>{candidate}</option>
              ))}
            </select>
          )}
        </Field>
        <Field label={t('admin.check.resource')} hint={t(type.idHint)}>
          {(props) => <input {...props} className="input" value={resourceId} onChange={(event) => setResourceId(event.target.value)} />}
        </Field>
        <div className="confirm__actions">
          <Button variant="primary" type="submit" busy={result.isPending} disabled={!userId.trim()}>
            {t('admin.check.run')}
          </Button>
        </div>
      </form>
      {result.isError && <ErrorState error={result.error} />}
      {result.data && asked && (
        <div className={cx('access-result', result.data.authorized ? 'access-result--yes' : 'access-result--no')} role="status">
          <p className="access-result__verdict">
            {result.data.authorized ? <CircleCheck aria-hidden size={18} /> : <Ban aria-hidden size={18} />}
            {t(result.data.authorized ? 'admin.check.allowed' : 'admin.check.denied', {
              user: asked.userId,
              permission: asked.permission,
              resource: asked.resourceId || '*',
            })}
          </p>
          {result.data.groups.length > 0 && (
            <p className="muted">{t('admin.check.groups', { groups: result.data.groups.join(', ') })}</p>
          )}
          {result.data.incomplete && <p className="muted">{t('admin.check.incomplete')}</p>}
          {result.data.rules.length === 0 ? (
            <p className="muted">{t('admin.check.noRules')}</p>
          ) : (
            <ul className="access-result__rules" aria-label={t('admin.check.rules')}>
              {result.data.rules.map((rule) => (
                <li key={rule.id}>
                  <TypeBadge type={rule.type} /> {who(rule)} · {rule.resourceId} · {rule.permissions.join(', ')}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Dialog>
  );
}

export function AuthorizationsPage() {
  const { t } = useI18n();
  const run = useAdminAction();
  const [params, setParams] = useUrlState();
  const type = resourceType(Number(params.get('resource') ?? '0'));
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);
  const [dialog, setDialog] = useState<{ existing?: Authorization } | null>(null);
  const [deleting, setDeleting] = useState<Authorization | null>(null);
  const [checking, setChecking] = useState(false);
  const { access } = useAccess('/authorization');


  // the engine filters by user or by group; a term is looked for in both
  const authorizations = useQuery({
    queryKey: [ADMIN, 'authorizations', type.id, term, page],
    queryFn: async ({ signal }) => {
      const paging = { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE };
      if (!term) {
        return getAuthorizations({ resourceType: type.id }, paging, signal);
      }
      // the engine refuses both at once: the rules of the user come first, then those of the group
      const first = page * PAGE_SIZE;
      const byUser = await getAuthorizations({ resourceType: type.id, userId: term }, paging, signal);
      const groupFirst = Math.max(0, first - byUser.count);
      const room = PAGE_SIZE - byUser.items.length;
      const byGroup = await getAuthorizations(
        { resourceType: type.id, groupId: term },
        { firstResult: groupFirst, maxResults: Math.max(room, 1) },
        signal,
      );
      return { count: byUser.count + byGroup.count, items: [...byUser.items, ...(room > 0 ? byGroup.items.slice(0, room) : [])] };
    },
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<Authorization>[]>(
    () => [
      { id: 'type', header: t('admin.auth.type'), cell: (row) => <TypeBadge type={row.type} /> },
      {
        id: 'identity',
        header: t('admin.auth.identity'),
        cell: (row) =>
          row.groupId ? (
            <span className="admin-identity-cell">
              <Users aria-hidden size={14} />
              <span className="sr-only">{t('admin.auth.group')}</span>
              {row.groupId}
            </span>
          ) : (
            <span className="admin-identity-cell">
              <User aria-hidden size={14} />
              <span className="sr-only">{t('admin.auth.user')}</span>
              {row.userId === '*' ? t('admin.auth.everyone') : row.userId}
            </span>
          ),
      },
      { id: 'permissions', header: t('admin.auth.permissions'), cell: (row) => <Permissions permissions={row.permissions} /> },
      { id: 'resource', header: t('admin.auth.resourceId'), secondary: true, cell: (row) => <span className="mono">{row.resourceId}</span> },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (row) => (
          <RowActions
            id={row.id}
            onEdit={() => setDialog({ existing: row })}
            onDelete={() => setDeleting(row)}
          />
        ),
      },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('admin.auth.title')}
        subtitle={authorizations.data ? t('admin.auth.count', { count: authorizations.data.count }) : undefined}
        actions={
          <>
            <Button icon={ShieldQuestion} onClick={() => setChecking(true)}>
              {t('admin.check.action')}
            </Button>
            {access.has('create') && (
              <Button variant="primary" icon={Plus} onClick={() => setDialog({})}>
                {t('admin.auth.create')}
              </Button>
            )}
          </>
        }
      />
      <div className="admin-split">
        <nav className="admin-resources" aria-label={t('admin.auth.resourceTypes')}>
          <label className="admin-resources__select">
            <span className="sr-only">{t('admin.auth.resourceTypes')}</span>
            <select
              className="input"
              value={type.id}
              onChange={(event) => setParams({ resource: event.target.value === '0' ? null : event.target.value, page: null })}
            >
              {RESOURCE_TYPES.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {t(candidate.label)}
                </option>
              ))}
            </select>
          </label>
          <ul className="admin-resources__list">
            {RESOURCE_TYPES.map((candidate) => (
              <li key={candidate.id}>
                <button
                  type="button"
                  className={cx('resource-list__item', candidate.id === type.id && 'is-selected')}
                  aria-current={candidate.id === type.id ? 'true' : undefined}
                  onClick={() => setParams({ resource: candidate.id === 0 ? null : String(candidate.id), page: null })}
                >
                  {t(candidate.label)}
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <Card flush title={t(type.label)}>
          <div className="toolbar">
            <SearchInput value={search} onChange={setSearch} label={t('admin.auth.search')} />
          </div>
          <DataTable
            label={t(type.label)}
            columns={columns}
            updating={authorizations.isPlaceholderData}
            rows={authorizations.data?.items}
            rowKey={(row) => row.id}
            error={authorizations.error}
            onRetry={() => void authorizations.refetch()}
            empty={
              term ? (
                <NoMatch search title={t('admin.auth.noMatch')} onClear={() => setSearch('')} />
              ) : (
                <EmptyState icon={ShieldCheck} title={t('admin.auth.empty')} />
              )
            }
          />
          <Pager
            page={page}
            count={authorizations.data?.count ?? 0}
            onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
          />
        </Card>
      </div>
      {checking && <CheckAccessDialog initialType={type} onClose={() => setChecking(false)} />}
      {dialog && <AuthorizationDialog type={type} existing={dialog.existing} onClose={() => setDialog(null)} />}
      {deleting && (
        <DeleteDialog
          title={t('admin.auth.deleteOne')}
          text={t('admin.auth.deleteText', {
            who: deleting.groupId ?? (deleting.userId === '*' ? t('admin.auth.everyone') : (deleting.userId ?? '')),
            resource: deleting.resourceId,
          })}
          onClose={() => setDeleting(null)}
          onConfirm={() =>
            run(() => deleteAuthorization(deleting.id), {
              success: t('admin.auth.deleted'),
              failure: t('admin.auth.deleteFailed'),
            })
          }
        />
      )}
    </div>
  );
}
