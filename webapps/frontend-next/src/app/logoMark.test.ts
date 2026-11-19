/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { LOGO_MARK } from './logoMark';

// the tests run in the folder of the new web UI
const favicon = readFileSync(join(process.cwd(), 'public', 'assets', 'favicon.svg'), 'utf8');

describe('favicon', () => {
  it('has the same geometry as the header mark', () => {
    const { tile, flow, states } = LOGO_MARK;
    expect(favicon).toContain(`viewBox="${LOGO_MARK.viewBox}"`);
    expect(favicon).toContain(
      `<rect x="${tile.x}" y="${tile.y}" width="${tile.width}" height="${tile.height}" rx="${tile.rx}"`,
    );
    expect(favicon).toContain(`d="${flow.d}"`);
    expect(favicon).toContain(`stroke-width="${flow.strokeWidth}"`);
    for (const s of states) {
      expect(favicon).toContain(`<circle cx="${s.cx}" cy="${s.cy}" r="${s.r}"`);
    }
  });
});
