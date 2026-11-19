/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { isApiError, request, seg, type QueryValue } from './http';
import type { Batch, InstanceQuery, Page, Paging } from './cockpit';
import type { SortOrder, VariableMap } from './types';
import { tenantFilter, tenantFilterBody } from '../lib/userPrefs';
import { mapLimited } from './workload';

/**
 * Operations on many instances and the history of the engine. Everything here
 * uses the public REST API of the engine; the server is not extended.
 */
const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

interface Count {
  count: number;
}

async function paged<T>(
  path: string,
  query: Record<string, QueryValue>,
  paging: Paging,
  signal?: AbortSignal,
): Promise<Page<T>> {
  const [total, items] = await Promise.all([
    request<Count>(`${path}/count`, { query, signal }),
    request<T[]>(path, { query: { ...query, ...paging }, signal }),
  ]);
  return { count: total.count, items };
}

/** `null` if history is switched off or the user may not read it. */
export async function orNull<T>(call: Promise<T>): Promise<T | null> {
  try {
    return await call;
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return null;
    }
    throw error;
  }
}

/** Which instances a batch works on: listed ones or every one a query finds. */
export type InstanceSelection =
  | { kind: 'ids'; ids: string[] }
  | { kind: 'query'; query: InstanceQuery; count: number };

export function selectionCount(selection: InstanceSelection): number {
  return selection.kind === 'ids' ? selection.ids.length : selection.count;
}

/** The query of the runtime API for a query of the instance list. */
export function runtimeQuery(query: InstanceQuery): Record<string, unknown> {
  return {
    ...tenantFilterBody(),
    processDefinitionId: query.processDefinitionId,
    businessKey: query.businessKey || undefined,
    activityIdIn: query.activityIdIn?.length ? query.activityIdIn : undefined,
    withIncident: query.withIncident || undefined,
    variables: query.variables?.length ? query.variables : undefined,
    orQueries: query.orQueries?.length ? query.orQueries : undefined,
  };
}

function selectionBody(selection: InstanceSelection): Record<string, unknown> {
  return selection.kind === 'ids'
    ? { processInstanceIds: selection.ids }
    : { processInstanceQuery: runtimeQuery(selection.query) };
}

/** Counts with the same runtime query a batch uses, so the count matches what the batch changes. */
export async function countInstancesOf(query: InstanceQuery): Promise<number> {
  return (
    await request<Count>(`${engineApi()}/process-instance/count`, { method: 'POST', json: runtimeQuery(query) })
  ).count;
}

// --- modification -----------------------------------------------------------

export type ModificationType = 'startBeforeActivity' | 'startAfterActivity' | 'cancel';

export interface ModificationInstruction {
  type: ModificationType;
  activityId: string;
  /** cancels exactly this activity instance */
  activityInstanceId?: string;
  /** cancels exactly this transition instance (a token waiting at an asynchronous continuation) */
  transitionInstanceId?: string;
  /** variables set in the scope of the started activity */
  variables?: VariableMap;
}

export interface ModificationOptions {
  skipCustomListeners: boolean;
  skipIoMappings: boolean;
  annotation?: string;
}

function variablesWithScope(variables: VariableMap | undefined) {
  if (!variables || Object.keys(variables).length === 0) {
    return undefined;
  }
  return Object.fromEntries(
    Object.entries(variables).map(([name, value]) => [name, { ...value, local: false }]),
  );
}

/** The body of a modification of one instance; exported for tests. */
export function modificationBody(instructions: ModificationInstruction[], options: ModificationOptions) {
  return {
    skipCustomListeners: options.skipCustomListeners,
    skipIoMappings: options.skipIoMappings,
    annotation: options.annotation || undefined,
    instructions: instructions.map((instruction) =>
      instruction.type === 'cancel'
        ? instruction.transitionInstanceId
          ? { type: 'cancel', transitionInstanceId: instruction.transitionInstanceId }
          : instruction.activityInstanceId
            ? { type: 'cancel', activityInstanceId: instruction.activityInstanceId }
            : { type: 'cancel', activityId: instruction.activityId }
        : {
            type: instruction.type,
            activityId: instruction.activityId,
            variables: variablesWithScope(instruction.variables),
          },
    ),
  };
}

