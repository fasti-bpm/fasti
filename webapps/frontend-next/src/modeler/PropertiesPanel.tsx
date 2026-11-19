/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { useI18n, type MessageKey } from '../i18n';
import { readPreference, writePreference } from '../lib/storage';
import { cx } from '../ui';
import {
  IMPLEMENTATIONS,
  fieldsFor,
  implementationChange,
  implementationOf,
  isValidId,
  type FieldSpec,
  type Implementation,
} from './properties';
import { FormFieldsSection, ListenersSection, ParametersSection, type ExtensionAccess } from './ExtensionFields';
import { EventFields, type EventAccess, type ModelObject } from './EventFields';
import type { Generic } from './extensions';

export interface BusinessObject {
  id: string;
  $type: string;
  $attrs: Record<string, unknown>;
  get(name: string): unknown;
  sourceRef?: BusinessObject;
  conditionExpression?: { body?: string };
  documentation?: { text?: string }[];
  processRef?: BusinessObject;
  eventDefinitions?: ModelObject[];
  extensionElements?: BusinessObject & { values?: Generic[] };
}
export interface DiagramElement {
  id: string;
  type: string;
  businessObject: BusinessObject;
  labelTarget?: DiagramElement;
}
export type SelectionListener = (event: { newSelection?: DiagramElement[] }) => void;

/** The services of the process editor the panel uses. */
export interface EditorServices {
  on(event: string, listener: SelectionListener): void;
  off(event: string, listener: SelectionListener): void;
  root(): DiagramElement;
  /** what is selected when the panel opens */
  selected(): DiagramElement[];
  byId(id: string): DiagramElement | undefined;
  /** whether any object of the document, drawn or not, has the id */
  idTaken(id: string): boolean;
  update(element: DiagramElement, properties: Record<string, unknown>): void;
  /** changes an object behind an element, e.g. the process of a pool */
  updateObject(element: DiagramElement, object: BusinessObject, properties: Record<string, unknown>): void;
  label(element: DiagramElement, text: string): void;
  create(type: string, properties: Record<string, unknown>): unknown;
  /** runs several changes as one step to undo */
  together(run: () => void): void;
  /** an element of the Camunda namespace the editor has no schema for, e.g. `inputParameter` */
  createGeneric(localName: string, properties: Record<string, unknown>): Generic;
  /** the root of the file: messages, signals, errors and escalations live there */
  definitions(): BusinessObject & { rootElements?: ModelObject[] };
}

const TYPE_KEYS = new Set(
  [
    'Process', 'Collaboration', 'Participant', 'Lane', 'Task', 'UserTask', 'ServiceTask', 'ScriptTask',
    'BusinessRuleTask', 'SendTask', 'ReceiveTask', 'ManualTask', 'CallActivity', 'SubProcess', 'StartEvent',
    'EndEvent', 'IntermediateCatchEvent', 'IntermediateThrowEvent', 'BoundaryEvent', 'ExclusiveGateway',
    'ParallelGateway', 'InclusiveGateway', 'EventBasedGateway', 'SequenceFlow', 'DataObjectReference',
    'DataStoreReference', 'TextAnnotation', 'Group',
  ].map((type) => `props.type.${type}`),
);

const ACTIVITY = /Task$|SubProcess$|CallActivity$|Transaction$/;

const MIN_WIDTH = 260;
const MAX_WIDTH = 640;
const clamp = (value: number) => Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, value));

export function readValue(element: DiagramElement, attr: string, scope?: 'process'): string {
  const bo = scope === 'process' ? element.businessObject.processRef : element.businessObject;
  if (!bo) return '';
  if (attr === 'documentation') return bo.documentation?.[0]?.text ?? '';
  if (attr === 'condition') return bo.conditionExpression?.body ?? '';
  const value = bo.get(attr);
  return value == null ? '' : String(value);
}

/**
 * The properties of the selected element of the process, or of the process
 * itself when nothing is selected. A value is written when its field is left
 * or Enter is pressed, so one edit is one step to undo.
 */
