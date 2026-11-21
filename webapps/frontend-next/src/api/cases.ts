/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { tenantFilter } from '../lib/userPrefs';
import type { Page, Paging, Sorting } from './cockpit';
import { isApiError, request, seg, type QueryValue } from './http';
import type { Task } from './types';

/**
 * Cases (CMMN) as the REST API of the engine offers them: definitions, the
 * running instances with their plan items, and their history. Nothing here
 * adds to the contract of the server.
 */
const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

async function count(path: string, query?: Record<string, QueryValue>, signal?: AbortSignal): Promise<number> {
  return (await request<{ count: number }>(path, { query, signal })).count;
}

/** `null` if history is switched off or the user may not read it. */
async function optional<T>(call: Promise<T>): Promise<T | null> {
  try {
    return await call;
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return null;
    }
    throw error;
  }
}

/** The tenant of a version: its own tenant, or the versions without one. */
function tenantOf(tenantId: string | null): Record<string, QueryValue> {
  return tenantId ? { tenantIdIn: tenantId } : { withoutTenantId: true };
}

// --- definitions ------------------------------------------------------------

export interface CaseDefinition {
  id: string;
  key: string;
  name: string | null;
  version: number;
  category: string | null;
  resource: string | null;
  deploymentId: string | null;
  tenantId: string | null;
  historyTimeToLive: number | null;
}

export type CaseDefinitionSortBy = 'name' | 'key' | 'tenantId' | 'version';

