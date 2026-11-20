/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Reading and writing the Camunda 7 extension elements of a BPMN element
 * (input/output mappings, listeners, generated form fields, the four-eyes
 * property). The editor knows no schema for them, so they are generic
 * elements: attributes are plain properties, text is `$body` and nested
 * elements are `$children`. Changes always build new elements, so the editor
 * can undo them, and whatever the panel does not edit (scripts, lists, maps,
 * fields of listeners, validation of form fields, other extension properties)
 * is carried over unchanged.
 */
export interface Generic {
  $type: string;
  $body?: string;
  $children?: Generic[];
  [attribute: string]: unknown;
}

/** Creates a generic element in the Camunda namespace, e.g. `inputParameter`. */
export type CreateGeneric = (localName: string, properties: Record<string, unknown>) => Generic;

export const localName = (type: string): string => type.slice(type.indexOf(':') + 1);
const isA = (element: { $type?: string }, name: string) => !!element.$type && localName(element.$type) === name;
const text = (value: unknown) => (typeof value === 'string' ? value : '');

// --- input/output -------------------------------------------------------------

export interface Parameter {
  name: string;
  value: string;
  /** a script, list or map: shown, not edited */
  complex: boolean;
  original?: Generic;
}

export function readParameters(values: Generic[]): { inputs: Parameter[]; outputs: Parameter[] } {
  const io = values.find((value) => isA(value, 'inputOutput'));
  const read = (kind: string) =>
    (io?.$children ?? [])
      .filter((child) => isA(child, kind))
      .map((child) => ({
        name: text(child.name),
        value: text(child.$body),
        complex: (child.$children?.length ?? 0) > 0,
        original: child,
      }));
  return { inputs: read('inputParameter'), outputs: read('outputParameter') };
}

export function writeParameters(
  values: Generic[],
  inputs: Parameter[],
  outputs: Parameter[],
  create: CreateGeneric,
): Generic[] {
  const build = (kind: string, parameter: Parameter): Generic =>
    parameter.complex && parameter.original
      ? create(kind, { name: parameter.name, $children: parameter.original.$children })
      : create(kind, parameter.value ? { name: parameter.name, $body: parameter.value } : { name: parameter.name });
  const children = [
    ...inputs.filter((entry) => entry.name).map((entry) => build('inputParameter', entry)),
    ...outputs.filter((entry) => entry.name).map((entry) => build('outputParameter', entry)),
  ];
  return replace(values, 'inputOutput', children.length ? [create('inputOutput', { $children: children })] : []);
}

// --- listeners ----------------------------------------------------------------

export type ListenerKind = 'executionListener' | 'taskListener';
export const LISTENER_TYPES = ['class', 'expression', 'delegateExpression'] as const;
export type ListenerType = (typeof LISTENER_TYPES)[number];
export const LISTENER_EVENTS: Record<ListenerKind, string[]> = {
  executionListener: ['start', 'end'],
  taskListener: ['create', 'assignment', 'update', 'complete', 'delete', 'timeout'],
};

/** The events an execution listener may have on an element: a flow is only taken. */
export function listenerEvents(kind: ListenerKind, type: string): string[] {
  return kind === 'executionListener' && type === 'bpmn:SequenceFlow' ? ['take'] : LISTENER_EVENTS[kind];
}

export interface Listener {
  event: string;
  type: ListenerType;
  value: string;
  /** a script or injected fields: shown, not edited */
  complex: boolean;
  original?: Generic;
}

export function readListeners(values: Generic[], kind: ListenerKind): Listener[] {
  return values
    .filter((value) => isA(value, kind))
    .map((value) => {
      const type = LISTENER_TYPES.find((candidate) => typeof value[candidate] === 'string') ?? 'class';
      return {
        event: text(value.event),
        type,
        value: text(value[type]),
        complex: (value.$children?.length ?? 0) > 0,
        original: value,
      };
    });
}

export function writeListeners(
  values: Generic[],
  kind: ListenerKind,
  listeners: Listener[],
  create: CreateGeneric,
): Generic[] {
  const built = listeners
    .filter((listener) => listener.complex || listener.value)
    .map((listener) => {
      const properties: Record<string, unknown> = { event: listener.event };
      if (listener.value) properties[listener.type] = listener.value;
      // other attributes, e.g. the id of a timeout listener, stay
      for (const [key, value] of Object.entries(listener.original ?? {})) {
        if (!key.startsWith('$') && key !== 'event' && !(LISTENER_TYPES as readonly string[]).includes(key)) {
          properties[key] = value;
        }
      }
      if (listener.original?.$children) properties.$children = listener.original.$children;
      return create(kind, properties);
    });
  return replace(values, kind, built);
}

// --- generated form fields ----------------------------------------------------

export const FIELD_TYPES = ['string', 'long', 'boolean', 'date', 'enum'] as const;

export interface FormField {
  id: string;
  label: string;
  type: string;
  defaultValue: string;
  original?: Generic;
}

