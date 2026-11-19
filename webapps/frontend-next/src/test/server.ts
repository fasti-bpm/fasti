/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { vi } from 'vitest';

/** A request as the fake server sees it; the path is relative to the API root. */
export interface FakeRequest {
  method: string;
  path: string;
  url: string;
  query: URLSearchParams;
  headers: Headers;
  /** parsed JSON body, form parameters or undefined */
  body: unknown;
}

export type FakeHandler = (request: FakeRequest) => Response | Promise<Response>;

export interface FakeServer {
  /** every request that reached the server, in order */
  requests: FakeRequest[];
  /** requests of one route, e.g. `find('POST', '/engine/engine/default/task/t1/claim')` */
  find: (method: string, path: string) => FakeRequest[];
  /** replaces or adds a route */
  on: (route: string, handler: FakeHandler) => void;
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export function empty(status = 204, headers: Record<string, string> = {}): Response {
  return new Response(null, { status, headers });
}

export function text(body: string, contentType: string, status = 200): Response {
  return new Response(body, { status, headers: { 'Content-Type': contentType } });
}

const API_ROOT = '/fasti/api';

/**
 * Replaces `fetch` with a server that knows the given routes. A route is
 * `METHOD /path` below the API root; unknown routes answer 404, requests to
 * anything but the API root fail the test.
 */
export function fakeServer(routes: Record<string, FakeHandler>): FakeServer {
  const table = new Map(Object.entries(routes));
  const requests: FakeRequest[] = [];

  const fetchStub = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin);
    if (url.origin !== window.location.origin) {
      throw new Error(`Request to a foreign origin: ${url.href}`);
    }
    if (!url.pathname.startsWith(API_ROOT)) {
      // endpoints outside the API root are addressed by their full path, e.g.
      // `GET /fasti/sso/config`; static files such as config.json answer 404
      const outside = table.get(`${init?.method ?? 'GET'} ${url.pathname}`);
      if (outside) {
        const request: FakeRequest = {
          method: init?.method ?? 'GET',
          path: url.pathname,
          url: url.pathname + url.search,
          query: url.searchParams,
          headers: new Headers(init?.headers),
          body: undefined,
        };
        requests.push(request);
        return outside(request);
      }
      return new Response('', { status: 404 });
    }
    const headers = new Headers(init?.headers);
    let body: unknown;
    if (typeof init?.body === 'string') {
      body = (headers.get('Content-Type') ?? '').includes('json')
        ? JSON.parse(init.body)
        : Object.fromEntries(new URLSearchParams(init.body));
    } else if (init?.body instanceof FormData) {
      body = init.body;
    }
    const request: FakeRequest = {
      method: init?.method ?? 'GET',
      path: decodeURIComponent(url.pathname.substring(API_ROOT.length)),
      url: url.pathname + url.search,
      query: url.searchParams,
      headers,
      body,
    };
    requests.push(request);
    const handler = table.get(`${request.method} ${request.path}`);
    if (!handler) {
      return json({ type: 'NotFound', message: `No route ${request.method} ${request.path}` }, 404);
    }
    return handler(request);
  });
  vi.stubGlobal('fetch', fetchStub);

  return {
    requests,
    find: (method, path) =>
      requests.filter((request) => request.method === method && request.path === path),
    on: (route, handler) => {
      table.set(route, handler);
    },
  };
}
