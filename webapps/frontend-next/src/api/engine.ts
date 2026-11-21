/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { getCaseBusinessKeys } from './cases';
import { tenantFilter, tenantFilterBody } from '../lib/userPrefs';
import { env } from '../env';
import { apiUrl, isApiError, request, seg, type QueryValue } from './http';
import type {
  AuthInfo,
  EngineInfo,
  FormInfo,
  FormTarget,
  IdentityLink,
  ProcessDefinition,
  ProcessInstanceRef,
  Task,
  TaskComment,
  TaskFilter,
  TaskListItem,
  TaskPage,
  TaskSorting,
  TaskVariable,
  UserOperation,
  UserProfile,
  VariableMap,
} from './types';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;
const authApi = (): string => `/admin/auth/user/${seg(env.engine)}`;

/** The application users log in to: the shell is open to every user. */
const LOGIN_APP = 'welcome';

// --- session ---------------------------------------------------------------

export async function getAuthentication(): Promise<AuthInfo | null> {
  try {
    return await request<AuthInfo>(authApi(), { silentUnauthorized: true });
  } catch (error) {
    // the resource answers 404 if nobody is logged in
    if (isApiError(error, 'notFound') || isApiError(error, 'unauthorized')) {
      return null;
    }
    throw error;
  }
}

export function login(username: string, password: string): Promise<AuthInfo> {
  return request<AuthInfo>(`${authApi()}/login/${LOGIN_APP}`, {
    method: 'POST',
    form: { username, password },
    silentUnauthorized: true,
  });
}

export function logout(): Promise<void> {
  return request<void>(`${authApi()}/logout`, { method: 'POST', silentUnauthorized: true });
}

/** The preferences the current user keeps on the server; `{}` if there are none. */
export function getPreferences(): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>(`${authApi()}/preferences`, { silentUnauthorized: true });
}

export function savePreferences(preferences: Record<string, unknown>): Promise<void> {
  return request<void>(`${authApi()}/preferences`, {
    method: 'PUT',
    json: preferences,
    silentUnauthorized: true,
  });
}

export function getEngines(): Promise<EngineInfo[]> {
  return request<EngineInfo[]>('/engine/engine');
}

export async function getUserProfile(userId: string): Promise<UserProfile | null> {
  try {
    return await request<UserProfile>(`${engineApi()}/user/${seg(userId)}/profile`);
  } catch (error) {
    if (isApiError(error, 'notFound') || isApiError(error, 'forbidden')) {
      return null;
    }
    throw error;
  }
}

// --- filters ---------------------------------------------------------------

export async function getTaskFilters(): Promise<TaskFilter[]> {
  const filters = await request<TaskFilter[]>(`${engineApi()}/filter`, {
    query: { resourceType: 'Task', itemCount: true },
  });
  return [...filters].sort(
    (a, b) =>
      (a.properties?.priority ?? 0) - (b.properties?.priority ?? 0) || a.name.localeCompare(b.name),
  );
}

export async function canCreateFilter(): Promise<boolean> {
  try {
    const options = await request<{ links?: { rel: string }[] }>(`${engineApi()}/filter`, {
      method: 'OPTIONS',
    });
    return options.links?.some((link) => link.rel === 'create') ?? false;
  } catch {
    return false;
  }
}

export function createTaskFilter(
  name: string,
  description: string,
  options: { query?: Record<string, unknown>; priority?: number; owner?: string } = {},
): Promise<TaskFilter> {
  return request<TaskFilter>(`${engineApi()}/filter/create`, {
    method: 'POST',
    json: {
      name,
      resourceType: 'Task',
      owner: options.owner,
      query: options.query ?? {},
      properties: { priority: options.priority ?? 0, description, refresh: true },
    },
  });
}

/** A ready-made task list: its name and description and the query of the filter. */
export interface StarterFilter {
  name: string;
  description: string;
  query: Record<string, unknown>;
}

/**
 * Task lists a user can start with instead of building filters: their own
 * tasks, the tasks offered to their groups, their overdue tasks and all
 * tasks. They use the expressions the engine evaluates for the user who
 * reads the filter, so the same lists work for everybody.
 */
