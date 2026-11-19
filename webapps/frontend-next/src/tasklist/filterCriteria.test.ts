/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { criterionError, fromQuery, toQuery } from './filterCriteria';

describe('filter criteria', () => {
  it('reads a stored query, expressions included, and keeps what it does not know', () => {
    const editable = fromQuery({
      candidateGroupsExpression: '${currentUserGroups()}',
      assigneeExpression: '${currentUser()}',
      processDefinitionKeyIn: ['invoice', 'order'],
      unassigned: true,
      processVariables: [{ name: 'amount', operator: 'gt', value: 100 }],
      sorting: [{ sortBy: 'created', sortOrder: 'desc' }],
      orQueries: [{ assignee: 'mary' }],
    });
    expect(editable.criteria).toEqual([
      { key: 'candidateGroups', value: '${currentUserGroups()}' },
      { key: 'assignee', value: '${currentUser()}' },
      { key: 'processDefinitionKeyIn', value: 'invoice, order' },
      { key: 'unassigned', value: 'true' },
    ]);
    expect(editable.variables).toEqual([{ scope: 'processVariables', name: 'amount', operator: 'gt', value: '100', stored: 100 }]);
    expect(Object.keys(editable.rest)).toEqual(['sorting', 'orQueries']);
  });

  it('writes expressions under their own key and types the values', () => {
    const query = toQuery({
      criteria: [
        { key: 'candidateGroups', value: ' ${currentUserGroups()} ' },
        { key: 'tenantIdIn', value: 'a, b,,' },
        { key: 'minPriority', value: '40' },
        { key: 'dueBefore', value: '${dateTime().plusDays(2)}' },
        { key: 'withoutDueDate', value: 'true' },
        { key: 'nameLike', value: '' },
      ],
      variables: [
        { scope: 'taskVariables', name: 'approved', operator: 'eq', value: 'true' },
        { scope: 'taskVariables', name: 'region', operator: 'like', value: 'north%' },
        { scope: 'processVariables', name: ' ', operator: 'eq', value: 'x' },
      ],
      rest: { sorting: [] },
    });
    expect(query).toEqual({
      sorting: [],
      candidateGroupsExpression: '${currentUserGroups()}',
      tenantIdIn: ['a', 'b'],
      minPriority: 40,
      dueBeforeExpression: '${dateTime().plusDays(2)}',
      withoutDueDate: true,
      taskVariables: [
        { name: 'approved', operator: 'eq', value: true },
        { name: 'region', operator: 'like', value: 'north%' },
      ],
    });
  });

  it('rejects expressions where the query takes none, and malformed numbers', () => {
    expect(criterionError({ key: 'processDefinitionKey', value: '${x}' })).toBe('filterEdit.noExpression');
    expect(criterionError({ key: 'priority', value: 'high' })).toBe('filterEdit.number');
    expect(criterionError({ key: 'assignee', value: '' })).toBe('validation.required');
    expect(criterionError({ key: 'assignee', value: '${currentUser()}' })).toBeNull();
    expect(criterionError({ key: 'unassigned', value: 'true' })).toBeNull();
  });
});

describe('variable comparisons of a saved filter', () => {
  it('keeps the type of values that were not edited', () => {
    const editable = fromQuery({
      processVariables: [
        { name: 'code', operator: 'eq', value: '001' },
        { name: 'flag', operator: 'eq', value: 'true' },
        { name: 'gone', operator: 'eq', value: null },
      ],
    });
    expect(toQuery(editable).processVariables).toEqual([
      { name: 'code', operator: 'eq', value: '001' },
      { name: 'flag', operator: 'eq', value: 'true' },
      { name: 'gone', operator: 'eq', value: null },
    ]);
    const edited = { ...editable, variables: editable.variables.map((row) => (row.name === 'code' ? { ...row, value: '42' } : row)) };
    expect(toQuery(edited).processVariables).toContainEqual({ name: 'code', operator: 'eq', value: 42 });
  });
});
