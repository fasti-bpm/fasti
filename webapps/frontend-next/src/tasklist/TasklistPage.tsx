/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { Inbox, MousePointerClick, PanelLeftClose, PanelLeftOpen, Play, Plus, SlidersHorizontal } from 'lucide-react';
import { canCreateFilter, createStarterFilters, getFilterTasks, starterFilters } from '../api/engine';
import { isTyping } from '../app/shortcuts';
import { useSession } from '../app/session';
import { usePacedInterval } from '../lib/activity';
import { afterComplete, tenantScope } from '../lib/userPrefs';
import { useI18n } from '../i18n';
import { Button, EmptyState, IconButton, Kbd, cx } from '../ui';
import { useToast } from '../ui/toast';
import { isTaskCompleted, refreshTasks, useTaskActions } from './actions';
import { FilterEditor } from './FilterEditor';
import { FILTER_COUNTS_KEY, useCountedFilters, useTaskFilters } from './filterCounts';
import { FilterList, FilterSelect } from './FilterList';
import { NotifyToggle, taskListRefresh, useNewTaskNotices, useNotifyPreference } from './notifications';
import { toTaskQuery, type Criterion } from './search';
import { isQueueView, minuteOf, queueQuery, queueSorting } from './queue';
import { PAGE_SIZE, useTasklistLocation, useTasklistPreferences } from './state';
import { TaskDetail } from './TaskDetail';
import { useGetNext } from './useGetNext';
import { TaskList } from './TaskList';
import { stabilize } from './stable';
import type { TaskListItem } from '../api/types';
import { SearchTools } from './SearchTools';
import { decodeSearch, getCardVariables, searchLink, withCardVariables } from './searchLinks';
import './tasklist.css';
import { fieldNames, processKeyOf, useProcessCardFields } from '../lib/cardFields';
import { getSubstitutionMarks } from '../api/absence';
import { AbsenceBanner, useAbsenceEnabled } from '../absence/AbsenceBanner';
import { withSubstitutionMarks } from '../absence/marks';

const REFRESH_INTERVAL = 30_000;
/** below this width either the list or the open task is shown; see tasklist.css */
const SINGLE_PANE = '(max-width: 760px)';

