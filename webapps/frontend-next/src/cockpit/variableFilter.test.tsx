/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { fireEvent, render, screen } from '@testing-library/react';
import { Tooltip } from 'radix-ui';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { toEngineDate } from '../lib/dates';
import { historicQueryBody } from '../api/operations';
import { readVariableFilter } from './DefinitionPage';
import { historicQueryOf, historyVariableFilter } from './HistoryPage';
import { VariableFilterBuilder } from './VariableFilterBuilder';
import {
  conditionLabel,
  decodeFilter,
  encodeFilter,
  isComplete,
  operatorsFor,
  toEngineQuery,
  type FilterCondition,
  type VariableFilter,
} from './variableFilter';

const condition = (extra: Partial<FilterCondition>): FilterCondition => ({
  name: 'amount',
  operator: 'eq',
  type: 'auto',
  value: '',
  ...extra,
});

describe('variable filter', () => {
  it('sends typed values: numbers, booleans, dates and text that looks like a number', () => {
    const filter: VariableFilter = {
      mode: 'and',
      conditions: [
        condition({ operator: 'gt', type: 'number', value: '100.5' }),
        condition({ name: 'vip', type: 'boolean', value: 'true' }),
        condition({ name: 'code', type: 'string', value: '0042' }),
        condition({ name: 'due', operator: 'lt', type: 'date', value: '2026-03-01T09:30' }),
        condition({ name: 'auto', value: '12' }),
      ],
    };
    expect(toEngineQuery(filter).variables).toEqual([
      { name: 'amount', operator: 'gt', value: 100.5 },
      { name: 'vip', operator: 'eq', value: true },
      { name: 'code', operator: 'eq', value: '0042' },
      { name: 'due', operator: 'lt', value: toEngineDate(new Date(2026, 2, 1, 9, 30)) },
      { name: 'auto', operator: 'eq', value: 12 },
    ]);
  });

  it('turns null checks into comparisons with null and wraps a like without a wildcard', () => {
    const query = toEngineQuery({
      mode: 'and',
      conditions: [
        condition({ operator: 'null' }),
        condition({ name: 'x', operator: 'notNull' }),
        condition({ name: 'customer', operator: 'like', value: 'Nguyen' }),
        condition({ name: 'mail', operator: 'like', type: 'string', value: '%@bank.vn' }),
      ],
    });
    expect(query.variables).toEqual([
      { name: 'amount', operator: 'eq', value: null },
      { name: 'x', operator: 'neq', value: null },
      { name: 'customer', operator: 'like', value: '%Nguyen%' },
      { name: 'mail', operator: 'like', value: '%@bank.vn' },
    ]);
  });

  it('joins with OR in one nested query and leaves incomplete rows out', () => {
    const filter: VariableFilter = {
      mode: 'or',
      conditions: [
        condition({ value: '5' }),
        condition({ name: 'state', value: 'open' }),
        condition({ name: 'n', type: 'number', value: 'abc' }),
        condition({ name: '' }),
      ],
    };
    expect(filter.conditions.map(isComplete)).toEqual([true, true, false, false]);
    expect(toEngineQuery(filter)).toEqual({
      orQueries: [
        {
          variables: [
            { name: 'amount', operator: 'eq', value: 5 },
            { name: 'state', operator: 'eq', value: 'open' },
          ],
        },
      ],
    });
    // a single condition needs no OR
    expect(toEngineQuery({ mode: 'or', conditions: [condition({ value: '1' })] })).toEqual({
      variables: [{ name: 'amount', operator: 'eq', value: 1 }],
    });
  });

  it('round-trips through the address, also names with special characters', () => {
    const filter: VariableFilter = {
      mode: 'or',
      conditions: [condition({ name: 'số hồ sơ=a&b"c', type: 'string', value: 'x,y' }), condition({ operator: 'null' })],
    };
    expect(decodeFilter(encodeFilter(filter))).toEqual(filter);
    expect(encodeFilter({ mode: 'and', conditions: [condition({})] })).toBeNull();
    expect(decodeFilter('{"m":"or","c":[["a","bogus","auto","1"],["b","gt","weird",3]]')).toEqual({ mode: 'and', conditions: [] });
    expect(decodeFilter('{"m":"or","c":[["a","bogus","auto","1"],["b","gt","weird",3]]}')).toEqual({
      mode: 'or',
      conditions: [{ name: 'b', operator: 'gt', type: 'auto', value: '' }],
    });
  });

  it('offers only comparisons that fit the type', () => {
    expect(operatorsFor('boolean')).toEqual(['eq', 'neq', 'null', 'notNull']);
    expect(operatorsFor('number')).not.toContain('like');
    expect(operatorsFor('string')).toContain('like');
  });

  it('labels conditions for chips', () => {
    expect(conditionLabel(condition({ operator: 'gteq', value: '5' }))).toBe('amount ≥ 5');
    expect(conditionLabel(condition({ type: 'string', value: '5' }))).toBe('amount = "5"');
    expect(conditionLabel(condition({ operator: 'null' }))).toBe('amount ∅');
  });

  it('reads links from before the builder', () => {
    expect(readVariableFilter('[{"name":"amount","operator":"gt","value":5},{"name":"c","operator":"eq","value":"x"}]')).toEqual({
      mode: 'and',
      conditions: [condition({ operator: 'gt', value: '5' }), condition({ name: 'c', value: '"x"' })],
    });
    expect(historyVariableFilter('done=true').conditions).toEqual([condition({ name: 'done', value: 'true' })]);
  });

  it('puts OR conditions of the history next to the canceled state as a second nested query', () => {
    const raw = encodeFilter({ mode: 'or', conditions: [condition({ value: '1' }), condition({ name: 'b', value: '2' })] }) ?? '';
    const query = historicQueryOf(new URLSearchParams({ hstate: 'canceled', hvar: raw }));
    expect(historicQueryBody(query).orQueries).toEqual([
      { externallyTerminated: true, internallyTerminated: true },
      {
        variables: [
          { name: 'amount', operator: 'eq', value: 1 },
          { name: 'b', operator: 'eq', value: 2 },
        ],
      },
    ]);
  });
});

