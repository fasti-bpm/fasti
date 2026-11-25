/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { isApiError, request, seg, type QueryValue } from './http';
import type { SortOrder, UserProfile } from './types';
import { toEngineDate } from '../lib/dates';

/**
 * Resources the administration pages read and change: the REST API of the
 * engine for users, groups, tenants and authorizations, and the setup
 * resource of the classic administration for the very first user. Nothing
 * here adds to the contract of the server.
 */
const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;
const setupApi = (): string => `/admin/setup/${seg(env.engine)}`;

export interface Paging {
  firstResult: number;
  maxResults: number;
}

export interface Page<T> {
  count: number;
  items: T[];
}

interface Count {
  count: number;
}

// --- what the user may do ----------------------------------------------------

/**
 * The actions the engine offers on a resource for the current user, read
 * from the links of an OPTIONS request: e.g. `create` is missing if the user
 * lacks the permission or the identity provider is read-only (LDAP).
 */
export type Access = ReadonlySet<string>;

export async function getAccess(path: string): Promise<Access> {
  try {
    const options = await request<{ links?: { rel: string }[] }>(`${engineApi()}${path}`, {
      method: 'OPTIONS',
    });
    return new Set((options.links ?? []).map((link) => link.rel));
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return new Set();
    }
    throw error;
  }
}

async function page<T>(
  path: string,
  query: Record<string, QueryValue>,
  paging: Paging,
  signal?: AbortSignal,
): Promise<Page<T>> {
  const [items, count] = await Promise.all([
    request<T[]>(path, { query: { ...query, ...paging }, signal }),
    request<Count>(`${path}/count`, { query, signal }),
  ]);
  return { items, count: count.count };
}

/** `abc` → `%abc%`, so that a search finds parts of a value. */
function like(term: string | undefined): string | undefined {
  const trimmed = term?.trim();
  return trimmed ? `%${trimmed}%` : undefined;
}

// --- users -------------------------------------------------------------------

export type User = UserProfile;

export type UserSortBy = 'userId' | 'firstName' | 'lastName' | 'email';

export interface UserQuery {
  search?: string;
  memberOfGroup?: string;
  memberOfTenant?: string;
  sortBy?: UserSortBy;
  sortOrder?: SortOrder;
}

/**
 * The engine has no "any field" search and no partial match on ids: the
 * term is taken as the exact id first, then looked for in the first name,
 * the last name and the e-mail address one after the other; the first field
 * that matches anything wins. A term with a space is read as first and last
 * name.
 */
export async function getUsers(query: UserQuery, paging: Paging, signal?: AbortSignal): Promise<Page<User>> {
  const path = `${engineApi()}/user`;
  const base: Record<string, QueryValue> = {
    memberOfGroup: query.memberOfGroup,
    memberOfTenant: query.memberOfTenant,
    sortBy: query.sortBy ?? 'userId',
    sortOrder: query.sortOrder ?? 'asc',
  };
  const term = query.search?.trim();
  if (!term) {
    return page<User>(path, base, paging, signal);
  }
  const words = term.split(/\s+/);
  const candidates: Record<string, QueryValue>[] =
    words.length > 1
      ? [
          { firstNameLike: like(words[0]), lastNameLike: like(words.slice(1).join(' ')) },
          { lastNameLike: like(term) },
          { firstNameLike: like(term) },
        ]
      : [{ id: term }, { firstNameLike: like(term) }, { lastNameLike: like(term) }, { emailLike: like(term) }];
  let result: Page<User> = { items: [], count: 0 };
  for (const candidate of candidates) {
    result = await page<User>(path, { ...base, ...candidate }, paging, signal);
    if (result.count > 0) {
      break;
    }
  }
  return result;
}

export function getUser(id: string): Promise<User> {
  return request<User>(`${engineApi()}/user/${seg(id)}/profile`);
}

export interface NewUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export function createUser(user: NewUser): Promise<void> {
  const { password, ...profile } = user;
  return request<void>(`${engineApi()}/user/create`, {
    method: 'POST',
    json: { profile, credentials: { password } },
  });
}

