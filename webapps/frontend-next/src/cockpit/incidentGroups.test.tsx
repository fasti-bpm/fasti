/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import type { Incident } from '../api/cockpit';
import { groupIncidents, messagePattern } from './IncidentGroups';

const incident = (id: string, extra: Partial<Incident>): Incident => ({
  id,
  incidentTimestamp: '2026-03-01T09:00:00.000+0000',
  incidentType: 'failedJob',
  incidentMessage: null,
  activityId: 'charge',
  failedActivityId: 'charge',
  processInstanceId: `p-${id}`,
  processDefinitionId: 'order:3:abc',
  causeIncidentId: null,
  rootCauseIncidentId: null,
  configuration: null,
  annotation: null,
  ...extra,
});

describe('incident groups', () => {
  it('ignore what differs between occurrences of one failure', () => {
    expect(messagePattern("Order 4711 failed: card '1234' declined")).toBe('Order # failed: card … declined');
    expect(messagePattern('id 0b9c1e4a-1111-4a2b-9c3d-123456789abc gone')).toBe('id … gone');
  });

  it('group by process, activity and failure, largest first, with the oldest occurrence', () => {
    const groups = groupIncidents([
      incident('1', { incidentMessage: 'Timeout after 30 s', incidentTimestamp: '2026-03-01T10:00:00.000+0000' }),
      incident('2', { incidentMessage: 'Timeout after 31 s', incidentTimestamp: '2026-03-01T08:00:00.000+0000' }),
      incident('3', { incidentMessage: 'Timeout after 30 s', processInstanceId: 'p-1' }),
      incident('4', { incidentMessage: 'Card declined' }),
      incident('5', { incidentMessage: 'Timeout after 30 s', processDefinitionId: 'invoice:1:x' }),
    ]);
    expect(groups.map((group) => [group.processKey, group.incidents.length, group.instances])).toEqual([
      ['order', 3, 2],
      ['order', 1, 1],
      ['invoice', 1, 1],
    ]);
    expect(groups[0]).toMatchObject({
      message: 'Timeout after 31 s',
      oldest: '2026-03-01T08:00:00.000+0000',
      newest: '2026-03-01T10:00:00.000+0000',
    });
  });
});
