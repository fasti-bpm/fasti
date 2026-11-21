/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { toEngineDate } from '../lib/dates';
import { tenantFilter, tenantFilterBody } from '../lib/userPrefs';
import { apiUrl, isApiError, request, seg, type QueryValue } from './http';
import type { SortOrder, Task, VariableMap, VariableValue } from './types';

/**
 * Resources the monitoring pages read and change: the REST API of the engine
 * and the resources of the base plugin of the classic monitoring application.
 * Both exist already; nothing here adds to the contract of the server.
 */
const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;
const pluginApi = (): string => `/cockpit/plugin/base/${seg(env.engine)}`;

export interface Paging {
  firstResult: number;
  maxResults: number;
}

export interface Sorting<S extends string = string> {
  sortBy: S;
  sortOrder: SortOrder;
}

export interface Page<T> {
  count: number;
  items: T[];
}

interface Count {
  count: number;
}

async function count(path: string, query?: Record<string, QueryValue>, signal?: AbortSignal) {
  return (await request<Count>(path, { query, signal })).count;
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

// --- dashboard --------------------------------------------------------------

/** What moves all the time: read with every refresh. The open tasks come with the task totals (`getTaskTotals`). */
export interface DashboardNumbers {
  instances: number;
  incidents: number;
}

export async function getDashboardNumbers(signal?: AbortSignal): Promise<DashboardNumbers> {
  const api = engineApi();
  const [instances, incidents] = await Promise.all([
    count(`${api}/process-instance/count`, tenantFilter(), signal),
    count(`${api}/incident/count`, tenantFilter(), signal),
  ]);
  return { instances, incidents };
}

/** What only a deployment changes: read every few minutes. */
export interface DefinitionNumbers {
  processDefinitions: number;
  decisionDefinitions: number;
  deployments: number;
}

export async function getDefinitionNumbers(signal?: AbortSignal): Promise<DefinitionNumbers> {
  const api = engineApi();
  const [processDefinitions, decisionDefinitions, deployments] = await Promise.all([
    count(`${api}/process-definition/count`, { latestVersion: true, ...tenantFilter() }, signal),
    count(`${api}/decision-definition/count`, { latestVersion: true, ...tenantFilter() }, signal),
    count(`${api}/deployment/count`, tenantFilter(), signal),
  ]);
  return { processDefinitions, decisionDefinitions, deployments };
}

export interface TaskAssignment {
  /** a user works on the task */
  assigned: number;
  /** nobody works on it yet, but groups are offered the task */
  withGroups: number;
  /** nobody works on it and no group is offered the task */
  unassigned: number;
}

/** Who has the open tasks, from the totals the dashboard reads anyway. */
export function taskAssignment(totals: { assigned: number; unassigned: number; orphaned: number }): TaskAssignment {
  return { assigned: totals.assigned, withGroups: totals.unassigned - totals.orphaned, unassigned: totals.orphaned };
}

/** Unassigned open tasks of one candidate group; `null` for tasks without a candidate group. */
export interface GroupTaskCount {
  groupName: string | null;
  taskCount: number;
}

/**
 * The unassigned open tasks by candidate group, from the task report of the
 * REST API. A task with several candidate groups counts for each of them.
 * The report covers every tenant the user may see.
 */
export function getTasksByGroup(signal?: AbortSignal): Promise<GroupTaskCount[]> {
  return request<GroupTaskCount[]>(`${engineApi()}/task/report/candidate-group-count`, { signal });
}

// --- process definitions ----------------------------------------------------

/** The latest version of a process with the numbers of all its versions. */
export interface DefinitionStatistics {
  id: string;
  key: string;
  name: string | null;
  version: number;
  tenantId: string | null;
  /** 1 active, 2 suspended */
  suspensionState: number;
  instances: number;
  incidents: number;
}

export type DefinitionSortBy = 'name' | 'key' | 'instances' | 'incidents' | 'tenantId';

export async function getDefinitionStatistics(options: {
  search?: string;
  sorting: Sorting<DefinitionSortBy>;
  paging: Paging;
  /** `false` for a top list that is not paged: the total is not asked for, `count` is the length of the list */
  total?: boolean;
  signal?: AbortSignal;
}): Promise<Page<DefinitionStatistics>> {
  const path = `${pluginApi()}/process-definition`;
  const like = options.search ? `%${options.search}%` : undefined;
  const byName = { nameLike: like, ...tenantFilter() };
  if (options.total === false && !like) {
    const items = await request<DefinitionStatistics[]>(`${path}/statistics`, {
      query: { ...byName, ...options.sorting, ...options.paging },
      signal: options.signal,
    });
    return { count: items.length, items };
  }
  const [total, items] = await Promise.all([
    count(`${path}/statistics-count`, byName, options.signal),
    request<DefinitionStatistics[]>(`${path}/statistics`, {
      query: { ...byName, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  if (like && total === 0) {
    // processes without a name are found by their key
    const byKey = { keyLike: like, ...tenantFilter() };
    const [keyTotal, keyItems] = await Promise.all([
      count(`${path}/statistics-count`, byKey, options.signal),
      request<DefinitionStatistics[]>(`${path}/statistics`, {
        query: { ...byKey, ...options.sorting, ...options.paging },
        signal: options.signal,
      }),
    ]);
    return { count: keyTotal, items: keyItems };
  }
  return { count: total, items };
}

export interface ProcessDefinitionDetail {
  id: string;
  key: string;
  name: string | null;
  description: string | null;
  version: number;
  versionTag: string | null;
  tenantId: string | null;
  deploymentId: string | null;
  resource: string | null;
  suspended: boolean;
  historyTimeToLive: number | null;
}

export function getDefinition(id: string): Promise<ProcessDefinitionDetail> {
  return request<ProcessDefinitionDetail>(`${engineApi()}/process-definition/${seg(id)}`);
}

/** Every version of a process, the newest first. */
export function getDefinitionVersions(
  key: string,
  tenantId: string | null,
): Promise<ProcessDefinitionDetail[]> {
  return request<ProcessDefinitionDetail[]>(`${engineApi()}/process-definition`, {
    query: {
      key,
      ...(tenantId ? { tenantIdIn: tenantId } : { withoutTenantId: true }),
      sortBy: 'version',
      sortOrder: 'desc',
    },
  });
}

export async function getLatestDefinition(key: string): Promise<ProcessDefinitionDetail | null> {
  const found = await request<ProcessDefinitionDetail[]>(`${engineApi()}/process-definition`, {
    query: { key, latestVersion: true, maxResults: 1, ...tenantFilter() },
  });
  return found[0] ?? null;
}

/** The newest version of every process, by name: the choices of a process filter. */
export function getLatestDefinitions(signal?: AbortSignal): Promise<{ key: string; name: string | null }[]> {
  return request<{ key: string; name: string | null }[]>(`${engineApi()}/process-definition`, {
    query: { latestVersion: true, sortBy: 'name', sortOrder: 'asc', firstResult: 0, maxResults: 500, ...tenantFilter() },
    signal,
  });
}

export interface IncidentCount {
  incidentType: string;
  incidentCount: number;
}

export interface ActivityStatistics {
  /** id of the activity in the diagram */
  id: string;
  instances: number;
  failedJobs: number;
  incidents: IncidentCount[];
}

export function getActivityStatistics(
  definitionId: string,
  signal?: AbortSignal,
): Promise<ActivityStatistics[]> {
  return request<ActivityStatistics[]>(
    `${engineApi()}/process-definition/${seg(definitionId)}/statistics`,
    { query: { incidents: true }, signal },
  );
}

export function setDefinitionSuspended(
  id: string,
  options: { suspended: boolean; includeProcessInstances: boolean; executionDate: string | null },
): Promise<void> {
  return request<void>(`${engineApi()}/process-definition/${seg(id)}/suspended`, {
    method: 'PUT',
    json: options,
  });
}

export function deleteDefinition(
  id: string,
  options: { cascade: boolean; skipCustomListeners: boolean; skipIoMappings: boolean },
): Promise<void> {
  return request<void>(`${engineApi()}/process-definition/${seg(id)}`, {
    method: 'DELETE',
    query: options,
  });
}

export interface CalledDefinition {
  id: string;
  key: string;
  name: string | null;
  version: number;
  calledFromActivityIds: string[];
}

export function getCalledDefinitions(definitionId: string): Promise<CalledDefinition[]> {
  return request<CalledDefinition[]>(
    `${pluginApi()}/process-definition/${seg(definitionId)}/called-process-definitions`,
    { method: 'POST', json: {} },
  );
}

export interface JobDefinition {
  id: string;
  processDefinitionId: string;
  activityId: string | null;
  jobType: string;
  jobConfiguration: string | null;
  overridingJobPriority: number | null;
  suspended: boolean;
}

export function getJobDefinitions(processDefinitionId: string): Promise<JobDefinition[]> {
  return request<JobDefinition[]>(`${engineApi()}/job-definition`, {
    query: { processDefinitionId, sortBy: 'activityId', sortOrder: 'asc' },
  });
}

export function setJobDefinitionSuspended(
  id: string,
  suspended: boolean,
  includeJobs: boolean,
): Promise<void> {
  return request<void>(`${engineApi()}/job-definition/${seg(id)}/suspended`, {
    method: 'PUT',
    json: { suspended, includeJobs },
  });
}

/** `null` removes the overriding priority. */
export function setJobDefinitionPriority(
  id: string,
  priority: number | null,
  includeJobs: boolean,
): Promise<void> {
  return request<void>(`${engineApi()}/job-definition/${seg(id)}/jobPriority`, {
    method: 'PUT',
    json: { priority, includeJobs: priority === null ? false : includeJobs },
  });
}

/** Sets the retries of every job of a job definition. */
export function setJobDefinitionRetries(id: string, retries: number): Promise<void> {
  return request<void>(`${engineApi()}/job-definition/${seg(id)}/retries`, {
    method: 'PUT',
    json: { retries },
  });
}

// --- process instances ------------------------------------------------------

export interface InstanceListItem {
  id: string;
  businessKey: string | null;
  startTime: string | null;
  incidents: IncidentCount[];
  suspended: boolean;
}

export type VariableOperator = 'eq' | 'neq' | 'gt' | 'gteq' | 'lt' | 'lteq' | 'like';

export interface VariableCondition {
  name: string;
  operator: VariableOperator;
  value: string | number | boolean | null;
}

export interface InstanceQuery {
  processDefinitionId?: string;
  parentProcessInstanceId?: string;
  businessKey?: string;
  activityIdIn?: string[];
  withIncident?: boolean;
  variables?: VariableCondition[];
  /** conditions joined with OR; answered by the engine API, which knows nested queries */
  orQueries?: { variables: VariableCondition[] }[];
}

/**
 * Instances matching a query with OR conditions: the plugin API knows no
 * nested queries, so the engine API answers, newest id first, and the
 * incidents of the rows on the page are counted afterwards.
 */
async function getInstancesWithOr(
  body: Record<string, unknown>,
  sortOrder: SortOrder,
  paging: Paging,
  signal?: AbortSignal,
): Promise<Page<InstanceListItem>> {
  const path = `${engineApi()}/process-instance`;
  const { businessKey, ...rest } = body;
  const engineBody = { ...rest, businessKey: businessKey || undefined };
  const [total, rows] = await Promise.all([
    request<Count>(`${path}/count`, { method: 'POST', json: engineBody, signal }),
    request<{ id: string; businessKey: string | null; suspended: boolean }[]>(path, {
      method: 'POST',
      query: { ...paging },
      json: { ...engineBody, sorting: [{ sortBy: 'instanceId', sortOrder }] },
      signal,
    }),
  ]);
  const ids = rows.map((row) => row.id);
  const failing = ids.length
    ? await request<{ id: string }[]>(path, { method: 'POST', json: { processInstanceIds: ids, withIncident: true }, signal })
    : [];
  const counts = new Map<string, number>();
  await Promise.all(
    failing.map(async ({ id }) => {
      const count = await request<Count>(`${engineApi()}/incident/count`, { query: { processInstanceId: id }, signal });
      counts.set(id, count.count);
    }),
  );
  return {
    count: total.count,
    items: rows.map((row) => {
      const incidents = counts.get(row.id) ?? 0;
      return {
        id: row.id,
        businessKey: row.businessKey,
        suspended: row.suspended,
        startTime: null,
        incidents: incidents ? [{ incidentType: 'failedJob', incidentCount: incidents }] : [],
      };
    }),
  };
}

export async function getInstances(options: {
  query: InstanceQuery;
  sortOrder: SortOrder;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<InstanceListItem>> {
  const path = `${pluginApi()}/process-instance`;
  const body = {
    ...tenantFilterBody(),
    ...options.query,
    activityIdIn: options.query.activityIdIn?.length ? options.query.activityIdIn : undefined,
    variables: options.query.variables?.length ? options.query.variables : undefined,
    withIncident: options.query.withIncident || undefined,
  };
  if (options.query.orQueries?.length) {
    return getInstancesWithOr(body, options.sortOrder, options.paging, options.signal);
  }
  const [total, items] = await Promise.all([
    request<Count>(`${path}/count`, { method: 'POST', json: body, signal: options.signal }),
    request<InstanceListItem[]>(path, {
      method: 'POST',
      query: { ...options.paging },
      json: { ...body, sortBy: 'startTime', sortOrder: options.sortOrder },
      signal: options.signal,
    }),
  ]);
  return { count: total.count, items };
}

export interface ProcessInstance {
  id: string;
  definitionId: string;
  businessKey: string | null;
  suspended: boolean;
  tenantId: string | null;
}

export function getInstance(id: string): Promise<ProcessInstance> {
  return request<ProcessInstance>(`${engineApi()}/process-instance/${seg(id)}`);
}

export interface InstanceHistory {
  startTime: string | null;
  startUserId: string | null;
  superProcessInstanceId: string | null;
  /** the instance at the top of a chain of call activities */
  rootProcessInstanceId?: string | null;
}

/**
 * The instance a call activity of an instance started, the running one first;
 * `null` if there is none or the history is off.
 */
export async function getCalledInstance(processInstanceId: string, activityId: string): Promise<string | null> {
  const find = async (unfinished: boolean) => {
    const activities = await request<{ calledProcessInstanceId: string | null }[]>(`${engineApi()}/history/activity-instance`, {
      query: {
        processInstanceId,
        activityId,
        unfinished: unfinished || undefined,
        finished: !unfinished || undefined,
        sortBy: 'startTime',
        sortOrder: 'desc',
        maxResults: 20,
      },
    });
    return activities.find((activity) => activity.calledProcessInstanceId)?.calledProcessInstanceId ?? null;
  };
  // a running call first, however many have finished since; else the latest one
  return (await find(true)) ?? (await find(false));
}

/** When and by whom the instance was started; `null` without history. */
export function getInstanceHistory(id: string): Promise<InstanceHistory | null> {
  return optional(request<InstanceHistory>(`${engineApi()}/history/process-instance/${seg(id)}`));
}

/** The instance that called this one through a call activity, if any. */
export async function getSuperInstance(id: string): Promise<ProcessInstance | null> {
  const found = await request<ProcessInstance[]>(`${engineApi()}/process-instance`, {
    query: { subProcessInstance: id, maxResults: 1 },
  });
  return found[0] ?? null;
}

export interface ActivityInstance {
  id: string;
  activityId: string;
  activityName: string | null;
  activityType: string;
  executionIds?: string[];
  executionId?: string;
  incidents?: { id: string; activityId: string }[] | null;
  childActivityInstances?: ActivityInstance[];
  childTransitionInstances?: ActivityInstance[];
}

export function getActivityInstances(id: string, signal?: AbortSignal): Promise<ActivityInstance> {
  return request<ActivityInstance>(
    `${engineApi()}/process-instance/${seg(id)}/activity-instances`,
    { signal },
  );
}

export function setInstanceSuspended(id: string, suspended: boolean): Promise<void> {
  return request<void>(`${engineApi()}/process-instance/${seg(id)}/suspended`, {
    method: 'PUT',
    json: { suspended },
  });
}

export interface DeleteInstanceOptions {
  skipCustomListeners: boolean;
  skipIoMappings: boolean;
  skipSubprocesses: boolean;
}

export function deleteInstance(id: string, options: DeleteInstanceOptions): Promise<void> {
  return request<void>(`${engineApi()}/process-instance/${seg(id)}`, {
    method: 'DELETE',
    query: { ...options },
  });
}

export interface CalledInstance {
  id: string;
  businessKey: string | null;
  callActivityInstanceId: string;
  callActivityId: string;
  processDefinitionId: string;
  processDefinitionKey: string;
  processDefinitionName: string | null;
  incidents: IncidentCount[];
}

export function getCalledInstances(id: string): Promise<CalledInstance[]> {
  return request<CalledInstance[]>(
    `${pluginApi()}/process-instance/${seg(id)}/called-process-instances`,
    { method: 'POST', json: {} },
  );
}

// --- variables --------------------------------------------------------------

export interface VariableInstance extends VariableValue {
  id: string;
  name: string;
  type: string;
  processInstanceId: string | null;
  executionId: string | null;
  taskId: string | null;
  activityInstanceId: string | null;
  errorMessage: string | null;
  /** set for a variable of a case instance (CMMN) */
  caseInstanceId?: string | null;
  caseExecutionId?: string | null;
}

/** The variables of a process instance or, with `caseInstanceId`, of a case instance. */
export async function getVariables(options: {
  processInstanceId?: string;
  caseInstanceId?: string;
  nameLike?: string;
  sortOrder: SortOrder;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<VariableInstance>> {
  const path = `${engineApi()}/variable-instance`;
  const query = {
    processInstanceIdIn: options.caseInstanceId ? undefined : options.processInstanceId,
    caseInstanceIdIn: options.caseInstanceId,
    variableNameLike: options.nameLike ? `%${options.nameLike}%` : undefined,
  };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<VariableInstance[]>(path, {
      query: {
        ...query,
        // serialized values are shown as text and never turned into objects
        deserializeValues: false,
        sortBy: 'variableName',
        sortOrder: options.sortOrder,
        ...options.paging,
      },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

/** Where a variable lives: a task, an execution of the instance or a plan item of a case. */
function variableScope(variable: Pick<VariableInstance, 'taskId' | 'executionId' | 'caseExecutionId'>): string {
  if (variable.taskId) {
    return `${engineApi()}/task/${seg(variable.taskId)}/localVariables`;
  }
  if (!variable.executionId && variable.caseExecutionId) {
    return `${engineApi()}/case-execution/${seg(variable.caseExecutionId)}/localVariables`;
  }
  return `${engineApi()}/execution/${seg(variable.executionId ?? '')}/localVariables`;
}

export function updateVariable(variable: VariableInstance, value: VariableValue): Promise<void> {
  return request<void>(`${variableScope(variable)}/${seg(variable.name)}`, {
    method: 'PUT',
    json: value,
  });
}

function binaryParts(file: File, valueType: 'File' | 'Bytes'): FormData {
  const parts = new FormData();
  parts.append('data', file, file.name);
  parts.append('valueType', valueType);
  return parts;
}

/** Replaces the content of a file or bytes variable with an uploaded file. */
export function uploadVariableData(variable: VariableInstance, file: File): Promise<void> {
  return request<void>(`${variableScope(variable)}/${seg(variable.name)}/data`, {
    method: 'POST',
    multipart: binaryParts(file, variable.type === 'Bytes' ? 'Bytes' : 'File'),
  });
}

/** Adds a file variable to a process instance. */
export function addInstanceFileVariable(processInstanceId: string, name: string, file: File): Promise<void> {
  return request<void>(
    `${engineApi()}/process-instance/${seg(processInstanceId)}/variables/${seg(name)}/data`,
    { method: 'POST', multipart: binaryParts(file, 'File') },
  );
}

export function deleteVariable(variable: VariableInstance): Promise<void> {
  return request<void>(`${variableScope(variable)}/${seg(variable.name)}`, { method: 'DELETE' });
}

export function addInstanceVariable(
  processInstanceId: string,
  name: string,
  value: VariableValue,
): Promise<void> {
  return request<void>(
    `${engineApi()}/process-instance/${seg(processInstanceId)}/variables/${seg(name)}`,
    { method: 'PUT', json: value },
  );
}

/** Adds a variable to a case instance. */
export function addCaseVariable(caseInstanceId: string, name: string, value: VariableValue): Promise<void> {
  return request<void>(`${engineApi()}/case-instance/${seg(caseInstanceId)}/variables/${seg(name)}`, {
    method: 'PUT',
    json: value,
  });
}

/** Adds a file variable to a case instance. */
export function addCaseFileVariable(caseInstanceId: string, name: string, file: File): Promise<void> {
  return request<void>(`${engineApi()}/case-instance/${seg(caseInstanceId)}/variables/${seg(name)}/data`, {
    method: 'POST',
    multipart: binaryParts(file, 'File'),
  });
}

/** Link to the content of a file or bytes variable; the browser downloads it. */
export function variableDataUrl(variableId: string): string {
  return apiUrl(`${engineApi()}/variable-instance/${seg(variableId)}/data`);
}

// --- incidents --------------------------------------------------------------

export interface Incident {
  id: string;
  incidentTimestamp: string;
  incidentType: string;
  incidentMessage: string | null;
  activityId: string | null;
  failedActivityId: string | null;
  processInstanceId: string | null;
  processDefinitionId: string | null;
  causeIncidentId: string | null;
  rootCauseIncidentId: string | null;
  /** id of the failed job or external task */
  configuration: string | null;
  annotation: string | null;
  /** only reported for the incidents of one instance or definition */
  rootCauseIncidentProcessInstanceId?: string | null;
  rootCauseIncidentConfiguration?: string | null;
  rootCauseIncidentMessage?: string | null;
}

export type IncidentSortBy = 'incidentTimestamp' | 'incidentMessage' | 'incidentType' | 'activityId';

/** Incidents of instances or definitions, with the incident that caused them. */
export async function getScopedIncidents(options: {
  processInstanceId?: string;
  processDefinitionId?: string;
  activityIdIn?: string[];
  sorting: Sorting<IncidentSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<Incident>> {
  const path = `${pluginApi()}/incident`;
  const body = {
    processInstanceIdIn: options.processInstanceId ? [options.processInstanceId] : undefined,
    processDefinitionIdIn: options.processDefinitionId ? [options.processDefinitionId] : undefined,
    activityIdIn: options.activityIdIn?.length ? options.activityIdIn : undefined,
  };
  const [total, items] = await Promise.all([
    request<Count>(`${path}/count`, { method: 'POST', json: body, signal: options.signal }),
    request<Incident[]>(path, {
      method: 'POST',
      query: { ...options.paging },
      json: { ...body, ...options.sorting },
      signal: options.signal,
    }),
  ]);
  return { count: total.count, items };
}

/** Incidents of the whole engine. */
export async function getIncidents(options: {
  incidentType?: string;
  messageLike?: string;
  processDefinitionKey?: string;
  activityId?: string;
  sorting: Sorting<IncidentSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<Incident>> {
  const path = `${engineApi()}/incident`;
  const query = {
    incidentType: options.incidentType || undefined,
    incidentMessageLike: options.messageLike ? `%${options.messageLike}%` : undefined,
    processDefinitionKeyIn: options.processDefinitionKey || undefined,
    activityId: options.activityId || undefined,
    ...tenantFilter(),
  };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<Incident[]>(path, {
      query: { ...query, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

export function setIncidentAnnotation(id: string, annotation: string | null): Promise<void> {
  const path = `${engineApi()}/incident/${seg(id)}/annotation`;
  return annotation
    ? request<void>(path, { method: 'PUT', json: { annotation } })
    : request<void>(path, { method: 'DELETE' });
}

/** What failed: a job or an external task. */
export type FailureKind = 'job' | 'externalTask';

export function failureKind(incidentType: string): FailureKind | null {
  if (incidentType === 'failedJob') return 'job';
  if (incidentType === 'failedExternalTask') return 'externalTask';
  return null;
}

/** The stack trace of a failed job or the error details of an external task; plain text. */
export async function getFailureDetails(kind: FailureKind, id: string): Promise<string> {
  const path =
    kind === 'job'
      ? `${engineApi()}/job/${seg(id)}/stacktrace`
      : `${engineApi()}/external-task/${seg(id)}/errorDetails`;
  return String((await request<unknown>(path, { accept: 'text/plain' })) ?? '');
}

export function setRetries(kind: FailureKind, id: string, retries: number): Promise<void> {
  const resource = kind === 'job' ? 'job' : 'external-task';
  return request<void>(`${engineApi()}/${resource}/${seg(id)}/retries`, {
    method: 'PUT',
    json: { retries },
  });
}

// --- tasks, jobs and external tasks of an instance ---------------------------

export function getInstanceTasks(processInstanceId: string): Promise<Task[]> {
  return request<Task[]>(`${engineApi()}/task`, {
    query: { processInstanceId, sortBy: 'created', sortOrder: 'desc' },
  });
}

export interface Job {
  id: string;
  jobDefinitionId: string | null;
  dueDate: string | null;
  createTime: string | null;
  retries: number;
  exceptionMessage: string | null;
  failedActivityId: string | null;
  suspended: boolean;
  priority: number;
  processInstanceId: string | null;
  processDefinitionKey: string | null;
}

export function getJobs(query: {
  processInstanceId?: string;
  jobDefinitionId?: string;
  noRetriesLeft?: boolean;
  maxResults?: number;
}): Promise<Job[]> {
  return request<Job[]>(`${engineApi()}/job`, {
    query: { ...query, sortBy: 'jobDueDate', sortOrder: 'asc' },
  });
}

/** Which jobs the engine-wide list of failed jobs shows. */
export type FailedJobScope = 'noRetriesLeft' | 'withException';

export type FailedJobSortBy = 'jobDueDate' | 'jobRetries' | 'processInstanceId' | 'jobPriority';

function failedJobQuery(scope: FailedJobScope, processDefinitionKey?: string) {
  return {
    withException: true,
    noRetriesLeft: scope === 'noRetriesLeft' ? true : undefined,
    processDefinitionKey: processDefinitionKey || undefined,
  };
}

/** The same in a JSON body of a batch, with the tenant the list shows. */
export function failedJobQueryBody(scope: FailedJobScope, processDefinitionKey?: string) {
  return { ...failedJobQuery(scope, processDefinitionKey), ...tenantFilterBody() };
}

/** Jobs of the whole engine that failed: with an exception, by default without retries. */
export async function getFailedJobs(options: {
  scope: FailedJobScope;
  processDefinitionKey?: string;
  sorting: Sorting<FailedJobSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<Job>> {
  const path = `${engineApi()}/job`;
  const query = { ...failedJobQuery(options.scope, options.processDefinitionKey), ...tenantFilter() };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<Job[]>(path, {
      query: { ...query, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

/** Sets the retries of the given jobs one by one; the result names the jobs that failed. */
export async function setJobsRetries(ids: string[], retries: number): Promise<string[]> {
  const results = await Promise.allSettled(ids.map((id) => setRetries('job', id, retries)));
  return ids.filter((_, index) => results[index]?.status === 'rejected');
}

/** Sets the retries of every failed job of a scope in a batch the engine runs in the background. */
export function setFailedJobsRetriesAsync(
  scope: FailedJobScope,
  retries: number,
  processDefinitionKey?: string,
): Promise<Batch> {
  return request<Batch>(`${engineApi()}/job/retries`, {
    method: 'POST',
    json: { retries, jobQuery: failedJobQueryBody(scope, processDefinitionKey) },
  });
}

export function setJobSuspended(id: string, suspended: boolean): Promise<void> {
  return request<void>(`${engineApi()}/job/${seg(id)}/suspended`, {
    method: 'PUT',
    json: { suspended },
  });
}

export interface ExternalTask {
  id: string;
  activityId: string | null;
  topicName: string | null;
  workerId: string | null;
  lockExpirationTime: string | null;
  retries: number | null;
  errorMessage: string | null;
  priority: number;
  suspended: boolean;
  processInstanceId?: string | null;
  processDefinitionKey?: string | null;
  tenantId?: string | null;
}

/** Which external tasks the engine-wide list shows. */
export type ExternalTaskState = 'all' | 'locked' | 'notLocked' | 'noRetriesLeft' | 'withRetriesLeft';

export type ExternalTaskSortBy = 'id' | 'lockExpirationTime' | 'processInstanceId' | 'processDefinitionKey' | 'taskPriority';

/** Query parameters of the engine-wide list; empty filters are left out. */
export function externalTaskQuery(filter: {
  state: ExternalTaskState;
  topicName?: string;
  workerId?: string;
}): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {};
  if (filter.state !== 'all') {
    query[filter.state] = true;
  }
  if (filter.topicName?.trim()) {
    query.topicName = filter.topicName.trim();
  }
  if (filter.workerId?.trim()) {
    query.workerId = filter.workerId.trim();
  }
  return query;
}

export async function queryExternalTasks(options: {
  filter: Parameters<typeof externalTaskQuery>[0];
  sorting: Sorting<ExternalTaskSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<ExternalTask>> {
  const path = `${engineApi()}/external-task`;
  const query = { ...externalTaskQuery(options.filter), ...tenantFilter() };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<ExternalTask[]>(path, {
      query: { ...query, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

export function getExternalTaskTopics(): Promise<string[]> {
  return request<string[]>(`${engineApi()}/external-task/topic-names`);
}

/** Releases the lock of the given external tasks; the result names the tasks that failed. */
export async function unlockExternalTasks(ids: string[]): Promise<string[]> {
  const results = await Promise.allSettled(
    ids.map((id) => request<void>(`${engineApi()}/external-task/${seg(id)}/unlock`, { method: 'POST' })),
  );
  return ids.filter((_, index) => results[index]?.status === 'rejected');
}

/** Sets the retries of the given external tasks one by one; the result names the tasks that failed. */
export async function setExternalTasksRetries(ids: string[], retries: number): Promise<string[]> {
  const results = await Promise.allSettled(ids.map((id) => setRetries('externalTask', id, retries)));
  return ids.filter((_, index) => results[index]?.status === 'rejected');
}

export function getExternalTasks(processInstanceId: string): Promise<ExternalTask[]> {
  return request<ExternalTask[]>(`${engineApi()}/external-task`, {
    query: { processInstanceId, sortBy: 'id', sortOrder: 'asc' },
  });
}

// --- deployments ------------------------------------------------------------

export interface Deployment {
  id: string;
  name: string | null;
  source: string | null;
  deploymentTime: string;
  tenantId: string | null;
}

export type DeploymentSortBy = 'deploymentTime' | 'name' | 'id';

export async function getDeployments(options: {
  search?: string;
  sorting: Sorting<DeploymentSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<Deployment>> {
  const path = `${engineApi()}/deployment`;
  const query = { nameLike: options.search ? `%${options.search}%` : undefined, ...tenantFilter() };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<Deployment[]>(path, {
      query: { ...query, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

export function getDeployment(id: string): Promise<Deployment> {
  return request<Deployment>(`${engineApi()}/deployment/${seg(id)}`);
}

export interface DeploymentResource {
  id: string;
  name: string;
  deploymentId: string;
}

export function getDeploymentResources(deploymentId: string): Promise<DeploymentResource[]> {
  return request<DeploymentResource[]>(`${engineApi()}/deployment/${seg(deploymentId)}/resources`);
}

function resourcePath(deploymentId: string, resourceId: string): string {
  return `${engineApi()}/deployment/${seg(deploymentId)}/resources/${seg(resourceId)}/data`;
}

/** Link to the content of a resource; used for downloads and images. */
export function resourceDataUrl(deploymentId: string, resourceId: string): string {
  return apiUrl(resourcePath(deploymentId, resourceId));
}

/** The content of a text resource (model, form, script) as it was deployed. */
export async function getResourceText(deploymentId: string, resourceId: string): Promise<string> {
  const content = await request<unknown>(resourcePath(deploymentId, resourceId), {
    accept: '*/*',
  });
  if (content === undefined || content === null) {
    return '';
  }
  // a JSON resource arrives parsed
  return typeof content === 'string' ? content : JSON.stringify(content, null, 2);
}

export function getDeploymentDefinitions(deploymentId: string): Promise<ProcessDefinitionDetail[]> {
  return request<ProcessDefinitionDetail[]>(`${engineApi()}/process-definition`, {
    query: { deploymentId },
  });
}

export function deleteDeployment(
  id: string,
  options: { cascade: boolean; skipCustomListeners: boolean; skipIoMappings: boolean },
): Promise<void> {
  return request<void>(`${engineApi()}/deployment/${seg(id)}`, {
    method: 'DELETE',
    query: options,
  });
}

/** What a deployment created, as far as the modeler needs it. */
export interface CreatedDeployment extends Deployment {
  deployedProcessDefinitions?: Record<string, { id: string; key: string }> | null;
}

/** Starts an instance of a process definition without variables, e.g. to try a new version. */
export function startProcessInstance(definitionId: string): Promise<{ id: string; ended: boolean }> {
  return request<{ id: string; ended: boolean }>(`${engineApi()}/process-definition/${seg(definitionId)}/start`, { method: 'POST', json: {} });
}

/** The newest version of a process, within a tenant or without one. */
export async function getLatestDefinitionId(key: string, tenantId: string | null): Promise<string | null> {
  const [definition] = await request<{ id: string }[]>(`${engineApi()}/process-definition`, {
    query: { key, latestVersion: true, ...(tenantId ? { tenantIdIn: tenantId } : { withoutTenantId: true }), maxResults: 1 },
  });
  return definition?.id ?? null;
}

export function createDeployment(options: {
  name: string;
  files: File[];
  tenantId?: string;
  /** do not deploy again what is deployed already under this name */
  duplicateFiltering: boolean;
}): Promise<CreatedDeployment> {
  const parts = new FormData();
  parts.append('deployment-name', options.name);
  parts.append('deployment-source', 'fasti web application');
  if (options.duplicateFiltering) {
    parts.append('enable-duplicate-filtering', 'true');
    parts.append('deploy-changed-only', 'true');
  }
  if (options.tenantId) {
    parts.append('tenant-id', options.tenantId);
  }
  options.files.forEach((file) => parts.append(file.name, file, file.name));
  return request<CreatedDeployment>(`${engineApi()}/deployment/create`, {
    method: 'POST',
    multipart: parts,
  });
}

// --- decisions --------------------------------------------------------------

export interface DecisionDefinition {
  id: string;
  key: string;
  name: string | null;
  version: number;
  versionTag: string | null;
  tenantId: string | null;
  deploymentId: string | null;
  resource: string | null;
  decisionRequirementsDefinitionKey: string | null;
}

export type DecisionSortBy = 'name' | 'key' | 'tenantId';

export async function getDecisionDefinitions(options: {
  search?: string;
  sorting: Sorting<DecisionSortBy>;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<DecisionDefinition>> {
  const path = `${engineApi()}/decision-definition`;
  const query = {
    latestVersion: true,
    nameLike: options.search ? `%${options.search}%` : undefined,
    ...tenantFilter(),
  };
  const [total, items] = await Promise.all([
    count(`${path}/count`, query, options.signal),
    request<DecisionDefinition[]>(path, {
      query: { ...query, ...options.sorting, ...options.paging },
      signal: options.signal,
    }),
  ]);
  return { count: total, items };
}

export function getDecisionDefinition(id: string): Promise<DecisionDefinition> {
  return request<DecisionDefinition>(`${engineApi()}/decision-definition/${seg(id)}`);
}

export function getDecisionVersions(
  key: string,
  tenantId: string | null,
): Promise<DecisionDefinition[]> {
  return request<DecisionDefinition[]>(`${engineApi()}/decision-definition`, {
    query: {
      key,
      ...(tenantId ? { tenantIdIn: tenantId } : { withoutTenantId: true }),
      sortBy: 'version',
      sortOrder: 'desc',
    },
  });
}

export async function getDecisionXml(id: string): Promise<string> {
  const result = await request<{ dmnXml: string }>(
    `${engineApi()}/decision-definition/${seg(id)}/xml`,
  );
  return result.dmnXml;
}

export interface DecisionValue {
  clauseName: string | null;
  variableName?: string | null;
  type: string | null;
  value: unknown;
}

export interface DecisionInstance {
  id: string;
  evaluationTime: string;
  processInstanceId: string | null;
  processDefinitionKey: string | null;
  activityId: string | null;
  userId: string | null;
  inputs?: DecisionValue[];
  outputs?: DecisionValue[];
}

/** Evaluations of a decision; `null` if history is switched off or not readable. */
export function getDecisionInstances(options: {
  decisionDefinitionId: string;
  paging: Paging;
  signal?: AbortSignal;
}): Promise<Page<DecisionInstance> | null> {
  const path = `${engineApi()}/history/decision-instance`;
  const query = { decisionDefinitionId: options.decisionDefinitionId };
  return optional(
    Promise.all([
      count(`${path}/count`, query, options.signal),
      request<DecisionInstance[]>(path, {
        query: {
          ...query,
          includeInputs: true,
          includeOutputs: true,
          disableCustomObjectDeserialization: true,
          sortBy: 'evaluationTime',
          sortOrder: 'desc',
          ...options.paging,
        },
        signal: options.signal,
      }),
    ]).then(([total, items]) => ({ count: total, items })),
  );
}

// --- batches ----------------------------------------------------------------

export interface Batch {
  id: string;
  type: string;
  totalJobs: number;
  suspended: boolean;
  createUserId: string | null;
  startTime: string | null;
  batchJobDefinitionId: string | null;
  remainingJobs: number;
  completedJobs: number;
  failedJobs: number;
}

export function getRunningBatches(signal?: AbortSignal): Promise<Batch[]> {
  return request<Batch[]>(`${engineApi()}/batch/statistics`, {
    query: { sortBy: 'batchId', sortOrder: 'desc', ...tenantFilter() },
    signal,
  });
}

/** One batch with its progress; `null` once it ended (or when it is not visible to the user). */
export async function getBatchStatistics(id: string, signal?: AbortSignal): Promise<Batch | null> {
  const [batch] = await request<Batch[]>(`${engineApi()}/batch/statistics`, { query: { batchId: id }, signal });
  return batch ?? null;
}

export interface FinishedBatch {
  id: string;
  type: string;
  totalJobs: number;
  createUserId: string | null;
  startTime: string | null;
  endTime: string | null;
}

/** Batches that ended; `null` if history is switched off or not readable. */
export function getFinishedBatches(
  paging: Paging,
  signal?: AbortSignal,
): Promise<Page<FinishedBatch> | null> {
  const path = `${engineApi()}/history/batch`;
  return optional(
    Promise.all([
      count(`${path}/count`, { completed: true, ...tenantFilter() }, signal),
      request<FinishedBatch[]>(path, {
        query: { completed: true, ...tenantFilter(), sortBy: 'endTime', sortOrder: 'desc', ...paging },
        signal,
      }),
    ]).then(([total, items]) => ({ count: total, items })),
  );
}

export function setBatchSuspended(id: string, suspended: boolean): Promise<void> {
  return request<void>(`${engineApi()}/batch/${seg(id)}/suspended`, {
    method: 'PUT',
    json: { suspended },
  });
}

/** Stops a batch; with `cascade` its history is removed as well. */
export function deleteBatch(id: string, cascade: boolean): Promise<void> {
  return request<void>(`${engineApi()}/batch/${seg(id)}`, { method: 'DELETE', query: { cascade } });
}

export function deleteFinishedBatch(id: string): Promise<void> {
  return request<void>(`${engineApi()}/history/batch/${seg(id)}`, { method: 'DELETE' });
}

// --- search -----------------------------------------------------------------

export interface SearchHit {
  /** `finished` is an instance that only the history knows; `case` a case instance (CMMN), open or closed */
  kind: 'instance' | 'finished' | 'case' | 'task' | 'incident' | 'job' | 'deployment' | 'batch';
  id: string;
  title: string;
  detail: string | null;
  /** the instance an incident or job belongs to */
  instanceId?: string | null;
}

/** Whether a text may be an id of the engine: one word, no spaces. */
export function mayBeId(term: string): boolean {
  return /^\S{1,100}$/u.test(term);
}

/**
 * What has exactly the given id: a running or finished instance, a case
 * instance, a task, an incident, a job, a deployment or a batch; or process
 * and case instances with exactly this business key. Each lookup the user may
 * not make is skipped; the engine only returns what the user may read.
 */
export async function findById(
  term: string,
  options: { instances: boolean; tasks: boolean; signal?: AbortSignal },
): Promise<SearchHit[]> {
  if (!mayBeId(term)) {
    return [];
  }
  const { signal } = options;
  const get = <T,>(path: string, query: Record<string, string | number>, enabled: boolean): Promise<T[]> =>
    enabled ? request<T[]>(`${engineApi()}${path}`, { query: { ...query, maxResults: 5 }, signal }).catch(() => []) : Promise.resolve([]);
  type Instance = { id: string; businessKey: string | null; definitionId: string };
  type Finished = { id: string; businessKey: string | null; processDefinitionKey: string; state: string };
  type Case = { id: string; businessKey: string | null; caseDefinitionId: string };
  type FinishedCase = { id: string; businessKey: string | null; caseDefinitionKey: string | null };
  const [running, byKey, finished, cases, casesByKey, closedCases, tasks, incidents, jobs, deployments, batches] = await Promise.all([
    get<Instance>('/process-instance', { processInstanceIds: term }, options.instances),
    get<Instance>('/process-instance', { businessKey: term }, options.instances),
    get<Finished>('/history/process-instance', { processInstanceId: term }, options.instances),
    get<Case>('/case-instance', { caseInstanceId: term }, options.instances),
    get<Case>('/case-instance', { businessKey: term }, options.instances),
    get<FinishedCase>('/history/case-instance', { caseInstanceId: term }, options.instances),
    get<{ id: string; name: string | null; taskDefinitionKey: string | null; assignee: string | null }>('/task', { taskId: term }, options.tasks),
    get<{ id: string; incidentMessage: string | null; activityId: string; processInstanceId: string | null }>('/incident', { incidentId: term }, options.instances),
    get<{ id: string; exceptionMessage: string | null; processInstanceId: string | null; processDefinitionKey: string | null }>('/job', { jobId: term }, options.instances),
    get<{ id: string; name: string | null; source: string | null }>('/deployment', { id: term }, options.instances),
    get<{ id: string; type: string }>('/batch', { batchId: term }, options.instances),
  ]);
  const hits: SearchHit[] = [];
  const seen = new Set<string>();
  for (const instance of [...running, ...byKey]) {
    if (seen.has(instance.id)) continue;
    seen.add(instance.id);
    hits.push({ kind: 'instance', id: instance.id, title: instance.businessKey ?? instance.id, detail: instance.definitionId.split(':')[0] ?? null });
  }
  for (const instance of finished) {
    if (seen.has(instance.id)) continue;
    hits.push({ kind: 'finished', id: instance.id, title: instance.businessKey ?? instance.id, detail: instance.processDefinitionKey });
  }
  // a case instance is shown on one page, open or closed
  for (const instance of [...cases, ...casesByKey, ...closedCases]) {
    if (seen.has(instance.id)) continue;
    seen.add(instance.id);
    const detail = 'caseDefinitionKey' in instance ? instance.caseDefinitionKey : (instance.caseDefinitionId.split(':')[0] ?? null);
    hits.push({ kind: 'case', id: instance.id, title: instance.businessKey ?? instance.id, detail });
  }
  for (const task of tasks) {
    hits.push({ kind: 'task', id: task.id, title: task.name ?? task.taskDefinitionKey ?? task.id, detail: task.assignee });
  }
  for (const incident of incidents) {
    hits.push({ kind: 'incident', id: incident.id, title: incident.incidentMessage ?? incident.activityId, detail: incident.activityId, instanceId: incident.processInstanceId });
  }
  for (const job of jobs) {
    hits.push({ kind: 'job', id: job.id, title: job.exceptionMessage ?? job.id, detail: job.processDefinitionKey, instanceId: job.processInstanceId });
  }
  for (const deployment of deployments) {
    hits.push({ kind: 'deployment', id: deployment.id, title: deployment.name ?? deployment.id, detail: deployment.source });
  }
  for (const batch of batches) {
    hits.push({ kind: 'batch', id: batch.id, title: batch.type, detail: null });
  }
  return hits;
}

/** `like` pattern of the engine for text anywhere in a value; its wildcards are escaped. */
export function likePattern(term: string): string {
  return `%${term.replace(/[\\%_]/g, (char) => `\\${char}`)}%`;
}

/**
 * Process instances whose business key contains the term and open tasks whose
 * name contains it, a few of each. The engine only returns what the user may read.
 */
export async function searchEngine(
  term: string,
  options: { instances: boolean; tasks: boolean; signal?: AbortSignal },
): Promise<SearchHit[]> {
  const like = likePattern(term);
  const [instances, tasks] = await Promise.all([
    options.instances
      ? request<{ id: string; businessKey: string | null; definitionId: string }[]>(`${engineApi()}/process-instance`, {
          query: { businessKeyLike: like, maxResults: 5 },
          signal: options.signal,
        }).catch(() => [])
      : [],
    options.tasks
      ? request<{ id: string; name: string | null; taskDefinitionKey: string | null; assignee: string | null }[]>(
          `${engineApi()}/task`,
          { query: { nameLike: like, maxResults: 5, sortBy: 'created', sortOrder: 'desc' }, signal: options.signal },
        ).catch(() => [])
      : [],
  ]);
  return [
    ...instances.map((instance): SearchHit => ({
      kind: 'instance',
      id: instance.id,
      title: instance.businessKey ?? instance.id,
      detail: instance.definitionId.split(':')[0] ?? null,
    })),
    ...tasks.map((task): SearchHit => ({
      kind: 'task',
      id: task.id,
      title: task.name ?? task.taskDefinitionKey ?? task.id,
      detail: task.assignee,
    })),
  ];
}
/** Types of variables that a start form can take over from another instance. */
const COPYABLE = new Set(['String', 'Boolean', 'Integer', 'Long', 'Short', 'Double', 'Date']);

/**
 * The simple variables of an instance, e.g. to start another one like it;
 * objects, files and other serialized values are left out.
 */
export async function getCopyableVariables(processInstanceId: string): Promise<VariableMap> {
  const all = await request<VariableMap>(`${engineApi()}/process-instance/${seg(processInstanceId)}/variables`, {
    query: { deserializeValues: false },
  });
  return Object.fromEntries(Object.entries(all).filter(([, variable]) => COPYABLE.has(variable.type ?? '')));
}

/** How many process definitions the user may see, in every version; to tell a new installation. */
export async function countDefinitions(signal?: AbortSignal): Promise<number> {
  const result = await request<{ count: number }>(`${engineApi()}/process-definition/count`, {
    query: { ...tenantFilter() },
    signal,
  });
  return result.count;
}

/**
 * What needs someone now: the oldest open incident, jobs out of retries, batches with failures.
 * The overdue tasks belong to it as well; they come with the task totals (`getTaskTotals`).
 */
export interface Attention {
  oldestIncident: { message: string | null; time: string; processInstanceId: string | null } | null;
  failedJobs: number | null;
  failingBatches: number | null;
  /** the incidents could not be read */
  incidentsUnknown: boolean;
}

/** Reads what needs attention; a part the user may not read is `null`, the rest is still shown. */
export async function getAttention(signal?: AbortSignal): Promise<Attention> {
  const api = engineApi();
  const optionalCount = (path: string, query: Record<string, QueryValue>) =>
    request<{ count: number }>(`${api}${path}`, { query: { ...query, ...tenantFilter() }, signal }).then(
      (result) => result.count,
      () => null,
    );
  const [incidents, failedJobs, failingBatches] = await Promise.all([
    request<{ incidentMessage: string | null; incidentTimestamp: string; processInstanceId: string | null }[]>(`${api}/incident`, {
      query: { sortBy: 'incidentTimestamp', sortOrder: 'asc', maxResults: 1, ...tenantFilter() },
      signal,
    }).catch(() => null),
    optionalCount('/job/count', { withException: true, noRetriesLeft: true }),
    // counted by the server: the list of all batches is not needed for one number
    optionalCount('/batch/statistics/count', { withFailures: true }),
  ]);
  if (incidents === null && failedJobs === null && failingBatches === null) {
    // nothing could be read: a failure, not a calm day
    throw new Error('attention unavailable');
  }
  const oldest = incidents?.[0];
  return {
    oldestIncident: oldest ? { message: oldest.incidentMessage, time: oldest.incidentTimestamp, processInstanceId: oldest.processInstanceId } : null,
    failedJobs,
    failingBatches,
    incidentsUnknown: incidents === null,
  };
}

// --- trends -----------------------------------------------------------------

export type TrendRange = '24h' | '7d' | '30d';
export const TREND_RANGES: readonly TrendRange[] = ['24h', '7d', '30d'];
const RANGE_MS: Record<TrendRange, number> = { '24h': 86_400_000, '7d': 7 * 86_400_000, '30d': 30 * 86_400_000 };

export type TrendMeasure = 'started' | 'completed' | 'incidents' | 'tasksDone';

/** A count in the chosen range and in the range just before it; `null` where history cannot be read. */
export interface Trend {
  measure: TrendMeasure;
  current: number | null;
  previous: number | null;
}

/** The periods of a range ending at `now`: the current one and the one before it. */
export function trendPeriods(range: TrendRange, now: Date): { from: Date; to: Date; before: Date } {
  const span = RANGE_MS[range];
  return { to: now, from: new Date(now.getTime() - span), before: new Date(now.getTime() - 2 * span) };
}

/**
 * How the work went in a range compared with the range before it: instances
 * started and completed, incidents raised and tasks completed. Read from history.
 */
export async function getTrends(range: TrendRange, now: Date, signal?: AbortSignal): Promise<Trend[]> {
  const api = engineApi();
  const { from, to, before } = trendPeriods(range, now);
  const measures: { measure: TrendMeasure; path: string; after: string; until: string; extra?: Record<string, QueryValue> }[] = [
    { measure: 'started', path: '/history/process-instance/count', after: 'startedAfter', until: 'startedBefore' },
    { measure: 'completed', path: '/history/process-instance/count', after: 'finishedAfter', until: 'finishedBefore', extra: { completed: true } },
    { measure: 'incidents', path: '/history/incident/count', after: 'createTimeAfter', until: 'createTimeBefore' },
    { measure: 'tasksDone', path: '/history/task/count', after: 'finishedAfter', until: 'finishedBefore' },
  ];
  const read = (path: string, query: Record<string, QueryValue>) =>
    optional(count(`${api}${path}`, { ...query, ...tenantFilter() }, signal)).catch(() => null);
  return Promise.all(
    measures.map(async ({ measure, path, after, until, extra }) => {
      const [current, previous] = await Promise.all([
        read(path, { ...extra, [after]: toEngineDate(from), [until]: toEngineDate(to) }),
        read(path, { ...extra, [after]: toEngineDate(before), [until]: toEngineDate(from) }),
      ]);
      return { measure, current, previous };
    }),
  );
}

// --- business variables as columns of instance lists ------------------------

/** A value of a variable as a column shows it: the type and the plain value, never deserialized. */
export interface ColumnValue {
  type: string;
  value: unknown;
}

const COLUMN_PAGE = 500;

interface ColumnVariableDto {
  name: string;
  type: string;
  value: unknown;
  processInstanceId: string;
  activityInstanceId?: string | null;
}

/**
 * The values of chosen variables of listed instances, by instance and name:
 * one query per name for all instances (`processInstanceIdIn` + `variableName`).
 * A variable of the instance itself wins over one of the same name in a sub
 * scope. Running instances are read from the runtime, `history` also finds
 * finished ones.
 */
export async function getColumnValues(options: {
  instanceIds: string[];
  names: string[];
  history?: boolean;
  signal?: AbortSignal;
}): Promise<Map<string, Map<string, ColumnValue>>> {
  const result = new Map<string, Map<string, ColumnValue>>();
  const ids = [...new Set(options.instanceIds)];
  if (!ids.length || !options.names.length) return result;
  const path = `${engineApi()}/${options.history ? 'history/variable-instance' : 'variable-instance'}`;
  // every page, so local copies in sub scopes never push out the values of other instances
  const readAll = async (variableName: string) => {
    const found: ColumnVariableDto[] = [];
    for (let firstResult = 0; ; firstResult += COLUMN_PAGE) {
      const page = await request<ColumnVariableDto[]>(path, {
        method: 'POST',
        query: { deserializeValues: false, firstResult, maxResults: COLUMN_PAGE },
        json: { processInstanceIdIn: ids, variableName },
        signal: options.signal,
      });
      found.push(...page);
      if (page.length < COLUMN_PAGE) return found;
    }
  };
  const lists = await Promise.all(options.names.map(readAll));
  for (const variable of lists.flat()) {
    const values = result.get(variable.processInstanceId) ?? new Map<string, ColumnValue>();
    const own = !variable.activityInstanceId || variable.activityInstanceId === variable.processInstanceId;
    if (own || !values.has(variable.name)) values.set(variable.name, { type: variable.type, value: variable.value });
    result.set(variable.processInstanceId, values);
  }
  return result;
}

/** Names of the variables of some instances, to suggest as columns. */
export async function getVariableNames(instanceIds: string[], history: boolean, signal?: AbortSignal): Promise<string[]> {
  if (!instanceIds.length) return [];
  const path = `${engineApi()}/${history ? 'history/variable-instance' : 'variable-instance'}`;
  const variables = await request<{ name: string }[]>(path, {
    method: 'POST',
    query: { deserializeValues: false, firstResult: 0, maxResults: 500 },
    json: { processInstanceIdIn: [...new Set(instanceIds)] },
    signal,
  });
  return [...new Set(variables.map((variable) => variable.name))].sort((a, b) => a.localeCompare(b));
}
