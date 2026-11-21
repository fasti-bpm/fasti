/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * The immutable audit trail (`/audit` of the REST API of Fasti): records the engine writes next
 * to its history, chained by SHA-256 hashes so that a changed, removed or inserted record is
 * found when the trail is verified. Reading needs an administrators group or `READ` on `SYSTEM`.
 */
import { env } from '../env';
import { isApiError, request, requestBlob, seg, type QueryValue } from './http';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

export const AUDIT_TYPES = ['process', 'task', 'variable', 'deployment', 'operation', 'incident', 'decision', 'activity', 'audit'] as const;
export type AuditType = (typeof AUDIT_TYPES)[number];

export interface AuditStatus {
  enabled: boolean;
  nodeId: string | null;
  chains: number;
  types: string[] | null;
  variableValues: string | null;
  maskedVariables: string[] | null;
  signed: boolean;
  keyId: string | null;
  /** ids of earlier keys whose signatures are still accepted; `unsigned` for records without signature */
  retiredKeyIds?: string[] | null;
  checkpointInterval: number;
  retentionDays: number;
  historyLevel: string | null;
}

export interface AuditEvent {
  id: string;
  chainId: string;
  sequence: number;
  timestamp: string;
  time: number;
  type: string;
  action: string | null;
  /** the authenticated user; null for the engine itself */
  actor: string | null;
  engineName: string | null;
  tenantId: string | null;
  processDefinitionKey: string | null;
  processDefinitionId: string | null;
  processInstanceId: string | null;
  businessKey: string | null;
  entityType: string | null;
  entityId: string | null;
  entityName: string | null;
  /** JSON text, exactly as hashed */
  details: string | null;
  previousHash: string;
  hash: string;
}

export interface AuditQuery {
  processInstanceId?: string;
  businessKey?: string;
  /** a user id, or `system` for the engine itself */
  actor?: string;
  type?: string;
  action?: string;
  from?: string;
  to?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface AuditBreak {
  chainId: string;
  sequence: number;
  recordId: string | null;
  reason: string;
  message: string;
}

export interface AuditChain {
  chainId: string;
  intact: boolean;
  /** intact, and every checkpoint and purge record was checked with a signing key of the engine */
  authenticated?: boolean;
  /** the last record a checked signature covers; 0 if not authenticated or before the first checkpoint */
  authenticatedThrough?: number;
  records: number;
  firstSequence: number;
  lastSequence: number;
  lastHash: string | null;
  checkpoints: number;
  checkpointsVerified: number;
  purgedThrough: number;
  /** whether a checked signature attests the purge that removed the start of the chain */
  purgeAttested?: boolean;
  firstBreak: AuditBreak | null;
}

export interface AuditVerification {
  /** nothing that can be checked with the settings of the engine failed */
  intact: boolean;
  /** intact, and every signature a chain needs was checked with a key of the engine; false without a key */
  authenticated?: boolean;
  verifiedAt: string;
  records: number;
  chains: AuditChain[];
  firstBreak: AuditBreak | null;
}

/** The query parameters of a query, without empty values. */
export function auditParams(query: AuditQuery): Record<string, QueryValue> {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined && value !== ''));
}

/** The settings of the audit trail; `enabled: false` also for a server that does not know it. */
export async function getAuditStatus(signal?: AbortSignal): Promise<AuditStatus> {
  try {
    return await request<AuditStatus>(`${engineApi()}/audit`, { signal });
  } catch (error) {
    if (isApiError(error, 'notFound')) {
      return {
        enabled: false,
        nodeId: null,
        chains: 0,
        types: null,
        variableValues: null,
        maskedVariables: null,
        signed: false,
        keyId: null,
        checkpointInterval: 0,
        retentionDays: 0,
        historyLevel: null,
      };
    }
    throw error;
  }
}

export async function getAuditEvents(
  query: AuditQuery,
  paging: { firstResult: number; maxResults: number },
  signal?: AbortSignal,
): Promise<{ items: AuditEvent[]; count: number }> {
  const params = auditParams(query);
  const [items, count] = await Promise.all([
    request<AuditEvent[]>(`${engineApi()}/audit/event`, { query: { ...params, ...paging }, signal }),
    request<{ count: number }>(`${engineApi()}/audit/event/count`, { query: params, signal }),
  ]);
  return { items, count: count.count };
}

/**
 * The records a query selects as a file of the server (recorded there as an export). A POST, as it
 * writes that record: the request carries the CSRF token of the session like every other change.
 */
export async function exportAudit(query: AuditQuery, format: 'csv' | 'json'): Promise<{ blob: Blob; filename: string }> {
  const params = auditParams({ ...query, sortOrder: undefined });
  const { blob, filename } = await requestBlob(`${engineApi()}/audit/event/export`, {
    method: 'POST',
    query: { ...params, format },
    accept: format === 'csv' ? 'text/csv' : 'application/json',
  });
  return { blob, filename: filename ?? `audit-trail.${format}` };
}

export async function verifyAudit(chainId?: string): Promise<AuditVerification> {
  return request<AuditVerification>(`${engineApi()}/audit/verify`, { method: 'POST', query: chainId ? { chainId } : undefined });
}

/** The details of a record as an object, or null if they are missing or not JSON. */
export function auditDetails(event: Pick<AuditEvent, 'details'>): Record<string, unknown> | null {
  if (!event.details) {
    return null;
  }
  try {
    const value: unknown = JSON.parse(event.details);
    return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
