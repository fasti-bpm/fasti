/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useIsFetching, useQueryClient } from '@tanstack/react-query';
import { DropdownMenu } from 'radix-ui';
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronsUpDown,
  CircleCheck,
  Columns3,
  Copy,
  FileDown,
  Pause,
  Play,
  RefreshCw,
  Search,
} from 'lucide-react';
import type { IncidentCount } from '../api/cockpit';
import type { SortOrder } from '../api/types';
import { useI18n } from '../i18n';
import { downloadCsv } from '../lib/csv';
import { formatUtc, parseEngineDate } from '../lib/dates';
import { readPreference, writePreference } from '../lib/storage';
import { TIME_DISPLAYS, tenantScope, timeDisplay } from '../lib/userPrefs';
import { Button, Dialog, ErrorState, IconButton, Loading, Skeleton, Tooltip, cx } from '../ui';
import { useToast } from '../ui/toast';
import { MenuContent } from '../ui/menu';

// --- automatic refresh ------------------------------------------------------

/** Every query of the monitoring pages starts with this key. */
export const COCKPIT = 'cockpit';

const REFRESH_INTERVAL = 30_000;
const FOLLOW_UP = 2_000;

interface Refresh {
  paused: boolean;
  setPaused: (paused: boolean) => void;
}

const RefreshContext = createContext<Refresh>({ paused: false, setPaused: () => undefined });

export function RefreshProvider({ children }: { children: ReactNode }) {
  const [paused, setPausedState] = useState(() => readPreference('cockpit.refreshPaused', false));
  const setPaused = useCallback((next: boolean) => {
    writePreference('cockpit.refreshPaused', next);
    setPausedState(next);
  }, []);
  const value = useMemo(() => ({ paused, setPaused }), [paused, setPaused]);
  return <RefreshContext.Provider value={value}>{children}</RefreshContext.Provider>;
}

/** The `refetchInterval` of a query that follows the automatic refresh. */
export function useRefreshInterval(): number | false {
  return useContext(RefreshContext).paused ? false : REFRESH_INTERVAL;
}

/** Shows whether the pages refresh themselves and lets the user pause that. */
/**
 * When the oldest data on screen was read, if a refresh of it failed: the page
 * keeps showing what it has, and says how old it is. `null` while all is fresh.
 */
export function useStaleSince(): number | null {
  const queryClient = useQueryClient();
  const read = useCallback(() => {
    let oldest: number | null = null;
    for (const query of queryClient.getQueryCache().findAll({ queryKey: [COCKPIT] })) {
      const { status, data, dataUpdatedAt } = query.state;
      if (status === 'error' && data !== undefined && query.isActive()) {
        oldest = oldest === null ? dataUpdatedAt : Math.min(oldest, dataUpdatedAt);
      }
    }
    return oldest;
  }, [queryClient]);
  return useSyncExternalStore(
    useCallback((notify) => queryClient.getQueryCache().subscribe(notify), [queryClient]),
    read,
    () => null,
  );
}

export function RefreshControl() {
  const { t, dateTime } = useI18n();
  const { paused, setPaused } = useContext(RefreshContext);
  const queryClient = useQueryClient();
  const fetching = useIsFetching({ queryKey: [COCKPIT] }) > 0;
  const staleSince = useStaleSince();
  return (
    <div className="refresh" role="group" aria-label={t('cockpit.refresh.title')}>
      <span className="refresh__stale" role="status">
        {staleSince !== null && (
          <>
            <AlertTriangle aria-hidden size={14} />
            {t('cockpit.refresh.stale', { time: dateTime(new Date(staleSince)) })}
          </>
        )}
      </span>
      <Tooltip label={paused ? t('cockpit.refresh.resume') : t('cockpit.refresh.pause')}>
        <button
          type="button"
          className={cx('refresh__toggle', paused && 'is-paused')}
          aria-pressed={!paused}
          onClick={() => setPaused(!paused)}
        >
          <span className={cx('refresh__dot', fetching && 'is-fetching')} aria-hidden />
          <span className="refresh__text">
            {paused
              ? t('cockpit.refresh.paused')
              : t('cockpit.refresh.on', { seconds: REFRESH_INTERVAL / 1000 })}
          </span>
          {paused ? <Play aria-hidden size={14} /> : <Pause aria-hidden size={14} />}
        </button>
      </Tooltip>
      <IconButton
        icon={RefreshCw}
        label={t('action.refresh')}
        className={cx(fetching && 'is-spinning')}
        onClick={() => void queryClient.invalidateQueries({ queryKey: [COCKPIT] })}
      />
    </div>
  );
}