export function updateUser(id: string, profile: Omit<User, 'id'> & { id?: string }): Promise<void> {
  return request<void>(`${engineApi()}/user/${seg(id)}/profile`, {
    method: 'PUT',
    json: { ...profile, id },
  });
}

/** The password of the user who is logged in confirms the change. */
export function changePassword(id: string, password: string, authenticatedUserPassword: string): Promise<void> {
  return request<void>(`${engineApi()}/user/${seg(id)}/credentials`, {
    method: 'PUT',
    json: { password, authenticatedUserPassword },
  });
}

export function deleteUser(id: string): Promise<void> {
  return request<void>(`${engineApi()}/user/${seg(id)}`, { method: 'DELETE' });
}

/** Lifts the lock the engine sets after too many failed logins. */
export function unlockUser(id: string): Promise<void> {
  return request<void>(`${engineApi()}/user/${seg(id)}/unlock`, { method: 'POST' });
}

// --- groups ------------------------------------------------------------------

export interface Group {
  id: string;
  name: string | null;
  type: string | null;
}

export type GroupSortBy = 'id' | 'name' | 'type';

export interface GroupQuery {
  search?: string;
  member?: string;
  memberOfTenant?: string;
  sortBy?: GroupSortBy;
  sortOrder?: SortOrder;
}

export async function getGroups(query: GroupQuery, paging: Paging, signal?: AbortSignal): Promise<Page<Group>> {
  const path = `${engineApi()}/group`;
  const base: Record<string, QueryValue> = {
    member: query.member,
    memberOfTenant: query.memberOfTenant,
    sortBy: query.sortBy ?? 'id',
    sortOrder: query.sortOrder ?? 'asc',
  };
  const term = query.search?.trim();
  if (!term) {
    return page<Group>(path, base, paging, signal);
  }
  // the group query knows no "id like": an exact id first, then parts of the name
  const byId = await page<Group>(path, { ...base, id: term }, paging, signal);
  return byId.count > 0 ? byId : page<Group>(path, { ...base, nameLike: like(term) }, paging, signal);
}

export function getGroup(id: string): Promise<Group> {
  return request<Group>(`${engineApi()}/group/${seg(id)}`);
}

export function createGroup(group: Group): Promise<void> {
  return request<void>(`${engineApi()}/group/create`, { method: 'POST', json: group });
}

export function updateGroup(id: string, group: Omit<Group, 'id'>): Promise<void> {
  return request<void>(`${engineApi()}/group/${seg(id)}`, { method: 'PUT', json: { ...group, id } });
}

export function deleteGroup(id: string): Promise<void> {
  return request<void>(`${engineApi()}/group/${seg(id)}`, { method: 'DELETE' });
}

export function addGroupMember(groupId: string, userId: string): Promise<void> {
  return request<void>(`${engineApi()}/group/${seg(groupId)}/members/${seg(userId)}`, { method: 'PUT' });
}

export function removeGroupMember(groupId: string, userId: string): Promise<void> {
  return request<void>(`${engineApi()}/group/${seg(groupId)}/members/${seg(userId)}`, { method: 'DELETE' });
}

// --- tenants -----------------------------------------------------------------

export interface Tenant {
  id: string;
  name: string | null;
}

export type TenantSortBy = 'id' | 'name';

export interface TenantQuery {
  search?: string;
  userMember?: string;
  groupMember?: string;
  sortBy?: TenantSortBy;
  sortOrder?: SortOrder;
}

export async function getTenants(query: TenantQuery, paging: Paging, signal?: AbortSignal): Promise<Page<Tenant>> {
  const path = `${engineApi()}/tenant`;
  const base: Record<string, QueryValue> = {
    userMember: query.userMember,
    groupMember: query.groupMember,
    sortBy: query.sortBy ?? 'id',
    sortOrder: query.sortOrder ?? 'asc',
  };
  const term = query.search?.trim();
  if (!term) {
    return page<Tenant>(path, base, paging, signal);
  }
  // the tenant query knows no "id like"
  const byName = await page<Tenant>(path, { ...base, nameLike: like(term) }, paging, signal);
  if (byName.count > 0) {
    return byName;
  }
  return page<Tenant>(path, { ...base, id: term }, paging, signal);
}

