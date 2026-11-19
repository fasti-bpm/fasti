/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetHttpState } from '../../api/http';
import { env, readFormsSettings } from '../../env';
import { FRAME_SANDBOX, FRAME_SANDBOX_WITH_ORIGIN, frameSetup } from '../EmbeddedForm';
import type { ResolvedForm } from '../types';
import {
  authorize,
  authorizeDownload,
  clearDrafts,
  draftKey,
  formResourcePath,
  pathSegments,
  storageKey,
  type BrokerContext,
} from './broker';
import { FormBroker, MAX_PENDING, safeFilename } from './brokerHost';
import { FRAME_SOURCE, parseFrameMessage } from './protocol';

const ORIGIN = 'https://bpm.example.com';
const API = '/fasti/api/engine/engine/default';

const taskContext: BrokerContext = {
  appRoot: '/fasti',
  engine: 'default',
  origin: ORIGIN,
  target: { type: 'task', taskId: 'task-1' },
  formPath: '/invoice/forms/approve.html',
  processInstanceId: 'pi-1',
  processDefinitionId: 'invoice:1:2',
};

const startContext: BrokerContext = {
  appRoot: '/fasti',
  engine: 'default',
  origin: ORIGIN,
  target: { type: 'start', processDefinitionId: 'invoice:1:2' },
  formPath: `${API}/process-definition/invoice%3A1%3A2/deployed-start-form`,
};

const allowed = (context: BrokerContext, method: string, url: string) =>
  authorize(context, { method, url }).allowed;

