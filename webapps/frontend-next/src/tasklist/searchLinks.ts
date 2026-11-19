/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';
import { request, seg } from '../api/http';
import type { TaskListItem, TaskVariable } from '../api/types';
import type { Criterion, CriterionField, VariableOperator } from './search';
import { isQueueView, type QueueView } from './queue';

const engineApi = (): string => `/engine/engine/${seg(env.engine)}`;

/** A search as it is shared in a link or saved under a name. */
export interface SavedSearch {
  name: string;
  criteria: Criterion[];
  onlyMine: boolean;
}

const FIELDS: readonly CriterionField[] = [
  'name',
  'assignee',
  'description',
  'businessKey',
  'processName',
  'processVariable',
  'taskVariable',
  'candidateGroup',
  'assigneeId',
];
const OPERATORS: readonly VariableOperator[] = ['eq', 'neq', 'gt', 'gteq', 'lt', 'lteq', 'like'];

/** The compact form of criteria in a link: field, value, variable and operator; and the urgency view, if any. */
export function encodeSearch(criteria: Criterion[], onlyMine: boolean, view?: QueueView): string {
  const rows = criteria
    .filter((criterion) => criterion.value.trim() !== '')
    .map((criterion) =>
      criterion.variable ? [criterion.field, criterion.value, criterion.variable, criterion.operator ?? 'eq'] : [criterion.field, criterion.value],
    );
  return JSON.stringify({ c: rows, ...(onlyMine ? { m: 1 } : {}), ...(view ? { v: view } : {}) });
}

/** Criteria of a link; anything malformed is dropped, never trusted. */
export function decodeSearch(
  text: string | null,
): { criteria: Criterion[]; onlyMine: boolean; view?: QueueView } | null {
  if (!text) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object' || !Array.isArray((parsed as { c?: unknown }).c)) return null;
  const criteria: Criterion[] = [];
  for (const [index, row] of ((parsed as { c: unknown[] }).c).slice(0, 20).entries()) {
    if (!Array.isArray(row)) continue;
    const [field, value, variable, operator] = row as unknown[];
    if (!FIELDS.includes(field as CriterionField) || typeof value !== 'string' || value.length > 500) continue;
    const criterion: Criterion = { id: `link-${index}`, field: field as CriterionField, value };
    if (field === 'processVariable' || field === 'taskVariable') {
      if (typeof variable !== 'string' || !variable.trim()) continue;
      criterion.variable = variable;
      criterion.operator = OPERATORS.includes(operator as VariableOperator) ? (operator as VariableOperator) : 'eq';
    }
    criteria.push(criterion);
  }
  const view = (parsed as { v?: unknown }).v;
  return { criteria, onlyMine: (parsed as { m?: unknown }).m === 1, ...(isQueueView(view) ? { view } : {}) };
}

/** A link that opens the Tasklist with this filter and search. */
export function searchLink(base: string, filterId: string | null, criteria: Criterion[], onlyMine: boolean): string {
  return `${base}#${tasklistPath(filterId, criteria, onlyMine)}`;
}

/** The path of the Tasklist with a filter, a search and an urgency view, for links inside the application. */
export function tasklistPath(filterId: string | null, criteria: Criterion[], onlyMine = false, view?: QueueView): string {
  const params = new URLSearchParams();
  if (filterId) params.set('filter', filterId);
  params.set('q', encodeSearch(criteria, onlyMine, view));
  return `/tasklist?${params.toString()}`;
}

/** Variable names a user wants on the task cards: trimmed, unique, at most 8. */
export function normaliseCardVariables(names: string[]): string[] {
  return [...new Set(names.map((name) => name.trim()).filter(Boolean))].slice(0, 8);
}

interface VariableInstanceDto {
  name: string;
  type: string;
  value: unknown;
  processInstanceId: string;
}

const CARD_PAGE = 500;

/** Values of the chosen process variables of the instances of the listed tasks. */
export async function getCardVariables(
  tasks: TaskListItem[],
  names: string[],
  signal?: AbortSignal,
): Promise<Map<string, TaskVariable[]>> {
  const instances = [...new Set(tasks.map((task) => task.processInstanceId).filter((id): id is string => !!id))];
  const result = new Map<string, TaskVariable[]>();
  if (!instances.length || !names.length) return result;
  // the scope of the instance itself is its root execution, whose id is the id of the instance;
  // variables of sub scopes and of tasks are not read at all
  const readAll = async (variableName: string) => {
    const found: VariableInstanceDto[] = [];
    for (let firstResult = 0; ; firstResult += CARD_PAGE) {
      const page = await request<VariableInstanceDto[]>(`${engineApi()}/variable-instance`, {
        method: 'POST',
        query: { deserializeValues: false, firstResult, maxResults: CARD_PAGE },
        json: { variableScopeIdIn: instances, variableName },
        signal,
      });
      found.push(...page);
      if (page.length < CARD_PAGE) return found;
    }
  };
  const lists = await Promise.all(names.map(readAll));
  for (const variable of lists.flat()) {
    const known = result.get(variable.processInstanceId) ?? [];
    if (!known.some((candidate) => candidate.name === variable.name)) {
      known.push({ name: variable.name, type: variable.type, value: variable.value } as TaskVariable);
    }
    result.set(variable.processInstanceId, known);
  }
  return result;
}

/** The tasks with the chosen variables of their instances added. */
export function withCardVariables(tasks: TaskListItem[], byInstance: Map<string, TaskVariable[]>): TaskListItem[] {
  return tasks.map((task) => {
    const extra = (task.processInstanceId && byInstance.get(task.processInstanceId)) || [];
    const missing = extra.filter((variable) => !task.variables.some((own) => own.name === variable.name));
    return missing.length ? { ...task, variables: [...task.variables, ...missing] } : task;
  });
}
