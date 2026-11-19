/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Translate } from '../i18n';

/** What an instance is doing, as far as the page knows it. */
export interface InstanceState {
  suspended: boolean;
  /** names of the activities that have incidents */
  failing: string[];
  /** names of the activities that hold a token */
  waiting: string[];
}

export type SummaryTone = 'danger' | 'warning' | 'info';

/** Quotes names and joins them as the language does: “A”, “B” and “C”. */
export function nameList(names: string[], locale: string, t: Translate): string {
  const unique = [...new Set(names)];
  const quoted = unique.slice(0, 3).map((name) => t('cockpit.summary.quoted', { name }));
  if (unique.length > 3) quoted.push(t('cockpit.summary.more', { count: unique.length - 3 }));
  return new Intl.ListFormat(locale, { type: 'conjunction' }).format(quoted);
}

/**
 * One sentence on where an instance stands, for people who do not read
 * activity trees: an incident comes first, then a suspension, then what the
 * instance waits for.
 */
export function describeInstance(state: InstanceState, locale: string, t: Translate): { tone: SummaryTone; text: string } {
  if (state.failing.length > 0) {
    return { tone: 'danger', text: t('cockpit.summary.failing', { activities: nameList(state.failing, locale, t) }) };
  }
  if (state.suspended) {
    return { tone: 'warning', text: t('cockpit.summary.suspended') };
  }
  if (state.waiting.length > 0) {
    return { tone: 'info', text: t('cockpit.summary.waiting', { activities: nameList(state.waiting, locale, t) }) };
  }
  return { tone: 'info', text: t('cockpit.summary.moving') };
}
