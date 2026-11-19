/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { messageBody, toReceivers } from './messages';

describe('message delivery', () => {
  it('sends a tenant only without an instance, or no tenant at all', () => {
    expect(messageBody({ messageName: 'paid', processInstanceId: 'p1', tenantId: 't1', withoutTenantId: true })).toEqual({
      messageName: 'paid',
      resultEnabled: true,
      processInstanceId: 'p1',
    });
    expect(messageBody({ messageName: 'paid', tenantId: 't1' })).toMatchObject({ tenantId: 't1' });
    expect(messageBody({ messageName: 'paid', tenantId: 't1', withoutTenantId: true })).toEqual({
      messageName: 'paid',
      resultEnabled: true,
      withoutTenantId: true,
    });
  });

  it('leaves empty parts out and always asks for the result', () => {
    expect(messageBody({ messageName: ' paid ', businessKey: ' ', processInstanceId: '', correlationKeys: {} })).toEqual({
      messageName: 'paid',
      resultEnabled: true,
    });
    expect(
      messageBody({
        messageName: 'paid',
        businessKey: 'B-1',
        all: true,
        correlationKeys: { order: { value: 7, type: 'Long' } },
        processVariables: { amount: { value: 2.5, type: 'Double' } },
      }),
    ).toEqual({
      messageName: 'paid',
      resultEnabled: true,
      businessKey: 'B-1',
      all: true,
      correlationKeys: { order: { value: 7, type: 'Long' } },
      processVariables: { amount: { value: 2.5, type: 'Double' } },
    });
  });

  it('tells continued executions from started instances', () => {
    expect(
      toReceivers([
        { resultType: 'Execution', execution: { id: 'e1', processInstanceId: 'p1' }, processInstance: null },
        { resultType: 'ProcessDefinition', processInstance: { id: 'p2', definitionId: 'd:1', businessKey: 'B' } },
      ]),
    ).toEqual([
      { kind: 'execution', processInstanceId: 'p1', executionId: 'e1', businessKey: null, processDefinitionId: null },
      { kind: 'started', processInstanceId: 'p2', executionId: null, businessKey: 'B', processDefinitionId: 'd:1' },
    ]);
  });
});
