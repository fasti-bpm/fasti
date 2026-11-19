/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { fieldLabels, fieldNames, processCardFields, processKeyOf, setProcessCardFields } from './cardFields';

describe('business fields of task cards', () => {
  it('finds the process of a task by its key, or by the id of its definition', () => {
    expect(processKeyOf({ processDefinitionKey: 'invoice', processDefinitionId: 'x' })).toBe('invoice');
    expect(processKeyOf({ processDefinitionId: 'invoice:3:abc' })).toBe('invoice');
    // the engine generates the id of a definition with a long key; it holds no key
    expect(processKeyOf({ processDefinitionId: '0f6a3c3e-1b2d-11ef-9f1b-0242ac120002' })).toBeNull();
    expect(processKeyOf({ processDefinitionId: null })).toBeNull();
  });

  it('keeps valid fields only and names them by their label', () => {
    window.localStorage.setItem(
      'fasti.next.tasklist.processFields',
      JSON.stringify({ invoice: [{ name: 'amount', label: '' }, { name: '', label: 'x' }, 'junk'], other: 'junk' }),
    );
    const fields = processCardFields();
    expect(fields).toEqual({ invoice: [{ name: 'amount', label: '' }] });
    expect(fieldLabels(fields, 'invoice')).toEqual({ amount: 'amount' });
    expect(fieldLabels(fields, null)).toEqual({});
    expect(fieldNames({ ...fields, order: [{ name: 'customer', label: 'Customer' }] }, ['order', null])).toEqual(['customer']);

    setProcessCardFields({ invoice: [], order: [{ name: 'customer', label: 'Customer' }] });
    expect(processCardFields()).toEqual({ order: [{ name: 'customer', label: 'Customer' }] });
  });
});
