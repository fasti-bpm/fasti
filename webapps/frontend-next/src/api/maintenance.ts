/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { parseEngineDate } from '../lib/dates';
import { isApiError, request, seg } from './http';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

// --- due dates of jobs -------------------------------------------------------

/** Sets the due date of a job; `null` makes it due at once. */
export function setJobDueDate(jobId: string, dueDate: string | null, cascade: boolean): Promise<void> {
  return request<void>(`${engineApi()}/job/${seg(jobId)}/duedate`, {
    method: 'PUT',
    json: { duedate: dueDate, cascade },
  });
}

/** Computes the due date again from the timer definition, from the creation time or from now. */
export function recalculateJobDueDate(jobId: string, fromCreation: boolean): Promise<void> {
  return request<void>(`${engineApi()}/job/${seg(jobId)}/duedate/recalculate`, {
    method: 'POST',
    query: { creationDateBased: fromCreation },
  });
}

// --- history cleanup -----------------------------------------------------------

export interface CleanupConfiguration {
  batchWindowStartTime: string | null;
  batchWindowEndTime: string | null;
  enabled: boolean;
}

export function getCleanupConfiguration(): Promise<CleanupConfiguration> {
  return request<CleanupConfiguration>(`${engineApi()}/history/cleanup/configuration`);
}

export interface CleanupJob {
  id: string;
  dueDate: string | null;
  retries: number;
  exceptionMessage: string | null;
  suspended: boolean;
}

/** The jobs of the cleanup; the engine answers 404 while there is none. */
export async function getCleanupJobs(): Promise<CleanupJob[]> {
  try {
    return (await request<CleanupJob[]>(`${engineApi()}/history/cleanup/jobs`)) ?? [];
  } catch (error) {
    if (isApiError(error, 'notFound')) return [];
    throw error;
  }
}

/** Starts the cleanup now, outside the batch window; returns the job that runs it. */
export function runCleanupNow(): Promise<CleanupJob> {
  return request<CleanupJob>(`${engineApi()}/history/cleanup`, {
    method: 'POST',
    query: { immediatelyDue: true },
  });
}

export interface CleanableCount {
  key: string;
  name: string | null;
  version: number;
  historyTimeToLive: number | null;
  finished: number;
  cleanable: number;
}

interface CleanableDto {
  processDefinitionKey: string;
  processDefinitionName: string | null;
  processDefinitionVersion: number;
  historyTimeToLive: number | null;
  finishedProcessInstanceCount: number;
  cleanableProcessInstanceCount: number;
}

/** How many finished process instances the cleanup may remove, per definition. */
export async function getCleanableProcesses(): Promise<CleanableCount[]> {
  // every page is read: the sum over the report is what a cleanup of the whole engine removes
  const PAGE = 200;
  const list: CleanableDto[] = [];
  for (let firstResult = 0; ; firstResult += PAGE) {
    const page =
      (await request<CleanableDto[]>(`${engineApi()}/history/process-definition/cleanable-process-instance-report`, {
        query: { sortBy: 'finished', sortOrder: 'desc', firstResult, maxResults: PAGE },
      })) ?? [];
    list.push(...page);
    if (page.length < PAGE) break;
  }
  return list.map((entry) => ({
    key: entry.processDefinitionKey,
    name: entry.processDefinitionName,
    version: entry.processDefinitionVersion,
    historyTimeToLive: entry.historyTimeToLive,
    finished: entry.finishedProcessInstanceCount,
    cleanable: entry.cleanableProcessInstanceCount,
  }));
}

/** The batch window as "22:00–06:00", or `null` if there is none. */
export function batchWindow(config: Pick<CleanupConfiguration, 'batchWindowStartTime' | 'batchWindowEndTime'>, format: (date: Date) => string): string | null {
  if (!config.batchWindowStartTime || !config.batchWindowEndTime) return null;
  const start = parseEngineDate(config.batchWindowStartTime);
  const end = parseEngineDate(config.batchWindowEndTime);
  if (!start || !end) return null;
  return `${format(start)}–${format(end)}`;
}
