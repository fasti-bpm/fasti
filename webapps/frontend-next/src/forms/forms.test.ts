/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import type { FormInfo } from '../api/types';
import { translate } from '../i18n';
import { MessageError, describeError } from '../ui';
import { ApiError } from '../api/http';
import { toVariables, validateField } from './GeneratedForm';
import { findFormRenderer, registerFormRenderer } from './registry';
import { parseRenderedForm, toEnginePattern, toIsoDate } from './renderedForm';
import { applicationResourcePath, resolveForm } from './resolve';
import { convertInput, formDataToVariables, variablesToFormData } from './variables';

const info = (key: string | null, extra: Partial<FormInfo> = {}): FormInfo => ({
  key,
  contextPath: '/invoice',
  ...extra,
});

describe('resolveForm', () => {
  it('recognises the kinds of forms of the engine', () => {
    expect(resolveForm(info(null)).kind).toBe('generic');
    expect(
      resolveForm(info(null, { camundaFormRef: { key: 'f', binding: 'latest' } })).kind,
    ).toBe('camunda-form');
    expect(resolveForm(info('camunda-forms:deployment:review.form')).kind).toBe('camunda-form');
    expect(
      resolveForm(info('embedded:engine://engine/:engine/task/abc/rendered-form')).kind,
    ).toBe('generated');
    expect(
      resolveForm(info('embedded:engine://engine/:engine/process-definition/p:1:2/rendered-form'))
        .kind,
    ).toBe('generated');
    expect(resolveForm(info('embedded:app:forms/approve.html')).kind).toBe('embedded');
    expect(resolveForm(info('embedded:deployment:approve.html')).kind).toBe('embedded');
    expect(resolveForm(info('app:forms/approve.jsf')).kind).toBe('external');
    expect(resolveForm(info('https://example.org/form')).kind).toBe('external');
  });

  it('resolves a form of a process application to a path on the same origin', () => {
    expect(resolveForm(info('camunda-forms:app:forms/a.form'))).toMatchObject({
      kind: 'camunda-form',
      resourcePath: '/invoice/forms/a.form',
    });
    expect(applicationResourcePath('/invoice/', '/forms/a.form')).toBe('/invoice/forms/a.form');
  });

  it('never resolves a form to another origin or out of the application', () => {
    expect(applicationResourcePath(null, 'a.form')).toBeNull();
    expect(applicationResourcePath('//example.org', 'a.form')).toBeNull();
    expect(applicationResourcePath('https://example.org', 'a.form')).toBeNull();
    expect(applicationResourcePath('/invoice', '../camunda/api/engine/engine')).toBeNull();
    expect(applicationResourcePath('/invoice', '\\\\example.org\\a.form')).toBeNull();
    expect(resolveForm(info('camunda-forms:app:a.form', { contextPath: null })).kind).toBe(
      'external',
    );
  });
});

describe('form renderers', () => {
  it('finds a renderer for every kind of form', () => {
    const target = { type: 'task', taskId: 't' } as const;
    expect(findFormRenderer(resolveForm(info(null)), target)?.id).toBe('generic');
    expect(
      findFormRenderer(resolveForm(info('camunda-forms:deployment:a.form')), target)?.id,
    ).toBe('camunda-form');
    expect(findFormRenderer(resolveForm(info('embedded:app:a.html')), target)?.id).toBe(
      'embedded',
    );
    expect(findFormRenderer(resolveForm(info('embedded:deployment:a.html')), target)?.id).toBe(
      'embedded',
    );
    expect(
      findFormRenderer(resolveForm(info('embedded:engine://engine/:engine/task/t/rendered-form')), target)
        ?.id,
    ).toBe('generated');
    expect(findFormRenderer(resolveForm(info('app:forms/a.jsf')), target)?.id).toBe('external');
    expect(findFormRenderer(resolveForm(info('https://example.org/f')), target)?.id).toBe(
      'external',
    );
  });

  it('prefers a renderer that was registered later', () => {
    const target = { type: 'task', taskId: 't' } as const;
    registerFormRenderer({
      id: 'custom',
      supports: (form) => form.info.key === 'embedded:app:special.html',
      component: () => null,
    });

    expect(findFormRenderer(resolveForm(info('embedded:app:special.html')), target)?.id).toBe(
      'custom',
    );
    expect(findFormRenderer(resolveForm(info('embedded:app:other.html')), target)?.id).toBe(
      'embedded',
    );
  });
});