describe('broker allow-list of a task form', () => {
  it('allows what the forms SDK needs for the own task', () => {
    const urls: [string, string][] = [
      ['GET', `${API}/task/task-1`],
      ['GET', `${API}/task/task-1/form`],
      ['GET', `${API}/task/task-1/form-variables?deserializeValues=false&variableNames=a,b`],
      ['GET', `${API}/task/task-1/deployed-form`],
      ['GET', `${API}/task/task-1/variables/invoiceDocument/data`],
      ['POST', `${API}/task/task-1/submit-form`],
      ['POST', `${API}/task/task-1/complete`],
      ['POST', `${API}/task/task-1/bpmnError`],
      ['POST', `${API}/task/task-1/bpmnEscalation`],
      ['PUT', `${API}/task/task-1/variables/amount`],
      ['GET', `${API}/process-instance/pi-1/variables`],
      ['GET', `${API}/process-instance/pi-1/variables/creditor`],
      ['GET', `${API}/process-definition/invoice%3A1%3A2`],
      ['GET', '/fasti/api/admin/auth/user/default'],
      ['GET', `${ORIGIN}${API}/task/task-1/form`],
      ['get', `${API}/task/task-1/form`],
    ];
    for (const [method, url] of urls) {
      expect(allowed(taskContext, method, url), `${method} ${url}`).toBe(true);
    }
  });

  it('returns the path below /api with the query as it is', () => {
    expect(authorize(taskContext, { method: 'GET', url: `${API}/task/task-1/form-variables?a=1&a=2` })).toEqual({
      allowed: true,
      kind: 'api',
      method: 'GET',
      path: '/engine/engine/default/task/task-1/form-variables',
      search: '?a=1&a=2',
    });
  });

  it('allows the form resource itself, as a resource', () => {
    expect(authorize(taskContext, { method: 'GET', url: '/invoice/forms/approve.html?noCache=1&userId=demo' })).toEqual({
      allowed: true,
      kind: 'resource',
      method: 'GET',
      path: '/invoice/forms/approve.html',
      // the query of a static resource is dropped
      search: '',
    });
    expect(allowed(taskContext, 'POST', '/invoice/forms/approve.html')).toBe(false);
    expect(allowed(taskContext, 'GET', '/invoice/forms/other.html')).toBe(false);
    expect(allowed(taskContext, 'GET', '/invoice/forms/approve.html/../../fasti/api/admin/auth/user')).toBe(false);
  });

  it('allows the scripts of the classic Tasklist configuration, as resources', () => {
    expect(authorize(taskContext, { method: 'GET', url: '/fasti/app/tasklist/scripts/config.js?bust=1' })).toMatchObject({
      allowed: true,
      kind: 'resource',
    });
    expect(allowed(taskContext, 'GET', '/fasti/app/tasklist/custom/a.js')).toBe(true);
    expect(allowed(taskContext, 'POST', '/fasti/app/tasklist/scripts/config.js')).toBe(false);
    expect(allowed(taskContext, 'GET', '/fasti/app/tasklist/default/')).toBe(false);
    expect(allowed(taskContext, 'GET', '/fasti/app/tasklist/index.html')).toBe(false);
    expect(allowed(taskContext, 'GET', '/fasti/app/cockpit/scripts/config.js')).toBe(false);
    expect(allowed(taskContext, 'GET', '/fasti/api/tasklist/plugin/x.js')).toBe(false);
  });

  it('gives a form key that points into the REST API no exception', () => {
    // e.g. embedded:engine://engine/:engine/task or embedded:/fasti/api/...
    for (const formPath of [`${API}/task`, `${API}/user`, '/fasti/api/admin/auth/user/default/login/cockpit']) {
      const context = { ...taskContext, formPath };
      expect(allowed(context, 'GET', `${formPath}?processInstanceId=other`), formPath).toBe(false);
      expect(allowed(context, 'GET', formPath), formPath).toBe(false);
    }
    expect(allowed({ ...taskContext, formPath: `${API}/task/task-1/rendered-form` }, 'GET', `${API}/task/task-1/rendered-form`)).toBe(true);
  });

  it('refuses other tasks, instances and definitions', () => {
    const urls: [string, string][] = [
      ['GET', `${API}/task/task-2`],
      ['GET', `${API}/task/task-2/variables`],
      ['POST', `${API}/task/task-2/complete`],
      ['POST', `${API}/task/task-2/submit-form`],
      ['GET', `${API}/process-instance/pi-2/variables`],
      ['GET', `${API}/process-definition/other:1:1/xml`],
      ['POST', `${API}/process-definition/invoice%3A1%3A2/start`],
      ['GET', '/fasti/api/engine/engine/other/task/task-1/form'],
      ['GET', '/fasti/api/admin/auth/user/other'],
    ];
    for (const [method, url] of urls) {
      expect(allowed(taskContext, method, url), `${method} ${url}`).toBe(false);
    }
  });

  it('refuses everything else of the REST API', () => {
    const urls: [string, string][] = [
      ['GET', `${API}/task`],
      ['GET', `${API}/user`],
      ['POST', `${API}/user/create`],
      ['GET', `${API}/process-instance`],
      ['DELETE', `${API}/process-instance/pi-1`],
      ['POST', `${API}/process-instance/pi-1/variables`],
      ['PUT', `${API}/process-instance/pi-1/variables/x`],
      ['POST', `${API}/task/task-1/claim`],
      ['POST', `${API}/task/task-1/assignee`],
      ['POST', `${API}/task/task-1/delegate`],
      ['DELETE', `${API}/task/task-1`],
      ['DELETE', `${API}/task/task-1/variables/x`],
      ['POST', `${API}/deployment/create`],
      ['GET', `${API}/authorization`],
      ['POST', `${API}/filter/abc/list`],
      ['GET', '/fasti/api/admin/auth/user/default/login/cockpit'],
      ['POST', '/fasti/api/admin/auth/user/default/logout'],
      ['GET', '/fasti/api/cockpit/plugin/base/default/process-instance'],
      ['GET', '/fasti/app/next/default/'],
      ['GET', `${API}/task/task-1/form/extra`],
      ['GET', `${API}/task/task-1/variables/a/data/more`],
    ];
    for (const [method, url] of urls) {
      expect(allowed(taskContext, method, url), `${method} ${url}`).toBe(false);
    }
  });

  it('refuses other methods', () => {
    for (const method of ['OPTIONS', 'HEAD', 'PATCH', 'TRACE', 'CONNECT', '', 'GET POST']) {
      expect(allowed(taskContext, method, `${API}/task/task-1/form`), method).toBe(false);
    }
    expect(authorize(taskContext, { method: 42 as unknown as string, url: `${API}/task/task-1/form` }).allowed).toBe(
      false,
    );
  });

  it('refuses other origins and URLs with credentials', () => {
    const urls = [
      `https://evil.example${API}/task/task-1/form`,
      `//evil.example${API}/task/task-1/form`,
      `http://bpm.example.com${API}/task/task-1/form`,
      `https://bpm.example.com:8443${API}/task/task-1/form`,
      `https://user:pw@bpm.example.com${API}/task/task-1/form`,
      'javascript:alert(1)',
      'data:text/html,x',
      'blob:https://bpm.example.com/abc',
    ];
    for (const url of urls) {
      expect(allowed(taskContext, 'GET', url), url).toBe(false);
    }
  });

  it('refuses paths that the server could read differently', () => {
    const urls = [
      `${API}/task/task-1%2F..%2F..%2Fuser`,
      `${API}/task/task-1%2fcomplete`,
      `${API}/task/task-1%5C..%5Cuser`,
      `${API}/task/task-1;jsessionid=x/form`,
      `${API}/task/task-1/form;x`,
      `${API}//task/task-1/form`,
      `${API}/task/%E0%A4%A/form`,
      `${API}/task/task-1%00/form`,
      `${API}/task/task-2/../task-1/form/../../task-2/complete`,
      `${API}/task/task-1/%2e%2e/task-2/complete`,
      '/fasti/api/engine/engine/default%2Ftask/task-1/form',
    ];
    for (const url of urls) {
      expect(allowed(taskContext, 'POST', url) || allowed(taskContext, 'GET', url), url).toBe(false);
    }
  });

  it('does not let dot segments reach another task', () => {
    // the browser resolves dot segments; the result is what is checked
    expect(allowed(taskContext, 'POST', `${API}/task/task-1/../task-2/complete`)).toBe(false);
    expect(allowed(taskContext, 'POST', `${API}/task/task-2/../task-1/complete`)).toBe(true);
  });

  it('refuses the instance and definition rules while they are unknown', () => {
    const unknown = { ...taskContext, processInstanceId: null, processDefinitionId: null };
    expect(allowed(unknown, 'GET', `${API}/process-instance/pi-1/variables`)).toBe(false);
    expect(allowed(unknown, 'GET', `${API}/process-instance//variables`)).toBe(false);
    expect(allowed(unknown, 'GET', `${API}/process-definition/invoice%3A1%3A2`)).toBe(false);
    expect(allowed(unknown, 'GET', `${API}/task/task-1/form`)).toBe(true);
  });

  it('respects an empty application root', () => {
    const root = { ...taskContext, appRoot: '' };
    expect(allowed(root, 'GET', '/api/engine/engine/default/task/task-1/form')).toBe(true);
    expect(allowed(root, 'GET', `${API}/task/task-1/form`)).toBe(false);
  });
});

