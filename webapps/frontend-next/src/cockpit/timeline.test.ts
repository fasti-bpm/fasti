/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import type { HistoricActivity, HistoricIncident } from '../api/operations';
import { layoutTimeline } from './Timeline';

const activity = (id: string, type: string, start: string, end: string | null, extra: Partial<HistoricActivity> = {}): HistoricActivity => ({
  id,
  activityId: id,
  activityName: null,
  activityType: type,
  startTime: `2026-03-01T${start}:00.000+0000`,
  endTime: end && `2026-03-01T${end}:00.000+0000`,
  durationInMillis: null,
  assignee: null,
  canceled: false,
  completeScope: false,
  calledProcessInstanceId: null,
  ...extra,
});

describe('the timeline of an instance', () => {
  it('places activities on one axis, leaves out gateways and marks waiting, running and failed ones', () => {
    const now = Date.parse('2026-03-01T12:00:00Z');
    const incident: HistoricIncident = {
      id: 'i1', incidentType: 'failedJob', incidentMessage: 'boom', activityId: 'charge',
      createTime: '2026-03-01T11:00:00.000+0000', endTime: null, open: true, deleted: false, resolved: false,
    };
    const layout = layoutTimeline(
      [
        activity('start', 'startEvent', '08:00', '08:00'),
        activity('split', 'exclusiveGateway', '08:00', '08:00'),
        activity('approve', 'userTask', '08:00', '10:00', { assignee: 'demo' }),
        activity('charge', 'serviceTask', '10:00', null),
      ],
      [incident],
      new Map([['approve', 'Approve invoice']]),
      now,
    );
    expect(layout?.rows.map((row) => row.id)).toEqual(['start', 'approve', 'charge']);
    const [, approve, charge] = layout?.rows ?? [];
    expect(approve).toMatchObject({ name: 'Approve invoice', offset: 0, width: 50, waiting: true, state: 'done', assignee: 'demo' });
    expect(charge).toMatchObject({ offset: 50, width: 50, state: 'running', incidents: 1 });
    expect(layoutTimeline([], [], new Map())).toBeNull();
  });
});
