/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Types of the message catalogues. The files of a language contain only type
 * imports, so that `scripts/i18n.mjs` can load them with plain Node.js.
 *
 * Layout: `locales/<BCP 47 tag>/manifest.ts` describes the language, every
 * other `.ts` file in the folder holds a part of its messages. The parts are
 * merged; how the keys are split among the files does not matter.
 */
import type audit from './locales/en/audit';
import type cmmn from './locales/en/cmmn';
import type core from './locales/en/core';
import type ops from './locales/en/ops';
import type tools from './locales/en/tools';

type English = typeof core & typeof ops & typeof tools & typeof audit & typeof cmmn;

/**
 * Plural categories of `Intl.PluralRules` that a message may have as a
 * variant: `tasks.count.one`, `tasks.count.few`, ... The key without suffix
 * is the `other` form, used whenever a language has no variant for the
 * category of the count.
 */
export const PLURAL_VARIANTS = ['zero', 'one', 'two', 'few', 'many'] as const;
export type PluralVariant = (typeof PLURAL_VARIANTS)[number];

/** Every key of the English catalogue, plural variants included. */
export type AnyKey = keyof English;

/** The keys the code passes to `t()`; plural variants are found through their base key. */
export type MessageKey = Exclude<AnyKey, `${string}.${PluralVariant}`>;

/**
 * A part of the messages of a language other than English. Unknown keys are
 * compile errors; missing keys fall back to English at run time and are
 * reported by `npm run i18n:check`.
 */
export type Catalog = Partial<Record<MessageKey, string>> & {
  [variant: `${string}.${PluralVariant}`]: string;
};

export interface LocaleManifest {
  /** BCP 47 tag, equal to the name of the folder */
  tag: string;
  /** name of the language in the language itself, shown in the language menu */
  nativeName: string;
  englishName: string;
  dir: 'ltr' | 'rtl';
  /** false while the translation has not been checked by a native speaker */
  reviewed: boolean;
}