describe('broker allow-list of a start form', () => {
  it('allows what the forms SDK needs to start the own process', () => {
    expect(allowed(startContext, 'GET', `${API}/process-definition/invoice:1:2/startForm`)).toBe(true);
    expect(allowed(startContext, 'GET', `${API}/process-definition/invoice%3A1%3A2/form-variables`)).toBe(true);
    expect(allowed(startContext, 'POST', `${API}/process-definition/invoice:1:2/submit-form`)).toBe(true);
    // a deployed form is an endpoint of the REST API, allowed by the rules
    expect(authorize(startContext, { method: 'GET', url: startContext.formPath ?? '' })).toMatchObject({
      allowed: true,
      kind: 'api',
    });
  });

  it('refuses tasks, instances and other definitions', () => {
    expect(allowed(startContext, 'GET', `${API}/task/task-1/form`)).toBe(false);
    expect(allowed(startContext, 'GET', `${API}/process-instance/pi-1/variables`)).toBe(false);
    expect(allowed(startContext, 'POST', `${API}/process-definition/other:1:1/submit-form`)).toBe(false);
    expect(allowed(startContext, 'POST', `${API}/process-definition/key/invoice/start`)).toBe(false);
    expect(allowed(startContext, 'DELETE', `${API}/process-definition/invoice:1:2`)).toBe(false);
    expect(allowed(startContext, 'POST', `${API}/process-definition/invoice:1:2/suspended`)).toBe(false);
  });
});

