/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { DropdownMenu } from 'radix-ui';
import { ArrowDown, ArrowUp, ChevronDown, ChevronLeft, ChevronRight, ChevronsUpDown, Columns3, FileDown } from 'lucide-react';
import type { SortOrder } from '../api/types';
import { useI18n } from '../i18n';
import { downloadCsv } from '../lib/csv';
import { Button, ErrorState, IconButton, Loading, Skeleton, cx } from './index';
import { formatNumber } from './format';
import { useRemembered } from './hooks';
import { MenuContent } from './menu';
import { useToast } from './toast';

// Tables of records: sorting, columns, export to CSV, cards on phones, and paging.

export interface Column<T> {
  id: string;
  header: string;
  /** the column can be sorted by this key */
  sortBy?: string;
  align?: 'end';
  /** hidden on narrow screens */
  secondary?: boolean;
  cell: (row: T) => ReactNode;
}

/** Columns that hold only controls are not exported. */
const CONTROL_COLUMNS = new Set(['actions', 'select', 'check', 'toggle']);

/** The shown text of a table as CSV rows: header first, controls left out. */
export function tableText(table: HTMLTableElement, skip: (index: number) => boolean): { header: string[]; rows: string[][] } {
  const text = (cell: Element) => (cell.textContent ?? '').replace(/\s+/g, ' ').trim();
  const pick = (cells: ArrayLike<Element> | undefined) =>
    Array.from(cells ?? []).filter((_, index) => !skip(index)).map(text);
  const header = pick(table.tHead?.rows[0]?.cells);
  const rows = [...(table.tBodies[0]?.rows ?? [])].map((row) => pick(row.cells));
  return { header, rows };
}

/** A file name from a label: lower case letters, digits and dashes. */
export function csvFileName(label: string): string {
  const slug = label
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
  return `${slug || 'export'}.csv`;
}

/** At most this many rows go into an export of all matching rows. */
export const EXPORT_LIMIT = 5000;
const EXPORT_PAGE = 500;

export interface ExportPaging {
  firstResult: number;
  maxResults: number;
}

/**
 * Fetches every matching row page by page, up to `limit` rows. One row more
 * than the limit is asked for, so a cut is known for sure.
 */
export async function fetchAllRows<T>(
  fetchPage: (paging: ExportPaging) => Promise<T[]>,
  limit = EXPORT_LIMIT,
  pageSize = EXPORT_PAGE,
): Promise<{ rows: T[]; truncated: boolean }> {
  const rows: T[] = [];
  while (rows.length <= limit) {
    const maxResults = Math.min(pageSize, limit + 1 - rows.length);
    const page = await fetchPage({ firstResult: rows.length, maxResults });
    rows.push(...page);
    if (page.length < maxResults) {
      return { rows, truncated: false };
    }
  }
  return { rows: rows.slice(0, limit), truncated: true };
}

export interface TableSorting {
  sortBy: string;
  sortOrder: SortOrder;
}

