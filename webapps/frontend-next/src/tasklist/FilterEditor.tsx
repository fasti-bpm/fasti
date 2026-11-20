/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import { Plus, Trash2, Users, X } from 'lucide-react';
import {
  canShareFilters,
  deleteTaskFilter,
  getFilterAccess,
  getFilterShares,
  getTaskFilter,
  saveTaskFilter,
  shareFilter,
  unshareFilter,
  type FilterShare,
  type TaskFilterDetails,
} from '../api/engine';
import type { FilterVariable } from '../api/types';
import { useSession } from '../app/session';
import { ConfirmDialog, Checkbox } from '../cockpit/common';
import { useI18n, type MessageKey } from '../i18n';
import { Button, Dialog, ErrorState, Field, IconButton, Loading, Notice, Skeleton, cx } from '../ui';
import { useToast } from '../ui/toast';
import {
  CRITERIA,
  EXPRESSIONS,
  VARIABLE_OPERATORS,
  VARIABLE_SCOPES,
  criterionError,
  criterionOf,
  fromQuery,
  toQuery,
  type CriterionDefinition,
  type CriterionRow,
  type EditableQuery,
  type VariableCriterionRow,
} from './filterCriteria';
import { IdentityInput } from './IdentityInput';

const COLOR = /^#[0-9a-f]{6}$/i;
const DEFAULT_COLOR = '#555555';

const GROUPS: { group: CriterionDefinition['group']; label: MessageKey }[] = [
  { group: 'process', label: 'filterEdit.group.process' },
  { group: 'task', label: 'filterEdit.group.task' },
  { group: 'user', label: 'filterEdit.group.user' },
  { group: 'date', label: 'filterEdit.group.date' },
  { group: 'other', label: 'filterEdit.group.other' },
];

const SCOPE_LABELS: Record<VariableCriterionRow['scope'], MessageKey> = {
  processVariables: 'filterEdit.scope.process',
  taskVariables: 'filterEdit.scope.task',
  caseInstanceVariables: 'filterEdit.scope.case',
};

type Tab = 'general' | 'criteria' | 'variables' | 'sharing';

interface Draft {
  name: string;
  description: string;
  priority: string;
  color: string;
  refresh: boolean;
  showUndefinedVariable: boolean;
  columns: FilterVariable[];
  query: EditableQuery;
}

function toDraft(filter: TaskFilterDetails | undefined): Draft {
  const properties = filter?.properties ?? {};
  return {
    name: filter?.name ?? '',
    description: properties.description ?? '',
    priority: String(properties.priority ?? 0),
    color: properties.color && COLOR.test(properties.color) ? properties.color : DEFAULT_COLOR,
    refresh: properties.refresh ?? true,
    showUndefinedVariable: properties.showUndefinedVariable ?? false,
    columns: (properties.variables ?? []).map((variable) => ({ name: variable.name, label: variable.label ?? '' })),
    query: fromQuery(filter?.query),
  };
}

/** A grant that is being added: a user or group id, or every user (`*`). */
interface NewShare {
  kind: 'user' | 'group';
  id: string;
}

export interface FilterEditorProps {
  /** the filter to edit; `null` creates a new one */
  filterId: string | null;
  onClose: () => void;
  onSaved: (filterId: string) => void;
  onDeleted: (filterId: string) => void;
}