export function modifyInstance(
  processInstanceId: string,
  instructions: ModificationInstruction[],
  options: ModificationOptions,
): Promise<void> {
  return request<void>(`${engineApi()}/process-instance/${seg(processInstanceId)}/modification`, {
    method: 'POST',
    json: modificationBody(instructions, options),
  });
}

/** Modifies many instances of one definition in a batch; variables are not supported there. */
export function modifyInstancesAsync(
  processDefinitionId: string,
  selection: InstanceSelection,
  instructions: ModificationInstruction[],
  options: ModificationOptions,
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/modification/executeAsync`, {
    method: 'POST',
    json: {
      processDefinitionId,
      ...selectionBody(selection),
      skipCustomListeners: options.skipCustomListeners,
      skipIoMappings: options.skipIoMappings,
      annotation: options.annotation || undefined,
      instructions: instructions.map((instruction) =>
        instruction.type === 'cancel'
          ? { type: 'cancel', activityId: instruction.activityId, cancelCurrentActiveActivityInstances: true }
          : { type: instruction.type, activityId: instruction.activityId },
      ),
    },
  });
}

// --- migration --------------------------------------------------------------

export interface MigrationInstruction {
  sourceActivityIds: string[];
  targetActivityIds: string[];
  updateEventTrigger?: boolean;
}

export interface MigrationPlan {
  sourceProcessDefinitionId: string;
  targetProcessDefinitionId: string;
  instructions: MigrationInstruction[];
  variables?: VariableMap;
}

export function generateMigrationPlan(
  sourceProcessDefinitionId: string,
  targetProcessDefinitionId: string,
  updateEventTriggers: boolean,
): Promise<MigrationPlan> {
  return request<MigrationPlan>(`${engineApi()}/migration/generate`, {
    method: 'POST',
    json: { sourceProcessDefinitionId, targetProcessDefinitionId, updateEventTriggers },
  });
}

export interface MigrationValidation {
  instructionReports: { instruction: MigrationInstruction; failures: string[] }[];
  variableReports?: Record<string, { failures: string[] }> | null;
}

export function validateMigrationPlan(plan: MigrationPlan): Promise<MigrationValidation> {
  return request<MigrationValidation>(`${engineApi()}/migration/validate`, {
    method: 'POST',
    json: plan,
  });
}

export interface MigrationOptions {
  skipCustomListeners: boolean;
  skipIoMappings: boolean;
}

export function executeMigration(
  plan: MigrationPlan,
  selection: InstanceSelection,
  options: MigrationOptions,
): Promise<void> {
  return request<void>(`${engineApi()}/migration/execute`, {
    method: 'POST',
    json: { migrationPlan: plan, ...selectionBody(selection), ...options },
  });
}

export function executeMigrationAsync(
  plan: MigrationPlan,
  selection: InstanceSelection,
  options: MigrationOptions,
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/migration/executeAsync`, {
    method: 'POST',
    json: { migrationPlan: plan, ...selectionBody(selection), ...options },
  });
}

// --- history of process instances --------------------------------------------

export type HistoricState = 'ACTIVE' | 'SUSPENDED' | 'COMPLETED' | 'EXTERNALLY_TERMINATED' | 'INTERNALLY_TERMINATED';

export interface HistoricInstance {
  id: string;
  businessKey: string | null;
  processDefinitionId: string;
  processDefinitionKey: string;
  processDefinitionName: string | null;
  processDefinitionVersion: number | null;
  startTime: string | null;
  endTime: string | null;
  durationInMillis: number | null;
  startUserId: string | null;
  deleteReason: string | null;
  superProcessInstanceId: string | null;
  removalTime: string | null;
  tenantId: string | null;
  state: HistoricState;
}