export function starterFilters(text: (key: 'mine' | 'groups' | 'overdue' | 'all') => { name: string; description: string }): StarterFilter[] {
  return [
    { ...text('mine'), query: { assigneeExpression: '${currentUser()}' } },
    { ...text('groups'), query: { candidateGroupsExpression: '${currentUserGroups()}', unassigned: true } },
    { ...text('overdue'), query: { assigneeExpression: '${currentUser()}', dueBeforeExpression: '${now()}' } },
    { ...text('all'), query: {} },
  ];
}

/**
 * Creates the ready-made task lists for a user, owned by that user, in their
 * order; lists whose names exist already, e.g. from an attempt that failed
 * halfway, are not created twice.
 */
export async function createStarterFilters(owner: string, filters: StarterFilter[], existing: string[] = []): Promise<void> {
  for (const [index, filter] of filters.entries()) {
    if (existing.includes(filter.name)) continue;
    await createTaskFilter(filter.name, filter.description, { query: filter.query, priority: index - filters.length, owner });
  }
}

/** A filter together with its stored query, as edited in the filter dialog. */
export interface TaskFilterDetails extends TaskFilter {
  query: TaskQuery;
}

export type TaskFilterInput = Pick<TaskFilterDetails, 'name' | 'owner' | 'query' | 'properties'>;

export function getTaskFilter(filterId: string): Promise<TaskFilterDetails> {
  return request<TaskFilterDetails>(`${engineApi()}/filter/${seg(filterId)}`);
}

/** The actions (`update`, `delete`) the engine offers on a filter for the current user. */
export async function getFilterAccess(filterId: string): Promise<ReadonlySet<string>> {
  try {
    const options = await request<{ links?: { rel: string }[] }>(`${engineApi()}/filter/${seg(filterId)}`, {
      method: 'OPTIONS',
    });
    return new Set((options.links ?? []).map((link) => link.rel));
  } catch {
    return new Set();
  }
}

export function saveTaskFilter(filterId: string | null, filter: TaskFilterInput): Promise<TaskFilterDetails | void> {
  const json = { ...filter, resourceType: 'Task' };
  return filterId
    ? request<void>(`${engineApi()}/filter/${seg(filterId)}`, { method: 'PUT', json })
    : request<TaskFilterDetails>(`${engineApi()}/filter/create`, { method: 'POST', json });
}

export function deleteTaskFilter(filterId: string): Promise<void> {
  return request<void>(`${engineApi()}/filter/${seg(filterId)}`, { method: 'DELETE' });
}

/** The resource type of filters in authorizations. */
const FILTER_RESOURCE = 5;

/** Who may see a filter: one authorization for every user, a user or a group. */
export interface FilterShare {
  id: string;
  /** 0: every user (`userId` is `*`), 1: a grant */
  type: number;
  userId: string | null;
  groupId: string | null;
  permissions: string[];
}

/**
 * The authorizations of a filter, or `null` if the user may not read them
 * (or authorization checks are off, which makes sharing pointless).
 */
export async function getFilterShares(filterId: string): Promise<FilterShare[] | null> {
  try {
    const shares = await request<FilterShare[]>(`${engineApi()}/authorization`, {
      query: { resourceType: FILTER_RESOURCE, resourceId: filterId },
    });
    return shares.filter((share) => share.type !== 2);
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return null;
    }
    throw error;
  }
}

export async function canShareFilters(): Promise<boolean> {
  try {
    const options = await request<{ links?: { rel: string }[] }>(`${engineApi()}/authorization`, {
      method: 'OPTIONS',
    });
    return options.links?.some((link) => link.rel === 'create') ?? false;
  } catch {
    return false;
  }
}

/** Lets every user (`'*'`), a user or a group read a filter, as the classic Tasklist did. */
export function shareFilter(
  filterId: string,
  with_: { userId: string } | { groupId: string },
): Promise<FilterShare> {
  const everyone = 'userId' in with_ && with_.userId === '*';
  return request<FilterShare>(`${engineApi()}/authorization/create`, {
    method: 'POST',
    json: {
      type: everyone ? 0 : 1,
      permissions: ['READ'],
      userId: 'userId' in with_ ? with_.userId : null,
      groupId: 'groupId' in with_ ? with_.groupId : null,
      resourceType: FILTER_RESOURCE,
      resourceId: filterId,
    },
  });
}

export function unshareFilter(authorizationId: string): Promise<void> {
  return request<void>(`${engineApi()}/authorization/${seg(authorizationId)}`, { method: 'DELETE' });
}

