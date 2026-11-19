/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readPreference, writePreference } from './storage';

/**
 * The tenant the lists are narrowed to, chosen by the user where
 * multi-tenancy is in use; `null` shows the data of every tenant the user
 * may see. The engine still checks the memberships.
 */
export function tenantScope(): string | null {
  const value = readPreference<unknown>('tenant', null);
  return typeof value === 'string' && value ? value : null;
}

export function setTenantScope(tenantId: string | null): void {
  writePreference('tenant', tenantId);
}

/** The query parameter for the tenant scope, if there is one. */
export function tenantFilter(): { tenantIdIn?: string } {
  const tenant = tenantScope();
  return tenant ? { tenantIdIn: tenant } : {};
}

/** The same in a JSON body, where lists are arrays. */
export function tenantFilterBody(): { tenantIdIn?: string[] } {
  const tenant = tenantScope();
  return tenant ? { tenantIdIn: [tenant] } : {};
}

export type Density = 'comfortable' | 'compact';
export type StartPage = 'home' | 'tasklist' | 'processes' | 'cockpit' | 'admin';

export const START_PAGES: readonly StartPage[] = ['home', 'tasklist', 'processes', 'cockpit', 'admin'];

export function density(): Density {
  return readPreference<string>('density', 'comfortable') === 'compact' ? 'compact' : 'comfortable';
}

export function applyDensity(value: Density = density()): void {
  if (value === 'compact') document.documentElement.dataset.density = 'compact';
  else delete document.documentElement.dataset.density;
}

export function setDensity(value: Density): void {
  writePreference('density', value);
  applyDensity(value);
}

export function startPage(): StartPage {
  const value = readPreference<string>('startPage', 'home');
  return (START_PAGES as readonly string[]).includes(value) ? (value as StartPage) : 'home';
}

export function setStartPage(value: StartPage): void {
  writePreference('startPage', value);
}

/** The path of a start page, or `null` if the user may not use its application. */
export function startPath(page: StartPage, apps: readonly string[]): string | null {
  switch (page) {
    case 'home':
      return '/';
    case 'tasklist':
    case 'processes':
      return apps.includes('tasklist') ? `/${page}` : null;
    case 'cockpit':
    case 'admin':
      return apps.includes(page) ? `/${page}` : null;
  }
}

/** What the Tasklist shows after a task was completed: the next task of the list, or the list. */
export type AfterComplete = 'next' | 'list';

export function afterComplete(): AfterComplete {
  return readPreference<string>('afterComplete', 'next') === 'list' ? 'list' : 'next';
}

export function setAfterComplete(value: AfterComplete): void {
  writePreference('afterComplete', value);
}

/** How times are shown in the tables: in the local time zone, relative to now, or in UTC. */
export type TimeDisplay = 'local' | 'relative' | 'utc';

export const TIME_DISPLAYS: readonly TimeDisplay[] = ['local', 'relative', 'utc'];

export function timeDisplay(): TimeDisplay {
  const value = readPreference<string>('timeDisplay', 'local');
  return (TIME_DISPLAYS as readonly string[]).includes(value) ? (value as TimeDisplay) : 'local';
}

export function setTimeDisplay(value: TimeDisplay): void {
  writePreference('timeDisplay', value);
}
