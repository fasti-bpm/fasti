/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Form } from '@bpmn-io/form-js-viewer';
import '@bpmn-io/form-js-viewer/dist/assets/form-js.css';
import { getDeployedForm, getFormVariables } from '../api/engine';
import type { VariableMap } from '../api/types';
import { useI18n } from '../i18n';
import { ErrorState, Loading, MessageError, Notice } from '../ui';
import { FormActions, formTargetKey, useServerSave } from './FormHost';
import type { FormRendererProps } from './types';
import { formDataToVariables, variablesToFormData } from './variables';
import { useFormDraft, type DraftControl } from './drafts';
import { DraftNotice } from './DraftNotice';

type FormData = Record<string, unknown>;

/** The data a form starts with: the variables, or the draft of the user. */
export function initialFormData(variables: VariableMap, draft: { kind: string; values: unknown } | null): FormData {
  if (draft?.kind === 'variables') {
    return variablesToFormData({ ...variables, ...(draft.values as VariableMap) }) as FormData;
  }
  const data = variablesToFormData(variables) as FormData;
  return draft ? { ...data, ...(draft.values as FormData) } : data;
}
import './forms.css';

type Schema = Parameters<Form['importSchema']>[0];

/** Loads a form schema that is served by a process application on this origin. */
async function fetchApplicationForm(path: string): Promise<unknown> {
  const url = new URL(path, window.location.origin);
  if (url.origin !== window.location.origin) {
    throw new MessageError('form.otherOrigin');
  }
  const response = await fetch(url.pathname, {
    credentials: 'same-origin',
    cache: 'no-cache',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new MessageError('form.loadFailed', { status: response.status });
  }
  return response.json();
}

function toSchema(resource: unknown): Schema {
  const schema: unknown = typeof resource === 'string' ? JSON.parse(resource) : resource;
  if (!schema || typeof schema !== 'object' || !Array.isArray((schema as Schema).components)) {
    throw new MessageError('form.notSchema');
  }
  return schema as Schema;
}

function FormView({
  schema,
  variables,
  disabled,
  submitLabel,
  submit,
  save: saveToServer,
  draft,
}: {
  schema: Schema;
  variables: VariableMap;
  draft: DraftControl<FormData>;
} & Pick<FormRendererProps, 'disabled' | 'submitLabel' | 'submit' | 'save'>) {
  const { t } = useI18n();
  const container = useRef<HTMLDivElement>(null);
  const form = useRef<Form | null>(null);
  const [importError, setImportError] = useState<unknown>(null);
  const [invalid, setInvalid] = useState(false);
  const [busy, setBusy] = useState(false);
  const { restored, save } = draft;

  useEffect(() => {
    const element = container.current;
    if (!element) {
      return;
    }
    const instance = new Form({ container: element });
    form.current = instance;
    let active = true;
    let imported = false;
    // what the user enters is kept as a draft; the import itself is no change
    instance.on('changed', (event: { data?: FormData }) => {
      if (imported && active && event.data) save(event.data);
    });
    instance.importSchema(schema, initialFormData(variables, restored)).then(
      () => {
        imported = true;
      },
      (error: unknown) => {
        if (active) {
          setImportError(error);
        }
      },
    );
    return () => {
      active = false;
      form.current = null;
      instance.destroy();
    };
  }, [schema, variables, restored, save]);

  // saving keeps unfinished work: fields in error are saved as they are, the engine checks only on completion
  const serverSave = useServerSave(
    saveToServer,
    () => {
      const instance = form.current;
      return instance ? formDataToVariables(instance.submit().data, variables) : null;
    },
    draft.clear,
  );

  const saving = !!serverSave?.saving;
  useEffect(() => {
    // read-only while saving too, so nothing entered meanwhile is lost with the cleared draft
    form.current?.setProperty('readOnly', disabled || busy || saving);
  }, [disabled, busy, saving]);

  const onSubmit = async () => {
    const instance = form.current;
    if (!instance || saving) {
      return;
    }
    const { data, errors } = instance.submit();
    if (Object.keys(errors).length > 0) {
      setInvalid(true);
      container.current?.querySelector<HTMLElement>('.fjs-has-errors input, .fjs-has-errors select, .fjs-has-errors textarea')?.focus();
      return;
    }
    setInvalid(false);
    setBusy(true);
    try {
      await submit(formDataToVariables(data, variables));
      draft.clear();
    } catch {
      // reported by the host; the entered values are kept
    } finally {
      setBusy(false);
    }
  };

  if (importError) {
    return <ErrorState error={importError} />;
  }
  return (
    <div className="form">
      {!disabled && <DraftNotice draft={restored} disabled={busy} onDiscard={draft.discard} />}
      <div ref={container} className="camunda-form" />
      {invalid && <Notice tone="danger">{t('form.invalid')}</Notice>}
      <FormActions
        label={submitLabel}
        busy={busy}
        disabled={disabled}
        onSubmit={() => void onSubmit()}
        save={serverSave}
      />
    </div>
  );
}

/** Renders a form schema with the form viewer of bpmn.io. */
export function CamundaForm({ target, form, disabled, submitLabel, submit, save }: FormRendererProps) {
  const { t } = useI18n();
  const draft = useFormDraft<FormData>(target, 'camunda-form');
  const query = useQuery({
    queryKey: ['camunda-form', formTargetKey(target), form.resourcePath ?? ''],
    queryFn: async () => {
      const [resource, variables] = await Promise.all([
        form.resourcePath ? fetchApplicationForm(form.resourcePath) : getDeployedForm(target),
        getFormVariables(target),
      ]);
      return { schema: toSchema(resource), variables };
    },
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });

  if (query.isPending) {
    return <Loading label={t('form.loading')} />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  return (
    <FormView
      draft={draft}
      schema={query.data.schema}
      variables={query.data.variables}
      disabled={disabled}
      submitLabel={submitLabel}
      submit={submit}
      save={save}
    />
  );
}