export function PropertiesPanel({ services }: { services: EditorServices }) {
  const { t } = useI18n();
  const [selected, setSelected] = useState<DiagramElement | null>(() => {
    const now = services.selected();
    const only = now.length === 1 ? now[0] : undefined;
    return only ? (only.labelTarget ?? only) : null;
  });
  // counts changes of the model, so the fields show what undo and redo did
  const [, setVersion] = useState(0);
  const [idError, setIdError] = useState<{ key: string; text: string } | null>(null);
  const [width, setWidth] = useState(() => clamp(readPreference<number>('modelerPanelWidth', 320)));

  useEffect(() => {
    const onSelect: SelectionListener = (event) => {
      const next = event.newSelection?.length === 1 ? event.newSelection[0] : null;
      setSelected(next?.labelTarget ?? next ?? null);
      setIdError(null);
    };
    const onChange = () => setVersion((value) => value + 1);
    services.on('selection.changed', onSelect);
    services.on('elements.changed', onChange);
    return () => {
      services.off('selection.changed', onSelect);
      services.off('elements.changed', onChange);
    };
  }, [services]);

  // an element that was removed (e.g. by undo) is no longer shown
  const current = (selected && services.byId(selected.id)) || services.root();
  const isRoot = current === services.root();
  const bo = current.businessObject;
  const conditional =
    bo.$type === 'bpmn:SequenceFlow' &&
    !!bo.sourceRef &&
    !/StartEvent$|ParallelGateway$|EventBasedGateway$/.test(bo.sourceRef.$type);
  // a pool without a process (a black box) has no process fields
  const fields = fieldsFor(bo.$type, { conditional }).filter((field) => field.scope !== 'process' || bo.processRef);
  // the extension elements: of the process for a pool, of the element otherwise
  const holder: DiagramElement = current;
  const owner = bo.$type === 'bpmn:Participant' && bo.processRef ? bo.processRef : bo;
  const values = owner.extensionElements?.values ?? [];
  const access: ExtensionAccess = {
    values,
    create: (name, properties) => services.createGeneric(name, properties),
    write: (next) => {
      if (owner.extensionElements) {
        services.updateObject(holder, owner.extensionElements, { values: next });
      } else {
        const extensions = services.create('bpmn:ExtensionElements', { values: next });
        if (owner === bo) services.update(holder, { extensionElements: extensions });
        else services.updateObject(holder, owner, { extensionElements: extensions });
      }
    },
  };
  // what triggers the event; a receive task names its message itself
  const eventDefinition: ModelObject | undefined =
    bo.eventDefinitions?.[0] ?? (bo.$type === 'bpmn:ReceiveTask' ? (bo as unknown as ModelObject) : undefined);
  const eventKey = eventDefinition
    ? `${eventDefinition.$type}|${Object.values(eventDefinition)
        .map((entry) => {
          if (!entry || typeof entry !== 'object') return '';
          const object = entry as ModelObject;
          return [object.body, object.name, object.errorCode, object.escalationCode].join(',');
        })
        .join('|')}`
    : '';
  const eventAccess: EventAccess = {
    update: (target, properties) =>
      target === (bo as unknown as ModelObject)
        ? services.update(holder, properties)
        : services.updateObject(holder, target as unknown as BusinessObject, properties),
    create: (type, properties) => services.create(type, properties) as ModelObject,
    roots: () => services.definitions().rootElements ?? [],
    together: (run) => services.together(run),
    addRoot: (root) => {
      const definitions = services.definitions();
      services.updateObject(holder, definitions, { rootElements: [...(definitions.rootElements ?? []), root] });
    },
  };
  const typeKey = `props.type.${bo.$type.replace(/^bpmn:/, '')}`;
  const typeName = TYPE_KEYS.has(typeKey) ? t(typeKey as MessageKey) : bo.$type.replace(/^bpmn:/, '').replace(/([a-z])([A-Z])/g, '$1 $2');

  const write = (field: FieldSpec, raw: string | boolean) => {
    // multi-line content (scripts, expressions, documentation) is kept as written
    const value = typeof raw !== 'string' ? raw : field.kind === 'textarea' ? (raw.trim() ? raw : '') : raw.trim();
    const before = readValue(current, field.attr, field.scope);
    if (field.kind === 'checkbox' ? value === (before === 'true') : value === before) return;
    if (field.attr === 'id') {
      const id = String(value);
      const key = (field.scope ?? '') + 'id';
      if (!isValidId(id)) return setIdError({ key, text: t('props.idInvalid') });
      if (services.idTaken(id)) return setIdError({ key, text: t('props.idTaken') });
      setIdError(null);
    }
    if (field.scope === 'process') {
      const process = bo.processRef;
      if (!process) return;
      const change =
        field.kind === 'checkbox'
          ? { [field.attr]: field.attr === 'isExecutable' ? !!value : value ? 'true' : undefined }
          : { [field.attr]: value === '' ? undefined : value };
      services.updateObject(current, process, change);
      return;
    }
    switch (field.attr) {
      case 'id':
        services.update(current, { id: value });
        return;
      case 'name':
        if (isRoot) services.update(current, { name: value || undefined });
        else services.label(current, String(value));
        return;
      case 'documentation':
        services.update(current, {
          documentation: value ? [services.create('bpmn:Documentation', { text: value })] : [],
        });
        return;
      case 'condition':
        services.update(current, {
          conditionExpression: value ? services.create('bpmn:FormalExpression', { body: value }) : undefined,
        });
        return;
      case 'isExecutable':
        services.update(current, { isExecutable: !!value });
        return;
      default:
        if (field.kind === 'checkbox') services.update(current, { [field.attr]: value ? 'true' : undefined });
        else services.update(current, { [field.attr]: value === '' ? undefined : value });
    }
  };

  // the panel is made wider or narrower by dragging its edge, or with the arrow keys
  const dragging = useRef<{ x: number; width: number } | null>(null);
  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    dragging.current = { x: event.clientX, width };
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const resize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (dragging.current) setWidth(clamp(dragging.current.width + dragging.current.x - event.clientX));
  };
  const endResize = () => {
    if (dragging.current) writePreference('modelerPanelWidth', width);
    dragging.current = null;
  };
  const resizeByKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = event.key === 'ArrowLeft' ? 16 : event.key === 'ArrowRight' ? -16 : 0;
    if (!step) return;
    event.preventDefault();
    const next = clamp(width + step);
    setWidth(next);
    writePreference('modelerPanelWidth', next);
  };

  return (
    <aside className="props-panel" style={{ width }} aria-label={t('props.title')}>
      <div
        className="props-panel__resize"
        role="separator"
        aria-orientation="vertical"
        aria-label={t('props.resize')}
        aria-valuemin={MIN_WIDTH}
        aria-valuemax={MAX_WIDTH}
        aria-valuenow={width}
        tabIndex={0}
        onPointerDown={startResize}
        onPointerMove={resize}
        onPointerUp={endResize}
        onPointerCancel={endResize}
        onKeyDown={resizeByKey}
      />
      <header className="props-panel__header">
        <span className="props-panel__type">{typeName}</span>
        <h2 className="props-panel__title">{readValue(current, 'name') || current.id}</h2>
        {isRoot && (
          <p className="props-panel__hint">
            {bo.$type === 'bpmn:Collaboration' ? t('props.collaborationHint') : t('props.rootHint')}
          </p>
        )}
      </header>
      <div className="props-panel__body" key={current.id}>
        {eventDefinition && (
          <EventFields key={eventKey} definition={eventDefinition} access={eventAccess} />
        )}
        {fields.map((field) => (
          <PropertyField
            key={(field.scope ?? '') + field.attr}
            field={field}
            element={current}
            error={idError && idError.key === (field.scope ?? '') + field.attr ? idError.text : null}
            onWrite={(value) => write(field, value)}
            onImplementation={(next, value) => services.update(current, implementationChange(next, value))}
          />
        ))}
        <div className="props-sections">
          {ACTIVITY.test(bo.$type) && <ParametersSection access={access} />}
          {(bo.$type === 'bpmn:UserTask' || bo.$type === 'bpmn:StartEvent') && <FormFieldsSection access={access} />}
          {bo.$type === 'bpmn:UserTask' && <ListenersSection access={access} kind="taskListener" elementType={bo.$type} />}
          {bo.$type !== 'bpmn:Collaboration' && <ListenersSection access={access} kind="executionListener" elementType={bo.$type} />}
        </div>
      </div>
    </aside>
  );
}