// --- state kept in the address ----------------------------------------------

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

/** A choice the browser remembers, e.g. the sorting of a table. */
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

// --- page frame -------------------------------------------------------------

export function PageHeader({
  title,
  subtitle,
  back,
  badges,
  actions,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  back?: { to: string; label: string };
  badges?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      {back && (
        <Link to={back.to} className="icon-btn page-header__back" aria-label={back.label}>
          <ChevronLeft aria-hidden size={18} />
        </Link>
      )}
      <div className="page-header__titles">
        <div className="page-header__line">
          <h1 className="page-header__title">{title}</h1>
          {badges}
        </div>
        {subtitle && <div className="page-header__subtitle">{subtitle}</div>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}

export function Card({
  title,
  action,
  children,
  flush,
  className,
  label,
  id,
}: {
  id?: string;
  title?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  /** content reaches the border, e.g. a table */
  flush?: boolean;
  className?: string;
  /** names the card as a region, for a card that holds controls of its own */
  label?: string;
}) {
  return (
    <section id={id} className={cx('card', flush && 'card--flush', className)} aria-label={label}>
      {(title || action) && (
        <header className="card__header">
          <h2 className="card__title">{title}</h2>
          {action}
        </header>
      )}
      <div className="card__body">{children}</div>
    </section>
  );
}

// --- small pieces -----------------------------------------------------------

export function totalIncidents(incidents: IncidentCount[] | null | undefined): number {
  return (incidents ?? []).reduce((sum, entry) => sum + entry.incidentCount, 0);
}

/** State is never shown by colour alone: every badge has an icon and a text. */
export function IncidentBadge({ count }: { count: number }) {
  const { t } = useI18n();
  if (count <= 0) {
    return null;
  }
  return (
    <span className="badge badge--danger">
      <AlertTriangle aria-hidden size={12} />
      {t('cockpit.incidents.count', { count })}
    </span>
  );
}

export function StateBadge({ suspended }: { suspended: boolean }) {
  const { t } = useI18n();
  return suspended ? (
    <span className="badge badge--warning">
      <Pause aria-hidden size={12} />
      {t('cockpit.state.suspended')}
    </span>
  ) : (
    <span className="badge badge--success">
      <CircleCheck aria-hidden size={12} />
      {t('cockpit.state.active')}
    </span>
  );
}

export function Time({ value }: { value: string | null | undefined }) {
  const { relative, dateTime, locale } = useI18n();
  const date = parseEngineDate(value);
  if (!date) {
    return <span className="muted">—</span>;
  }
  // the chosen form is shown, the other two are in the tooltip
  const forms = { local: dateTime(date), relative: relative(date), utc: formatUtc(date, locale) };
  const shown = timeDisplay();
  return (
    <time
      dateTime={date.toISOString()}
      title={TIME_DISPLAYS.filter((form) => form !== shown)
        .map((form) => forms[form])
        .join(' · ')}
    >
      {forms[shown]}
    </time>
  );
}

/** A technical id: monospaced, shortened in the middle, complete in the tooltip. */
export function Id({ value, copy }: { value: string; copy?: boolean }) {
  const { t } = useI18n();
  const toast = useToast();
  const short = value.length > 20 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value;
  return (
    <span className="id" title={value}>
      {short}
      {copy && (
        <button
          type="button"
          className="id__copy"
          aria-label={t('cockpit.copyId')}
          onClick={() => {
            void navigator.clipboard
              ?.writeText(value)
              .then(() => toast.show({ title: t('cockpit.copied') }))
              .catch(() => toast.show({ tone: 'error', title: t('cockpit.copyFailed') }));
          }}
        >
          <Copy aria-hidden size={13} />
        </button>
      )}
    </span>
  );
}

export function formatNumber(value: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(value);
}

// --- tables -----------------------------------------------------------------

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

// --- confirmation -----------------------------------------------------------

/**
 * Asks before something is changed that cannot be taken back. The title names
 * the action, the text names the object and what happens to it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  text,
  confirmLabel,
  danger,
  busy,
  disabled,
  onConfirm,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  text: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onConfirm: () => void;
  /** options of the action */
  children?: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          onConfirm();
        }}
      >
        <p className="confirm__text">{text}</p>
        {children && <div className="confirm__options">{children}</div>}
        <div className="confirm__actions">
          <Button onClick={() => onOpenChange(false)}>{t('action.cancel')}</Button>
          <Button
            type="submit"
            variant={danger ? 'danger' : 'primary'}
            busy={busy}
            disabled={disabled}
          >
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

