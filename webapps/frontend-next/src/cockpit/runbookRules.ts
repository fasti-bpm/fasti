/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Guidance for an incident: what its type usually means and what to check for
 * the errors that come up again and again. Pure functions, so the rules can be
 * tested without a page; the texts live in the message catalogues.
 */

/** Incident types with their own guidance; any other type gets the general one. */
export const RUNBOOK_TYPES = ['failedJob', 'failedExternalTask', 'other'] as const;
export type RunbookType = (typeof RUNBOOK_TYPES)[number];

/**
 * Error patterns, in the order they are shown. The first that match are the
 * most specific: a timeout is also a connection problem, so it comes first.
 */
export const RUNBOOK_PATTERNS = [
  'optimisticLock',
  'timeout',
  'connection',
  'unauthorized',
  'serverError',
  'expression',
  'classNotFound',
  'nullPointer',
  'database',
  'bpmnError',
] as const;
export type RunbookPattern = (typeof RUNBOOK_PATTERNS)[number];

const RULES: Record<RunbookPattern, RegExp> = {
  optimisticLock: /OptimisticLocking|optimistic lock/i,
  timeout: /time[ds]?[ -]?out|SocketTimeout|deadline exceeded/i,
  connection:
    /connection refused|ConnectException|ECONNREFUSED|ECONNRESET|UnknownHost|No route to host|connection reset|Name or service not known/i,
  unauthorized: /\b(401|403)\b|Unauthori[sz]ed|Forbidden|Access denied|AuthorizationException/i,
  serverError: /\b50[0234]\b|Internal Server Error|Bad Gateway|Service Unavailable|Gateway Time/i,
  expression: /Unknown property used in expression|Cannot resolve identifier|PropertyNotFound|ELException|ScriptEvaluation/i,
  classNotFound: /ClassNotFound|NoClassDefFound|Cannot instantiate|ClassCastException/i,
  nullPointer: /NullPointerException|\bis null\b|Cannot invoke .* because/i,
  database: /SQLException|deadlock|constraint violation|could not execute|Lock wait timeout|DataIntegrity/i,
  bpmnError: /BpmnError|no catching boundary event|error event definition/i,
};

export function runbookType(incidentType: string): RunbookType {
  return incidentType === 'failedJob' || incidentType === 'failedExternalTask' ? incidentType : 'other';
}

/** The patterns an error message and, once read, its stack trace show; at most `limit`. */
export function runbookPatterns(...texts: (string | null | undefined)[]): RunbookPattern[] {
  // the head of a stack trace says what went wrong; the frames below add noise
  const text = texts
    .filter((part): part is string => !!part)
    .map((part) => part.slice(0, 4000))
    .join('\n');
  if (!text) return [];
  const found = RUNBOOK_PATTERNS.filter((pattern) => RULES[pattern].test(text));
  // a lock timeout of the database is a database problem, not a slow service
  return found.includes('database') && /Lock wait timeout/i.test(text)
    ? found.filter((pattern) => pattern !== 'timeout')
    : found;
}

/** The name of the extension property that points to the runbook of an activity. */
export const RUNBOOK_PROPERTY = 'fasti.runbook';

const LINK = /^(https?:\/\/|mailto:)\S+$/i;

/** What the model says: a link to open, or a short text, shown as plain text with safe marks. */
export type ModelRunbook = { kind: 'link'; href: string } | { kind: 'text'; text: string };

export function modelRunbook(value: string | null | undefined): ModelRunbook | null {
  const text = value?.trim();
  if (!text) return null;
  if (LINK.test(text)) return { kind: 'link', href: text };
  // anything that looks like a link of another scheme stays text and is never a link
  return { kind: 'text', text: text.slice(0, 2000) };
}
