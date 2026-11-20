/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { DropdownMenu } from 'radix-ui';
import { ChevronDown, Layers } from 'lucide-react';
import type { Batch } from '../api/cockpit';
import {
  deleteInstancesAsync,
  selectionCount,
  setInstancesJobRetriesAsync,
  setInstancesSuspendedAsync,
  setInstancesVariablesAsync,
  setRemovalTimeAsync,
  type InstanceSelection,
} from '../api/operations';
import { getInstances } from '../api/cockpit';
import { useI18n } from '../i18n';
import { fromDateTimeInput, toEngineDate } from '../lib/dates';
import { Button, Field, Notice } from '../ui';
import { COCKPIT, useCockpitAction } from './common';
import { Checkbox, ConfirmDialog, SAMPLE_SIZE, SampleList } from '../ui/controls';
import { migrationPath } from './paths';
import { withReceipt } from './receipts';
import { VariableRows, emptyRow, rowsToVariables, type VariableRow } from './VariableRows';
import { MenuContent } from '../ui/menu';

export type BulkAction = 'suspend' | 'activate' | 'delete' | 'retries' | 'variables' | 'removal' | 'modify';

/**
 * The actions for the instances selected in a list. Every action that changes
 * instances runs as a batch of the engine; its progress is on the batches page.
 */