interface HalUser {
  id: string;
  firstName?: string | null;
  lastName?: string | null;
}
interface HalDefinition {
  id: string;
  name?: string | null;
  key?: string;
}
interface HalTask extends Task {
  _embedded?: { variable?: TaskVariable[] } | null;
}
interface HalTaskList {
  count: number;
  _embedded?: {
    task?: HalTask[];
    assignee?: HalUser[];
    processDefinition?: HalDefinition[];
    caseDefinition?: HalDefinition[];
  } | null;
}

/** Languages that put the family name first, e.g. "Trần Thị Bích Ngọc". */
const FAMILY_NAME_FIRST = /^(vi|ja|ko|zh|hu)\b/;

export function fullName(
  user: {
    id: string;
    firstName?: string | null;
    lastName?: string | null;
  },
  locale: string = typeof document === 'undefined' ? 'en' : document.documentElement.lang,
): string {
  const parts = FAMILY_NAME_FIRST.test(locale) ? [user.lastName, user.firstName] : [user.firstName, user.lastName];
  const name = parts.filter(Boolean).join(' ').trim();
  return name || user.id;
}

/** Additional criteria that narrow the query of a filter. */
export type TaskQuery = Record<string, unknown>;

export async function getFilterTasks(
  filterId: string,
  options: {
    firstResult: number;
    maxResults: number;
    sorting: TaskSorting[];
    query?: TaskQuery;
    signal?: AbortSignal;
  },
): Promise<TaskPage> {
  const result = await request<HalTaskList>(`${engineApi()}/filter/${seg(filterId)}/list`, {
    method: 'POST',
    accept: 'application/hal+json',
    query: { firstResult: options.firstResult, maxResults: options.maxResults },
    json: { ...tenantFilterBody(), ...options.query, sorting: options.sorting },
    signal: options.signal,
  });
  const users = new Map((result._embedded?.assignee ?? []).map((user) => [user.id, user]));
  // tasks of processes and of cases (CMMN) are named by their definition alike
  const definitions = new Map(
    [...(result._embedded?.processDefinition ?? []), ...(result._embedded?.caseDefinition ?? [])].map((definition) => [
      definition.id,
      definition,
    ]),
  );
  const tasks: TaskListItem[] = (result._embedded?.task ?? []).map((halTask) => {
    const { _embedded, ...task } = halTask;
    const assignee = task.assignee ? users.get(task.assignee) : undefined;
    const definitionId = task.processDefinitionId ?? task.caseDefinitionId;
    const definition = definitionId ? definitions.get(definitionId) : undefined;
    return {
      ...task,
      variables: _embedded?.variable ?? [],
      assigneeName: assignee ? fullName(assignee) : undefined,
      processDefinitionName: definition?.name ?? definition?.key,
      processDefinitionKey: definition?.key,
    };
  });
  // the business keys name the cases; without them the list still works
  const instanceIds = [...new Set(tasks.map((task) => task.processInstanceId).filter((id): id is string => !!id))];
  if (instanceIds.length > 0) {
    const instances = await request<{ id: string; businessKey: string | null }[]>(`${engineApi()}/process-instance`, {
      query: { processInstanceIds: instanceIds.join(','), maxResults: instanceIds.length },
      signal: options.signal,
    }).catch((): { id: string; businessKey: string | null }[] => []);
    const keys = new Map(instances.map((instance) => [instance.id, instance.businessKey]));
    for (const task of tasks) {
      task.businessKey = task.processInstanceId ? (keys.get(task.processInstanceId) ?? null) : null;
    }
  }
  // the business keys of cases (CMMN) come from the history, which knows several at once
  const caseIds = [...new Set(tasks.map((task) => task.caseInstanceId).filter((id): id is string => !!id))];
  if (caseIds.length > 0) {
    const keys = await getCaseBusinessKeys(caseIds, options.signal).catch(() => new Map<string, string | null>());
    for (const task of tasks) {
      if (task.caseInstanceId && !task.processInstanceId) task.businessKey = keys.get(task.caseInstanceId) ?? null;
    }
  }
  return { count: result.count, tasks };
}

/** Types the engine can sort tasks by; values of other types are left out of the order. */
export const SORTABLE_TYPES = ['String', 'Integer', 'Long', 'Short', 'Double', 'Boolean', 'Date'] as const;
export type SortableType = (typeof SORTABLE_TYPES)[number];