/** What the history list filters by. */
export interface HistoricQuery {
  processDefinitionId?: string;
  processDefinitionKey?: string;
  /** `finished` covers completed and canceled ones; `canceled` is externally and internally terminated */
  state?: 'running' | 'finished' | 'completed' | 'canceled';
  withIncidents?: boolean;
  /** only instances with an incident that is still open */
  openIncidents?: boolean;
  businessKeyLike?: string;
  startedAfter?: string;
  startedBefore?: string;
  finishedAfter?: string;
  finishedBefore?: string;
  variables?: { name: string; operator: string; value: unknown }[];
  /** variable conditions joined with OR */
  orVariables?: { name: string; operator: string; value: unknown }[];
}

function orGroups(groups: (Record<string, unknown> | null)[]): Record<string, unknown>[] | undefined {
  const present = groups.filter((group): group is Record<string, unknown> => group !== null);
  return present.length ? present : undefined;
}

/** The body of the history query of the engine for a filter of the list; exported for tests. */
export function historicQueryBody(query: HistoricQuery): Record<string, unknown> {
  const state = query.state;
  return {
    ...tenantFilterBody(),
    processDefinitionId: query.processDefinitionId || undefined,
    processDefinitionKey: query.processDefinitionKey || undefined,
    unfinished: state === 'running' || undefined,
    finished: state === 'finished' || undefined,
    completed: state === 'completed' || undefined,
    // canceled by a user or the API (externally) or by the process itself, e.g. a terminate end event
    // (internally); within an "or" query the engine accepts both states at once
    // each nested query is one OR group; the groups and the other criteria are joined with AND
    orQueries: orGroups([
      state === 'canceled' ? { externallyTerminated: true, internallyTerminated: true } : null,
      query.orVariables?.length ? { variables: query.orVariables } : null,
    ]),
    withIncidents: query.withIncidents || query.openIncidents || undefined,
    incidentStatus: query.openIncidents ? 'open' : undefined,
    processInstanceBusinessKeyLike: query.businessKeyLike ? `%${query.businessKeyLike}%` : undefined,
    startedAfter: query.startedAfter || undefined,
    startedBefore: query.startedBefore || undefined,
    finishedAfter: query.finishedAfter || undefined,
    finishedBefore: query.finishedBefore || undefined,
    variables: query.variables?.length ? query.variables : undefined,
  };
}

