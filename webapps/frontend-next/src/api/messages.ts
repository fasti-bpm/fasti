/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { request, seg } from './http';
import type { VariableMap } from './types';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

/** What a message is sent with; empty parts are left out of the request. */
export interface MessageDelivery {
  messageName: string;
  businessKey?: string;
  processInstanceId?: string;
  tenantId?: string;
  /** only receivers that belong to no tenant */
  withoutTenantId?: boolean;
  /** deliver to every waiting receiver instead of exactly one */
  all?: boolean;
  correlationKeys?: VariableMap;
  localCorrelationKeys?: VariableMap;
  processVariables?: VariableMap;
}

/** A receiver of a delivered message: a waiting execution or a started instance. */
export interface MessageReceiver {
  kind: 'execution' | 'started';
  processInstanceId: string;
  executionId: string | null;
  businessKey: string | null;
  processDefinitionId: string | null;
}

interface CorrelationResultDto {
  resultType: 'Execution' | 'ProcessDefinition';
  execution?: { id: string; processInstanceId: string } | null;
  processInstance?: {
    id: string;
    definitionId?: string | null;
    businessKey?: string | null;
  } | null;
}

function nonEmpty(map: VariableMap | undefined): VariableMap | undefined {
  return map && Object.keys(map).length ? map : undefined;
}

/** The request body of the message resource of the engine. */
export function messageBody(delivery: MessageDelivery): Record<string, unknown> {
  const body: Record<string, unknown> = {
    messageName: delivery.messageName.trim(),
    resultEnabled: true,
  };
  if (delivery.businessKey?.trim()) body.businessKey = delivery.businessKey.trim();
  // the engine rejects a tenant together with an instance; the instance already decides the tenant
  if (delivery.processInstanceId?.trim()) body.processInstanceId = delivery.processInstanceId.trim();
  else if (delivery.withoutTenantId) body.withoutTenantId = true;
  else if (delivery.tenantId?.trim()) body.tenantId = delivery.tenantId.trim();
  if (delivery.all) body.all = true;
  const keys = nonEmpty(delivery.correlationKeys);
  if (keys) body.correlationKeys = keys;
  const local = nonEmpty(delivery.localCorrelationKeys);
  if (local) body.localCorrelationKeys = local;
  const variables = nonEmpty(delivery.processVariables);
  if (variables) body.processVariables = variables;
  return body;
}

export function toReceivers(results: CorrelationResultDto[]): MessageReceiver[] {
  return results.map((result) => {
    const started = result.resultType === 'ProcessDefinition';
    return {
      kind: started ? 'started' : 'execution',
      processInstanceId: result.processInstance?.id ?? result.execution?.processInstanceId ?? '',
      executionId: result.execution?.id ?? null,
      businessKey: result.processInstance?.businessKey ?? null,
      processDefinitionId: result.processInstance?.definitionId ?? null,
    };
  });
}

/** Delivers a message and tells who received it. */
export async function deliverMessage(delivery: MessageDelivery): Promise<MessageReceiver[]> {
  const results = await request<CorrelationResultDto[]>(`${engineApi()}/message`, {
    method: 'POST',
    json: messageBody(delivery),
  });
  return toReceivers(results ?? []);
}

interface SubscriptionDto {
  eventName: string | null;
  activityId: string | null;
}

/** Names of the messages that are waited for, of one instance or of the engine. */
export async function getAwaitedMessages(processInstanceId?: string): Promise<{ name: string; activityId: string | null }[]> {
  const subscriptions = await request<SubscriptionDto[]>(`${engineApi()}/event-subscription`, {
    query: { eventType: 'message', processInstanceId, maxResults: 500 },
  });
  const seen = new Map<string, string | null>();
  for (const subscription of subscriptions ?? []) {
    if (subscription.eventName && !seen.has(subscription.eventName)) {
      seen.set(subscription.eventName, subscription.activityId);
    }
  }
  return [...seen].map(([name, activityId]) => ({ name, activityId })).sort((a, b) => a.name.localeCompare(b.name));
}