const MARKUP = `<form name="generatedForm" role="form">
  <div class="form-group"><label for="customer">Customer</label>
    <input class="form-control" name="customer" cam-variable-type="String" cam-variable-name="customer" required="true" maxlength="10" type="text" value="ACME" />
    <div ng-if="x" class="has-error"><div class="help-block">Required field</div></div></div>
  <div class="form-group"><label for="amount">Amount</label>
    <input class="form-control" name="amount" cam-variable-type="Long" cam-variable-name="amount" min="1" max="100" type="text" /></div>
  <div class="form-group"><label for="approved">Approved</label>
    <input class="form-control" name="approved" cam-variable-type="Boolean" cam-variable-name="approved" type="checkbox" /></div>
  <div class="form-group"><label for="kind">Kind</label>
    <select class="form-control" name="kind" cam-variable-type="String" cam-variable-name="kind">
      <option value="a">Alpha</option><option value="b">Beta</option></select></div>
  <div class="form-group"><label for="due">Due</label><div class="input-group">
    <input class="form-control" name="due" cam-variable-type="String" cam-variable-name="due" uib-datepicker-popup="dd/MM/yyyy" is-open="dateFieldOpeneddue" type="text" />
    <script cam-script type="text/form-script">$scope.opendue = function () {};</script></div></div>
  <div class="form-group"><label for="note">Note</label>
    <input class="form-control" name="note" cam-variable-type="String" cam-variable-name="note" disabled="true" type="text" /></div>
  <div class="form-group"><input class="form-control" name="key" cam-business-key type="text" /></div>
  <img src="x" onerror="window.__executed = true" />
</form>`;

describe('parseRenderedForm', () => {
  const fields = parseRenderedForm(MARKUP);
  const field = (name: string) => {
    const found = fields.find((candidate) => candidate.name === name);
    if (!found) throw new Error(`no field ${name}`);
    return found;
  };

  it('describes the fields of the generated markup', () => {
    expect(fields.map((candidate) => candidate.name)).toEqual([
      'customer',
      'amount',
      'approved',
      'kind',
      'due',
      'note',
      'key',
    ]);
    expect(field('customer')).toMatchObject({
      label: 'Customer',
      type: 'String',
      control: 'text',
      required: true,
      maxLength: 10,
      defaultValue: 'ACME',
    });
    expect(field('amount')).toMatchObject({ type: 'Long', min: 1, max: 100, required: false });
    expect(field('approved')).toMatchObject({ type: 'Boolean', control: 'checkbox' });
    expect(field('kind')).toMatchObject({
      control: 'select',
      options: [
        { value: 'a', label: 'Alpha' },
        { value: 'b', label: 'Beta' },
      ],
    });
    expect(field('due')).toMatchObject({ control: 'date', datePattern: 'dd/MM/yyyy' });
    expect(field('note').readOnly).toBe(true);
    expect(field('key')).toMatchObject({ businessKey: true, label: 'key' });
  });

  it('never executes or inserts the markup', () => {
    expect((window as unknown as { __executed?: boolean }).__executed).toBeUndefined();
    expect(document.querySelector('[cam-variable-name]')).toBeNull();
  });

  it('validates the entered values', () => {
    const t = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) =>
      translate('en', key, params);
    expect(validateField(field('customer'), '', t)).toBe('This field is required.');
    expect(validateField(field('customer'), 'A very long name', t)).toBe('At most 10 characters.');
    expect(validateField(field('customer'), 'ACME', t)).toBeNull();
    expect(validateField(field('amount'), 'abc', t)).toBe('Enter a whole number.');
    expect(validateField(field('amount'), '0', t)).toBe('The smallest value is 1.');
    expect(validateField(field('amount'), '101', t)).toBe('The largest value is 100.');
    expect(validateField(field('amount'), '', t)).toBeNull();
    expect(validateField(field('note'), '', t)).toBeNull();
  });

  it('sends text as entered and numbers without the spaces around them', () => {
    const { variables } = toVariables([field('customer'), field('amount'), field('kind')], {
      customer: ' ACME ',
      amount: ' 5 ',
      kind: '   ',
    });
    expect(variables.customer).toEqual({ value: ' ACME ', type: 'String' });
    expect(variables.amount?.value).toBe(5);
    expect(variables.kind?.value).toBeNull();
  });

  it('converts dates between the date input and the pattern of the engine', () => {
    expect(toEnginePattern('2026-03-09', 'dd/MM/yyyy')).toBe('09/03/2026');
    expect(toIsoDate('2026-03-09T00:00:00.000+0700')).toBe('2026-03-09');
    expect(toIsoDate('09/03/2026')).toBe('2026-03-09');
    expect(toIsoDate(null)).toBe('');
  });
});

