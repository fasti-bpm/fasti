/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { onUnauthorized } from '../../api/http';
import type { FormTarget } from '../../api/types';
import { config } from '../../config';
import { I18nProvider } from '../../i18n';
import { ThemeProvider } from '../../theme/theme';
import { EmbeddedForm, FRAME_SANDBOX } from '../EmbeddedForm';
import { callbackUrl, externalFormUrl } from '../ExternalForm';
import type { FormRendererProps } from '../types';
import {
  FRAME_SOURCE,
  HOST_SOURCE,
  MAX_FRAME_HEIGHT,
  acceptFrameEvent,
  parseFrameMessage,
} from './protocol';

const ORIGIN = window.location.origin;

describe('frame messages', () => {
  it('accepts the known messages of the frame only', () => {
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'ready' })).toEqual({ type: 'ready' });
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'state', valid: true, dirty: 1 })).toEqual(
      { type: 'state', valid: true, dirty: false },
    );
    expect(parseFrameMessage({ source: HOST_SOURCE, type: 'ready' })).toBeNull();
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'eval', code: 'x' })).toBeNull();
    expect(parseFrameMessage('ready')).toBeNull();
    expect(parseFrameMessage(null)).toBeNull();
    expect(parseFrameMessage([FRAME_SOURCE])).toBeNull();
  });

  it('keeps the height of the frame within bounds', () => {
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'resize', height: 120.2 })).toEqual({
      type: 'resize',
      height: 121,
    });
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'resize', height: 1e9 })).toEqual({
      type: 'resize',
      height: MAX_FRAME_HEIGHT,
    });
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'resize', height: -1 })).toBeNull();
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'resize', height: '10' })).toBeNull();
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'resize', height: NaN })).toBeNull();
  });

  it('copies only the known properties of a started process instance', () => {
    expect(
      parseFrameMessage({
        source: FRAME_SOURCE,
        type: 'submitted',
        processInstance: { id: 'pi-1', definitionId: 'invoice:1:2', businessKey: 'B', extra: 1 },
      }),
    ).toEqual({
      type: 'submitted',
      processInstance: { id: 'pi-1', definitionId: 'invoice:1:2', businessKey: 'B' },
    });
    expect(
      parseFrameMessage({ source: FRAME_SOURCE, type: 'submitted', processInstance: { id: '../x' } }),
    ).toEqual({ type: 'submitted', processInstance: null });
  });

  it('limits texts of the frame', () => {
    const message = parseFrameMessage({ source: FRAME_SOURCE, type: 'error', message: 'x'.repeat(5000) });
    expect(message?.type === 'error' && message.message.length).toBe(2000);
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'failed', message: { html: 1 } })).toEqual({
      type: 'failed',
      prevented: false,
      message: '',
    });
  });

  it('accepts events of the given frame and of this origin only', () => {
    const frame = {} as Window;
    const other = {} as Window;
    const data = { source: FRAME_SOURCE, type: 'loaded' };
    const event = (source: Window, origin: string) =>
      ({ data, origin, source }) as unknown as MessageEvent;

    expect(acceptFrameEvent(event(frame, ORIGIN), frame)).toEqual({ type: 'loaded' });
    expect(acceptFrameEvent(event(other, ORIGIN), frame)).toBeNull();
    expect(acceptFrameEvent(event(frame, 'https://evil.example'), frame)).toBeNull();
    expect(acceptFrameEvent(event(frame, 'null'), frame)).toBeNull();
    expect(acceptFrameEvent(event(frame, ORIGIN), null)).toBeNull();
  });
});

function renderForm(overrides: Partial<FormRendererProps> = {}) {
  const props: FormRendererProps = {
    target: { type: 'task', taskId: 'task-1' } as FormTarget,
    form: { kind: 'embedded', info: { key: 'embedded:app:forms/a.html', contextPath: '/invoice' } },
    disabled: false,
    submitLabel: 'Complete',
    submit: vi.fn(),
    submitted: vi.fn(),
    reportFailure: vi.fn(),
    ...overrides,
  };
  const tree = (current: FormRendererProps) => (
    <I18nProvider initial="en">
      <ThemeProvider>
        <EmbeddedForm {...current} />
      </ThemeProvider>
    </I18nProvider>
  );
  const { rerender } = render(tree(props));
  const frame = document.querySelector('iframe') as HTMLIFrameElement;
  const window = frame.contentWindow as Window;
  const posted = vi.spyOn(window, 'postMessage').mockImplementation(() => {});
  // the frame is sandboxed with an opaque origin by default
  const send = (data: Record<string, unknown>, origin = 'null', source: Window = window) =>
    act(() => {
      globalThis.window.dispatchEvent(
        new MessageEvent('message', { data: { source: FRAME_SOURCE, ...data }, origin, source }),
      );
    });
  const sent = () => posted.mock.calls.map(([message, origin]) => ({ message, origin }));
  const update = (changes: Partial<FormRendererProps>) => rerender(tree({ ...props, ...changes }));
  return { props, frame, send, sent, update, posted };
}

