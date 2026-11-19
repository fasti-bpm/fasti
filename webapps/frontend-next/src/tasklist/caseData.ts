/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { isApiError, request, seg } from '../api/http';
import { parseEngineDate } from '../lib/dates';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

/** at most this many instances of a call chain and tasks per instance are read */
export const CASE_LIMIT = { instances: 20, tasks: 60 };

export interface CaseInstance {
  id: string;
  processDefinitionName: string | null;
  processDefinitionKey: string | null;
  superProcessInstanceId: string | null;
}

export interface CaseTask {
  id: string;
  name: string | null;
  taskDefinitionKey: string | null;
  processInstanceId: string | null;
  assignee: string | null;
  startTime: string | null;
  endTime: string | null;
  /** `completed`, `deleted` or the reason given when the task was removed */
  deleteReason: string | null;
}

export interface CaseComment {
  id: string;
  userId: string | null;
  time: string;
  message: string;
  taskId: string | null;
  processInstanceId?: string | null;
}

export type CaseEntryKind = 'comment' | 'created' | 'completed' | 'canceled';

export interface CaseEntry {
  id: string;
  kind: CaseEntryKind;
  time: string;
  userId: string | null;
  /** the step the entry belongs to; empty for a comment on the whole case */
  taskId: string | null;
  taskName: string | null;
  /** set when the entry belongs to another process of the call chain */
  processName: string | null;
  message: string | null;
}

export interface CaseData {
  instances: CaseInstance[];
  tasks: CaseTask[];
  comments: CaseComment[];
  /** whether some tasks or instances were left out */
  truncated: boolean;
}

async function optional<T>(promise: Promise<T>, fallback: T): Promise<T> {
  try {
    return await promise;
  } catch (error) {
    if (isApiError(error, 'forbidden') || isApiError(error, 'notFound')) return fallback;
    throw error;
  }
}

/**
 * Everything that happened in a case: the tasks and comments of the
 * instance, the instances it called and the one that called it, up to the
 * root of the chain. `null` when the history cannot be read.
 */
export async function getCaseData(processInstanceId: string, signal?: AbortSignal): Promise<CaseData | null> {
  const api = engineApi();
  const own = await optional(
    request<{ id: string; rootProcessInstanceId?: string | null }>(`${api}/history/process-instance/${seg(processInstanceId)}`, { signal }),
    null,
  );
  if (!own) return null;
  const root = own.rootProcessInstanceId || own.id;
  const chain = await request<CaseInstance[]>(`${api}/history/process-instance`, {
    query: { rootProcessInstanceId: root, sortBy: 'startTime', sortOrder: 'asc', maxResults: CASE_LIMIT.instances + 1 },
    signal,
  });
  // the own instance is always read, even far down a long chain
  const instances = chain.slice(0, CASE_LIMIT.instances);
  if (!instances.some((instance) => instance.id === processInstanceId)) {
    instances.push({ id: processInstanceId, processDefinitionName: null, processDefinitionKey: null, superProcessInstanceId: null });
  }
  let truncated = chain.length > CASE_LIMIT.instances;
  const perInstance = await Promise.all(
    instances.map(async (instance) => {
      const [tasks, comments] = await Promise.all([
        request<CaseTask[]>(`${api}/history/task`, {
          query: {
            processInstanceId: instance.id,
            sortBy: 'startTime',
            sortOrder: 'desc',
            maxResults: CASE_LIMIT.tasks + 1,
          },
          signal,
        }),
        optional(request<CaseComment[]>(`${api}/process-instance/${seg(instance.id)}/comment`, { signal }), []),
      ]);
      if (tasks.length > CASE_LIMIT.tasks) truncated = true;
      return { tasks: tasks.slice(0, CASE_LIMIT.tasks), comments };
    }),
  );
  const tasks = perInstance.flatMap((entry) => entry.tasks);
  const taskComments = await Promise.all(
    tasks.map((task) => optional(request<CaseComment[]>(`${api}/task/${seg(task.id)}/comment`, { signal }), [])),
  );
  return {
    instances,
    tasks,
    comments: [...perInstance.flatMap((entry) => entry.comments), ...taskComments.flat()],
    truncated,
  };
}

const time = (value: string | null | undefined): number => parseEngineDate(value)?.getTime() ?? 0;

/**
 * The entries of a case, newest first: who commented, which steps started
 * and ended. A comment listed both for its task and for the instance is
 * shown once; the process is named only for steps of another process.
 */
export function caseEntries(data: CaseData, ownInstanceId: string | null): CaseEntry[] {
  const tasks = new Map(data.tasks.map((task) => [task.id, task]));
  const instances = new Map(data.instances.map((instance) => [instance.id, instance]));
  const processOf = (instanceId: string | null | undefined): string | null => {
    if (!instanceId || instanceId === ownInstanceId) return null;
    const instance = instances.get(instanceId);
    return instance ? (instance.processDefinitionName ?? instance.processDefinitionKey ?? instanceId) : null;
  };
  const entries: CaseEntry[] = [];
  const seen = new Set<string>();
  for (const comment of data.comments) {
    if (seen.has(comment.id)) continue;
    seen.add(comment.id);
    const task = comment.taskId ? tasks.get(comment.taskId) : undefined;
    entries.push({
      id: `c-${comment.id}`,
      kind: 'comment',
      time: comment.time,
      userId: comment.userId,
      taskId: comment.taskId,
      taskName: task ? (task.name ?? task.taskDefinitionKey) : null,
      processName: processOf(task?.processInstanceId ?? comment.processInstanceId),
      message: comment.message,
    });
  }
  for (const task of data.tasks) {
    const name = task.name ?? task.taskDefinitionKey;
    const processName = processOf(task.processInstanceId);
    if (task.startTime) {
      entries.push({ id: `s-${task.id}`, kind: 'created', time: task.startTime, userId: null, taskId: task.id, taskName: name, processName, message: null });
    }
    if (task.endTime) {
      const completed = !task.deleteReason || task.deleteReason === 'completed';
      entries.push({
        id: `e-${task.id}`,
        kind: completed ? 'completed' : 'canceled',
        time: task.endTime,
        userId: task.assignee,
        taskId: task.id,
        taskName: name,
        processName,
        message: completed || task.deleteReason === 'deleted' ? null : task.deleteReason,
      });
    }
  }
  return entries.sort((a, b) => time(b.time) - time(a.time) || a.id.localeCompare(b.id));
}