export function Checkbox({
  label,
  hint,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <label className="option">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>
        <span className="option__label">{label}</span>
        {hint && <span className="option__hint">{hint}</span>}
      </span>
    </label>
  );
}

// --- actions ----------------------------------------------------------------

/**
 * Runs a change, reports the outcome and reloads what the pages show. The
 * result tells the caller whether the change went through.
 */
export function useCockpitAction(): (
  call: () => Promise<unknown>,
  messages: { success: string; failure: string },
) => Promise<boolean> {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useCallback(
    async (call, messages) => {
      try {
        await call();
        toast.show({ title: messages.success });
        return true;
      } catch (error) {
        toast.error(error, messages.failure);
        return false;
      } finally {
        void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
        // what the engine does in the background, e.g. a retried job, shows up a moment later
        window.setTimeout(() => void queryClient.invalidateQueries({ queryKey: [COCKPIT] }), FOLLOW_UP);
      }
    },
    [queryClient, toast],
  );
}

export function SearchInput({
  value,
  onChange,
  label,
  id,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  id?: string;
}) {
  return (
    <div className="search-input">
      <Search aria-hidden size={15} />
      <input
        id={id}
        className="input"
        type="search"
        aria-label={label}
        placeholder={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

/** Names of the elements of a model by their id; the model is only read, never shown. */
export function elementNames(xml: string | undefined): Map<string, string> {
  const names = new Map<string, string>();
  if (!xml) {
    return names;
  }
  try {
    const model = new DOMParser().parseFromString(xml, 'application/xml');
    for (const element of Array.from(model.querySelectorAll('[id][name]'))) {
      const name = element.getAttribute('name')?.replace(/\s+/g, ' ').trim();
      const id = element.getAttribute('id');
      if (id && name) {
        names.set(id, name);
      }
    }
  } catch {
    // ids are shown instead of names
  }
  return names;
}

/**
 * Says that a view covers every tenant although the lists are narrowed to
 * one: the engine offers no tenant filter for it.
 */
export function EngineWideNote() {
  const { t } = useI18n();
  const tenant = tenantScope();
  return tenant ? <p className="field__hint engine-wide">{t('tenant.engineWide', { tenant })}</p> : null;
}

/** How many items a confirmation names before it says how many more there are. */
export const SAMPLE_SIZE = 5;

/**
 * A few of the items an action changes, so that the user sees what the
 * number in the confirmation stands for before it is too late.
 */
export function SampleList({ items, total }: { items: string[] | undefined; total: number }) {
  const { t, number } = useI18n();
  if (!items) {
    return <p className="sample sample--loading muted">{t('ops.sample.loading')}</p>;
  }
  if (items.length === 0) {
    return null;
  }
  const shown = items.slice(0, SAMPLE_SIZE);
  return (
    <div className="sample">
      <p className="sample__label">{t('ops.sample.label')}</p>
      <ul className="sample__list">
        {shown.map((item, index) => (
          <li key={`${index}-${item}`} className="sample__item">
            {item}
          </li>
        ))}
      </ul>
      {total > shown.length && <p className="sample__more muted">{t('ops.sample.more', { count: number(total - shown.length) })}</p>}
    </div>
  );
}