export function BulkBar({
  selection,
  definitionId,
  onClear,
  onModify,
  pending = false,
}: {
  selection: InstanceSelection;
  definitionId: string;
  onClear: () => void;
  onModify: () => void;
  /** the list is still loading for its current filter; its count is not yet known */
  pending?: boolean;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  // the selection is fixed when a dialog opens, so a later refresh cannot change what was confirmed
  const [action, setAction] = useState<{ kind: Exclude<BulkAction, 'modify'>; selection: InstanceSelection } | null>(null);
  const count = selectionCount(selection);
  const item = (value: BulkAction, label: string) => (
    <DropdownMenu.Item className="menu__item" onSelect={() => (value === 'modify' ? onModify() : setAction({ kind: value, selection }))}>
      {label}
    </DropdownMenu.Item>
  );
  return (
    <div className="toolbar bulk-bar" role="region" aria-label={t('ops.bulk.title')}>
      <span className="toolbar__text" aria-live="polite">
        {t('ops.bulk.selected', { count })}
      </span>
      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <Button variant="primary" icon={Layers} disabled={pending}>
            {t('ops.bulk.actions')}
            <ChevronDown aria-hidden size={14} />
          </Button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <MenuContent align="start">
            {item('suspend', t('cockpit.suspend'))}
            {item('activate', t('cockpit.activate'))}
            {item('retries', t('ops.bulk.retries'))}
            {item('variables', t('ops.bulk.variables'))}
            {selection.kind === 'ids' && item('removal', t('ops.bulk.removal'))}
            {item('modify', t('ops.modify.action'))}
            <DropdownMenu.Item
              className="menu__item"
              onSelect={() =>
                void navigate(
                  selection.kind === 'ids'
                    ? migrationPath(definitionId, selection.ids)
                    : migrationPath(definitionId, undefined, selection.query),
                )
              }
            >
              {t('ops.migrate.action')}
            </DropdownMenu.Item>
            <DropdownMenu.Separator className="menu__separator" />
            {item('delete', t('ops.bulk.delete'))}
          </MenuContent>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
      <Button onClick={onClear}>{t('ops.bulk.clear')}</Button>
      <Link className="link link--subtle" to="/cockpit/batches">
        {t('ops.bulk.batches')}
      </Link>
      {action && (
        <BulkDialog
          key={action.kind}
          action={action.kind}
          selection={action.selection}
          onClose={(done) => {
            setAction(null);
            if (done) onClear();
          }}
        />
      )}
    </div>
  );
}

function BulkDialog({
  action,
  selection,
  onClose,
}: {
  action: Exclude<BulkAction, 'modify'>;
  selection: InstanceSelection;
  onClose: (done: boolean) => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [busy, setBusy] = useState(false);
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [skipSubprocesses, setSkipSubprocesses] = useState(false);
  const [reason, setReason] = useState('');
  const [retries, setRetries] = useState('1');
  const [rows, setRows] = useState<VariableRow[]>([emptyRow()]);
  const [removal, setRemoval] = useState<'absolute' | 'cleared' | 'calculated'>('absolute');
  const [removalDate, setRemovalDate] = useState('');
  const [hierarchical, setHierarchical] = useState(true);
  const count = selectionCount(selection);
  // a few of the instances, by business key where there is one
  const sample = useQuery({
    queryKey: [COCKPIT, 'bulk-sample', selection],
    queryFn: async ({ signal }) =>
      selection.kind === 'ids'
        ? selection.ids.slice(0, SAMPLE_SIZE)
        : (
            await getInstances({ query: selection.query, sortOrder: 'desc', paging: { firstResult: 0, maxResults: SAMPLE_SIZE }, signal })
          ).items.map((item) => (item.businessKey ? `${item.businessKey} · ${item.id}` : item.id)),
    staleTime: 30_000,
  });

  const variables = rowsToVariables(rows);
  const retriesValue = Number(retries);
  const invalid =
    (action === 'retries' && (String(retries).trim() === '' || !Number.isInteger(retriesValue) || retriesValue < 0)) ||
    (action === 'variables' && (!variables.ok || Object.keys(variables.variables).length === 0)) ||
    (action === 'removal' && removal === 'absolute' && !fromDateTimeInput(removalDate));

  const call = (): Promise<Batch> => {
    switch (action) {
      case 'suspend':
      case 'activate':
        return setInstancesSuspendedAsync(selection, action === 'suspend');
      case 'delete':
        return deleteInstancesAsync(selection, {
          deleteReason: reason.trim(),
          skipCustomListeners: skipListeners,
          skipIoMappings: skipMappings,
          skipSubprocesses,
        });
      case 'retries':
        return setInstancesJobRetriesAsync(selection, retriesValue);
      case 'variables':
        return setInstancesVariablesAsync(selection, variables.ok ? variables.variables : {});
      case 'removal': {
        const date = fromDateTimeInput(removalDate);
        return setRemovalTimeAsync(
          { kind: 'ids', ids: selection.kind === 'ids' ? selection.ids : [] },
          removal === 'absolute' && date
            ? { mode: 'absolute', date: toEngineDate(date) }
            : removal === 'cleared'
              ? { mode: 'cleared' }
              : { mode: 'calculated' },
          hierarchical,
        );
      }
    }
  };

  const titles = {
    suspend: t('ops.bulk.suspendTitle', { count }),
    activate: t('ops.bulk.activateTitle', { count }),
    delete: t('ops.bulk.deleteTitle', { count }),
    retries: t('ops.bulk.retriesTitle', { count }),
    variables: t('ops.bulk.variablesTitle', { count }),
    removal: t('ops.bulk.removalTitle', { count }),
  } as const;

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose(false)}
      title={titles[action]}
      text={action === 'delete' ? t('ops.bulk.deleteText', { count }) : t('ops.bulk.text', { count })}
      confirmLabel={action === 'delete' ? t('ops.bulk.deleteConfirm', { count }) : t('ops.bulk.confirm', { count })}
      danger={action === 'delete'}
      busy={busy}
      disabled={invalid}
      onConfirm={() => {
        setBusy(true);
        void run(
          withReceipt(call, titles[action]),
          { success: t('ops.batchStarted'), failure: t('ops.bulk.failed') },
        ).then((ok) => {
          setBusy(false);
          if (ok) onClose(true);
        });
      }}
    >
      <SampleList items={sample.isError ? [] : sample.data} total={count} />
      {action === 'delete' && (
        <>
          <Field label={t('ops.bulk.reason')}>
            {(props) => <input {...props} className="input" value={reason} onChange={(event) => setReason(event.target.value)} />}
          </Field>
          <Checkbox label={t('cockpit.delete.skipListeners')} hint={t('cockpit.delete.skipListenersHint')} checked={skipListeners} onChange={setSkipListeners} />
          <Checkbox label={t('cockpit.delete.skipMappings')} hint={t('cockpit.delete.skipMappingsHint')} checked={skipMappings} onChange={setSkipMappings} />
          <Checkbox label={t('cockpit.delete.skipSubprocesses')} hint={t('cockpit.delete.skipSubprocessesHint')} checked={skipSubprocesses} onChange={setSkipSubprocesses} />
        </>
      )}
      {action === 'retries' && (
        <Field label={t('cockpit.retry.retries')} hint={t('ops.bulk.retriesHint')}>
          {(props) => (
            <input {...props} className="input" type="number" min={0} step={1} value={retries} onChange={(event) => setRetries(event.target.value)} />
          )}
        </Field>
      )}
      {action === 'variables' && (
        <>
          <VariableRows label={t('ops.modify.variables')} rows={rows} onChange={setRows} />
          <p className="field__hint">{t('ops.bulk.variablesHint')}</p>
        </>
      )}
      {action === 'removal' && (
        <>
          <Field label={t('ops.removal.mode')}>
            {(props) => (
              <select {...props} className="input" value={removal} onChange={(event) => setRemoval(event.target.value as typeof removal)}>
                <option value="absolute">{t('ops.removal.absolute')}</option>
                <option value="calculated">{t('ops.removal.calculated')}</option>
                <option value="cleared">{t('ops.removal.cleared')}</option>
              </select>
            )}
          </Field>
          {removal === 'absolute' && (
            <Field label={t('ops.removal.date')} required>
              {(props) => (
                <input {...props} className="input" type="datetime-local" value={removalDate} onChange={(event) => setRemovalDate(event.target.value)} />
              )}
            </Field>
          )}
          <Checkbox label={t('ops.removal.hierarchical')} hint={t('ops.removal.hierarchicalHint')} checked={hierarchical} onChange={setHierarchical} />
          <Notice>{t('ops.removal.hint')}</Notice>
        </>
      )}
    </ConfirmDialog>
  );
}
