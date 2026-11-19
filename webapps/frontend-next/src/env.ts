/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Where the application is served from. The server replaces the application
 * root placeholder of the index page; the engine name is part of the path:
 * `{appRoot}/app/next/{engine}/`.
 */
export interface Env {
  /** context path plus application path, e.g. `/fasti`; no trailing slash */
  appRoot: string;
  engine: string;
}

const PAGE_PATH = /^(.*)\/app\/next\/([^/]+)\//;

export function readEnv(
  doc: Document = document,
  pathname: string = window.location.pathname,
): Env {
  const match = PAGE_PATH.exec(pathname);
  const meta = doc.querySelector('meta[name="fasti-app-root"]')?.getAttribute('content');

  let appRoot: string;
  if (meta != null && !meta.startsWith('$')) {
    appRoot = meta;
  } else if (match) {
    appRoot = match[1] ?? '';
  } else {
    // development server
    appRoot = '/fasti';
  }

  let engine = 'default';
  if (match?.[2]) {
    try {
      engine = decodeURIComponent(match[2]);
    } catch {
      engine = match[2];
    }
  }
  return { appRoot: appRoot.replace(/\/+$/, ''), engine };
}

export const env: Env = readEnv();

export function assetUrl(file: string): string {
  return `${env.appRoot}/app/next/${file}`;
}

export function enginePageUrl(engine: string): string {
  return `${env.appRoot}/app/next/${encodeURIComponent(engine)}/`;
}

/**
 * How embedded HTML forms are isolated from this application, announced by
 * the server in the index page (`fasti.webapp.forms-isolation`):
 *
 * - `sandboxed` (default): the form frame has an opaque origin and no
 *   session; this application performs the allowed requests for it,
 * - `separate-origin`: the form frame is served from another origin
 *   (`fasti.webapp.forms-origin`), requests are brokered as well,
 * - `same-origin`: the form frame shares origin and session with this
 *   application (the behaviour before the isolation).
 */
export type FormsIsolation = 'sandboxed' | 'separate-origin' | 'same-origin';

export interface FormsSettings {
  isolation: FormsIsolation;
  /** the origin of the form frame in mode `separate-origin`, otherwise null */
  origin: string | null;
}

function meta(doc: Document, name: string): string | null {
  const value = doc.querySelector(`meta[name="${name}"]`)?.getAttribute('content');
  return value == null || value.startsWith('$') ? null : value.trim();
}

/**
 * Reads the forms isolation of the server. Anything unexpected, e.g. a
 * separate origin that is no http(s) origin, falls back to `sandboxed`.
 */
export function readFormsSettings(doc: Document = document): FormsSettings {
  const isolation = meta(doc, 'fasti-forms-isolation');
  if (isolation === 'same-origin') {
    return { isolation, origin: null };
  }
  if (isolation === 'separate-origin') {
    const origin = meta(doc, 'fasti-forms-origin') ?? '';
    try {
      const url = new URL(origin);
      if (
        (url.protocol === 'https:' || url.protocol === 'http:') &&
        url.origin === origin &&
        origin !== window.location.origin
      ) {
        return { isolation, origin };
      }
    } catch {
      // not an origin
    }
  }
  return { isolation: 'sandboxed', origin: null };
}

export const formsSettings: FormsSettings = readFormsSettings();

/**
 * The page that renders embedded HTML forms in a frame. It is served with
 * the policy of the classic applications, outside of `/app/next`; in mode
 * `separate-origin` from the forms origin, with the origin of this page in
 * the fragment, so that the frame knows whom to talk to.
 */
export function formFrameUrl(
  engine: string = env.engine,
  settings: FormsSettings = formsSettings,
): string {
  const path = `${env.appRoot}/app/forms/${encodeURIComponent(engine)}/`;
  if (settings.isolation === 'separate-origin' && settings.origin) {
    return `${settings.origin}${path}#host=${encodeURIComponent(window.location.origin)}`;
  }
  return path;
}

export type ClassicApp = 'tasklist' | 'cockpit' | 'admin' | 'welcome';

/**
 * Whether the server serves the classic applications
 * (`fasti.webapp.classic.enabled`; off in the container image). Only an
 * explicit `false` turns their links off, so that servers that do not
 * announce it keep them.
 */
export function readClassicWebapps(doc: Document = document): boolean {
  return meta(doc, 'fasti-classic-webapps') !== 'false';
}

/** Mutable for tests. */
export const classicWebapps = { enabled: readClassicWebapps() };

export function classicAppUrl(app: ClassicApp, hash = ''): string {
  return `${env.appRoot}/app/${app}/${encodeURIComponent(env.engine)}/${hash}`;
}
