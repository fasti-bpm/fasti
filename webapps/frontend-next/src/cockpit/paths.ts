/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { InstanceQuery } from '../api/cockpit';

/** The history of one process instance, running or finished. */
export function historyPath(id: string): string {
  return `/cockpit/history/instance/${encodeURIComponent(id)}`;
}

/**
 * The migration page for picked instances, for every instance a filter of the
 * list finds (`query`), or for every running instance of the version.
 */
export function migrationPath(sourceDefinitionId: string, instanceIds?: string[], query?: InstanceQuery): string {
  const params = new URLSearchParams({ source: sourceDefinitionId });
  if (instanceIds?.length) {
    params.set('ids', instanceIds.join(','));
  } else if (query) {
    const filter: InstanceQuery = { ...query };
    delete filter.processDefinitionId;
    if (Object.values(filter).some((value) => (Array.isArray(value) ? value.length > 0 : !!value))) {
      params.set('q', JSON.stringify(filter));
    }
  }
  return `/cockpit/migration?${params.toString()}`;
}

export function decisionInstancePath(id: string): string {
  return `/cockpit/decision-history/${encodeURIComponent(id)}`;
}

// --- cases (CMMN) -------------------------------------------------------------

export const CASES_PATH = '/cockpit/cases';
export const CASE_HISTORY_PATH = '/cockpit/case-history';

export function caseDefinitionPath(id: string): string {
  return `${CASES_PATH}/definition/${encodeURIComponent(id)}`;
}

/** A case instance, open or closed: the page reads the history for a closed one. */
export function caseInstancePath(id: string, tab?: string): string {
  return `${CASES_PATH}/instance/${encodeURIComponent(id)}${tab ? `?tab=${tab}` : ''}`;
}
