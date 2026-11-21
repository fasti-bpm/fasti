/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { formatJson } from '../lib/json';
import { useEffect, useMemo, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Braces, Check, Download, Eye, Pencil, Plus, Trash2, Upload, X } from 'lucide-react';
import {
  addCaseFileVariable,
  addCaseVariable,
  addInstanceFileVariable,
  addInstanceVariable,
  deleteVariable,
  getVariables,
  updateVariable,
  uploadVariableData,
  variableDataUrl,
  type VariableInstance,
} from '../api/cockpit';
import type { SortOrder, VariableValue } from '../api/types';
import { convertInput, jsonProblem } from '../forms/variables';
import { useI18n, type MessageKey, type Translate } from '../i18n';
import { parseEngineDate, toDateTimeInput } from '../lib/dates';
import { Button, Dialog, EmptyState, Field, IconButton, NoMatch, Notice } from '../ui';
import { COCKPIT, useCockpitAction, useRefreshInterval } from './common';
import { ConfirmDialog, SearchInput } from '../ui/controls';
import { Time } from '../ui/format';
import { pageOf, useDebounced, useRemembered, useUrlState } from '../ui/hooks';
import { DataTable, PAGE_SIZE, Pager, type Column } from '../ui/table';

const INLINE_TYPES = new Set(['String', 'Boolean', 'Integer', 'Long', 'Short', 'Double', 'Date']);
const TEXT_TYPES = new Set(['Json', 'Xml']);
const NEW_TYPES = ['String', 'Boolean', 'Integer', 'Long', 'Double', 'Date', 'Json', 'Xml', 'File', 'Null'] as const;
const PREVIEW_LENGTH = 96;

export type VariableKind = 'inline' | 'text' | 'object' | 'binary' | 'null';

export function variableKind(variable: Pick<VariableInstance, 'type'>): VariableKind {
  if (INLINE_TYPES.has(variable.type)) return 'inline';
  if (TEXT_TYPES.has(variable.type)) return 'text';
  if (variable.type === 'Bytes' || variable.type === 'File') return 'binary';
  if (variable.type === 'Null') return 'null';
  return 'object';
}

/**
 * An object variable serialized as JSON can be changed as text; other
 * serialization formats (e.g. Java serialization) stay read-only.
 */
export function isJsonObject(variable: Pick<VariableInstance, 'type' | 'valueInfo'>): boolean {
  return (
    variableKind(variable) === 'object' && variable.valueInfo?.serializationDataFormat === 'application/json'
  );
}

