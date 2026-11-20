/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useState, type ReactNode } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, SearchX, UserPlus } from 'lucide-react';
import { checkPassword, getAccess, type Access, type Page, type PolicyRule } from '../api/admin';
import { useI18n, type MessageKey, type Translate } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, Loading, Notice, Skeleton } from '../ui';
import { useToast } from '../ui/toast';
import { useDebounced } from '../ui/hooks';

/** Every query of the administration pages starts with this key. */
export const ADMIN = 'admin';

/** The actions the engine offers on a resource, see {@link getAccess}. */
export function useAccess(path: string): { access: Access; known: boolean } {
  const query = useQuery({
    queryKey: [ADMIN, 'access', path],
    queryFn: () => getAccess(path),
    staleTime: 60_000,
  });
  return { access: query.data ?? new Set(), known: query.isSuccess };
}

/**
 * Runs a change, reports the outcome and reloads what the pages show. The
 * result tells the caller whether the change went through.
 */
export function useAdminAction(): (
  call: () => Promise<unknown>,
  messages: { success: string; failure: string },
) => Promise<boolean> {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useCallback(
    async (call, messages) => {
      try {
        await call();
        toast.show({ title: messages.success });
        return true;
      } catch (error) {
        toast.error(error, messages.failure);
        return false;
      } finally {
        void queryClient.invalidateQueries({ queryKey: [ADMIN] });
        // memberships decide which tenants the switch of the header offers
        void queryClient.invalidateQueries({ queryKey: ['my-tenants'] });
      }
    },
    [queryClient, toast],
  );
}

/** Says why changes are not offered, if the identity provider is read-only. */
export function ReadOnlyNotice({ show }: { show: boolean }) {
  const { t } = useI18n();
  if (!show) {
    return null;
  }
  return <Notice title={t('admin.readOnly.title')}>{t('admin.readOnly.text')}</Notice>;
}

// --- password ----------------------------------------------------------------

const POLICY_MESSAGES: Record<string, MessageKey> = {
  PASSWORD_POLICY_LENGTH: 'admin.policy.length',
  PASSWORD_POLICY_LOWERCASE: 'admin.policy.lowercase',
  PASSWORD_POLICY_UPPERCASE: 'admin.policy.uppercase',
  PASSWORD_POLICY_DIGIT: 'admin.policy.digit',
  PASSWORD_POLICY_SPECIAL: 'admin.policy.special',
  PASSWORD_POLICY_USER_DATA: 'admin.policy.userData',
};

export function describeRule(rule: PolicyRule, t: Translate): string {
  const key = POLICY_MESSAGES[rule.placeholder];
  const value = rule.parameter ? Object.values(rule.parameter)[0] : undefined;
  const count = Number(value);
  return key ? t(key, { count: Number.isFinite(count) ? count : (value ?? '') }) : rule.placeholder;
}

/**
 * Checks a new password: both entries must match and the policy of the
 * engine, if there is one, must accept it. Returns the problems found.
 */
export async function passwordProblems(
  password: string,
  repeated: string,
  t: Translate,
  profile?: { id?: string; firstName?: string; lastName?: string; email?: string },
): Promise<string[]> {
  if (!password) {
    return [t('validation.required')];
  }
  if (password !== repeated) {
    return [t('admin.password.mismatch')];
  }
  const result = await checkPassword(password, profile).catch(() => null);
  if (!result || result.valid) {
    return [];
  }
  return result.rules.filter((rule) => rule.valid === false).map((rule) => describeRule(rule, t));
}

export function PasswordFields({
  password,
  repeated,
  onPassword,
  onRepeated,
  problems,
  label,
}: {
  password: string;
  repeated: string;
  onPassword: (value: string) => void;
  onRepeated: (value: string) => void;
  problems: string[];
  label?: string;
}) {
  const { t } = useI18n();
  return (
    <>
      <Field label={label ?? t('admin.user.password')} required error={problems[0] ?? null}>
        {(props) => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => onPassword(event.target.value)}
          />
        )}
      </Field>
      {problems.length > 1 && (
        <ul className="admin-problems">
          {problems.slice(1).map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      )}
      <Field label={t('admin.password.repeat')} required>
        {(props) => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="new-password"
            value={repeated}
            onChange={(event) => onRepeated(event.target.value)}
          />
        )}
      </Field>
    </>
  );
}