describe('downloads of isolated forms', () => {
  it('allows files of variables of the own task and instance only', () => {
    expect(authorizeDownload(taskContext, `${API}/task/task-1/variables/doc/data`).allowed).toBe(true);
    expect(authorizeDownload(taskContext, `${API}/process-instance/pi-1/variables/doc/data`).allowed).toBe(true);
    expect(authorizeDownload(taskContext, `${API}/task/task-2/variables/doc/data`).allowed).toBe(false);
    expect(authorizeDownload(taskContext, `${API}/task/task-1/variables/doc`).allowed).toBe(false);
    expect(authorizeDownload(taskContext, `${API}/task/task-1/form`).allowed).toBe(false);
    expect(authorizeDownload(taskContext, '/invoice/forms/approve.html').allowed).toBe(false);
    expect(authorizeDownload(taskContext, `https://evil.example${API}/task/task-1/variables/doc/data`).allowed).toBe(
      false,
    );
  });

  it('cleans file names', () => {
    expect(safeFilename('../../etc/passwd')).toBe('_.._etc_passwd');
    expect(safeFilename('a\u0000b<c>.pdf')).toBe('a_b_c_.pdf');
    expect(safeFilename('...')).toBe('download');
    expect(safeFilename('invoice.pdf')).toBe('invoice.pdf');
  });
});

describe('path segments', () => {
  it('decodes segments and rejects ambiguous ones', () => {
    expect(pathSegments('/a/b%3Ac/')).toEqual(['a', 'b:c']);
    expect(pathSegments('/')).toEqual([]);
    expect(pathSegments('a/b')).toBeNull();
    expect(pathSegments('/a//b')).toBeNull();
    expect(pathSegments('/a/%2e%2e/b')).toBeNull();
    expect(pathSegments('/a/%zz')).toBeNull();
  });
});

describe('form resource of the frame', () => {
  const form = (key: string, contextPath: string | null = '/invoice'): ResolvedForm => ({
    kind: 'embedded',
    info: { key, contextPath },
  });
  const task = { type: 'task', taskId: 't 1' } as const;

  it('is determined like the frame does', () => {
    expect(formResourcePath(form('embedded:app:forms/a.html'), task, '/fasti', 'default', ORIGIN)).toBe(
      '/invoice/forms/a.html',
    );
    expect(formResourcePath(form('embedded:deployment:a.html'), task, '/fasti', 'default', ORIGIN)).toBe(
      `${API}/task/t%201/deployed-form`,
    );
    expect(
      formResourcePath(form('embedded:engine://engine/:engine/task/x/rendered-form'), task, '/fasti', 'default', ORIGIN),
    ).toBe(`${API}/task/x/rendered-form`);
    expect(formResourcePath(form('embedded:/forms/a.html'), task, '/fasti', 'default', ORIGIN)).toBe('/forms/a.html');
  });

  it('is null for forms of other origins and odd keys', () => {
    expect(formResourcePath(form('embedded:https://evil.example/a.html'), task, '/fasti', 'default', ORIGIN)).toBeNull();
    expect(formResourcePath(form('embedded:app:../../x'), task, '/fasti', 'default', ORIGIN)).toBeNull();
    expect(formResourcePath(form('embedded:app:a.html', null), task, '/fasti', 'default', ORIGIN)).toBeNull();
    expect(formResourcePath(form('app:a.html'), task, '/fasti', 'default', ORIGIN)).toBeNull();
  });

  it('keeps the entered values under the key of the forms SDK', () => {
    expect(storageKey(task)).toBe('camForm:t 1');
    expect(storageKey({ type: 'start', processDefinitionId: 'p:1:2' })).toBe('camForm:p:1:2');
  });
});