/**
 * The type a variable is mostly stored with, from a sample of its values;
 * `null` if no value can be read or its type cannot be sorted by.
 */
export async function detectVariableType(name: string, signal?: AbortSignal): Promise<SortableType | null> {
  const sample = await request<{ type: string }[]>(`${engineApi()}/variable-instance`, {
    method: 'POST',
    query: { maxResults: 50, deserializeValues: false },
    json: { variableName: name },
    signal,
  });
  const counts = new Map<SortableType, number>();
  for (const { type } of sample) {
    const known = SORTABLE_TYPES.find((entry) => entry.toLowerCase() === type?.toLowerCase());
    if (known) counts.set(known, (counts.get(known) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;
}

// --- tasks -----------------------------------------------------------------

/** Tasks matching a query of the task resource, without the embedded data of a filter. */
export function queryTasks(
  query: TaskQuery,
  options: { firstResult?: number; maxResults: number; sorting: TaskSorting[]; signal?: AbortSignal },
): Promise<Task[]> {
  return request<Task[]>(`${engineApi()}/task`, {
    method: 'POST',
    query: { firstResult: options.firstResult ?? 0, maxResults: options.maxResults },
    json: { ...tenantFilterBody(), ...query, sorting: options.sorting },
    signal: options.signal,
  });
}

export function getTask(taskId: string): Promise<Task> {
  return request<Task>(`${engineApi()}/task/${seg(taskId)}`);
}

export function claimTask(taskId: string, userId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/claim`, {
    method: 'POST',
    json: { userId },
  });
}

export function unclaimTask(taskId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/unclaim`, { method: 'POST' });
}

export function setTaskAssignee(taskId: string, userId: string | null): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/assignee`, {
    method: 'POST',
    json: { userId },
  });
}

/** Changes properties of a task; the resource expects the complete task. */
export async function updateTask(
  taskId: string,
  changes: Partial<Pick<Task, 'due' | 'followUp' | 'priority' | 'description' | 'name'>>,
): Promise<void> {
  const task = await getTask(taskId);
  await request<void>(`${engineApi()}/task/${seg(taskId)}`, {
    method: 'PUT',
    json: {
      name: task.name,
      description: task.description,
      assignee: task.assignee,
      owner: task.owner,
      delegationState: task.delegationState,
      due: task.due,
      followUp: task.followUp,
      priority: task.priority,
      parentTaskId: task.parentTaskId,
      caseInstanceId: task.caseInstanceId,
      tenantId: task.tenantId,
      ...changes,
    },
  });
}

/** Hands the task to a user who resolves it and returns it to the current assignee. */
export function delegateTask(taskId: string, userId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/delegate`, {
    method: 'POST',
    json: { userId },
  });
}

/** A user or group offered while an id is typed. */
export interface IdentitySuggestion {
  id: string;
  name: string;
}

const SUGGESTIONS = 8;

/**
 * Users or groups whose id or name contains a term, for pickers. The engine
 * shows only what the user may read; if it refuses, nothing is suggested and
 * an id can still be typed.
 */
export async function suggestIdentities(
  kind: 'user' | 'group',
  term: string,
  signal?: AbortSignal,
): Promise<IdentitySuggestion[]> {
  const exact = term.trim();
  const like = `%${exact}%`;
  // the REST API has no pattern for ids: an id is matched exactly, names by pattern
  const queries: Record<string, QueryValue>[] =
    kind === 'user'
      ? [{ id: exact }, { firstNameLike: like }, { lastNameLike: like }]
      : [{ id: exact }, { nameLike: like }];
  try {
    const pages = await Promise.all(
      queries.map((query) =>
        request<{ id: string; name?: string | null; firstName?: string | null; lastName?: string | null }[]>(
          `${engineApi()}/${kind}`,
          { query: { ...query, maxResults: SUGGESTIONS }, signal },
        ),
      ),
    );
    const found = new Map<string, IdentitySuggestion>();
    for (const entry of pages.flat()) {
      const name =
        kind === 'user'
          ? [entry.firstName, entry.lastName].filter(Boolean).join(' ')
          : (entry.name ?? '');
      found.set(entry.id, { id: entry.id, name });
    }
    return [...found.values()].slice(0, SUGGESTIONS);
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound') || isApiError(error, 'client')) {
      return [];
    }
    throw error;
  }
}

