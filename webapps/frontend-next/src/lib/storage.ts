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
import { isApiError } from '../api/http';

const PREFIX = 'fasti.next.';
/**
 * The user whose preferences this browser holds, outside the synchronized
 * keys. Only the preferences of that user (or of nobody yet) are sent to the
 * server, so a shared browser never hands one user's choices to another.
 */
const OWNER_KEY = 'fasti.next-owner';
/** changes made within this time are sent together */
const UPLOAD_DELAY = 2500;
/** after a failed upload */
const RETRY_DELAY = 30_000;

/** What the server keeps at most (see `UserPreferencesResource`); a larger document is refused. */
export const MAX_DOCUMENT_BYTES = 32 * 1024;
export const MAX_DOCUMENT_KEYS = 64;
/** Saved views (and saved searches) per list, so that the document stays below the limits. */
export const MAX_SAVED_VIEWS = 20;

/**
 * Keys of earlier versions that lay in the namespace of the per-user Tasklist
 * entries (`tasklist.<user>`): they were neither synchronized nor cleared for
 * the next user of the browser. Old name → new name.
 */
const RENAMED: Readonly<Record<string, string>> = {
  'tasklist.processFields': 'tasks.processFields',
  'tasklist.notify': 'tasks.notify',
};

export type PreferenceDocument = Record<string, unknown>;

export interface PreferenceRemote {
  load: () => Promise<PreferenceDocument>;
  /** `keepalive` lets the request outlive the page, for the last changes when it is closed */
  save: (document: PreferenceDocument, options?: { keepalive?: boolean }) => Promise<void>;
}

let remote: PreferenceRemote | null = null;
let remoteUser: string | null = null;
let remoteOwner: string | null = null;
/** counts connections, so an answer for an earlier one is ignored */
let connection = 0;
let uploadTimer: number | undefined;
/** changes that were not sent yet */
let dirty = false;
/** the upload under way: there is never more than one */
let inFlight: Promise<void> | null = null;
/** a change arrived while an upload was under way: the latest state is sent after it */
let again = false;
let rejectionReported = false;
let exitListening = false;
const replacedListeners = new Set<() => void>();
const rejectedListeners = new Set<() => void>();

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
    dirty = true;
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

/** Whether the server accepts the document; a larger one is refused as a whole. */
function fits(document: PreferenceDocument): boolean {
  return (
    Object.keys(document).length <= MAX_DOCUMENT_KEYS &&
    new TextEncoder().encode(JSON.stringify(document)).length <= MAX_DOCUMENT_BYTES
  );
}

function scheduleUpload(): void {
  window.clearTimeout(uploadTimer);
  uploadTimer = window.setTimeout(() => void flushPreferences(), UPLOAD_DELAY);
}

/** Tells the listeners, once per login, that the server refuses the preferences. */
function reportRejected(): void {
  if (rejectionReported) return;
  rejectionReported = true;
  rejectedListeners.forEach((listener) => listener());
}

/** Another tab logged in as someone else: the session (and the shared local storage) belong to that user. */
function ownerChanged(): boolean {
  return remoteOwner !== null && readOwner() !== remoteOwner;
}

/** Sends the local document once; answers whether it was stored. */
async function uploadOnce(): Promise<boolean> {
  const target = remote;
  if (!target) {
    return false;
  }
  if (ownerChanged()) {
    // this tab sends nothing any more
    disconnectPreferences();
    return false;
  }
  const generation = connection;
  const document = localDocument();
  dirty = false;
  if (!fits(document)) {
    reportRejected();
    return false;
  }
  try {
    await target.save(document);
    return true;
  } catch (error) {
    if (generation !== connection) {
      return false;
    }
    if (isApiError(error) && error.status >= 400 && error.status < 500) {
      // refused as it is (too large, malformed, no longer logged in): sending it again changes nothing
      if (error.status === 400 || error.status === 413) reportRejected();
      return false;
    }
    // kept locally; tried again a little later, or with the next change
    dirty = true;
    window.clearTimeout(uploadTimer);
    uploadTimer = window.setTimeout(() => void flushPreferences(), RETRY_DELAY);
    return false;
  }
}

/**
 * Sends pending changes now; failures leave the local copy in place. Uploads
 * follow one another: a change made while one is under way is sent after it,
 * as the then current state.
 */
