/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Component, Suspense, createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getFormInfo, saveTaskVariables, submitStartForm, submitTaskForm } from '../api/engine';
import type { FormTarget, ProcessInstanceRef, VariableMap } from '../api/types';
import { useI18n } from '../i18n';
import { Button, ErrorState, Kbd, Loading, Notice, Tooltip, describeError } from '../ui';
import { findFormRenderer } from './registry';
import { resolveForm } from './resolve';
import type { SubmitOptions } from './types';

/** State of the Save button of a task form. */
export interface ServerSave {
  run: () => void;
  saving: boolean;
  savedAt: Date | null;
  /** fields with errors were left out of the save */
  partial?: boolean;
}

/**
 * Runs the save of a form: the values the renderer collects are written,
 * the time of the last success is kept and the browser draft, now on the
 * server, is cleared.
 */
export function useServerSave(
  save: ((variables: VariableMap) => Promise<void>) | undefined,
  collect: () => VariableMap | null,
  onSaved?: () => void,
  /** the state of the entered values; the draft is only cleared if it did not change while saving */
  revision?: unknown,
): ServerSave | undefined {
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<{ at: Date; revision: unknown } | null>(null);
  const latest = useRef(revision);
  latest.current = revision;
  if (!save) return undefined;
  return {
    saving,
    // a change after the save makes the time stale: it is no longer shown
    savedAt: saved && saved.revision === latest.current ? saved.at : null,
    run: () => {
      const variables = collect();
      if (!variables || saving) return;
      const started = latest.current;
      setSaving(true);
      save(variables).then(
        () => {
          setSaved({ at: new Date(), revision: started });
          if (latest.current === started) onSaved?.();
        },
        () => {
          // reported by the host
        },
      ).finally(() => setSaving(false));
    },
  };
}

export function formTargetKey(target: FormTarget): string {
  return target.type === 'task' ? `task:${target.taskId}` : `start:${target.processDefinitionId}`;
}

/**
 * Whether the form is not to be submitted for now, although it can be filled in: a change of its
 * task is on its way. Read by the buttons of the form, whichever renderer shows them.
 */
const SubmitHeldContext = createContext(false);

class RendererBoundary extends Component<
  { children: ReactNode; resetKey: string },
  { error: unknown }
