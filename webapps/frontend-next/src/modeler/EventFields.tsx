/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { useI18n, type MessageKey } from '../i18n';
import { PropsSection } from './ExtensionFields';

/** A semantic object of the model as the editor holds it. */
export interface ModelObject {
  id?: string;
  $type: string;
  name?: string;
  body?: string;
  errorCode?: string;
  escalationCode?: string;
  [property: string]: unknown;
}

/** What the event fields need of the editor. */
export interface EventAccess {
  /** writes properties of the event definition, or of the element for a receive task */
  update: (target: ModelObject, properties: Record<string, unknown>) => void;
  create: (type: string, properties: Record<string, unknown>) => ModelObject;
  /** the messages, signals, errors and escalations of the file */
  roots: () => ModelObject[];
  /** adds a message, signal, error or escalation to the file */
  addRoot: (root: ModelObject) => void;
  /** runs several changes as one step to undo */
  together: (run: () => void) => void;
}

const TIMER_KINDS = ['timeDuration', 'timeDate', 'timeCycle'] as const;
type TimerKind = (typeof TIMER_KINDS)[number];
const TIMER_LABELS: Record<TimerKind, MessageKey> = {
  timeDuration: 'props.timer.duration',
  timeDate: 'props.timer.date',
  timeCycle: 'props.timer.cycle',
};

/** The referenced object of each kind of event definition. */
const REFERENCES: Record<string, { attr: string; type: string; label: MessageKey; code?: string; codeLabel?: MessageKey }> = {
  MessageEventDefinition: { attr: 'messageRef', type: 'bpmn:Message', label: 'props.message' },
  ReceiveTask: { attr: 'messageRef', type: 'bpmn:Message', label: 'props.message' },
  SignalEventDefinition: { attr: 'signalRef', type: 'bpmn:Signal', label: 'props.signal' },
  ErrorEventDefinition: {
    attr: 'errorRef',
    type: 'bpmn:Error',
    label: 'props.error',
    code: 'errorCode',
    codeLabel: 'props.errorCode',
  },
  EscalationEventDefinition: {
    attr: 'escalationRef',
    type: 'bpmn:Escalation',
    label: 'props.escalation',
    code: 'escalationCode',
    codeLabel: 'props.escalationCode',
  },
};

const local = (type: string) => type.slice(type.indexOf(':') + 1);
const newId = (type: string) => `${local(type)}_${Math.random().toString(36).slice(2, 9)}`;

/**
 * What triggers or throws an event: the time of a timer, the message, signal,
 * error or escalation by name (an existing one of that name is used, otherwise
 * one is added to the file), or the condition of a conditional event.
 */
export function EventFields({ definition, access }: { definition: ModelObject; access: EventAccess }) {
  const { t } = useI18n();
  const kind = local(definition.$type);

  if (kind === 'TimerEventDefinition') {
    return <TimerFields definition={definition} access={access} />;
  }

  if (kind === 'ConditionalEventDefinition') {
    const condition = (definition.condition as ModelObject | undefined)?.body ?? '';
    return (
      <PropsSection title={t('props.section.event')}>
        <label className="props-field__label" htmlFor="prop-event-condition">
          {t('props.condition')}
        </label>
        <textarea
          id="prop-event-condition"
          className="input mono"
          rows={2}
          defaultValue={condition}
          spellCheck={false}
          onBlur={(event) => {
            const value = event.target.value.trim();
            if (value === condition) return;
            access.update(definition, {
              condition: value ? access.create('bpmn:FormalExpression', { body: value }) : undefined,
            });
          }}
        />
        <p className="props-field__hint">{t('props.conditionHint')}</p>
      </PropsSection>
    );
  }

  const reference = REFERENCES[kind];
  if (!reference) return null;
  const current = definition[reference.attr] as ModelObject | undefined;
  const name = current?.name ?? '';
  const code = reference.code ? String(current?.[reference.code] ?? '') : '';

  const writeName = (value: string) => {
    if (value === name) return;
    if (!value) {
      access.update(definition, { [reference.attr]: undefined });
      return;
    }
    const found = access.roots().find((root) => root.$type === reference.type && root.name === value);
    if (found) {
      access.update(definition, { [reference.attr]: found });
      return;
    }
    // a new one is added and referred to in one step, so one undo removes both
    const created = access.create(reference.type, { id: newId(reference.type), name: value });
    access.together(() => {
      access.addRoot(created);
      access.update(definition, { [reference.attr]: created });
    });
  };

  return (
    <PropsSection title={t('props.section.event')}>
      <label className="props-field__label" htmlFor="prop-event-ref">
        {t(reference.label)}
      </label>
      <input
        id="prop-event-ref"
        className="input"
        defaultValue={name}
        onBlur={(event) => writeName(event.target.value.trim())}
        onKeyDown={(event) => event.key === 'Enter' && writeName(event.currentTarget.value.trim())}
      />
      <p className="props-field__hint">{t('props.refHint')}</p>
      {reference.code && reference.codeLabel && current && (
        <>
          <label className="props-field__label" htmlFor="prop-event-code">
            {t(reference.codeLabel)}
          </label>
          <input
            id="prop-event-code"
            className="input mono"
            defaultValue={code}
            spellCheck={false}
            onBlur={(event) => {
              const value = event.target.value.trim();
              if (value !== code && reference.code) access.update(current, { [reference.code]: value || undefined });
            }}
          />
        </>
      )}
    </PropsSection>
  );
}

function TimerFields({ definition, access }: { definition: ModelObject; access: EventAccess }) {
  const { t } = useI18n();
  const found = TIMER_KINDS.find((candidate) => definition[candidate] != null);
  const [kind, setKind] = useState<TimerKind>(found ?? 'timeDuration');
  const value = found ? ((definition[found] as ModelObject | undefined)?.body ?? '') : '';
  const write = (nextKind: TimerKind, nextValue: string) => {
    if (nextKind === found && nextValue === value) return;
    const properties: Record<string, unknown> = {};
    for (const candidate of TIMER_KINDS) {
      properties[candidate] =
        candidate === nextKind && nextValue ? access.create('bpmn:FormalExpression', { body: nextValue }) : undefined;
    }
    access.update(definition, properties);
  };
  return (
    <PropsSection title={t('props.section.event')}>
      <label className="props-field__label" htmlFor="prop-timer-kind">
        {t('props.timer.kind')}
      </label>
      <select
        id="prop-timer-kind"
        className="input"
        value={kind}
        onChange={(event) => {
          const next = event.target.value as TimerKind;
          setKind(next);
          if (value) write(next, value);
        }}
      >
        {TIMER_KINDS.map((candidate) => (
          <option key={candidate} value={candidate}>
            {t(TIMER_LABELS[candidate])}
          </option>
        ))}
      </select>
      <label className="props-field__label" htmlFor="prop-timer-value">
        {t('props.timer.value')}
      </label>
      <input
        id="prop-timer-value"
        className="input mono"
        defaultValue={value}
        spellCheck={false}
        onBlur={(event) => write(kind, event.target.value.trim())}
        onKeyDown={(event) => event.key === 'Enter' && write(kind, event.currentTarget.value.trim())}
      />
      <p className="props-field__hint">{t('props.timer.hint')}</p>
    </PropsSection>
  );
}
