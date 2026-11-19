/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useContext, useMemo, useState } from 'react';
import { SessionContext } from '../app/session';
import { env } from '../env';
import type { FormTarget, VariableMap } from '../api/types';
import { draftPrefix } from './embedded/broker';

/**
 * Entered values of a form that was not submitted yet. They are kept in the
 * local storage of the browser, per engine, user and form, so that switching
 * tasks, reloading the page or an expired session never loses work. They are
 * removed when the form is submitted, discarded or the user logs out (the
 * prefix is the one of the embedded forms, which are cleared together).
 */
export interface FormDraft<T> {
  /** what the renderer stored; `variables` is a prefill in the neutral format */
  kind: string;
  values: T;
  savedAt: number;
}

/** Upper bound of a stored draft; larger ones are not kept. */
const MAX_DRAFT = 256 * 1024;

export function formDraftKey(engine: string, userId: string, target: FormTarget): string {
  const form = target.type === 'task' ? `task:${target.taskId}` : `start:${target.processDefinitionId}`;
  return `${draftPrefix(engine, userId)}form/${form}`;
}

export function readDraft<T>(key: string): FormDraft<T> | null {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const draft = JSON.parse(raw) as FormDraft<T>;
    return draft && typeof draft === 'object' && typeof draft.kind === 'string' ? draft : null;
  } catch {
    return null;
  }
}

export function writeDraft(key: string, draft: FormDraft<unknown>): void {
  try {
    const raw = JSON.stringify(draft);
    if (raw.length <= MAX_DRAFT) {
      window.localStorage.setItem(key, raw);
      return;
    }
  } catch {
    // not kept
  }
  // an older draft must not come back in place of values that could not be kept
  removeDraft(key);
}

export function removeDraft(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // nothing kept either
  }
}

/**
 * Prepares the start form of a process with the given variables, e.g. those
 * of an instance that is started again; the form shows them as a draft.
 */
export function prefillStartForm(userId: string, processDefinitionId: string, variables: VariableMap): void {
  writeDraft(formDraftKey(env.engine, userId, { type: 'start', processDefinitionId }), {
    kind: 'variables',
    values: variables,
    savedAt: Date.now(),
  });
}

export interface DraftControl<T> {
  /** the draft found when the form was opened, if any */
  restored: FormDraft<T> | null;
  /** keeps the current values */
  save: (values: T) => void;
  /** drops the draft, e.g. after the form was submitted */
  clear: () => void;
  /** drops the draft and asks the renderer to start over: its key changes */
  discard: () => void;
  /** changes with every discard; the renderer uses it as the key of its fields */
  generation: number;
}

/**
 * The draft of the form of a target. `kind` names the format of the values;
 * a draft of another format is offered as `restored` only if it is a prefill
 * (`variables`), which every renderer understands.
 */
export function useFormDraft<T>(target: FormTarget, kind: string): DraftControl<T> {
  const session = useContext(SessionContext);
  const key = session ? formDraftKey(env.engine, session.userId, target) : null;
  const [generation, setGeneration] = useState(0);
  const restored = useMemo(() => {
    if (!key) return null;
    const draft = readDraft<T>(key);
    return draft && (draft.kind === kind || draft.kind === 'variables') ? draft : null;
    // read again when the draft was discarded
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, kind, generation]);
  const save = useCallback(
    (values: T) => {
      if (key) writeDraft(key, { kind, values, savedAt: Date.now() });
    },
    [key, kind],
  );
  const clear = useCallback(() => {
    if (key) removeDraft(key);
  }, [key]);
  const discard = useCallback(() => {
    if (key) removeDraft(key);
    setGeneration((value) => value + 1);
  }, [key]);
  return { restored, save, clear, discard, generation };
}
