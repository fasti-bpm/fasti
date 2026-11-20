/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

/*
 * The style sheet of an area loads with its first page. A component that more
 * than one area uses must not be styled there, or it looks different
 * depending on the page that was opened first.
 */

// the tests run in the folder of the new web UI
const src = join(process.cwd(), 'src');

function files(directory: string, extensions: RegExp): string[] {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) {
      return files(path, extensions);
    }
    return extensions.test(name) ? [path] : [];
  });
}

/** The style sheets main.tsx loads with the application. */
function globalSheets(): string[] {
  const main = readFileSync(join(src, 'main.tsx'), 'utf8');
  return Array.from(main.matchAll(/import '\.\/([\w/]+\.css)';/g), (match) => join(src, match[1] ?? ''));
}

describe('style sheets', () => {
  it('load the shared components with the application', () => {
    // the selectors of every rule of the global sheets, one per entry
    const selectors = new Set(
      globalSheets()
        .map((path) => readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\//g, ''))
        .flatMap((css) => Array.from(css.matchAll(/([^{}]+)\{/g), (match) => match[1] ?? ''))
        .flatMap((list) => list.split(','))
        .map((selector) => selector.trim()),
    );
    for (const selector of ['.page-header', '.card__header', '.table th', '.table-pager', '.ctabs__tab', '.tabs__tab', '.segmented__item', '.confirm__actions', '.option__label', '.muted', '.search-input', '.toolbar', '.area__bar']) {
      expect(selectors.has(selector), selector).toBe(true);
    }
  });

  it('of the monitoring section are loaded by that section only', () => {
    const importers = files(src, /\.tsx?$/)
      .filter((path) => !/\.test\.tsx?$/.test(path))
      .filter((path) => /import '[./]*(?:cockpit\/)?cockpit\.css'/.test(readFileSync(path, 'utf8')))
      .map((path) => relative(src, path).replaceAll('\\', '/'));
    expect(importers).toEqual(['cockpit/CockpitLayout.tsx']);
  });

  it('take sizes, weights, radii and layers from the design tokens', () => {
    const literals = files(src, /\.css$/)
      .filter((path) => !path.endsWith('tokens.css'))
      .flatMap((path) =>
        readFileSync(path, 'utf8')
          .split(/\r?\n/)
          .map((line, index) => ({ line: line.trim(), where: `${relative(src, path).replaceAll('\\', '/')}:${index + 1}` }))
          .filter(({ line }) => /^(font-weight:\s*\d|z-index:\s*\d|border-radius:\s*\d+px)/.test(line)),
      )
      .map(({ line, where }) => `${where} ${line}`);
    expect(literals).toEqual([]);
  });
});

/*
 * The colours of text and of the frames of fields meet WCAG 2.2 AA in both
 * themes: 4.5:1 for text, 3:1 for the frame of a field (1.4.11).
 */
function tokens(block: RegExp): Record<string, string> {
  const css = readFileSync(join(src, 'theme', 'tokens.css'), 'utf8');
  const body = css.match(block)?.[1] ?? '';
  return Object.fromEntries(Array.from(body.matchAll(/--([\w-]+):\s*(#[0-9a-f]{6});/gi), (match) => [match[1], match[2]]));
}

function luminance(hex: string): number {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return ((light ?? 0) + 0.05) / ((dark ?? 0) + 0.05);
}

describe.each([
  ['light', /^:root \{([^}]*)\}/m],
  ['dark', /^:root\[data-theme='dark'\] \{([^}]*)\}/m],
])('the %s theme', (_, block) => {
  const colours = tokens(block);
  const surfaces = ['bg', 'surface', 'surface-subtle', 'surface-hover', 'surface-raised', 'input-bg'].filter((name) => colours[name]);

  it.each(['text', 'text-muted', 'text-subtle'])('has readable %s on every surface', (text) => {
    for (const surface of surfaces) {
      expect(contrast(colours[text] ?? '', colours[surface] ?? ''), `${text} on ${surface}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('frames fields visibly', () => {
    for (const surface of ['bg', 'surface', 'input-bg', 'surface-raised'].filter((name) => colours[name])) {
      expect(contrast(colours['border-control'] ?? '', colours[surface] ?? ''), `border-control on ${surface}`).toBeGreaterThanOrEqual(3);
    }
  });

  it('has readable text on the accent and the danger colour', () => {
    expect(contrast(colours['text-on-accent'] ?? '', colours.accent ?? '')).toBeGreaterThanOrEqual(4.5);
    expect(contrast(colours['text-on-danger'] ?? '', colours.danger ?? '')).toBeGreaterThanOrEqual(4.5);
  });
});
