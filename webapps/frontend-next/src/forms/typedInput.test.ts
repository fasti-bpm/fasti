/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { convertInput, jsonProblem } from './variables';

describe('typed variable input', () => {
  it('checks the range of the integer types', () => {
    expect(convertInput('Short', '32767')).toEqual({ ok: true, value: 32767 });
    expect(convertInput('Short', '32768')).toEqual({ ok: false, error: 'validation.range' });
    expect(convertInput('Integer', '-2147483649')).toEqual({ ok: false, error: 'validation.range' });
    expect(convertInput('Long', '9007199254740993')).toEqual({ ok: false, error: 'validation.unsafeLong' });
    expect(convertInput('Long', '9223372036854775808')).toEqual({ ok: false, error: 'validation.range' });
    expect(convertInput('Long', '1.5')).toEqual({ ok: false, error: 'validation.integer' });
  });

  it('accepts decimal notations for doubles only', () => {
    expect(convertInput('Double', '1,5')).toEqual({ ok: true, value: 1.5 });
    expect(convertInput('Double', '2e3')).toEqual({ ok: true, value: 2000 });
    expect(convertInput('Double', 'Infinity')).toEqual({ ok: false, error: 'validation.number' });
    expect(convertInput('Double', '0x10')).toEqual({ ok: false, error: 'validation.number' });
  });

  it('points to where JSON is broken', () => {
    expect(jsonProblem('{"a": 1}')).toBeNull();
    const problem = jsonProblem('{\n  "a": 1,\n  "b": }');
    expect(problem?.line).toBe(3);
    expect(problem?.column).toBeGreaterThan(1);
  });
});
