/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { Tooltip } from 'radix-ui';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SessionContext } from '../app/session';
import { env } from '../env';
import { I18nProvider } from '../i18n';
import { clearDrafts } from './embedded/broker';
import { initialFormData } from './CamundaForm';
import { formDraftKey, prefillStartForm, readDraft, writeDraft } from './drafts';
import { GenericForm, variablesToRows } from './GenericForm';
import { initialValues } from './GeneratedForm';
import type { GeneratedField } from './renderedForm';
import type { FormRendererProps } from './types';

const session = { userId: 'alice', displayName: 'Alice', authorizedApps: ['tasklist'] };
const target = { type: 'task', taskId: 't1' } as const;

function wrap(children: ReactNode) {
  return (
    <I18nProvider initial="en">
      <Tooltip.Provider>
        <SessionContext.Provider value={session}>{children}</SessionContext.Provider>
      </Tooltip.Provider>
    </I18nProvider>
  );
}

function genericProps(submit = vi.fn(() => Promise.resolve())): FormRendererProps {
  return {
    target,
    form: { kind: 'generic', info: { key: null, contextPath: null } },
    disabled: false,
    submitLabel: 'Complete',
    submit,
    submitted: () => undefined,
    reportFailure: () => undefined,
  };
}

afterEach(() => window.localStorage.clear());

describe('form drafts', () => {
  it('are kept per engine, user and form, under the prefix cleared at logout', () => {
    const key = formDraftKey(env.engine, 'alice', target);
    expect(key).not.toBe(formDraftKey(env.engine, 'bob', target));
    expect(key).not.toBe(formDraftKey(env.engine, 'alice', { type: 'task', taskId: 't2' }));
    window.localStorage.setItem(key, '{"kind":"generic","values":{},"savedAt":1}');
    clearDrafts(env.engine, 'alice');
    expect(window.localStorage.getItem(key)).toBeNull();
  });

  it('keep what was entered in a generic form when it is opened again, until it is discarded', async () => {
    const user = userEvent.setup();
    const first = render(wrap(<GenericForm {...genericProps()} />));
    await user.click(screen.getByRole('button', { name: 'Add variable' }));
    await user.type(screen.getByLabelText('Variable name'), 'amount');
    first.unmount();

    render(wrap(<GenericForm {...genericProps()} />));
    expect(screen.getByRole('status')).toHaveTextContent('Restored what you entered');
    expect(screen.getByLabelText('Variable name')).toHaveValue('amount');
    await user.click(screen.getByRole('button', { name: 'Discard draft' }));
    expect(screen.queryByLabelText('Variable name')).not.toBeInTheDocument();
    expect(readDraft(formDraftKey(env.engine, 'alice', target))).toBeNull();
  });

  it('are removed once the form was submitted', async () => {
    const user = userEvent.setup();
    const submit = vi.fn(() => Promise.resolve());
    render(wrap(<GenericForm {...genericProps(submit)} />));
    await user.click(screen.getByRole('button', { name: 'Add variable' }));
    await user.type(screen.getByLabelText('Variable name'), 'x');
    expect(readDraft(formDraftKey(env.engine, 'alice', target))).not.toBeNull();
    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'Complete' }));
    });
    expect(submit).toHaveBeenCalled();
    expect(readDraft(formDraftKey(env.engine, 'alice', target))).toBeNull();
  });

  it('are written to the server by Save without completing, and the draft is cleared', async () => {
    const user = userEvent.setup();
    const submit = vi.fn(() => Promise.resolve());
    const save = vi.fn(() => Promise.resolve());
    render(wrap(<GenericForm {...genericProps(submit)} save={save} />));
    await user.click(screen.getByRole('button', { name: 'Add variable' }));
    await user.type(screen.getByLabelText('Variable name'), 'note');
    await user.type(screen.getByLabelText('Value'), 'call back');
    await act(async () => {
      await user.click(screen.getByRole('button', { name: 'Save' }));
    });
    expect(save).toHaveBeenCalledWith({ note: { value: 'call back', type: 'String' } });
    expect(submit).not.toHaveBeenCalled();
    expect(screen.getByText(/^Saved at /)).toBeInTheDocument();
    expect(readDraft(formDraftKey(env.engine, 'alice', target))).toBeNull();
    // a later change makes the time stale
    await user.type(screen.getByLabelText('Value'), '!');
    expect(screen.queryByText(/^Saved at /)).toBeNull();
  });

  it('offer no Save without a server to save to, e.g. on a start form', () => {
    render(wrap(<GenericForm {...genericProps()} />));
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('drop an older draft when the latest values are too large to keep', () => {
    const key = formDraftKey(env.engine, 'alice', target);
    writeDraft(key, { kind: 'generic', values: { rows: [] }, savedAt: 1 });
    writeDraft(key, { kind: 'generic', values: { text: 'x'.repeat(300 * 1024) }, savedAt: 2 });
    expect(readDraft(key)).toBeNull();
  });

  it('prefill a start form with the variables of another instance', () => {
    prefillStartForm('alice', 'p:1', { amount: { value: 30, type: 'Integer' }, ok: { value: true, type: 'Boolean' } });
    const draft = readDraft(formDraftKey(env.engine, 'alice', { type: 'start', processDefinitionId: 'p:1' }));
    expect(draft?.kind).toBe('variables');
    expect(variablesToRows(draft?.values as never)).toEqual([
      { name: 'amount', type: 'Integer', value: '30' },
      { name: 'ok', type: 'Boolean', value: 'true' },
    ]);
  });

  it('override the initial values of generated and form-js forms', () => {
    const field = { name: 'amount', label: 'Amount', control: 'text', type: 'Long', readOnly: false } as GeneratedField;
    const variables = { amount: { value: 1, type: 'Long' } };
    expect(initialValues([field], variables, null)).toEqual({ amount: '1' });
    expect(initialValues([field], variables, { kind: 'generated', values: { amount: '7' } })).toEqual({ amount: '7' });
    expect(initialValues([field], variables, { kind: 'variables', values: { amount: { value: 5, type: 'Long' } } })).toEqual({
      amount: '5',
    });
    expect(initialFormData(variables, { kind: 'camunda-form', values: { note: 'hi' } })).toEqual({ amount: 1, note: 'hi' });
  });
});
