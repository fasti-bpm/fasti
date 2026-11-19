/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Geometry of the Fasti mark, identical to the mark on fasti.dev
 * (64x64 grid). The single source for the header monogram; the favicon
 * (public/assets/favicon.svg) is kept equal by logoMark.test.ts.
 */
export const LOGO_MARK = {
  viewBox: '0 0 64 64',
  tile: { x: 1, y: 1, width: 62, height: 62, rx: 14 },
  flow: {
    d: 'M21 47V25a8 8 0 0 1 8-8h15M21 32.5h15',
    strokeWidth: 7,
  },
  states: [
    { cx: 44, cy: 17, r: 5.2 },
    { cx: 36, cy: 32.5, r: 5.2 },
    { cx: 21, cy: 47, r: 5.2 },
  ],
  // colours of the mark on fasti.dev, used until the app gets its own
  colors: {
    tile: ['#1d2946', '#0a0f1a'],
    tileRing: { color: '#ffffff', opacity: 0.16, width: 1.5 },
    flow: ['#ffb199', '#e2492d'],
    state: '#ffffff',
  },
} as const;