describe('EmbeddedForm', () => {
  it('renders the form frame sandboxed without an origin, below its own path', () => {
    const { frame } = renderForm();

    expect(frame.getAttribute('src')).toBe('/fasti/app/forms/default/');
    expect(frame.getAttribute('sandbox')).toBe(FRAME_SANDBOX);
    expect(FRAME_SANDBOX.split(' ')).not.toContain('allow-same-origin');
    expect(FRAME_SANDBOX.split(' ')).not.toContain('allow-top-navigation');
    expect(FRAME_SANDBOX.split(' ')).not.toContain('allow-modals');
  });

  it('tells the ready frame what to render, with the broker as transport', async () => {
    config.csrfCookieName = 'MY-TOKEN';
    const { send, sent } = renderForm();

    await send({ type: 'ready' });

    expect(sent()).toContainEqual({
      // an opaque origin cannot be addressed; nothing secret is sent
      origin: '*',
      message: {
        source: HOST_SOURCE,
        type: 'init',
        target: { type: 'task', taskId: 'task-1' },
        disabled: false,
        theme: 'light',
        csrfCookieName: 'MY-TOKEN',
        transport: 'broker',
        storedValues: null,
      },
    });
  });

  it('repeats a change of the state that reached the frame before its form ran', async () => {
    const { send, sent, update, posted } = renderForm({ disabled: true });
    await send({ type: 'ready' });
    // the task is claimed while the frame still loads its form; the frame cannot take the change yet
    update({ disabled: false });
    posted.mockClear();

    await send({ type: 'loaded' });

    expect(sent().map(({ message }) => message)).toContainEqual({ source: HOST_SOURCE, type: 'disabled', disabled: false });
  });

  it('ignores messages of other origins and windows', async () => {
    const { send } = renderForm();

    await send({ type: 'loaded' }, 'https://evil.example');
    // a sandboxed frame cannot send with the origin of this page
    await send({ type: 'loaded' }, ORIGIN);
    await send({ type: 'loaded' }, 'null', globalThis.window);

    expect(screen.queryByRole('button', { name: 'Complete' })).not.toBeInTheDocument();
  });

  it('submits through the frame and reports the completion', async () => {
    const user = userEvent.setup();
    const { props, frame, send, sent } = renderForm({
      target: { type: 'start', processDefinitionId: 'invoice:1:2' },
      submitLabel: 'Start',
    });

    await send({ type: 'loaded' });
    await send({ type: 'resize', height: 321 });
    await send({ type: 'state', valid: false, dirty: false });
    expect(frame.style.height).toBe('321px');
    expect(screen.getByRole('button', { name: 'Start' })).toBeDisabled();

    await send({ type: 'state', valid: true, dirty: true });
    await user.click(screen.getByRole('button', { name: 'Start' }));

    expect(sent().map(({ message }) => (message as { type: string }).type)).toContain('submit');
    expect(props.reportFailure).toHaveBeenCalledWith(null);

    await send({
      type: 'submitted',
      processInstance: { id: 'pi-1', definitionId: 'invoice:1:2', businessKey: null },
    });
    expect(props.submitted).toHaveBeenCalledWith({
      id: 'pi-1',
      definitionId: 'invoice:1:2',
      businessKey: null,
    });
    expect(props.submit).not.toHaveBeenCalled();
  });

  it('reports failures and prevented submits', async () => {
    const user = userEvent.setup();
    const { props, send } = renderForm();
    await send({ type: 'loaded' });
    await send({ type: 'state', valid: true, dirty: false });

    await user.click(screen.getByRole('button', { name: 'Complete' }));
    await send({ type: 'failed', prevented: true, message: 'camForm submission prevented' });
    expect(screen.getByText('The form was not submitted. Check the entered values.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Complete' }));
    await send({ type: 'failed', prevented: false, message: 'Variable missing' });
    expect(props.reportFailure).toHaveBeenLastCalledWith(new Error('Variable missing'));
  });

  it('stores the entered values on request', async () => {
    const user = userEvent.setup();
    const { send, sent } = renderForm();
    await send({ type: 'loaded' });

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(sent().map(({ message }) => (message as { type: string }).type)).toContain('store');
    expect(screen.getByText('The entered values are saved in this browser.')).toBeInTheDocument();
  });

  it('shows why the form could not be loaded', async () => {
    const { send } = renderForm();

    await send({ type: 'error', message: 'Form must provide exactly one element <form ..>' });

    expect(screen.getByText('The form could not be loaded')).toBeInTheDocument();
    expect(screen.getByText('Form must provide exactly one element <form ..>')).toBeInTheDocument();
  });

  it('passes an ended session on to the application', async () => {
    const listener = vi.fn();
    onUnauthorized(listener);
    const { send } = renderForm();

    await send({ type: 'unauthorized' });

    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe('external forms', () => {
  const info = (key: string) => ({ key, contextPath: '/invoice' });

  it('builds the address with the task and the callback', () => {
    expect(
      externalFormUrl(info('https://forms.example.org/review?x=1'), { taskId: 't 1' }, 'https://h/cb#/tasklist'),
    ).toBe(
      'https://forms.example.org/review?x=1&taskId=t+1&callbackUrl=https%3A%2F%2Fh%2Fcb%23%2Ftasklist',
    );
    expect(
      externalFormUrl(info('app:forms/start.jsf'), { processDefinitionKey: 'invoice' }, 'cb', 'https://h'),
    ).toBe('https://h/invoice/forms/start.jsf?processDefinitionKey=invoice&callbackUrl=cb');
  });

  it('rejects addresses that are no web pages', () => {
    expect(externalFormUrl(info('javascript:alert(1)'), { taskId: 't' }, 'cb')).toBeNull();
    expect(externalFormUrl(info('data:text/html,x'), { taskId: 't' }, 'cb')).toBeNull();
    expect(externalFormUrl(info('app:../../x'), { taskId: 't' }, 'cb')).toBeNull();
    expect(externalFormUrl({ key: 'app:x', contextPath: null }, { taskId: 't' }, 'cb')).toBeNull();
    expect(externalFormUrl({ key: null, contextPath: null }, { taskId: 't' }, 'cb')).toBeNull();
  });

  it('sends the user back to the Tasklist of this application', () => {
    expect(callbackUrl()).toBe(`${ORIGIN}${window.location.pathname}#/tasklist`);
  });
});
