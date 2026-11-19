/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { config } from '../config';
import { env } from '../env';

export type ApiErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'network'
  | 'client'
  | 'server';

/** Uniform error of every API call. */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number;
  /** exception type reported by the REST API, if any */
  readonly serverType?: string;
  /** message reported by the REST API, if any; plain text, never HTML */
  readonly serverMessage?: string;

  constructor(kind: ApiErrorKind, status: number, serverType?: string, serverMessage?: string) {
    super(serverMessage ?? `Request failed (${kind}${status ? ` ${status}` : ''})`);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = status;
    this.serverType = serverType;
    this.serverMessage = serverMessage;
  }
}

export function isApiError(error: unknown, kind?: ApiErrorKind): error is ApiError {
  return error instanceof ApiError && (kind === undefined || error.kind === kind);
}

export type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'OPTIONS';
  query?: Record<string, QueryValue>;
  /** sent as JSON body */
  json?: unknown;
  /** sent as `application/x-www-form-urlencoded` body */
  form?: Record<string, string>;
  /** sent as `multipart/form-data` body, e.g. files of a deployment */
  multipart?: FormData;
  accept?: string;
  signal?: AbortSignal;
  /** a 401 of this request does not end the session of the user interface */
  silentUnauthorized?: boolean;
  /** sent as body as it is, with the given content type (brokered requests of forms) */
  raw?: { body: string; contentType: string };
  /** query string as it is, e.g. `?a=1&a=2`; replaces `query` */
  search?: string;
}

const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_REQUIRED = 'Required';
const SAFE_METHODS = new Set(['GET', 'OPTIONS']);

let csrfToken: string | null = null;
// after a login the session is replaced: the token cookie is outdated until
// the next response announces a new token
let cookieOutdated = false;

const unauthorizedListeners = new Set<() => void>();

/** Registers a listener that is called whenever the API answers 401. */
export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
}

/**
 * Reports that the session ended although no request of this application
 * failed, e.g. a request of an embedded form in its frame.
 */
export function notifyUnauthorized(): void {
  unauthorizedListeners.forEach((listener) => listener());
}

/** Must be called when the session changed (login), so that a new token is fetched. */
export function invalidateCsrfToken(): void {
  csrfToken = null;
  cookieOutdated = true;
}

/** Test support. */
export function resetHttpState(): void {
  csrfToken = null;
  cookieOutdated = false;
  unauthorizedListeners.clear();
}

function readTokenCookie(): string | null {
  const prefix = `${config.csrfCookieName}=`;
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.substring(prefix.length)) || null;
    }
  }
  return null;
}

function rememberToken(response: Response): void {
  const token = response.headers.get(CSRF_HEADER);
  if (token && token !== CSRF_REQUIRED) {
    csrfToken = token;
    cookieOutdated = false;
  }
}

/**
 * Builds the URL of an API resource. Requests are only ever sent to the
 * origin of the page, so that neither the session nor the CSRF token can
 * reach another host.
 */
export function apiUrl(path: string, query?: Record<string, QueryValue>): string {
  if (!path.startsWith('/')) {
    throw new Error(`API path must start with a slash: ${path}`);
  }
  const url = new URL(`${env.appRoot}/api${path}`, window.location.origin);
  if (url.origin !== window.location.origin) {
    throw new Error('Refusing to send a request to another origin');
  }
  if (query) {
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) {
        url.searchParams.set(name, String(value));
      }
    }
  }
  return url.pathname + url.search;
}

/** Encodes a value for use as one path segment. */
export function seg(value: string): string {
  return encodeURIComponent(value);
}