/** The value as text: what an input shows and what a viewer prints. */
export function valueText(variable: Pick<VariableInstance, 'type' | 'value'>): string {
  const { value } = variable;
  if (value === null || value === undefined) {
    return '';
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** Text for the viewer: JSON is indented, everything else is shown as it is. */
export function prettyValue(variable: Pick<VariableInstance, 'type' | 'value' | 'valueInfo'>): string {
  const text = valueText(variable);
  const json =
    variable.type === 'Json' || variable.valueInfo?.serializationDataFormat === 'application/json';
  if (json) {
    try {
      return formatJson(text);
    } catch {
      return text;
    }
  }
  return text;
}

/** Checks text that is stored as a structured value; `null` if it is well-formed. */
export function validateText(type: string, text: string): MessageKey | null {
  if (type === 'Json') {
    try {
      JSON.parse(text);
      return null;
    } catch {
      return 'cockpit.variable.invalidJson';
    }
  }
  if (type === 'Xml') {
    const parsed = new DOMParser().parseFromString(text, 'application/xml');
    return parsed.querySelector('parsererror') || !text.trim() ? 'cockpit.variable.invalidXml' : null;
  }
  return null;
}

/** The value to store for what the user typed, or the message that explains what is wrong. */
export function toVariableValue(
  type: string,
  raw: string,
): { ok: true; value: VariableValue } | { ok: false; error: MessageKey } {
  if (type === 'Null') {
    return { ok: true, value: { value: null, type: 'Null' } };
  }
  if (TEXT_TYPES.has(type)) {
    const error = validateText(type, raw);
    return error ? { ok: false, error } : { ok: true, value: { value: raw, type } };
  }
  const converted = convertInput(type, raw);
  return converted.ok
    ? { ok: true, value: { value: converted.value, type } }
    : { ok: false, error: converted.error };
}

function inputValue(variable: VariableInstance): string {
  if (variable.type === 'Date') {
    return toDateTimeInput(parseEngineDate(valueText(variable)));
  }
  return valueText(variable);
}

function ValueInput({
  type,
  value,
  onChange,
  onKeyDown,
  label,
  autoFocus,
  invalid,
  describedBy,
}: {
  type: string;
  value: string;
  onChange: (value: string) => void;
  onKeyDown?: (event: KeyboardEvent) => void;
  label: string;
  autoFocus?: boolean;
  invalid?: boolean;
  /** the id of the error that explains the value */
  describedBy?: string;
}) {
  const { t } = useI18n();
  const shared = {
    className: 'input',
    'aria-label': label,
    'aria-invalid': invalid || undefined,
    'aria-describedby': describedBy,
    autoFocus,
    onKeyDown,
  };
  if (type === 'Boolean') {
    return (
      <select {...shared} value={value === 'true' ? 'true' : 'false'} onChange={(event) => onChange(event.target.value)}>
        <option value="true">{t('value.true')}</option>
        <option value="false">{t('value.false')}</option>
      </select>
    );
  }
  if (type === 'Null') {
    return <input {...shared} value="null" readOnly />;
  }
  if (TEXT_TYPES.has(type)) {
    return (
      <textarea
        {...shared}
        className="input input--code"
        rows={8}
        spellCheck={false}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={undefined}
      />
    );
  }
  return (
    <input
      {...shared}
      type={type === 'Date' ? 'datetime-local' : type === 'String' ? 'text' : 'number'}
      step={type === 'Double' ? 'any' : 1}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

function ValueCell({ variable, onView, t }: { variable: VariableInstance; onView: () => void; t: Translate }) {
  if (variable.errorMessage) {
    return <span className="value value--error">{variable.errorMessage}</span>;
  }
  switch (variableKind(variable)) {
    case 'null':
      return <span className="muted mono">null</span>;
    case 'binary': {
      const filename = variable.valueInfo?.filename;
      return (
        <span className="value">
          <span className="muted">
            {typeof filename === 'string' ? filename : t('cockpit.variable.binary')}
          </span>
          <a className="link link--subtle" href={variableDataUrl(variable.id)} download>
            <Download aria-hidden size={13} />
            {t('cockpit.download')}
          </a>
        </span>
      );
    }
    default: {
      if (variable.type === 'Boolean') {
        return <span>{variable.value === true ? t('value.true') : t('value.false')}</span>;
      }
      if (variable.type === 'Date') {
        return <Time value={valueText(variable)} />;
      }
      const text = valueText(variable);
      const structured = variableKind(variable) !== 'inline';
      const long = text.length > PREVIEW_LENGTH || text.includes('\n');
      return (
        <span className="value">
          <span className={structured ? 'value__text mono' : 'value__text'}>
            {long ? `${text.slice(0, PREVIEW_LENGTH).replace(/\s+/g, ' ')}…` : text}
          </span>
          {(long || structured) && (
            <button type="button" className="link link--subtle" onClick={onView}>
              <Eye aria-hidden size={13} />
              {t('cockpit.variable.view')}
            </button>
          )}
        </span>
      );
    }
  }
}

/** Shows a long or structured value as plain text; nothing of it is interpreted. */
function ViewDialog({ variable, onClose }: { variable: VariableInstance; onClose: () => void }) {
  const { t } = useI18n();
  const objectType = variable.valueInfo?.objectTypeName;
  const format = variable.valueInfo?.serializationDataFormat;
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={variable.name} wide>
      <dl className="facts">
        <div>
          <dt>{t('form.variable.type')}</dt>
          <dd>{variable.type}</dd>
        </div>
        {typeof objectType === 'string' && (
          <div>
            <dt>{t('cockpit.variable.objectType')}</dt>
            <dd className="mono">{objectType}</dd>
          </div>
        )}
        {typeof format === 'string' && (
          <div>
            <dt>{t('cockpit.variable.format')}</dt>
            <dd className="mono">{format}</dd>
          </div>
        )}
      </dl>
      <pre className="code" tabIndex={0}>
        {prettyValue(variable)}
      </pre>
    </Dialog>
  );
}

function TextEditDialog({ variable, onClose }: { variable: VariableInstance; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [text, setText] = useState(() => prettyValue(variable));
  const [busy, setBusy] = useState(false);
  const json = isJsonObject(variable) || variable.type === 'Json';
  const error = validateText(json ? 'Json' : variable.type, text);
  const where = json && error ? jsonProblem(text) : null;
  const message = error ? (where ? t('cockpit.variable.jsonAt', { line: where.line, column: where.column }) : t(error)) : null;
  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.variable.editTitle', { name: variable.name })}
      wide
    >
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          if (error) return;
          setBusy(true);
          void run(
            () => updateVariable(variable, { value: text, type: variable.type, valueInfo: variable.valueInfo ?? {} }),
            { success: t('cockpit.variable.saved', { name: variable.name }), failure: t('cockpit.variable.saveFailed') },
          ).then((ok) => {
            setBusy(false);
            if (ok) onClose();
          });
        }}
      >
        {isJsonObject(variable) && typeof variable.valueInfo?.objectTypeName === 'string' && (
          <p className="confirm__text">
            {t('cockpit.variable.objectJsonHint', { type: variable.valueInfo.objectTypeName })}
          </p>
        )}
        <Field label={`${t('form.variable.value')} (${variable.type})`} error={message}>
          {(props) => (
            <textarea
              {...props}
              className="input input--code"
              rows={14}
              spellCheck={false}
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
          )}
        </Field>
        <div className="confirm__actions">
          {json && (
            <Button
              disabled={!!error}
              onClick={() => setText(formatJson(text))}
            >
              {t('cockpit.variable.formatJson')}
            </Button>
          )}
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" busy={busy} disabled={!!error}>
            {t('action.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Replaces the content of a file or bytes variable. */
function UploadDialog({ variable, onClose }: { variable: VariableInstance; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [file, setFile] = useState<File | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.variable.uploadTitle', { name: variable.name })}
    >
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (!file) return;
          setBusy(true);
          void run(() => uploadVariableData(variable, file), {
            success: t('cockpit.variable.saved', { name: variable.name }),
            failure: t('cockpit.variable.saveFailed'),
          }).then((ok) => {
            setBusy(false);
            if (ok) onClose();
          });
        }}
      >
        <p className="confirm__text">{t('cockpit.variable.uploadText')}</p>
        <Field label={t('attachments.file')} required error={submitted && !file ? t('attachments.fileRequired') : null}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="file"
              autoFocus
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          )}
        </Field>
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" icon={Upload} busy={busy}>
            {t('cockpit.variable.upload')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** The instance variables belong to: a process instance or a case instance (CMMN). */
type VariableOwner = { kind: 'process' | 'case'; id: string };

function AddDialog({ owner, onClose }: { owner: VariableOwner; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [name, setName] = useState('');
  const [type, setType] = useState<string>('String');
  const [value, setValue] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const isFile = type === 'File';
  const converted = isFile
    ? file
      ? ({ ok: true, value: { value: null, type } } as const)
      : ({ ok: false, error: 'attachments.fileRequired' } as const)
    : toVariableValue(type, type === 'Boolean' && value !== 'true' ? 'false' : value);
  const nameError = name.trim() ? null : t('validation.required');
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('cockpit.variable.addTitle')}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (nameError || !converted.ok) return;
          setBusy(true);
          void run(
            () =>
              isFile && file
                ? (owner.kind === 'case' ? addCaseFileVariable : addInstanceFileVariable)(owner.id, name.trim(), file)
                : (owner.kind === 'case' ? addCaseVariable : addInstanceVariable)(owner.id, name.trim(), converted.value),
            {
              success: t('cockpit.variable.added', { name: name.trim() }),
              failure: t('cockpit.variable.saveFailed'),
            },
          ).then((ok) => {
            setBusy(false);
            if (ok) onClose();
          });
        }}
      >
        <p className="confirm__text">{t('cockpit.variable.addText')}</p>
        <Field label={t('form.variable.name')} required error={submitted ? nameError : null}>
          {(props) => (
            <input
              {...props}
              className="input"
              autoFocus
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          )}
        </Field>
        <Field label={t('form.variable.type')}>
          {(props) => (
            <select
              {...props}
              className="input"
              value={type}
              onChange={(event) => {
                setType(event.target.value);
                setValue('');
              }}
            >
              {NEW_TYPES.map((entry) => (
                <option key={entry} value={entry}>
                  {entry}
                </option>
              ))}
            </select>
          )}
        </Field>
        {isFile && (
          <Field label={t('attachments.file')} required error={submitted && !file ? t('attachments.fileRequired') : null}>
            {(props) => (
              <input
                {...props}
                className="input"
                type="file"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            )}
          </Field>
        )}
        {type !== 'Null' && !isFile && (
          <div className="field">
            <span className="field__label">{t('form.variable.value')}</span>
            <ValueInput
              type={type}
              value={value}
              onChange={setValue}
              label={t('form.variable.value')}
              invalid={submitted && !converted.ok}
            />
            {submitted && !converted.ok && (
              <p className="field__error" role="alert">
                {t(converted.error)}
              </p>
            )}
          </div>
        )}
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" busy={busy}>
            {t('action.add')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** The variables of an instance: look at them, change simple ones in place, add and delete. */
export function VariablesTab({
  processInstanceId,
  caseInstanceId,
  scopeNames,
}: {
  processInstanceId?: string;
  /** the variables of a case instance (CMMN) instead of a process instance */
  caseInstanceId?: string;
  /** names of the activity instances (or case executions) by id */
  scopeNames: Map<string, string>;
}) {
  const owner = useMemo<VariableOwner>(
    () => (caseInstanceId ? { kind: 'case', id: caseInstanceId } : { kind: 'process', id: processInstanceId ?? '' }),
    [caseInstanceId, processInstanceId],
  );
  const { t } = useI18n();
  const run = useCockpitAction();
  const [params, setParams] = useUrlState();
  const refetchInterval = useRefreshInterval();
  const [sortOrder, setSortOrder] = useRemembered<SortOrder>('variables.order', 'asc');
  const [search, setSearch] = useState(params.get('vq') ?? '');
  const term = useDebounced(search.trim());
  const page = pageOf(params);

  const [editing, setEditing] = useState<{ id: string; text: string; error: MessageKey | null } | null>(null);
  const [saving, setSaving] = useState(false);
  const [viewed, setViewed] = useState<VariableInstance | null>(null);
  const [textEdited, setTextEdited] = useState<VariableInstance | null>(null);
  const [uploaded, setUploaded] = useState<VariableInstance | null>(null);
  const [removed, setRemoved] = useState<VariableInstance | null>(null);
  const [removing, setRemoving] = useState(false);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    if ((params.get('vq') ?? '') !== term) {
      setParams({ vq: term || null, page: null });
    }
  }, [term, params, setParams]);

  const variables = useQuery({
    queryKey: [COCKPIT, 'variables', owner.kind, owner.id, term, sortOrder, page],
    queryFn: ({ signal }) =>
      getVariables({
        ...(owner.kind === 'case' ? { caseInstanceId: owner.id } : { processInstanceId: owner.id }),
        nameLike: term || undefined,
        sortOrder,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
    // a refresh must not replace the row that is being edited
    refetchInterval: editing ? false : refetchInterval,
  });

  const columns = useMemo<Column<VariableInstance>[]>(() => {
    const save = (variable: VariableInstance, text: string) => {
      // Enter while the previous write runs would send it again
      if (saving) return;
      const converted = toVariableValue(variable.type, text);
      if (!converted.ok) {
        setEditing({ id: variable.id, text, error: converted.error });
        return;
      }
      setSaving(true);
      void run(() => updateVariable(variable, converted.value), {
        success: t('cockpit.variable.saved', { name: variable.name }),
        failure: t('cockpit.variable.saveFailed'),
      }).then((ok) => {
        setSaving(false);
        // only the editor of this value, not one opened or changed since
        if (ok) setEditing((current) => (current?.id === variable.id && current.text === text ? null : current));
      });
    };
    return [
      {
        id: 'name',
        header: t('form.variable.name'),
        sortBy: 'variableName',
        cell: (variable) => <span className="variable-name">{variable.name}</span>,
      },
      {
        id: 'type',
        header: t('form.variable.type'),
        secondary: true,
        cell: (variable) => <span className="badge">{variable.type}</span>,
      },
      {
        id: 'value',
        header: t('form.variable.value'),
        cell: (variable) => {
          if (editing?.id === variable.id) {
            return (
              <span className="value-editor">
                <ValueInput
                  type={variable.type}
                  value={editing.text}
                  label={t('cockpit.variable.valueOf', { name: variable.name })}
                  autoFocus
                  invalid={!!editing.error}
                  describedBy={editing.error ? `variable-error-${variable.id}` : undefined}
                  onChange={(text) => setEditing({ id: variable.id, text, error: null })}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      save(variable, editing.text);
                    } else if (event.key === 'Escape') {
                      event.preventDefault();
                      if (!saving) setEditing(null);
                    }
                  }}
                />
                <IconButton
                  icon={Check}
                  label={t('action.save')}
                  disabled={saving}
                  onClick={() => save(variable, editing.text)}
                />
                <IconButton icon={X} label={t('action.cancel')} disabled={saving} onClick={() => setEditing(null)} />
                {editing.error && (
                  <span className="field__error" role="alert" id={`variable-error-${variable.id}`}>
                    {t(editing.error)}
                  </span>
                )}
              </span>
            );
          }
          return <ValueCell variable={variable} onView={() => setViewed(variable)} t={t} />;
        },
      },
      {
        id: 'scope',
        header: t('cockpit.variable.scope'),
        secondary: true,
        cell: (variable) => {
          // a variable of a case lives on the case instance or on one of its plan items
          const scope = owner.kind === 'case' ? variable.caseExecutionId : variable.activityInstanceId;
          return variable.taskId
            ? t('cockpit.variable.scopeTask')
            : scope && scope !== owner.id
              ? (scopeNames.get(scope) ?? scope)
              : t('cockpit.variable.scopeInstance');
        },
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (variable) => {
          const kind = variableKind(variable);
          return (
            <span className="row-actions">
              {kind === 'binary' && (
                <IconButton
                  className="icon-btn--sm"
                  icon={Upload}
                  label={t('cockpit.variable.replaceFile', { name: variable.name })}
                  onClick={() => setUploaded(variable)}
                />
              )}
              {(kind === 'inline' || kind === 'text' || isJsonObject(variable)) && (
                <IconButton
                  className="icon-btn--sm"
                  icon={Pencil}
                  label={t('cockpit.variable.edit', { name: variable.name })}
                  onClick={() =>
                    kind === 'inline'
                      ? setEditing({ id: variable.id, text: inputValue(variable), error: null })
                      : setTextEdited(variable)
                  }
                />
              )}
              <IconButton
                className="icon-btn--sm"
                icon={Trash2}
                label={t('cockpit.variable.delete', { name: variable.name })}
                onClick={() => setRemoved(variable)}
              />
            </span>
          );
        },
      },
    ];
  }, [t, editing, saving, run, scopeNames, owner]);

  return (
    <>
      <div className="toolbar">
        <SearchInput value={search} onChange={setSearch} label={t('cockpit.variable.search')} />
        <Button icon={Plus} onClick={() => setAdding(true)}>
          {t('form.variable.add')}
        </Button>
      </div>
      <DataTable
        label={t('cockpit.instance.tab.variables')}
        columns={columns}
        updating={variables.isPlaceholderData}
        rows={variables.data?.items}
        rowKey={(variable) => variable.id}
        error={variables.error}
        onRetry={() => void variables.refetch()}
        sorting={{ sortBy: 'variableName', sortOrder }}
        onSort={(next) => setSortOrder(next.sortOrder)}
        empty={
          term ? (
            <NoMatch search title={t('cockpit.variable.noMatch')} onClear={() => setSearch('')} />
          ) : (
            <EmptyState icon={Braces} title={t('cockpit.variable.empty')} />
          )
        }
      />
      <Pager
        page={page}
        count={variables.data?.count ?? 0}
        onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
      />

      {viewed && <ViewDialog variable={viewed} onClose={() => setViewed(null)} />}
      {textEdited && <TextEditDialog variable={textEdited} onClose={() => setTextEdited(null)} />}
      {uploaded && <UploadDialog variable={uploaded} onClose={() => setUploaded(null)} />}
      {adding && <AddDialog owner={owner} onClose={() => setAdding(false)} />}
      <ConfirmDialog
        open={!!removed}
        onOpenChange={(open) => !open && setRemoved(null)}
        title={t('cockpit.variable.deleteTitle')}
        text={t('cockpit.variable.deleteText', { name: removed?.name ?? '' })}
        confirmLabel={t('cockpit.delete')}
        danger
        busy={removing}
        onConfirm={() => {
          if (!removed) return;
          setRemoving(true);
          void run(() => deleteVariable(removed), {
            success: t('cockpit.variable.deleted', { name: removed.name }),
            failure: t('cockpit.variable.deleteFailed'),
          }).then((ok) => {
            setRemoving(false);
            if (ok) setRemoved(null);
          });
        }}
      >
        {removed && variableKind(removed) === 'object' && (
          <Notice tone="warning">{t('cockpit.variable.deleteObjectHint')}</Notice>
        )}
      </ConfirmDialog>
    </>
  );
}
