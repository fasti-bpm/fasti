/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { TaskQuery } from '../api/engine';
import type { MessageKey } from '../i18n';

/**
 * How the value of a criterion is entered: text, a comma separated list, a
 * whole number, a date (or expression) or a flag that is either set or not.
 */
export type CriterionKind = 'text' | 'list' | 'number' | 'date' | 'flag' | 'delegation';

export interface CriterionDefinition {
  key: string;
  label: MessageKey;
  kind: CriterionKind;
  group: 'process' | 'task' | 'user' | 'date' | 'other';
  /** the task query accepts `${…}` for this criterion under `<key>Expression` */
  expression?: boolean;
}

/** The criteria of the task query that the filter dialog offers. */
export const CRITERIA: CriterionDefinition[] = [
  { key: 'processDefinitionKey', label: 'filterEdit.c.processDefinitionKey', kind: 'text', group: 'process' },
  { key: 'processDefinitionKeyIn', label: 'filterEdit.c.processDefinitionKeyIn', kind: 'list', group: 'process' },
  { key: 'processDefinitionName', label: 'filterEdit.c.processDefinitionName', kind: 'text', group: 'process' },
  { key: 'processDefinitionNameLike', label: 'filterEdit.c.processDefinitionNameLike', kind: 'text', group: 'process' },
  { key: 'processInstanceBusinessKey', label: 'filterEdit.c.businessKey', kind: 'text', group: 'process', expression: true },
  { key: 'processInstanceBusinessKeyLike', label: 'filterEdit.c.businessKeyLike', kind: 'text', group: 'process', expression: true },
  { key: 'processInstanceId', label: 'filterEdit.c.processInstanceId', kind: 'text', group: 'process' },
  { key: 'name', label: 'filterEdit.c.name', kind: 'text', group: 'task' },
  { key: 'nameLike', label: 'filterEdit.c.nameLike', kind: 'text', group: 'task' },
  { key: 'descriptionLike', label: 'filterEdit.c.descriptionLike', kind: 'text', group: 'task' },
  { key: 'taskDefinitionKey', label: 'filterEdit.c.taskDefinitionKey', kind: 'text', group: 'task' },
  { key: 'taskDefinitionKeyIn', label: 'filterEdit.c.taskDefinitionKeyIn', kind: 'list', group: 'task' },
  { key: 'priority', label: 'filterEdit.c.priority', kind: 'number', group: 'task' },
  { key: 'minPriority', label: 'filterEdit.c.minPriority', kind: 'number', group: 'task' },
  { key: 'maxPriority', label: 'filterEdit.c.maxPriority', kind: 'number', group: 'task' },
  { key: 'delegationState', label: 'filterEdit.c.delegationState', kind: 'delegation', group: 'task' },
  { key: 'assignee', label: 'filterEdit.c.assignee', kind: 'text', group: 'user', expression: true },
  { key: 'assigneeLike', label: 'filterEdit.c.assigneeLike', kind: 'text', group: 'user', expression: true },
  { key: 'owner', label: 'filterEdit.c.owner', kind: 'text', group: 'user', expression: true },
  { key: 'candidateUser', label: 'filterEdit.c.candidateUser', kind: 'text', group: 'user', expression: true },
  { key: 'candidateGroup', label: 'filterEdit.c.candidateGroup', kind: 'text', group: 'user', expression: true },
  { key: 'candidateGroups', label: 'filterEdit.c.candidateGroups', kind: 'list', group: 'user', expression: true },
  { key: 'involvedUser', label: 'filterEdit.c.involvedUser', kind: 'text', group: 'user', expression: true },
  { key: 'unassigned', label: 'filterEdit.c.unassigned', kind: 'flag', group: 'user' },
  { key: 'assigned', label: 'filterEdit.c.assigned', kind: 'flag', group: 'user' },
  { key: 'includeAssignedTasks', label: 'filterEdit.c.includeAssignedTasks', kind: 'flag', group: 'user' },
  { key: 'withCandidateGroups', label: 'filterEdit.c.withCandidateGroups', kind: 'flag', group: 'user' },
  { key: 'withoutCandidateGroups', label: 'filterEdit.c.withoutCandidateGroups', kind: 'flag', group: 'user' },
  { key: 'dueDate', label: 'filterEdit.c.dueDate', kind: 'date', group: 'date', expression: true },
  { key: 'dueBefore', label: 'filterEdit.c.dueBefore', kind: 'date', group: 'date', expression: true },
  { key: 'dueAfter', label: 'filterEdit.c.dueAfter', kind: 'date', group: 'date', expression: true },
  { key: 'withoutDueDate', label: 'filterEdit.c.withoutDueDate', kind: 'flag', group: 'date' },
  { key: 'followUpDate', label: 'filterEdit.c.followUpDate', kind: 'date', group: 'date', expression: true },
  { key: 'followUpBefore', label: 'filterEdit.c.followUpBefore', kind: 'date', group: 'date', expression: true },
  { key: 'followUpAfter', label: 'filterEdit.c.followUpAfter', kind: 'date', group: 'date', expression: true },
  { key: 'followUpBeforeOrNotExistent', label: 'filterEdit.c.followUpBeforeOrNotExistent', kind: 'date', group: 'date', expression: true },
  { key: 'createdOn', label: 'filterEdit.c.createdOn', kind: 'date', group: 'date', expression: true },
  { key: 'createdBefore', label: 'filterEdit.c.createdBefore', kind: 'date', group: 'date', expression: true },
  { key: 'createdAfter', label: 'filterEdit.c.createdAfter', kind: 'date', group: 'date', expression: true },
  { key: 'tenantIdIn', label: 'filterEdit.c.tenantIdIn', kind: 'list', group: 'other' },
  { key: 'withoutTenantId', label: 'filterEdit.c.withoutTenantId', kind: 'flag', group: 'other' },
  { key: 'active', label: 'filterEdit.c.active', kind: 'flag', group: 'other' },
  { key: 'suspended', label: 'filterEdit.c.suspended', kind: 'flag', group: 'other' },
];