export function getTenant(id: string): Promise<Tenant> {
  return request<Tenant>(`${engineApi()}/tenant/${seg(id)}`);
}

export function createTenant(tenant: Tenant): Promise<void> {
  return request<void>(`${engineApi()}/tenant/create`, { method: 'POST', json: tenant });
}

export function updateTenant(id: string, tenant: Omit<Tenant, 'id'>): Promise<void> {
  return request<void>(`${engineApi()}/tenant/${seg(id)}`, { method: 'PUT', json: { ...tenant, id } });
}

export function deleteTenant(id: string): Promise<void> {
  return request<void>(`${engineApi()}/tenant/${seg(id)}`, { method: 'DELETE' });
}

export type TenantMember = 'user' | 'group';

export function addTenantMember(tenantId: string, kind: TenantMember, memberId: string): Promise<void> {
  return request<void>(`${engineApi()}/tenant/${seg(tenantId)}/${kind}-members/${seg(memberId)}`, {
    method: 'PUT',
  });
}

export function removeTenantMember(tenantId: string, kind: TenantMember, memberId: string): Promise<void> {
  return request<void>(`${engineApi()}/tenant/${seg(tenantId)}/${kind}-members/${seg(memberId)}`, {
    method: 'DELETE',
  });
}

// --- authorizations ------------------------------------------------------------

/** 0: applies to every user, 1: grants, 2: revokes. */
export type AuthorizationType = 0 | 1 | 2;

export interface Authorization {
  id: string;
  type: AuthorizationType;
  permissions: string[];
  userId: string | null;
  groupId: string | null;
  resourceType: number;
  resourceId: string;
}

export type AuthorizationInput = Omit<Authorization, 'id'>;

export interface AuthorizationQuery {
  resourceType: number;
  userId?: string;
  groupId?: string;
  resourceId?: string;
}

export function getAuthorizations(
  query: AuthorizationQuery,
  paging: Paging,
  signal?: AbortSignal,
): Promise<Page<Authorization>> {
  return page<Authorization>(
    `${engineApi()}/authorization`,
    {
      resourceType: query.resourceType,
      userIdIn: query.userId?.trim() || undefined,
      groupIdIn: query.groupId?.trim() || undefined,
      resourceId: query.resourceId?.trim() || undefined,
      sortBy: 'resourceId',
      sortOrder: 'asc',
    },
    paging,
    signal,
  );
}

export function createAuthorization(authorization: AuthorizationInput): Promise<Authorization> {
  return request<Authorization>(`${engineApi()}/authorization/create`, { method: 'POST', json: authorization });
}

export function updateAuthorization(id: string, authorization: AuthorizationInput): Promise<void> {
  return request<void>(`${engineApi()}/authorization/${seg(id)}`, {
    method: 'PUT',
    json: { ...authorization, id },
  });
}

export function deleteAuthorization(id: string): Promise<void> {
  return request<void>(`${engineApi()}/authorization/${seg(id)}`, { method: 'DELETE' });
}

// --- system --------------------------------------------------------------------

export interface Diagnostics {
  installation?: string | null;
  product?: {
    name?: string;
    version?: string;
    edition?: string;
    internals?: {
      database?: { vendor?: string; version?: string } | null;
      'application-server'?: { vendor?: string; version?: string } | null;
      jdk?: { vendor?: string; version?: string } | null;
      webapps?: string[] | null;
      'camunda-integration'?: string[] | null;
      'data-collection-start-date'?: string | null;
    } | null;
  } | null;
}

/**
 * What the engine knows about its environment. It is only read here; the
 * engine of this distribution sends it nowhere. `null` if the user may not
 * read it.
 */
export async function getDiagnostics(): Promise<Diagnostics | null> {
  try {
    return await request<Diagnostics>(`${engineApi()}/telemetry/data`);
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return null;
    }
    throw error;
  }
}

// --- health ----------------------------------------------------------------------

/** How long a due job may wait before the job executor counts as behind. */
export const JOB_LAG_MS = 5 * 60_000;

/**
 * Signs of trouble across the whole engine, read from the REST API: each
 * count is `null` when it could not be read, e.g. without permission.
 */