/** Creates, edits or deletes a task filter, including who it is shared with. */
export function FilterEditor({ filterId, onClose, onSaved, onDeleted }: FilterEditorProps) {
  const { t } = useI18n();
  const filter = useQuery({
    queryKey: ['filter-edit', filterId],
    queryFn: () => getTaskFilter(filterId ?? ''),
    enabled: !!filterId,
    gcTime: 0,
  });
  const access = useQuery({
    queryKey: ['filter-edit', filterId, 'access'],
    queryFn: () => getFilterAccess(filterId ?? ''),
    enabled: !!filterId,
    gcTime: 0,
  });
  const shares = useQuery({
    queryKey: ['filter-edit', filterId, 'shares'],
    queryFn: () => getFilterShares(filterId ?? ''),
    enabled: !!filterId,
    gcTime: 0,
  });
  const shareable = useQuery({ queryKey: ['filter-edit', 'shareable'], queryFn: canShareFilters, staleTime: 60_000 });

  const loading = !!filterId && (filter.isPending || access.isPending || shares.isPending);
  const error = filter.error ?? access.error ?? shares.error;

  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={filterId ? t('filterEdit.editTitle') : t('filterEdit.createTitle')}
      wide
    >
      {error ? (
        <ErrorState error={error} />
      ) : loading || shareable.isPending ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={6} />
        </Loading>
      ) : (
        <FilterForm
          filter={filterId ? filter.data : undefined}
          canUpdate={!filterId || (access.data?.has('update') ?? false)}
          canDelete={!!filterId && (access.data?.has('delete') ?? false)}
          shares={filterId ? (shares.data ?? null) : []}
          canShare={shareable.data ?? false}
          onClose={onClose}
          onSaved={onSaved}
          onDeleted={onDeleted}
        />
      )}
    </Dialog>
  );
}