> {
  override state: { error: unknown } = { error: null };

  static getDerivedStateFromError(error: unknown) {
    return { error };
  }

  override componentDidUpdate(previous: { resetKey: string }) {
    if (previous.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  override render() {
    if (this.state.error) {
      return <ErrorState error={this.state.error} />;
    }
    return this.props.children;
  }
}

/**
 * Displays the form of a task or of a start event. The host knows nothing
 * about how a form is drawn: it resolves the kind of form, picks the
 * renderer that is registered for it and submits what the renderer returns.
 */
export function FormHost({
  target,
  disabled,
  disabledHint,
  pending = false,
  beforeSubmit,
  submitLabel,
  onSubmitted,
}: {
  target: FormTarget;
  disabled: boolean;
  disabledHint?: ReactNode;
  /** something the submit depends on is under way, e.g. the claim of the task: no submit until then */
  pending?: boolean;
  /**
   * Awaited before the form is sent: answers whether it may be, once what was under way is
   * settled. A form that may not is kept as it is; why is reported by what failed.
   */
  beforeSubmit?: () => Promise<boolean>;
  submitLabel: string;
  onSubmitted: (result: ProcessInstanceRef | undefined) => void;
}) {
  const { t } = useI18n();
  const key = formTargetKey(target);
  const [failure, setFailure] = useState<unknown>(null);
  const info = useQuery({
    queryKey: ['form', key],
    queryFn: () => getFormInfo(target),
    staleTime: 60_000,
  });

  useEffect(() => setFailure(null), [key]);

  const submit = useCallback(
    async (variables: VariableMap, options?: SubmitOptions) => {
      setFailure(null);
      // not sent before, e.g., the claim of the task is made: the engine would refuse it
      if (beforeSubmit && !(await beforeSubmit())) {
        throw new Error('The form was not submitted: a change it depends on was not made.');
      }
      try {
        const result =
          target.type === 'task'
            ? await submitTaskForm(target.taskId, variables)
            : await submitStartForm(target.processDefinitionId, variables, options?.businessKey);
        onSubmitted(result ?? undefined);
      } catch (error) {
        setFailure(error);
        throw error;
      }
    },
    [target, onSubmitted, beforeSubmit],
  );

  const save = useCallback(
    async (variables: VariableMap) => {
      if (target.type !== 'task') return;
      setFailure(null);
      try {
        await saveTaskVariables(target.taskId, variables);
      } catch (error) {
        setFailure(error);
        throw error;
      }
    },
    [target],
  );

  const submitted = useCallback(
    (result?: ProcessInstanceRef) => {
      setFailure(null);
      onSubmitted(result);
    },
    [onSubmitted],
  );

  if (info.isPending) {
    return <Loading label={t('form.loading')} />;
  }
  if (info.isError) {
    return <ErrorState error={info.error} onRetry={() => void info.refetch()} />;
  }

  const form = resolveForm(info.data);
  const renderer = findFormRenderer(form, target);
  if (!renderer) {
    return <Notice tone="warning">{t('form.unsupported')}</Notice>;
  }
  const Renderer = renderer.component;
  const described = failure ? describeError(failure, t) : null;

  return (
    <div className="form-host" data-form-kind={form.kind}>
      {disabled && disabledHint && <Notice>{disabledHint}</Notice>}
      {described && (
        <Notice tone="danger" title={t('form.submitFailed')}>
          {described.detail ?? described.title} {described.hint} {t('form.inputKept')}
        </Notice>
      )}
      <SubmitHeldContext.Provider value={pending}>
        <RendererBoundary resetKey={key}>
          <Suspense fallback={<Loading label={t('form.loading')} />}>
            <Renderer
              key={key}
              target={target}
              form={form}
              disabled={disabled}
              submitLabel={submitLabel}
              submit={submit}
              save={target.type === 'task' ? save : undefined}
              submitted={submitted}
              reportFailure={setFailure}
            />
          </Suspense>
        </RendererBoundary>
      </SubmitHeldContext.Provider>
    </div>
  );
}

/**
 * The submit button of a form, shared by the renderers. Ctrl+Enter submits
 * from anywhere on the page.
 */
export function FormActions({
  label,
  busy,
  disabled,
  onSubmit,
  save,
  children,
}: {
  label: string;
  busy: boolean;
  disabled: boolean;
  onSubmit: () => void;
  /** a Save button that writes the values without submitting */
  save?: ServerSave;
  children?: ReactNode;
}) {
  const { t, locale } = useI18n();
  const submitRef = useRef(onSubmit);
  useEffect(() => {
    submitRef.current = onSubmit;
  });
  // a change of the task is on its way: the form is submitted once it is settled
  const held = useContext(SubmitHeldContext);
  const inactive = disabled || busy || held || !!save?.saving;
  const actionsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (inactive) {
      return;
    }
    const listener = (event: KeyboardEvent) => {
      const target = event.target instanceof Element ? event.target : null;
      // only from the form itself (or nowhere in particular), never from a dialog or popover over it
      const own = !target || target === document.body || !!actionsRef.current?.parentElement?.contains(target);
      const layered = !!target?.closest('[role="dialog"], [role="alertdialog"], [role="menu"], [data-radix-popper-content-wrapper]');
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && own && !layered) {
        event.preventDefault();
        submitRef.current();
      }
    };
    // in the capture phase: form controls may keep the key from bubbling
    window.addEventListener('keydown', listener, true);
    return () => window.removeEventListener('keydown', listener, true);
  }, [inactive]);

  return (
    <div className="form-actions" ref={actionsRef}>
      {children}
      <span className="form-actions__hint" aria-hidden>
        <Kbd>Ctrl</Kbd>
        <Kbd>Enter</Kbd>
      </span>
      {save && (
        <>
          <span className="form-actions__saved" role="status">
            {save.savedAt
              ? t(save.partial ? 'form.savedPartly' : 'form.savedAt', {
                  time: new Intl.DateTimeFormat(locale, { timeStyle: 'short' }).format(save.savedAt),
                })
              : ''}
          </span>
          <Tooltip label={t('form.saveHint')}>
            <Button busy={save.saving} disabled={disabled || busy} onClick={save.run}>
              {t('form.save')}
            </Button>
          </Tooltip>
        </>
      )}
      <Button variant="primary" busy={busy} disabled={disabled || held || !!save?.saving} onClick={onSubmit}>
        {label}
      </Button>
    </div>
  );
}
