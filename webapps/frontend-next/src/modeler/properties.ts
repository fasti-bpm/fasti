/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { MessageKey } from '../i18n';

/**
 * What the properties panel of the process editor offers for an element:
 * the plain BPMN attributes and the Camunda 7 extension attributes the
 * engine reads. Attributes with the `camunda:` prefix are kept as plain
 * attributes of the element, so extension elements the panel does not know
 * (form fields, listeners, input/output) stay as they are.
 */
export type FieldKind = 'text' | 'textarea' | 'checkbox' | 'implementation';

export interface FieldSpec {
  /** the attribute, or a pseudo attribute the panel handles itself */
  attr: string;
  label: MessageKey;
  kind: FieldKind;
  hint?: MessageKey;
  mono?: boolean;
  /** read from and written to the process of a pool instead of the element itself */
  scope?: 'process';
}

/** How a service-like task says what runs; at most one of them is set. */
export const IMPLEMENTATIONS = [
  { value: 'none', attr: null, label: 'props.impl.none' },
  { value: 'class', attr: 'camunda:class', label: 'props.impl.class' },
  { value: 'expression', attr: 'camunda:expression', label: 'props.impl.expression' },
  { value: 'delegateExpression', attr: 'camunda:delegateExpression', label: 'props.impl.delegateExpression' },
  { value: 'external', attr: 'camunda:topic', label: 'props.impl.external' },
] as const satisfies readonly { value: string; attr: string | null; label: MessageKey }[];

export type Implementation = (typeof IMPLEMENTATIONS)[number]['value'];

const ID: FieldSpec = { attr: 'id', label: 'props.id', kind: 'text', mono: true, hint: 'props.idHint' };
const NAME: FieldSpec = { attr: 'name', label: 'props.name', kind: 'text' };
const DOCUMENTATION: FieldSpec = { attr: 'documentation', label: 'props.documentation', kind: 'textarea' };
const ASYNC: FieldSpec = { attr: 'camunda:asyncBefore', label: 'props.asyncBefore', kind: 'checkbox', hint: 'props.asyncBeforeHint' };
const RESULT: FieldSpec = { attr: 'camunda:resultVariable', label: 'props.resultVariable', kind: 'text', mono: true };
const LIST_HINT = 'props.listHint' as const;

const SERVICE_LIKE = new Set(['bpmn:ServiceTask', 'bpmn:SendTask']);

/** The fields for an element of the given BPMN type, in the order they are shown. */
const PROCESS: FieldSpec[] = [
  { attr: 'isExecutable', label: 'props.executable', kind: 'checkbox', hint: 'props.executableHint' },
  { attr: 'camunda:versionTag', label: 'props.versionTag', kind: 'text' },
  { attr: 'camunda:historyTimeToLive', label: 'props.historyTtl', kind: 'text', hint: 'props.historyTtlHint' },
  { attr: 'camunda:candidateStarterGroups', label: 'props.starterGroups', kind: 'text', hint: LIST_HINT },
];

export function fieldsFor(type: string, options: { conditional?: boolean } = {}): FieldSpec[] {
  if (type === 'bpmn:Process') {
    return [ID, NAME, ...PROCESS, DOCUMENTATION];
  }
  if (type === 'bpmn:Participant') {
    // a pool: its own name, and what the engine needs of the process it holds
    return [
      ID,
      NAME,
      { attr: 'id', label: 'props.processId', kind: 'text', mono: true, hint: 'props.idHint', scope: 'process' },
      ...PROCESS.map((field) => ({ ...field, scope: 'process' as const })),
      DOCUMENTATION,
    ];
  }
  if (type === 'bpmn:Collaboration') {
    return [ID, DOCUMENTATION];
  }
  if (type === 'bpmn:SequenceFlow') {
    return [ID, NAME, ...(options.conditional ? [{ attr: 'condition', label: 'props.condition', kind: 'textarea', mono: true, hint: 'props.conditionHint' } as FieldSpec] : []), DOCUMENTATION];
  }
  const fields: FieldSpec[] = [ID, NAME];
  if (type === 'bpmn:UserTask') {
    fields.push(
      { attr: 'camunda:assignee', label: 'props.assignee', kind: 'text', mono: true },
      { attr: 'camunda:candidateGroups', label: 'props.candidateGroups', kind: 'text', hint: LIST_HINT },
      { attr: 'camunda:candidateUsers', label: 'props.candidateUsers', kind: 'text', hint: LIST_HINT },
      { attr: 'camunda:formKey', label: 'props.formKey', kind: 'text', mono: true, hint: 'props.formKeyHint' },
      { attr: 'camunda:dueDate', label: 'props.dueDate', kind: 'text', mono: true, hint: 'props.dateHint' },
      { attr: 'camunda:followUpDate', label: 'props.followUpDate', kind: 'text', mono: true, hint: 'props.dateHint' },
      { attr: 'camunda:priority', label: 'props.priority', kind: 'text' },
    );
  } else if (SERVICE_LIKE.has(type)) {
    fields.push({ attr: 'implementation', label: 'props.implementation', kind: 'implementation' }, RESULT);
  } else if (type === 'bpmn:BusinessRuleTask') {
    fields.push({ attr: 'camunda:decisionRef', label: 'props.decisionRef', kind: 'text', mono: true }, RESULT);
  } else if (type === 'bpmn:ScriptTask') {
    fields.push(
      { attr: 'scriptFormat', label: 'props.scriptFormat', kind: 'text', mono: true },
      { attr: 'script', label: 'props.script', kind: 'textarea', mono: true },
      RESULT,
    );
  } else if (type === 'bpmn:CallActivity') {
    fields.push({ attr: 'calledElement', label: 'props.calledElement', kind: 'text', mono: true });
  } else if (type === 'bpmn:StartEvent') {
    fields.push(
      { attr: 'camunda:initiator', label: 'props.initiator', kind: 'text', mono: true },
      { attr: 'camunda:formKey', label: 'props.formKey', kind: 'text', mono: true, hint: 'props.formKeyHint' },
    );
  }
  if (/Task$|SubProcess$|CallActivity$/.test(type)) {
    fields.push(ASYNC);
  }
  fields.push(DOCUMENTATION);
  return fields;
}

/** The implementation an element has now, read from its attributes. */
export function implementationOf(attrs: Record<string, unknown>): Implementation {
  if (attrs['camunda:type'] === 'external' || attrs['camunda:topic'] != null) return 'external';
  const found = IMPLEMENTATIONS.find((entry) => entry.attr && entry.attr !== 'camunda:topic' && attrs[entry.attr] != null);
  return found?.value ?? 'none';
}

/** The attributes to write for another implementation: the others are cleared. */
export function implementationChange(next: Implementation, value: string): Record<string, string | undefined> {
  const change: Record<string, string | undefined> = { 'camunda:type': next === 'external' ? 'external' : undefined };
  for (const entry of IMPLEMENTATIONS) {
    if (entry.attr) change[entry.attr] = entry.value === next ? value : undefined;
  }
  return change;
}

/** A valid BPMN id: an XML name without a colon. */
export function isValidId(id: string): boolean {
  return /^[A-Za-z_][\w.-]*$/.test(id);
}
