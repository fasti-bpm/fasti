/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { env } from '../env';

/**
 * Single sign-on as announced by the server (`{appRoot}/sso/config`). The
 * endpoint only exists when the server is configured for OpenID Connect; any
 * other answer means that users sign in with user name and password.
 */
export interface SsoConfig {
  enabled: boolean;
  /** name of the identity provider for the sign-in button */
  label: string;
  loginUrl: string;
  logoutUrl: string;
}

const DISABLED: SsoConfig = { enabled: false, label: '', loginUrl: '', logoutUrl: '' };

/** A path on the origin of the page; never a URL of another host. */
function isLocalPath(value: unknown): value is string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\');
}

export async function getSsoConfig(): Promise<SsoConfig> {
  let response: Response;
  try {
    response = await fetch(`${env.appRoot}/sso/config`, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      redirect: 'error',
    });
  } catch {
    return DISABLED;
  }
  if (!response.ok || !(response.headers.get('Content-Type') ?? '').includes('json')) {
    return DISABLED;
  }
  const body = (await response.json().catch(() => null)) as Partial<SsoConfig> | null;
  if (!body || body.enabled !== true || !isLocalPath(body.loginUrl) || !isLocalPath(body.logoutUrl)) {
    return DISABLED;
  }
  return {
    enabled: true,
    label: typeof body.label === 'string' && body.label.trim() ? body.label.trim() : '',
    loginUrl: body.loginUrl,
    logoutUrl: body.logoutUrl,
  };
}

/** Starts the login at the identity provider; the user comes back to `returnTo`. */
export function ssoLoginHref(config: SsoConfig, returnTo: string): string {
  return `${config.loginUrl}?${new URLSearchParams({ return: returnTo }).toString()}`;
}

/**
 * Ends the session on the server and returns the page to continue on: the
 * logout endpoint of the identity provider or a page of this server.
 */
export async function ssoLogout(config: SsoConfig): Promise<string> {
  const fallback = `${window.location.origin}/`;
  try {
    const response = await fetch(config.logoutUrl, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      redirect: 'error',
    });
    if (!response.ok) {
      return fallback;
    }
    const body = (await response.json().catch(() => null)) as { redirectUrl?: unknown } | null;
    if (!body || typeof body.redirectUrl !== 'string') {
      return fallback;
    }
    const target = new URL(body.redirectUrl, window.location.origin);
    return target.protocol === 'https:' || target.protocol === 'http:' ? target.href : fallback;
  } catch {
    return fallback;
  }
}
