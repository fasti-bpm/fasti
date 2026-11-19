/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect, open, test } from '../support/test';
import { PREFIX, appPath, password, user } from '../support/env';

test('signs in and out through the login page', async ({ page, api }) => {
  // a wrong password delays further logins of that user, so use a throwaway one
  const victim = `${PREFIX}login`;
  await api.delete(`user/${victim}`);
  await api.post('user/create', {
    profile: { id: victim, firstName: 'Login', lastName: 'Test', email: 'login@example.org' },
    credentials: { password: 'Secret-e2e-1' },
  });

  await open(page, 'tasklist');
  await expect(page).toHaveURL(/#\/login/);
  await page.getByLabel('User name').fill(victim);
  await page.getByLabel('Password').fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toBeVisible();

  await page.getByLabel('User name').fill(user);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // back on the page that was asked for
  await expect(page).toHaveURL(/#\/tasklist/);
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();

  await page.getByRole('button', { name: 'Account', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/#\/login/);
  // the session is gone on the server, not only in the page
  await page.goto(`${appPath}#/cockpit`);
  await expect(page.getByLabel('User name')).toBeVisible();
});

test('the former path /camunda redirects to /fasti', async ({ page, baseURL }) => {
  const legacy = new URL('../camunda/app/welcome/default/', baseURL).toString();
  const response = await page.request.get(legacy, { maxRedirects: 0 });
  expect([301, 302, 307, 308]).toContain(response.status());
  expect(response.headers()['location']).toContain('/fasti/app/welcome/default/');
  await page.goto(legacy);
  await expect(page).toHaveURL(/\/fasti\//);
});

test('classic web apps that are turned off lead to the new web UI', async ({ page, baseURL }) => {
  const bundle = await page.request.get(new URL('app/cockpit/camunda-cockpit-bootstrap.js', baseURL).toString());
  test.skip(bundle.ok(), 'the classic web apps are served by this server (fasti.webapp.classic.enabled)');
  expect(bundle.status()).toBe(404);

  const classic = new URL('app/cockpit/default/', baseURL).toString();
  const response = await page.request.get(classic, { maxRedirects: 0 });
  expect(response.status()).toBe(302);
  expect(response.headers()['location']).toMatch(/\/fasti\/app\/next\/default\/#\/cockpit$/);

  // a bookmark of the former path ends in the new web UI as well
  await page.goto(new URL('../camunda/app/tasklist/default/', baseURL).toString());
  await expect(page).toHaveURL(/\/fasti\/app\/next\/default\/#\/(tasklist|login)/);
  // the embedded forms frame still gets the configuration of the classic Tasklist
  const config = await page.request.get(new URL('app/tasklist/scripts/config.js', baseURL).toString());
  expect(config.status()).toBe(200);
});
