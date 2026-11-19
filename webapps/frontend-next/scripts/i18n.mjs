/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Checks and scaffolds the message catalogues in src/i18n/locales.
 *
 *   node scripts/i18n.mjs check [--verbose]   missing, extra and unused keys,
 *                                             placeholder and plural mismatches
 *   node scripts/i18n.mjs new <tag>           a folder for a new language
 *
 * The catalogue files contain only type imports, so Node.js loads them
 * directly (type stripping).
 */
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = join(root, 'src');
const localesDir = join(src, 'i18n', 'locales');
const VARIANTS = ['zero', 'one', 'two', 'few', 'many'];
const VARIANT = new RegExp(`^(.+)\\.(${VARIANTS.join('|')})$`);

const placeholders = (message) => new Set(Array.from(message.matchAll(/\{(\w+)\}/g), (match) => match[1]));
const parts = (tag) =>
  readdirSync(join(localesDir, tag))
    .filter((name) => name.endsWith('.ts') && name !== 'manifest.ts')
    .sort();

async function load(file) {
  return (await import(pathToFileURL(file).href)).default;
}

async function loadLocale(tag, problems) {
  const messages = {};
  for (const part of parts(tag)) {
    const values = await load(join(localesDir, tag, part));
    for (const [key, value] of Object.entries(values)) {
      if (key in messages) {
        problems.push(`${tag}: ${key} is defined twice (${part})`);
      }
      messages[key] = value;
    }
  }
  return messages;
}

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return path === localesDir ? [] : sourceFiles(path);
    }
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name) ? [path] : [];
  });
}

/** Keys that appear nowhere in the code as a literal; keys built at run time show up here too. */
function unusedKeys(keys) {
  const code = sourceFiles(src)
    .map((file) => readFileSync(file, 'utf8'))
    .join('\n');
  const prefixes = Array.from(code.matchAll(/`([a-z][\w.]*\.)\$\{/g), (match) => match[1]);
  return keys.filter(
    (key) =>
      !code.includes(`'${key}'`) &&
      !code.includes(`"${key}"`) &&
      !code.includes(`\`${key}\``) &&
      !prefixes.some((prefix) => key.startsWith(prefix)),
  );
}

