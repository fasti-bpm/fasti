/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { MessageKey } from '../i18n';

export type ShortcutGroup = 'general' | 'tasklist' | 'navigation';

export interface Shortcut {
  label: MessageKey;
  keys: string[];
  group: ShortcutGroup;
  /** the application the user must be allowed to use, if any */
  app?: string;
}

export const SHORTCUT_GROUPS: { group: ShortcutGroup; label: MessageKey }[] = [
  { group: 'general', label: 'shortcuts.group.general' },
  { group: 'tasklist', label: 'shortcuts.group.tasklist' },
  { group: 'navigation', label: 'shortcuts.group.navigation' },
];

export const SHORTCUTS: Shortcut[] = [
  { label: 'shortcuts.palette', keys: ['Ctrl K'], group: 'general' },
  { label: 'shortcuts.help', keys: ['?'], group: 'general' },
  { label: 'shortcuts.move', keys: ['J', 'K', '↓', '↑'], group: 'tasklist' },
  { label: 'shortcuts.open', keys: ['Enter'], group: 'tasklist' },
  { label: 'shortcuts.check', keys: ['Space'], group: 'tasklist' },
  { label: 'shortcuts.search', keys: ['/'], group: 'tasklist' },
  { label: 'shortcuts.claim', keys: ['C'], group: 'tasklist' },
  { label: 'shortcuts.getNext', keys: ['N'], group: 'tasklist' },
  { label: 'shortcuts.complete', keys: ['Ctrl Enter'], group: 'tasklist' },
  { label: 'shortcuts.refresh', keys: ['R'], group: 'tasklist' },
  { label: 'shortcuts.back', keys: ['Esc'], group: 'tasklist' },
  { label: 'shortcuts.goHome', keys: ['G H'], group: 'navigation' },
  { label: 'shortcuts.goTasks', keys: ['G T'], group: 'navigation', app: 'tasklist' },
  { label: 'shortcuts.goDashboard', keys: ['G D'], group: 'navigation', app: 'cockpit' },
  { label: 'shortcuts.goProcesses', keys: ['G P'], group: 'navigation', app: 'cockpit' },
  { label: 'shortcuts.goIncidents', keys: ['G I'], group: 'navigation', app: 'cockpit' },
  { label: 'shortcuts.goJobs', keys: ['G J'], group: 'navigation', app: 'cockpit' },
  { label: 'shortcuts.goDeployments', keys: ['G M'], group: 'navigation', app: 'cockpit' },
  { label: 'shortcuts.goBatches', keys: ['G B'], group: 'navigation', app: 'cockpit' },
];

/** Where `G` followed by a key leads, and the application it belongs to. */
export const GO_TO: Record<string, { path: string; app?: string }> = {
  h: { path: '/' },
  t: { path: '/tasklist', app: 'tasklist' },
  d: { path: '/cockpit', app: 'cockpit' },
  p: { path: '/cockpit/processes', app: 'cockpit' },
  i: { path: '/cockpit/incidents', app: 'cockpit' },
  j: { path: '/cockpit/jobs', app: 'cockpit' },
  m: { path: '/cockpit/deployments', app: 'cockpit' },
  b: { path: '/cockpit/batches', app: 'cockpit' },
};

/** How long the second key of a `G …` sequence is awaited. */
export const SEQUENCE_TIMEOUT = 1200;

/** True if the event comes from a place where the user types text. */
export function isTyping(event: KeyboardEvent): boolean {
  const target = event.target;
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return (
    target.isContentEditable ||
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT'
  );
}
