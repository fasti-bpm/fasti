/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// Checks the license header of every source file.
//
// Rule (see .github/CONTRIBUTING.md): files written for Fasti BPM, i.e. files that do
// not exist in the upstream tag 7.24.0, must start with the Fasti header (the
// one at the top of this file). Files that exist upstream keep the header of
// Camunda 7 CE, as do new files copied from an upstream file (listed in
// build-config/license-header-upstream-derived.txt). Which files exist upstream
// is read with `git ls-tree -r --name-only 7.24.0` at the repository root.
// Without git or the tag (a shallow clone) the check fails when CI is set and
// otherwise accepts both headers with a warning.
// `--fix` adds the Fasti header to files that have neither header.
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { extname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const self = fileURLToPath(import.meta.url);
// Line endings depend on the checkout (core.autocrlf), so compare with LF.
const toLf = (text) => text.replace(/\r\n/g, '\n');
const fastiHeader = toLf(readFileSync(self, 'utf8')).split(' */')[0] + ' */';
const upstreamHeaderStart = '/*\n * Copyright Camunda Services GmbH';
const extensions = new Set(['.ts', '.tsx', '.css', '.js', '.mjs']);
const skipped = new Set(['node_modules', 'node', 'target']);
const fix = process.argv.includes('--fix');

// Files of the tag 7.24.0 below this directory, relative to the repository
// root with "/", or null when git or the tag is not available.
function upstreamFiles() {
  try {
    const git = (args) =>
      execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    const top = git(['rev-parse', '--show-toplevel']).trim();
    const prefix = relative(top, root).split(sep).join('/');
    // ls-tree takes and prints paths relative to its working directory: run it at the root
    const names = execFileSync('git', ['ls-tree', '-r', '--name-only', '7.24.0', '--', prefix || '.'],
      { cwd: top, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    let derived = [];
    try {
      derived = readFileSync(join(top, 'build-config', 'license-header-upstream-derived.txt'), 'utf8')
        .split(/\r?\n/).map((line) => line.trim()).filter((line) => line && !line.startsWith('#'));
    } catch {
      // no list: no derived files
    }
    return { top, names: new Set(names.split('\n').filter(Boolean)), derived: new Set(derived) };
  } catch {
    return null;
  }
}

function* sourceFiles(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!skipped.has(entry.name)) {
        yield* sourceFiles(join(directory, entry.name));
      }
    } else if (extensions.has(extname(entry.name))) {
      yield join(directory, entry.name);
    }
  }
}

const upstream = upstreamFiles();
if (!upstream) {
  if (process.env.CI) {
    console.error('check-license-headers: git or tag 7.24.0 not available; fetch the full history in CI');
    process.exit(1);
  }
  console.warn('check-license-headers: git or tag 7.24.0 not available, accepting both headers');
}
const missing = [];
const wrong = [];
const wrongFasti = [];
for (const file of sourceFiles(root)) {
  const content = readFileSync(file, 'utf8');
  const text = toLf(content);
  const hasFasti = text.startsWith(fastiHeader);
  const hasUpstream = text.startsWith(upstreamHeaderStart);
  const path = upstream ? relative(upstream.top, file).split(sep).join('/') : null;
  // files of 7.24.0 and copies of them keep the Camunda header (upstream attribution)
  const keepsUpstream = upstream ? upstream.names.has(path) || upstream.derived.has(path) : false;
  if (!upstream ? hasFasti || hasUpstream : keepsUpstream ? hasUpstream : hasFasti) {
    continue;
  }
  if (hasFasti && keepsUpstream) {
    wrongFasti.push(file);
  } else if (hasUpstream) {
    wrong.push(file);
  } else if (fix) {
    writeFileSync(file, `${fastiHeader}\n${content}`);
  } else {
    missing.push(file);
  }
}

if (wrong.length > 0) {
  console.error(`Camunda header on a file that is not in 7.24.0, use the Fasti header:\n  ${wrong.join('\n  ')}`);
}
if (wrongFasti.length > 0) {
  console.error(`Fasti header on a file from 7.24.0 or copied from one, keep the Camunda header:\n  ${wrongFasti.join('\n  ')}`);
}
if (missing.length > 0) {
  console.error(`License header missing in:\n  ${missing.join('\n  ')}`);
}
if (wrong.length > 0 || wrongFasti.length > 0 || missing.length > 0) {
  process.exit(1);
}
