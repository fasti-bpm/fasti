/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { fromDateTimeInput, toEngineDate } from '../lib/dates';
import { parseConditionValue } from './model';

/** How the value of a condition is compared; `null` and `notNull` take no value. */
export type FilterOperator = 'eq' | 'neq' | 'gt' | 'gteq' | 'lt' | 'lteq' | 'like' | 'null' | 'notNull';

/** The type the value is sent as; `auto` reads `12` as a number and `true` as a boolean. */
export type FilterValueType = 'auto' | 'string' | 'number' | 'boolean' | 'date';

export interface FilterCondition {
  name: string;
  operator: FilterOperator;
  type: FilterValueType;
  /** as typed; a date as the value of a datetime-local input */
  value: string;
}

/** Conditions on variables, all of which (`and`) or any of which (`or`) must hold. */
export interface VariableFilter {
  mode: 'and' | 'or';
  conditions: FilterCondition[];
}

export const FILTER_OPERATORS: { operator: FilterOperator; symbol: string }[] = [
  { operator: 'eq', symbol: '=' },
  { operator: 'neq', symbol: '≠' },
  { operator: 'gt', symbol: '>' },
  { operator: 'gteq', symbol: '≥' },
  { operator: 'lt', symbol: '<' },
  { operator: 'lteq', symbol: '≤' },
  { operator: 'like', symbol: '≈' },
  { operator: 'null', symbol: '∅' },
  { operator: 'notNull', symbol: '≠ ∅' },
];

export const FILTER_TYPES: FilterValueType[] = ['auto', 'string', 'number', 'boolean', 'date'];

/** Operators that make sense for a type: no order on booleans, `like` only on text. */
export function operatorsFor(type: FilterValueType): FilterOperator[] {
  const all = FILTER_OPERATORS.map((entry) => entry.operator);
  switch (type) {
    case 'boolean':
      return ['eq', 'neq', 'null', 'notNull'];
    case 'number':
    case 'date':
      return all.filter((operator) => operator !== 'like');
    default:
      return all;
  }
}

export const takesValue = (operator: FilterOperator): boolean => operator !== 'null' && operator !== 'notNull';

export const emptyFilter = (): VariableFilter => ({ mode: 'and', conditions: [] });

export const emptyCondition = (): FilterCondition => ({ name: '', operator: 'eq', type: 'auto', value: '' });

/** A condition is complete with a name and, where the operator needs one, a valid value of its type. */
export function isComplete(condition: FilterCondition): boolean {
  if (!condition.name.trim()) return false;
  if (!takesValue(condition.operator)) return true;
  const value = condition.value.trim();
  switch (condition.type) {
    case 'number':
      return value !== '' && Number.isFinite(Number(value));
    case 'boolean':
      return value === 'true' || value === 'false';
    case 'date':
      return fromDateTimeInput(value) !== null;
    case 'string':
      return true;
    default:
      return value !== '';
  }
}

const contains = (text: string): string => (text.includes('%') ? text : `%${text}%`);

/** The value as the engine compares it. */
export function engineValue(condition: FilterCondition): string | number | boolean | null {
  if (!takesValue(condition.operator)) return null;
  const value = condition.value;
  switch (condition.type) {
    case 'number':
      return Number(value.trim());
    case 'boolean':
      return value.trim() === 'true';
    case 'date': {
      // the REST API turns a text in its date format into a date before comparing
      const date = fromDateTimeInput(value.trim());
      return date ? toEngineDate(date) : null;
    }
    case 'string':
      return condition.operator === 'like' ? contains(value) : value;
    default: {
      const parsed = parseConditionValue(value);
      return condition.operator === 'like' && typeof parsed === 'string' ? contains(parsed) : parsed;
    }
  }
}

export interface EngineVariableCondition {
  name: string;
  operator: 'eq' | 'neq' | 'gt' | 'gteq' | 'lt' | 'lteq' | 'like';
  value: string | number | boolean | null;
}

/** One condition in the shape of the `variables` of a REST query. */
export function toEngineCondition(condition: FilterCondition): EngineVariableCondition {
  const operator = condition.operator === 'null' ? 'eq' : condition.operator === 'notNull' ? 'neq' : condition.operator;
  return { name: condition.name.trim(), operator, value: engineValue(condition) };
}

/**
 * The part of a REST query for the filter: `variables` when all must hold;
 * one nested "or" query when any may, as the engine joins the criteria of a
 * nested query with OR. Incomplete conditions are left out.
 */
export function toEngineQuery(filter: VariableFilter): {
  variables?: EngineVariableCondition[];
  orQueries?: { variables: EngineVariableCondition[] }[];
} {
  const conditions = filter.conditions.filter(isComplete).map(toEngineCondition);
  if (conditions.length === 0) return {};
  if (filter.mode === 'or' && conditions.length > 1) return { orQueries: [{ variables: conditions }] };
  return { variables: conditions };
}

/** Whether the filter needs a query of the engine that joins conditions with OR. */
export const needsOr = (filter: VariableFilter): boolean =>
  filter.mode === 'or' && filter.conditions.filter(isComplete).length > 1;

const isOperator = (value: unknown): value is FilterOperator => FILTER_OPERATORS.some((entry) => entry.operator === value);
const isType = (value: unknown): value is FilterValueType => FILTER_TYPES.includes(value as FilterValueType);

/** Short form for the address and saved views: `{"m":"or","c":[[name,op,type,value]]}`. */
export function encodeFilter(filter: VariableFilter): string | null {
  const conditions = filter.conditions.filter(isComplete);
  if (conditions.length === 0) return null;
  return JSON.stringify({
    m: filter.mode,
    c: conditions.map((condition) => [condition.name.trim(), condition.operator, condition.type, condition.value]),
  });
}

/** Reads the short form; a link cut off or edited by hand yields what is still valid. */
export function decodeFilter(raw: string | null | undefined): VariableFilter {
  if (!raw) return emptyFilter();
  try {
    const parsed = JSON.parse(raw) as { m?: unknown; c?: unknown };
    const rows: unknown[] = Array.isArray(parsed.c) ? parsed.c : [];
    const conditions: FilterCondition[] = [];
    for (const row of rows) {
      if (!Array.isArray(row)) continue;
      const [name, operator, type, value] = row as unknown[];
      if (typeof name !== 'string' || !isOperator(operator)) continue;
      conditions.push({ name, operator, type: isType(type) ? type : 'auto', value: typeof value === 'string' ? value : '' });
    }
    return { mode: parsed.m === 'or' ? 'or' : 'and', conditions };
  } catch {
    return emptyFilter();
  }
}

/** A condition as text for a chip, e.g. `amount > 100`. */
export function conditionLabel(condition: FilterCondition): string {
  const symbol = FILTER_OPERATORS.find((entry) => entry.operator === condition.operator)?.symbol ?? '=';
  if (!takesValue(condition.operator)) return `${condition.name} ${symbol}`;
  const text =
    condition.type === 'string' || (condition.type === 'auto' && typeof parseConditionValue(condition.value) === 'string')
      ? `"${condition.value.replace(/^"(.*)"$/, '$1')}"`
      : condition.type === 'date'
        ? condition.value.replace('T', ' ')
        : condition.value;
  return `${condition.name} ${symbol} ${text}`;
}
