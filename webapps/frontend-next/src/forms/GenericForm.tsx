/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import type { VariableMap } from '../api/types';
import { useI18n } from '../i18n';
import { Button, Field, IconButton } from '../ui';
import { FormActions, useServerSave } from './FormHost';
import type { FormRendererProps } from './types';
import { VARIABLE_TYPES, convertInput } from './variables';
import { useFormDraft } from './drafts';
import { DraftNotice } from './DraftNotice';
import { parseEngineDate, toDateTimeInput } from '../lib/dates';

interface Row {
  id: number;
  name: string;
  type: string;
  value: string;
}

let nextRow = 1;

interface GenericDraft {
  rows: Omit<Row, 'id'>[];
  businessKey: string;
}

/** Rows for variables that prefill the form, e.g. those of another instance. */
export function variablesToRows(variables: VariableMap): Omit<Row, 'id'>[] {
  return Object.entries(variables).flatMap(([name, variable]) => {
    const type = (VARIABLE_TYPES as readonly string[]).includes(variable.type ?? '') ? (variable.type as string) : null;
    if (!type || variable.value === undefined) return [];
    const value = variable.value;
    if (type === 'Date') {
      return [{ name, type, value: toDateTimeInput(parseEngineDate(String(value ?? ''))) }];
    }
    if (type === 'Boolean') return [{ name, type, value: value === true || value === 'true' ? 'true' : 'false' }];
    return [{ name, type, value: value === null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value) }];
  });
}

function toDraft(rows: Row[], businessKey: string): GenericDraft {
  return { rows: rows.map(({ name, type, value }) => ({ name, type, value })), businessKey };
}

function fromDraft(draft: { kind: string; values: unknown } | null): { rows: Row[]; businessKey: string } {
  if (!draft) return { rows: [], businessKey: '' };
  const stored: GenericDraft =
    draft.kind === 'variables'
      ? { rows: variablesToRows(draft.values as VariableMap), businessKey: '' }
      : (draft.values as GenericDraft);
  return {
    rows: (Array.isArray(stored.rows) ? stored.rows : []).map((row) => ({ ...row, id: nextRow++ })),
    businessKey: typeof stored.businessKey === 'string' ? stored.businessKey : '',
  };
}

/**
 * The form of a task or start event without a form definition: variables
 * are entered freely, each with a name, a type and a value.
 */
