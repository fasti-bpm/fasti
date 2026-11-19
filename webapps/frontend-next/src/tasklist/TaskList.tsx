/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useState, useEffect } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { DropdownMenu } from 'radix-ui';
import {
  ArrowDownWideNarrow,
  ArrowUpNarrowWide,
  CalendarClock,
  Check,
  ChevronLeft,
  ChevronRight,
  Flag,
  Plus,
  RefreshCw,
  Search,
  User,
  UserCheck,
  Timer,
  X, AlarmClock } from 'lucide-react';
import type { TaskListItem, TaskPage, TaskSortBy, TaskSorting, TaskVariable } from '../api/types';
import { useI18n, type MessageKey } from '../i18n';
import { dueState, parseEngineDate } from '../lib/dates';
import { EmptyState, ErrorState, IconButton, Loading, NoMatch, Skeleton, Tooltip, cx } from '../ui';
import {
  CRITERION_FIELDS,
  OPERATORS,
  isVariableField,
  type Criterion,
  type CriterionField,
  type VariableOperator,
} from './search';
import { BulkTaskBar } from './BulkTasks';
import { PAGE_SIZE } from './state';
import { QUEUE_VIEWS, slaUsed, type QueueView } from './queue';
import { VariableSortDialog } from './VariableSortDialog';
import { fieldLabels, processKeyOf, type ProcessCardFields } from '../lib/cardFields';
import { MenuContent } from '../ui/menu';

const SORT_OPTIONS: { sortBy: TaskSortBy; label: MessageKey }[] = [
  { sortBy: 'created', label: 'sort.created' },
  { sortBy: 'dueDate', label: 'sort.due' },
  { sortBy: 'followUpDate', label: 'sort.followUp' },
  { sortBy: 'priority', label: 'sort.priority' },
  { sortBy: 'nameCaseInsensitive', label: 'sort.name' },
  { sortBy: 'assignee', label: 'sort.assignee' },
];

export function DueBadge({ due, prefix, created }: { due: string | null; prefix?: string; created?: string | null }) {
  const { t, relative, dateTime } = useI18n();
  const date = parseEngineDate(due);
  if (!date) {
    return null;
  }
  const state = dueState(date);
  // how much of the time from creation to the due date is used: a small clock of the service level
  const used = created === undefined ? null : slaUsed(parseEngineDate(created), date);
  return (
    <span className={cx('badge', `badge--${state === 'later' ? 'ontrack' : state}`)} title={dateTime(date)}>
      <CalendarClock aria-hidden size={13} />
      {prefix ?? (state === 'overdue' ? t('task.overdue') : t('task.due'))} {relative(date)}
      {used !== null && (
        <span
          className="sla"
          role="meter"
          aria-label={t('sla.used')}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(used * 100)}
        >
          <span className="sla__bar" style={{ width: `${Math.round(used * 100)}%` }} />
        </span>
      )}
    </span>
  );
}

/** A follow-up date still ahead: the task is put aside until then. */
export function FollowUpBadge({ followUp, now }: { followUp: string | null; now?: Date }) {
  const { t, relative, dateTime } = useI18n();
  const date = parseEngineDate(followUp);
  // the badge leaves by itself when the date comes, also without a refresh of the list
  const [passed, setPassed] = useState(false);
  const due = date?.getTime() ?? null;
  useEffect(() => {
    if (due === null || now) return undefined;
    const wait = due - Date.now();
    if (wait <= 0 || wait > 2 ** 31 - 1) return undefined;
    const timer = window.setTimeout(() => setPassed(true), wait);
    return () => window.clearTimeout(timer);
  }, [due, now]);
  if (!date || passed || date.getTime() <= (now ?? new Date()).getTime()) {
    return null;
  }
  return (
    <span className="badge badge--later" title={dateTime(date)}>
      <AlarmClock aria-hidden size={13} />
      {t('task.followUpBadge', { time: relative(date) })}
    </span>
  );
}

/**
 * The variables a task card shows: the business fields of its process first, then the
 * labelled variables of the filter and the user, then - if the filter asks for it
 * (showUndefinedVariable) - the variables of the filter the task does not have, without value.
 */
