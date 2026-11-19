/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import {
  listenerEvents,
  readFormFields,
  readListeners,
  readParameters,
  replace,
  writeFormFields,
  writeListeners,
  writeParameters,
  type FormField,
  type Generic,
  type Listener,
} from './extensions';

const create = (name: string, properties: Record<string, unknown>): Generic => ({ $type: `camunda:${name}`, ...properties });

const script: Generic = { $type: 'camunda:script', scriptFormat: 'js', $body: '1' };
const values: Generic[] = [
  { $type: 'camunda:properties' },
  {
    $type: 'camunda:inputOutput',
    $children: [
      { $type: 'camunda:inputParameter', name: 'a', $body: '${x}' },
      { $type: 'camunda:outputParameter', name: 'b', $children: [script] },
    ],
  },
  { $type: 'camunda:executionListener', event: 'start', class: 'a.B' },
  { $type: 'camunda:taskListener', event: 'timeout', id: 't1', expression: '${t}', $children: [{ $type: 'camunda:timerEventDefinition' }] },
];

describe('input/output', () => {
  it('reads plain and complex parameters', () => {
    const { inputs, outputs } = readParameters(values);
    expect(inputs).toMatchObject([{ name: 'a', value: '${x}', complex: false }]);
    expect(outputs).toMatchObject([{ name: 'b', complex: true }]);
  });

  it('writes the parameters in place and keeps scripts', () => {
    const { inputs, outputs } = readParameters(values);
    const next = writeParameters(values, [...inputs, { name: 'c', value: '3', complex: false }], outputs, create);
    expect(next.map((value) => value.$type)).toEqual(values.map((value) => value.$type));
    expect(next[1]?.$children).toEqual([
      { $type: 'camunda:inputParameter', name: 'a', $body: '${x}' },
      { $type: 'camunda:inputParameter', name: 'c', $body: '3' },
      { $type: 'camunda:outputParameter', name: 'b', $children: [script] },
    ]);
  });

  it('drops the mapping when no parameter is left', () => {
    expect(writeParameters(values, [], [], create).some((value) => value.$type === 'camunda:inputOutput')).toBe(false);
  });
});

describe('listeners', () => {
  it('reads the kind of implementation', () => {
    expect(readListeners(values, 'executionListener')).toMatchObject([{ event: 'start', type: 'class', value: 'a.B' }]);
    expect(readListeners(values, 'taskListener')).toMatchObject([{ event: 'timeout', type: 'expression', complex: true }]);
  });

  it('keeps the id and the timer of a timeout listener', () => {
    const listener = readListeners(values, 'taskListener')[0] as Listener;
    const next = writeListeners(values, 'taskListener', [{ ...listener, value: '${u}' }], create);
    expect(next.find((value) => value.$type === 'camunda:taskListener')).toEqual({
      $type: 'camunda:taskListener',
      event: 'timeout',
      expression: '${u}',
      id: 't1',
      $children: [{ $type: 'camunda:timerEventDefinition' }],
    });
  });
});

describe('form fields', () => {
  it('adds a form to an element without one and keeps validation of existing fields', () => {
    const validation: Generic = { $type: 'camunda:validation', $children: [] };
    const withForm = writeFormFields(values, [{ id: 'amount', label: 'Amount', type: 'long', defaultValue: '' }], create);
    expect(readFormFields(withForm)).toMatchObject([{ id: 'amount', label: 'Amount', type: 'long' }]);

    const existing: Generic[] = [
      { $type: 'camunda:formData', $children: [{ $type: 'camunda:formField', id: 'a', type: 'string', $children: [validation] }] },
    ];
    const field = readFormFields(existing)[0] as FormField;
    const next = writeFormFields(existing, [{ ...field, label: 'A' }], create);
    expect(next[0]?.$children?.[0]).toEqual({ $type: 'camunda:formField', id: 'a', type: 'string', label: 'A', $children: [validation] });
  });
});

describe('form field attributes', () => {
  it('keeps the pattern of a date field', () => {
    const existing: Generic[] = [
      { $type: 'camunda:formData', $children: [{ $type: 'camunda:formField', id: 'd', type: 'date', datePattern: 'dd/MM/yyyy' }] },
    ];
    const field = readFormFields(existing)[0] as FormField;
    const next = writeFormFields(existing, [{ ...field, label: 'Ngày' }], create);
    expect(next[0]?.$children?.[0]).toEqual({ $type: 'camunda:formField', id: 'd', type: 'date', datePattern: 'dd/MM/yyyy', label: 'Ngày' });
  });
});

describe('listener events', () => {
  it('offers take on flows only', () => {
    expect(listenerEvents('executionListener', 'bpmn:SequenceFlow')).toEqual(['take']);
    expect(listenerEvents('executionListener', 'bpmn:UserTask')).toEqual(['start', 'end']);
  });
});

describe('replace', () => {
  it('keeps the place of the replaced element among the others', () => {
    const list: Generic[] = [{ $type: 'x:a' }, { $type: 'x:b' }, { $type: 'x:c' }];
    expect(replace(list, 'b', [{ $type: 'x:B' }]).map((value) => value.$type)).toEqual(['x:a', 'x:B', 'x:c']);
    expect(replace(list, 'd', [{ $type: 'x:d' }]).map((value) => value.$type)).toEqual(['x:a', 'x:b', 'x:c', 'x:d']);
  });
});
