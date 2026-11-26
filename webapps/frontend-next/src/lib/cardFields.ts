/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useState } from 'react';
import { onPreferencesReplaced, readPreference, writePreference } from './storage';

/** A process variable shown on the task cards of one process, under a name of the business. */
export interface CardField {
  name: string;
  /** what the business calls it; the variable name if empty */
  label: string;
}

/** The fields per process, by the key of the process definition. */
export type ProcessCardFields = Record<string, CardField[]>;

/** outside `tasklist.`, where the entries of other users of the browser lie */
const KEY = 'tasks.processFields';
export const MAX_FIELDS = 6;

function valid(value: unknown): value is CardField {
  return (
    !!value &&
    typeof value === 'object' &&
    typeof (value as CardField).name === 'string' &&
    !!(value as CardField).name &&
    typeof (value as CardField).label === 'string'
  );
}

export function processCardFields(): ProcessCardFields {
  const value = readPreference<unknown>(KEY, {});
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const result: ProcessCardFields = {};
  for (const [key, fields] of Object.entries(value)) {
    if (Array.isArray(fields)) {
      const kept = fields.filter(valid).slice(0, MAX_FIELDS);
      if (kept.length) result[key] = kept;
    }
  }
  return result;
}

export function setProcessCardFields(fields: ProcessCardFields): void {
  writePreference(
    KEY,
    Object.fromEntries(Object.entries(fields).filter(([, list]) => list.length > 0)),
  );
}

/** The fields per process and a setter; follows the copy of the server when it arrives late. */
export function useProcessCardFields(): [ProcessCardFields, (fields: ProcessCardFields) => void] {
  const [fields, setFields] = useState<ProcessCardFields>(processCardFields);
  useEffect(() => onPreferencesReplaced(() => setFields(processCardFields())), []);
  const save = (next: ProcessCardFields) => {
    setProcessCardFields(next);
    setFields(processCardFields());
  };
  return [fields, save];
}

/**
 * The key of the process of a task: as the engine names it, or else from the id
 * of the definition (`key:version:id`; ids of long keys are generated and hold no key).
 */
export function processKeyOf(task: { processDefinitionKey?: string; processDefinitionId: string | null }): string | null {
  if (task.processDefinitionKey) return task.processDefinitionKey;
  const parts = task.processDefinitionId?.split(':') ?? [];
  return parts.length === 3 ? (parts[0] ?? null) : null;
}

/** The labels of the fields of a process, by variable name. */
export function fieldLabels(fields: ProcessCardFields, key: string | null): Record<string, string> {
  return Object.fromEntries((key ? (fields[key] ?? []) : []).map((field) => [field.name, field.label.trim() || field.name]));
}

/** Every variable name the fields of the given processes need. */
export function fieldNames(fields: ProcessCardFields, processKeys: (string | null)[]): string[] {
  const keys = new Set(processKeys);
  return [...new Set(Object.entries(fields).flatMap(([key, list]) => (keys.has(key) ? list.map((field) => field.name) : [])))];
}
