/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Locale } from '../i18n';
import vi from './texts/vi';
import bpmnVi from './texts/bpmn-vi';

/**
 * The model libraries (dmn-js, form-js) write their own interface texts in
 * English and look every text up through a "translate" service. This module
 * replaces that service with one that reads the texts of the user's language;
 * a text without a translation stays English. Placeholders such as {label}
 * are filled in either way.
 */
export type LibraryTexts = Readonly<Record<string, string>>;

const TEXTS: Partial<Record<Locale, LibraryTexts>> = { vi };

export function libraryTexts(locale: Locale): LibraryTexts {
  return TEXTS[locale] ?? {};
}

export type Translate = (template: string, replacements?: Record<string, unknown>) => string;

export function libraryTranslate(texts: LibraryTexts): Translate {
  return (template, replacements) =>
    (texts[template] ?? template).replace(/\{([^}]+)\}/g, (whole, key: string) =>
      replacements && key in replacements ? String(replacements[key]) : whole,
    );
}

/** a module for the libraries' dependency injection that provides the translate service */
export function translateModule(locale: Locale): { translate: ['value', Translate] } {
  return { translate: ['value', libraryTranslate(libraryTexts(locale))] };
}

/** the options of dmn-js that give every view of a model the translate service */
export function dmnTranslation(locale: Locale) {
  const modules = [translateModule(locale)];
  return {
    decisionTable: { additionalModules: modules },
    literalExpression: { additionalModules: modules },
    boxedExpression: { additionalModules: modules },
  };
}

const BPMN_TEXTS: Partial<Record<Locale, LibraryTexts>> = { vi: bpmnVi };

/** The texts of the bpmn-js palette, context pad and menus in a language; empty for English. */
export function bpmnTexts(locale: Locale): LibraryTexts {
  return BPMN_TEXTS[locale] ?? {};
}

interface PopupEntry {
  label?: string;
  search?: string | string[];
  entries?: Record<string, PopupEntry>;
}
type PopupEntries = Record<string, PopupEntry>;

/** Adds the English name of every translated entry to its search words, recursively. */
export function withEnglishSearch(entries: PopupEntries, english: ReadonlyMap<string, string>): PopupEntries {
  const result: PopupEntries = {};
  for (const [id, entry] of Object.entries(entries)) {
    const original = entry.label ? english.get(entry.label) : undefined;
    const search = entry.search === undefined ? [] : Array.isArray(entry.search) ? entry.search : [entry.search];
    result[id] = {
      ...entry,
      ...(original ? { search: [...search, original] } : {}),
      ...(entry.entries ? { entries: withEnglishSearch(entry.entries, english) } : {}),
    };
  }
  return result;
}

const POPUP_MENUS = ['bpmn-replace', 'bpmn-append', 'bpmn-create'];

/**
 * Modules for bpmn-js: its texts in the user's language, and menus that still
 * find an element by its English name ("user task" in a Vietnamese editor),
 * since many people learnt the notation in English.
 */
export function bpmnTranslationModules(locale: Locale): unknown[] {
  const texts = bpmnTexts(locale);
  if (Object.keys(texts).length === 0) return [];
  const english = new Map(Object.entries(texts).map(([original, translated]) => [translated, original]));
  function EnglishSearch(popupMenu: { registerProvider(id: string, priority: number, provider: unknown): void }) {
    const provider = {
      getPopupMenuEntries: () => (entries: PopupEntries) => withEnglishSearch(entries, english),
    };
    // the lowest priority runs last, after the providers of bpmn-js filled the menu
    for (const id of POPUP_MENUS) popupMenu.registerProvider(id, 1, provider);
  }
  EnglishSearch.$inject = ['popupMenu'];
  return [
    { translate: ['value', libraryTranslate(texts)] },
    { __init__: ['fastiEnglishSearch'], fastiEnglishSearch: ['type', EnglishSearch] },
  ];
}
