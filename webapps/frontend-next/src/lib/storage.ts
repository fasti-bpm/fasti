/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Preferences are kept in the local storage of the browser. Storage may be
 * unavailable (private mode, blocked site data): reading falls back to the
 * default and writing is skipped.
 *
 * Once a user is logged in, the preferences also follow the user across
 * browsers: they are loaded from the server (per user and engine) and every
 * change is sent back. The local storage stays the working copy, so the
 * preferences keep working when the server cannot be reached.
 */
const PREFIX = 'fasti.next.';
/**
 * The user whose preferences this browser holds, outside the synchronized
 * keys. Only the preferences of that user (or of nobody yet) are sent to the
 * server, so a shared browser never hands one user's choices to another.
 */
const OWNER_KEY = 'fasti.next-owner';
const UPLOAD_DELAY = 600;

export type PreferenceDocument = Record<string, unknown>;

export interface PreferenceRemote {
  load: () => Promise<PreferenceDocument>;
  save: (document: PreferenceDocument) => Promise<void>;
}

let remote: PreferenceRemote | null = null;
let remoteUser: string | null = null;
let remoteOwner: string | null = null;
/** counts connections, so an answer for an earlier one is ignored */
let connection = 0;
let uploadTimer: number | undefined;
const replacedListeners = new Set<() => void>();

export function readPreference<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(PREFIX + key);
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

export function writePreference(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    // not stored
  }
  if (remote && isSynced(key, remoteUser)) {
    scheduleUpload();
  }
}

/**
 * Whether a key belongs to the document of the user: the Tasklist keeps one
 * entry per user (`tasklist.<user>`), only the own one is shared.
 */
function isSynced(key: string, userId: string | null): boolean {
  return !key.startsWith('tasklist.') || key === `tasklist.${userId}`;
}

function localKeys(): string[] {
  const keys: string[] = [];
  try {
    for (let i = 0; i < window.localStorage.length; i++) {
      const key = window.localStorage.key(i);
      if (key?.startsWith(PREFIX)) {
        keys.push(key.substring(PREFIX.length));
      }
    }
  } catch {
    // no storage
  }
  return keys;
}

/** The preferences of the user kept in this browser. */
export function localDocument(userId: string | null = remoteUser): PreferenceDocument {
  const document: PreferenceDocument = {};
  for (const key of localKeys()) {
    if (isSynced(key, userId)) {
      const value = readPreference<unknown>(key, undefined);
      if (value !== undefined) {
        document[key] = value;
      }
    }
  }
  return document;
}

/** after a failed upload */
const RETRY_DELAY = 30_000;

function scheduleUpload(): void {
  window.clearTimeout(uploadTimer);
  uploadTimer = window.setTimeout(() => void flushPreferences(), UPLOAD_DELAY);
}

/** Sends pending changes now; failures leave the local copy in place. */
export async function flushPreferences(): Promise<void> {
  window.clearTimeout(uploadTimer);
  uploadTimer = undefined;
  if (!remote) {
    return;
  }
  if (remoteOwner !== null && readOwner() !== remoteOwner) {
    // another tab logged in as someone else: the session (and the shared local
    // storage) now belong to that user, so this tab sends nothing any more
    disconnectPreferences();
    return;
  }
  try {
    await remote.save(localDocument());
  } catch {
    // kept locally; tried again a little later, or with the next change
    window.clearTimeout(uploadTimer);
    uploadTimer = window.setTimeout(() => void flushPreferences(), RETRY_DELAY);
  }
}

/** Called when preferences were replaced by those stored on the server. */
export function onPreferencesReplaced(listener: () => void): () => void {
  replacedListeners.add(listener);
  return () => {
    replacedListeners.delete(listener);
  };
}

/**
 * Connects the preferences of a user to the server. If the server has none
 * yet, the preferences of this browser are sent to it (first use); otherwise
 * the server copy replaces the local one. If the server cannot be reached,
 * the local preferences are used and nothing is sent.
 */
export async function connectPreferences(
  userId: string,
  target: PreferenceRemote,
  scope = '',
): Promise<void> {
  disconnectPreferences();
  const generation = connection;
  // the owner names the engine too: the same name on another engine is
  // another user
  const ownerId = `${scope}/${userId}`;
  const owner = readOwner();
  const sameOwner = owner === null || owner === ownerId;
  let stored: PreferenceDocument | null = null;
  try {
    const loaded = await target.load();
    if (loaded && typeof loaded === 'object' && !Array.isArray(loaded)) {
      stored = loaded;
    }
  } catch {
    // the server cannot be reached: the local copy is used
  }
  if (generation !== connection) {
    // logged out or switched user meanwhile
    return;
  }
  writeOwner(ownerId);
  if (!stored) {
    // without the server copy, a new user of a shared browser starts from the
    // defaults rather than with the previous user's choices
    if (!sameOwner) replaceLocal(userId, {});
    return;
  }
  remote = target;
  remoteUser = userId;
  remoteOwner = ownerId;
  if (Object.keys(stored).length === 0 && sameOwner) {
    // first use: the preferences of this browser become the server copy
    if (Object.keys(localDocument(userId)).length > 0) {
      await flushPreferences();
    }
    return;
  }
  // otherwise the server copy wins; for a new user of a shared browser that
  // is an empty one, so the previous user's choices are dropped
  replaceLocal(userId, stored);
}

function replaceLocal(userId: string, stored: PreferenceDocument): void {
  let changed = false;
  try {
    for (const key of localKeys()) {
      if (isSynced(key, userId) && !(key in stored)) {
        window.localStorage.removeItem(PREFIX + key);
        changed = true;
      }
    }
    for (const [key, value] of Object.entries(stored)) {
      const raw = JSON.stringify(value);
      if (window.localStorage.getItem(PREFIX + key) !== raw) {
        window.localStorage.setItem(PREFIX + key, raw);
        changed = true;
      }
    }
  } catch {
    // no local storage: the server copy cannot be applied
  }
  if (changed) {
    replacedListeners.forEach((listener) => listener());
  }
}

function readOwner(): string | null {
  try {
    return window.localStorage.getItem(OWNER_KEY);
  } catch {
    return null;
  }
}

function writeOwner(userId: string): void {
  try {
    window.localStorage.setItem(OWNER_KEY, userId);
  } catch {
    // no storage
  }
}

/** Stops sending changes, e.g. when the user logs out. */
export function disconnectPreferences(): void {
  window.clearTimeout(uploadTimer);
  uploadTimer = undefined;
  remote = null;
  remoteUser = null;
  remoteOwner = null;
  connection++;
}
