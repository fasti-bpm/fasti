/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { fieldsFor, implementationChange, implementationOf, isValidId } from './properties';

const attrs = (fields: ReturnType<typeof fieldsFor>) => fields.map((field) => field.attr);

describe('fieldsFor', () => {
  it('offers who works on a user task', () => {
    expect(attrs(fieldsFor('bpmn:UserTask'))).toEqual([
      'id',
      'name',
      'camunda:assignee',
      'camunda:candidateGroups',
      'camunda:candidateUsers',
      'camunda:formKey',
      'camunda:dueDate',
      'camunda:followUpDate',
      'camunda:priority',
      'camunda:asyncBefore',
      'documentation',
    ]);
  });

  it('offers what runs for a service task', () => {
    expect(attrs(fieldsFor('bpmn:ServiceTask'))).toContain('implementation');
  });

  it('offers what the engine needs of a process', () => {
    expect(attrs(fieldsFor('bpmn:Process'))).toEqual(
      expect.arrayContaining(['isExecutable', 'camunda:historyTimeToLive', 'camunda:versionTag']),
    );
  });

  it('offers a condition only on flows that can have one', () => {
    expect(attrs(fieldsFor('bpmn:SequenceFlow', { conditional: true }))).toContain('condition');
    expect(attrs(fieldsFor('bpmn:SequenceFlow'))).not.toContain('condition');
  });

  it('does not offer asynchronous continuation on events', () => {
    expect(attrs(fieldsFor('bpmn:EndEvent'))).toEqual(['id', 'name', 'documentation']);
  });
});

describe('implementation', () => {
  it('is read from the attributes', () => {
    expect(implementationOf({})).toBe('none');
    expect(implementationOf({ 'camunda:class': 'a.B' })).toBe('class');
    expect(implementationOf({ 'camunda:delegateExpression': '${b}' })).toBe('delegateExpression');
    expect(implementationOf({ 'camunda:type': 'external', 'camunda:topic': 'mail' })).toBe('external');
  });

  it('clears the other kinds when it changes', () => {
    expect(implementationChange('external', 'mail')).toEqual({
      'camunda:type': 'external',
      'camunda:class': undefined,
      'camunda:expression': undefined,
      'camunda:delegateExpression': undefined,
      'camunda:topic': 'mail',
    });
    expect(implementationChange('none', '')['camunda:class']).toBeUndefined();
  });
});

describe('isValidId', () => {
  it('accepts XML names without a colon', () => {
    expect(isValidId('Activity_1')).toBe(true);
    expect(isValidId('review-order.v2')).toBe(true);
    expect(isValidId('1task')).toBe(false);
    expect(isValidId('a b')).toBe(false);
    expect(isValidId('camunda:x')).toBe(false);
  });
});
