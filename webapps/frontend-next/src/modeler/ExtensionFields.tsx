/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useI18n, type MessageKey } from '../i18n';
import { IconButton, cx } from '../ui';
import {
  FIELD_TYPES,
  LISTENER_TYPES,
  listenerEvents,
  readFormFields,
  readListeners,
  readParameters,
  writeFormFields,
  writeListeners,
  writeParameters,
  type CreateGeneric,
  type FormField,
  type Generic,
  type Listener,
  type ListenerKind,
  type Parameter,
} from './extensions';

/** What the sections need of the editor: the extension elements of the element and a way to replace them. */
export interface ExtensionAccess {
  values: Generic[];
  write: (values: Generic[]) => void;
  create: CreateGeneric;
}

function Section({ title, count, children }: { title: string; count?: number; children: ReactNode }) {
  return (
    <details className="props-section" open>
      <summary className="props-section__title">
        {title}
        {count ? <span className="count">{count}</span> : null}
      </summary>
      <div className="props-section__body">{children}</div>
    </details>
  );
}

/**
 * Rows of small fields with an add and a remove button. A row is written when
 * one of its fields is left; a new row is written once it has what it needs.
 */
function Rows<T>({
  rows,
  empty,
  onWrite,
  render,
  addLabel,
}: {
  rows: T[];
  empty: () => T;
  onWrite: (rows: T[]) => void;
  /** `change(next, true)` writes at once, e.g. for a choice from a list */
  render: (row: T, change: (next: T, now?: boolean) => void, commit: () => void) => ReactNode;
  addLabel: string;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState(rows);
  const container = useRef<HTMLDivElement>(null);
  // what undo, redo or another edit changed shows up, unless a row is being edited
  const shown = JSON.stringify(rows, (key, value: unknown) => (key === 'original' ? undefined : value));
  const latest = useRef(rows);
  latest.current = rows;
  useEffect(() => {
    if (!container.current?.contains(document.activeElement)) setDraft(latest.current);
  }, [shown]);
  // leaving a field without a change writes nothing, so undo is not filled with empty steps
  const commit = (next: T[] = draft) => {
    if (JSON.stringify(next, (key, value: unknown) => (key === 'original' ? undefined : value)) !== shown) onWrite(next);
  };
  return (
    <div className="props-rows" ref={container}>
      {draft.length === 0 && <p className="props-field__hint">{t('props.none')}</p>}
      {draft.map((row, index) => (
        <div key={index} className="props-row">
          <div className="props-row__fields">
            {render(
              row,
              (next, now) => {
                const rowsNext = draft.map((entry, position) => (position === index ? next : entry));
                setDraft(rowsNext);
                if (now) commit(rowsNext);
              },
              () => commit(),
            )}
          </div>
          <IconButton
            icon={Trash2}
            label={t('props.remove')}
            onClick={() => {
              const next = draft.filter((_, position) => position !== index);
              setDraft(next);
              commit(next);
            }}
          />
        </div>
      ))}
      <button type="button" className="link props-rows__add" onClick={() => setDraft([...draft, empty()])}>
        <Plus aria-hidden size={14} />
        {addLabel}
      </button>
    </div>
  );
}

function Mini({
  label,
  value,
  onChange,
  onCommit,
  mono,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  onCommit: () => void;
  mono?: boolean;
  disabled?: boolean;
}) {
  return (
    <input
      className={cx('input input--sm', mono && 'mono')}
      aria-label={label}
      placeholder={label}
      value={value}
      disabled={disabled}
      spellCheck={!mono}
      onChange={(event) => onChange(event.target.value)}
      onBlur={onCommit}
      onKeyDown={(event) => event.key === 'Enter' && onCommit()}
    />
  );
}

function Choice({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <select className="input input--sm" aria-label={label} value={value} onChange={(event) => onChange(event.target.value)}>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export function ParametersSection({ access }: { access: ExtensionAccess }) {
  const { t } = useI18n();
  const { inputs, outputs } = readParameters(access.values);
  const list = (kind: 'inputs' | 'outputs', rows: Parameter[]) => (
    <Section title={t(kind === 'inputs' ? 'props.inputs' : 'props.outputs')} count={rows.length}>
      <Rows
        rows={rows}
        empty={() => ({ name: '', value: '', complex: false })}
        addLabel={t('props.add')}
        onWrite={(next) =>
          access.write(
            writeParameters(access.values, kind === 'inputs' ? next : inputs, kind === 'outputs' ? next : outputs, access.create),
          )
        }
        render={(row, change, commit) => (
          <>
            <Mini label={t('props.paramName')} mono value={row.name} onChange={(name) => change({ ...row, name })} onCommit={commit} />
            {row.complex ? (
              <p className="props-field__hint">{t('props.complex')}</p>
            ) : (
              <Mini label={t('props.paramValue')} mono value={row.value} onChange={(value) => change({ ...row, value })} onCommit={commit} />
            )}
          </>
        )}
      />
    </Section>
  );
  return (
    <>
      {list('inputs', inputs)}
      {list('outputs', outputs)}
    </>
  );
}

const TYPE_LABELS: Record<(typeof LISTENER_TYPES)[number], MessageKey> = {
  class: 'props.impl.class',
  expression: 'props.impl.expression',
  delegateExpression: 'props.impl.delegateExpression',
};

export function ListenersSection({
  access,
  kind,
  elementType,
}: {
  access: ExtensionAccess;
  kind: ListenerKind;
  elementType: string;
}) {
  const { t } = useI18n();
  const listeners = readListeners(access.values, kind);
  const events = listenerEvents(kind, elementType);
  return (
    <Section title={t(kind === 'taskListener' ? 'props.taskListeners' : 'props.executionListeners')} count={listeners.length}>
      <Rows<Listener>
        rows={listeners}
        empty={() => ({ event: events[0] ?? 'start', type: 'class', value: '', complex: false })}
        addLabel={t('props.add')}
        onWrite={(next) => access.write(writeListeners(access.values, kind, next, access.create))}
        render={(row, change, commit) => (
          <>
            <div className="props-row__pair">
              <Choice
                label={t('props.listenerEvent')}
                value={row.event}
                options={events.map((event) => ({ value: event, label: event }))}
                onChange={(event) => change({ ...row, event }, true)}
              />
              <Choice
                label={t('props.implementation')}
                value={row.type}
                options={LISTENER_TYPES.map((type) => ({ value: type, label: t(TYPE_LABELS[type]) }))}
                onChange={(type) => change({ ...row, type: type as Listener['type'] }, true)}
              />
            </div>
            <Mini label={t(TYPE_LABELS[row.type])} mono value={row.value} onChange={(value) => change({ ...row, value })} onCommit={commit} />
            {row.complex && <p className="props-field__hint">{t('props.complex')}</p>}
          </>
        )}
      />
    </Section>
  );
}

export function FormFieldsSection({ access }: { access: ExtensionAccess }) {
  const { t } = useI18n();
  const fields = readFormFields(access.values);
  return (
    <Section title={t('props.formFields')} count={fields.length}>
      <Rows<FormField>
        rows={fields}
        empty={() => ({ id: '', label: '', type: 'string', defaultValue: '' })}
        addLabel={t('props.add')}
        onWrite={(next) => access.write(writeFormFields(access.values, next, access.create))}
        render={(row, change, commit) => (
          <>
            <div className="props-row__pair">
              <Mini label={t('props.fieldId')} mono value={row.id} onChange={(id) => change({ ...row, id })} onCommit={commit} />
              <Choice
                label={t('props.fieldType')}
                value={row.type}
                options={[...new Set([...FIELD_TYPES, row.type])].map((type) => ({ value: type, label: type }))}
                onChange={(type) => change({ ...row, type }, true)}
              />
            </div>
            <Mini label={t('props.fieldLabel')} value={row.label} onChange={(label) => change({ ...row, label })} onCommit={commit} />
            <Mini
              label={t('props.fieldDefault')}
              value={row.defaultValue}
              onChange={(defaultValue) => change({ ...row, defaultValue })}
              onCommit={commit}
            />
          </>
        )}
      />
    </Section>
  );
}

export { Section as PropsSection };
