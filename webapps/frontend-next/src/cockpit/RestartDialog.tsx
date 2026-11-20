/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { Plus, X } from 'lucide-react';
import {
  restartInstances,
  restartInstancesAsync,
  type HistoricQuery,
  type RestartOptions,
} from '../api/operations';
import { useI18n } from '../i18n';
import { Button, Dialog, Field, IconButton, Notice } from '../ui';
import { useCockpitAction } from './common';
import { Checkbox } from '../ui/controls';
import type { FlowNode } from './model';
import { withReceipt } from './receipts';

/** Instances restarted at once without a batch; more run in the background. */
export const SYNC_LIMIT = 10;

/**
 * Starts finished instances again as new instances of the same version, at
 * the start event or at chosen activities.
 */
export function RestartDialog({
  definitionId,
  selection,
  count,
  nodes,
  onClose,
}: {
  definitionId: string;
  selection: { kind: 'ids'; ids: string[] } | { kind: 'query'; query: HistoricQuery };
  count: number;
  nodes: FlowNode[];
  onClose: (done: boolean) => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  // the engine needs at least one instruction; a restart begins at the start event by default
  const initial = nodes.find((node) => node.initial);
  const [instructions, setInstructions] = useState<RestartOptions['instructions']>(
    initial ? [{ type: 'startBeforeActivity', activityId: initial.id }] : [],
  );
  const [type, setType] = useState<'startBeforeActivity' | 'startAfterActivity'>('startBeforeActivity');
  const [activityId, setActivityId] = useState(nodes[0]?.id ?? '');
  const [initialVariables, setInitialVariables] = useState(true);
  const [withoutBusinessKey, setWithoutBusinessKey] = useState(false);
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [async, setAsync] = useState(count > SYNC_LIMIT);
  const [step, setStep] = useState<'edit' | 'review'>('edit');
  const [busy, setBusy] = useState(false);
  const name = (id: string) => nodes.find((node) => node.id === id)?.name ?? id;

  const startAt = (instruction: RestartOptions['instructions'][number]) =>
    instruction.type === 'startBeforeActivity'
      ? t('ops.modify.review.startBefore', { activity: name(instruction.activityId) })
      : t('ops.modify.review.startAfter', { activity: name(instruction.activityId) });

  const submit = () => {
    const options: RestartOptions = {
      selection,
      instructions,
      initialVariables,
      withoutBusinessKey,
      skipCustomListeners: skipListeners,
      skipIoMappings: skipMappings,
    };
    setBusy(true);
    void run(
      withReceipt(
        () => (async ? restartInstancesAsync(definitionId, options) : restartInstances(definitionId, options)),
        t('ops.restart.title', { count }),
      ),
      { success: async ? t('ops.batchStarted') : t('ops.restart.done'), failure: t('ops.restart.failed') },
    ).then((ok) => {
      setBusy(false);
      if (ok) onClose(true);
    });
  };

  return (
    <Dialog
      open
      wide
      onOpenChange={(open) => !open && onClose(false)}
      title={t('ops.restart.title', { count })}
      description={t('ops.restart.description')}
    >
      {step === 'edit' ? (
        <div className="ops-form">
          <form
            className="ops-form__add"
            onSubmit={(event) => {
              event.preventDefault();
              if (activityId) setInstructions([...instructions, { type, activityId }]);
            }}
          >
            <Field label={t('ops.modify.type')}>
              {(props) => (
                <select {...props} className="input" value={type} onChange={(event) => setType(event.target.value as typeof type)}>
                  <option value="startBeforeActivity">{t('ops.modify.startBefore')}</option>
                  <option value="startAfterActivity">{t('ops.modify.startAfter')}</option>
                </select>
              )}
            </Field>
            <Field label={t('cockpit.activity')}>
              {(props) => (
                <select {...props} className="input" value={activityId} onChange={(event) => setActivityId(event.target.value)}>
                  {nodes.map((node) => (
                    <option key={node.id} value={node.id}>
                      {node.name === node.id ? node.id : `${node.name} (${node.id})`}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Button type="submit" icon={Plus} disabled={!activityId}>
              {t('ops.modify.add')}
            </Button>
          </form>
          {instructions.length === 0 ? (
            <Notice tone="warning">{t('ops.restart.needInstruction')}</Notice>
          ) : (
            <ol className="ops-list" aria-label={t('ops.modify.instructions')}>
              {instructions.map((instruction, index) => (
                <li key={`${index}-${instruction.activityId}`} className="ops-list__item">
                  <div className="ops-list__head">
                    <span className="ops-list__index">{index + 1}</span>
                    <span className="ops-list__text">{startAt(instruction)}</span>
                    <IconButton
                      icon={X}
                      label={t('ops.remove')}
                      onClick={() => setInstructions(instructions.filter((_, position) => position !== index))}
                    />
                  </div>
                </li>
              ))}
            </ol>
          )}
          <div className="ops-form__options">
            <Checkbox label={t('ops.restart.initialVariables')} hint={t('ops.restart.initialVariablesHint')} checked={initialVariables} onChange={setInitialVariables} />
            <Checkbox label={t('ops.restart.withoutBusinessKey')} checked={withoutBusinessKey} onChange={setWithoutBusinessKey} />
            <Checkbox label={t('cockpit.delete.skipListeners')} hint={t('ops.skipListenersHint')} checked={skipListeners} onChange={setSkipListeners} />
            <Checkbox label={t('cockpit.delete.skipMappings')} hint={t('ops.skipMappingsHint')} checked={skipMappings} onChange={setSkipMappings} />
            <Checkbox label={t('ops.async')} hint={t('ops.asyncHint')} checked={async} onChange={setAsync} />
          </div>
          <div className="confirm__actions">
            <Button onClick={() => onClose(false)}>{t('action.cancel')}</Button>
            <Button variant="primary" disabled={count === 0 || instructions.length === 0} onClick={() => setStep('review')}>
              {t('ops.review')}
            </Button>
          </div>
        </div>
      ) : (
        <div className="ops-form">
          <p>{t('ops.restart.review', { count })}</p>
          <ul className="ops-review">
            {instructions.map((entry, index) => (
              <li key={index}>{startAt(entry)}</li>
            ))}
            {initialVariables && <li>{t('ops.restart.initialVariables')}</li>}
            {withoutBusinessKey && <li>{t('ops.restart.withoutBusinessKey')}</li>}
            {skipListeners && <li>{t('cockpit.delete.skipListeners')}</li>}
            {skipMappings && <li>{t('cockpit.delete.skipMappings')}</li>}
            <li>{async ? t('ops.async') : t('ops.sync')}</li>
          </ul>
          <div className="confirm__actions">
            <Button onClick={() => setStep('edit')}>{t('ops.back')}</Button>
            <Button variant="primary" busy={busy} onClick={submit}>
              {t('ops.restart.submit', { count })}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
