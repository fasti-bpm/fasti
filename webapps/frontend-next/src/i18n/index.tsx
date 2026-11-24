/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { config, onLateConfig } from '../config';
import { formatDateTime, formatRelative } from '../lib/dates';
import { onPreferencesReplaced, readPreference, writePreference } from '../lib/storage';
import type { AnyKey, LocaleManifest, MessageKey } from './catalog';
import { PLURAL_VARIANTS } from './catalog';
import enAnalytics from './locales/en/analytics';
import enAudit from './locales/en/audit';
import enCmmn from './locales/en/cmmn';
import enCore from './locales/en/core';
import enOps from './locales/en/ops';
import enTools from './locales/en/tools';

export type { LocaleManifest, MessageKey };

/** A BCP 47 tag of a language found in `locales/`. */
export type Locale = string;

type Messages = Partial<Record<string, string>>;

/** English is bundled: it is the fallback of every other language. */
const english: Messages = { ...enCore, ...enOps, ...enTools, ...enAudit, ...enCmmn, ...enAnalytics } satisfies Record<AnyKey, string>;

// Every folder below locales/ is a language: its manifest is bundled, its
// messages are separate chunks loaded when the language is chosen.
const manifestModules = import.meta.glob<LocaleManifest>('./locales/*/manifest.ts', {
  eager: true,
  import: 'default',
});
const messageModules = import.meta.glob<Messages>(['./locales/*/*.ts', '!./locales/*/manifest.ts', '!./locales/en/*'], {
  import: 'default',
});

const folder = (path: string): string => path.split('/')[2] ?? '';

/** The languages of the user interface: English first, then by English name. */
export const LOCALE_MANIFESTS: readonly LocaleManifest[] = Object.entries(manifestModules)
  .map(([path, manifest]) => {
    if (manifest.tag !== folder(path)) {
      throw new Error(`The manifest in ${path} declares the tag ${manifest.tag}`);
    }
    return manifest;
  })
  .sort((a, b) => (a.tag === 'en' ? -1 : b.tag === 'en' ? 1 : a.englishName.localeCompare(b.englishName)));

export const LOCALES: readonly Locale[] = LOCALE_MANIFESTS.map((manifest) => manifest.tag);

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && LOCALES.includes(value);
}

export function manifestOf(locale: Locale): LocaleManifest {
  return LOCALE_MANIFESTS.find((manifest) => manifest.tag === locale) ?? (manifestModules['./locales/en/manifest.ts'] as LocaleManifest);
}

const loaded = new Map<Locale, Messages>([['en', english]]);
const loading = new Map<Locale, Promise<void>>();

export function isLoaded(locale: Locale): boolean {
  return loaded.has(locale);
}

/** Loads the messages of a language; resolves at once for a loaded one. */
export function loadLocale(locale: Locale): Promise<void> {
  if (loaded.has(locale) || !isLocale(locale)) {
    return Promise.resolve();
  }
  let pending = loading.get(locale);
  if (!pending) {
    const parts = Object.entries(messageModules).filter(([path]) => folder(path) === locale);
    pending = Promise.all(parts.map(([, load]) => load()))
      .then((messages) => {
        loaded.set(locale, Object.assign({}, ...messages) as Messages);
      })
      .finally(() => loading.delete(locale));
    loading.set(locale, pending);
  }
  return pending;
}

/** Loads every language; for tests and the catalogue checks. */
export async function loadAllLocales(): Promise<void> {
  await Promise.all(LOCALES.map(loadLocale));
}

export type MessageParams = Record<string, string | number>;

export type Translate = (key: MessageKey, params?: MessageParams) => string;

const pluralRules = new Map<Locale, Intl.PluralRules>();

function pluralCategory(locale: Locale, count: number): Intl.LDMLPluralRule {
  let rules = pluralRules.get(locale);
  if (!rules) {
    rules = new Intl.PluralRules(locale);
    pluralRules.set(locale, rules);
  }
  return rules.select(count);
}

/**
 * The message of a key in a language. A message the language lacks is taken
 * from English. With a numeric `count`, the variant for its plural category
 * (`key.one`, `key.few`, ...) is used if the catalogue has one.
 */
function lookup(locale: Locale, key: string, count: unknown): string | undefined {
  const catalog = loaded.get(locale);
  const base = catalog?.[key];
  if (base === undefined) {
    return locale === 'en' ? undefined : lookup('en', key, count);
  }
  if (typeof count === 'number') {
    const category = pluralCategory(locale, count);
    if ((PLURAL_VARIANTS as readonly string[]).includes(category)) {
      return catalog?.[`${key}.${category}`] ?? base;
    }
  }
  return base;
}