describe('modes of the forms isolation', () => {
  const doc = (isolation: string, origin = '') => {
    const d = document.implementation.createHTMLDocument('x');
    for (const [name, content] of [
      ['fasti-forms-isolation', isolation],
      ['fasti-forms-origin', origin],
    ] as const) {
      const element = d.createElement('meta');
      element.setAttribute('name', name);
      element.setAttribute('content', content);
      d.head.appendChild(element);
    }
    return d;
  };

  it('reads the mode of the server and falls back to sandboxed', () => {
    expect(readFormsSettings(doc('same-origin'))).toEqual({ isolation: 'same-origin', origin: null });
    expect(readFormsSettings(doc('separate-origin', 'https://forms.example.com'))).toEqual({
      isolation: 'separate-origin',
      origin: 'https://forms.example.com',
    });
    expect(readFormsSettings(doc('$FORMS_ISOLATION'))).toEqual({ isolation: 'sandboxed', origin: null });
    expect(readFormsSettings(doc('none'))).toEqual({ isolation: 'sandboxed', origin: null });
    expect(readFormsSettings(doc('separate-origin', ''))).toEqual({ isolation: 'sandboxed', origin: null });
    expect(readFormsSettings(doc('separate-origin', 'javascript:alert(1)'))).toEqual({
      isolation: 'sandboxed',
      origin: null,
    });
    expect(readFormsSettings(doc('separate-origin', window.location.origin))).toEqual({
      isolation: 'sandboxed',
      origin: null,
    });
    expect(readFormsSettings(doc('separate-origin', 'https://forms.example.com/path'))).toEqual({
      isolation: 'sandboxed',
      origin: null,
    });
  });

  it('embeds and addresses the frame per mode', () => {
    expect(frameSetup({ isolation: 'sandboxed', origin: null }, ORIGIN)).toEqual({
      sandbox: FRAME_SANDBOX,
      frameOrigin: 'null',
      targetOrigin: '*',
      broker: true,
    });
    expect(frameSetup({ isolation: 'separate-origin', origin: 'https://forms.example.com' }, ORIGIN)).toEqual({
      sandbox: FRAME_SANDBOX_WITH_ORIGIN,
      frameOrigin: 'https://forms.example.com',
      targetOrigin: 'https://forms.example.com',
      broker: true,
    });
    // a separate origin that is this origin would share the session
    expect(frameSetup({ isolation: 'separate-origin', origin: ORIGIN }, ORIGIN)).toMatchObject({
      sandbox: FRAME_SANDBOX,
      frameOrigin: 'null',
    });
    expect(frameSetup({ isolation: 'same-origin', origin: null }, ORIGIN)).toEqual({
      sandbox: FRAME_SANDBOX_WITH_ORIGIN,
      frameOrigin: ORIGIN,
      targetOrigin: ORIGIN,
      broker: false,
    });
  });
});

describe('request messages of the frame', () => {
  it('accepts well-formed requests only', () => {
    const request = { source: FRAME_SOURCE, type: 'request', id: 1, method: 'post', url: '/x', body: '{}' };
    expect(parseFrameMessage(request)).toEqual({
      type: 'request',
      id: 1,
      method: 'POST',
      url: '/x',
      accept: '',
      contentType: '',
      body: '{}',
    });
    expect(parseFrameMessage({ ...request, id: 0 })).toBeNull();
    expect(parseFrameMessage({ ...request, id: 1.5 })).toBeNull();
    expect(parseFrameMessage({ ...request, method: 'GET\r\nX: y' })).toBeNull();
    expect(parseFrameMessage({ ...request, url: 'x'.repeat(10_000) })).toBeNull();
    expect(parseFrameMessage({ ...request, body: { a: 1 } })).toBeNull();
    expect(parseFrameMessage({ ...request, contentType: 'application/json\r\nX-Evil: 1' })).toMatchObject({
      contentType: '',
    });
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'storage', value: 1 })).toBeNull();
    expect(parseFrameMessage({ source: FRAME_SOURCE, type: 'download', url: 2 })).toBeNull();
  });
});

