/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { TaskQuery } from '../api/engine';
import type { MessageKey } from '../i18n';

export type CriterionField =
  | 'name'
  | 'assignee'
  | 'description'
  | 'businessKey'
  | 'processName'
  | 'processVariable'
  | 'taskVariable'
  | 'candidateGroup'
  | 'assigneeId';

export type VariableOperator = 'eq' | 'neq' | 'gt' | 'gteq' | 'lt' | 'lteq' | 'like';

export interface Criterion {
  id: string;
  field: CriterionField;
  /** variable criteria only */
  variable?: string;
  operator?: VariableOperator;
  value: string;
}

/** The fields of a search; `hidden` ones come only from links, e.g. the team workload board. */
export const CRITERION_FIELDS: { field: CriterionField; label: MessageKey; hidden?: boolean }[] = [
  { field: 'name', label: 'search.field.name' },
  { field: 'assignee', label: 'search.field.assignee' },
  { field: 'processVariable', label: 'search.field.processVariable' },
  { field: 'taskVariable', label: 'search.field.taskVariable' },
  { field: 'businessKey', label: 'search.field.businessKey' },
  { field: 'processName', label: 'search.field.processName' },
  { field: 'description', label: 'search.field.description' },
  { field: 'candidateGroup', label: 'search.field.candidateGroup' },
  { field: 'assigneeId', label: 'search.field.assigneeId', hidden: true },
];

export const OPERATORS: { operator: VariableOperator; symbol: string }[] = [
  { operator: 'eq', symbol: '=' },
  { operator: 'neq', symbol: '≠' },
  { operator: 'gt', symbol: '>' },
  { operator: 'gteq', symbol: '≥' },
  { operator: 'lt', symbol: '<' },
  { operator: 'lteq', symbol: '≤' },
  { operator: 'like', symbol: '~' },
];

export function isVariableField(field: CriterionField): boolean {
  return field === 'processVariable' || field === 'taskVariable';
}

/** `true`, `false` and numbers are compared as such, anything else as text. */
function typedValue(text: string): string | number | boolean {
  const value = text.trim();
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value !== '' && /^-?\d+(\.\d+)?$/.test(value) && Number.isFinite(Number(value))) {
    return Number(value);
  }
  return value;
}

const contains = (text: string): string => `%${text.trim()}%`;

/** The additional query that is sent along with the query of the filter. */
export function toTaskQuery(criteria: Criterion[], onlyMine: string | null): TaskQuery {
  const query: TaskQuery = {};
  const processVariables: unknown[] = [];
  const taskVariables: unknown[] = [];
  for (const criterion of criteria) {
    if (!criterion.value.trim()) {
      continue;
    }
    switch (criterion.field) {
      case 'name':
        query.nameLike = contains(criterion.value);
        break;
      case 'assignee':
        query.assigneeLike = contains(criterion.value);
        break;
      case 'description':
        query.descriptionLike = contains(criterion.value);
        break;
      case 'businessKey':
        query.processInstanceBusinessKeyLike = contains(criterion.value);
        break;
      case 'processName':
        query.processDefinitionNameLike = contains(criterion.value);
        break;
      case 'candidateGroup':
        // every task the group may work on, also those already claimed by a member
        query.candidateGroup = criterion.value.trim();
        query.includeAssignedTasks = true;
        break;
      case 'assigneeId':
        query.assignee = criterion.value.trim();
        break;
      case 'processVariable':
      case 'taskVariable': {
        if (!criterion.variable?.trim()) {
          break;
        }
        const operator = criterion.operator ?? 'eq';
        (criterion.field === 'processVariable' ? processVariables : taskVariables).push({
          name: criterion.variable.trim(),
          operator,
          value: operator === 'like' ? contains(criterion.value) : typedValue(criterion.value),
        });
        break;
      }
    }
  }
  if (processVariables.length) query.processVariables = processVariables;
  if (taskVariables.length) query.taskVariables = taskVariables;
  if (onlyMine) query.assignee = onlyMine;
  return query;
}
