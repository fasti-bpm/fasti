/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * The REST API exchanges dates as `yyyy-MM-dd'T'HH:mm:ss.SSSZ`, e.g.
 * `2026-03-01T09:30:00.000+0700`. The offset has no colon, which not every
 * browser parses.
 */
const ENGINE_DATE = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)([+-]\d{2}):?(\d{2})$/;

export function parseEngineDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const match = ENGINE_DATE.exec(value);
  const date = new Date(match ? `${match[1]}${match[2]}:${match[3]}` : value);
  return Number.isNaN(date.getTime()) ? null : date;
}

const pad = (value: number, length = 2): string => String(value).padStart(length, '0');

export function toEngineDate(date: Date): string {
  const offset = -date.getTimezoneOffset();
  const sign = offset < 0 ? '-' : '+';
  const absolute = Math.abs(offset);
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` +
    `.${pad(date.getMilliseconds(), 3)}${sign}${pad(Math.floor(absolute / 60))}${pad(absolute % 60)}`
  );
}

/** Value of an `<input type="datetime-local">`. */
export function toDateTimeInput(date: Date | null): string {
  if (!date) {
    return '';
  }
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

export function fromDateTimeInput(value: string): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export type DueState = 'overdue' | 'soon' | 'later';

const DAY = 24 * 60 * 60 * 1000;

export function dueState(due: Date, now: Date = new Date()): DueState {
  const remaining = due.getTime() - now.getTime();
  if (remaining < 0) {
    return 'overdue';
  }
  return remaining < DAY ? 'soon' : 'later';
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * DAY],
  ['month', 30 * DAY],
  ['week', 7 * DAY],
  ['day', DAY],
  ['hour', 60 * 60 * 1000],
  ['minute', 60 * 1000],
];

/** "3 hours ago", "in 2 days" in the given language. */
export function formatRelative(date: Date, locale: string, now: Date = new Date()): string {
  const difference = date.getTime() - now.getTime();
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  for (const [unit, length] of UNITS) {
    if (Math.abs(difference) >= length) {
      return format.format(Math.round(difference / length), unit);
    }
  }
  return format.format(0, 'minute');
}

export function formatDateTime(date: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

/** The same in Coordinated Universal Time, marked as such. */
export function formatUtc(date: Date, locale: string): string {
  return `${new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(date)} UTC`;
}
