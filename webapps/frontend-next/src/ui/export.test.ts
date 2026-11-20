/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { csvFileName, fetchAllRows, tableText } from './table';

describe('export of shown rows', () => {
  it('takes the shown text and leaves control columns out', () => {
    const table = document.createElement('table');
    const head = table.createTHead().insertRow();
    for (const text of ['Name', 'Actions', 'Count']) head.insertCell().textContent = text;
    const row = table.createTBody().insertRow();
    row.insertCell().textContent = ' Order\n  process ';
    row.insertCell().append(Object.assign(document.createElement('button'), { textContent: 'Retry' }));
    row.insertCell().textContent = '3';
    expect(tableText(table, (index) => index === 1)).toEqual({
      header: ['Name', 'Count'],
      rows: [['Order process', '3']],
    });
  });

  it('names the file after the table', () => {
    expect(csvFileName('Process definitions')).toBe('process-definitions.csv');
    expect(csvFileName('Định nghĩa tiến trình')).toBe('dinh-nghia-tien-trinh.csv');
    expect(csvFileName('***')).toBe('export.csv');
  });
});

describe('export of all matching rows', () => {
  const source = (total: number) => {
    const calls: { firstResult: number; maxResults: number }[] = [];
    const fetchPage = async (paging: { firstResult: number; maxResults: number }) => {
      calls.push(paging);
      const end = Math.min(total, paging.firstResult + paging.maxResults);
      return Array.from({ length: Math.max(0, end - paging.firstResult) }, (_, i) => paging.firstResult + i);
    };
    return { calls, fetchPage };
  };

  it('fetches page by page until the rows run out', async () => {
    const { calls, fetchPage } = source(1200);
    const result = await fetchAllRows(fetchPage, 5000, 500);
    expect(result.rows).toHaveLength(1200);
    expect(result.truncated).toBe(false);
    expect(calls.map((call) => call.firstResult)).toEqual([0, 500, 1000]);
  });

  it('stops at the limit and reports the cut', async () => {
    const { calls, fetchPage } = source(100_000);
    const result = await fetchAllRows(fetchPage, 1000, 500);
    expect(result.rows).toHaveLength(1000);
    expect(result.rows[999]).toBe(999);
    expect(result.truncated).toBe(true);
    expect(calls.at(-1)).toEqual({ firstResult: 1000, maxResults: 1 });
  });

  it('does not report a cut when exactly the limit matches', async () => {
    const { fetchPage } = source(1000);
    const result = await fetchAllRows(fetchPage, 1000, 500);
    expect(result).toMatchObject({ truncated: false });
    expect(result.rows).toHaveLength(1000);
  });
});
