/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { MessageKey } from '../i18n';

export type Section = { to: string; label: MessageKey; end?: boolean };

/** The monitoring pages, grouped by what a user wants to do: watch, look back, or build and maintain. */
export const COCKPIT_GROUPS: { label: MessageKey; sections: Section[] }[] = [
  {
    label: 'cockpit.nav.group.monitor',
    sections: [
      { to: '/cockpit', label: 'cockpit.nav.dashboard', end: true },
      { to: '/cockpit/processes', label: 'cockpit.nav.processes' },
      { to: '/cockpit/decisions', label: 'cockpit.nav.decisions' },
      { to: '/cockpit/incidents', label: 'cockpit.nav.incidents' },
      { to: '/cockpit/jobs', label: 'cockpit.nav.jobs' },
      { to: '/cockpit/external-tasks', label: 'ops.nav.externalTasks' },
      { to: '/cockpit/workload', label: 'workload.nav' },
    ],
  },
  {
    label: 'cockpit.nav.group.history',
    sections: [
      { to: '/cockpit/history', label: 'ops.nav.history' },
      { to: '/cockpit/decision-history', label: 'ops.nav.decisionHistory' },
      { to: '/cockpit/audit', label: 'ops.nav.audit' },
    ],
  },
  {
    label: 'cockpit.nav.group.build',
    sections: [
      { to: '/cockpit/modeler', label: 'modeler.nav' },
      { to: '/cockpit/deployments', label: 'cockpit.nav.deployments' },
      { to: '/cockpit/batches', label: 'cockpit.nav.batches' },
      { to: '/cockpit/cleanup', label: 'cleanup.nav' },
    ],
  },
];