export function flushPreferences(): Promise<void> {
  window.clearTimeout(uploadTimer);
  uploadTimer = undefined;
  if (!remote) {
    return Promise.resolve();
  }
  if (inFlight) {
    if (dirty) again = true;
    return inFlight;
  }
  if (!dirty) {
    return Promise.resolve();
  }
  const run = async () => {
    try {
      let stored: boolean;
      do {
        again = false;
        stored = await uploadOnce();
      } while (stored && again && dirty && remote);
    } finally {
      inFlight = null;
    }
  };
  inFlight = run();
  return inFlight;
}

/**
 * The page is closed or left: the changes that wait for the delay go out with
 * a request that outlives the page. An upload under way cannot be waited for
 * any more; this one carries the latest state.
 */
function flushOnExit(): void {
  if (!remote || !dirty || ownerChanged()) {
    return;
  }
  window.clearTimeout(uploadTimer);
  uploadTimer = undefined;
  const document = localDocument();
  if (!fits(document)) {
    return;
  }
  dirty = false;
  remote.save(document, { keepalive: true }).catch(() => undefined);
}

/** Called when preferences were replaced by those stored on the server. */
export function onPreferencesReplaced(listener: () => void): () => void {
  replacedListeners.add(listener);
  return () => {
    replacedListeners.delete(listener);
  };
}

/** Called (once per login) when the server refuses the preferences, e.g. because they grew too large. */
export function onPreferencesRejected(listener: () => void): () => void {
  rejectedListeners.add(listener);
  return () => {
    rejectedListeners.delete(listener);
  };
}

/**
 * Called when another tab of this browser connects the preferences of a user,
 * i.e. after a login there. Whether that is another user is for the session
 * to find out: the same browser may also be logged in to another engine.
 */
export function onOwnerChanged(listener: () => void): () => void {
  const handler = (event: StorageEvent) => {
    // `null`: the whole storage was cleared
    if (event.key === OWNER_KEY || event.key === null) listener();
  };
  window.addEventListener('storage', handler);
  return () => window.removeEventListener('storage', handler);
}

/**
 * Moves the keys of earlier versions to their new names. They belonged to
 * whoever used the browser: kept for its owner (or if it had none), dropped
 * for a new user. Answers the values kept, by their new names.
 */
function moveRenamedKeys(userId: string, sameOwner: boolean): PreferenceDocument {
  const moved: PreferenceDocument = {};
  try {
    for (const [legacy, next] of Object.entries(RENAMED)) {
      if (legacy === `tasklist.${userId}`) continue; // a user of that very name
      const raw = window.localStorage.getItem(PREFIX + legacy);
      if (raw === null) continue;
      window.localStorage.removeItem(PREFIX + legacy);
      if (!sameOwner) continue;
      if (window.localStorage.getItem(PREFIX + next) === null) {
        window.localStorage.setItem(PREFIX + next, raw);
      }
      try {
        moved[next] = JSON.parse(window.localStorage.getItem(PREFIX + next) ?? raw);
      } catch {
        // not readable: left out
      }
    }
  } catch {
    // no storage
  }
  return moved;
}

/**
 * Connects the preferences of a user to the server. If the server has none
 * yet, the preferences of this browser are sent to it (first use); otherwise
 * the server copy replaces the local one. If the server cannot be reached,
 * the local preferences are used and nothing is sent. Nothing is sent before
 * the server copy was applied or is known to be absent.
 */
export async function connectPreferences(
  userId: string,
  target: PreferenceRemote,
  scope = '',
): Promise<void> {
  disconnectPreferences();
  const generation = connection;
  if (!exitListening) {
    exitListening = true;
    window.addEventListener('pagehide', flushOnExit);
  }
  // the owner names the engine too: the same name on another engine is
  // another user
  const ownerId = `${scope}/${userId}`;
  const owner = readOwner();
  const sameOwner = owner === null || owner === ownerId;
  const moved = moveRenamedKeys(userId, sameOwner);
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
  rejectionReported = false;
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
      dirty = true;
      await flushPreferences();
    }
    return;
  }
  // otherwise the server copy wins; for a new user of a shared browser that
  // is an empty one, so the previous user's choices are dropped. Values moved
  // from their old names were never on the server: they are added to it.
  const kept = Object.fromEntries(Object.entries(moved).filter(([key]) => !(key in stored)));
  replaceLocal(userId, { ...kept, ...stored });
  if (Object.keys(kept).length > 0) {
    dirty = true;
    scheduleUpload();
  }
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
  dirty = false;
  again = false;
  connection++;
}
