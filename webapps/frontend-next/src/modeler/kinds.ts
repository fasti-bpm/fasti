/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** What the editors of the modeler have in common. */
export interface EditorHandle {
  /** the model as it is drawn now, as the text of its file */
  save(): Promise<string>;
}

export interface EditorProps {
  /** the text of the model file */
  source: string;
  onChange: () => void;
  /** called once the model is shown and can be saved */
  onReady?: () => void;
  label: string;
}

export type ModelKind = 'bpmn' | 'dmn' | 'form';

export const MODEL_KINDS: readonly ModelKind[] = ['bpmn', 'dmn', 'form'];

export function isModelKind(value: unknown): value is ModelKind {
  return value === 'bpmn' || value === 'dmn' || value === 'form';
}

export function newModelId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`;
}

/** An empty process with one start event, the starting point of a new model. */
export function blankProcess(id: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
  id="Definitions_${id}" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${id}" isExecutable="true" camunda:historyTimeToLive="180">
    <bpmn:startEvent id="StartEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${id}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="160" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;
}

/** A decision with an empty table of one input and one output. */
export function blankDecision(id: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
  xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
  xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
  xmlns:camunda="http://camunda.org/schema/1.0/dmn"
  id="Definitions_${id}" name="Decisions" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="${id}" name="Decision" camunda:historyTimeToLive="180">
    <decisionTable id="DecisionTable_${id}">
      <input id="Input_1">
        <inputExpression id="InputExpression_1" typeRef="string">
          <text></text>
        </inputExpression>
      </input>
      <output id="Output_1" typeRef="string" />
    </decisionTable>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram id="DMNDiagram_1">
      <dmndi:DMNShape id="DMNShape_${id}" dmnElementRef="${id}">
        <dc:Bounds height="80" width="180" x="260" y="220" />
      </dmndi:DMNShape>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>
`;
}

/** A form with one text field; the id is what a task refers to. */
export function blankForm(id: string): string {
  return `${JSON.stringify(
    {
      id,
      type: 'default',
      schemaVersion: 18,
      components: [{ id: 'Field_1', key: 'textfield', label: 'Text field', type: 'textfield' }],
    },
    null,
    2,
  )}\n`;
}

/** The id of the first process of a model, used to name its file and deployment. */
export function processIdOf(xml: string): string | null {
  return /<(?:\w+:)?process\b[^>]*\bid="([^"]+)"/.exec(xml)?.[1] ?? null;
}

/** The id of the first decision of a model. */
export function decisionIdOf(xml: string): string | null {
  return /<(?:\w+:)?decision\b[^>]*\bid="([^"]+)"/.exec(xml)?.[1] ?? null;
}

/** The id of a form schema. */
export function formIdOf(text: string): string | null {
  try {
    const schema: unknown = JSON.parse(text);
    const id = schema && typeof schema === 'object' ? (schema as { id?: unknown }).id : null;
    return typeof id === 'string' && id ? id : null;
  } catch {
    return null;
  }
}

export interface KindInfo {
  extension: string;
  accept: string;
  mime: string;
  blank: () => string;
  idOf: (text: string) => string | null;
}

export const KINDS: Record<ModelKind, KindInfo> = {
  bpmn: {
    extension: '.bpmn',
    accept: '.bpmn,.bpmn20.xml,.xml',
    mime: 'application/xml',
    blank: () => blankProcess(newModelId('Process')),
    idOf: processIdOf,
  },
  dmn: {
    extension: '.dmn',
    accept: '.dmn,.dmn11.xml,.xml',
    mime: 'application/xml',
    blank: () => blankDecision(newModelId('Decision')),
    idOf: decisionIdOf,
  },
  form: {
    extension: '.form',
    accept: '.form,.json',
    mime: 'application/json',
    blank: () => blankForm(newModelId('Form')),
    idOf: formIdOf,
  },
};

/** The kind of a model file by its name. */
export function kindOfFile(name: string): ModelKind | null {
  const lower = name.toLowerCase();
  if (lower.endsWith('.bpmn') || lower.endsWith('.bpmn20.xml')) return 'bpmn';
  if (lower.endsWith('.dmn') || lower.endsWith('.dmn11.xml')) return 'dmn';
  if (lower.endsWith('.form')) return 'form';
  return null;
}