export async function getCandidateGroups(taskId: string): Promise<string[]> {
  const links = await request<IdentityLink[]>(`${engineApi()}/task/${seg(taskId)}/identity-links`, {
    query: { type: 'candidate' },
  });
  return links.flatMap((link) => (link.groupId ? [link.groupId] : []));
}

export function addCandidateGroup(taskId: string, groupId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/identity-links`, {
    method: 'POST',
    json: { groupId, type: 'candidate' },
  });
}

export function removeCandidateGroup(taskId: string, groupId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/identity-links/delete`, {
    method: 'POST',
    json: { groupId, type: 'candidate' },
  });
}

export function getTaskComments(taskId: string): Promise<TaskComment[]> {
  return request<TaskComment[]>(`${engineApi()}/task/${seg(taskId)}/comment`);
}

export function addTaskComment(taskId: string, message: string): Promise<TaskComment> {
  return request<TaskComment>(`${engineApi()}/task/${seg(taskId)}/comment/create`, {
    method: 'POST',
    json: { message },
  });
}

/** A comment on a running process instance as a whole; the engine checks the right to update it. */
export function addInstanceComment(processInstanceId: string, message: string): Promise<TaskComment> {
  return request<TaskComment>(`${engineApi()}/process-instance/${seg(processInstanceId)}/comment/create`, {
    method: 'POST',
    json: { message },
  });
}

/** A file or link attached to a task. */
export interface TaskAttachment {
  id: string;
  name: string | null;
  description: string | null;
  type: string | null;
  /** set for a link, empty for a file */
  url: string | null;
  createTime?: string | null;
  taskId: string;
}

export function getTaskAttachments(taskId: string): Promise<TaskAttachment[]> {
  return request<TaskAttachment[]>(`${engineApi()}/task/${seg(taskId)}/attachment`);
}

/** Link to the content of an attached file; the browser downloads it. */
export function attachmentDataUrl(taskId: string, attachmentId: string): string {
  return apiUrl(`${engineApi()}/task/${seg(taskId)}/attachment/${seg(attachmentId)}/data`);
}

/** Attaches a file or a link to a task. */
export function addTaskAttachment(
  taskId: string,
  attachment: { name: string; description: string } & ({ file: File } | { url: string }),
): Promise<TaskAttachment> {
  const parts = new FormData();
  parts.append('attachment-name', attachment.name);
  if (attachment.description) {
    parts.append('attachment-description', attachment.description);
  }
  if ('file' in attachment) {
    parts.append('attachment-type', attachment.file.type || 'application/octet-stream');
    parts.append('content', attachment.file, attachment.file.name);
  } else {
    parts.append('attachment-type', 'url');
    parts.append('url', attachment.url);
  }
  return request<TaskAttachment>(`${engineApi()}/task/${seg(taskId)}/attachment/create`, {
    method: 'POST',
    multipart: parts,
  });
}

export function deleteTaskAttachment(taskId: string, attachmentId: string): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/attachment/${seg(attachmentId)}`, {
    method: 'DELETE',
  });
}

export async function getTaskOperations(taskId: string): Promise<UserOperation[]> {
  try {
    return await request<UserOperation[]>(`${engineApi()}/history/user-operation`, {
      query: { taskId, sortBy: 'timestamp', sortOrder: 'desc', maxResults: 200 },
    });
  } catch (error) {
    // history may be switched off or not readable for the user
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) {
      return [];
    }
    throw error;
  }
}

// --- forms -----------------------------------------------------------------

function targetPath(target: FormTarget): string {
  return target.type === 'task'
    ? `${engineApi()}/task/${seg(target.taskId)}`
    : `${engineApi()}/process-definition/${seg(target.processDefinitionId)}`;
}

export function getFormInfo(target: FormTarget): Promise<FormInfo> {
  return request<FormInfo>(`${targetPath(target)}/${target.type === 'task' ? 'form' : 'startForm'}`);
}

export function getFormVariables(target: FormTarget): Promise<VariableMap> {
  return request<VariableMap>(`${targetPath(target)}/form-variables`, {
    query: { deserializeValues: false },
  });
}

/** The deployed form resource: a form schema (object) or markup (string). */
export function getDeployedForm(target: FormTarget): Promise<unknown> {
  return request<unknown>(
    `${targetPath(target)}/${target.type === 'task' ? 'deployed-form' : 'deployed-start-form'}`,
    { accept: 'application/json, */*;q=0.5' },
  );
}

/** The markup the engine generates for the form fields of a task or start event. */
export function getRenderedForm(target: FormTarget): Promise<string> {
  return request<string>(`${targetPath(target)}/rendered-form`, {
    accept: 'application/xhtml+xml, text/html;q=0.9, */*;q=0.5',
  });
}

export function submitTaskForm(taskId: string, variables: VariableMap): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/submit-form`, {
    method: 'POST',
    json: { variables },
  });
}

