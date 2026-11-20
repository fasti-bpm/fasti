/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useRef, useState } from 'react';
import { detectVariableType, SORTABLE_TYPES, type SortableType } from '../api/engine';
import type { TaskSorting } from '../api/types';
import { useI18n } from '../i18n';
import { Button, Dialog } from '../ui';

/**
 * Chooses a variable to sort tasks by. The engine compares values of one
 * type only, so a wrong type puts the tasks in a seemingly random order:
 * the type the variable is stored with is looked up and preset.
 */
export function VariableSortDialog({
  open,
  current,
  onOpenChange,
  onSort,
}: {
  open: boolean;
  current: TaskSorting;
  onOpenChange: (open: boolean) => void;
  onSort: (sorting: TaskSorting) => void;
}) {
  const { t } = useI18n();
  const id = useId();
  const isVariable = current.sortBy === 'processVariable' || current.sortBy === 'taskVariable';
  const [name, setName] = useState(isVariable ? (current.parameters?.variable ?? '') : '');
  const [scope, setScope] = useState<'processVariable' | 'taskVariable'>(
    current.sortBy === 'taskVariable' ? 'taskVariable' : 'processVariable',
  );
  const [type, setType] = useState<SortableType>((current.parameters?.type as SortableType | undefined) ?? 'String');
  const [detected, setDetected] = useState<'found' | 'unknown' | null>(null);
  const [looking, setLooking] = useState(false);

  // only the answer for the name now in the field counts, and a type the user chose stays
  const lookup = useRef(0);
  const chosen = useRef(false);
  const lookUp = async (variable: string) => {
    if (!variable.trim()) return;
    const ticket = ++lookup.current;
    setLooking(true);
    const found = await detectVariableType(variable.trim()).catch(() => null);
    if (ticket !== lookup.current) return;
    setLooking(false);
    setDetected(found ? 'found' : 'unknown');
    if (found && !chosen.current) setType(found);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t('sort.variable.title')} description={t('sort.variable.hint')}>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          if (!name.trim()) return;
          onSort({ sortBy: scope, sortOrder: current.sortOrder, parameters: { variable: name.trim(), type } });
          onOpenChange(false);
        }}
      >
        <div className="field">
          <label className="field__label" htmlFor={`${id}-name`}>
            {t('search.variableName')}
          </label>
          <input
            id={`${id}-name`}
            className="input"
            value={name}
            autoFocus
            onChange={(event) => {
              setName(event.target.value);
              setDetected(null);
              chosen.current = false;
              lookup.current += 1;
              setLooking(false);
            }}
            onBlur={(event) => void lookUp(event.target.value)}
          />
        </div>
        <div className="field">
          <label className="field__label" htmlFor={`${id}-scope`}>
            {t('sort.variable.scope')}
          </label>
          <select
            id={`${id}-scope`}
            className="input"
            value={scope}
            onChange={(event) => setScope(event.target.value as 'processVariable' | 'taskVariable')}
          >
            <option value="processVariable">{t('search.field.processVariable')}</option>
            <option value="taskVariable">{t('search.field.taskVariable')}</option>
          </select>
        </div>
        <div className="field">
          <label className="field__label" htmlFor={`${id}-type`}>
            {t('sort.variable.type')}
          </label>
          <select
            id={`${id}-type`}
            className="input"
            value={type}
            aria-describedby={`${id}-detected`}
            onChange={(event) => {
              chosen.current = true;
              setType(event.target.value as SortableType);
            }}
          >
            {SORTABLE_TYPES.map((entry) => (
              <option key={entry} value={entry}>
                {entry}
              </option>
            ))}
          </select>
          <p id={`${id}-detected`} className="field__hint" aria-live="polite">
            {looking
              ? t('sort.variable.looking')
              : detected === 'found'
                ? t('sort.variable.detected', { type })
                : detected === 'unknown'
                  ? t('sort.variable.unknown')
                  : ''}
          </p>
        </div>
        <div className="vsort__actions">
          <Button onClick={() => onOpenChange(false)}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" disabled={!name.trim() || looking}>
            {t('sort.variable.apply')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
