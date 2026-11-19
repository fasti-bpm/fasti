/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { measureOverflow } from './OverflowMenu';

function box(left: number, right: number): HTMLElement {
  const element = document.createElement('a');
  element.getBoundingClientRect = () => ({ left, right, width: right - left }) as DOMRect;
  return element;
}

describe('measureOverflow', () => {
  const container = box(0, 300);

  it('keeps every item when all fit', () => {
    expect(measureOverflow(container, [box(0, 100), box(100, 200), box(200, 300)])).toEqual([]);
  });

  it('moves what does not fit, and keeps room for the menu', () => {
    // the third item ends within the room of the menu, the fourth beyond the row
    expect(measureOverflow(container, [box(0, 100), box(100, 200), box(200, 260), box(260, 360)])).toEqual([2, 3]);
  });

  it('measures nothing in a row that is not laid out', () => {
    expect(measureOverflow(box(0, 0), [box(0, 100)])).toEqual([]);
  });
});
