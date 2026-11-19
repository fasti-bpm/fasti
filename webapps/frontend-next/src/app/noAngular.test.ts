/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

/*
 * AngularJS stays inside the form frame of embedded forms
 * (webapps/frontend/ui/forms); no page of /app/next may load it.
 */

// the tests run in the folder of the new web UI
const root = process.cwd();

function files(directory: string, extensions: RegExp): string[] {
  if (!existsSync(directory)) {
    return [];
  }
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    if (statSync(path).isDirectory()) {
      return files(path, extensions);
    }
    return extensions.test(name) ? [path] : [];
  });
}

const ANGULAR_IMPORT = /(?:from\s+|import\s*\(\s*|require\s*\(\s*)['"](?:angular|angular-[\w-]+|@uirouter\/angularjs|camunda-commons-ui[^'"]*|camunda-bpm-sdk-js\/lib\/angularjs[^'"]*)['"]/;

describe('AngularJS', () => {
  it('is no dependency of the new web UI', () => {
    const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8')) as {
      dependencies?: Record<string, string>;
      devDependencies?: Record<string, string>;
    };
    const names = [...Object.keys(pkg.dependencies ?? {}), ...Object.keys(pkg.devDependencies ?? {})];
    expect(names.filter((name) => /angular/i.test(name))).toEqual([]);
  });

  it('is not imported by any source file', () => {
    const offenders = files(join(root, 'src'), /\.(ts|tsx|js|mjs)$/).filter((file) =>
      ANGULAR_IMPORT.test(readFileSync(file, 'utf8')),
    );
    expect(offenders).toEqual([]);
  });

  it('is not part of the built pages, if they are built', () => {
    // the bundle of AngularJS announces itself in its license comment and its bootstrap
    const offenders = files(join(root, 'target', 'webapp', 'app', 'next'), /\.(js|html)$/).filter((file) => {
      const content = readFileSync(file, 'utf8');
      return /@license AngularJS|angular\.bootstrap\(|ng-app=/.test(content);
    });
    expect(offenders).toEqual([]);
    // the built pages are large; reading them takes a while on a busy machine
  }, 60_000);
});