describe('FormBroker', () => {
  const fetchMock = vi.fn();
  const form: ResolvedForm = { kind: 'embedded', info: { key: 'embedded:app:forms/a.html', contextPath: '/invoice' } };
  const origin = window.location.origin;

  beforeEach(() => {
    resetHttpState();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=token-1';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  const request = (method: string, url: string, body: string | null = null) => ({
    type: 'request' as const,
    id: 1,
    method,
    url,
    accept: 'application/json',
    contentType: body === null ? '' : 'application/json',
    body,
  });

  it('performs an allowed request with the session and the CSRF token', async () => {
    fetchMock
      .mockResolvedValueOnce(json({ id: 'task-1', processInstanceId: 'pi-1', processDefinitionId: 'd:1:1' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const broker = new FormBroker({ type: 'task', taskId: 'task-1' }, form, 'alice', origin);

    const response = await broker.request(
      request('POST', '/fasti/api/engine/engine/default/task/task-1/submit-form', '{"variables":{}}'),
    );

    expect(response.status).toBe(204);
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe('/fasti/api/engine/engine/default/task/task-1/submit-form');
    expect(init.method).toBe('POST');
    expect(init.credentials).toBe('same-origin');
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-1');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"variables":{}}');
  });

  it('refuses and logs a request outside the allow-list without sending it', async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 'task-1', processInstanceId: 'pi-1', processDefinitionId: 'd:1:1' }));
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const broker = new FormBroker({ type: 'task', taskId: 'task-1' }, form, 'alice', origin);

    const response = await broker.request(request('POST', '/fasti/api/engine/engine/default/user/create', '{}'));

    expect(response.status).toBe(403);
    expect(response.text).toContain('FormRequestRefused');
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('/user/create'));
    // only the task itself was read, for the allow-list
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/fasti/api/engine/engine/default/task/task-1');
  });

  it('refuses bodies that are not JSON', async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 'task-1' }));
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const broker = new FormBroker({ type: 'task', taskId: 'task-1' }, form, 'alice', origin);

    const response = await broker.request({
      ...request('POST', '/fasti/api/engine/engine/default/task/task-1/complete', 'a=1'),
      contentType: 'application/x-www-form-urlencoded',
    });

    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('limits the requests that are performed at once', async () => {
    let release: (value: Response) => void = () => {};
    fetchMock.mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    );
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const broker = new FormBroker({ type: 'start', processDefinitionId: 'd:1:1' }, form, 'alice', origin);

    const requests = Array.from({ length: MAX_PENDING }, () =>
      broker.request(request('GET', '/fasti/api/engine/engine/default/process-definition/d:1:1/startForm')),
    );
    const refused = await broker.request(
      request('GET', '/fasti/api/engine/engine/default/process-definition/d:1:1/startForm'),
    );

    expect(refused.status).toBe(403);
    release(json({}));
    void requests;
  });

  it('keeps the entered values of its own form only', () => {
    const broker = new FormBroker({ type: 'task', taskId: 'task-9' }, form, 'alice', origin);
    broker.store('{"date":1}');
    expect(window.localStorage.getItem(draftKey(env.engine, 'alice', { type: 'task', taskId: 'task-9' }))).toBe('{"date":1}');
    expect(window.localStorage.getItem('fasti.embeddedForm.camForm:task-9')).toBeNull();
    expect(broker.storedValues()).toBe('{"date":1}');
    broker.store('x'.repeat(600 * 1024));
    expect(broker.storedValues()).toBe('{"date":1}');
    broker.store(null);
    expect(broker.storedValues()).toBeNull();
  });

  it('keeps the entered values of each user apart', () => {
    const target = { type: 'task', taskId: 'task-8' } as const;
    const alice = new FormBroker(target, form, 'alice', origin);
    const bob = new FormBroker(target, form, 'bob', origin);
    alice.store('{"secret":1}');
    expect(bob.storedValues()).toBeNull();
    expect(new FormBroker(target, form, null, origin).storedValues()).toBeNull();
    new FormBroker(target, form, null, origin).store('{"x":1}');
    expect(alice.storedValues()).toBe('{"secret":1}');
    alice.store(null);
  });
});

describe('drafts of embedded forms', () => {
  afterEach(() => window.localStorage.clear());

  it('are scoped by engine and user', () => {
    const target = { type: 'task', taskId: 't/1' } as const;
    expect(draftKey('default', 'alice', target)).toBe('fasti.embeddedForm.default/alice/camForm:t/1');
    expect(draftKey('other', 'alice', target)).not.toBe(draftKey('default', 'alice', target));
    expect(draftKey('default', 'a/b', target)).toBe('fasti.embeddedForm.default/a%2Fb/camForm:t/1');
  });

  it('of one user are cleared on logout, those of others are kept', () => {
    const target = { type: 'task', taskId: 't1' } as const;
    window.localStorage.setItem(draftKey('default', 'alice', target), 'a');
    window.localStorage.setItem(draftKey('default', 'alice', { type: 'start', processDefinitionId: 'p:1' }), 'a2');
    window.localStorage.setItem(draftKey('default', 'bob', target), 'b');
    window.localStorage.setItem(draftKey('other', 'alice', target), 'o');
    window.localStorage.setItem('fasti.embeddedForm.camForm:t1', 'legacy');
    window.localStorage.setItem('unrelated', 'u');

    clearDrafts('default', 'alice');

    expect(window.localStorage.getItem(draftKey('default', 'alice', target))).toBeNull();
    expect(window.localStorage.getItem(draftKey('default', 'alice', { type: 'start', processDefinitionId: 'p:1' }))).toBeNull();
    expect(window.localStorage.getItem('fasti.embeddedForm.camForm:t1')).toBeNull();
    expect(window.localStorage.getItem(draftKey('default', 'bob', target))).toBe('b');
    expect(window.localStorage.getItem(draftKey('other', 'alice', target))).toBe('o');
    expect(window.localStorage.getItem('unrelated')).toBe('u');
  });
});