const BY_KEY = new Map(CRITERIA.map((criterion) => [criterion.key, criterion]));

export function criterionOf(key: string): CriterionDefinition | undefined {
  return BY_KEY.get(key);
}

/** Expressions such as `${currentUser()}` that the dialog suggests. */
export const EXPRESSIONS = [
  '${currentUser()}',
  '${currentUserGroups()}',
  '${now()}',
  '${dateTime().plusDays(1)}',
  '${dateTime().minusDays(1)}',
];

const EXPRESSION = /^\$\{.+\}$/s;

export function isExpression(value: string): boolean {
  return EXPRESSION.test(value.trim());
}

/** One criterion as edited: its key and the value as text (flags: `true`). */
export interface CriterionRow {
  key: string;
  value: string;
}

export type VariableScope = 'processVariables' | 'taskVariables' | 'caseInstanceVariables';

export const VARIABLE_SCOPES: VariableScope[] = ['processVariables', 'taskVariables', 'caseInstanceVariables'];

export type VariableOperator = 'eq' | 'neq' | 'gt' | 'gteq' | 'lt' | 'lteq' | 'like';

export const VARIABLE_OPERATORS: { operator: VariableOperator; symbol: string }[] = [
  { operator: 'eq', symbol: '=' },
  { operator: 'neq', symbol: '≠' },
  { operator: 'gt', symbol: '>' },
  { operator: 'gteq', symbol: '≥' },
  { operator: 'lt', symbol: '<' },
  { operator: 'lteq', symbol: '≤' },
  { operator: 'like', symbol: 'like' },
];

export interface VariableCriterionRow {
  scope: VariableScope;
  name: string;
  operator: VariableOperator;
  value: string;
  /** the stored value, kept with its type while the text is not changed */
  stored?: unknown;
}

export interface EditableQuery {
  criteria: CriterionRow[];
  variables: VariableCriterionRow[];
  /** whatever the dialog does not know, e.g. sorting or `orQueries`; kept as it is */
  rest: TaskQuery;
}

