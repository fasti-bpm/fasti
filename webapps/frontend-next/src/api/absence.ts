/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Absences and substitutes (`/user/{id}/absence` of the REST API of Fasti). While a user is
 * absent, the engine gives the tasks created for or assigned to them to their substitute and
 * marks each such task with the local variable `fasti_absentAssignee`.
 */
import { env } from '../env';
import { isApiError, request, seg } from './http';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;
const absenceApi = (userId: string): string => `${engineApi()}/user/${seg(userId)}/absence`;

/** The local task variable naming the absent user a task was meant for. */
export const ABSENT_ASSIGNEE_VARIABLE = 'fasti_absentAssignee';

export type AbsenceStatus = 'none' | 'upcoming' | 'active' | 'ended';

export interface Absence {
  userId: string;
  /** whether absences can be set on the engine at all */
  enabled: boolean;
  unavailableReason: string | null;
  status: AbsenceStatus;
  from: string | null;
  until: string | null;
  substituteId: string | null;
  note: string | null;
  /** while active: who gets the tasks now (a substitute of an absent substitute, too) */
  effectiveSubstituteId: string | null;
}

export interface AbsenceInput {
  /** engine date; `null` for now */
  from: string | null;
  until: string;
  substituteId: string;
  note: string | null;
}

export interface Handover {
  substituteId: string;
  handedOver: number;
  remaining: number;
}

export function getAbsence(userId: string, signal?: AbortSignal): Promise<Absence> {
  return request<Absence>(absenceApi(userId), { signal });
}

export function setAbsence(userId: string, absence: AbsenceInput): Promise<Absence> {
  return request<Absence>(absenceApi(userId), { method: 'PUT', json: absence });
}

export function clearAbsence(userId: string): Promise<void> {
  return request<void>(absenceApi(userId), { method: 'DELETE' });
}

export function handOverTasks(userId: string): Promise<Handover> {
  return request<Handover>(`${absenceApi(userId)}/handover`, { method: 'POST' });
}

/** The absences active now whose tasks go to the user. */
export async function getSubstitutedAbsences(userId: string, signal?: AbortSignal): Promise<Absence[]> {
  try {
    return await request<Absence[]>(`${absenceApi(userId)}/substituting`, { signal });
  } catch (error) {
    // an engine without the endpoint (an older server) has nobody to substitute for
    if (isApiError(error, 'notFound')) {
      return [];
    }
    throw error;
  }
}

/** How many tasks are assigned to the user, to offer the handover only when there is something to hand over. */
export function countAssignedTasks(userId: string, signal?: AbortSignal): Promise<number> {
  return request<{ count: number }>(`${engineApi()}/task/count`, { query: { assignee: userId }, signal }).then(
    (result) => result.count,
  );
}

/** The absent users that tasks are held for (task id to user id), for the tasks given. */
export async function getSubstitutionMarks(taskIds: string[], signal?: AbortSignal): Promise<Map<string, string>> {
  const marks = new Map<string, string>();
  if (taskIds.length === 0) {
    return marks;
  }
  const variables = await request<{ taskId: string | null; value: unknown }[]>(`${engineApi()}/variable-instance`, {
    query: {
      variableName: ABSENT_ASSIGNEE_VARIABLE,
      taskIdIn: taskIds.join(','),
      deserializeValues: false,
      maxResults: taskIds.length,
    },
    signal,
  });
  for (const variable of variables) {
    if (variable.taskId && typeof variable.value === 'string') {
      marks.set(variable.taskId, variable.value);
    }
  }
  return marks;
}

/** The absent user the task is held for, or `null`. */
export async function getSubstitutionMark(taskId: string, signal?: AbortSignal): Promise<string | null> {
  try {
    const variable = await request<{ value: unknown }>(
      `${engineApi()}/task/${seg(taskId)}/localVariables/${seg(ABSENT_ASSIGNEE_VARIABLE)}`,
      { query: { deserializeValue: false }, signal },
    );
    return typeof variable.value === 'string' ? variable.value : null;
  } catch (error) {
    if (isApiError(error, 'notFound') || isApiError(error, 'forbidden')) {
      return null;
    }
    throw error;
  }
}