export interface Health {
  /** milliseconds the engine took to answer the first read */
  responseMs: number;
  incidents: number | null;
  failedJobs: number | null;
  /** executable jobs whose due date passed more than JOB_LAG_MS ago */
  lateJobs: number | null;
  failedExternalTasks: number | null;
  failingBatches: number | null;
  users: number | null;
  groups: number | null;
  tenants: number | null;
  authorizations: number | null;
  deployments: number | null;
  latestDeployment: { id: string; name: string | null; deploymentTime: string | null } | null;
}

export async function getHealth(now: Date = new Date(), signal?: AbortSignal): Promise<Health> {
  const api = engineApi();
  const count = (path: string, query: Record<string, QueryValue> = {}) =>
    request<Count>(`${api}${path}`, { query, signal }).then(
      (result) => result.count,
      () => null,
    );
  const started = performance.now();
  // the engine is reachable at all: this one read may fail the whole check
  const incidents = await request<Count>(`${api}/incident/count`, { signal }).then((result) => result.count);
  const responseMs = Math.round(performance.now() - started);
  const late = toEngineDate(new Date(now.getTime() - JOB_LAG_MS));
  const [failedJobs, lateJobs, failedExternalTasks, batches, users, groups, tenants, authorizations, deployments, latest] =
    await Promise.all([
      count('/job/count', { withException: true, noRetriesLeft: true }),
      // timers by their due date; async continuations have none, so by the time they were created
      Promise.all([
        count('/job/count', { executable: true, active: true, timers: true, dueDates: `lt_${late}` }),
        count('/job/count', { executable: true, active: true, messages: true, createTimes: `lt_${late}` }),
      ]).then(([timers, messages]) => (timers === null || messages === null ? null : timers + messages)),
      count('/external-task/count', { noRetriesLeft: true }),
      request<{ failedJobs: number }[]>(`${api}/batch/statistics`, { signal }).catch(() => null),
      count('/user/count'),
      count('/group/count'),
      count('/tenant/count'),
      count('/authorization/count'),
      count('/deployment/count'),
      request<{ id: string; name: string | null; deploymentTime: string | null }[]>(`${api}/deployment`, {
        query: { sortBy: 'deploymentTime', sortOrder: 'desc', maxResults: 1 },
        signal,
      }).catch(() => null),
    ]);
  return {
    responseMs,
    incidents,
    failedJobs,
    lateJobs,
    failedExternalTasks,
    failingBatches: batches ? batches.filter((batch) => batch.failedJobs > 0).length : null,
    users,
    groups,
    tenants,
    authorizations,
    deployments,
    latestDeployment: latest?.[0] ?? null,
  };
}

// --- execution metrics -----------------------------------------------------------

/** The metrics the engine sums up for a period, as the classic system page shows them. */
export const EXECUTION_METRICS = [
  'root-process-instance-start',
  'activity-instance-start',
  'executed-decision-instances',
  'executed-decision-elements',
  'task-users',
] as const;

export type ExecutionMetric = (typeof EXECUTION_METRICS)[number];

/** The sum of each metric between two dates; `null` for a metric the engine refuses. */
export async function getExecutionMetrics(
  startDate: Date,
  endDate: Date,
  signal?: AbortSignal,
): Promise<Record<ExecutionMetric, number | null>> {
  const query = { startDate: toEngineDate(startDate), endDate: toEngineDate(endDate) };
  const sums = await Promise.all(
    EXECUTION_METRICS.map(async (name) => {
      try {
        const sum = await request<{ result: number }>(`${engineApi()}/metrics/${seg(name)}/sum`, {
          query,
          signal,
        });
        return sum.result;
      } catch (error) {
        if (isApiError(error, 'forbidden') || isApiError(error, 'notFound') || isApiError(error, 'client')) {
          return null;
        }
        throw error;
      }
    }),
  );
  return Object.fromEntries(EXECUTION_METRICS.map((name, index) => [name, sums[index] ?? null])) as Record<
    ExecutionMetric,
    number | null
  >;
}

// --- password policy -------------------------------------------------------------

export interface PolicyRule {
  /** e.g. `PASSWORD_POLICY_LENGTH` */
  placeholder: string;
  parameter: Record<string, string> | null;
  valid?: boolean;
}