export async function getHistoricInstances(options: {
  query: HistoricQuery;
  sortOrder: SortOrder;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<HistoricInstance>> {
  const path = `${engineApi()}/history/process-instance`;
  const body = historicQueryBody(options.query);
  const [total, items] = await Promise.all([
    request<Count>(`${path}/count`, { method: 'POST', json: body, signal: options.signal }),
    request<HistoricInstance[]>(path, {
      method: 'POST',
      query: { ...options.paging },
      json: { ...body, sorting: [{ sortBy: 'startTime', sortOrder: options.sortOrder }] },
      signal: options.signal,
    }),
  ]);
  return { count: total.count, items };
}

export function getHistoricInstance(id: string): Promise<HistoricInstance> {
  return request<HistoricInstance>(`${engineApi()}/history/process-instance/${seg(id)}`);
}

export interface HistoricActivity {
  id: string;
  executionId?: string | null;
  activityId: string;
  activityName: string | null;
  activityType: string;
  startTime: string;
  endTime: string | null;
  durationInMillis: number | null;
  assignee: string | null;
  canceled: boolean;
  completeScope: boolean;
  calledProcessInstanceId: string | null;
}

export function getHistoricActivities(processInstanceId: string, signal?: AbortSignal): Promise<HistoricActivity[]> {
  return request<HistoricActivity[]>(`${engineApi()}/history/activity-instance`, {
    query: { processInstanceId, sortBy: 'startTime', sortOrder: 'asc', maxResults: 2000 },
    signal,
  });
}

export interface HistoricVariable {
  id: string;
  name: string;
  type: string;
  value: unknown;
  valueInfo?: Record<string, unknown> | null;
  activityInstanceId: string | null;
  taskId?: string | null;
  createTime: string | null;
  state: string | null;
  errorMessage: string | null;
}

export function getHistoricVariables(processInstanceId: string, signal?: AbortSignal): Promise<HistoricVariable[]> {
  return request<HistoricVariable[]>(`${engineApi()}/history/variable-instance`, {
    query: {
      processInstanceId,
      deserializeValues: false,
      sortBy: 'variableName',
      sortOrder: 'asc',
      maxResults: 1000,
    },
    signal,
  });
}

export interface VariableUpdate {
  id: string;
  time: string;
  variableName: string;
  variableType: string | null;
  value: unknown;
  revision: number | null;
  userOperationId: string | null;
  errorMessage: string | null;
}

/** Every value a variable had, oldest first. */
export function getVariableUpdates(variableInstanceId: string): Promise<VariableUpdate[]> {
  return request<VariableUpdate[]>(`${engineApi()}/history/detail`, {
    query: {
      variableInstanceId,
      variableUpdates: true,
      deserializeValues: false,
      sortBy: 'time',
      sortOrder: 'asc',
      maxResults: 500,
    },
  });
}

export interface HistoricIncident {
  id: string;
  executionId?: string | null;
  incidentType: string;
  incidentMessage: string | null;
  activityId: string | null;
  createTime: string;
  endTime: string | null;
  open: boolean;
  deleted: boolean;
  resolved: boolean;
}

export function getHistoricIncidents(processInstanceId: string): Promise<HistoricIncident[]> {
  return request<HistoricIncident[]>(`${engineApi()}/history/incident`, {
    query: { processInstanceId, sortBy: 'createTime', sortOrder: 'desc', maxResults: 500 },
  });
}

export interface JobLogEntry {
  id: string;
  timestamp: string;
  jobId: string;
  activityId: string | null;
  jobRetries: number | null;
  jobExceptionMessage: string | null;
  creationLog: boolean;
  failureLog: boolean;
  successLog: boolean;
  deletionLog: boolean;
}

export function getJobLog(processInstanceId: string): Promise<JobLogEntry[]> {
  return request<JobLogEntry[]>(`${engineApi()}/history/job-log`, {
    query: { processInstanceId, sortBy: 'timestamp', sortOrder: 'desc', maxResults: 500 },
  });
}

// --- user operation log ------------------------------------------------------

export interface OperationLogEntry {
  id: string;
  operationId: string;
  operationType: string;
  entityType: string;
  category: string | null;
  property: string | null;
  orgValue: string | null;
  newValue: string | null;
  userId: string | null;
  timestamp: string;
  processInstanceId: string | null;
  processDefinitionKey: string | null;
  /** the task an operation on a task was about */
  taskId?: string | null;
  annotation: string | null;
}

export interface OperationLogQuery {
  processInstanceId?: string;
  userId?: string;
  operationType?: string;
  entityType?: string;
  category?: string;
  afterTimestamp?: string;
  beforeTimestamp?: string;
}

export function getOperationLog(options: {
  query: OperationLogQuery;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<OperationLogEntry>> {
  const query = Object.fromEntries(
    Object.entries(options.query).filter(([, value]) => value),
  ) as Record<string, QueryValue>;
  return paged<OperationLogEntry>(
    `${engineApi()}/history/user-operation`,
    { ...query, sortBy: 'timestamp', sortOrder: 'desc' },
    options.paging,
    options.signal,
  );
}

/** at most this many entries go into one exported report */
export const OPERATION_EXPORT_LIMIT = 20_000;

/**
 * Every entry of the operation log that matches, oldest first, for a
 * report; read in pages, up to a limit. `truncated` tells when it was hit.
 */
export async function getAllOperations(
  query: OperationLogQuery,
  signal?: AbortSignal,
  pageSize = 1000,
): Promise<{ entries: OperationLogEntry[]; truncated: boolean }> {
  const filter = Object.fromEntries(Object.entries(query).filter(([, value]) => value)) as Record<string, QueryValue>;
  const path = `${engineApi()}/history/user-operation`;
  const entries: OperationLogEntry[] = [];
  for (let first = 0; first < OPERATION_EXPORT_LIMIT; first += pageSize) {
    const page = await request<OperationLogEntry[]>(path, {
      query: { ...filter, sortBy: 'timestamp', sortOrder: 'asc', firstResult: first, maxResults: pageSize },
      signal,
    });
    entries.push(...page);
    if (page.length < pageSize) return { entries, truncated: false };
  }
  return { entries, truncated: true };
}

/**
 * The names of tasks, open or done, by id, read from the history. The history
 * query has no list of ids, so each task is read on its own, a few at a time.
 * Tasks the user may not read, or whose history is gone, are left out.
 */
export async function getTaskNames(ids: string[], signal?: AbortSignal): Promise<Map<string, string>> {
  const names = new Map<string, string>();
  const distinct = Array.from(new Set(ids.filter(Boolean)));
  await mapLimited(distinct, 6, async (taskId) => {
    const [task] = await request<{ id: string; name: string | null; taskDefinitionKey: string | null }[]>(
      `${engineApi()}/history/task`,
      { query: { taskId, maxResults: 1 }, signal },
    ).catch(() => []);
    const name = task?.name ?? task?.taskDefinitionKey;
    if (task && name) names.set(task.id, name);
  });
  return names;
}

/** The instance a text names: an instance id as it is, else the newest instance with that business key. */
/** An instance a case text can name; several when a business key was reused. */
export interface CaseMatch {
  id: string;
  processDefinitionKey: string | null;
  startTime: string | null;
  state: string | null;
}

/** How many instances sharing a business key are offered to choose from. */
export const CASE_MATCH_LIMIT = 20;

/**
 * The instances a case text names: the one with that id, or else those with
 * that business key, newest first. A business key need not be unique.
 */
export async function findCases(text: string, signal?: AbortSignal): Promise<{ matches: CaseMatch[]; more: boolean }> {
  const value = text.trim();
  if (!value) return { matches: [], more: false };
  const byId = await request<CaseMatch[]>(`${engineApi()}/history/process-instance`, {
    query: { processInstanceId: value, maxResults: 1 },
    signal,
  });
  if (byId[0]) return { matches: byId.slice(0, 1), more: false };
  const byKey = await request<CaseMatch[]>(`${engineApi()}/history/process-instance`, {
    query: { processInstanceBusinessKey: value, sortBy: 'startTime', sortOrder: 'desc', maxResults: CASE_MATCH_LIMIT + 1 },
    signal,
  });
  return { matches: byKey.slice(0, CASE_MATCH_LIMIT), more: byKey.length > CASE_MATCH_LIMIT };
}

/** The instance a case text names; the newest one if a business key was reused. */
export async function resolveCase(text: string, signal?: AbortSignal): Promise<string | null> {
  return (await findCases(text, signal)).matches[0]?.id ?? null;
}

/** Sets or, with an empty text, removes the note of an operation. */
export function setOperationAnnotation(operationId: string, annotation: string): Promise<void> {
  const path = `${engineApi()}/history/user-operation/${seg(operationId)}`;
  return annotation.trim()
    ? request<void>(`${path}/set-annotation`, { method: 'PUT', json: { annotation: annotation.trim() } })
    : request<void>(`${path}/clear-annotation`, { method: 'PUT' });
}

// --- restart ----------------------------------------------------------------

export interface RestartOptions {
  /** ids of historic instances, or every one a history query finds */
  selection: { kind: 'ids'; ids: string[] } | { kind: 'query'; query: HistoricQuery };
  instructions: { type: 'startBeforeActivity' | 'startAfterActivity'; activityId: string }[];
  initialVariables: boolean;
  withoutBusinessKey: boolean;
  skipCustomListeners: boolean;
  skipIoMappings: boolean;
}

export function restartBody(options: RestartOptions): Record<string, unknown> {
  return {
    ...(options.selection.kind === 'ids'
      ? { processInstanceIds: options.selection.ids }
      : { historicProcessInstanceQuery: historicQueryBody(options.selection.query) }),
    instructions: options.instructions,
    initialVariables: options.initialVariables,
    withoutBusinessKey: options.withoutBusinessKey,
    skipCustomListeners: options.skipCustomListeners,
    skipIoMappings: options.skipIoMappings,
  };
}

export function restartInstances(processDefinitionId: string, options: RestartOptions): Promise<void> {
  return request<void>(`${engineApi()}/process-definition/${seg(processDefinitionId)}/restart`, {
    method: 'POST',
    json: restartBody(options),
  });
}

export function restartInstancesAsync(processDefinitionId: string, options: RestartOptions): Promise<Batch> {
  return request<Batch>(`${engineApi()}/process-definition/${seg(processDefinitionId)}/restart-async`, {
    method: 'POST',
    json: restartBody(options),
  });
}

// --- bulk operations ---------------------------------------------------------

export function setInstancesSuspendedAsync(selection: InstanceSelection, suspended: boolean): Promise<Batch> {
  return request<Batch>(`${engineApi()}/process-instance/suspended-async`, {
    method: 'POST',
    json: { ...selectionBody(selection), suspended },
  });
}

export function deleteInstancesAsync(
  selection: InstanceSelection,
  options: {
    deleteReason?: string;
    skipCustomListeners: boolean;
    skipIoMappings: boolean;
    skipSubprocesses: boolean;
  },
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/process-instance/delete`, {
    method: 'POST',
    json: { ...selectionBody(selection), ...options, deleteReason: options.deleteReason || undefined },
  });
}

export function setInstancesJobRetriesAsync(selection: InstanceSelection, retries: number): Promise<Batch> {
  return request<Batch>(`${engineApi()}/process-instance/job-retries`, {
    method: 'POST',
    // SetJobRetriesByProcessDto names the id list `processInstances`, unlike the other batches.
    json:
      selection.kind === 'ids'
        ? { processInstances: selection.ids, retries }
        : { processInstanceQuery: runtimeQuery(selection.query), retries },
  });
}

export function setInstancesVariablesAsync(selection: InstanceSelection, variables: VariableMap): Promise<Batch> {
  return request<Batch>(`${engineApi()}/process-instance/variables-async`, {
    method: 'POST',
    json: { ...selectionBody(selection), variables },
  });
}

export type RemovalTime =
  | { mode: 'absolute'; date: string }
  | { mode: 'cleared' }
  | { mode: 'calculated' };

/** Sets when the history of instances is removed; the instances are the same ids in history. */
export function setRemovalTimeAsync(
  selection: { kind: 'ids'; ids: string[] } | { kind: 'query'; query: HistoricQuery },
  removal: RemovalTime,
  hierarchical: boolean,
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/history/process-instance/set-removal-time`, {
    method: 'POST',
    json: {
      ...(selection.kind === 'ids'
        ? { historicProcessInstanceIds: selection.ids }
        : { historicProcessInstanceQuery: historicQueryBody(selection.query) }),
      hierarchical,
      ...(removal.mode === 'absolute'
        ? { absoluteRemovalTime: removal.date }
        : removal.mode === 'cleared'
          ? { clearedRemovalTime: true }
          : { calculatedRemovalTime: true }),
    },
  });
}

/** Removes the history of finished instances in a batch. */
export function deleteHistoricInstancesAsync(
  selection: { kind: 'ids'; ids: string[] } | { kind: 'query'; query: HistoricQuery },
  deleteReason?: string,
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/history/process-instance/delete`, {
    method: 'POST',
    json: {
      ...(selection.kind === 'ids'
        ? { historicProcessInstanceIds: selection.ids }
        : { historicProcessInstanceQuery: historicQueryBody(selection.query) }),
      deleteReason: deleteReason || undefined,
    },
  });
}

// --- statistics from history -------------------------------------------------

export interface HistoricActivityStatistics {
  id: string;
  /** still running */
  instances: number;
  finished: number;
  canceled: number;
  completeScope: number;
  openIncidents: number;
  resolvedIncidents: number;
  deletedIncidents: number;
}

export function getHistoricActivityStatistics(
  processDefinitionId: string,
  range: { startedAfter?: string; startedBefore?: string },
  signal?: AbortSignal,
): Promise<HistoricActivityStatistics[]> {
  return request<HistoricActivityStatistics[]>(
    `${engineApi()}/history/process-definition/${seg(processDefinitionId)}/statistics`,
    {
      query: {
        finished: true,
        canceled: true,
        incidents: true,
        startedAfter: range.startedAfter,
        startedBefore: range.startedBefore,
      },
      signal,
    },
  );
}

export interface ActivityDuration {
  activityId: string;
  count: number;
  average: number;
  minimum: number;
  maximum: number;
  /** nine in ten took at most this long */
  p90: number;
}

/** The value below which the given share of the values lie (nearest rank); exported for tests. */
export function percentile(values: number[], share: number): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(share * sorted.length) - 1))] ?? 0;
}

/** Summarises the durations of finished activity instances, per activity; exported for tests. */
export function summariseDurations(activities: Pick<HistoricActivity, 'activityId' | 'durationInMillis'>[]): ActivityDuration[] {
  const byActivity = new Map<string, number[]>();
  for (const activity of activities) {
    if (activity.durationInMillis === null || activity.durationInMillis === undefined) continue;
    const list = byActivity.get(activity.activityId) ?? [];
    list.push(activity.durationInMillis);
    byActivity.set(activity.activityId, list);
  }
  return [...byActivity.entries()]
    .map(([activityId, durations]) => ({
      activityId,
      count: durations.length,
      average: durations.reduce((sum, value) => sum + value, 0) / durations.length,
      minimum: Math.min(...durations),
      maximum: Math.max(...durations),
      p90: percentile(durations, 0.9),
    }))
    .sort((a, b) => b.average - a.average);
}

/** How long the activities that are active now have been waiting, per activity. */
export interface ActivityAge {
  activityId: string;
  /** active activity instances */
  count: number;
  /** the age of the oldest one, in milliseconds */
  oldest: number;
}

/** Ages of the active activity instances of a version, oldest first; exported for tests. */
export function summariseAges(activities: Pick<HistoricActivity, 'activityId' | 'startTime'>[], now: number): ActivityAge[] {
  const byActivity = new Map<string, ActivityAge>();
  for (const activity of activities) {
    const started = activity.startTime ? Date.parse(activity.startTime) : NaN;
    if (Number.isNaN(started)) continue;
    const age = Math.max(0, now - started);
    const entry = byActivity.get(activity.activityId) ?? { activityId: activity.activityId, count: 0, oldest: 0 };
    entry.count++;
    entry.oldest = Math.max(entry.oldest, age);
    byActivity.set(activity.activityId, entry);
  }
  return [...byActivity.values()].sort((a, b) => b.oldest - a.oldest);
}

export async function getActivityAges(
  processDefinitionId: string,
  signal?: AbortSignal,
): Promise<{ sample: number; ages: ActivityAge[] }> {
  const activities = await request<HistoricActivity[]>(`${engineApi()}/history/activity-instance`, {
    query: { processDefinitionId, unfinished: true, sortBy: 'startTime', sortOrder: 'asc', maxResults: DURATION_SAMPLE },
    signal,
  });
  return { sample: activities.length, ages: summariseAges(activities, Date.now()) };
}

/** How many finished activity instances the duration statistics read at most. */
export const DURATION_SAMPLE = 2000;

export async function getActivityDurations(
  processDefinitionId: string,
  range: { startedAfter?: string; startedBefore?: string },
  signal?: AbortSignal,
): Promise<{ sample: number; durations: ActivityDuration[] }> {
  const activities = await request<HistoricActivity[]>(`${engineApi()}/history/activity-instance`, {
    query: {
      processDefinitionId,
      finished: true,
      startedAfter: range.startedAfter,
      startedBefore: range.startedBefore,
      sortBy: 'startTime',
      sortOrder: 'desc',
      maxResults: DURATION_SAMPLE,
    },
    signal,
  });
  return { sample: activities.length, durations: summariseDurations(activities) };
}

export interface DurationReportEntry {
  period: number;
  periodUnit: string;
  average: number;
  minimum: number;
  maximum: number;
  processDefinitionKey?: string;
}

export function getDurationReport(
  periodUnit: 'month' | 'quarter',
  processDefinitionKey?: string,
  signal?: AbortSignal,
): Promise<DurationReportEntry[]> {
  return request<DurationReportEntry[]>(`${engineApi()}/history/process-instance/report`, {
    query: { reportType: 'duration', periodUnit, processDefinitionKeyIn: processDefinitionKey || undefined },
    signal,
  });
}

/** Instances started per day over the last days, oldest first. */
export async function getStartedPerDay(
  days: number,
  toEngine: (date: Date) => string,
  signal?: AbortSignal,
  now: Date = new Date(),
): Promise<{ day: Date; count: number }[]> {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (days - 1));
  const ranges = Array.from({ length: days }, (_, index) => {
    const from = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index);
    const to = new Date(from.getFullYear(), from.getMonth(), from.getDate() + 1);
    return { from, to };
  });
  const counts = await Promise.all(
    ranges.map((range) =>
      request<Count>(`${engineApi()}/history/process-instance/count`, {
        query: { startedAfter: toEngine(range.from), startedBefore: toEngine(range.to), ...tenantFilter() },
        signal,
      }),
    ),
  );
  return ranges.map((range, index) => ({ day: range.from, count: counts[index]?.count ?? 0 }));
}

// --- decision history ---------------------------------------------------------

export interface HistoricDecisionValue {
  id: string;
  clauseId: string | null;
  clauseName: string | null;
  variableName?: string | null;
  ruleId?: string | null;
  ruleOrder?: number | null;
  type: string | null;
  value: unknown;
  errorMessage?: string | null;
}

export interface HistoricDecision {
  id: string;
  decisionDefinitionId: string;
  decisionDefinitionKey: string;
  decisionDefinitionName: string | null;
  evaluationTime: string;
  processDefinitionKey: string | null;
  processInstanceId: string | null;
  activityId: string | null;
  userId: string | null;
  rootDecisionInstanceId: string | null;
  collectResultValue: number | null;
  inputs?: HistoricDecisionValue[];
  outputs?: HistoricDecisionValue[];
}

export function getHistoricDecisions(options: {
  query: { decisionDefinitionKey?: string; processInstanceId?: string; evaluatedAfter?: string; evaluatedBefore?: string };
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<HistoricDecision>> {
  const query = {
    ...(Object.fromEntries(Object.entries(options.query).filter(([, value]) => value)) as Record<string, QueryValue>),
    ...tenantFilter(),
  };
  const path = `${engineApi()}/history/decision-instance`;
  return Promise.all([
    request<Count>(`${path}/count`, { query, signal: options.signal }),
    request<HistoricDecision[]>(path, {
      query: { ...query, sortBy: 'evaluationTime', sortOrder: 'desc', ...options.paging },
      signal: options.signal,
    }),
  ]).then(([total, items]) => ({ count: total.count, items }));
}

export function getHistoricDecision(id: string): Promise<HistoricDecision> {
  return request<HistoricDecision>(`${engineApi()}/history/decision-instance/${seg(id)}`, {
    query: { includeInputs: true, includeOutputs: true, disableCustomObjectDeserialization: true },
  });
}

/** The rules that matched, without repetition, in the order of the table. */
export function matchedRules(decision: Pick<HistoricDecision, 'outputs'>): string[] {
  const rules = (decision.outputs ?? [])
    .filter((output) => output.ruleId)
    .sort((a, b) => (a.ruleOrder ?? 0) - (b.ruleOrder ?? 0))
    .map((output) => output.ruleId as string);
  return [...new Set(rules)];
}

// --- tasks --------------------------------------------------------------------

export type TaskAssignment =
  | { kind: 'claim'; userId: string }
  | { kind: 'unclaim' }
  | { kind: 'assign'; userId: string };

/**
 * Changes the assignee of tasks one by one; the result names the tasks that
 * failed. A claim fails for a task someone else claimed meanwhile, unlike an
 * assignment, which replaces the assignee.
 */
export async function assignTasks(taskIds: string[], change: TaskAssignment): Promise<string[]> {
  const call = (id: string) => {
    const path = `${engineApi()}/task/${seg(id)}`;
    switch (change.kind) {
      case 'claim':
        return request<void>(`${path}/claim`, { method: 'POST', json: { userId: change.userId } });
      case 'unclaim':
        return request<void>(`${path}/unclaim`, { method: 'POST' });
      case 'assign':
        return request<void>(`${path}/assignee`, { method: 'POST', json: { userId: change.userId } });
    }
  };
  const results = await Promise.allSettled(taskIds.map(call));
  return taskIds.filter((_, index) => results[index]?.status === 'rejected');
}
