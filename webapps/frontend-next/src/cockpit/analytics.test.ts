/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { periodBoundaries, type ActivityAnalytics, type DurationStatistics } from '../api/analytics';
import { diagramActivities, measureValue, parseTarget } from './AnalyticsTab';

const durations = (values: Partial<DurationStatistics>): DurationStatistics => ({
  count: 0,
  total: 0,
  minimum: null,
  maximum: null,
  average: null,
  p50: null,
  p75: null,
  p90: null,
  p95: null,
  p99: null,
  approximate: true,
  withinTarget: null,
  buckets: [],
  ...values,
});

const activity = (values: Partial<ActivityAnalytics>): ActivityAnalytics => ({
  activityId: 'review',
  activityType: 'userTask',
  activityName: 'Review',
  finished: 0,
  canceled: 0,
  running: 0,
  runningOverP90: 0,
  oldestRunningStart: null,
  repeatedInstances: 0,
  repeatedExecutions: 0,
  openIncidents: 0,
  incidents: 0,
  timeAboveMedian: null,
  durations: durations({}),
  ...values,
});

describe('process analytics helpers', () => {
  it('reads targets in minutes, hours and days', () => {
    expect(parseTarget('90m')).toBe(90 * 60_000);
    expect(parseTarget('4h')).toBe(4 * 3_600_000);
    expect(parseTarget('1,5 h')).toBe(1.5 * 3_600_000);
    expect(parseTarget('2d')).toBe(2 * 86_400_000);
    expect(parseTarget('3')).toBe(3 * 3_600_000);
    expect(parseTarget('soon')).toBeNull();
    expect(parseTarget('0h')).toBeNull();
  });

  it('gives each heat measure its value', () => {
    const now = Date.parse('2026-11-24T12:00:00Z');
    const entry = activity({
      finished: 7,
      canceled: 1,
      running: 2,
      repeatedExecutions: 3,
      openIncidents: 1,
      incidents: 4,
      oldestRunningStart: '2026-11-24T10:00:00Z',
      durations: durations({ total: 1000, p50: 10, p90: 90, p99: 99 }),
    });
    expect(measureValue(entry, 'reached', now)).toBe(10);
    expect(measureValue(entry, 'p50', now)).toBe(10);
    expect(measureValue(entry, 'p90', now)).toBe(90);
    expect(measureValue(entry, 'p99', now)).toBe(99);
    expect(measureValue(entry, 'total', now)).toBe(1000);
    expect(measureValue(entry, 'waiting', now)).toBe(2 * 3_600_000);
    expect(measureValue(entry, 'rework', now)).toBe(3);
    expect(measureValue(entry, 'incidents', now)).toBe(5);
  });

  it('leaves the multi-instance bodies out of the diagram', () => {
    const shown = diagramActivities([activity({ activityId: 'approve' }), activity({ activityId: 'approve#multiInstanceBody' })]);
    expect(shown.map((entry) => entry.activityId)).toEqual(['approve']);
  });

  it('splits a range into days, weeks or months ending after now', () => {
    const now = new Date(2026, 10, 24, 15, 30);
    const days = periodBoundaries(7, now);
    expect(days.unit).toBe('day');
    expect(days.bounds).toHaveLength(8);
    expect(days.bounds[7]).toEqual(new Date(2026, 10, 25));
    expect(days.bounds[0]).toEqual(new Date(2026, 10, 18));

    const weeks = periodBoundaries(90, now);
    expect(weeks.unit).toBe('week');
    // the Monday after now
    expect(weeks.bounds[weeks.bounds.length - 1]).toEqual(new Date(2026, 10, 30));
    expect(weeks.bounds).toHaveLength(14);

    const months = periodBoundaries(365, now);
    expect(months.unit).toBe('month');
    expect(months.bounds[months.bounds.length - 1]).toEqual(new Date(2026, 11, 1));
    expect(months.bounds).toHaveLength(14);
  });
});