export function DataTable<T>({
  label,
  columns,
  rows,
  rowKey,
  error,
  onRetry,
  empty,
  sorting,
  onSort,
  selectedKey,
  exportable = true,
  fetchAll,
  columnsKey,
  updating = false,
}: {
  label: string;
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  error?: unknown;
  onRetry?: () => void;
  /** shown instead of the table if there is no row */
  empty: ReactNode;
  sorting?: TableSorting;
  onSort?: (sorting: TableSorting) => void;
  selectedKey?: string | null;
  /** offers the shown rows as a CSV file */
  exportable?: boolean;
  /** fetches a page of all matching rows, for an export beyond the shown page */
  fetchAll?: (paging: ExportPaging) => Promise<T[]>;
  /** lets the user hide columns; the choice is remembered under this key */
  columnsKey?: string;
  /** the rows are of an earlier filter or page while the new ones load */
  updating?: boolean;
}) {
  const { t, locale } = useI18n();
  const toast = useToast();
  const table = useRef<HTMLTableElement>(null);
  const allTable = useRef<HTMLTableElement>(null);
  const [hidden, setHidden] = useRemembered<string[]>(`columns.${columnsKey ?? 'none'}`, []);
  const [exporting, setExporting] = useState(false);
  const [allRows, setAllRows] = useState<{ rows: T[]; truncated: boolean } | null>(null);
  const hiddenIds = columnsKey && Array.isArray(hidden) ? hidden : [];
  const shownColumns = columns.filter((column) => !hiddenIds.includes(column.id));
  const skipColumn = (index: number) => {
    const column = shownColumns[index];
    return !column || !column.header || CONTROL_COLUMNS.has(column.id);
  };

  // the fetched rows are rendered once, out of sight, with the cells of the
  // table, so the file holds the same text as the screen
  useEffect(() => {
    if (!allRows || !allTable.current) return;
    const { header, rows: lines } = tableText(allTable.current, skipColumn);
    downloadCsv(csvFileName(label), header, lines);
    const count = formatNumber(lines.length, locale);
    toast.show(
      allRows.truncated
        ? { tone: 'error', title: t('csv.exportCut', { count }), detail: t('csv.exportCutHint') }
        : { tone: 'success', title: t('csv.exportDone', { count }) },
    );
    setAllRows(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- runs once per fetched set
  }, [allRows]);

  if (error && !rows) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  if (!rows) {
    return (
      <Loading label={t('state.loading')}>
        <Skeleton lines={5} className="skeleton--rows" />
      </Loading>
    );
  }
  if (rows.length === 0) {
    return (
      <div aria-busy={updating || undefined}>
        {!!error && (
          <div className="table-stale" role="alert">
            <span>{t('table.staleError')}</span>
            {onRetry && (
              <Button size="sm" variant="ghost" onClick={onRetry}>
                {t('action.retry')}
              </Button>
            )}
          </div>
        )}
        {updating ? <p className="muted table-stale">{t('table.updating')}</p> : empty}
      </div>
    );
  }
  const exportRows = () => {
    if (!table.current) return;
    const { header, rows: lines } = tableText(table.current, skipColumn);
    downloadCsv(csvFileName(label), header, lines);
  };
  const exportAll = async () => {
    if (!fetchAll) return;
    setExporting(true);
    try {
      setAllRows(await fetchAllRows(fetchAll));
    } catch (error) {
      toast.error(error, t('csv.exportFailed'));
    } finally {
      setExporting(false);
    }
  };
  const sortable = columns.filter((column) => column.sortBy && column.header);
  const toggleColumn = (id: string, shown: boolean) => {
    const next = shown ? hiddenIds.filter((other) => other !== id) : [...hiddenIds, id];
    if (next.length < columns.length) setHidden(next);
  };
  return (
    <div className={cx('table-wrap', updating && 'table-wrap--updating')} aria-busy={updating || undefined}>
      {updating && (
        <p className="sr-only" role="status">
          {t('table.updating')}
        </p>
      )}
      {!!error && (
        <div className="table-stale" role="alert">
          <span>{t('table.staleError')}</span>
          {onRetry && (
            <Button size="sm" variant="ghost" onClick={onRetry}>
              {t('action.retry')}
            </Button>
          )}
        </div>
      )}
      {(exportable || columnsKey) && (
        <div className="table-export">
          {columnsKey && (
            <details className="table-columns">
              <summary className="btn btn--ghost btn--sm">
                <Columns3 aria-hidden size={14} />
                {t('table.columns')}
              </summary>
              <fieldset className="table-columns__menu">
                <legend className="table-columns__legend">{t('table.columnsLegend')}</legend>
                {columns
                  .filter((column) => column.header)
                  .map((column) => {
                    const shown = !hiddenIds.includes(column.id);
                    return (
                      <label key={column.id} className="table-columns__item">
                        <input
                          type="checkbox"
                          checked={shown}
                          disabled={shown && shownColumns.length === 1}
                          onChange={(event) => toggleColumn(column.id, event.target.checked)}
                        />
                        {column.header}
                      </label>
                    );
                  })}
              </fieldset>
            </details>
          )}
          {exportable && !fetchAll && (
            <Button size="sm" variant="ghost" icon={FileDown} onClick={exportRows}>
              {t('csv.exportShown')}
            </Button>
          )}
          {exportable && fetchAll && (
            <DropdownMenu.Root>
              <DropdownMenu.Trigger asChild>
                <Button size="sm" variant="ghost" icon={FileDown} busy={exporting}>
                  {exporting ? t('csv.exporting') : t('csv.export')}
                  <ChevronDown aria-hidden size={14} />
                </Button>
              </DropdownMenu.Trigger>
              <DropdownMenu.Portal>
                <MenuContent>
                  <DropdownMenu.Item className="menu__item" onSelect={exportRows}>
                    {t('csv.exportShown')}
                  </DropdownMenu.Item>
                  <DropdownMenu.Item className="menu__item" onSelect={() => void exportAll()}>
                    {t('csv.exportAll', { limit: formatNumber(EXPORT_LIMIT, locale) })}
                  </DropdownMenu.Item>
                </MenuContent>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          )}
        </div>
      )}
      {allRows && (
        <table ref={allTable} hidden aria-hidden>
          <thead>
            <tr>
              {shownColumns.map((column) => (
                <th key={column.id}>{column.header}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {allRows.rows.map((row, index) => (
              <tr key={index}>
                {shownColumns.map((column) => (
                  <td key={column.id}>{column.cell(row)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {onSort && sortable.length > 0 && (
        // a phone shows the rows as cards: one choice of sorting instead of a row of header chips
        <div className="table-phone-sort">
          <label className="table-phone-sort__label">
            <span>{t('sort.title')}</span>
            <select
              className="input"
              value={sorting?.sortBy ?? ''}
              onChange={(event) => onSort({ sortBy: event.target.value, sortOrder: sorting?.sortOrder ?? 'asc' })}
            >
              {!sorting?.sortBy && <option value="">—</option>}
              {sortable.map((column) => (
                <option key={column.id} value={column.sortBy}>
                  {column.header}
                </option>
              ))}
            </select>
          </label>
          <Button
            size="sm"
            variant="ghost"
            icon={sorting?.sortOrder === 'desc' ? ArrowDown : ArrowUp}
            disabled={!sorting?.sortBy}
            aria-label={`${t('table.sortOrder')}: ${sorting?.sortOrder === 'desc' ? t('sort.descending') : t('sort.ascending')}`}
            onClick={() =>
              sorting && onSort({ sortBy: sorting.sortBy, sortOrder: sorting.sortOrder === 'asc' ? 'desc' : 'asc' })
            }
          >
            {sorting?.sortOrder === 'desc' ? t('sort.descending') : t('sort.ascending')}
          </Button>
        </div>
      )}
      <table ref={table} className="table table--cards" aria-label={label}>
        <thead>
          <tr>
            {shownColumns.map((column) => {
              const sorted = sorting && column.sortBy === sorting.sortBy;
              const className = cx(
                column.align === 'end' && 'is-end',
                column.secondary && 'is-secondary',
              );
              if (!column.sortBy || !onSort) {
                return (
                  <th key={column.id} scope="col" className={className}>
                    {column.header}
                  </th>
                );
              }
              const sortBy = column.sortBy;
              return (
                <th
                  key={column.id}
                  scope="col"
                  className={className}
                  // keeps the name of the column when a phone hides the sort button
                  aria-label={column.header}
                  aria-sort={sorted ? (sorting.sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}
                >
                  <button
                    type="button"
                    className={cx('table__sort', sorted && 'is-sorted')}
                    onClick={() =>
                      onSort({
                        sortBy,
                        sortOrder: sorted && sorting.sortOrder === 'asc' ? 'desc' : 'asc',
                      })
                    }
                  >
                    {column.header}
                    {sorted ? (
                      sorting.sortOrder === 'asc' ? (
                        <ArrowUp aria-hidden size={13} />
                      ) : (
                        <ArrowDown aria-hidden size={13} />
                      )
                    ) : (
                      <ChevronsUpDown aria-hidden size={13} className="table__sort-idle" />
                    )}
                  </button>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const key = rowKey(row);
            return (
              <tr key={key} className={cx(key === selectedKey && 'is-selected')}>
                {shownColumns.map((column) => (
                  <td
                    key={column.id}
                    // on a phone each row is a card and each value is labelled with its column
                    data-label={column.header || undefined}
                    className={cx(
                      column.align === 'end' && 'is-end',
                      column.secondary && 'is-secondary',
                    )}
                  >
                    {column.cell(row)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export const PAGE_SIZE = 25;

/** Moves to the last page when the shown one is past the end, e.g. after its last row was removed. */
export function useClampedPage(page: number, count: number | undefined, onPage: (page: number) => void, pageSize = PAGE_SIZE) {
  useEffect(() => {
    if (count === undefined) return;
    const last = Math.max(0, Math.ceil(count / pageSize) - 1);
    if (page > last) onPage(last);
  }, [page, count, onPage, pageSize]);
}

export function Pager({
  page,
  count,
  pageSize = PAGE_SIZE,
  onPage,
}: {
  page: number;
  count: number;
  pageSize?: number;
  onPage: (page: number) => void;
}) {
  const { t } = useI18n();
  if (count <= pageSize && page === 0) {
    return null;
  }
  return (
    <nav className="table-pager" aria-label={t('pager.title')}>
      <span className="table-pager__text">
        {t('pager.range', {
          from: count === 0 ? 0 : page * pageSize + 1,
          to: Math.min((page + 1) * pageSize, count),
          total: count,
        })}
      </span>
      <IconButton
        icon={ChevronLeft}
        label={t('pager.previous')}
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
      />
      <IconButton
        icon={ChevronRight}
        label={t('pager.next')}
        disabled={(page + 1) * pageSize >= count}
        onClick={() => onPage(page + 1)}
      />
    </nav>
  );
}