// --- adding members ------------------------------------------------------------

export interface Candidate {
  id: string;
  label: string;
}

/**
 * Picks users, groups or tenants to add to something: the user searches,
 * the list leaves out what is already there and every row has its own
 * button, so that several can be added one after the other.
 */
export function AddMembersDialog({
  title,
  searchLabel,
  search,
  exclude,
  accessPath,
  onAdd,
  onClose,
}: {
  title: string;
  searchLabel: string;
  search: (term: string, signal?: AbortSignal) => Promise<Page<Candidate>>;
  exclude: ReadonlySet<string>;
  /**
   * The resource whose OPTIONS tell whether this one candidate may be added,
   * e.g. the members of one group; without it every candidate is offered.
   */
  accessPath?: (id: string) => string;
  onAdd: (id: string) => Promise<boolean>;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const [term, setTerm] = useState('');
  const debounced = useDebounced(term.trim());
  const [added, setAdded] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState<string | null>(null);
  const candidates = useQuery({
    queryKey: [ADMIN, 'candidates', title, debounced],
    queryFn: ({ signal }) => search(debounced, signal),
    placeholderData: keepPreviousData,
  });
  const rows = candidates.data?.items.filter((item) => !exclude.has(item.id) || added.has(item.id));
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={title}>
      <div className="confirm">
        <Field label={searchLabel}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="search"
              autoFocus
              value={term}
              onChange={(event) => setTerm(event.target.value)}
            />
          )}
        </Field>
        {candidates.isError && !rows ? (
          <ErrorState error={candidates.error} onRetry={() => void candidates.refetch()} />
        ) : !rows ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={4} />
          </Loading>
        ) : rows.length === 0 ? (
          <EmptyState icon={SearchX} title={t('admin.members.noCandidates')} />
        ) : (
          <ul className="admin-candidates" aria-label={title}>
            {rows.map((row) => (
              <li key={row.id}>
                <span className="admin-candidates__label">
                  <span>{row.label}</span>
                  {row.label !== row.id && <span className="muted mono">{row.id}</span>}
                </span>
                {added.has(row.id) ? (
                  <span className="muted">{t('admin.members.added')}</span>
                ) : (
                  <AllowedFor path={accessPath?.(row.id)} action="create">
                    <Button
                      size="sm"
                      icon={Plus}
                      busy={busy === row.id}
                      aria-label={t('admin.members.addOne', { name: row.label })}
                      onClick={() => {
                        setBusy(row.id);
                        void onAdd(row.id).then((ok) => {
                          setBusy(null);
                          if (ok) setAdded((current) => new Set(current).add(row.id));
                        });
                      }}
                    >
                      {t('action.add')}
                    </Button>
                  </AllowedFor>
                )}
              </li>
            ))}
          </ul>
        )}
        {/* the search reads one page; say so, so that a missing candidate is searched for */}
        {candidates.data && candidates.data.count > candidates.data.items.length && (
          <p className="field__hint">
            {t('admin.members.truncated', { shown: candidates.data.items.length, count: candidates.data.count })}
          </p>
        )}
        <div className="confirm__actions">
          <Button variant="primary" onClick={onClose}>
            {t('admin.members.done')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

/**
 * Shows its content only if the engine offers the action on the resource,
 * e.g. `delete` on the members of one group; while that is being asked, or
 * without a resource, the content is shown and the engine decides.
 */
export function AllowedFor({
  path,
  action,
  children,
}: {
  path: string | undefined;
  action: string;
  children: ReactNode;
}) {
  const { t } = useI18n();
  const query = useQuery({
    queryKey: [ADMIN, 'access', path],
    queryFn: () => getAccess(path ?? ''),
    enabled: !!path,
    staleTime: 60_000,
  });
  if (path && query.isSuccess && !query.data.has(action)) {
    return (
      <span className="muted" title={t('admin.members.notAllowedHint')}>
        {t('admin.members.notAllowed')}
      </span>
    );
  }
  return <>{children}</>;
}

/** A button that opens {@link AddMembersDialog}; hidden if adding is not allowed. */
export function AddMembersButton({ allowed, label, onClick }: { allowed: boolean; label: string; onClick: () => void }) {
  if (!allowed) {
    return null;
  }
  return (
    <Button size="sm" icon={UserPlus} onClick={onClick}>
      {label}
    </Button>
  );
}
