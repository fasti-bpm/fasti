/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, ListChecks, Plus, X } from 'lucide-react';
import type { ActivityInstance } from '../api/cockpit';
import {
  modifyInstance,
  modifyInstancesAsync,
  selectionCount,
  type InstanceSelection,
  type ModificationInstruction,
  type ModificationType,
} from '../api/operations';
import { useI18n, type Translate } from '../i18n';
import { Button, Dialog, Field, IconButton, Notice } from '../ui';
import { Checkbox, useCockpitAction } from './common';
import type { FlowNode } from './model';
import { VariableRows, rowsToVariables, type VariableRow } from './VariableRows';
import { withReceipt } from './receipts';

/** An activity instance of a running instance, flat, for choosing what to cancel. */
export interface RunningActivity {
  id: string;
  activityId: string;
  name: string;
  /** a transition instance waits at an asynchronous continuation; the engine cancels it by its own id */
  kind: 'activity' | 'transition';
}

export function runningActivities(root: ActivityInstance | undefined, names: Map<string, string>): RunningActivity[] {
  const found: RunningActivity[] = [];
  const visit = (node: ActivityInstance) => {
    for (const child of node.childActivityInstances ?? []) {
      found.push({
        id: child.id,
        activityId: child.activityId,
        name: child.activityName ?? names.get(child.activityId) ?? child.activityId,
        kind: 'activity',
      });
      visit(child);
    }
    for (const child of node.childTransitionInstances ?? []) {
      found.push({
        id: child.id,
        activityId: child.activityId,
        name: child.activityName ?? names.get(child.activityId) ?? child.activityId,
        kind: 'transition',
      });
    }
  };
  if (root) visit(root);
  return found;
}

interface Draft extends ModificationInstruction {
  key: number;
  rows: VariableRow[];
}

/** One line of the review: what an instruction does, in words. */
export function describeInstruction(
  instruction: ModificationInstruction,
  name: (activityId: string) => string,
  t: Translate,
): string {
  const activity = name(instruction.activityId);
  switch (instruction.type) {
    case 'startBeforeActivity':
      return t('ops.modify.review.startBefore', { activity });
    case 'startAfterActivity':
      return t('ops.modify.review.startAfter', { activity });
    default:
      return instruction.activityInstanceId || instruction.transitionInstanceId
        ? t('ops.modify.review.cancelOne', { activity, id: instruction.activityInstanceId ?? instruction.transitionInstanceId ?? '' })
        : t('ops.modify.review.cancelAll', { activity });
  }
}

/**
 * Changes where the tokens of instances are: starts activities and cancels
 * activity instances. The instructions run in the given order; a review step
 * lists them before anything is changed.
 */
