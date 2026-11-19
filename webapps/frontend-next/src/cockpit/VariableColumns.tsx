/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState, type FormEvent } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Columns3, Plus, X } from 'lucide-react';
import { getColumnValues, getVariableNames, type ColumnValue } from '../api/cockpit';
import { isApiError } from '../api/http';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { readPreference, writePreference } from '../lib/storage';
import { Button, cx } from '../ui';
import { COCKPIT, type Column } from './common';

/**
 * Business variables as columns of the instance lists: the user picks names
 * per process (by the key of its definition), the values of the shown rows are
 * read in one query per name.
 */
const KEY = 'cockpit.variableColumns';
export const MAX_VARIABLE_COLUMNS = 6;
const SHORT = 40;

function storedChoices(): Record<string, unknown> {
  const stored = readPreference<unknown>(KEY, {});
  return stored && typeof stored === 'object' && !Array.isArray(stored) ? { ...(stored as Record<string, unknown>) } : {};
}

function clean(names: unknown[]): string[] {
  const kept = names.filter((name): name is string => typeof name === 'string').map((name) => name.trim());
  return [...new Set(kept.filter(Boolean))].slice(0, MAX_VARIABLE_COLUMNS);
}

/** The chosen names of a process; anything malformed in storage is dropped. */
export function readVariableColumns(processKey: string): string[] {
  const names = storedChoices()[processKey];
  return Array.isArray(names) ? clean(names) : [];
}

export function writeVariableColumns(processKey: string, names: string[]): void {
  const others = Object.entries(storedChoices()).filter(([key]) => key !== processKey);
  const kept = clean(names);
  writePreference(KEY, Object.fromEntries(kept.length ? [...others, [processKey, kept]] : others));
}

/** The chosen names of a process and a setter that remembers them; none without a key. */
export function useVariableColumns(processKey: string | null | undefined): [string[], (names: string[]) => void] {
  const [state, setState] = useState(() => ({ key: processKey, names: processKey ? readVariableColumns(processKey) : [] }));
  // another process: read its own choice
  const current = state.key === processKey ? state : { key: processKey, names: processKey ? readVariableColumns(processKey) : [] };
  if (current !== state) setState(current);
  return [
    current.names,
    (names: string[]) => {
      if (!processKey) return;
      writeVariableColumns(processKey, names);
      setState({ key: processKey, names: readVariableColumns(processKey) });
    },
  ];
}

/** A value in a few characters: objects by their type, long texts cut; `full` for the tooltip. */
export function compactValue(
  variable: ColumnValue | undefined,
  dateTime: (date: Date) => string,
): { text: string; full: string | null; muted: boolean } {
  if (!variable || variable.value === null || variable.value === undefined) {
    return { text: '—', full: null, muted: true };
  }
  const { type, value } = variable;
  if (type === 'Date' && typeof value === 'string') {
    const date = parseEngineDate(value);
    return { text: date ? dateTime(date) : value, full: null, muted: false };
  }
  if (['Object', 'Json', 'Xml', 'File', 'Bytes'].includes(type) || typeof value === 'object') {
    return { text: `{${type}}`, full: null, muted: true };
  }
  const text = String(value);
  return text.length > SHORT
    ? { text: `${text.slice(0, SHORT - 1).trimEnd()}…`, full: text, muted: false }
    : { text, full: null, muted: false };
}

function ValueCell({ variable }: { variable: ColumnValue | undefined }) {
  const { dateTime } = useI18n();
  const shown = compactValue(variable, dateTime);
  return (
    <span className={cx('varcol', shown.muted && 'muted')} title={shown.full ?? undefined}>
      {shown.text}
    </span>
  );
}

/** The values of the chosen variables for the shown rows. */
export function useColumnValues(instanceIds: string[], names: string[], history: boolean) {
  return useQuery({
    queryKey: [COCKPIT, 'variable-columns', history, instanceIds, names],
    queryFn: ({ signal }) => getColumnValues({ instanceIds, names, history, signal }),
    enabled: instanceIds.length > 0 && names.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 10_000,
  });
}

/** One column per chosen name, after the columns of the list. */
export function variableColumnsOf<T>(
  names: string[],
  values: Map<string, Map<string, ColumnValue>> | undefined,
  idOf: (row: T) => string,
): Column<T>[] {
  return names.map((name) => ({
    id: `var:${name}`,
    header: name,
    cell: (row: T) => <ValueCell variable={values?.get(idOf(row))?.get(name)} />,
  }));
}