async function currentToken(): Promise<string | null> {
  if (csrfToken) {
    return csrfToken;
  }
  if (!cookieOutdated) {
    const cookie = readTokenCookie();
    if (cookie) {
      return cookie;
    }
  }
  // a reading request makes the server issue a token for the session
  await send('/engine/engine', {}).catch(() => undefined);
  return csrfToken ?? readTokenCookie();
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: options.accept ?? 'application/json',
  };
  let body: string | FormData | undefined;
  if (options.multipart !== undefined) {
    // the browser adds the content type with the boundary of the parts
    body = options.multipart;
  } else if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.json);
  } else if (options.raw !== undefined) {
    if (options.raw.contentType) {
      headers['Content-Type'] = options.raw.contentType;
    }
    body = options.raw.body;
  } else if (options.form !== undefined) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    body = new URLSearchParams(options.form).toString();
  }
  if (!SAFE_METHODS.has(method)) {
    const token = await currentToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }

  let response: Response;
  try {
    response = await fetch(apiUrl(path, options.query) + (options.search ?? ''), {
      method,
      headers,
      body,
      credentials: 'same-origin',
      redirect: 'error',
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError('network', 0);
  }
  rememberToken(response);
  return response;
}

function isCsrfRejection(response: Response, text: string): boolean {
  return (
    response.status === 403 &&
    (response.headers.get(CSRF_HEADER) === CSRF_REQUIRED || text.includes('CSRFPreventionFilter'))
  );
}

function toError(response: Response, text: string): ApiError {
  let serverType: string | undefined;
  let serverMessage: string | undefined;
  if ((response.headers.get('Content-Type') ?? '').includes('json') && text) {
    try {
      const parsed: unknown = JSON.parse(text);
      if (parsed && typeof parsed === 'object') {
        const values = parsed as Record<string, unknown>;
        if (typeof values.type === 'string') serverType = values.type;
        if (typeof values.message === 'string') serverMessage = values.message;
      }
    } catch {
      // not JSON after all
    }
  }
  const status = response.status;
  const kind: ApiErrorKind =
    status === 401
      ? 'unauthorized'
      : status === 403
        ? 'forbidden'
        : status === 404
          ? 'notFound'
          : status >= 500
            ? 'server'
            : 'client';
  return new ApiError(kind, status, serverType, serverMessage);
}

/** The response of {@link exchange}, as it came from the server. */
export interface RawResponse {
  status: number;
  contentType: string;
  text: string;
}

/**
 * Sends a request to the REST API and returns the response as it is, also
 * for errors: used to perform the requests of embedded forms, whose forms
 * SDK interprets the response itself. A 401 ends the session as usual.
 */
export async function exchange(path: string, options: RequestOptions = {}): Promise<RawResponse> {
  let response = await send(path, options);
  let text = await response.text();
  if (isCsrfRejection(response, text)) {
    invalidateCsrfToken();
    response = await send(path, options);
    text = await response.text();
  }
  if (response.status === 401 && !options.silentUnauthorized) {
    notifyUnauthorized();
  }
  return { status: response.status, contentType: response.headers.get('Content-Type') ?? '', text };
}

/** Downloads a resource of the REST API, e.g. the file of a variable. */
export async function requestBlob(
  path: string,
  options: RequestOptions = {},
): Promise<{ blob: Blob; filename: string | null }> {
  const response = await send(path, options);
  if (!response.ok) {
    const error = toError(response, await response.text());
    if (error.kind === 'unauthorized') {
      notifyUnauthorized();
    }
    throw error;
  }
  return { blob: await response.blob(), filename: dispositionFilename(response.headers.get('Content-Disposition')) };
}

/** The file name of a `Content-Disposition` header, if any. */
export function dispositionFilename(header: string | null): string | null {
  if (!header) {
    return null;
  }
  const extended = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header);
  if (extended?.[1]) {
    try {
      return decodeURIComponent(extended[1].trim());
    } catch {
      // fall back to the plain name
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header);
  return plain?.[1]?.trim() ?? null;
}

/**
 * Sends a request to the REST API of the web applications and returns the
 * parsed response: JSON as object, other content as string and an empty
 * response as `undefined`.
 */
export async function request<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);
  let text = await response.text();

  if (isCsrfRejection(response, text)) {
    // the server dropped the session; fetch a new token and try once more
    invalidateCsrfToken();
    response = await send(path, options);
    text = await response.text();
  }

  if (!response.ok) {
    const error = toError(response, text);
    if (error.kind === 'unauthorized' && !options.silentUnauthorized) {
      notifyUnauthorized();
    }
    throw error;
  }

  if (!text) {
    return undefined as T;
  }
  if ((response.headers.get('Content-Type') ?? '').includes('json')) {
    return JSON.parse(text) as T;
  }
  return text as T;
}