export function cardVariablesOf(
  task: TaskListItem,
  variableLabels: Record<string, string>,
  own: Record<string, string>,
  undefinedVariables?: string[],
): TaskVariable[] {
  const variables = [
    ...Object.keys(own).flatMap((name) => task.variables.filter((variable) => variable.name === name)),
    ...task.variables.filter((variable) => variable.name in variableLabels && !(variable.name in own)),
  ];
  for (const name of undefinedVariables ?? []) {
    if (!(name in own) && !task.variables.some((variable) => variable.name === name)) {
      variables.push({ name, value: undefined } as TaskVariable);
    }
  }
  return variables;
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

let criterionId = 1;

function SearchBar({
  criteria,
  onChange,
}: {
  criteria: Criterion[];
  onChange: (criteria: Criterion[]) => void;
}) {
  const { t } = useI18n();
  const [field, setField] = useState<CriterionField>('name');
  const [variable, setVariable] = useState('');
  const [operator, setOperator] = useState<VariableOperator>('eq');
  const [value, setValue] = useState('');
  const variableField = isVariableField(field);

  const add = (event: FormEvent) => {
    event.preventDefault();
    if (!value.trim() || (variableField && !variable.trim())) {
      return;
    }
    const criterion: Criterion = {
      id: `c${Date.now()}-${criterionId++}`,
      field,
      value: value.trim(),
      ...(variableField ? { variable: variable.trim(), operator } : {}),
    };
    // one value per text field; any number of variable criteria
    onChange([
      ...criteria.filter((existing) => variableField || existing.field !== field),
      criterion,
    ]);
    setValue('');
  };

  const fieldLabel = (criterion: Criterion): string => {
    const label = CRITERION_FIELDS.find((entry) => entry.field === criterion.field)?.label;
    return label ? t(label) : criterion.field;
  };

  return (
    <div className="search">
      <form className="search__form" onSubmit={add} role="search" aria-label={t('search.title')}>
        <select
          className="input search__field"
          aria-label={t('search.fieldLabel')}
          value={field}
          onChange={(event) => setField(event.target.value as CriterionField)}
        >
          {CRITERION_FIELDS.filter((entry) => !entry.hidden).map((entry) => (
            <option key={entry.field} value={entry.field}>
              {t(entry.label)}
            </option>
          ))}
        </select>
        {variableField && (
          <>
            <input
              className="input search__variable"
              aria-label={t('search.variableName')}
              placeholder={t('search.variableName')}
              spellCheck={false}
              value={variable}
              onChange={(event) => setVariable(event.target.value)}
            />
            <select
              className="input search__operator"
              aria-label={t('search.operator')}
              value={operator}
              onChange={(event) => setOperator(event.target.value as VariableOperator)}
            >
              {OPERATORS.map((entry) => (
                <option key={entry.operator} value={entry.operator}>
                  {entry.symbol}
                </option>
              ))}
            </select>
          </>
        )}
        <div className="search__value">
          <Search aria-hidden size={15} />
          <input
            id="task-search"
            className="input"
            type="search"
            aria-label={t('search.value')}
            placeholder={t('search.placeholder')}
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />
        </div>
        <IconButton
          type="submit"
          icon={Plus}
          label={t('search.add')}
          className="search__add"
          disabled={!value.trim() || (variableField && !variable.trim())}
        />
      </form>
      {criteria.length > 0 && (
        <ul className="chips" aria-label={t('search.active')}>
          {criteria.map((criterion) => (
            <li key={criterion.id} className="chip">
              <span className="chip__label">
                {fieldLabel(criterion)}
                {criterion.variable &&
                  ` ${criterion.variable} ${OPERATORS.find((entry) => entry.operator === criterion.operator)?.symbol ?? '='}`}
                {': '}
              </span>
              {criterion.value}
              <button
                type="button"
                className="chip__remove"
                aria-label={t('search.remove', { criterion: fieldLabel(criterion) })}
                onClick={() => onChange(criteria.filter((other) => other.id !== criterion.id))}
              >
                <X aria-hidden size={13} />
              </button>
            </li>
          ))}
          <li>
            <button type="button" className="link link--subtle" onClick={() => onChange([])}>
              {t('search.clear')}
            </button>
          </li>
        </ul>
      )}
    </div>
  );
}

export interface TaskListProps {
  page: TaskPage | undefined;
  error: unknown;
  fetching: boolean;
  /** the tasks are of an earlier filter or page while the new ones load */
  updating?: boolean;
  pageIndex: number;
  onPage: (page: number) => void;
  selectedId: string | null;
  highlightedId: string | null;
  onHighlight: (taskId: string) => void;
  onOpen: (taskId: string) => void;
  sorting: TaskSorting;
  onSort: (sorting: TaskSorting) => void;
  /** a quick view by urgency */
  queue?: QueueView;
  onQueue?: (queue: QueueView) => void;
  criteria: Criterion[];
  onCriteria: (criteria: Criterion[]) => void;
  onlyMine: boolean;
  onOnlyMine: (onlyMine: boolean) => void;
  variableLabels: Record<string, string>;
  /**
   * the variables of the filter that cards show even when a task does not have them
   * (the showUndefinedVariable property of the filter), with an empty value
   */
  undefinedVariables?: string[];
  /** the business fields chosen per process */
  processFields?: ProcessCardFields;
  onRefresh: () => void;
  /** more controls of the search, next to refresh */
  tools?: ReactNode;
  /** shown between the toolbar and the list, e.g. that a shared search is applied */
  notice?: ReactNode;
  /** ids of the tasks checked for a change of many tasks */
  checked: Set<string>;
  onChecked: (checked: Set<string>) => void;
}

/** The tasks of a filter with search, sorting and paging. */
export const TaskList = forwardRef<HTMLUListElement, TaskListProps>(function TaskList(props, ref) {
  const { t, relative, dateTime } = useI18n();
  const { page, sorting } = props;
  const pages = page ? Math.max(1, Math.ceil(page.count / PAGE_SIZE)) : 1;
  const first = props.pageIndex * PAGE_SIZE;
  const narrowed = props.criteria.length > 0 || props.onlyMine;
  const checkedTasks = page ? page.tasks.filter((task) => props.checked.has(task.id)) : [];
  const allChecked = !!page && page.tasks.length > 0 && checkedTasks.length === page.tasks.length;
  const toggle = (taskId: string, on: boolean) => {
    const next = new Set(props.checked);
    if (on) {
      next.add(taskId);
    } else {
      next.delete(taskId);
    }
    props.onChecked(next);
  };

  const [sortingByVariable, setSortingByVariable] = useState(false);

  return (
    <div className="tasks">
      <div className="tasks__toolbar">
        <SearchBar criteria={props.criteria} onChange={props.onCriteria} />
        <div className="tasks__options">
          {page && page.tasks.length > 0 && (
            <input
              type="checkbox"
              className="tasks__check-all"
              aria-label={t('ops.tasks.checkAll')}
              checked={allChecked}
              ref={(input) => {
                // some, not all, checked
                if (input) input.indeterminate = !allChecked && checkedTasks.length > 0;
              }}
              onChange={(event) =>
                props.onChecked(event.target.checked ? new Set(page.tasks.map((task) => task.id)) : new Set())
              }
            />
          )}
          <Tooltip label={t('tasks.onlyMine')}>
            <button
              type="button"
              className={cx('toggle', props.onlyMine && 'is-on')}
              aria-pressed={props.onlyMine}
              onClick={() => props.onOnlyMine(!props.onlyMine)}
            >
              <UserCheck aria-hidden size={15} />
              {t('tasks.mine')}
            </button>
          </Tooltip>
          <span className="tasks__count" aria-live="polite">
            {page ? t('tasks.count', { count: page.count }) : ' '}
          </span>
          {props.onQueue && (
            <DropdownMenu.Root>
              <DropdownMenu.Trigger
                className={cx('chip-btn queue-btn', props.queue && props.queue !== 'all' && 'is-on')}
                aria-label={props.queue && props.queue !== 'all' ? `${t('queue.title')}: ${t(`queue.${props.queue}`)}` : t('queue.title')}
                title={t('queue.title')}
              >
                <Timer aria-hidden size={14} />
                {/* without a view only the clock shows, so the toolbar keeps its room */}
                {props.queue && props.queue !== 'all' && <span className="queue-btn__label">{t(`queue.${props.queue}`)}</span>}
              </DropdownMenu.Trigger>
              <DropdownMenu.Portal>
                <MenuContent>
                  <DropdownMenu.Label className="menu__label">{t('queue.title')}</DropdownMenu.Label>
                  <DropdownMenu.RadioGroup value={props.queue ?? 'all'} onValueChange={(value) => props.onQueue?.(value as QueueView)}>
                    {QUEUE_VIEWS.map((view) => (
                      <DropdownMenu.RadioItem key={view} value={view} className="menu__item">
                        <span className="menu__check">
                          <DropdownMenu.ItemIndicator>
                            <Check aria-hidden size={16} />
                          </DropdownMenu.ItemIndicator>
                        </span>
                        {t(`queue.${view}`)}
                      </DropdownMenu.RadioItem>
                    ))}
                  </DropdownMenu.RadioGroup>
                </MenuContent>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          )}
          <DropdownMenu.Root>
            <DropdownMenu.Trigger className="chip-btn" aria-label={t('sort.title')}>
              {sorting.parameters && (sorting.sortBy === 'processVariable' || sorting.sortBy === 'taskVariable')
                ? sorting.parameters.variable
                : t(SORT_OPTIONS.find((option) => option.sortBy === sorting.sortBy)?.label ?? 'sort.created')}
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <MenuContent>
                <DropdownMenu.Label className="menu__label">{t('sort.title')}</DropdownMenu.Label>
                <DropdownMenu.RadioGroup
                  value={sorting.sortBy}
                  onValueChange={(value) =>
                    props.onSort({ sortBy: value as TaskSortBy, sortOrder: sorting.sortOrder })
                  }
                >
                  {SORT_OPTIONS.map((option) => (
                    <DropdownMenu.RadioItem
                      key={option.sortBy}
                      value={option.sortBy}
                      className="menu__item"
                    >
                      <span className="menu__check">
                        <DropdownMenu.ItemIndicator>
                          <Check aria-hidden size={16} />
                        </DropdownMenu.ItemIndicator>
                      </span>
                      {t(option.label)}
                    </DropdownMenu.RadioItem>
                  ))}
                </DropdownMenu.RadioGroup>
                <DropdownMenu.Separator className="menu__separator" />
                <DropdownMenu.Item className="menu__item" onSelect={() => setSortingByVariable(true)}>
                  <span className="menu__check">
                    {(sorting.sortBy === 'processVariable' || sorting.sortBy === 'taskVariable') && <Check aria-hidden size={16} />}
                  </span>
                  {t('sort.variable')}
                </DropdownMenu.Item>
              </MenuContent>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
          {sortingByVariable && (
            <VariableSortDialog
              open
              current={sorting}
              onOpenChange={setSortingByVariable}
              onSort={props.onSort}
            />
          )}
          <IconButton
            icon={sorting.sortOrder === 'asc' ? ArrowUpNarrowWide : ArrowDownWideNarrow}
            label={sorting.sortOrder === 'asc' ? t('sort.ascending') : t('sort.descending')}
            onClick={() =>
              props.onSort({ ...sorting, sortOrder: sorting.sortOrder === 'asc' ? 'desc' : 'asc' })
            }
          />
          {props.tools}
          <IconButton
            icon={RefreshCw}
            label={t('action.refresh')}
            shortcut="R"
            className={cx(props.fetching && 'is-spinning')}
            onClick={props.onRefresh}
          />
        </div>
      </div>

      {props.notice}

      {checkedTasks.length > 0 && (
        <BulkTaskBar
          tasks={checkedTasks}
          onClear={() => props.onChecked(new Set())}
          onKeep={(ids) => props.onChecked(new Set(ids))}
        />
      )}

      <div className="tasks__body">
        {props.error ? (
          <ErrorState error={props.error} onRetry={props.onRefresh} />
        ) : !page ? (
          <Loading label={t('tasks.loading')}>
            <Skeleton lines={6} className="skeleton--cards" />
          </Loading>
        ) : page.tasks.length === 0 ? (
          narrowed ? (
            <NoMatch
              title={t('tasks.noMatch')}
              onClear={() => {
                props.onCriteria([]);
                props.onOnlyMine(false);
              }}
            />
          ) : (
            <EmptyState title={t('tasks.empty')}>{t('tasks.emptyHint')}</EmptyState>
          )
        ) : (
          <ul
            ref={ref}
            className={cx('task-list', props.updating && 'task-list--updating')}
            role="listbox"
            aria-label={t('tasks.title')}
            aria-busy={props.updating || undefined}
          >
            {page.tasks.map((task) => (
              <TaskRow
                key={task.id}
                task={task}
                selected={task.id === props.selectedId}
                highlighted={task.id === props.highlightedId}
                variableLabels={props.variableLabels}
                undefinedVariables={props.undefinedVariables}
                processFields={props.processFields}
                checked={props.checked.has(task.id)}
                onCheck={(on) => toggle(task.id, on)}
                onOpen={props.onOpen}
                onHighlight={props.onHighlight}
                created={(() => {
                  const date = parseEngineDate(task.created);
                  return date ? { relative: relative(date), absolute: dateTime(date) } : null;
                })()}
              />
            ))}
          </ul>
        )}
      </div>

      {page && page.count > PAGE_SIZE && (
        <nav className="pager" aria-label={t('pager.title')}>
          <IconButton
            icon={ChevronLeft}
            label={t('pager.previous')}
            disabled={props.pageIndex === 0}
            onClick={() => props.onPage(props.pageIndex - 1)}
          />
          <span className="pager__text">
            {t('pager.range', {
              from: first + 1,
              to: Math.min(first + PAGE_SIZE, page.count),
              total: page.count,
            })}
          </span>
          <IconButton
            icon={ChevronRight}
            label={t('pager.next')}
            disabled={props.pageIndex >= pages - 1}
            onClick={() => props.onPage(props.pageIndex + 1)}
          />
        </nav>
      )}
    </div>
  );
});

function TaskRow({
  task,
  selected,
  highlighted,
  variableLabels,
  undefinedVariables,
  processFields,
  created,
  checked,
  onCheck,
  onOpen,
  onHighlight,
}: {
  task: TaskListItem;
  selected: boolean;
  highlighted: boolean;
  variableLabels: Record<string, string>;
  undefinedVariables?: string[];
  processFields?: ProcessCardFields;
  created: { relative: string; absolute: string } | null;
  checked: boolean;
  onCheck: (checked: boolean) => void;
  onOpen: (taskId: string) => void;
  onHighlight: (taskId: string) => void;
}) {
  const { t } = useI18n();
  // the fields of the process come first, in the order chosen for it
  const own = processFields ? fieldLabels(processFields, processKeyOf(task)) : {};
  const labels = { ...variableLabels, ...own };
  const variables = cardVariablesOf(task, variableLabels, own, undefinedVariables);
  return (
    <li
      role="option"
      id={`task-${task.id}`}
      aria-selected={selected}
      aria-checked={checked}
      tabIndex={highlighted ? 0 : -1}
      className={cx('task-row', selected && 'is-selected', highlighted && 'is-highlighted')}
      onClick={() => onOpen(task.id)}
      onFocus={() => onHighlight(task.id)}
      aria-keyshortcuts="Enter Space"
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          onOpen(task.id);
        } else if (event.key === ' ') {
          // the check box stays out of the tab order of the list; Space checks the focused row
          event.preventDefault();
          onCheck(!checked);
        }
      }}
    >
      <div className="task-row__head">
        <input
          type="checkbox"
          className="task-row__check"
          tabIndex={-1}
          aria-label={t('ops.tasks.check', { name: task.name ?? task.id })}
          checked={checked}
          onClick={(event) => event.stopPropagation()}
          onChange={(event) => onCheck(event.target.checked)}
        />
        <span className="task-row__name">{task.name ?? task.taskDefinitionKey ?? task.id}</span>
        {created && (
          <span className="task-row__created" title={created.absolute}>
            {created.relative}
          </span>
        )}
      </div>
      {(task.processDefinitionName || task.businessKey) && (
        <div className="task-row__process">
          {task.businessKey && (
            <span className="task-row__case">
              <span className="sr-only">{t('process.businessKey')}: </span>
              {task.businessKey}
            </span>
          )}
          {task.processDefinitionName}
        </div>
      )}
      {variables.length > 0 && (
        <dl className="task-row__variables">
          {variables.map((variable) => (
            <div key={variable.name}>
              <dt>{labels[variable.name]}</dt>
              <dd>{displayValue(variable.value)}</dd>
            </div>
          ))}
        </dl>
      )}
      <div className="task-row__meta">
        <span className={cx('assignee', !task.assignee && 'assignee--none')}>
          <User aria-hidden size={13} />
          {task.assignee ? (task.assigneeName ?? task.assignee) : t('task.unassigned')}
        </span>
        <DueBadge due={task.due} created={task.created} />
        <FollowUpBadge followUp={task.followUp} />
        {task.priority !== 50 && (
          <span
            className={cx('priority', task.priority > 50 && 'priority--high')}
            title={t('task.priority')}
          >
            <Flag aria-hidden size={12} />
            <span className="sr-only">{t('task.priority')} </span>
            {task.priority}
          </span>
        )}
      </div>
    </li>
  );
}
