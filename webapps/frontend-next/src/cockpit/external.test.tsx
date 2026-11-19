/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { externalTaskQuery, likePattern } from '../api/cockpit';
import { csvField, toCsv } from '../lib/csv';

describe('external task list', () => {
  it('builds the engine query from the filter', () => {
    expect(externalTaskQuery({ state: 'all' })).toEqual({});
    expect(externalTaskQuery({ state: 'locked', topicName: ' mail ', workerId: '' })).toEqual({
      locked: true,
      topicName: 'mail',
    });
    expect(externalTaskQuery({ state: 'noRetriesLeft', workerId: 'w1' })).toEqual({
      noRetriesLeft: true,
      workerId: 'w1',
    });
  });
});

describe('CSV export', () => {
  it('quotes separators and neutralises formulas', () => {
    expect(csvField(null)).toBe('');
    expect(csvField(3)).toBe('3');
    expect(csvField('a,b')).toBe('"a,b"');
    expect(csvField('say "hi"')).toBe('"say ""hi"""');
    expect(csvField('=SUM(A1)')).toBe("'=SUM(A1)");
    expect(csvField('@cmd')).toBe("'@cmd");
    expect(csvField('  =1+1')).toBe("'  =1+1");
    expect(csvField('\uFF1DSUM(A1)')).toBe("'\uFF1DSUM(A1)");
    expect(csvField('\uFF0B1')).toBe("'\uFF0B1");
    expect(csvField('\uFF0D1')).toBe("'\uFF0D1");
    expect(csvField('\uFF20x')).toBe("'\uFF20x");
    expect(csvField(' ;cmd|calc')).toBe("' ;cmd|calc");
    expect(csvField('plain; text')).toBe('plain; text');
  });

  it('joins rows with CRLF', () => {
    expect(toCsv(['id', 'name'], [['1', 'x'], ['2', null]])).toBe('id,name\r\n1,x\r\n2,');
  });
});

describe('palette search', () => {
  it('escapes the wildcards of the engine', () => {
    expect(likePattern('A-1')).toBe('%A-1%');
    expect(likePattern('50%_off')).toBe('%50\\%\\_off%');
  });
});
