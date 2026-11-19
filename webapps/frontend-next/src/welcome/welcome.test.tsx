/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { Providers, createQueryClient, routes } from '../app/App';
import { preloadRoutes } from '../test/routes';
import type { Locale } from '../i18n';
import { classicWebapps, readClassicWebapps } from '../env';
import { empty, fakeServer, json, type FakeHandler } from '../test/server';

preloadRoutes(routes);

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';

const links = (...rels: string[]) => () => json({ links: rels.map((rel) => ({ rel, href: '', method: 'GET' })) });

function backend(options: { apps?: string[]; readOnly?: boolean; routes?: Record<string, FakeHandler> } = {}) {
  const profile = { id: 'demo', firstName: 'Demo', lastName: 'User', email: 'demo@example.org' };
  return fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: options.apps ?? ['tasklist', 'cockpit', 'admin', 'welcome'] }),
    [`GET ${ENGINE}/filter`]: () => json([]),
    [`GET ${ENGINE}/user/demo/profile`]: () => json(profile),
    [`OPTIONS ${ENGINE}/user/demo`]: options.readOnly ? links('self') : links('self', 'update', 'delete'),
    [`PUT ${ENGINE}/user/demo/profile`]: (request) => {
      Object.assign(profile, request.body);
      return empty();
    },
    [`PUT ${ENGINE}/user/demo/credentials`]: () => empty(),
    [`POST ${ENGINE}/identity/password-policy`]: () => json({ type: 'NotFound', message: 'no policy' }, 404),
    ...options.routes,
  });
}