function FilterForm({
  filter,
  canUpdate,
  canDelete,
  shares,
  canShare,
  onClose,
  onSaved,
  onDeleted,
}: {
  filter: TaskFilterDetails | undefined;
  canUpdate: boolean;
  canDelete: boolean;
  /** `null`: the user may not read the authorizations of the filter */
  shares: FilterShare[] | null;
  canShare: boolean;
  onClose: () => void;
  onSaved: (filterId: string) => void;
  onDeleted: (filterId: string) => void;
}) {
  const { t } = useI18n();
  const session = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('general');
  const [draft, setDraft] = useState<Draft>(() => toDraft(filter));
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const owner = filter?.owner ?? session.userId;
  // the owner's own authorization is created by the engine and not offered for removal
  const existing = useMemo(
    () => (shares ?? []).filter((share) => !(share.type === 1 && share.userId === owner)),
    [shares, owner],
  );
  const [removed, setRemoved] = useState<ReadonlySet<string>>(new Set());
  const [added, setAdded] = useState<NewShare[]>([]);
  const everyoneShare = existing.find((share) => share.type === 0 && share.userId === '*');
  const [everyone, setEveryone] = useState(!!everyoneShare);
  const sharingEditable = canUpdate && canShare && shares !== null;

  const update = (changes: Partial<Draft>) => setDraft((current) => ({ ...current, ...changes }));
  const updateQuery = (changes: Partial<EditableQuery>) =>
    setDraft((current) => ({ ...current, query: { ...current.query, ...changes } }));

  const nameError = draft.name.trim() ? null : t('validation.required');
  const priorityError = /^-?\d+$/.test(draft.priority.trim()) ? null : t('validation.integer');
  const criteriaInvalid = draft.query.criteria.some((row) => criterionError(row));
  const columnsInvalid = draft.columns.some((column) => !column.name.trim());
  const invalid = !!nameError || !!priorityError || criteriaInvalid || columnsInvalid;

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (invalid) {
      setTab(nameError || priorityError ? 'general' : criteriaInvalid ? 'criteria' : 'variables');
      return;
    }
    setBusy(true);
    try {
      const saved = await saveTaskFilter(filter?.id ?? null, {
        name: draft.name.trim(),
        owner,
        query: toQuery(draft.query),
        properties: {
          ...filter?.properties,
          description: draft.description.trim(),
          priority: Number(draft.priority.trim()),
          color: draft.color,
          refresh: draft.refresh,
          showUndefinedVariable: draft.showUndefinedVariable,
          variables: draft.columns.map((column) => ({
            name: column.name.trim(),
            label: column.label?.trim() || column.name.trim(),
          })),
        },
      });
      const id = filter?.id ?? (saved as TaskFilterDetails).id;
      if (sharingEditable) {
        const toRemove = existing.filter(
          (share) => removed.has(share.id) || (share === everyoneShare && !everyone),
        );
        for (const share of toRemove) {
          await unshareFilter(share.id);
        }
        if (everyone && !everyoneShare) {
          await shareFilter(id, { userId: '*' });
        }
        for (const share of added) {
          await shareFilter(id, share.kind === 'user' ? { userId: share.id } : { groupId: share.id });
        }
      }
      toast.show({ title: t('filterEdit.saved', { name: draft.name.trim() }) });
      onSaved(id);
    } catch (error) {
      toast.error(error, t('filterEdit.saveFailed'));
    } finally {
      setBusy(false);
      void queryClient.invalidateQueries({ queryKey: ['filters'] });
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    }
  };

  const remove = async () => {
    if (!filter) {
      return;
    }
    setBusy(true);
    try {
      await deleteTaskFilter(filter.id);
      toast.show({ title: t('filterEdit.deleted', { name: filter.name }) });
      setConfirmDelete(false);
      onDeleted(filter.id);
    } catch (error) {
      toast.error(error, t('filterEdit.deleteFailed'));
    } finally {
      setBusy(false);
      void queryClient.invalidateQueries({ queryKey: ['filters'] });
    }
  };

  const tabs: { name: Tab; label: string; invalid?: boolean }[] = [
    { name: 'general', label: t('filterEdit.tab.general'), invalid: submitted && (!!nameError || !!priorityError) },
    { name: 'criteria', label: t('filterEdit.tab.criteria'), invalid: submitted && criteriaInvalid },
    { name: 'variables', label: t('filterEdit.tab.variables'), invalid: submitted && columnsInvalid },
    { name: 'sharing', label: t('filterEdit.tab.sharing') },
  ];

  return (
    <form className="filter-edit" onSubmit={(event) => void save(event)} noValidate>
      {!canUpdate && <Notice>{t('filterEdit.readOnly')}</Notice>}
      <Tabs.Root className="ctabs" value={tab} onValueChange={(next) => setTab(next as Tab)}>
        <Tabs.List className="ctabs__list" aria-label={t('filterEdit.tabs')}>
          {tabs.map((entry) => (
            <Tabs.Trigger key={entry.name} className={cx('ctabs__tab', entry.invalid && 'is-invalid')} value={entry.name}>
              {entry.label}
              {entry.invalid && <span className="sr-only"> ({t('filterEdit.tabInvalid')})</span>}
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        <Tabs.Content className="ctabs__content filter-edit__tab" value="general">
          <fieldset className="filter-edit__fields" disabled={!canUpdate}>
            <Field label={t('filterEdit.name')} required error={submitted ? nameError : null}>
              {(props) => (
                <input
                  {...props}
                  className="input"
                  autoFocus
                  value={draft.name}
                  maxLength={255}
                  onChange={(event) => update({ name: event.target.value })}
                />
              )}
            </Field>
            <Field label={t('filterEdit.description')}>
              {(props) => (
                <input
                  {...props}
                  className="input"
                  value={draft.description}
                  onChange={(event) => update({ description: event.target.value })}
                />
              )}
            </Field>
            <div className="filter-edit__row">
              <Field
                label={t('filterEdit.priority')}
                hint={t('filterEdit.priorityHint')}
                error={submitted ? priorityError : null}
              >
                {(props) => (
                  <input
                    {...props}
                    className="input"
                    inputMode="numeric"
                    value={draft.priority}
                    onChange={(event) => update({ priority: event.target.value })}
                  />
                )}
              </Field>
              <Field label={t('filterEdit.color')}>
                {(props) => (
                  <input
                    {...props}
                    className="input filter-edit__color"
                    type="color"
                    value={draft.color}
                    onChange={(event) => update({ color: event.target.value })}
                  />
                )}
              </Field>
            </div>
            <Checkbox
              label={t('filterEdit.refresh')}
              hint={t('filterEdit.refreshHint')}
              checked={draft.refresh}
              disabled={!canUpdate}
              onChange={(refresh) => update({ refresh })}
            />
          </fieldset>
        </Tabs.Content>

        <Tabs.Content className="ctabs__content filter-edit__tab" value="criteria">
          <CriteriaEditor
            query={draft.query}
            onChange={updateQuery}
            disabled={!canUpdate}
            showErrors={submitted}
          />
        </Tabs.Content>

        <Tabs.Content className="ctabs__content filter-edit__tab" value="variables">
          <ColumnsEditor
            columns={draft.columns}
            onChange={(columns) => update({ columns })}
            showUndefined={draft.showUndefinedVariable}
            onShowUndefined={(showUndefinedVariable) => update({ showUndefinedVariable })}
            disabled={!canUpdate}
            showErrors={submitted}
          />
        </Tabs.Content>

        <Tabs.Content className="ctabs__content filter-edit__tab" value="sharing">
          {shares === null ? (
            <Notice>{t('filterEdit.sharing.unreadable')}</Notice>
          ) : (
            <SharingEditor
              existing={existing.filter((share) => share !== everyoneShare && !removed.has(share.id))}
              added={added}
              everyone={everyone}
              disabled={!sharingEditable}
              onEveryone={setEveryone}
              onRemoveExisting={(id) => setRemoved((current) => new Set([...current, id]))}
              onAdd={(share) => setAdded((current) => [...current, share])}
              onRemoveAdded={(index) => setAdded((current) => current.filter((_, at) => at !== index))}
            />
          )}
          {shares !== null && !canShare && <p className="filter-edit__hint">{t('filterEdit.sharing.noPermission')}</p>}
        </Tabs.Content>
      </Tabs.Root>

      <div className="filter-edit__actions">
        {canDelete && (
          <Button variant="danger-ghost" icon={Trash2} onClick={() => setConfirmDelete(true)} disabled={busy}>
            {t('filterEdit.delete')}
          </Button>
        )}
        <span className="filter-edit__spacer" />
        <Button onClick={onClose}>{t('action.cancel')}</Button>
        <Button type="submit" variant="primary" busy={busy} disabled={!canUpdate}>
          {t('action.save')}
        </Button>
      </div>

      {filter && (
        <ConfirmDialog
          open={confirmDelete}
          onOpenChange={setConfirmDelete}
          title={t('filterEdit.deleteTitle')}
          text={t('filterEdit.deleteText', { name: filter.name })}
          confirmLabel={t('filterEdit.delete')}
          danger
          busy={busy}
          onConfirm={() => void remove()}
        />
      )}
    </form>
  );
}

function CriterionValue({
  row,
  definition,
  onChange,
  invalid,
  describedBy,
  label,
}: {
  row: CriterionRow;
  definition: CriterionDefinition;
  onChange: (value: string) => void;
  invalid: boolean;
  describedBy?: string;
  label: string;
}) {
  const { t } = useI18n();
  const listId = useId();
  if (definition.kind === 'flag') {
    return <span className="filter-edit__flag">{t('filterEdit.flagSet')}</span>;
  }
  if (definition.kind === 'delegation') {
    return (
      <select
        className="input"
        aria-label={label}
        value={row.value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{t('form.choose')}</option>
        <option value="PENDING">{t('filterEdit.delegation.pending')}</option>
        <option value="RESOLVED">{t('filterEdit.delegation.resolved')}</option>
      </select>
    );
  }
  const placeholder =
    definition.kind === 'date'
      ? t('filterEdit.datePlaceholder')
      : definition.kind === 'list'
        ? t('filterEdit.listPlaceholder')
        : undefined;
  return (
    <>
      <input
        className="input"
        aria-label={label}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        spellCheck={false}
        autoComplete="off"
        placeholder={placeholder}
        list={definition.expression ? listId : undefined}
        value={row.value}
        onChange={(event) => onChange(event.target.value)}
      />
      {definition.expression && (
        <datalist id={listId}>
          {EXPRESSIONS.filter((expression) =>
            definition.kind === 'date'
              ? !expression.includes('currentUser')
              : definition.kind === 'list'
                ? expression === '${currentUserGroups()}'
                : expression === '${currentUser()}',
          ).map((expression) => (
            <option key={expression} value={expression} />
          ))}
        </datalist>
      )}
    </>
  );
}

function CriteriaEditor({
  query,
  onChange,
  disabled,
  showErrors,
}: {
  query: EditableQuery;
  onChange: (changes: Partial<EditableQuery>) => void;
  disabled: boolean;
  showErrors: boolean;
}) {
  const { t } = useI18n();
  const idPrefix = useId();
  const used = new Set(query.criteria.map((row) => row.key));
  const unused = CRITERIA.filter((criterion) => !used.has(criterion.key));
  // the engine stores empty lists such as `orQueries: []`; they are no criteria worth naming
  const restKeys = Object.entries(query.rest)
    .filter(([, value]) => !(Array.isArray(value) && value.length === 0))
    .map(([key]) => key);

  const setRow = (index: number, row: CriterionRow) =>
    onChange({ criteria: query.criteria.map((current, at) => (at === index ? row : current)) });
  const setVariable = (index: number, row: Partial<VariableCriterionRow>) =>
    onChange({ variables: query.variables.map((current, at) => (at === index ? { ...current, ...row } : current)) });

  return (
    <fieldset className="filter-edit__fields" disabled={disabled}>
      <p className="filter-edit__hint">{t('filterEdit.criteriaHint')}</p>
      {query.criteria.length === 0 && <p className="filter-edit__empty">{t('filterEdit.criteriaEmpty')}</p>}
      <ul className="filter-edit__list" aria-label={t('filterEdit.tab.criteria')}>
        {query.criteria.map((row, index) => {
          const definition = criterionOf(row.key);
          if (!definition) {
            return null;
          }
          const error = showErrors ? criterionError(row) : null;
          const errorId = `${idPrefix}-error-${index}`;
          const label = t(definition.label);
          return (
            <li key={row.key} className="filter-edit__criterion">
              <select
                className="input"
                aria-label={t('filterEdit.criterion')}
                value={row.key}
                onChange={(event) => {
                  const next = criterionOf(event.target.value);
                  setRow(index, { key: event.target.value, value: next?.kind === 'flag' ? 'true' : row.value });
                }}
              >
                {GROUPS.map((group) => (
                  <optgroup key={group.group} label={t(group.label)}>
                    {CRITERIA.filter(
                      (criterion) =>
                        criterion.group === group.group && (criterion.key === row.key || !used.has(criterion.key)),
                    ).map((criterion) => (
                      <option key={criterion.key} value={criterion.key}>
                        {t(criterion.label)}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </select>
              <div className="filter-edit__value">
                <CriterionValue
                  row={row}
                  definition={definition}
                  label={label}
                  invalid={!!error}
                  describedBy={error ? errorId : undefined}
                  onChange={(value) => setRow(index, { ...row, value })}
                />
                {error && (
                  <p className="field__error" id={errorId} role="alert">
                    {t(error)}
                  </p>
                )}
              </div>
              <IconButton
                icon={X}
                label={t('filterEdit.removeCriterion', { name: label })}
                onClick={() => onChange({ criteria: query.criteria.filter((_, at) => at !== index) })}
              />
            </li>
          );
        })}
      </ul>
      <div>
        <Button
          size="sm"
          icon={Plus}
          disabled={unused.length === 0}
          onClick={() => {
            const next = unused[0];
            if (next) {
              onChange({ criteria: [...query.criteria, { key: next.key, value: next.kind === 'flag' ? 'true' : '' }] });
            }
          }}
        >
          {t('filterEdit.addCriterion')}
        </Button>
      </div>

      <h3 className="filter-edit__subtitle">{t('filterEdit.variableCriteria')}</h3>
      {query.variables.length === 0 && <p className="filter-edit__empty">{t('filterEdit.variableCriteriaEmpty')}</p>}
      <ul className="filter-edit__list" aria-label={t('filterEdit.variableCriteria')}>
        {query.variables.map((row, index) => (
          <li key={index} className="filter-edit__criterion filter-edit__criterion--variable">
            <select
              className="input"
              aria-label={t('filterEdit.scope')}
              value={row.scope}
              onChange={(event) => setVariable(index, { scope: event.target.value as VariableCriterionRow['scope'] })}
            >
              {VARIABLE_SCOPES.map((scope) => (
                <option key={scope} value={scope}>
                  {t(SCOPE_LABELS[scope])}
                </option>
              ))}
            </select>
            <input
              className="input"
              aria-label={t('search.variableName')}
              placeholder={t('search.variableName')}
              aria-invalid={showErrors && !row.name.trim() ? true : undefined}
              spellCheck={false}
              value={row.name}
              onChange={(event) => setVariable(index, { name: event.target.value })}
            />
            <select
              className="input filter-edit__operator"
              aria-label={t('filterEdit.operator')}
              value={row.operator}
              onChange={(event) =>
                setVariable(index, { operator: event.target.value as VariableCriterionRow['operator'] })
              }
            >
              {VARIABLE_OPERATORS.map((entry) => (
                <option key={entry.operator} value={entry.operator}>
                  {entry.symbol}
                </option>
              ))}
            </select>
            <input
              className="input"
              aria-label={t('form.variable.value')}
              placeholder={t('form.variable.value')}
              value={row.value}
              onChange={(event) => setVariable(index, { value: event.target.value })}
            />
            <IconButton
              icon={X}
              label={t('filterEdit.removeVariableCriterion', { name: row.name || '…' })}
              onClick={() => onChange({ variables: query.variables.filter((_, at) => at !== index) })}
            />
          </li>
        ))}
      </ul>
      <div>
        <Button
          size="sm"
          icon={Plus}
          onClick={() =>
            onChange({
              variables: [...query.variables, { scope: 'processVariables', name: '', operator: 'eq', value: '' }],
            })
          }
        >
          {t('filterEdit.addVariableCriterion')}
        </Button>
      </div>
      {restKeys.length > 0 && (
        <p className="filter-edit__hint">{t('filterEdit.otherCriteria', { keys: restKeys.join(', ') })}</p>
      )}
    </fieldset>
  );
}

function ColumnsEditor({
  columns,
  onChange,
  showUndefined,
  onShowUndefined,
  disabled,
  showErrors,
}: {
  columns: FilterVariable[];
  onChange: (columns: FilterVariable[]) => void;
  showUndefined: boolean;
  onShowUndefined: (value: boolean) => void;
  disabled: boolean;
  showErrors: boolean;
}) {
  const { t } = useI18n();
  const set = (index: number, changes: Partial<FilterVariable>) =>
    onChange(columns.map((column, at) => (at === index ? { ...column, ...changes } : column)));
  return (
    <fieldset className="filter-edit__fields" disabled={disabled}>
      <p className="filter-edit__hint">{t('filterEdit.variablesHint')}</p>
      {columns.length === 0 && <p className="filter-edit__empty">{t('filterEdit.variablesEmpty')}</p>}
      <ul className="filter-edit__list" aria-label={t('filterEdit.tab.variables')}>
        {columns.map((column, index) => (
          <li key={index} className="filter-edit__criterion filter-edit__criterion--column">
            <input
              className="input"
              aria-label={t('search.variableName')}
              placeholder={t('search.variableName')}
              aria-invalid={showErrors && !column.name.trim() ? true : undefined}
              spellCheck={false}
              value={column.name}
              onChange={(event) => set(index, { name: event.target.value })}
            />
            <input
              className="input"
              aria-label={t('filterEdit.variableLabel')}
              placeholder={t('filterEdit.variableLabel')}
              value={column.label ?? ''}
              onChange={(event) => set(index, { label: event.target.value })}
            />
            <IconButton
              icon={X}
              label={t('filterEdit.removeVariable', { name: column.name || '…' })}
              onClick={() => onChange(columns.filter((_, at) => at !== index))}
            />
          </li>
        ))}
      </ul>
      <div>
        <Button size="sm" icon={Plus} onClick={() => onChange([...columns, { name: '', label: '' }])}>
          {t('filterEdit.addVariable')}
        </Button>
      </div>
      <Checkbox
        label={t('filterEdit.showUndefined')}
        checked={showUndefined}
        disabled={disabled}
        onChange={onShowUndefined}
      />
    </fieldset>
  );
}

function SharingEditor({
  existing,
  added,
  everyone,
  disabled,
  onEveryone,
  onRemoveExisting,
  onAdd,
  onRemoveAdded,
}: {
  existing: FilterShare[];
  added: NewShare[];
  everyone: boolean;
  disabled: boolean;
  onEveryone: (value: boolean) => void;
  onRemoveExisting: (id: string) => void;
  onAdd: (share: NewShare) => void;
  onRemoveAdded: (index: number) => void;
}) {
  const { t } = useI18n();
  const [kind, setKind] = useState<NewShare['kind']>('user');
  const [id, setId] = useState('');
  const next = id.trim();
  const duplicate =
    added.some((share) => share.kind === kind && share.id === next) ||
    existing.some((share) => (kind === 'user' ? share.userId : share.groupId) === next);
  const add = () => {
    if (next && !duplicate) {
      onAdd({ kind, id: next });
      setId('');
    }
  };
  const label = (share: { kind: 'user' | 'group'; id: string }) =>
    share.kind === 'user' ? t('filterEdit.sharing.user', { id: share.id }) : t('filterEdit.sharing.group', { id: share.id });
  const entries = [
    ...existing.map((share) => ({
      key: share.id,
      text: label(share.groupId ? { kind: 'group', id: share.groupId } : { kind: 'user', id: share.userId ?? '' }),
      remove: () => onRemoveExisting(share.id),
    })),
    ...added.map((share, index) => ({
      key: `new-${index}`,
      text: label(share),
      remove: () => onRemoveAdded(index),
    })),
  ];
  return (
    <fieldset className="filter-edit__fields" disabled={disabled}>
      <p className="filter-edit__hint">{t('filterEdit.sharing.hint')}</p>
      <Checkbox
        label={t('filterEdit.sharing.everyone')}
        hint={t('filterEdit.sharing.everyoneHint')}
        checked={everyone}
        disabled={disabled}
        onChange={onEveryone}
      />
      {entries.length === 0 ? (
        <p className="filter-edit__empty">{t('filterEdit.sharing.none')}</p>
      ) : (
        <ul className="chips" aria-label={t('filterEdit.sharing.list')}>
          {entries.map((entry) => (
            <li key={entry.key} className="chip">
              <Users aria-hidden size={13} />
              {entry.text}
              {!disabled && (
                <button
                  type="button"
                  className="chip__remove"
                  aria-label={t('filterEdit.sharing.remove', { name: entry.text })}
                  onClick={entry.remove}
                >
                  <X aria-hidden size={13} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className="filter-edit__share">
        <select
          className="input"
          aria-label={t('filterEdit.sharing.kind')}
          value={kind}
          onChange={(event) => setKind(event.target.value as NewShare['kind'])}
        >
          <option value="user">{t('filterEdit.sharing.kind.user')}</option>
          <option value="group">{t('filterEdit.sharing.kind.group')}</option>
        </select>
        <IdentityInput
          kind={kind}
          aria-label={kind === 'user' ? t('task.assignee.userId') : t('task.groups.add')}
          aria-invalid={duplicate || undefined}
          value={id}
          onChange={setId}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              add();
            }
          }}
        />
        <Button size="sm" icon={Plus} disabled={!next || duplicate} onClick={add}>
          {t('action.add')}
        </Button>
      </div>
      {duplicate && <p className="field__error">{t('validation.duplicate')}</p>}
    </fieldset>
  );
}
