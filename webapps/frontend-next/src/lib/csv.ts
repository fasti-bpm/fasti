/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
export type CsvValue = string | number | boolean | null | undefined;

/**
 * Starts of text a spreadsheet may run as a formula or a DDE call: also after
 * leading white space, and in the full-width forms some spreadsheets accept.
 */
const FORMULA_START = /^[\t\r\n]|^[\s\u3000]*[=+\-@;\uFF1D\uFF0B\uFF0D\uFF20]/;

/**
 * One field of a CSV file (RFC 4180). Text a spreadsheet would run as a
 * formula is prefixed with an apostrophe so an exported value cannot execute.
 */
export function csvField(value: CsvValue): string {
  if (value === null || value === undefined) {
    return '';
  }
  let text = String(value);
  if (typeof value === 'string' && FORMULA_START.test(text)) {
    text = `'${text}`;
  }
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(header: string[], rows: CsvValue[][]): string {
  return [header, ...rows].map((row) => row.map(csvField).join(',')).join('\r\n');
}

/** Hands a CSV file to the browser; the byte order mark lets spreadsheets read UTF-8. */
export function downloadCsv(fileName: string, header: string[], rows: CsvValue[][]): void {
  downloadBlob(new Blob(['﻿', toCsv(header, rows)], { type: 'text/csv;charset=utf-8' }), fileName);
}

/** Hands a file to the browser. */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