export function GenericForm({ target, disabled, submitLabel, submit, save: saveToServer }: FormRendererProps) {
  const { t } = useI18n();
  const draft = useFormDraft<GenericDraft>(target, 'generic');
  const [initial] = useState(() => fromDraft(draft.restored));
  const [rows, setRows] = useState<Row[]>(initial.rows);
  const [businessKey, setBusinessKey] = useState(initial.businessKey);
  // the entered values are kept as a draft as soon as they differ from what the form started with
  const baseline = useRef(JSON.stringify(toDraft(initial.rows, initial.businessKey)));
  const { save } = draft;
  useEffect(() => {
    const current = toDraft(rows, businessKey);
    if (JSON.stringify(current) !== baseline.current) {
      baseline.current = '';
      save(current);
    }
  }, [rows, businessKey, save]);
  const discard = () => {
    baseline.current = JSON.stringify(toDraft([], ''));
    setRows([]);
    setBusinessKey('');
    setChecked(false);
    draft.discard();
  };
  const [checked, setChecked] = useState(false);
  const [busy, setBusy] = useState(false);

  const update = (id: number, changes: Partial<Row>) =>
    setRows((current) => current.map((row) => (row.id === id ? { ...row, ...changes } : row)));

  const nameError = (row: Row): string | null => {
    const name = row.name.trim();
    if (!name) {
      return t('validation.required');
    }
    return rows.some((other) => other.id !== row.id && other.name.trim() === name)
      ? t('validation.duplicate')
      : null;
  };
  const valueError = (row: Row): string | null => {
    const converted = convertInput(row.type, row.type === 'Boolean' ? row.value === 'true' : row.value);
    return converted.ok ? null : t(converted.error);
  };

  const collect = (): VariableMap => {
    const variables: VariableMap = {};
    for (const row of rows) {
      const converted = convertInput(row.type, row.type === 'Boolean' ? row.value === 'true' : row.value);
      if (converted.ok && !nameError(row)) {
        variables[row.name.trim()] = { value: converted.value, type: row.type };
      }
    }
    return variables;
  };
  const serverSave = useServerSave(saveToServer, () => {
    setChecked(true);
    return rows.some((row) => nameError(row) || valueError(row)) ? null : collect();
  }, draft.clear, rows);

  const onSubmit = async (event?: FormEvent) => {
    event?.preventDefault();
    if (serverSave?.saving) return;
    setChecked(true);
    if (rows.some((row) => nameError(row) || valueError(row))) {
      return;
    }
    const variables = collect();
    setBusy(true);
    try {
      await submit(variables, { businessKey: businessKey.trim() || undefined });
      draft.clear();
    } catch {
      // reported by the host; the entered values are kept
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="form" onSubmit={(event) => void onSubmit(event)} noValidate>
      {!disabled && <DraftNotice draft={draft.restored} disabled={busy} onDiscard={discard} />}
      <fieldset className="form__fields" disabled={disabled || busy}>
        <p className="form__intro">
          {target.type === 'task' ? t('form.generic.task') : t('form.generic.start')}
        </p>
        {target.type === 'start' && (
          <Field label={t('process.businessKey')} hint={t('process.businessKeyHint')}>
            {(props) => (
              <input
                {...props}
                className="input"
                name="businessKey"
                value={businessKey}
                onChange={(event) => setBusinessKey(event.target.value)}
              />
            )}
          </Field>
        )}
        {rows.map((row) => (
          <div key={row.id} className="variable-row">
            <Field label={t('form.variable.name')} error={checked ? nameError(row) : null}>
              {(props) => (
                <input
                  {...props}
                  className="input"
                  value={row.name}
                  autoFocus
                  spellCheck={false}
                  onChange={(event) => update(row.id, { name: event.target.value })}
                />
              )}
            </Field>
            <Field label={t('form.variable.type')}>
              {(props) => (
                <select
                  {...props}
                  className="input"
                  value={row.type}
                  onChange={(event) =>
                    update(row.id, {
                      type: event.target.value,
                      value: event.target.value === 'Boolean' ? 'false' : '',
                    })
                  }
                >
                  {VARIABLE_TYPES.map((type) => (
                    <option key={type}>{type}</option>
                  ))}
                </select>
              )}
            </Field>
            <Field label={t('form.variable.value')} error={checked ? valueError(row) : null}>
              {(props) =>
                row.type === 'Boolean' ? (
                  <select
                    {...props}
                    className="input"
                    value={row.value}
                    onChange={(event) => update(row.id, { value: event.target.value })}
                  >
                    <option value="true">{t('value.true')}</option>
                    <option value="false">{t('value.false')}</option>
                  </select>
                ) : (
                  <input
                    {...props}
                    className="input"
                    type={row.type === 'Date' ? 'datetime-local' : 'text'}
                    inputMode={row.type === 'String' || row.type === 'Date' ? undefined : 'decimal'}
                    value={row.value}
                    onChange={(event) => update(row.id, { value: event.target.value })}
                  />
                )
              }
            </Field>
            <IconButton
              icon={Trash2}
              label={t('form.variable.remove')}
              className="variable-row__remove"
              onClick={() => setRows((current) => current.filter((other) => other.id !== row.id))}
            />
          </div>
        ))}
        <div>
          <Button
            icon={Plus}
            size="sm"
            onClick={() =>
              setRows((current) => [
                ...current,
                { id: nextRow++, name: '', type: 'String', value: '' },
              ])
            }
          >
            {t('form.variable.add')}
          </Button>
        </div>
      </fieldset>
      <FormActions
        label={submitLabel}
        busy={busy}
        disabled={disabled}
        onSubmit={() => void onSubmit()}
        save={serverSave}
      />
    </form>
  );
}