function PropertyField({
  field,
  element,
  error,
  onWrite,
  onImplementation,
}: {
  field: FieldSpec;
  element: DiagramElement;
  error: string | null;
  onWrite: (value: string | boolean) => void;
  onImplementation: (next: Implementation, value: string) => void;
}) {
  const { t } = useI18n();
  const id = `prop-${field.scope ?? ''}${field.attr.replace(/\W/g, '-')}`;
  const value = readValue(element, field.attr, field.scope);
  // a value changed elsewhere (undo, the canvas) shows up unless the field is being edited
  const input = useRef<HTMLInputElement & HTMLTextAreaElement>(null);
  useEffect(() => {
    const control = input.current;
    if (control && document.activeElement !== control && control.value !== value) control.value = value;
  }, [value]);
  const hint = field.hint ? t(field.hint) : null;
  const described = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  if (field.kind === 'implementation') {
    // drawn again when undo or redo changes what runs
    const attrs = element.businessObject.$attrs;
    const shown = IMPLEMENTATIONS.map((entry) => (entry.attr ? String(attrs[entry.attr] ?? '') : '')).join('|');
    return <ImplementationField key={shown} element={element} onChange={onImplementation} />;
  }

  if (field.kind === 'checkbox') {
    return (
      <div className="props-field props-field--check">
        <label className="props-check">
          <input
            id={id}
            type="checkbox"
            checked={value === 'true'}
            aria-describedby={described}
            onChange={(event) => onWrite(event.target.checked)}
          />
          <span>{t(field.label)}</span>
        </label>
        {hint && (
          <p id={`${id}-hint`} className="props-field__hint">
            {hint}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className={cx('props-field', error && 'props-field--invalid')}>
      <label className="props-field__label" htmlFor={id}>
        {t(field.label)}
      </label>
      {field.kind === 'textarea' ? (
        <textarea
          ref={input}
          id={id}
          className={cx('input', field.mono && 'mono')}
          rows={3}
          defaultValue={value}
          spellCheck={!field.mono}
          aria-describedby={described}
          onBlur={(event) => onWrite(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) onWrite(event.currentTarget.value);
          }}
        />
      ) : (
        <input
          ref={input}
          id={id}
          className={cx('input', field.mono && 'mono')}
          defaultValue={value}
          spellCheck={!field.mono}
          aria-invalid={error ? true : undefined}
          aria-describedby={described}
          onBlur={(event) => onWrite(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && onWrite(event.currentTarget.value)}
        />
      )}
      {error ? (
        <p id={`${id}-error`} className="props-field__error" role="alert">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="props-field__hint">
            {hint}
          </p>
        )
      )}
    </div>
  );
}

function ImplementationField({
  element,
  onChange,
}: {
  element: DiagramElement;
  onChange: (next: Implementation, value: string) => void;
}) {
  const { t } = useI18n();
  const attrs = element.businessObject.$attrs;
  const [kind, setKind] = useState<Implementation>(() => implementationOf(attrs));
  const entry = IMPLEMENTATIONS.find((candidate) => candidate.value === kind);
  const value = entry?.attr ? String(attrs[entry.attr] ?? '') : '';
  const valueLabel = kind === 'external' ? t('props.topic') : entry ? t(entry.label) : '';
  return (
    <div className="props-field">
      <label className="props-field__label" htmlFor="prop-implementation">
        {t('props.implementation')}
      </label>
      <select
        id="prop-implementation"
        className="input"
        value={kind}
        onChange={(event) => {
          const next = event.target.value as Implementation;
          setKind(next);
          // the value is kept when switching, e.g. from expression to delegate expression;
          // without a value, the choice is written once one is typed
          if (next === 'none' || value) onChange(next, next === 'none' ? '' : value);
        }}
      >
        {IMPLEMENTATIONS.map((candidate) => (
          <option key={candidate.value} value={candidate.value}>
            {t(candidate.label)}
          </option>
        ))}
      </select>
      {entry?.attr && (
        <input
          className="input mono"
          aria-label={valueLabel}
          placeholder={valueLabel}
          defaultValue={value}
          spellCheck={false}
          onBlur={(event) => event.target.value.trim() !== value && onChange(kind, event.target.value.trim())}
          onKeyDown={(event) => event.key === 'Enter' && onChange(kind, event.currentTarget.value.trim())}
        />
      )}
    </div>
  );
}