async function check(verbose) {
  const errors = [];
  const warnings = [];
  const tags = readdirSync(localesDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
  const en = await loadLocale('en', errors);
  const baseKeys = Object.keys(en).filter((key) => !VARIANT.test(key) || !(key.replace(VARIANT, '$1') in en));
  const report = [];

  for (const tag of tags) {
    const manifestFile = join(localesDir, tag, 'manifest.ts');
    if (!existsSync(manifestFile)) {
      errors.push(`${tag}: manifest.ts is missing`);
      continue;
    }
    const manifest = await load(manifestFile);
    if (manifest.tag !== tag) {
      errors.push(`${tag}: the manifest declares the tag ${manifest.tag}`);
    }
    if (!['ltr', 'rtl'].includes(manifest.dir) || !manifest.nativeName || !manifest.englishName) {
      errors.push(`${tag}: the manifest needs nativeName, englishName and dir (ltr or rtl)`);
    }
    if (Intl.PluralRules.supportedLocalesOf([tag]).length === 0) {
      errors.push(`${tag}: Intl.PluralRules does not know this language`);
    }
    const messages = tag === 'en' ? en : await loadLocale(tag, errors);
    const categories = new Set(new Intl.PluralRules(tag).resolvedOptions().pluralCategories);
    const missing = baseKeys.filter((key) => !(key in messages));
    const extra = [];

    for (const [key, message] of Object.entries(messages)) {
      if (typeof message !== 'string' || !message.trim()) {
        errors.push(`${tag}: ${key} is empty`);
        continue;
      }
      if (key !== 'about.builtOn' && /camunda/i.test(message)) {
        errors.push(`${tag}: ${key} names the upstream product; only about.builtOn may`);
      }
      const variant = VARIANT.exec(key);
      const base = variant && variant[1] in en ? variant[1] : key;
      if (!(base in en)) {
        extra.push(key);
        continue;
      }
      if (base !== key && !categories.has(variant[2])) {
        errors.push(`${tag}: ${key} is a plural form that ${tag} does not have (${[...categories].join(', ')})`);
      }
      const expected = placeholders(en[base]);
      const actual = placeholders(message);
      for (const name of actual) {
        if (!expected.has(name)) {
          errors.push(`${tag}: ${key} has the unknown placeholder {${name}}`);
        }
      }
      for (const name of expected) {
        // a plural form may write the number out ("1 task"), all others need every placeholder
        if (!actual.has(name) && !(base !== key && name === 'count')) {
          errors.push(`${tag}: ${key} lacks the placeholder {${name}}`);
        }
      }
    }
    for (const key of extra) {
      errors.push(`${tag}: ${key} is not an English key`);
    }
    for (const key of missing) {
      errors.push(`${tag}: ${key} is missing`);
    }
    report.push(`${tag.padEnd(6)} ${String(Object.keys(messages).length).padStart(5)} messages, ${missing.length} missing, ${extra.length} extra${manifest.reviewed ? '' : ' (not reviewed)'}`);
  }

  const unused = unusedKeys(baseKeys);
  if (unused.length > 0) {
    warnings.push(`${unused.length} English keys appear nowhere in the code as a literal (built at run time, or unused)`);
    if (verbose) {
      warnings.push(...unused.map((key) => `  ${key}`));
    }
  }

  console.log(report.join('\n'));
  for (const warning of warnings) {
    console.warn(`warning: ${warning}`);
  }
  for (const error of errors) {
    console.error(`error: ${error}`);
  }
  if (errors.length > 0) {
    console.error(`${errors.length} problems in the message catalogues`);
    process.exit(1);
  }
}

function scaffold(tag) {
  if (!tag || !/^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(tag)) {
    console.error('usage: npm run i18n:new -- <BCP 47 tag>, e.g. ar or fr-CA');
    process.exit(2);
  }
  const canonical = Intl.getCanonicalLocales(tag)[0];
  const folder = join(localesDir, canonical);
  if (existsSync(folder)) {
    console.error(`${relative(root, folder)} exists already`);
    process.exit(1);
  }
  mkdirSync(folder);
  const header = readFileSync(join(localesDir, 'en', 'manifest.ts'), 'utf8').split(/\r?\n/).slice(0, 16).join('\n');
  const locale = new Intl.Locale(canonical);
  const info = locale.getTextInfo?.() ?? locale.textInfo;
  const quote = (text) => `'${String(text).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
  writeFileSync(
    join(folder, 'manifest.ts'),
    `${header}
import type { LocaleManifest } from '../../catalog';

const manifest: LocaleManifest = {
  tag: '${canonical}',
  nativeName: ${quote(new Intl.DisplayNames([canonical], { type: 'language' }).of(canonical))},
  englishName: ${quote(new Intl.DisplayNames(['en'], { type: 'language' }).of(canonical))},
  dir: '${info?.direction === 'rtl' ? 'rtl' : 'ltr'}',
  reviewed: false,
};

export default manifest;
`,
  );
  const categories = new Intl.PluralRules(canonical).resolvedOptions().pluralCategories;
  for (const part of parts('en')) {
    const english = readFileSync(join(localesDir, 'en', part), 'utf8');
    const lines = Array.from(english.matchAll(/^ {2}'([^']+)':\s*([\s\S]*?),\r?$/gm), ([, key, value]) => {
      const variant = VARIANT.exec(key);
      return variant && !categories.includes(variant[2]) ? null : `  // '${key}': ${value.trim()},`;
    }).filter(Boolean);
    writeFileSync(
      join(folder, part),
      `${header}
import type { Catalog } from '../../catalog';

// Translate a line and remove the comment marker; untranslated keys fall back to English.
const messages: Catalog = {
${lines.join('\n')}
};

export default messages;
`,
    );
  }
  console.log(`Created ${relative(root, folder)}; plural categories of ${canonical}: ${categories.join(', ')}`);
}

const [command, ...args] = process.argv.slice(2);
if (command === 'check') {
  await check(args.includes('--verbose'));
} else if (command === 'new') {
  scaffold(args[0]);
} else {
  console.error('usage: node scripts/i18n.mjs check [--verbose] | new <tag>');
  process.exit(2);
}