function asText(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(String).join(', ');
  }
  if (value === null || value === undefined) {
    return '';
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** Splits a stored task query into what the dialog edits and the rest. */
export function fromQuery(query: TaskQuery | null | undefined): EditableQuery {
  const criteria: CriterionRow[] = [];
  const variables: VariableCriterionRow[] = [];
  const rest: TaskQuery = {};
  for (const [storedKey, value] of Object.entries(query ?? {})) {
    if (value === null || value === undefined) {
      continue;
    }
    if ((VARIABLE_SCOPES as string[]).includes(storedKey) && Array.isArray(value)) {
      for (const entry of value as { name?: string; operator?: string; value?: unknown }[]) {
        variables.push({
          scope: storedKey as VariableScope,
          name: entry.name ?? '',
          operator: (VARIABLE_OPERATORS.some((op) => op.operator === entry.operator)
            ? entry.operator
            : 'eq') as VariableOperator,
          value: asText(entry.value),
          stored: entry.value,
        });
      }
      continue;
    }
    const plainKey = storedKey.endsWith('Expression') ? storedKey.slice(0, -'Expression'.length) : storedKey;
    const definition = BY_KEY.get(plainKey);
    if (!definition || (plainKey !== storedKey && !definition.expression)) {
      rest[storedKey] = value;
      continue;
    }
    if (definition.kind === 'flag') {
      if (value === true) {
        criteria.push({ key: plainKey, value: 'true' });
      } else {
        rest[storedKey] = value;
      }
      continue;
    }
    criteria.push({ key: plainKey, value: asText(value) });
  }
  return { criteria, variables, rest };
}

/** A typed value of a variable criterion: numbers and flags as such, else text. */
export function variableValue(text: string): unknown {
  const trimmed = text.trim();
  if (trimmed === 'true' || trimmed === 'false') {
    return trimmed === 'true';
  }
  if (trimmed !== '' && /^-?\d+(\.\d+)?$/.test(trimmed)) {
    return Number(trimmed);
  }
  return text;
}

/** Joins what the dialog edited and the rest back into a task query. */
export function toQuery(editable: EditableQuery): TaskQuery {
  const query: TaskQuery = { ...editable.rest };
  for (const row of editable.criteria) {
    const definition = BY_KEY.get(row.key);
    const value = row.value.trim();
    if (!definition || (definition.kind !== 'flag' && value === '')) {
      continue;
    }
    if (definition.expression && isExpression(value)) {
      query[`${row.key}Expression`] = value;
      continue;
    }
    switch (definition.kind) {
      case 'flag':
        query[row.key] = true;
        break;
      case 'list':
        query[row.key] = value
          .split(',')
          .map((part) => part.trim())
          .filter(Boolean);
        break;
      case 'number':
        query[row.key] = Number(value);
        break;
      default:
        query[row.key] = value;
    }
  }
  for (const row of editable.variables) {
    if (!row.name.trim()) {
      continue;
    }
    const list = (query[row.scope] as unknown[] | undefined) ?? [];
    // "001" stays text and null stays null unless the value was edited
    const unchanged = 'stored' in row && asText(row.stored) === row.value;
    list.push({ name: row.name.trim(), operator: row.operator, value: unchanged ? row.stored : variableValue(row.value) });
    query[row.scope] = list;
  }
  return query;
}

/** Why the value of a criterion cannot be saved, if it cannot. */
export function criterionError(row: CriterionRow): MessageKey | null {
  const definition = BY_KEY.get(row.key);
  const value = row.value.trim();
  if (!definition || definition.kind === 'flag') {
    return null;
  }
  if (value === '') {
    return 'validation.required';
  }
  if (isExpression(value)) {
    return definition.expression ? null : 'filterEdit.noExpression';
  }
  if (definition.kind === 'number' && !/^-?\d+$/.test(value)) {
    return 'filterEdit.number';
  }
  return null;
}
