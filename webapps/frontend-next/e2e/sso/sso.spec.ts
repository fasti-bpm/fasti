/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// Single sign-on against a real OpenID Connect provider. Skipped unless the
// E2E_OIDC_* variables are set (see the README of the web UI):
//
//   E2E_OIDC_BASE_URL         root of the web applications, e.g. http://localhost:19181/fasti/
//   E2E_OIDC_USER             user at the identity provider (should be an administrator)
//   E2E_OIDC_PASSWORD         its password
//   E2E_OIDC_USER_ID          engine user id it maps to (default E2E_OIDC_USER)
//   E2E_OIDC_GROUPS           comma-separated groups the user must have, e.g. camunda-admin,accounting
//   E2E_OIDC_TENANTS          comma-separated tenants the user must have
//   E2E_OIDC_ENGINE           engine name (default default)
//   E2E_OIDC_TOKEN_URL        token endpoint; enables the bearer token tests, which get
//   E2E_OIDC_CLIENT_ID        a token from this client with the password grant
//   E2E_OIDC_OTHER_CLIENT_ID  client whose tokens carry another audience (optional)
//   E2E_OIDC_REST_URL         REST API (default ../engine-rest/ of E2E_OIDC_BASE_URL)
//
// The login form of the identity provider is filled through the fields named
// "username" and "password" (Keycloak and most others).
import { expect, test, type Page } from '@playwright/test';

const env = process.env;
const base = env.E2E_OIDC_BASE_URL ? env.E2E_OIDC_BASE_URL.replace(/\/*$/, '/') : '';
const username = env.E2E_OIDC_USER ?? '';
const secret = env.E2E_OIDC_PASSWORD ?? '';
const userId = env.E2E_OIDC_USER_ID ?? username;
const engine = env.E2E_OIDC_ENGINE ?? 'default';
const list = (value: string | undefined) => (value ?? '').split(',').map((v) => v.trim()).filter(Boolean);
const groups = list(env.E2E_OIDC_GROUPS);
const tenants = list(env.E2E_OIDC_TENANTS);
const restUrl = (env.E2E_OIDC_REST_URL ?? (base ? new URL('../engine-rest/', base).toString() : '')).replace(/\/*$/, '/');
const appUrl = `${base}app/next/${engine}/`;
const tokenUrl = env.E2E_OIDC_TOKEN_URL ?? '';
const clientId = env.E2E_OIDC_CLIENT_ID ?? '';
const otherClientId = env.E2E_OIDC_OTHER_CLIENT_ID ?? '';

test.skip(!base || !username || !secret, 'set E2E_OIDC_BASE_URL, E2E_OIDC_USER and E2E_OIDC_PASSWORD');
test.describe.configure({ mode: 'serial' });

/** Starts the login on the login page and signs in at the identity provider. */
async function signIn(page: Page): Promise<void> {
  await page.goto(`${appUrl}#/tasklist`);
  await page.getByRole('link', { name: /^Sign in with/ }).click();
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(secret);
  await page.locator('input[name="password"]').press('Enter');
  await page.waitForURL((url) => url.href.startsWith(appUrl));
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
}

async function json(page: Page, path: string): Promise<unknown> {
  const response = await page.request.get(new URL(path, base).toString(), { headers: { Accept: 'application/json' } });
  expect(response.status(), `${path}: ${await response.text()}`).toBe(200);
  return response.json();
}

test('signs in at the identity provider and maps user, groups and tenants', async ({ page }) => {
  await signIn(page);
  // back on the page that was asked for
  await expect(page).toHaveURL(/#\/tasklist/);

  const session = (await json(page, `api/admin/auth/user/${engine}`)) as { userId: string; authorizedApps: string[] };
  expect(session.userId).toBe(userId);
  if (groups.includes('camunda-admin')) {
    expect(session.authorizedApps).toEqual(expect.arrayContaining(['admin', 'cockpit', 'tasklist']));
  }
  const userGroups = (await json(page, `api/engine/engine/${engine}/group?member=${encodeURIComponent(userId)}`)) as { id: string }[];
  expect(userGroups.map((g) => g.id)).toEqual(expect.arrayContaining(groups));
  const userTenants = (await json(page, `api/engine/engine/${engine}/tenant?userMember=${encodeURIComponent(userId)}`)) as { id: string }[];
  expect(userTenants.map((t) => t.id)).toEqual(expect.arrayContaining(tenants));
  // a session cookie never authenticates the REST API
  const rest = await page.request.get(`${restUrl}engine`);
  expect(rest.status()).toBe(401);
});

test('signing out ends the session here and at the identity provider', async ({ page }) => {
  await signIn(page);
  await page.getByRole('button', { name: 'Account', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  // back from the logout endpoint of the identity provider
  await page.waitForURL((url) => url.href.startsWith(appUrl));
  await expect(page.getByRole('link', { name: /^Sign in with/ })).toBeVisible();
  const probe = await page.request.get(new URL(`api/admin/auth/user/${engine}`, base).toString(), {
    headers: { Accept: 'application/json' },
  });
  // Run answers 401, the web applications on Tomcat and WildFly 404: no session either way
  expect([401, 404]).toContain(probe.status());
  // no single sign-on any more: the identity provider asks for the password again
  await page.getByRole('link', { name: /^Sign in with/ }).click();
  await expect(page.locator('input[name="password"]')).toBeVisible();
});

test.describe('bearer tokens on the REST API', () => {
  test.skip(!tokenUrl || !clientId, 'set E2E_OIDC_TOKEN_URL and E2E_OIDC_CLIENT_ID');

  async function token(page: Page, client: string): Promise<string> {
    const response = await page.request.post(tokenUrl, {
      form: { grant_type: 'password', client_id: client, username, password: secret, scope: 'openid' },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
    return ((await response.json()) as { access_token: string }).access_token;
  }

  const get = (page: Page, path: string, authorization?: string) =>
    page.request.get(`${restUrl}${path}`, { headers: authorization ? { Authorization: authorization } : {} });

  test('a valid token authenticates, others do not', async ({ page }) => {
    const valid = await token(page, clientId);
    for (const path of ['task?maxResults=1', `engine/${engine}/task?maxResults=1`, 'engine']) {
      expect((await get(page, path, `Bearer ${valid}`)).status(), path).toBe(200);
    }
    const identity = await get(page, `identity/groups?userId=${encodeURIComponent(userId)}`, `Bearer ${valid}`);
    expect(identity.status()).toBe(200);
    const groupIds = ((await identity.json()) as { groups: { id: string }[] }).groups.map((g) => g.id);
    expect(groupIds).toEqual(expect.arrayContaining(groups));

    expect((await get(page, 'task')).status()).toBe(401);
    expect((await get(page, 'task', 'Bearer not-a-token')).status()).toBe(401);
    const [header, payload] = valid.split('.');
    expect((await get(page, 'task', `Bearer ${header}.${payload}.AAAA`)).status()).toBe(401);
    expect((await get(page, 'engine/no-such-engine/task', 'Bearer not-a-token')).status()).toBe(401);
  });

  test('a token for another audience is rejected', async ({ page }) => {
    test.skip(!otherClientId, 'set E2E_OIDC_OTHER_CLIENT_ID');
    const other = await token(page, otherClientId);
    expect((await get(page, 'task', `Bearer ${other}`)).status()).toBe(401);
  });
});
