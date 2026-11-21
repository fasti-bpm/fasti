/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useSyncExternalStore } from 'react';
import { config } from '../config';

/**
 * Whether someone is at the screen, so that pages that refresh themselves ask
 * the server less often when nobody looks: a list left open over lunch or
 * over night should not cost the same as one being worked with.
 *
 * - `active`: there was input within the last two minutes
 * - `idle`:   no input for two minutes: the pages refresh half as often
 * - `away`:   no input for ten minutes: they refresh every few minutes
 *
 * The first input after that makes the user `active` again and tells the
 * listeners of `onReturn`, which read at once what has become old. A hidden
 * tab is a separate matter: there the pages do not refresh at all.
 *
 * Input inside a frame (an embedded or external task form) does not reach this
 * page; while the keyboard focus is in such a frame of the shown page, the user
 * counts as present. A wall display that nobody touches turns the pacing off:
 * `"idlePacing": false` in `config.json` for an installation, or
 * `?idlePacing=false` in the address (before the `#`) for one screen.
 */
export type Presence = 'active' | 'idle' | 'away';

export const IDLE_AFTER = 2 * 60_000;
export const AWAY_AFTER = 10 * 60_000;

/** By how much the time between two refreshes is stretched. */
const FACTOR: Record<Presence, number> = { active: 1, idle: 2, away: 10 };

const INPUT_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'focusin'] as const;

let lastInput = Date.now();
let presence: Presence = 'active';
let timer: ReturnType<typeof setTimeout> | undefined;
let attached = false;
const listeners = new Set<() => void>();
const returnListeners = new Set<() => void>();

function presenceAt(now: number): Presence {
  const quiet = now - lastInput;
  return quiet >= AWAY_AFTER ? 'away' : quiet >= IDLE_AFTER ? 'idle' : 'active';
}

function schedule(): void {
  clearTimeout(timer);
  timer = undefined;
  if (presence === 'away' || listeners.size + returnListeners.size === 0) return;
  const next = lastInput + (presence === 'active' ? IDLE_AFTER : AWAY_AFTER);
  timer = setTimeout(update, Math.max(0, next - Date.now()));
}

/** Whether the keyboard focus is in a frame of the shown page, e.g. an embedded form. */
function focusInFrame(): boolean {
  return (
    document.visibilityState === 'visible' &&
    document.activeElement instanceof HTMLIFrameElement &&
    document.hasFocus()
  );
}

function update(): void {
  if (focusInFrame()) lastInput = Date.now();
  const next = presenceAt(Date.now());
  if (next !== presence) {
    const returned = next === 'active';
    presence = next;
    for (const listener of [...listeners]) listener();
    if (returned) for (const listener of [...returnListeners]) listener();
  }
  schedule();
}

function onInput(): void {
  const now = Date.now();
  // pointer moves arrive by the hundred; the state only changes after a pause
  if (presence === 'active' && now - lastInput < 1000) return;
  lastInput = now;
  update();
}

function onVisibility(): void {
  if (document.visibilityState === 'visible') onInput();
}

let blurTimer: ReturnType<typeof setTimeout> | undefined;

/** The page loses the focus, maybe to one of its frames: that is input too. */
function onBlur(): void {
  clearTimeout(blurTimer);
  // the frame holds the focus only once the event is over
  blurTimer = setTimeout(() => {
    blurTimer = undefined;
    if (focusInFrame()) onInput();
  }, 0);
}

function attach(): void {
  if (attached || typeof window === 'undefined') return;
  attached = true;
  for (const name of INPUT_EVENTS) window.addEventListener(name, onInput, { passive: true, capture: true });
  window.addEventListener('blur', onBlur);
  document.addEventListener('visibilitychange', onVisibility);
  lastInput = Date.now();
  presence = 'active';
}

function detach(): void {
  if (!attached || listeners.size + returnListeners.size > 0) return;
  attached = false;
  for (const name of INPUT_EVENTS) window.removeEventListener(name, onInput, { capture: true });
  window.removeEventListener('blur', onBlur);
  document.removeEventListener('visibilitychange', onVisibility);
  clearTimeout(timer);
  timer = undefined;
  clearTimeout(blurTimer);
  blurTimer = undefined;
}

function subscribe(listener: () => void): () => void {
  attach();
  listeners.add(listener);
  schedule();
  return () => {
    listeners.delete(listener);
    detach();
  };
}

/** Calls `listener` when the user is back after having been idle or away. */
export function onReturn(listener: () => void): () => void {
  attach();
  returnListeners.add(listener);
  schedule();
  return () => {
    returnListeners.delete(listener);
    detach();
  };
}

export function getPresence(): Presence {
  return presence;
}

export function usePresence(): Presence {
  return useSyncExternalStore(subscribe, getPresence, () => 'active' as const);
}

/**
 * Whether pages refresh less often while nobody is at the screen: on unless `config.json` or
 * the address of the page turns it off.
 */
export function idlePacing(): boolean {
  if (!config.idlePacing) return false;
  const flag = new URLSearchParams(window.location.search).get('idlePacing');
  return flag === null || !['false', 'off', '0', 'no'].includes(flag.toLowerCase());
}

/** The time between two refreshes for a user who is `presence`; `false` stays off. */
export function paced(interval: number | false, current: Presence): number | false {
  if (interval === false) return false;
  return idlePacing() ? interval * FACTOR[current] : interval;
}

/**
 * The `refetchInterval` of a query that refreshes itself every `interval` ms
 * while someone is at the screen and less often when nobody is.
 */
export function usePacedInterval(interval: number | false): number | false {
  return paced(interval, usePresence());
}
