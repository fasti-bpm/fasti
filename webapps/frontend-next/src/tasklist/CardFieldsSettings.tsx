/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { getProcessKeys } from '../api/engine';
import { useI18n } from '../i18n';
import {
  MAX_FIELDS,
  processCardFields,
  setProcessCardFields,
  type ProcessCardFields,
} from '../lib/cardFields';
import { Button, IconButton } from '../ui';

/**
 * The business fields per process: which process variables the task cards of
 * a process show, under which name. Kept with the other preferences of the user.
 */
export function CardFieldsSettings() {
  const { t } = useI18n();
  const id = useId();
  const [fields, setFields] = useState<ProcessCardFields>(processCardFields);
  const [process, setProcess] = useState('');
  const [name, setName] = useState('');
  const [label, setLabel] = useState('');
  const processes = useQuery({ queryKey: ['card-field-processes'], queryFn: ({ signal }) => getProcessKeys(signal), staleTime: 60_000 });
  const processName = (key: string) => processes.data?.find((entry) => entry.key === key)?.name ?? key;

  const save = (next: ProcessCardFields) => {
    setProcessCardFields(next);
    setFields(processCardFields());
  };
  const key = process.trim();
  const variable = name.trim();
  const full = (fields[key]?.length ?? 0) >= MAX_FIELDS;
  const duplicate = !!fields[key]?.some((field) => field.name === variable);
  const add = (event: FormEvent) => {
    event.preventDefault();
    if (!key || !variable || full || duplicate) return;
    save({ ...fields, [key]: [...(fields[key] ?? []), { name: variable, label: label.trim() }] });
    setName('');
    setLabel('');
  };

  const configured = Object.keys(fields).sort((a, b) => processName(a).localeCompare(processName(b)));
  return (
    <fieldset className="prefs__group card-fields">
      <legend className="prefs__legend">{t('cardFields.title')}</legend>
      <p className="field__hint">{t('cardFields.hint')}</p>
      {configured.length === 0 ? (
        <p className="muted card-fields__empty">{t('cardFields.empty')}</p>
      ) : (
        <ul className="card-fields__list">
          {configured.map((processKey) => (
            <li key={processKey} className="card-fields__process">
              <span className="card-fields__name">
                {processName(processKey)}
                {processName(processKey) !== processKey && <span className="mono muted"> {processKey}</span>}
              </span>
              <ul className="card-fields__fields" aria-label={t('cardFields.of', { process: processName(processKey) })}>
                {(fields[processKey] ?? []).map((field) => (
                  <li key={field.name} className="chip card-fields__chip">
                    <span>
                      {field.label || field.name}
                      {field.label && field.label !== field.name && <span className="mono muted"> {field.name}</span>}
                    </span>
                    <IconButton
                      icon={Trash2}
                      className="chip__remove"
                      label={t('cardFields.remove', { field: field.label || field.name, process: processName(processKey) })}
                      onClick={() =>
                        save({ ...fields, [processKey]: (fields[processKey] ?? []).filter((candidate) => candidate.name !== field.name) })
                      }
                    />
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
      <form className="card-fields__form" onSubmit={add} aria-label={t('cardFields.add')}>
        <label className="card-fields__input">
          <span className="field__label">{t('cardFields.process')}</span>
          <input
            className="input"
            list={`${id}-processes`}
            value={process}
            onChange={(event) => setProcess(event.target.value)}
            autoComplete="off"
            required
          />
          <datalist id={`${id}-processes`}>
            {processes.data?.map((entry) => (
              <option key={entry.key} value={entry.key}>
                {entry.name ?? entry.key}
              </option>
            ))}
          </datalist>
        </label>
        <label className="card-fields__input">
          <span className="field__label">{t('cardFields.variable')}</span>
          <input className="input mono" value={name} onChange={(event) => setName(event.target.value)} autoComplete="off" required />
        </label>
        <label className="card-fields__input">
          <span className="field__label">{t('cardFields.label')}</span>
          <input className="input" value={label} onChange={(event) => setLabel(event.target.value)} autoComplete="off" />
        </label>
        <Button type="submit" icon={Plus} disabled={!key || !variable || full || duplicate}>
          {t('cardFields.add')}
        </Button>
      </form>
      {(full || duplicate) && (
        <p className="field__hint" role="status">
          {full ? t('cardFields.full', { count: MAX_FIELDS }) : t('cardFields.duplicate')}
        </p>
      )}
    </fieldset>
  );
}
