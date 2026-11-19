/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readPreference, writePreference } from './storage';

/** Something the user opened from the search, offered again when the search is empty. */
export interface RecentItem {
  kind: string;
  id: string;
  title: string;
  path: string;
}

const KEY = 'recent';
const LIMIT = 8;

export function recentItems(): RecentItem[] {
  const value = readPreference<unknown>(KEY, []);
  return Array.isArray(value)
    ? value.filter(
        (item): item is RecentItem =>
          !!item && typeof item === 'object' && typeof item.path === 'string' && typeof item.title === 'string',
      )
    : [];
}

/** Puts an item first; the same item opened again moves up instead of appearing twice. */
export function rememberRecent(item: RecentItem): void {
  const others = recentItems().filter((other) => other.path !== item.path);
  writePreference(KEY, [item, ...others].slice(0, LIMIT));
}

export function clearRecent(): void {
  writePreference(KEY, []);
}