/** Every case once, in its latest version; found by name or, without a name, by key. */
export async function getCaseDefinitions(options: {
  search?: string;
  sorting: Sorting<CaseDefinitionSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<CaseDefinition>> {
  const path = `${engineApi()}/case-definition`;
  const like = options.search ? `%${options.search}%` : undefined;
  const find = async (filter: Record<string, QueryValue>) => {
    const query = { latestVersion: true, ...filter, ...tenantFilter() };
    const [total, items] = await Promise.all([
      count(`${path}/count`, query, options.signal),
      request<CaseDefinition[]>(path, {
        query: { ...query, ...options.sorting, ...options.paging },
        signal: options.signal,
      }),
    ]);
    return { count: total, items };
  };
  const byName = await find({ nameLike: like });
  return like && byName.count === 0 ? find({ keyLike: like }) : byName;
}

export function getCaseDefinition(id: string): Promise<CaseDefinition> {
  return request<CaseDefinition>(`${engineApi()}/case-definition/${seg(id)}`);
}

/** Every version of a case, the newest first. */
export function getCaseDefinitionVersions(key: string, tenantId: string | null): Promise<CaseDefinition[]> {
  return request<CaseDefinition[]>(`${engineApi()}/case-definition`, {
    query: { key, ...tenantOf(tenantId), sortBy: 'version', sortOrder: 'desc' },
  });
}

export async function getCaseXml(id: string): Promise<string> {
  const result = await request<{ cmmnXml: string }>(`${engineApi()}/case-definition/${seg(id)}/xml`);
  return result.cmmnXml;
}

/** The case definitions of a deployment, to link its resources to them. */
export function getDeploymentCaseDefinitions(deploymentId: string): Promise<CaseDefinition[]> {
  return request<CaseDefinition[]>(`${engineApi()}/case-definition`, { query: { deploymentId } });
}

/** How many case definitions the user may see, counted once per case. */
export function countCaseDefinitions(signal?: AbortSignal): Promise<number> {
  return count(`${engineApi()}/case-definition/count`, { latestVersion: true, ...tenantFilter() }, signal);
}

/** Running case instances of all versions of a case. */
export function countCaseInstances(key: string, tenantId: string | null, signal?: AbortSignal): Promise<number> {
  return count(`${engineApi()}/case-instance/count`, { caseDefinitionKey: key, ...tenantOf(tenantId) }, signal);
}

// --- instances --------------------------------------------------------------

export interface CaseInstance {
  id: string;
  caseDefinitionId: string;
  businessKey: string | null;
  tenantId: string | null;
  active: boolean;
  completed: boolean;
  terminated: boolean;
}

/** The state of a case instance: running ones are active, completed or terminated until they are closed. */
export type CaseInstanceState = 'active' | 'completed' | 'terminated' | 'closed';

export function caseInstanceState(instance: Pick<CaseInstance, 'active' | 'completed' | 'terminated'>): CaseInstanceState {
  return instance.completed ? 'completed' : instance.terminated ? 'terminated' : 'active';
}

export type CaseInstanceSortBy = 'caseInstanceId' | 'tenantId';

/** Case instances that are not closed yet, of one version. */
export async function getCaseInstances(options: {
  caseDefinitionId: string;
  businessKey?: string;
  sorting?: Sorting<CaseInstanceSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<CaseInstance>> {
  const path = `${engineApi()}/case-instance`;
  const query = {
    caseDefinitionId: options.caseDefinitionId,
    businessKey: options.businessKey || undefined,
    ...tenantFilter(),
  };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<CaseInstance[]>(path, {
      query: { ...query, ...(options.sorting ?? {}), ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

export function getCaseInstance(id: string, signal?: AbortSignal): Promise<CaseInstance> {
  return request<CaseInstance>(`${engineApi()}/case-instance/${seg(id)}`, { signal });
}

/** Creates a case instance of a version; it starts at once. */
export function createCaseInstance(definitionId: string, businessKey?: string): Promise<CaseInstance> {
  return request<CaseInstance>(`${engineApi()}/case-definition/${seg(definitionId)}/create`, {
    method: 'POST',
    json: { businessKey: businessKey || undefined, variables: {} },
  });
}

export type CaseInstanceAction = 'complete' | 'terminate' | 'close';

/** Completes or terminates a running case instance, or closes a completed or terminated one. */
export function runCaseInstanceAction(id: string, action: CaseInstanceAction): Promise<void> {
  return request<void>(`${engineApi()}/case-instance/${seg(id)}/${action}`, { method: 'POST', json: {} });
}

/** What may be done with a case instance in its state. */
export function caseInstanceActions(state: CaseInstanceState): CaseInstanceAction[] {
  switch (state) {
    case 'active':
      return ['complete', 'terminate'];
    case 'completed':
    case 'terminated':
      return ['close'];
    case 'closed':
      return [];
  }
}

// --- plan items (case executions) ------------------------------------------

export interface CaseExecution {
  id: string;
  caseInstanceId: string;
  caseDefinitionId: string;
  /** the id of the plan item in the model */
  activityId: string;
  activityName: string | null;
  /** e.g. humanTask, stage, milestone, casePlanModel */
  activityType: string | null;
  activityDescription: string | null;
  parentId: string | null;
  tenantId: string | null;
  required: boolean;
  enabled: boolean;
  active: boolean;
  disabled: boolean;
}

/** The plan items of a case instance that are not finished, the instance itself included. */
export function getCaseExecutions(caseInstanceId: string, signal?: AbortSignal): Promise<CaseExecution[]> {
  return request<CaseExecution[]>(`${engineApi()}/case-execution`, {
    query: { caseInstanceId, sortBy: 'caseExecutionId', sortOrder: 'asc' },
    signal,
  });
}

/** At most this many plan items are read for the numbers on the diagram of a version. */
export const EXECUTION_LIMIT = 2000;

/** Plan items of all running instances of a version, up to {@link EXECUTION_LIMIT}. */
export async function getDefinitionExecutions(
  caseDefinitionId: string,
  signal?: AbortSignal,
): Promise<{ items: CaseExecution[]; truncated: boolean }> {
  const items = await request<CaseExecution[]>(`${engineApi()}/case-execution`, {
    query: { caseDefinitionId, firstResult: 0, maxResults: EXECUTION_LIMIT + 1 },
    signal,
  });
  return { items: items.slice(0, EXECUTION_LIMIT), truncated: items.length > EXECUTION_LIMIT };
}

/**
 * The state of a plan item that is not finished. The engine reports enabled,
 * disabled and active; a plan item that is none of them waits for its entry
 * criteria: it is available.
 */
export type ExecutionState = 'active' | 'enabled' | 'disabled' | 'available';

export function executionState(execution: Pick<CaseExecution, 'active' | 'enabled' | 'disabled'>): ExecutionState {
  if (execution.active) return 'active';
  if (execution.enabled) return 'enabled';
  if (execution.disabled) return 'disabled';
  return 'available';
}

export type ExecutionAction = 'manual-start' | 'complete' | 'disable' | 'reenable' | 'terminate';

/** Milestones and event listeners are never started or completed by hand. */
const OCCURRING = new Set(['milestone', 'eventListener', 'timerEventListener', 'userEventListener']);

/**
 * What may be done by hand with a plan item, by the transitions the engine
 * allows: an enabled item is started or disabled, a disabled one enabled again,
 * an active one completed or terminated. A milestone that waits can only be
 * terminated. The case instance itself is handled on its own.
 */
export function executionActions(execution: Pick<CaseExecution, 'active' | 'enabled' | 'disabled' | 'activityType'>): ExecutionAction[] {
  const type = execution.activityType ?? '';
  if (type === 'casePlanModel') {
    return [];
  }
  const state = executionState(execution);
  if (OCCURRING.has(type)) {
    return state === 'available' ? ['terminate'] : [];
  }
  switch (state) {
    case 'enabled':
      return ['manual-start', 'disable'];
    case 'disabled':
      return ['reenable'];
    case 'active':
      return ['complete', 'terminate'];
    default:
      return [];
  }
}

export function runExecutionAction(id: string, action: ExecutionAction): Promise<void> {
  return request<void>(`${engineApi()}/case-execution/${seg(id)}/${action}`, {
    method: 'POST',
    json: { variables: {} },
  });
}

/** Open human tasks of a case instance. */
export function getCaseTasks(caseInstanceId: string, signal?: AbortSignal): Promise<Task[]> {
  return request<Task[]>(`${engineApi()}/task`, {
    query: { caseInstanceId, sortBy: 'created', sortOrder: 'asc', maxResults: 200 },
    signal,
  });
}

// --- history ----------------------------------------------------------------

export interface HistoricCaseInstance {
  id: string;
  businessKey: string | null;
  caseDefinitionId: string;
  caseDefinitionKey: string | null;
  caseDefinitionName: string | null;
  createTime: string | null;
  closeTime: string | null;
  durationInMillis: number | null;
  createUserId: string | null;
  superCaseInstanceId: string | null;
  superProcessInstanceId: string | null;
  tenantId: string | null;
  active: boolean;
  completed: boolean;
  terminated: boolean;
  closed: boolean;
}

export function historicCaseState(instance: Pick<HistoricCaseInstance, 'active' | 'completed' | 'terminated' | 'closed'>): CaseInstanceState {
  if (instance.closed) return 'closed';
  if (instance.completed) return 'completed';
  if (instance.terminated) return 'terminated';
  return 'active';
}

/** The history of a case instance; `null` if history is off, not readable or the instance unknown. */
export function getHistoricCaseInstance(id: string, signal?: AbortSignal): Promise<HistoricCaseInstance | null> {
  return optional(request<HistoricCaseInstance>(`${engineApi()}/history/case-instance/${seg(id)}`, { signal }));
}

export type CaseHistoryFilter = 'all' | 'open' | 'closed';
export type CaseHistorySortBy = 'createTime' | 'closeTime' | 'duration' | 'businessKey';

/** Case instances from the history; `null` if history is off or not readable. */
export function getHistoricCaseInstances(options: {
  filter: CaseHistoryFilter;
  search?: string;
  caseDefinitionId?: string;
  sorting: Sorting<CaseHistorySortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<HistoricCaseInstance> | null> {
  const path = `${engineApi()}/history/case-instance`;
  const query = {
    caseDefinitionId: options.caseDefinitionId,
    caseInstanceBusinessKeyLike: options.search ? `%${options.search}%` : undefined,
    closed: options.filter === 'closed' ? true : undefined,
    notClosed: options.filter === 'open' ? true : undefined,
    ...tenantFilter(),
  };
  return optional(
    Promise.all([
      count(`${path}/count`, query, options.signal),
      request<HistoricCaseInstance[]>(path, {
        query: { ...query, ...options.sorting, ...options.paging },
        signal: options.signal,
      }),
    ]).then(([total, items]) => ({ count: total, items })),
  );
}

/** Business keys of case instances by id, e.g. to name the cases of tasks; empty without history. */
export async function getCaseBusinessKeys(ids: string[], signal?: AbortSignal): Promise<Map<string, string | null>> {
  if (ids.length === 0) return new Map();
  const found = await optional(
    request<{ id: string; businessKey: string | null }[]>(`${engineApi()}/history/case-instance`, {
      query: { caseInstanceIds: ids.join(','), maxResults: ids.length },
      signal,
    }),
  );
  return new Map((found ?? []).map((instance) => [instance.id, instance.businessKey]));
}

export interface HistoricCaseActivity {
  id: string;
  parentCaseActivityInstanceId: string | null;
  caseActivityId: string;
  caseActivityName: string | null;
  caseActivityType: string | null;
  caseExecutionId: string;
  taskId: string | null;
  calledProcessInstanceId: string | null;
  calledCaseInstanceId: string | null;
  createTime: string | null;
  endTime: string | null;
  durationInMillis: number | null;
  required: boolean;
  available: boolean;
  enabled: boolean;
  disabled: boolean;
  active: boolean;
  completed: boolean;
  terminated: boolean;
}

export type HistoricActivityState = 'available' | 'enabled' | 'disabled' | 'active' | 'completed' | 'terminated';

export function historicActivityState(activity: HistoricCaseActivity): HistoricActivityState {
  if (activity.completed) return 'completed';
  if (activity.terminated) return 'terminated';
  if (activity.active) return 'active';
  if (activity.enabled) return 'enabled';
  if (activity.disabled) return 'disabled';
  return 'available';
}

/** At most this many plan items of one case instance are read from the history. */
export const ACTIVITY_LIMIT = 500;

/** What happened to the plan items of a case instance, oldest first; `null` without history. */
export function getCaseActivityHistory(caseInstanceId: string, signal?: AbortSignal): Promise<HistoricCaseActivity[] | null> {
  return optional(
    request<HistoricCaseActivity[]>(`${engineApi()}/history/case-activity-instance`, {
      query: { caseInstanceId, sortBy: 'createTime', sortOrder: 'asc', maxResults: ACTIVITY_LIMIT },
      signal,
    }),
  );
}

export interface HistoricCaseVariable {
  id: string;
  name: string;
  type: string;
  value: unknown;
  valueInfo?: Record<string, unknown> | null;
  caseExecutionId: string | null;
  errorMessage: string | null;
}

/** The variables of a closed case instance, as the history kept them; `null` without history. */
export function getHistoricCaseVariables(caseInstanceId: string, signal?: AbortSignal): Promise<HistoricCaseVariable[] | null> {
  return optional(
    request<HistoricCaseVariable[]>(`${engineApi()}/history/variable-instance`, {
      query: { caseInstanceId, deserializeValues: false, sortBy: 'variableName', sortOrder: 'asc', maxResults: 500 },
      signal,
    }),
  );
}
