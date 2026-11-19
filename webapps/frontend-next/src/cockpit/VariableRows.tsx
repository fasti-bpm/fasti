/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Plus, X } from 'lucide-react';
import type { VariableMap } from '../api/types';
import { useI18n, type MessageKey } from '../i18n';
import { Button, IconButton } from '../ui';
import { toVariableValue } from './Variables';

export interface VariableRow {
  name: string;
  type: string;
  value: string;
}

const TYPES = ['String', 'Boolean', 'Integer', 'Long', 'Double', 'Date', 'Json', 'Xml', 'Null'] as const;

export const emptyRow = (): VariableRow => ({ name: '', type: 'String', value: '' });

/** The variables the rows describe, or what is wrong with them. Rows without a name are left out. */
export function rowsToVariables(
  rows: VariableRow[],
): { ok: true; variables: VariableMap } | { ok: false; error: MessageKey; row: number } {
  const variables: VariableMap = {};
  for (const [index, row] of rows.entries()) {
    const name = row.name.trim();
    if (!name) continue;
    if (name in variables) {
      return { ok: false, error: 'ops.variables.duplicate', row: index };
    }
    const converted = toVariableValue(row.type, row.value);
    if (!converted.ok) {
      return { ok: false, error: converted.error, row: index };
    }
    variables[name] = converted.value;
  }
  return { ok: true, variables };
}

/** A small editor for a few variables: name, type and value per row. */
export function VariableRows({
  rows,
  onChange,
  label,
}: {
  rows: VariableRow[];
  onChange: (rows: VariableRow[]) => void;
  label: string;
}) {
  const { t } = useI18n();
  const checked = rowsToVariables(rows);
  const update = (index: number, change: Partial<VariableRow>) =>
    onChange(rows.map((row, position) => (position === index ? { ...row, ...change } : row)));
  return (
    <fieldset className="var-rows">
      <legend className="field__label">{label}</legend>
      {rows.map((row, index) => (
        <div key={index} className="var-rows__row">
          <input
            className="input"
            aria-label={t('ops.variables.name', { row: index + 1 })}
            placeholder={t('search.variableName')}
            value={row.name}
            onChange={(event) => update(index, { name: event.target.value })}
          />
          <select
            className="input"
            aria-label={t('ops.variables.type', { row: index + 1 })}
            value={row.type}
            onChange={(event) => update(index, { type: event.target.value })}
          >
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <input
            className="input"
            aria-label={t('ops.variables.value', { row: index + 1 })}
            placeholder={t('ops.value')}
            disabled={row.type === 'Null'}
            value={row.value}
            onChange={(event) => update(index, { value: event.target.value })}
          />
          <IconButton
            icon={X}
            label={t('ops.variables.remove', { row: index + 1 })}
            onClick={() => onChange(rows.filter((_, position) => position !== index))}
          />
        </div>
      ))}
      {!checked.ok && (
        <p className="field__error" role="alert">
          {t('ops.variables.rowError', { row: checked.row + 1, error: t(checked.error) })}
        </p>
      )}
      <Button size="sm" icon={Plus} onClick={() => onChange([...rows, emptyRow()])}>
        {t('ops.variables.add')}
      </Button>
    </fieldset>
  );
}
