/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { prettyValue } from '../cockpit/Variables';
import { formatJson } from './json';

describe('JSON formatting', () => {
  it('keeps big integers and number spelling exactly', () => {
    expect(formatJson('{"id":9007199254740993,"x":1.10,"e":1E400}')).toBe(
      '{\n  "id": 9007199254740993,\n  "x": 1.10,\n  "e": 1E400\n}',
    );
    expect(prettyValue({ type: 'Json', value: '[9007199254740993]', valueInfo: {} })).toBe('[\n  9007199254740993\n]');
  });

  it('indents nesting, keeps strings and escapes, and empty containers short', () => {
    expect(formatJson(' { "a" : [ ], "b":{}, "s":"x, {y}: \\"z\\"", "n":[1,[2]] } ')).toBe(
      '{\n  "a": [],\n  "b": {},\n  "s": "x, {y}: \\"z\\"",\n  "n": [\n    1,\n    [\n      2\n    ]\n  ]\n}',
    );
  });

  it('refuses text that is not JSON', () => {
    expect(() => formatJson('{"a":')).toThrow();
  });
});
