/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { BpmnModdle } from 'bpmn-moddle';
import { describe, expect, it } from 'vitest';
import {
  listenerEvents,
  readFormFields,
  readFourEyes,
  readListeners,
  readParameters,
  replace,
  userTasksOfProcess,
  writeFormFields,
  writeFourEyes,
  writeListeners,
  writeParameters,
  type FlowNode,
  type FormField,
  type Generic,
  type Listener,
} from './extensions';

const CAMUNDA = 'http://camunda.org/schema/1.0/bpmn';

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

describe('four-eyes rule', () => {
  const withRule: Generic[] = [
    {
      $type: 'camunda:properties',
      $children: [
        { $type: 'camunda:property', name: 'colour', value: 'red' },
        { $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft, review' },
        { $type: 'camunda:property', name: 'fasti:fourEyes', value: 'review,check' },
      ],
    },
    { $type: 'camunda:executionListener', event: 'start', class: 'a.B' },
  ];

  it('reads the named steps once each, from every property of that name', () => {
    expect(readFourEyes(withRule)).toEqual(['draft', 'review', 'check']);
    expect(readFourEyes(values)).toEqual([]);
  });

  it('writes one property and keeps the other properties', () => {
    const written = writeFourEyes(withRule, ['draft', 'approve'], create);
    expect(written[0]).toEqual({
      $type: 'camunda:properties',
      $children: [
        { $type: 'camunda:property', name: 'colour', value: 'red' },
        { $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft,approve' },
      ],
    });
    expect(written[1]).toBe(withRule[1]);
    expect(readFourEyes(written)).toEqual(['draft', 'approve']);
  });

  it('removes the property, and the properties once empty', () => {
    expect(writeFourEyes(withRule, [], create)[0]).toEqual({
      $type: 'camunda:properties',
      $children: [{ $type: 'camunda:property', name: 'colour', value: 'red' }],
    });
    const only: Generic[] = [{ $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'a' }] }];
    expect(writeFourEyes(only, [], create)).toEqual([]);
    expect(writeFourEyes([], ['a'], create)).toEqual([
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'a' }] },
    ]);
  });

  it('keeps the properties of every container of the element', () => {
    const two: Generic[] = [
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'a', value: '1' }] },
      { $type: 'camunda:inputOutput' },
      {
        $type: 'camunda:properties',
        $children: [
          { $type: 'camunda:property', name: 'b', value: '2' },
          { $type: 'camunda:property', name: 'fasti:fourEyes', value: 'old' },
        ],
      },
    ];
    const written = writeFourEyes(two, ['draft'], create);
    expect(written).toEqual([
      {
        $type: 'camunda:properties',
        $children: [
          { $type: 'camunda:property', name: 'a', value: '1' },
          { $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft' },
        ],
      },
      { $type: 'camunda:inputOutput' },
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'b', value: '2' }] },
    ]);
    expect(writeFourEyes(written, [], create)).toEqual([
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'a', value: '1' }] },
      { $type: 'camunda:inputOutput' },
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'b', value: '2' }] },
    ]);
  });

  it('leaves the properties of another vendor alone', () => {
    const foreign: Generic = {
      $type: 'acme:properties',
      $children: [
        { $type: 'acme:property', name: 'fasti:fourEyes', value: 'ghost' },
        { $type: 'acme:property', name: 'colour', value: 'red' },
      ],
    };
    expect(readFourEyes([foreign])).toEqual([]);

    const added = writeFourEyes([foreign], ['draft'], create);
    expect(added[0]).toBe(foreign);
    expect(added[1]).toEqual({
      $type: 'camunda:properties',
      $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft' }],
    });

    const mixed: Generic[] = [foreign, { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'a' }] }];
    expect(readFourEyes(mixed)).toEqual(['a']);
    expect(writeFourEyes(mixed, [], create)).toEqual([foreign]);
    // a foreign property inside the Camunda container is not the rule either
    const nested: Generic[] = [{ $type: 'camunda:properties', $children: [{ $type: 'acme:property', name: 'fasti:fourEyes', value: 'x' }] }];
    expect(readFourEyes(nested)).toEqual([]);
    expect(writeFourEyes(nested, [], create)).toEqual(nested);
  });

  it('goes by the namespace the editor resolved rather than by the prefix', () => {
    const ns = (uri: string) => ({ $descriptor: { ns: { uri } } });
    const aliased: Generic[] = [
      { $type: 'c7:properties', ...ns(CAMUNDA), $children: [{ $type: 'c7:property', ...ns(CAMUNDA), name: 'fasti:fourEyes', value: 'draft' }] },
    ];
    expect(readFourEyes(aliased)).toEqual(['draft']);
    const shadowed: Generic[] = [
      { $type: 'camunda:properties', ...ns('urn:other'), $children: [{ $type: 'camunda:property', ...ns('urn:other'), name: 'fasti:fourEyes', value: 'draft' }] },
    ];
    expect(readFourEyes(shadowed)).toEqual([]);
  });

  it('keeps a foreign properties element through a round trip of real XML', async () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="${CAMUNDA}" xmlns:acme="http://acme.example/bpmn" id="d" targetNamespace="http://fasti.dev/test">
  <bpmn:process id="p" isExecutable="true">
    <bpmn:userTask id="draft" />
    <bpmn:userTask id="approve">
      <bpmn:extensionElements>
        <acme:properties owner="acme">
          <acme:property name="fasti:fourEyes" value="ghost" />
          <acme:property name="colour" value="red" />
        </acme:properties>
        <camunda:properties>
          <camunda:property name="sla" value="2d" />
        </camunda:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`;
    interface Process {
      flowElements: { id: string; extensionElements: { values: Generic[] } }[];
    }
    const approve = (root: { rootElements: unknown[] }) => {
      const task = (root.rootElements[0] as Process).flowElements.find((element) => element.id === 'approve');
      if (!task) throw new Error('no task');
      return task.extensionElements;
    };
    const moddle = new BpmnModdle();
    const { rootElement } = await moddle.fromXML(xml);
    const extensions = approve(rootElement);
    const createAny = (name: string, properties: Record<string, unknown>) =>
      moddle.createAny(`camunda:${name}`, CAMUNDA, properties) as Generic;

    expect(readFourEyes(extensions.values)).toEqual([]);
    const foreign = extensions.values[0];
    extensions.values = writeFourEyes(extensions.values, ['draft'], createAny);
    expect(extensions.values[0]).toBe(foreign);
    expect(readFourEyes(extensions.values)).toEqual(['draft']);

    const { xml: saved } = await moddle.toXML(rootElement, { format: false });
    expect(saved).toContain(
      '<acme:properties owner="acme"><acme:property name="fasti:fourEyes" value="ghost" /><acme:property name="colour" value="red" /></acme:properties>',
    );
    expect(saved).toContain(
      '<camunda:properties><camunda:property name="sla" value="2d" /><camunda:property name="fasti:fourEyes" value="draft" /></camunda:properties>',
    );
    // and read again, the rule is the one written
    const reread = approve((await new BpmnModdle().fromXML(saved)).rootElement);
    expect(readFourEyes(reread.values)).toEqual(['draft']);
    expect(reread.values.map((value) => value.$type)).toEqual(['acme:properties', 'camunda:properties']);
  });

  it('offers the user tasks of the process, also in sub-processes', () => {
    const process: FlowNode = { $type: 'bpmn:Process', id: 'p', flowElements: [] };
    const sub: FlowNode = { $type: 'bpmn:SubProcess', id: 'sub', $parent: process, flowElements: [] };
    const draft: FlowNode = { $type: 'bpmn:UserTask', id: 'draft', name: 'Draft', $parent: process };
    const pay: FlowNode = { $type: 'bpmn:ServiceTask', id: 'pay', $parent: process };
    const review: FlowNode = { $type: 'bpmn:UserTask', id: 'review', $parent: sub };
    const approve: FlowNode = { $type: 'bpmn:UserTask', id: 'approve', name: 'Approve', $parent: process };
    process.flowElements = [draft, pay, sub, approve];
    sub.flowElements = [review];

    expect(userTasksOfProcess(review)).toEqual([
      { id: 'draft', name: 'Draft' },
      { id: 'review', name: '' },
      { id: 'approve', name: 'Approve' },
    ]);
    expect(userTasksOfProcess({ $type: 'bpmn:UserTask', id: 'lonely' })).toEqual([]);
  });
});