/**
 * Writes variables of a task without completing it, where completing would
 * write them: a variable of the task itself stays local, any other one goes
 * to the scope that has it, or to the process instance.
 */
export function saveTaskVariables(taskId: string, variables: VariableMap): Promise<void> {
  return request<void>(`${engineApi()}/task/${seg(taskId)}/variables`, {
    method: 'POST',
    json: { modifications: variables },
  });
}

export function submitStartForm(
  processDefinitionId: string,
  variables: VariableMap,
  businessKey?: string,
): Promise<ProcessInstanceRef> {
  return request<ProcessInstanceRef>(
    `${engineApi()}/process-definition/${seg(processDefinitionId)}/submit-form`,
    { method: 'POST', json: { variables, businessKey: businessKey || undefined } },
  );
}

// --- process definitions ---------------------------------------------------

/**
 * Whether the user may create process instances at all. Starting needs it
 * besides "create instance" on the definition, which the query of startable
 * definitions checks alone. Without an answer the engine decides at start.
 */
export async function canCreateInstances(signal?: AbortSignal): Promise<boolean> {
  try {
    const answer = await request<{ authorized: boolean }>(`${engineApi()}/authorization/check`, {
      // 8: process instance
      query: { permissionName: 'CREATE', resourceName: 'ProcessInstance', resourceType: 8 },
      signal,
    });
    return answer.authorized !== false;
  } catch (error) {
    if (signal?.aborted) throw error;
    return true;
  }
}

export async function getStartableDefinitions(options: {
  firstResult: number;
  maxResults: number;
  search?: string;
  signal?: AbortSignal;
}): Promise<{ count: number; definitions: ProcessDefinition[] }> {
  const query: Record<string, QueryValue> = {
    latest: true,
    active: true,
    startableInTasklist: true,
    startablePermissionCheck: true,
    ...tenantFilter(),
    nameLike: options.search ? `%${options.search}%` : undefined,
  };
  const [count, definitions] = await Promise.all([
    request<{ count: number }>(`${engineApi()}/process-definition/count`, {
      query,
      signal: options.signal,
    }),
    request<ProcessDefinition[]>(`${engineApi()}/process-definition`, {
      query: {
        ...query,
        sortBy: 'name',
        sortOrder: 'asc',
        firstResult: options.firstResult,
        maxResults: options.maxResults,
      },
      signal: options.signal,
    }),
  ]);
  return { count: count.count, definitions };
}

export function getProcessDefinition(id: string): Promise<ProcessDefinition> {
  return request<ProcessDefinition>(`${engineApi()}/process-definition/${seg(id)}`);
}

export async function getProcessXml(processDefinitionId: string): Promise<string> {
  const result = await request<{ bpmn20Xml: string }>(
    `${engineApi()}/process-definition/${seg(processDefinitionId)}/xml`,
  );
  return result.bpmn20Xml;
}

/** The first open task of an instance the user may see, e.g. right after starting it. */
export async function getFirstTaskOfInstance(processInstanceId: string, signal?: AbortSignal): Promise<Task | null> {
  const tasks = await request<Task[]>(`${engineApi()}/task`, {
    query: { processInstanceId, sortBy: 'created', sortOrder: 'asc', maxResults: 1 },
    signal,
  });
  return tasks[0] ?? null;
}

/** The processes a user may see, by key, in their newest version; for choices such as the card fields. */
export async function getProcessKeys(signal?: AbortSignal): Promise<{ key: string; name: string | null }[]> {
  const definitions = await request<{ key: string; name: string | null }[]>(`${engineApi()}/process-definition`, {
    query: { latestVersion: true, sortBy: 'name', sortOrder: 'asc', maxResults: 500, ...tenantFilter() },
    signal,
  });
  const seen = new Set<string>();
  return definitions.filter((definition) => !seen.has(definition.key) && !!seen.add(definition.key));
}
