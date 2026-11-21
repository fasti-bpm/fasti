/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Four-eyes control (`/task/{id}/four-eyes` of the REST API of Fasti). A user task with the
 * extension property `fasti:fourEyes` cannot be completed by a person who completed one of the
 * earlier steps it names; the engine refuses with `403` and the type `FourEyesViolationException`.
 */
import { env } from '../env';
import { isApiError, request, seg } from './http';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

/** The type of the error the engine answers a refused completion with. */
export const FOUR_EYES_VIOLATION = 'FourEyesViolationException';

export interface FourEyesStep {
  id: string;
  name: string | null;
}

export interface FourEyesStatus {
  taskId: string;
  /** whether the four-eyes plugin runs on the engine */
  enabled: boolean;
  /** whether the task has a rule */
  guarded: boolean;
  earlierSteps: FourEyesStep[];
  userId: string | null;
  userExcluded: boolean;
  userSteps: string[];
  assignee: string | null;
  assigneeExcluded: boolean;
  assigneeSteps: string[];
  /** whether the engine would refuse to let the signed-in user complete the task now */
  blocked: boolean;
}

/** The status of the task for the signed-in user, or `null` where the server cannot tell (an older server). */
export async function getFourEyesStatus(taskId: string, signal?: AbortSignal): Promise<FourEyesStatus | null> {
  try {
    return await request<FourEyesStatus>(`${engineApi()}/task/${seg(taskId)}/four-eyes`, { signal });
  } catch (error) {
    if (isApiError(error, 'notFound') || isApiError(error, 'forbidden')) {
      return null;
    }
    throw error;
  }
}

/** The names of the given steps, as the model has them (the id where a step has no name). */
export function stepNames(status: FourEyesStatus, ids: string[]): string[] {
  return ids.map((id) => status.earlierSteps.find((step) => step.id === id)?.name || id);
}
