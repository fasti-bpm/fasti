/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState } from 'react';
import { Plus, Trash2, Variable, X } from 'lucide-react';
import { useI18n } from '../i18n';
import { Button, IconButton, cx } from '../ui';
import {
  FILTER_OPERATORS,
  FILTER_TYPES,
  conditionLabel,
  emptyCondition,
  isComplete,
  operatorsFor,
  takesValue,
  type FilterCondition,
  type FilterValueType,
  type VariableFilter,
} from './variableFilter';

/**
 * Conditions on variables, built row by row: name, type, comparison and
 * value; joined with AND or OR. The filter applies when the user asks for
 * it, so a half-typed row does not query the engine.
 */
export function VariableFilterBuilder({
  filter,
  onApply,
}: {
  filter: VariableFilter;
  onApply: (filter: VariableFilter) => void;
}) {
  const { t } = useI18n();
  const id = useId();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<VariableFilter>(filter);
  const active = filter.conditions.length;

  const start = () => {
    setDraft(filter.conditions.length ? filter : { mode: filter.mode, conditions: [emptyCondition()] });
    setOpen(true);
  };
  const update = (index: number, change: Partial<FilterCondition>) =>
    setDraft((current) => ({
      ...current,
      conditions: current.conditions.map((condition, position) => {
        if (position !== index) return condition;
        const next = { ...condition, ...change };
        // a type without the chosen comparison falls back to equality
        if (!operatorsFor(next.type).includes(next.operator)) next.operator = 'eq';
        // a date input cannot show other values, nor other inputs a date: a change to or from a date starts empty
        if (change.type !== undefined && change.type !== condition.type && (change.type === 'date' || condition.type === 'date')) {
          next.value = '';
        }
        if (change.type && change.type !== 'boolean' && condition.type === 'boolean') next.value = '';
        if (change.type === 'boolean' && next.value !== 'true' && next.value !== 'false') next.value = 'true';
        return next;
      }),
    }));
  const complete = draft.conditions.filter(isComplete);
  // a row with a value but no name is incomplete too, rather than dropped without a word
  const incomplete = draft.conditions.some(
    (condition) => (condition.name.trim() || condition.value.trim()) && !isComplete(condition),
  );

  return (
    <div className="vfilter">
      <div className="vfilter__bar">
        <button
          type="button"
          className={cx('toggle', active > 0 && 'is-on')}
          aria-expanded={open}
          aria-controls={`${id}-panel`}
          onClick={() => (open ? setOpen(false) : start())}
        >
          <Variable aria-hidden size={14} />
          {active > 0 ? t('vfilter.buttonCount', { count: active }) : t('vfilter.button')}
        </button>
        {active > 0 && !open && (
          <ul className="chips vfilter__chips" aria-label={t('vfilter.active')}>
            {filter.conditions.map((condition, index) => (
              <li key={index} className="chip">
                {index > 0 && <span className="vfilter__join">{t(filter.mode === 'or' ? 'vfilter.or' : 'vfilter.and')}</span>}
                <span className="mono">{conditionLabel(condition)}</span>
                <button
                  type="button"
                  className="chip__remove"
                  aria-label={t('search.remove', { criterion: conditionLabel(condition) })}
                  onClick={() => onApply({ ...filter, conditions: filter.conditions.filter((_, position) => position !== index) })}
                >
                  <X aria-hidden size={14} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {open && (
        <form
          id={`${id}-panel`}
          className="vfilter__panel"
          aria-label={t('vfilter.title')}
          onSubmit={(event) => {
            event.preventDefault();
            if (incomplete) return;
            onApply({ mode: draft.mode, conditions: complete });
            setOpen(false);
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.stopPropagation();
              setOpen(false);
            }
          }}
        >
          <div className="vfilter__head">
            <span id={`${id}-mode`} className="vfilter__label">
              {t('vfilter.match')}
            </span>
            <div className="segmented" role="group" aria-labelledby={`${id}-mode`}>
              {(['and', 'or'] as const).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  className="segmented__item"
                  aria-pressed={draft.mode === mode}
                  onClick={() => setDraft((current) => ({ ...current, mode }))}
                >
                  {t(mode === 'and' ? 'vfilter.all' : 'vfilter.any')}
                </button>
              ))}
            </div>
          </div>
          <div className="vfilter__columns" aria-hidden>
            <span />
            <span>{t('search.variableName')}</span>
            <span>{t('vfilter.typeColumn')}</span>
            <span>{t('search.operator')}</span>
            <span>{t('search.value')}</span>
          </div>
          <ol className="vfilter__rows">
            {draft.conditions.map((condition, index) => {
              const invalid = !!(condition.name.trim() || condition.value.trim()) && !isComplete(condition);
              const row = index + 1;
              return (
                <li key={index} className="vfilter__row">
                  <span className="vfilter__join" aria-hidden>
                    {index === 0 ? '' : t(draft.mode === 'or' ? 'vfilter.or' : 'vfilter.and')}
                  </span>
                  <label className="vfilter__cell vfilter__name">
                    <span className="vfilter__cell-label" aria-hidden>
                      {t('search.variableName')}
                    </span>
                    <input
                      className="input"
                      aria-invalid={(!condition.name.trim() && !!condition.value.trim()) || undefined}
                      aria-label={t('vfilter.name', { row })}
                      placeholder={t('search.variableName')}
                      value={condition.name}
                      autoFocus={index === draft.conditions.length - 1 && !condition.name}
                      onChange={(event) => update(index, { name: event.target.value })}
                    />
                  </label>
                  <label className="vfilter__cell vfilter__type">
                    <span className="vfilter__cell-label" aria-hidden>
                      {t('vfilter.typeColumn')}
                    </span>
                    <select
                      className="input"
                      aria-label={t('vfilter.type', { row })}
                      value={condition.type}
                      onChange={(event) => update(index, { type: event.target.value as FilterValueType })}
                    >
                      {FILTER_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {t(`vfilter.type.${type}`)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="vfilter__cell vfilter__operator">
                    <span className="vfilter__cell-label" aria-hidden>
                      {t('search.operator')}
                    </span>
                    <select
                      className="input"
                      aria-label={t('vfilter.operator', { row })}
                      value={condition.operator}
                      onChange={(event) => update(index, { operator: event.target.value as FilterCondition['operator'] })}
                    >
                      {FILTER_OPERATORS.filter((entry) => operatorsFor(condition.type).includes(entry.operator)).map((entry) => (
                        <option key={entry.operator} value={entry.operator}>
                          {`${entry.symbol}  ${t(`vfilter.op.${entry.operator}`)}`}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="vfilter__cell vfilter__valuecell">
                    <span className="vfilter__cell-label" aria-hidden>
                      {t('search.value')}
                    </span>
                    {!takesValue(condition.operator) ? (
                      <span className="vfilter__novalue muted">{t('vfilter.noValue')}</span>
                    ) : condition.type === 'boolean' ? (
                      <select
                        className="input vfilter__value"
                        aria-label={t('vfilter.value', { row })}
                        value={condition.value}
                        onChange={(event) => update(index, { value: event.target.value })}
                      >
                        <option value="true">true</option>
                        <option value="false">false</option>
                      </select>
                    ) : (
                      <input
                        className="input vfilter__value"
                        aria-label={t('vfilter.value', { row })}
                        aria-invalid={invalid || undefined}
                        type={condition.type === 'date' ? 'datetime-local' : 'text'}
                        inputMode={condition.type === 'number' ? 'decimal' : undefined}
                        placeholder={condition.operator === 'like' ? t('vfilter.likeHint') : t('vfilter.valueHint')}
                        value={condition.value}
                        onChange={(event) => update(index, { value: event.target.value })}
                      />
                    )}
                  </label>
                  <IconButton
                    icon={Trash2}
                    className="vfilter__remove"
                    label={t('vfilter.removeRow', { row })}
                    onClick={() =>
                      setDraft((current) => ({
                        ...current,
                        conditions: current.conditions.filter((_, position) => position !== index),
                      }))
                    }
                  />
                </li>
              );
            })}
          </ol>
          {incomplete && (
            <p className="vfilter__error" role="alert">
              {t('vfilter.incomplete')}
            </p>
          )}
          <div className="vfilter__actions">
            <Button
              icon={Plus}
              onClick={() => setDraft((current) => ({ ...current, conditions: [...current.conditions, emptyCondition()] }))}
            >
              {t('vfilter.add')}
            </Button>
            <span className="vfilter__spacer" />
            {active > 0 && (
              <Button
                onClick={() => {
                  onApply({ mode: 'and', conditions: [] });
                  setOpen(false);
                }}
              >
                {t('vfilter.clear')}
              </Button>
            )}
            <Button onClick={() => setOpen(false)}>{t('action.cancel')}</Button>
            <Button type="submit" variant="primary" disabled={incomplete || (complete.length === 0 && active === 0)}>
              {t('vfilter.apply')}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