/**
 * Checks a password against the policy of the engine. `null` if the engine
 * has no policy: then every password is accepted.
 */
export async function checkPassword(
  password: string,
  profile?: Partial<UserProfile>,
): Promise<{ valid: boolean; rules: PolicyRule[] } | null> {
  try {
    return await request<{ valid: boolean; rules: PolicyRule[] }>(`${engineApi()}/identity/password-policy`, {
      method: 'POST',
      json: { password, profile },
      silentUnauthorized: true,
    });
  } catch (error) {
    if (isApiError(error, 'notFound')) {
      return null;
    }
    throw error;
  }
}

// --- initial setup -------------------------------------------------------------

/**
 * Creates the first administrator; the server refuses once one exists. The
 * setup token is the one-time token the server prints to its log.
 */
export function createInitialUser(user: NewUser, setupToken: string): Promise<void> {
  const { password, ...profile } = user;
  return request<void>(`${setupApi()}/user/create`, {
    method: 'POST',
    json: { profile, credentials: { password } },
    headers: { 'X-Fasti-Setup-Token': setupToken.trim() },
    silentUnauthorized: true,
  });
}

/** Whether a user may do something, as the engine decides it, and the rules that take part. */
export interface AccessCheck {
  authorized: boolean;
  /** the authorizations of the user, of their groups and global ones for the resource */
  rules: (Authorization & { via: 'user' | 'group' | 'global' })[];
  groups: string[];
  /** some rules or groups could not be read: the list may miss some */
  incomplete: boolean;
}

/**
 * Asks the engine whether a user has a permission on a resource, and collects
 * the authorizations that can contribute: those of the user, of the groups of
 * the user and the global ones, for the resource id or for every id (`*`).
 * The answer of the engine is authoritative; the rules explain it.
 */
export async function checkAccess(
  check: { userId: string; resourceType: number; permission: string; resourceId: string },
  signal?: AbortSignal,
): Promise<AccessCheck> {
  const resourceId = check.resourceId.trim() || '*';
  const answer = await request<{ authorized: boolean }>(`${engineApi()}/authorization/check`, {
    query: {
      userId: check.userId,
      permissionName: check.permission,
      resourceName: String(check.resourceType),
      resourceType: check.resourceType,
      resourceId: resourceId === '*' ? undefined : resourceId,
    },
    signal,
  });
  // the explanation is best effort: what cannot be read is marked, the verdict stays
  let incomplete = false;
  const groupPage = await getGroups({ member: check.userId }, { firstResult: 0, maxResults: 500 }, signal).catch(() => {
    incomplete = true;
    return { count: 0, items: [] };
  });
  if (groupPage.count > groupPage.items.length) incomplete = true;
  const groups = groupPage.items.map((group) => group.id);
  const ids = resourceId === '*' ? ['*'] : ['*', resourceId];
  const read = async (query: Record<string, QueryValue>) => {
    const lists = await Promise.all(
      ids.map((id) =>
        request<Authorization[]>(`${engineApi()}/authorization`, {
          query: { resourceType: check.resourceType, resourceId: id, maxResults: 500, ...query },
          signal,
        }).catch((): Authorization[] => {
          incomplete = true;
          return [];
        }),
      ),
    );
    const seen = new Set<string>();
    return lists.flat().filter((rule) => !seen.has(rule.id) && !!seen.add(rule.id));
  };
  const [own, ofGroups, global] = await Promise.all([
    read({ userIdIn: check.userId }),
    groups.length ? read({ groupIdIn: groups.join(',') }) : Promise.resolve([]),
    read({ type: 0 }),
  ]);
  const relevant = (rule: Authorization) =>
    rule.permissions.some((permission) => permission === check.permission || permission === 'ALL' || permission === 'NONE');
  return {
    authorized: answer.authorized,
    incomplete,
    groups,
    rules: [
      ...own.filter(relevant).map((rule) => ({ ...rule, via: 'user' as const })),
      ...ofGroups.filter(relevant).map((rule) => ({ ...rule, via: 'group' as const })),
      ...global.filter(relevant).map((rule) => ({ ...rule, via: 'global' as const })),
    ],
  };
}
