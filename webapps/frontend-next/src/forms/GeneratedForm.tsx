/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getFormVariables, getRenderedForm } from '../api/engine';
import type { VariableMap } from '../api/types';
import { useI18n, type Translate } from '../i18n';
import { ErrorState, Field, Loading } from '../ui';
import { FormActions, formTargetKey, useServerSave } from './FormHost';
import {
  parseRenderedForm,
  supportsDateInput,
  toEnginePattern,
  toIsoDate,
  type GeneratedField,
} from './renderedForm';
import type { FormRendererProps } from './types';
import { convertInput } from './variables';
import { useFormDraft, type DraftControl } from './drafts';
import { DraftNotice } from './DraftNotice';

type Values = Record<string, string | boolean>;

function initialValue(field: GeneratedField, variables: VariableMap): string | boolean {
  const current = variables[field.name]?.value;
  const value = current ?? field.defaultValue;
  if (field.control === 'checkbox') {
    return value === true || value === 'true';
  }
  if (field.control === 'date' && supportsDateInput(field.datePattern)) {
    return toIsoDate(value);
  }
  return value === null || value === undefined ? '' : String(value);
}

/** Checks one field; returns the message to show or null. */
export function validateField(
  field: GeneratedField,
  value: string | boolean,
  t: Translate,
): string | null {
  if (field.readOnly || field.control === 'checkbox') {
    return null;
  }
  const text = String(value).trim();
  if (text === '') {
    return field.required ? t('validation.required') : null;
  }
  if (field.control === 'text') {
    const converted = convertInput(field.type, text);
    if (!converted.ok) {
      return t(converted.error);
    }
    if (typeof converted.value === 'number') {
      if (field.min !== null && converted.value < field.min) {
        return t('validation.min', { min: field.min });
      }
      if (field.max !== null && converted.value > field.max) {
        return t('validation.max', { max: field.max });
      }
    }
  }
  if (field.minLength !== null && text.length < field.minLength) {
    return t('validation.minLength', { min: field.minLength });
  }
  if (field.maxLength !== null && text.length > field.maxLength) {
    return t('validation.maxLength', { max: field.maxLength });
  }
  return null;
}

export function toVariables(fields: GeneratedField[], values: Values) {
  const variables: VariableMap = {};
  let businessKey: string | undefined;
  for (const field of fields) {
    const value = values[field.name] ?? '';
    if (field.businessKey) {
      businessKey = String(value).trim() || undefined;
      continue;
    }
    if (field.readOnly) {
      continue;
    }
    if (field.control === 'checkbox') {
      variables[field.name] = { value: value === true, type: 'Boolean' };
      continue;
    }
    const text = String(value).trim();
    if (text === '') {
      variables[field.name] = { value: null, type: field.type };
    } else if (field.type === 'String' || field.control === 'select') {
      // text and choices are sent as entered; only numbers, dates and the like are normalised
      variables[field.name] = { value: String(value), type: field.type };
    } else if (field.control === 'date') {
      variables[field.name] = { value: toEnginePattern(text, field.datePattern), type: field.type };
    } else {
      const converted = convertInput(field.type, text);
      variables[field.name] = { value: converted.ok ? converted.value : text, type: field.type };
    }
  }
  return { variables, businessKey };
}

/** The values a form starts with: the variables, overridden by a draft. */
export function initialValues(
  fields: GeneratedField[],
  variables: VariableMap,
  draft: { kind: string; values: unknown } | null,
): Values {
  const source = draft?.kind === 'variables' ? { ...variables, ...(draft.values as VariableMap) } : variables;
  const values: Values = Object.fromEntries(fields.map((field) => [field.name, initialValue(field, source)]));
  if (draft && draft.kind !== 'variables') {
    const stored = draft.values as Values;
    for (const field of fields) {
      const value = stored[field.name];
      if (!field.readOnly && (typeof value === 'string' || typeof value === 'boolean')) values[field.name] = value;
    }
  }
  return values;
}