describe('variable filter builder', () => {
  const renderBuilder = (filter: VariableFilter, onApply = vi.fn()) => {
    render(
      <I18nProvider initial="en">
        <Tooltip.Provider>
          <VariableFilterBuilder filter={filter} onApply={onApply} />
        </Tooltip.Provider>
      </I18nProvider>,
    );
    return onApply;
  };

  it('builds two typed conditions joined with OR and applies them', () => {
    const onApply = renderBuilder({ mode: 'and', conditions: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Variables' }));
    fireEvent.change(screen.getByLabelText('Variable name, row 1'), { target: { value: 'amount' } });
    fireEvent.change(screen.getByLabelText('Type, row 1'), { target: { value: 'number' } });
    fireEvent.change(screen.getByLabelText('Comparison, row 1'), { target: { value: 'gt' } });
    fireEvent.change(screen.getByLabelText('Value, row 1'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add condition' }));
    fireEvent.change(screen.getByLabelText('Variable name, row 2'), { target: { value: 'note' } });
    fireEvent.change(screen.getByLabelText('Comparison, row 2'), { target: { value: 'null' } });
    expect(screen.queryByLabelText('Value, row 2')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Any condition' }));
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(onApply).toHaveBeenCalledWith({
      mode: 'or',
      conditions: [condition({ operator: 'gt', type: 'number', value: '100' }), condition({ name: 'note', operator: 'null' })],
    });
  });

  it('blocks applying a row with an invalid value and explains why', () => {
    const onApply = renderBuilder({ mode: 'and', conditions: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Variables' }));
    fireEvent.change(screen.getByLabelText('Variable name, row 1'), { target: { value: 'amount' } });
    fireEvent.change(screen.getByLabelText('Type, row 1'), { target: { value: 'number' } });
    fireEvent.change(screen.getByLabelText('Value, row 1'), { target: { value: 'many' } });
    expect(screen.getByRole('alert')).toHaveTextContent('Complete or remove');
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    expect(onApply).not.toHaveBeenCalled();
  });

  it('keeps a date while it is edited and clears it only when the type changes', () => {
    const onApply = renderBuilder({ mode: 'and', conditions: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Variables' }));
    fireEvent.change(screen.getByLabelText('Type, row 1'), { target: { value: 'date' } });
    fireEvent.change(screen.getByLabelText('Value, row 1'), { target: { value: '2026-03-01T09:30' } });
    fireEvent.change(screen.getByLabelText('Variable name, row 1'), { target: { value: 'due' } });
    fireEvent.change(screen.getByLabelText('Comparison, row 1'), { target: { value: 'lt' } });
    expect(screen.getByLabelText('Value, row 1')).toHaveValue('2026-03-01T09:30');
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(onApply).toHaveBeenCalledWith({ mode: 'and', conditions: [condition({ name: 'due', operator: 'lt', type: 'date', value: '2026-03-01T09:30' })] });
  });

  it('starts a yes/no value as a valid choice, also coming from a date', () => {
    renderBuilder({ mode: 'and', conditions: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Variables' }));
    fireEvent.change(screen.getByLabelText('Variable name, row 1'), { target: { value: 'vip' } });
    fireEvent.change(screen.getByLabelText('Type, row 1'), { target: { value: 'date' } });
    fireEvent.change(screen.getByLabelText('Value, row 1'), { target: { value: '2026-03-01T09:30' } });
    fireEvent.change(screen.getByLabelText('Type, row 1'), { target: { value: 'boolean' } });
    expect(screen.getByLabelText('Value, row 1')).toHaveValue('true');
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
  });

  it('flags a value without a name instead of dropping it', () => {
    renderBuilder({ mode: 'and', conditions: [] });
    fireEvent.click(screen.getByRole('button', { name: 'Variables' }));
    expect(screen.getByRole('button', { name: 'Apply' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Value, row 1'), { target: { value: '5' } });
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByLabelText('Variable name, row 1')).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows applied conditions as removable chips', () => {
    const onApply = renderBuilder({ mode: 'or', conditions: [condition({ value: '1' }), condition({ name: 'b', value: '2' })] });
    expect(screen.getByRole('button', { name: 'Variables (2)' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remove the criterion amount = 1' }));
    expect(onApply).toHaveBeenCalledWith({ mode: 'or', conditions: [condition({ name: 'b', value: '2' })] });
  });
});