function renderApp(path = '/', locale: Locale = 'en') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers queryClient={createQueryClient()} locale={locale}>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe('welcome page', () => {
  it('is the landing page and offers the applications the user may use', async () => {
    backend({ apps: ['tasklist', 'cockpit', 'welcome'] });
    const router = renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Welcome, Demo User' })).toBeVisible();
    expect(router.state.location.pathname).toBe('/');

    const apps = screen.getByRole('list', { name: 'Applications' });
    expect(within(apps).getByRole('link', { name: /Tasks/ })).toHaveAttribute('href', '/tasklist');
    expect(within(apps).getByRole('link', { name: /Cockpit/ })).toHaveAttribute('href', '/cockpit');
    // the user of this test may not use the administration
    expect(within(apps).queryByRole('link', { name: /Admin/ })).toBeNull();
  });

  it('offers quick links to the new sections and to the classic applications', async () => {
    backend({ apps: ['tasklist', 'welcome'] });
    renderApp('/');

    const heading = await screen.findByRole('heading', { name: 'Quick links' });
    const card = within(heading.closest('section') as HTMLElement);
    expect(card.getByRole('link', { name: 'Start process' })).toHaveAttribute('href', '/processes');
    expect(card.getByRole('link', { name: 'Classic Tasklist' })).toHaveAttribute('href', '/fasti/app/tasklist/default/');
    expect(card.getByRole('link', { name: 'Classic Welcome' })).toHaveAttribute('href', '/fasti/app/welcome/default/');
    expect(card.queryByRole('link', { name: 'Classic Cockpit' })).toBeNull();
    expect(card.queryByRole('link', { name: 'Incidents' })).toBeNull();
    expect(card.getByText(/Every kind of task form opens here/)).toBeVisible();
  });

  it('leaves out the classic applications when the server does not serve them', async () => {
    classicWebapps.enabled = false;
    try {
      backend({ apps: ['tasklist', 'cockpit', 'admin', 'welcome'] });
      renderApp('/');

      const heading = await screen.findByRole('heading', { name: 'Quick links' });
      const card = within(heading.closest('section') as HTMLElement);
      expect(card.getByRole('link', { name: 'Start process' })).toBeVisible();
      expect(card.queryByRole('link', { name: /Classic/ })).toBeNull();
      expect(card.queryByText(/Every kind of task form opens here/)).toBeNull();
    } finally {
      classicWebapps.enabled = true;
    }
  });

  it('reads from the page whether the classic applications are served', () => {
    const page = (content?: string) => {
      const doc = document.implementation.createHTMLDocument('');
      if (content !== undefined) {
        const meta = doc.createElement('meta');
        meta.name = 'fasti-classic-webapps';
        meta.content = content;
        doc.head.append(meta);
      }
      return doc;
    };
    expect(readClassicWebapps(page('false'))).toBe(false);
    expect(readClassicWebapps(page('true'))).toBe(true);
    // a server that does not announce it, or the development server
    expect(readClassicWebapps(page())).toBe(true);
    expect(readClassicWebapps(page('$CLASSIC_WEBAPPS'))).toBe(true);
  });

  it('shows the first steps while nothing is deployed, and only then', async () => {
    backend({ routes: { [`GET ${ENGINE}/process-definition/count`]: () => json({ count: 0 }) } });
    renderApp('/');
    const steps = await screen.findByRole('heading', { name: 'Get your first process running' });
    expect(steps).toBeVisible();
    expect(screen.getByRole('link', { name: 'Draw a process in the Modeler' })).toHaveAttribute('href', '/cockpit/modeler');
    expect(screen.getByRole('link', { name: /Deploy it/ })).toHaveAttribute('href', '/cockpit/deployments');
  });

  it('leaves out the first steps once a process is deployed', async () => {
    backend({ routes: { [`GET ${ENGINE}/process-definition/count`]: () => json({ count: 3 }) } });
    renderApp('/');
    expect(await screen.findByRole('heading', { name: /Welcome/ })).toBeVisible();
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Get your first process running' })).not.toBeInTheDocument());
  });

  it('explains a missing access to every application', async () => {
    backend({ apps: ['welcome'] });
    renderApp('/');

    expect(await screen.findByText('Your account has no access to any application')).toBeVisible();
  });

  it('saves the own profile and shows the new name', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/');

    const firstName = await screen.findByLabelText('First name');
    await waitFor(() => expect(firstName).toBeEnabled());
    await user.clear(firstName);
    await user.type(firstName, 'Dana');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Profile saved')).toBeVisible();
    const [saved] = server.find('PUT', `${ENGINE}/user/demo/profile`);
    expect(saved?.body).toEqual({ id: 'demo', firstName: 'Dana', lastName: 'User', email: 'demo@example.org' });
    // the session is reloaded, so that the header and the greeting show the new name
    expect(await screen.findByRole('heading', { name: 'Welcome, Dana User' })).toBeVisible();
  });

  it('rejects an invalid e-mail address', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/');

    const email = await screen.findByLabelText('E-mail');
    await waitFor(() => expect(email).toBeEnabled());
    await user.clear(email);
    await user.type(email, 'not an address');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/e-mail address/i)).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/user/demo/profile`)).toHaveLength(0);
  });

  it('changes the own password with the current one', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/');

    await user.click(await screen.findByRole('tab', { name: 'Password' }));
    await user.type(screen.getByLabelText(/^New password/), 'n3w-Secret!');
    await user.type(screen.getByLabelText(/^Repeat the password/), 'n3w-Secret!');
    await user.type(screen.getByLabelText(/^Current password/), 'old-secret');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText('Password changed')).toBeVisible();
    const [change] = server.find('PUT', `${ENGINE}/user/demo/credentials`);
    expect(change?.body).toEqual({ password: 'n3w-Secret!', authenticatedUserPassword: 'old-secret' });
  });

  it('shows the profile read-only when the identity provider is read-only', async () => {
    backend({ readOnly: true });
    renderApp('/');

    expect(await screen.findByText('Read-only identity provider')).toBeVisible();
    expect(screen.getByLabelText('First name')).toBeDisabled();
    expect(screen.queryByRole('tab', { name: 'Password' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('is translated to Vietnamese', async () => {
    backend();
    renderApp('/', 'vi');

    // Vietnamese puts the family name first
    expect(await screen.findByRole('heading', { name: 'Xin chào, User Demo' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Hồ sơ của bạn' })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Liên kết nhanh' })).toBeVisible();
  });
});