export function TasklistPage() {
  const { t } = useI18n();
  const session = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const actions = useTaskActions();
  const [location, navigate] = useTasklistLocation();
  const [preferences, setPreferences] = useTasklistPreferences(session.userId);
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const [checked, setChecked] = useState<Set<string>>(() => new Set());
  const listRef = useRef<HTMLUListElement>(null);
  /** the filter dialog: `null` closed, `{ filterId: null }` a new filter */
  const [editing, setEditing] = useState<{ filterId: string | null } | null>(null);

  const refreshInterval = usePacedInterval(REFRESH_INTERVAL);
  const minuteInterval = usePacedInterval(60_000);
  const [notify, toggleNotify, notifyInBackground] = useNotifyPreference();
  /** the filter menu of narrow screens was opened: its entries show the task counts */
  const [filterMenuUsed, setFilterMenuUsed] = useState(false);
  const { filters, counts: filterCounts } = useTaskFilters({ collapsed: preferences.filtersCollapsed, menuUsed: filterMenuUsed });
  const creatable = useQuery({
    queryKey: ['filters', 'creatable'],
    queryFn: canCreateFilter,
    staleTime: 60_000,
  });
  const createFilter = useMutation({
    mutationFn: () =>
      createStarterFilters(
        session.userId,
        starterFilters((kind) => ({ name: t(`filters.starter.${kind}`), description: t(`filters.starter.${kind}.text`) })),
        (filters.data ?? []).map((filter) => filter.name),
      ),
    // also after a failure: the lists created until then are shown, a retry adds the others
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['filters'] }),
    onError: (error) => toast.error(error, t('filters.createFailed')),
  });

  // the filter of the URL, else the one used last, else the first one
  const filterId = useMemo(() => {
    const available = filters.data;
    if (!available) {
      // not known yet: the tasks of the filter used last are asked for beside the filters, not after them
      return location.filterId ?? preferences.filterId ?? null;
    }
    const exists = (id: string | null) => !!id && available.some((filter) => filter.id === id);
    if (exists(location.filterId)) return location.filterId;
    if (exists(preferences.filterId)) return preferences.filterId;
    return available[0]?.id ?? null;
  }, [filters.data, location.filterId, preferences.filterId]);
  const filter = filters.data?.find((candidate) => candidate.id === filterId);

  useEffect(() => {
    if (filterId && filterId !== location.filterId) {
      navigate({ filterId }, { replace: true });
    }
  }, [filterId, location.filterId, navigate]);

  // a shared link shows its search without replacing the remembered one, until the user keeps it
  const [linkedSearch, setLinkedSearch] = useState<{
    criteria: Criterion[];
    onlyMine: boolean;
    /** the view and order before a link changed them, for going back */
    restore?: Pick<typeof preferences, 'queue' | 'sorting'>;
  } | null>(null);
  const viewBefore = useRef({ queue: preferences.queue, sorting: preferences.sorting });
  viewBefore.current = { queue: preferences.queue, sorting: preferences.sorting };
  const search = linkedSearch ?? { criteria: preferences.criteria, onlyMine: preferences.onlyMine };
  const setSearch = (change: Partial<{ criteria: Criterion[]; onlyMine: boolean }>) => {
    // two changes in a row (e.g. clearing the search) must both stay
    if (linkedSearch) setLinkedSearch((current) => (current ? { ...current, ...change } : current));
    else setPreferences(change);
  };
  const queue = isQueueView(preferences.queue) ? preferences.queue : 'all';
  // what the search adds to the filter; the urgency views add a comparison with now (see the query)
  const searchQuery = useMemo(
    () => toTaskQuery(search.criteria, search.onlyMine ? session.userId : null),
    [search.criteria, search.onlyMine, session.userId],
  );
  const taskQuery = useMemo(() => ({ search: searchQuery, queue }), [searchQuery, queue]);
  const refreshOff = filter?.properties?.refresh === false;
  // what is read along with the list: the values on the cards, the substitution marks
  const listInterval = refreshOff ? false : refreshInterval;
  const listRefresh = taskListRefresh({
    interval: refreshInterval,
    minute: minuteInterval,
    refreshOff,
    urgency: queue !== 'all' && queue !== 'unclaimed',
    notify,
    notifyInBackground,
  });
  const tasks = useQuery({
    queryKey: ['tasks', filterId, location.page, preferences.sorting, taskQuery],
    queryFn: ({ signal }) =>
      getFilterTasks(filterId ?? '', {
        firstResult: location.page * PAGE_SIZE,
        maxResults: PAGE_SIZE,
        sorting: [preferences.sorting],
        // "now" is taken when the list is read, so an urgency view moves along with every
        // refresh while the query, and with it the cached list, stays the same
        query: { ...searchQuery, ...queueQuery(queue, minuteOf(new Date())) },
        signal,
      }),
    // also before the filters are known: the list of the filter used last loads beside them
    enabled: !!filterId,
    placeholderData: keepPreviousData,
    ...listRefresh,
  });
  // the task list counts the tasks of its filter with every page: unless a search narrows the
  // list, that is the number the filter shows, without a request of its own
  const unfiltered = queue === 'all' && Object.keys(searchQuery).length === 0 && !tenantScope();
  const listedCount = filterId && filter && unfiltered && tasks.data && !tasks.isPlaceholderData ? tasks.data.count : null;
  const countedFilters = useCountedFilters(filters.data, filterCounts.data, { id: filterId, count: listedCount });
  // the process variables a user chose for the cards, read for the shown tasks
  const cardVariables = preferences.cardVariables;
  // and the business fields chosen for the processes of the shown tasks
  const [processFields] = useProcessCardFields();
  const cardNames = useMemo(
    () => [
      ...new Set([
        ...cardVariables,
        ...fieldNames(processFields, tasks.data?.tasks.map(processKeyOf) ?? []),
      ]),
    ],
    [cardVariables, processFields, tasks.data],
  );
  const cardValues = useQuery({
    queryKey: ['task-card-variables', cardNames, tasks.data?.tasks.map((task) => task.processInstanceId)],
    queryFn: ({ signal }) => getCardVariables(tasks.data?.tasks ?? [], cardNames, signal),
    enabled: !!tasks.data && cardNames.length > 0,
    placeholderData: keepPreviousData,
    // the values change without the list changing, so they are read again with it
    refetchInterval: listInterval,
  });
  // while a task is open the list keeps its order; tasks that arrive wait behind a notice
  const listKey = JSON.stringify([filterId, location.page, preferences.sorting, taskQuery]);
  const working = !!location.taskId;
  const [held, setHeld] = useState<{ key: string; tasks: TaskListItem[] } | null>(null);
  useEffect(() => {
    if (!working) {
      if (held) setHeld(null);
    } else if (tasks.data && !tasks.isPlaceholderData && held?.key !== listKey) {
      setHeld({ key: listKey, tasks: tasks.data.tasks });
    }
  }, [working, tasks.data, tasks.isPlaceholderData, listKey, held]);
  // a task completed here is gone, also while a list read before still has it
  const completed = useCallback((taskId: string) => isTaskCompleted(queryClient, taskId), [queryClient]);
  const stable = useMemo(
    () =>
      held && held.key === listKey && tasks.data
        ? stabilize(held.tasks, tasks.data.tasks, tasks.data.tasks.length >= PAGE_SIZE, completed)
        : null,
    [held, listKey, tasks.data, completed],
  );
  const listed = useMemo(
    () => stable?.tasks ?? tasks.data?.tasks.filter((task) => !completed(task.id)),
    [stable, tasks.data, completed],
  );
  // tasks that went to a substitute carry the absent user they are held for
  const absenceEnabled = useAbsenceEnabled();
  const listedIds = listed?.map((task) => task.id) ?? [];
  const substitutionMarks = useQuery({
    queryKey: ['substitution-marks', listedIds],
    queryFn: ({ signal }) => getSubstitutionMarks(listedIds, signal),
    enabled: absenceEnabled && listedIds.length > 0,
    placeholderData: keepPreviousData,
    refetchInterval: listInterval,
  });
  const shownPage = useMemo(
    () =>
      tasks.data && listed
        ? {
            ...tasks.data,
            tasks: withSubstitutionMarks(
              cardValues.data ? withCardVariables(listed, cardValues.data) : listed,
              substitutionMarks.data,
            ),
          }
        : tasks.data,
    [tasks.data, listed, cardValues.data, substitutionMarks.data],
  );
  const taskItems = listed;
  // when the last tasks of the last page are done, show the page that is now the last one
  const lastPage = tasks.data ? Math.max(0, Math.ceil(tasks.data.count / PAGE_SIZE) - 1) : 0;
  useEffect(() => {
    if (tasks.data && !tasks.isPlaceholderData && location.page > lastPage) navigate({ page: lastPage }, { replace: true });
  }, [tasks.data, tasks.isPlaceholderData, location.page, lastPage, navigate]);
  // a task that is not on the shown page cannot hold the tab stop of the list
  const shownHighlight =
    [highlightedId, location.taskId].find((id) => id && taskItems?.some((task) => task.id === id)) ??
    taskItems?.[0]?.id ??
    null;

  // a shared link carries a search; it is shown until the user keeps it or goes back
  const [urlParams, setUrlParams] = useSearchParams();
  const linked = urlParams.get('q');
  useEffect(() => {
    const decoded = decodeSearch(linked);
    if (linked === null) return;
    if (decoded)
      setLinkedSearch({
        criteria: decoded.criteria,
        onlyMine: decoded.onlyMine,
        ...(decoded.view ? { restore: viewBefore.current } : {}),
      });
    // a link may bring an urgency view, e.g. the overdue tasks of a group, with its order
    if (decoded?.view) {
      const view = decoded.view;
      const sorting = queueSorting(view);
      setPreferences({ queue: view, ...(sorting ? { sorting } : {}) });
    }
    setUrlParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete('q');
        return next;
      },
      { replace: true },
    );
  }, [linked, setUrlParams, setPreferences]);
  // only the first page shows the newest tasks of a filter sorted by creation
  useNewTaskNotices(
    JSON.stringify([filterId, taskQuery]),
    // every task of the page, also those the stable list holds back
    location.page === 0 && !tasks.isPlaceholderData ? tasks.data?.tasks : undefined,
    notify,
  );

  const variableLabels = useMemo(
    () => ({
      ...Object.fromEntries(cardVariables.map((name) => [name, name])),
      ...Object.fromEntries(
        (filter?.properties?.variables ?? []).map((variable) => [
          variable.name,
          variable.label || variable.name,
        ]),
      ),
    }),
    [filter, cardVariables],
  );

  const undefinedVariables = useMemo(
    () =>
      filter?.properties?.showUndefinedVariable
        ? (filter.properties.variables ?? []).map((variable) => variable.name).filter(Boolean)
        : undefined,
    [filter],
  );

  const selectFilter = useCallback(
    (id: string) => {
      setPreferences({ filterId: id });
      setChecked(new Set());
      navigate({ filterId: id, taskId: null, page: 0 });
    },
    [navigate, setPreferences],
  );

  const openTask = useCallback(
    (taskId: string) => {
      setHighlightedId(taskId);
      navigate({ taskId, tab: 'form' });
      // on a phone the list is hidden: focus follows to the task once it is shown
      if (window.matchMedia(SINGLE_PANE).matches) {
        window.setTimeout(() => document.getElementById('task-detail-title')?.focus(), 150);
      }
    },
    [navigate],
  );

  // "get next work": the task the user may take that is due first, claimed and opened
  const nextScope = useMemo(
    () => (preferences.nextFromFilter && filter ? { filterId: filter.id, name: filter.name } : null),
    [preferences.nextFromFilter, filter],
  );
  const nextWork = useGetNext(nextScope, openTask);
  const getNext = nextWork.run;
  // the command palette asks for it with `?next=1`, once the filters are known
  const nextAsked = urlParams.get('next') === '1';
  useEffect(() => {
    if (!nextAsked || !filters.data) return;
    setUrlParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete('next');
        return next;
      },
      { replace: true },
    );
    void getNext();
  }, [nextAsked, filters.data, setUrlParams, getNext]);

  const closeTask = useCallback(() => {
    navigate({ taskId: null });
    if (highlightedId) {
      // after the list is shown again
      window.setTimeout(() => document.getElementById(`task-${highlightedId}`)?.focus());
    }
  }, [navigate, highlightedId]);

  // after a task was completed the next one of the list is opened, unless the
  // user chose to return to the list; on a narrow screen, where the task
  // covers the list, the list is shown again
  const onCompleted = useCallback(
    (taskId: string, resolved: boolean) => {
      // a resolved task goes back to its owner; only completing gets the next work, if the user chose it
      if (preferences.completeAndNext && !resolved) {
        navigate({ taskId: null }, { replace: true });
        void getNext();
        return;
      }
      // never a task that is gone, should the list still show one
      const open = taskItems?.filter((task) => task.id === taskId || !completed(task.id));
      const index = open?.findIndex((task) => task.id === taskId) ?? -1;
      const next = index >= 0 ? (open?.[index + 1] ?? open?.[index - 1]) : undefined;
      setHighlightedId(next?.id ?? null);
      if (afterComplete() === 'list' || window.matchMedia(SINGLE_PANE).matches) {
        // the next task is marked in the list, so Enter opens it
        navigate({ taskId: null }, { replace: true });
        if (next) window.setTimeout(() => document.getElementById(`task-${next.id}`)?.focus());
        return;
      }
      navigate({ taskId: next?.id ?? null, tab: 'form' }, { replace: true });
    },
    [taskItems, navigate, preferences.completeAndNext, getNext, completed],
  );

  const move = useCallback(
    (step: 1 | -1) => {
      if (!taskItems?.length) {
        return;
      }
      const current = taskItems.findIndex((task) => task.id === (highlightedId ?? location.taskId));
      const index = Math.min(Math.max(current + step, 0), taskItems.length - 1);
      const target = taskItems[index];
      if (target) {
        setHighlightedId(target.id);
        const element = document.getElementById(`task-${target.id}`);
        element?.focus();
        element?.scrollIntoView({ block: 'nearest' });
      }
    },
    [taskItems, highlightedId, location.taskId],
  );

  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event)) {
        return;
      }
      // dialogs, menus and popovers handle their keys themselves
      if (document.querySelector('[role="dialog"], [role="menu"]')) {
        return;
      }
      const inList =
        document.activeElement === document.body ||
        !!listRef.current?.contains(document.activeElement);
      const selected = location.taskId
        ? queryClient.getQueryData<{ assignee: string | null }>(['task', location.taskId])
        : undefined;
      switch (event.key) {
        case 'j':
          move(1);
          break;
        case 'k':
          move(-1);
          break;
        case 'ArrowDown':
          if (!inList) return;
          move(1);
          break;
        case 'ArrowUp':
          if (!inList) return;
          move(-1);
          break;
        case 'Enter':
          if (document.activeElement !== document.body || !highlightedId) return;
          openTask(highlightedId);
          break;
        case '/':
          document.getElementById('task-search')?.focus();
          break;
        case 'n':
          if (event.shiftKey || event.repeat) return;
          void getNext();
          break;
        case 'r':
          refreshTasks(queryClient, location.taskId ?? undefined);
          break;
        case 'c':
          if (!location.taskId || !selected) return;
          if (selected.assignee === session.userId) {
            void actions.unclaim(location.taskId);
          } else if (!selected.assignee) {
            void actions.claim(location.taskId);
          }
          break;
        case 'Escape':
          if (!location.taskId) return;
          closeTask();
          break;
        default:
          return;
      }
      event.preventDefault();
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [move, openTask, closeTask, highlightedId, location.taskId, queryClient, actions, session.userId, getNext]);

  const collapsed = preferences.filtersCollapsed;
  const preview = taskItems?.find((task) => task.id === location.taskId);

  return (
    <div
      className={cx(
        'tasklist',
        collapsed && 'tasklist--collapsed',
        location.taskId && 'tasklist--task-open',
      )}
    >
      <p className="sr-only" role="status" aria-live="polite">
        {nextWork.status?.text}
      </p>
      <aside className="tasklist__filters" aria-label={t('filters.title')}>
        <header className="pane-header">
          <h2 className="pane-title">{t('filters.title')}</h2>
          {creatable.data && (
            <IconButton icon={Plus} label={t('filterEdit.createTitle')} onClick={() => setEditing({ filterId: null })} />
          )}
          <IconButton
            icon={PanelLeftClose}
            label={t('filters.collapse')}
            onClick={() => setPreferences({ filtersCollapsed: true })}
          />
        </header>
        <div className="tasklist__scroll">
          {filters.data?.length === 0 ? (
            <p className="pane-empty">{t('filters.empty')}</p>
          ) : (
            <FilterList
              filters={countedFilters}
              error={filters.error}
              selectedId={filterId}
              onSelect={selectFilter}
              onRetry={() => void filters.refetch()}
              canCreate={false}
              creating={false}
              onCreate={() => undefined}
            />
          )}
        </div>
      </aside>

      <section className="tasklist__list" aria-label={t('tasks.title')}>
        <header className="pane-header">
          {collapsed && (
            <IconButton
              icon={PanelLeftOpen}
              label={t('filters.expand')}
              className="tasklist__expand"
              onClick={() => setPreferences({ filtersCollapsed: false })}
            />
          )}
          <h2 className="pane-title pane-title--filter">{filter?.name ?? t('tasks.title')}</h2>
          <FilterSelect
            filters={countedFilters}
            selectedId={filterId}
            onSelect={selectFilter}
            onUse={() => {
              setFilterMenuUsed(true);
              // behind the closed pane the counts follow no timer: every use of the menu reads old ones again
              void queryClient.refetchQueries(
                { queryKey: FILTER_COUNTS_KEY, exact: true, type: 'active', stale: true },
                // focus and pointer arrive together: one read
                { cancelRefetch: false },
              );
            }}
            // the counts of a closed menu are not read after every burst of task actions
            onLeave={() => setFilterMenuUsed(false)}
          />
          {filter && (
            <IconButton
              icon={SlidersHorizontal}
              label={t('filterEdit.open', { name: filter.name })}
              onClick={() => setEditing({ filterId: filter.id })}
            />
          )}
          {creatable.data && (
            <IconButton
              icon={Plus}
              label={t('filterEdit.createTitle')}
              className="tasklist__new-filter"
              onClick={() => setEditing({ filterId: null })}
            />
          )}
          <NotifyToggle on={notify} onToggle={toggleNotify} />
          <Link to="/processes" className="btn btn--secondary btn--sm">
            <Play className="btn__icon" aria-hidden size={15} />
            {t('nav.processes')}
          </Link>
        </header>
        <AbsenceBanner />
        <div className="get-next">
          <Button
            variant="primary"
            size="sm"
            icon={Inbox}
            className="get-next__run"
            busy={nextWork.running}
            aria-keyshortcuts="N"
            title={nextScope ? t('getNext.fromFilter', { filter: nextScope.name }) : t('getNext.fromAll')}
            onClick={() => void getNext()}
          >
            {nextWork.running ? t('getNext.running') : t('getNext.button')}
          </Button>
          {filter && (
            <label className="checkbox get-next__scope">
              <input
                type="checkbox"
                checked={preferences.nextFromFilter}
                onChange={(event) => setPreferences({ nextFromFilter: event.target.checked })}
              />
              <span>{t('getNext.scope', { filter: filter.name })}</span>
            </label>
          )}
        </div>
        {filters.data && filters.data.length === 0 ? (
          <div className="tasklist__scroll tasklist__no-filter">
            <FilterList
              filters={filters.data}
              error={null}
              selectedId={null}
              onSelect={selectFilter}
              onRetry={() => void filters.refetch()}
              canCreate={creatable.data ?? false}
              creating={createFilter.isPending}
              onCreate={() => createFilter.mutate()}
            />
          </div>
        ) : (
          <TaskList
            ref={listRef}
            page={filter ? shownPage : undefined}
            // the filter used last may be gone by now: its list failing only matters once it is known to exist
            error={filters.error ?? (filter ? tasks.error : null)}
            fetching={tasks.isFetching}
            updating={tasks.isPlaceholderData}
            pageIndex={location.page}
            onPage={(page) => navigate({ page })}
            selectedId={location.taskId}
            highlightedId={shownHighlight}
            onHighlight={setHighlightedId}
            onOpen={openTask}
            sorting={preferences.sorting}
            onSort={(sorting) => {
              setPreferences({ sorting });
              navigate({ page: 0 }, { replace: true });
            }}
            queue={queue}
            onQueue={(next) => {
              // a view brings its order: soonest due first, or the longest waiting first
              setPreferences({ queue: next, ...(queueSorting(next) ? { sorting: queueSorting(next) ?? preferences.sorting } : {}) });
              navigate({ page: 0 }, { replace: true });
            }}
            criteria={search.criteria}
            onCriteria={(criteria) => {
              setSearch({ criteria });
              navigate({ page: 0 }, { replace: true });
            }}
            onlyMine={search.onlyMine}
            onOnlyMine={(onlyMine) => {
              setSearch({ onlyMine });
              navigate({ page: 0 }, { replace: true });
            }}
            variableLabels={variableLabels}
            undefinedVariables={undefinedVariables}
            processFields={processFields}
            notice={
              <>
              {nextWork.status?.visible && (
                // announced by the live region of the page, which stays when a phone shows only the task
                <div className={`notice notice--${nextWork.status.tone} notice--actions tasks__linked`}>
                  <span className="notice__body">{nextWork.status.text}</span>
                  <button type="button" className="link" onClick={nextWork.dismiss}>
                    {t('action.close')}
                  </button>
                </div>
              )}

              {stable && stable.added > 0 && (
                <div className="notice notice--info notice--actions tasks__linked" role="status">
                  <span className="notice__body">{t('tasks.arrived', { count: stable.added })}</span>
                  <button
                    type="button"
                    className="link"
                    onClick={() => tasks.data && setHeld({ key: listKey, tasks: tasks.data.tasks })}
                  >
                    {t('tasks.arrived.show')}
                  </button>
                </div>
              )}
              {linkedSearch && (
                <div className="notice notice--info notice--actions tasks__linked" role="status">
                  <span className="notice__body">{t('tasks.linked')}</span>
                  <span className="notice__actions">
                    <button
                      type="button"
                      className="link"
                      onClick={() => {
                        setPreferences({ criteria: linkedSearch.criteria, onlyMine: linkedSearch.onlyMine });
                        setLinkedSearch(null);
                      }}
                    >
                      {t('tasks.linked.keep')}
                    </button>
                    <button
                      type="button"
                      className="link"
                      onClick={() => {
                        if (linkedSearch.restore) setPreferences(linkedSearch.restore);
                        setLinkedSearch(null);
                        navigate({ page: 0 }, { replace: true });
                      }}
                    >
                      {t('tasks.linked.undo')}
                    </button>
                  </span>
                </div>
              )}
              </>
            }
            tools={
              <SearchTools
                link={searchLink(
                  window.location.href.split('#')[0] ?? '',
                  filterId,
                  search.criteria,
                  search.onlyMine,
                )}
                criteria={search.criteria}
                onlyMine={search.onlyMine}
                saved={preferences.savedSearches}
                onSaved={(savedSearches) => setPreferences({ savedSearches })}
                onApply={(saved) => {
                  setSearch({ criteria: saved.criteria, onlyMine: saved.onlyMine });
                  navigate({ page: 0 }, { replace: true });
                }}
                cardVariables={cardVariables}
                onCardVariables={(names) => setPreferences({ cardVariables: names })}
              />
            }
            onRefresh={() => refreshTasks(queryClient, location.taskId ?? undefined)}
            checked={checked}
            onChecked={setChecked}
          />
        )}
      </section>

      {editing && (
        <FilterEditor
          filterId={editing.filterId}
          onClose={() => setEditing(null)}
          onSaved={(id) => {
            setEditing(null);
            selectFilter(id);
          }}
          onDeleted={() => {
            setEditing(null);
            setPreferences({ filterId: null });
            navigate({ filterId: null, taskId: null, page: 0 });
          }}
        />
      )}

      <div className="tasklist__detail">
        {location.taskId ? (
          <TaskDetail
            key={location.taskId}
            taskId={location.taskId}
            preview={preview}
            tab={location.tab}
            onTab={(tab) => navigate({ tab }, { replace: true })}
            onClose={closeTask}
            onCompleted={onCompleted}
            actions={actions}
            completeAndNext={preferences.completeAndNext}
            onCompleteAndNext={(completeAndNext) => setPreferences({ completeAndNext })}
          />
        ) : (
          <EmptyState icon={MousePointerClick} title={t('task.none')}>
            <span className="hint-keys">
              <Kbd>J</Kbd>
              <Kbd>K</Kbd> {t('task.noneHint.move')} · <Kbd>Enter</Kbd> {t('task.noneHint.open')} · <Kbd>N</Kbd>{' '}
              {t('task.noneHint.next')}
            </span>
          </EmptyState>
        )}
      </div>
    </div>
  );
}