export function readFormFields(values: Generic[]): FormField[] {
  const formData = values.find((value) => isA(value, 'formData'));
  return (formData?.$children ?? [])
    .filter((child) => isA(child, 'formField'))
    .map((child) => ({
      id: text(child.id),
      label: text(child.label),
      type: text(child.type) || 'string',
      defaultValue: text(child.defaultValue),
      original: child,
    }));
}

export function writeFormFields(values: Generic[], fields: FormField[], create: CreateGeneric): Generic[] {
  const formData = values.find((value) => isA(value, 'formData'));
  const built = fields
    .filter((field) => field.id)
    .map((field) => {
      // other attributes, e.g. the pattern of a date field, stay
      const properties: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(field.original ?? {})) {
        if (!key.startsWith('$')) properties[key] = value;
      }
      Object.assign(properties, { id: field.id, type: field.type });
      delete properties.label;
      delete properties.defaultValue;
      if (field.label) properties.label = field.label;
      if (field.defaultValue) properties.defaultValue = field.defaultValue;
      // validation, properties and the values of an enum stay
      if (field.original?.$children) properties.$children = field.original.$children;
      return create('formField', properties);
    });
  // other children of the form data, e.g. a business key field reference, stay
  const others = (formData?.$children ?? []).filter((child) => !isA(child, 'formField'));
  const keep: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(formData ?? {})) {
    if (!key.startsWith('$')) keep[key] = value;
  }
  const children = [...built, ...others];
  return replace(values, 'formData', children.length ? [create('formData', { ...keep, $children: children })] : []);
}

// --- four-eyes control (Fasti) -------------------------------------------------

/** The extension property of a user task that names the earlier user tasks it checks. */
export const FOUR_EYES_PROPERTY = 'fasti:fourEyes';

const isFourEyes = (child: Generic) => isA(child, 'property') && child.name === FOUR_EYES_PROPERTY;

/** The ids of the earlier user tasks a user task checks, in the order written. */
export function readFourEyes(values: Generic[]): string[] {
  const ids: string[] = [];
  for (const properties of values.filter((value) => isA(value, 'properties'))) {
    for (const child of (properties.$children ?? []).filter(isFourEyes)) {
      for (const part of text(child.value).split(',')) {
        const id = part.trim();
        if (id && !ids.includes(id)) ids.push(id);
      }
    }
  }
  return ids;
}

/**
 * Writes the four-eyes property into the first `camunda:properties` (created if needed); other
 * extension properties stay where they are, in every `camunda:properties` of the element. An
 * empty list removes the property, and a `camunda:properties` that held nothing else.
 */
export function writeFourEyes(values: Generic[], ids: string[], create: CreateGeneric): Generic[] {
  const rebuild = (properties: Generic, extra: Generic[]): Generic | null => {
    const children = [...(properties.$children ?? []).filter((child) => !isFourEyes(child)), ...extra];
    if (children.length === 0) return null;
    const keep: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(properties)) {
      if (!key.startsWith('$')) keep[key] = value;
    }
    return create('properties', { ...keep, $children: children });
  };
  const rule = ids.length ? [create('property', { name: FOUR_EYES_PROPERTY, value: ids.join(',') })] : [];
  let placed = false;
  const next: Generic[] = [];
  for (const value of values) {
    if (!isA(value, 'properties')) {
      next.push(value);
      continue;
    }
    const touched = !placed || (value.$children ?? []).some(isFourEyes);
    const built = touched ? rebuild(value, placed ? [] : rule) : value;
    placed = true;
    if (built) next.push(built);
  }
  if (!placed && rule.length) next.push(create('properties', { $children: rule }));
  return next;
}

/** An element of the model as far as the four-eyes field needs it. */
export interface FlowNode {
  $type: string;
  id: string;
  name?: string;
  $parent?: FlowNode;
  flowElements?: FlowNode[];
}

/**
 * The user tasks of the process that holds the element, also inside sub-processes, in the
 * order of the model: the steps a four-eyes rule may name.
 */
export function userTasksOfProcess(element: FlowNode): { id: string; name: string }[] {
  let process: FlowNode | undefined = element;
  while (process && process.$type !== 'bpmn:Process') process = process.$parent;
  const tasks: { id: string; name: string }[] = [];
  const visit = (node: FlowNode) => {
    for (const child of node.flowElements ?? []) {
      if (child.$type === 'bpmn:UserTask') tasks.push({ id: child.id, name: child.name ?? '' });
      visit(child);
    }
  };
  if (process) visit(process);
  return tasks;
}

/** Replaces the extension elements of one kind, at the place of the first of them. */
export function replace(values: Generic[], name: string, next: Generic[]): Generic[] {
  const index = values.findIndex((value) => isA(value, name));
  const rest = values.filter((value) => !isA(value, name));
  if (index < 0) return [...rest, ...next];
  const before = values.slice(0, index).filter((value) => !isA(value, name)).length;
  return [...rest.slice(0, before), ...next, ...rest.slice(before)];
}
