/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { VariableMap, VariableValue } from '../api/types';
import type { MessageKey } from '../i18n';
import { fromDateTimeInput, toEngineDate } from '../lib/dates';

export const VARIABLE_TYPES = ['String', 'Boolean', 'Integer', 'Long', 'Double', 'Date'] as const;
export type VariableType = (typeof VARIABLE_TYPES)[number] | 'Short';

export type Conversion = { ok: true; value: unknown } | { ok: false; error: MessageKey };

const INTEGER = /^-?\d+$/;

/** The ranges of the integer types of the engine (Java short, int and long). */
const INTEGER_RANGES: Record<'Short' | 'Integer' | 'Long', [bigint, bigint]> = {
  Short: [-32768n, 32767n],
  Integer: [-2147483648n, 2147483647n],
  Long: [-9223372036854775808n, 9223372036854775807n],
};

/** Where JSON text is broken, as line and column counted from 1; `null` if it parses. */
export function jsonProblem(text: string): { line: number; column: number } | null {
  try {
    JSON.parse(text);
    return null;
  } catch (error) {
    const message = error instanceof Error ? error.message : '';
    const position = /position (\d+)/.exec(message)?.[1];
    const lineColumn = /line (\d+) column (\d+)/.exec(message);
    if (lineColumn) return { line: Number(lineColumn[1]), column: Number(lineColumn[2]) };
    const offset = position === undefined ? text.length : Number(position);
    const before = text.slice(0, offset).split('\n');
    return { line: before.length, column: (before[before.length - 1]?.length ?? 0) + 1 };
  }
}

/** Converts what the user typed to the value of a variable of the given type. */
export function convertInput(type: string, raw: string | boolean): Conversion {
  if (type === 'Boolean') {
    return { ok: true, value: raw === true || raw === 'true' };
  }
  const text = String(raw).trim();
  switch (type) {
    case 'Integer':
    case 'Short':
    case 'Long': {
      if (!INTEGER.test(text)) {
        return { ok: false, error: 'validation.integer' };
      }
      const [min, max] = INTEGER_RANGES[type];
      const big = BigInt(text);
      if (big < min || big > max) {
        return { ok: false, error: 'validation.range' };
      }
      // a Long beyond 2^53 would silently lose digits in the browser
      if (!Number.isSafeInteger(Number(text))) {
        return { ok: false, error: 'validation.unsafeLong' };
      }
      return { ok: true, value: Number(text) };
    }
    case 'Double': {
      const number = Number(text.replace(',', '.'));
      if (text === '' || !/^[-+]?(\d+[.,]?\d*|[.,]\d+)(e[-+]?\d+)?$/i.test(text) || !Number.isFinite(number)) {
        return { ok: false, error: 'validation.number' };
      }
      return { ok: true, value: number };
    }
    case 'Date': {
      const date = fromDateTimeInput(text);
      if (!date) {
        return { ok: false, error: 'validation.date' };
      }
      return { ok: true, value: toEngineDate(date) };
    }
    default:
      return { ok: true, value: String(raw) };
  }
}

function isJsonVariable(variable: VariableValue): boolean {
  return (
    variable.type === 'Json' || variable.valueInfo?.serializationDataFormat === 'application/json'
  );
}

/**
 * The data of a form schema from the variables of the engine. Variables are
 * requested in their serialized form, JSON values are parsed.
 */
export function variablesToFormData(variables: VariableMap): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  for (const [name, variable] of Object.entries(variables)) {
    let value = variable.value;
    if (isJsonVariable(variable) && typeof value === 'string') {
      try {
        value = JSON.parse(value);
      } catch {
        // kept as text
      }
    }
    data[name] = value;
  }
  return data;
}

/**
 * The variables to submit for the data of a form schema. Structured values
 * are stored as JSON, with the type of the variable they were read from if
 * there is one.
 */
export function formDataToVariables(
  data: Record<string, unknown>,
  existing: VariableMap,
): VariableMap {
  const variables: VariableMap = {};
  for (const [name, value] of Object.entries(data)) {
    if (value === undefined) {
      continue;
    }
    if (value !== null && typeof value === 'object') {
      const known = existing[name];
      variables[name] =
        known && isJsonVariable(known)
          ? { value: JSON.stringify(value), type: known.type, valueInfo: known.valueInfo }
          : { value: JSON.stringify(value), type: 'Json' };
    } else {
      variables[name] = { value };
    }
  }
  return variables;
}
