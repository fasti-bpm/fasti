/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import type { TaskListItem } from '../api/types';
import { cardVariablesOf } from './TaskList';

const task = {
  id: 't1',
  variables: [
    { name: 'amount', value: 30, type: 'Integer' },
    { name: 'creditor', value: 'Acme', type: 'String' },
  ],
} as unknown as TaskListItem;

describe('the variables on a task card', () => {
  it('shows only the labelled variables the task has', () => {
    const shown = cardVariablesOf(task, { amount: 'Amount', invoice: 'Invoice' }, {});
    expect(shown.map((variable) => variable.name)).toEqual(['amount']);
  });

  it('adds the missing variables of a filter that shows undefined variables, without value', () => {
    const shown = cardVariablesOf(task, { amount: 'Amount', invoice: 'Invoice' }, {}, ['amount', 'invoice']);
    expect(shown.map((variable) => [variable.name, variable.value])).toEqual([
      ['amount', 30],
      ['invoice', undefined],
    ]);
  });

  it('puts the business fields of the process first and does not repeat them', () => {
    const shown = cardVariablesOf(task, { amount: 'Amount' }, { creditor: 'Creditor' }, ['creditor', 'amount']);
    expect(shown.map((variable) => variable.name)).toEqual(['creditor', 'amount']);
  });
});