/**
 * Translates a key. A numeric `count` selects the plural variant and is shown
 * with the digit grouping of the language, so callers pass the number itself:
 * a count formatted beforehand cannot select a plural variant.
 */
export function translate(locale: Locale, key: MessageKey, params?: MessageParams): string {
  const message = lookup(locale, key, params?.count) ?? key;
  if (!params) {
    return message;
  }
  return message.replace(/\{(\w+)\}/g, (placeholder, name: string) => {
    if (!(name in params)) {
      return placeholder;
    }
    const value = params[name];
    return name === 'count' && typeof value === 'number' ? new Intl.NumberFormat(locale).format(value) : String(value);
  });
}

export interface I18n {
  locale: Locale;
  manifest: LocaleManifest;
  setLocale: (locale: Locale) => void;
  t: Translate;
  /** "5 minutes ago" */
  relative: (date: Date) => string;
  dateTime: (date: Date) => string;
  number: (value: number) => string;
}

const I18nContext = createContext<I18n | null>(null);

/** Whether the user has chosen a language; only then is it remembered. */
export function storedLocale(): Locale | null {
  const stored = readPreference<string>('locale', '');
  return isLocale(stored) ? stored : null;
}

/**
 * The language the user chose earlier, otherwise the default of the
 * installation (English unless `config.json` says otherwise). The language of
 * the browser is never applied by itself; the login page only suggests it.
 */
export function initialLocale(): Locale {
  return storedLocale() ?? (isLocale(config.defaultLocale) ? config.defaultLocale : 'en');
}

/** The first language of the browser that the user interface knows, if any. */
export function browserLocale(languages: readonly string[] = navigator.languages ?? []): Locale | null {
  for (const language of languages) {
    const exact = LOCALES.find((tag) => tag.toLowerCase() === language.toLowerCase());
    if (exact) {
      return exact;
    }
    const primary = language.split('-')[0]?.toLowerCase();
    const sameLanguage = LOCALES.find((tag) => tag.split('-')[0]?.toLowerCase() === primary);
    if (sameLanguage) {
      return sameLanguage;
    }
  }
  return null;
}

export function I18nProvider({ children, initial }: { children: ReactNode; initial?: Locale }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    const wanted = initial ?? initialLocale();
    // a language that is not loaded yet is shown once it is
    return isLoaded(wanted) ? wanted : 'en';
  });

  // the language asked for last; a slower earlier load must not override it
  const requested = useRef<Locale>(locale);

  // the user picked a language in this page: a default that arrives late does not replace it
  const chosen = useRef(false);

  const apply = useCallback((next: Locale, remember: boolean) => {
    requested.current = next;
    loadLocale(next).then(
      () => {
        if (requested.current === next) {
          if (remember) {
            writePreference('locale', next);
          }
          setLocaleState(next);
        }
      },
      () => {
        // the chunk could not be loaded: the current language stays and is not replaced in storage
      },
    );
  }, []);

  useEffect(() => {
    const wanted = initial ?? initialLocale();
    if (wanted !== locale && !isLoaded(wanted)) {
      apply(wanted, false);
    }
    // only on mount: later changes go through setLocale
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = manifestOf(locale).dir;
  }, [locale]);

  // preferences loaded from the server replace the stored choice
  useEffect(() => {
    if (initial) return undefined;
    return onPreferencesReplaced(() => apply(initialLocale(), false));
  }, [initial, apply]);

  // config.json came after the page had started with the defaults: its default language applies
  // unless the user chose one
  useEffect(() => {
    if (initial) return undefined;
    return onLateConfig(() => {
      if (!chosen.current && !storedLocale()) apply(initialLocale(), false);
    });
  }, [initial, apply]);

  const setLocale = useCallback(
    (next: Locale) => {
      if (isLocale(next)) {
        chosen.current = true;
        apply(next, true);
      }
    },
    [apply],
  );

  const value = useMemo<I18n>(() => {
    const numbers = new Intl.NumberFormat(locale);
    return {
      locale,
      manifest: manifestOf(locale),
      setLocale,
      t: (key, params) => translate(locale, key, params),
      relative: (date) => formatRelative(date, locale),
      dateTime: (date) => formatDateTime(date, locale),
      number: (value) => numbers.format(value),
    };
  }, [locale, setLocale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useI18n must be used inside I18nProvider');
  }
  return context;
}
