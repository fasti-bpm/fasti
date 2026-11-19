/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { readEnv } from '../env';
import { dueState, formatRelative, parseEngineDate, toEngineDate } from '../lib/dates';
import { readPreference, writePreference } from '../lib/storage';
import { toTaskQuery, type Criterion } from './search';

const criterion = (values: Omit<Criterion, 'id'>): Criterion => ({ id: 'x', ...values });

describe('toTaskQuery', () => {
  it('builds the additional query of a filter', () => {
    const query = toTaskQuery(
      [
        criterion({ field: 'name', value: ' invoice ' }),
        criterion({ field: 'assignee', value: 'ma' }),
        criterion({ field: 'businessKey', value: 'INV-1' }),
        criterion({ field: 'processName', value: 'Receipt' }),
        criterion({ field: 'description', value: 'urgent' }),
        criterion({ field: 'processVariable', variable: 'amount', operator: 'gteq', value: '300' }),
        criterion({ field: 'processVariable', variable: 'approved', value: 'true' }),
        criterion({ field: 'taskVariable', variable: 'note', operator: 'like', value: 'call' }),
        criterion({ field: 'processVariable', variable: ' ', value: 'ignored' }),
        criterion({ field: 'name', value: '   ' }),
      ],
      null,
    );

    expect(query).toEqual({
      nameLike: '%invoice%',
      assigneeLike: '%ma%',
      processInstanceBusinessKeyLike: '%INV-1%',
      processDefinitionNameLike: '%Receipt%',
      descriptionLike: '%urgent%',
      processVariables: [
        { name: 'amount', operator: 'gteq', value: 300 },
        { name: 'approved', operator: 'eq', value: true },
      ],
      taskVariables: [{ name: 'note', operator: 'like', value: '%call%' }],
    });
  });

  it('restricts the list to the tasks of the user', () => {
    expect(toTaskQuery([], 'demo')).toEqual({ assignee: 'demo' });
    expect(toTaskQuery([], null)).toEqual({});
  });
});

describe('dates', () => {
  it('parses and writes the date format of the REST API', () => {
    const date = parseEngineDate('2026-03-01T09:30:00.000+0700');
    expect(date?.toISOString()).toBe('2026-03-01T02:30:00.000Z');
    expect(parseEngineDate('2026-03-01T09:30:00.000Z')?.toISOString()).toBe(
      '2026-03-01T09:30:00.000Z',
    );
    expect(parseEngineDate(null)).toBeNull();
    expect(parseEngineDate('soon')).toBeNull();

    const written = toEngineDate(new Date(2026, 2, 1, 9, 30, 0));
    expect(written).toMatch(/^2026-03-01T09:30:00\.000[+-]\d{4}$/);
    expect(parseEngineDate(written)?.getTime()).toBe(new Date(2026, 2, 1, 9, 30, 0).getTime());
  });

  it('tells overdue and soon due tasks apart', () => {
    const now = new Date('2026-03-01T12:00:00Z');
    expect(dueState(new Date('2026-03-01T11:00:00Z'), now)).toBe('overdue');
    expect(dueState(new Date('2026-03-02T09:00:00Z'), now)).toBe('soon');
    expect(dueState(new Date('2026-03-05T09:00:00Z'), now)).toBe('later');
  });

  it('formats relative dates in both languages', () => {
    const now = new Date('2026-03-01T12:00:00Z');
    expect(formatRelative(new Date('2026-03-01T09:00:00Z'), 'en', now)).toBe('3 hours ago');
    expect(formatRelative(new Date('2026-03-03T12:00:00Z'), 'en', now)).toBe('in 2 days');
    expect(formatRelative(new Date('2026-03-01T09:00:00Z'), 'vi', now)).toBe('3 giờ trước');
    expect(formatRelative(new Date('2026-02-28T12:00:00Z'), 'vi', now)).toBe('Hôm qua');
  });
});

describe('environment', () => {
  const page = (root: string): Document => {
    const doc = document.implementation.createHTMLDocument();
    const meta = doc.createElement('meta');
    meta.name = 'fasti-app-root';
    meta.content = root;
    doc.head.append(meta);
    return doc;
  };

  it('reads the application root from the page and the engine from the path', () => {
    expect(readEnv(page('/fasti'), '/fasti/app/next/default/')).toEqual({
      appRoot: '/fasti',
      engine: 'default',
    });
    expect(readEnv(page('/ctx/custom/path'), '/ctx/custom/path/app/next/my%20engine/')).toEqual({
      appRoot: '/ctx/custom/path',
      engine: 'my engine',
    });
    expect(readEnv(page(''), '/app/next/default/')).toEqual({ appRoot: '', engine: 'default' });
  });

  it('falls back to the path if the placeholder was not replaced', () => {
    expect(readEnv(page('$APP_ROOT'), '/x/app/next/e1/')).toEqual({ appRoot: '/x', engine: 'e1' });
    expect(readEnv(page('$APP_ROOT'), '/')).toEqual({ appRoot: '/fasti', engine: 'default' });
  });
});

describe('preferences', () => {
  it('survives a storage that is not available', () => {
    writePreference('a', { b: 1 });
    expect(readPreference('a', null)).toEqual({ b: 1 });
    expect(readPreference('missing', 'fallback')).toBe('fallback');

    window.localStorage.setItem('fasti.next.broken', '{not json');
    expect(readPreference('broken', 'fallback')).toBe('fallback');
  });
});