describe('variables', () => {
  it('converts typed input', () => {
    expect(convertInput('Long', ' 42 ')).toEqual({ ok: true, value: 42 });
    expect(convertInput('Long', '4.2')).toEqual({ ok: false, error: 'validation.integer' });
    expect(convertInput('Double', '4,5')).toEqual({ ok: true, value: 4.5 });
    expect(convertInput('Double', '')).toEqual({ ok: false, error: 'validation.number' });
    expect(convertInput('Boolean', true)).toEqual({ ok: true, value: true });
    expect(convertInput('String', ' as typed ')).toEqual({ ok: true, value: ' as typed ' });
    expect(convertInput('Date', 'nonsense')).toEqual({ ok: false, error: 'validation.date' });
    const date = convertInput('Date', '2026-03-09T10:30');
    expect(date.ok && String(date.value)).toMatch(/^2026-03-09T10:30:00\.000[+-]\d{4}$/);
  });

  it('parses JSON variables for a form and serializes structured values again', () => {
    const variables = {
      name: { value: 'Mary', type: 'String' },
      items: { value: '[{"id":1}]', type: 'Json' },
      customer: {
        value: '{"id":7}',
        type: 'Object',
        valueInfo: { serializationDataFormat: 'application/json', objectTypeName: 'x.Customer' },
      },
    };

    const data = variablesToFormData(variables);
    expect(data).toEqual({ name: 'Mary', items: [{ id: 1 }], customer: { id: 7 } });

    expect(
      formDataToVariables({ ...data, name: 'John', tags: ['a'], count: 3, skipped: undefined }, variables),
    ).toEqual({
      name: { value: 'John' },
      items: { value: '[{"id":1}]', type: 'Json', valueInfo: undefined },
      customer: {
        value: '{"id":7}',
        type: 'Object',
        valueInfo: { serializationDataFormat: 'application/json', objectTypeName: 'x.Customer' },
      },
      tags: { value: '["a"]', type: 'Json' },
      count: { value: 3 },
    });
  });
});

describe('errors of loading a form', () => {
  it('are told in the chosen language', () => {
    const t = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate('vi', key, params);
    expect(describeError(new MessageError('form.loadFailed', { status: 404 }), t).detail).toBe('Không tải được biểu mẫu (mã 404).');
  });
});

describe('errors', () => {
  const en = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate('en', key, params);

  it('say what to do next and recognise a change made by someone else at the same time', () => {
    expect(describeError(new ApiError('network', 0), en)).toMatchObject({ hint: 'It is not known whether the action went through. Check the current state before you try again.' });
    expect(describeError(new ApiError('notFound', 404, 'RestException', 'gone'), en)).toMatchObject({
      detail: 'gone',
      hint: 'It may have been completed or deleted meanwhile. Refresh the list.',
    });
    expect(describeError(new ApiError('server', 500, 'OptimisticLockingException', 'locked'), en).title).toBe(
      'Someone else changed this at the same time.',
    );
    expect(describeError(new ApiError('client', 409), en).hint).toBe('Refresh to see the current state, then try again.');
  });
});