function GeneratedFields({
  fields,
  variables,
  disabled,
  submitLabel,
  submit,
  save,
  draft,
}: {
  fields: GeneratedField[];
  variables: VariableMap;
  draft: DraftControl<Values>;
} & Pick<FormRendererProps, 'disabled' | 'submitLabel' | 'submit' | 'save'>) {
  const { t } = useI18n();
  const [values, setValues] = useState<Values>(() => initialValues(fields, variables, draft.restored));
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState(false);

  const errors = useMemo(
    () =>
      Object.fromEntries(
        fields.map((field) => [field.name, validateField(field, values[field.name] ?? '', t)]),
      ),
    [fields, values, t],
  );

  const set = (name: string, value: string | boolean) => {
    const next = { ...values, [name]: value };
    setValues(next);
    draft.save(next);
  };
  const touch = (name: string) => setTouched((current) => ({ ...current, [name]: true }));

  const onSubmit = async (event?: FormEvent) => {
    event?.preventDefault();
    if (serverSave?.saving) return;
    setTouched(Object.fromEntries(fields.map((field) => [field.name, true])));
    const firstInvalid = fields.find((field) => errors[field.name]);
    if (firstInvalid) {
      document.getElementsByName(firstInvalid.name)[0]?.focus();
      return;
    }
    const { variables: submitted, businessKey } = toVariables(fields, values);
    setBusy(true);
    try {
      await submit(submitted, { businessKey });
      draft.clear();
    } catch {
      // reported by the host; the entered values are kept
    } finally {
      setBusy(false);
    }
  };

  // what is valid is saved; a field still in error keeps its value in the draft only
  const serverSave = useServerSave(
    save,
    () => toVariables(fields.filter((field) => !errors[field.name]), values).variables,
    // a field left out for its error keeps the whole draft
    () => {
      if (!fields.some((field) => errors[field.name])) draft.clear();
    },
    values,
  );
  const partlySaved = serverSave && { ...serverSave, partial: fields.some((field) => !!errors[field.name]) };

  return (
    <form className="form" onSubmit={(event) => void onSubmit(event)} noValidate>
      {!disabled && <DraftNotice draft={draft.restored} disabled={busy} onDiscard={draft.discard} />}
      <fieldset className="form__fields" disabled={disabled || busy}>
        {fields.map((field) => {
          const value = values[field.name] ?? '';
          const error = touched[field.name] ? errors[field.name] : null;
          if (field.control === 'checkbox') {
            return (
              <label key={field.name} className="checkbox">
                <input
                  type="checkbox"
                  name={field.name}
                  checked={value === true}
                  disabled={field.readOnly}
                  onChange={(event) => set(field.name, event.target.checked)}
                />
                {field.label}
              </label>
            );
          }
          return (
            <Field
              key={field.name}
              label={field.label}
              required={field.required}
              error={error}
              hint={
                field.control === 'date' && !supportsDateInput(field.datePattern)
                  ? (field.datePattern ?? undefined)
                  : undefined
              }
            >
              {(props) =>
                field.control === 'select' ? (
                  <select
                    {...props}
                    className="input"
                    name={field.name}
                    value={String(value)}
                    disabled={field.readOnly}
                    onChange={(event) => set(field.name, event.target.value)}
                    onBlur={() => touch(field.name)}
                  >
                    {!field.options.some((option) => option.value === String(value)) && (
                      <option value="">{t('form.choose')}</option>
                    )}
                    {field.options.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    {...props}
                    className="input"
                    name={field.name}
                    type={
                      field.control === 'date' && supportsDateInput(field.datePattern)
                        ? 'date'
                        : 'text'
                    }
                    inputMode={
                      field.control === 'text' && field.type !== 'String'
                        ? field.type === 'Double'
                          ? 'decimal'
                          : 'numeric'
                        : undefined
                    }
                    value={String(value)}
                    readOnly={field.readOnly}
                    required={field.required}
                    onChange={(event) => set(field.name, event.target.value)}
                    onBlur={() => touch(field.name)}
                  />
                )
              }
            </Field>
          );
        })}
      </fieldset>
      <FormActions
        label={submitLabel}
        busy={busy}
        disabled={disabled}
        onSubmit={() => void onSubmit()}
        save={partlySaved}
      />
    </form>
  );
}

/** Renders the form fields that are defined in the process model. */
export function GeneratedForm({ target, disabled, submitLabel, submit, save }: FormRendererProps) {
  const { t } = useI18n();
  const draft = useFormDraft<Values>(target, 'generated');
  const query = useQuery({
    queryKey: ['generated-form', formTargetKey(target)],
    queryFn: async () => {
      const [markup, variables] = await Promise.all([
        getRenderedForm(target),
        getFormVariables(target),
      ]);
      return { fields: parseRenderedForm(markup), variables };
    },
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) {
    return <Loading label={t('form.loading')} />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  return (
    <GeneratedFields
      key={draft.generation}
      draft={draft}
      fields={query.data.fields}
      variables={query.data.variables}
      disabled={disabled}
      submitLabel={submitLabel}
      submit={submit}
      save={save}
    />
  );
}
