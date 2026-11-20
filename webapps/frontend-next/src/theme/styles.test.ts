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
    const global = globalSheets().map((path) => readFileSync(path, 'utf8')).join('\n');
    for (const selector of ['.page-header', '.card__header', '.table th', '.table-pager', '.ctabs__tab', '.confirm__actions', '.option__label', '.muted', '.search-input', '.toolbar', '.area__bar']) {
      expect(global, selector).toContain(`${selector} `);
    }
  });

  it('of the monitoring section are loaded by that section only', () => {
    const importers = files(src, /\.tsx?$/)
      .filter((path) => !/\.test\.tsx?$/.test(path))
      .filter((path) => /import '[./]*(?:cockpit\/)?cockpit\.css'/.test(readFileSync(path, 'utf8')))
      .map((path) => relative(src, path).replaceAll('\\', '/'));
    expect(importers).toEqual(['cockpit/CockpitLayout.tsx']);
  });
});