export function ModifyDialog({
  target,
  nodes,
  running,
  initialActivity,
  onClose,
}: {
  target: { kind: 'instance'; id: string } | { kind: 'batch'; definitionId: string; selection: InstanceSelection };
  nodes: FlowNode[];
  /** activity instances of the one instance, to cancel exactly one of them */
  running?: RunningActivity[];
  initialActivity?: string | null;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const batch = target.kind === 'batch';
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [type, setType] = useState<ModificationType>('startBeforeActivity');
  const [activityId, setActivityId] = useState(initialActivity ?? nodes[0]?.id ?? '');
  const [cancelWhich, setCancelWhich] = useState('all');
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [annotation, setAnnotation] = useState('');
  const [step, setStep] = useState<'edit' | 'review'>('edit');
  const [busy, setBusy] = useState(false);
  const [nextKey, setNextKey] = useState(1);
  const [announcement, setAnnouncement] = useState('');
  // where the focus goes after the list or the step changed, since the focused control may be gone
  const focusAfter = useRef<string | null>(null);
  const body = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const selector = focusAfter.current;
    if (!selector || !body.current) return;
    focusAfter.current = null;
    const target = body.current.querySelector<HTMLElement>(selector);
    target?.focus();
  });

  const names = useMemo(() => new Map(nodes.map((node) => [node.id, node.name])), [nodes]);
  const name = (id: string) => names.get(id) ?? id;
  const runningHere = (running ?? []).filter((entry) => entry.activityId === activityId);
  const count = batch ? selectionCount(target.selection) : 1;

  const instructions = drafts.map((draft) => {
    const variables = rowsToVariables(draft.rows);
    return { draft, variables };
  });
  const invalid = instructions.some((entry) => !entry.variables.ok);
  // two equal start instructions start the activity twice, which is rarely meant
  const duplicated = new Set(drafts.map((draft) => `${draft.type}:${draft.activityId}:${draft.activityInstanceId ?? ''}`)).size < drafts.length;

  const cancelTarget = (): Pick<ModificationInstruction, 'activityInstanceId' | 'transitionInstanceId'> => {
    if (type !== 'cancel' || cancelWhich === 'all') return {};
    const chosen = (running ?? []).find((entry) => entry.id === cancelWhich);
    return chosen?.kind === 'transition' ? { transitionInstanceId: cancelWhich } : { activityInstanceId: cancelWhich };
  };
  const add = () => {
    if (!activityId) return;
    const draft: Draft = {
      key: nextKey,
      type,
      activityId,
      ...cancelTarget(),
      rows: [],
    };
    setNextKey(nextKey + 1);
    setDrafts([...drafts, draft]);
    setAnnouncement(t('ops.modify.added', { index: drafts.length + 1, text: describeInstruction(draft, name, t) }));
  };
  const move = (index: number, by: number) => {
    const next = [...drafts];
    const [entry] = next.splice(index, 1);
    if (!entry) return;
    next.splice(index + by, 0, entry);
    setDrafts(next);
    const to = index + by;
    // stay on the same button of the moved instruction, or on the other one at either end
    const button = (by < 0 && to === 0) || (by > 0 && to === next.length - 1) ? (by < 0 ? 'down' : 'up') : by < 0 ? 'up' : 'down';
    focusAfter.current = `[data-instruction="${entry.key}"] [data-move="${button}"]`;
    setAnnouncement(t('ops.modify.moved', { index: to + 1 }));
  };
  const remove = (index: number) => {
    const removed = drafts[index];
    const rest = drafts.filter((_, other) => other !== index);
    setDrafts(rest);
    const neighbour = rest[index] ?? rest[index - 1];
    focusAfter.current = neighbour ? `[data-instruction="${neighbour.key}"] [data-remove]` : '[data-add-instruction]';
    if (removed) setAnnouncement(t('ops.modify.removed', { text: describeInstruction(removed, name, t) }));
  };

  const submit = () => {
    const final: ModificationInstruction[] = instructions.map(({ draft, variables }) => ({
      type: draft.type,
      activityId: draft.activityId,
      activityInstanceId: draft.activityInstanceId,
      transitionInstanceId: draft.transitionInstanceId,
      variables: variables.ok ? variables.variables : undefined,
    }));
    const options = { skipCustomListeners: skipListeners, skipIoMappings: skipMappings, annotation };
    setBusy(true);
    void run(
      withReceipt(
        () =>
          target.kind === 'instance'
            ? modifyInstance(target.id, final, options)
            : modifyInstancesAsync(target.definitionId, target.selection, final, options),
        t('ops.modify.batchTitle', { count }),
      ),
      {
        success: batch ? t('ops.batchStarted') : t('ops.modify.done'),
        failure: t('ops.modify.failed'),
      },
    ).then((ok) => {
      setBusy(false);
      if (ok) onClose();
    });
  };

  return (
    <Dialog
      open
      wide
      onOpenChange={(open) => !open && onClose()}
      title={batch ? t('ops.modify.batchTitle', { count }) : t('ops.modify.title')}
      description={t('ops.modify.description')}
    >
      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>
      {step === 'edit' ? (
        <div className="ops-form" ref={body}>
          <form
            className="ops-form__add"
            onSubmit={(event) => {
              event.preventDefault();
              add();
            }}
          >
            <Field label={t('ops.modify.type')}>
              {(props) => (
                <select {...props} className="input" value={type} onChange={(event) => setType(event.target.value as ModificationType)}>
                  <option value="startBeforeActivity">{t('ops.modify.startBefore')}</option>
                  <option value="startAfterActivity">{t('ops.modify.startAfter')}</option>
                  <option value="cancel">{t('ops.modify.cancel')}</option>
                </select>
              )}
            </Field>
            <Field label={t('cockpit.activity')}>
              {(props) => (
                <select
                  {...props}
                  className="input"
                  value={activityId}
                  onChange={(event) => {
                    setActivityId(event.target.value);
                    setCancelWhich('all');
                  }}
                >
                  {nodes.map((node) => (
                    <option key={node.id} value={node.id}>
                      {node.name === node.id ? node.id : `${node.name} (${node.id})`}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            {type === 'cancel' && !batch && runningHere.length > 1 && (
              <Field label={t('ops.modify.which')}>
                {(props) => (
                  <select {...props} className="input" value={cancelWhich} onChange={(event) => setCancelWhich(event.target.value)}>
                    <option value="all">{t('ops.modify.whichAll', { count: runningHere.length })}</option>
                    {runningHere.map((entry) => (
                      <option key={entry.id} value={entry.id}>
                        {entry.id}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
            )}
            <Button type="submit" icon={Plus} disabled={!activityId} data-add-instruction="">
              {t('ops.modify.add')}
            </Button>
          </form>
          {type === 'cancel' && !batch && running && runningHere.length === 0 && activityId && (
            <Notice tone="warning">{t('ops.modify.nothingToCancel')}</Notice>
          )}

          {duplicated && <Notice tone="warning">{t('ops.modify.duplicate')}</Notice>}
          {drafts.length === 0 ? (
            <p className="muted">{t('ops.modify.empty')}</p>
          ) : (
            <ol className="ops-list" aria-label={t('ops.modify.instructions')}>
              {drafts.map((draft, index) => (
                <li key={draft.key} className="ops-list__item" data-instruction={draft.key}>
                  <div className="ops-list__head">
                    <span className="ops-list__index">{index + 1}</span>
                    <span className="ops-list__text">{describeInstruction(draft, name, t)}</span>
                    <IconButton
                      icon={ArrowUp}
                      label={t('ops.modify.rowAction', { action: t('ops.moveUp'), index: index + 1 })}
                      disabled={index === 0}
                      data-move="up"
                      onClick={() => move(index, -1)}
                    />
                    <IconButton
                      icon={ArrowDown}
                      label={t('ops.modify.rowAction', { action: t('ops.moveDown'), index: index + 1 })}
                      disabled={index === drafts.length - 1}
                      data-move="down"
                      onClick={() => move(index, 1)}
                    />
                    <IconButton
                      icon={X}
                      label={t('ops.modify.rowAction', { action: t('ops.remove'), index: index + 1 })}
                      data-remove=""
                      onClick={() => remove(index)}
                    />
                  </div>
                  {draft.type !== 'cancel' && !batch && (
                    <VariableRows
                      label={t('ops.modify.variables')}
                      rows={draft.rows}
                      onChange={(rows) =>
                        setDrafts(drafts.map((entry) => (entry.key === draft.key ? { ...entry, rows } : entry)))
                      }
                    />
                  )}
                </li>
              ))}
            </ol>
          )}
          {batch && <p className="field__hint">{t('ops.modify.batchHint')}</p>}

          <div className="ops-form__options">
            <Checkbox
              label={t('cockpit.delete.skipListeners')}
              hint={t('ops.skipListenersHint')}
              checked={skipListeners}
              onChange={setSkipListeners}
            />
            <Checkbox
              label={t('cockpit.delete.skipMappings')}
              hint={t('ops.skipMappingsHint')}
              checked={skipMappings}
              onChange={setSkipMappings}
            />
            <Field label={t('ops.annotation')} hint={t('ops.annotationHint')}>
              {(props) => (
                <input {...props} className="input" value={annotation} maxLength={4000} onChange={(event) => setAnnotation(event.target.value)} />
              )}
            </Field>
          </div>
          <div className="confirm__actions">
            <Button onClick={onClose}>{t('action.cancel')}</Button>
            <Button
              variant="primary"
              icon={ListChecks}
              data-review=""
              disabled={drafts.length === 0 || invalid}
              onClick={() => {
                // rows without a name are not part of the instruction
                setDrafts(drafts.map((draft) => ({ ...draft, rows: draft.rows.filter((row) => row.name.trim()) })));
                focusAfter.current = '[data-review-start]';
                setStep('review');
              }}
            >
              {t('ops.review')}
            </Button>
          </div>
        </div>
      ) : (
        <div className="ops-form" ref={body}>
          <p tabIndex={-1} data-review-start="" className="ops-form__focus">
            {batch ? t('ops.modify.reviewBatch', { count }) : t('ops.modify.reviewOne')}</p>
          <ol className="ops-review" aria-label={t('ops.modify.instructions')}>
            {instructions.map(({ draft, variables }) => (
              <li key={draft.key}>
                {describeInstruction(draft, name, t)}
                {variables.ok && Object.keys(variables.variables).length > 0 && (
                  <span className="muted">
                    {' '}
                    {t('ops.modify.withVariables', { names: Object.keys(variables.variables).join(', ') })}
                  </span>
                )}
              </li>
            ))}
          </ol>
          <ul className="ops-review ops-review--options">
            {skipListeners && <li>{t('cockpit.delete.skipListeners')}</li>}
            {skipMappings && <li>{t('cockpit.delete.skipMappings')}</li>}
            {annotation.trim() && <li>{t('ops.annotationShown', { text: annotation.trim() })}</li>}
          </ul>
          <div className="confirm__actions">
            <Button
              onClick={() => {
                focusAfter.current = '[data-review]';
                setStep('edit');
              }}
            >
              {t('ops.back')}
            </Button>
            <Button variant="primary" busy={busy} onClick={submit}>
              {batch ? t('ops.modify.submitBatch', { count }) : t('ops.modify.submit')}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