/**
 * Picks the variables shown as columns: names typed or suggested from the
 * variables of the shown instances, up to a limit, removable one by one.
 */
export function VariableColumnsPicker({
  names,
  onChange,
  instanceIds,
  history,
}: {
  names: string[];
  onChange: (names: string[]) => void;
  /** the shown instances, whose variables are suggested */
  instanceIds: string[];
  history: boolean;
}) {
  const { t } = useI18n();
  const id = useId();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const suggestions = useQuery({
    queryKey: [COCKPIT, 'variable-names', history, instanceIds],
    queryFn: ({ signal }) => getVariableNames(instanceIds, history, signal),
    enabled: open && instanceIds.length > 0,
    staleTime: 60_000,
  });
  const full = names.length >= MAX_VARIABLE_COLUMNS;
  const value = name.trim();
  const add = (event: FormEvent) => {
    event.preventDefault();
    if (!value || full || names.includes(value)) return;
    onChange([...names, value]);
    setName('');
  };
  const offered = (suggestions.data ?? []).filter((candidate) => !names.includes(candidate));
  return (
    <div className="varcols">
      <button
        type="button"
        className={cx('toggle', names.length > 0 && 'is-on')}
        aria-expanded={open}
        aria-controls={`${id}-panel`}
        onClick={() => {
          setOpen(!open);
          setName('');
        }}
      >
        <Columns3 aria-hidden size={14} />
        {names.length > 0 ? t('varcols.buttonCount', { count: names.length }) : t('varcols.button')}
      </button>
      {open && (
        <form
          id={`${id}-panel`}
          className="varcols__panel"
          aria-label={t('varcols.title')}
          onSubmit={add}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              setOpen(false);
            }
          }}
        >
          <p className="varcols__hint">{t('varcols.hint', { max: MAX_VARIABLE_COLUMNS })}</p>
          {names.length > 0 && (
            <ul className="chips" aria-label={t('varcols.chosen')}>
              {names.map((chosen) => (
                <li key={chosen} className="chip">
                  <span className="mono">{chosen}</span>
                  <button
                    type="button"
                    className="chip__remove"
                    aria-label={t('varcols.remove', { name: chosen })}
                    onClick={() => onChange(names.filter((other) => other !== chosen))}
                  >
                    <X aria-hidden size={14} />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="varcols__add">
            <label className="varcols__field">
              <span className="field__label">{t('varcols.name')}</span>
              <input
                className="input"
                list={`${id}-names`}
                value={name}
                disabled={full}
                autoFocus
                placeholder={full ? t('varcols.full', { max: MAX_VARIABLE_COLUMNS }) : t('varcols.namePlaceholder')}
                onChange={(event) => setName(event.target.value)}
              />
              <datalist id={`${id}-names`}>
                {offered.map((candidate) => (
                  <option key={candidate} value={candidate} />
                ))}
              </datalist>
            </label>
            <Button type="submit" icon={Plus} disabled={!value || full || names.includes(value)}>
              {t('varcols.add')}
            </Button>
          </div>
          {offered.length > 0 && !full && (
            <div className="varcols__suggest">
              <span className="field__label">{t('varcols.suggested')}</span>
              <ul className="varcols__options">
                {offered.slice(0, 12).map((candidate) => (
                  <li key={candidate}>
                    <button
                      type="button"
                      className="chip-btn mono"
                      aria-label={t('varcols.addNamed', { name: candidate })}
                      onClick={() => onChange([...names, candidate])}
                    >
                      <Plus aria-hidden size={12} />
                      {candidate}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="varcols__actions">
            {names.length > 0 && <Button onClick={() => onChange([])}>{t('varcols.clear')}</Button>}
            <Button variant="primary" onClick={() => setOpen(false)}>
              {t('varcols.done')}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

/** Says that the values could not be read, so an empty cell is not taken for a missing variable. */
export function VariableColumnsError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const { t } = useI18n();
  if (!error) return null;
  return (
    <p className="varcols__error" role="alert">
      {t(isApiError(error, 'forbidden') ? 'varcols.forbidden' : 'varcols.failed')}
      <button type="button" className="link" onClick={onRetry}>
        {t('varcols.retry')}
      </button>
    </p>
  );
}
