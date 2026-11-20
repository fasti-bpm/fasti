/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { readPreference, writePreference } from '../lib/storage';

// State of list pages: filters and pages in the address, remembered choices, debounced input.

/**
 * Filters, pages and tabs live in the query string, so that every view can be
 * linked. Setting a value to `null` (or its default) removes it.
 */
export function useUrlState(): [
  URLSearchParams,
  (changes: Record<string, string | null>, options?: { push?: boolean }) => void,
] {
  const [params, setParams] = useSearchParams();
  const update = useCallback(
    (changes: Record<string, string | null>, options?: { push?: boolean }) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [name, value] of Object.entries(changes)) {
            if (value) next.set(name, value);
            else next.delete(name);
          }
          return next;
        },
        { replace: !options?.push },
      );
    },
    [setParams],
  );
  return [params, update];
}

export function pageOf(params: URLSearchParams, name = 'page'): number {
  return Math.max(0, Number(params.get(name) ?? '1') - 1 || 0);
}

/**
 * A choice the browser remembers, e.g. the sorting of a table. The keys keep the prefix of
 * the monitoring pages, where this began, so that choices made before stay.
 */
export function useRemembered<T>(key: string, fallback: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => readPreference(`cockpit.${key}`, fallback));
  const update = useCallback(
    (next: T) => {
      writePreference(`cockpit.${key}`, next);
      setValue(next);
    },
    [key],
  );
  return [value, update];
}

/** Follows an input with a short delay, so that typing does not query every key. */
export function useDebounced<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/**
 * The text of a search field that is kept in the `q` parameter of the address. Typing writes the
 * address after a short delay; an address restored by the browser history updates the field
 * instead of being overwritten by it.
 */
export function useUrlSearch(
  term: string,
  setParams: (changes: Record<string, string | null>) => void,
): [string, (value: string) => void] {
  const [search, setSearch] = useState(term);
  const [shownTerm, setShownTerm] = useState(term);
  if (term !== shownTerm) {
    setShownTerm(term);
    if (term !== search.trim()) setSearch(term);
  }
  const debounced = useDebounced(search.trim());
  const written = useRef(debounced);
  useEffect(() => {
    // only a change of the typed text is written, never a stale one after the address changed
    if (debounced === written.current) return;
    written.current = debounced;
    if (debounced !== term) setParams({ q: debounced || null, page: null });
  }, [debounced, term, setParams]);
  return [search, setSearch];
}
