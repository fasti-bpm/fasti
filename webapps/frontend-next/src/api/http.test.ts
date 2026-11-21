/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it, vi } from 'vitest';
import { config, loadConfig } from '../config';
import { empty, fakeServer, json, text } from '../test/server';
import { ApiError, apiUrl, invalidateCsrfToken, onUnauthorized, request } from './http';

const ENGINES = '/engine/engine';
const CLAIM = '/engine/engine/default/task/t1/claim';

describe('CSRF token', () => {
  it('sends the token that a response announced with every modifying request', async () => {
    const server = fakeServer({
      [`GET ${ENGINES}`]: () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'token-1' }),
      [`POST ${CLAIM}`]: () => empty(),
    });

    await request(ENGINES);
    await request(CLAIM, { method: 'POST', json: { userId: 'demo' } });

    const [claim] = server.find('POST', CLAIM);
    expect(claim?.headers.get('X-XSRF-TOKEN')).toBe('token-1');
    expect(claim?.body).toEqual({ userId: 'demo' });
    // the token is not sent with reading requests
    expect(server.find('GET', ENGINES)[0]?.headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('reads the token from the cookie after a page reload', async () => {
    document.cookie = 'XSRF-TOKEN=from-cookie; path=/';
    const server = fakeServer({ [`POST ${CLAIM}`]: () => empty() });

    await request(CLAIM, { method: 'POST' });

    expect(server.requests).toHaveLength(1);
    expect(server.requests[0]?.headers.get('X-XSRF-TOKEN')).toBe('from-cookie');
  });

  it('uses the cookie name of the installation', async () => {
    config.csrfCookieName = 'MY-TOKEN';
    document.cookie = 'XSRF-TOKEN=wrong; path=/';
    document.cookie = 'MY-TOKEN=right; path=/';
    const server = fakeServer({ [`POST ${CLAIM}`]: () => empty() });

    await request(CLAIM, { method: 'POST' });

    expect(server.requests[0]?.headers.get('X-XSRF-TOKEN')).toBe('right');
  });

  it('waits with a write for settings still on their way, which name the cookie', async () => {
    document.cookie = 'XSRF-TOKEN=wrong; path=/';
    document.cookie = 'MY-TOKEN=right; path=/';
    let answer: (response: Response) => void = () => undefined;
    const server = fakeServer({
      'GET /fasti/app/next/config.json': () => new Promise<Response>((resolve) => (answer = resolve)),
      [`POST ${CLAIM}`]: () => empty(),
    });
    // the page started without the settings
    await loadConfig(1);

    const claim = request(CLAIM, { method: 'POST' });
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(server.find('POST', CLAIM)).toHaveLength(0);
    answer(json({ csrfCookieName: 'MY-TOKEN' }));
    await claim;

    expect(server.find('POST', CLAIM)[0]?.headers.get('X-XSRF-TOKEN')).toBe('right');
  });

  it('fetches a token first if none is known', async () => {
    const server = fakeServer({
      [`GET ${ENGINES}`]: () => json([], 200, { 'X-XSRF-TOKEN': 'fresh' }),
      [`POST ${CLAIM}`]: () => empty(),
    });

    await request(CLAIM, { method: 'POST' });

    expect(server.requests.map((sent) => `${sent.method} ${sent.path}`)).toEqual([
      `GET ${ENGINES}`,
      `POST ${CLAIM}`,
    ]);
    expect(server.requests[1]?.headers.get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('does not trust the cookie after the session was replaced', async () => {
    document.cookie = 'XSRF-TOKEN=before-login; path=/';
    const server = fakeServer({
      [`GET ${ENGINES}`]: () => json([], 200, { 'X-XSRF-TOKEN': 'after-login' }),
      [`POST ${CLAIM}`]: () => empty(),
    });

    invalidateCsrfToken();
    await request(CLAIM, { method: 'POST' });

    expect(server.find('POST', CLAIM)[0]?.headers.get('X-XSRF-TOKEN')).toBe('after-login');
  });

  it('fetches a new token and repeats the request once if the token was rejected', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/';
    let attempts = 0;
    const server = fakeServer({
      [`GET ${ENGINES}`]: () => json([], 200, { 'X-XSRF-TOKEN': 'renewed' }),
      [`POST ${CLAIM}`]: () => {
        attempts++;
        return attempts === 1
          ? text('CSRFPreventionFilter: Invalid HTTP Header Token.', 'text/html', 403)
          : empty();
      },
    });

    await request(CLAIM, { method: 'POST' });

    const claims = server.find('POST', CLAIM);
    expect(claims).toHaveLength(2);
    expect(claims[1]?.headers.get('X-XSRF-TOKEN')).toBe('renewed');
  });
});

describe('errors', () => {
  it('reports a missing session to the listeners', async () => {
    fakeServer({ 'GET /engine/engine/default/task': () => empty(401) });
    const listener = vi.fn();
    const remove = onUnauthorized(listener);

    await expect(request('/engine/engine/default/task')).rejects.toMatchObject({
      kind: 'unauthorized',
      status: 401,
    });
    expect(listener).toHaveBeenCalledTimes(1);

    remove();
    await expect(request('/engine/engine/default/task')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('keeps a failed login from ending the session', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    fakeServer({ 'POST /admin/auth/user/default/login/welcome': () => empty(401) });
    const listener = vi.fn();
    onUnauthorized(listener);

    await expect(
      request('/admin/auth/user/default/login/welcome', {
        method: 'POST',
        form: { username: 'demo', password: 'wrong' },
        silentUnauthorized: true,
      }),
    ).rejects.toMatchObject({ kind: 'unauthorized' });
    expect(listener).not.toHaveBeenCalled();
  });

  it('maps the status and keeps the message of the server', async () => {
    fakeServer({
      'GET /a': () => json({ type: 'AuthorizationException', message: 'not allowed' }, 403),
      'GET /b': () => json({ type: 'InvalidRequestException', message: 'no such task' }, 404),
      'GET /c': () => json({ type: 'ProcessEngineException', message: 'boom' }, 500),
      'GET /d': () => json({ type: 'InvalidRequestException', message: 'bad value' }, 400),
    });

    await expect(request('/a')).rejects.toMatchObject({
      kind: 'forbidden',
      serverMessage: 'not allowed',
      serverType: 'AuthorizationException',
    });
    await expect(request('/b')).rejects.toMatchObject({ kind: 'notFound' });
    await expect(request('/c')).rejects.toMatchObject({ kind: 'server', status: 500 });
    await expect(request('/d')).rejects.toMatchObject({ kind: 'client', serverMessage: 'bad value' });
  });

  it('reports an unreachable server as network error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    await expect(request('/engine/engine')).rejects.toMatchObject({ kind: 'network', status: 0 });
  });
});

describe('requests', () => {
  it('sends form parameters and JSON with the matching content type', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    const server = fakeServer({
      'POST /form': () => empty(),
      'POST /json': () => empty(),
    });

    await request('/form', { method: 'POST', form: { username: 'a b', password: 'p&q' } });
    await request('/json', { method: 'POST', json: { a: 1 } });

    expect(server.requests[0]?.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(server.requests[0]?.body).toEqual({ username: 'a b', password: 'p&q' });
    expect(server.requests[1]?.headers.get('Content-Type')).toBe('application/json');
  });

  it('parses JSON, returns other content as text and nothing for an empty response', async () => {
    fakeServer({
      'GET /json': () => json({ a: 1 }),
      'GET /hal': () =>
        new Response('{"count":2}', { headers: { 'Content-Type': 'application/hal+json' } }),
      'GET /markup': () => text('<form></form>', 'application/xhtml+xml'),
      'GET /nothing': () => empty(),
    });

    expect(await request('/json')).toEqual({ a: 1 });
    expect(await request('/hal')).toEqual({ count: 2 });
    expect(await request('/markup')).toBe('<form></form>');
    expect(await request('/nothing')).toBeUndefined();
  });

  it('only ever addresses the origin of the page', () => {
    expect(apiUrl('/engine/engine', { a: 1, b: undefined, c: 'x y' })).toBe(
      '/fasti/api/engine/engine?a=1&c=x+y',
    );
    expect(() => apiUrl('engine')).toThrow();
    expect(() => apiUrl('https://example.org/')).toThrow();
    // a path cannot turn the URL into one of another host
    expect(apiUrl('//example.org/x')).toBe('/fasti/api//example.org/x');
  });

  it('sends cookies to the own origin only and follows no redirects', async () => {
    const fetchStub = vi.fn<typeof fetch>(async () => json([]));
    vi.stubGlobal('fetch', fetchStub);

    await request('/engine/engine');

    expect(fetchStub.mock.calls[0]?.[0]).toBe('/fasti/api/engine/engine');
    expect(fetchStub.mock.calls[0]?.[1]).toMatchObject({
      credentials: 'same-origin',
      redirect: 'error',
    });
  });
});
