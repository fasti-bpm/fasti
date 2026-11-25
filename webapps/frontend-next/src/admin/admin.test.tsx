/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { Authorization } from '../api/admin';
import { Providers, createQueryClient, routes } from '../app/App';
import { preloadRoutes } from '../test/routes';
import { empty, fakeServer, json, type FakeHandler, type FakeRequest } from '../test/server';
import { permissionSummary, resourceType } from './resources';
import { validEmail, validId } from './UsersPage';

preloadRoutes(routes);

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';

const links = (...rels: string[]) => () => json({ links: rels.map((rel) => ({ rel, href: '', method: 'GET' })) });
const WRITABLE = links('list', 'count', 'create');
const ITEM_WRITABLE = links('self', 'update', 'delete');
const MEMBERS_WRITABLE = links('create', 'delete');

interface Options {
  apps?: string[];
  readOnly?: boolean;
  routes?: Record<string, FakeHandler>;
}

function backend(options: Options = {}) {
  const apps = options.apps ?? ['tasklist', 'cockpit', 'admin', 'welcome'];
  const users = [
    { id: 'demo', firstName: 'Demo', lastName: 'User', email: 'demo@example.org' },
    { id: 'mary', firstName: 'Mary', lastName: 'Major', email: null },
  ];
  const groups = [{ id: 'accounting', name: 'Accounting', type: 'WORKFLOW' }];
  const tenants = [{ id: 'acme', name: 'ACME Corp' }];
  const authorizations: Authorization[] = [
    { id: 'a1', type: 1, permissions: ['ALL'], userId: null, groupId: 'camunda-admin', resourceType: 0, resourceId: '*' },
    { id: 'a2', type: 1, permissions: ['ACCESS'], userId: 'mary', groupId: null, resourceType: 0, resourceId: 'tasklist' },
  ];
  const list = <T,>(items: T[]) => (request: FakeRequest) => {
    const first = Number(request.query.get('firstResult') ?? 0);
    const max = Number(request.query.get('maxResults') ?? 1000);
    return json(items.slice(first, first + max));
  };
  const count = <T,>(items: T[]) => () => json({ count: items.length });
  const matchingUsers = (request: FakeRequest) => {
    if (request.query.get('memberOfGroup')) return users.slice(0, 1);
    const id = request.query.get('id');
    const firstName = request.query.get('firstNameLike')?.replace(/%/g, '').toLowerCase();
    return users.filter(
      (user) => (!id || user.id === id) && (!firstName || user.firstName.toLowerCase().includes(firstName)),
    );
  };
  const item = options.readOnly ? links('self') : ITEM_WRITABLE;
  const members = options.readOnly ? links() : MEMBERS_WRITABLE;

  const server = fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () => json({ userId: 'demo', authorizedApps: apps }),
    [`GET ${ENGINE}/filter`]: () => json([]),
    [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [] }),
    [`POST ${ENGINE}/identity/password-policy`]: () => json({ type: 'NotFound', message: 'no policy' }, 404),

    [`OPTIONS ${ENGINE}/user`]: options.readOnly ? links('list', 'count') : WRITABLE,
    [`GET ${ENGINE}/user`]: (request) => list(matchingUsers(request))(request),
    [`GET ${ENGINE}/user/count`]: (request) => json({ count: matchingUsers(request).length }),
    [`POST ${ENGINE}/user/create`]: (request) => {
      const body = request.body as { profile: (typeof users)[number] };
      users.push({ ...body.profile, email: body.profile.email || null });
      return empty();
    },
    [`GET ${ENGINE}/user/demo/profile`]: () => json(users[0]),
    [`GET ${ENGINE}/user/mary/profile`]: () => json(users[1]),
    [`GET ${ENGINE}/user/new.user/profile`]: () => json(users.find((user) => user.id === 'new.user')),
    [`OPTIONS ${ENGINE}/user/mary`]: item,
    [`OPTIONS ${ENGINE}/user/new.user`]: item,
    [`PUT ${ENGINE}/user/mary/profile`]: () => empty(),
    [`PUT ${ENGINE}/user/mary/credentials`]: () => empty(),
    [`DELETE ${ENGINE}/user/mary`]: () => empty(),

    [`OPTIONS ${ENGINE}/group`]: options.readOnly ? links('list', 'count') : WRITABLE,
    [`GET ${ENGINE}/group`]: (request) => list(request.query.get('member') === 'mary' || request.query.get('memberOfTenant') ? [] : groups)(request),
    [`GET ${ENGINE}/group/count`]: (request) => json({
        count: request.query.get('member') === 'mary' || request.query.get('memberOfTenant') ? 0 : groups.length,
      }),
    [`POST ${ENGINE}/group/create`]: (request) => {
      groups.push(request.body as (typeof groups)[number]);
      return empty();
    },
    [`GET ${ENGINE}/group/accounting`]: () => json(groups[0]),
    [`GET ${ENGINE}/group/sales`]: () => json(groups.find((group) => group.id === 'sales')),
    [`OPTIONS ${ENGINE}/group/accounting`]: item,
    [`OPTIONS ${ENGINE}/group/sales`]: item,
    [`OPTIONS ${ENGINE}/group/_/members`]: members,
    [`OPTIONS ${ENGINE}/group/accounting/members`]: members,
    [`PUT ${ENGINE}/group/accounting/members/mary`]: () => empty(),
    [`PUT ${ENGINE}/group/accounting`]: () => empty(),
    [`DELETE ${ENGINE}/group/accounting`]: () => empty(),

    [`OPTIONS ${ENGINE}/tenant`]: WRITABLE,
    [`GET ${ENGINE}/tenant`]: list(tenants),
    [`GET ${ENGINE}/tenant/count`]: count(tenants),
    [`POST ${ENGINE}/tenant/create`]: (request) => {
      tenants.push(request.body as (typeof tenants)[number]);
      return empty();
    },
    [`GET ${ENGINE}/tenant/acme`]: () => json(tenants[0]),
    [`OPTIONS ${ENGINE}/tenant/acme`]: item,
    [`OPTIONS ${ENGINE}/tenant/_/user-members`]: members,
    [`OPTIONS ${ENGINE}/tenant/acme/user-members`]: members,
    [`OPTIONS ${ENGINE}/tenant/acme/group-members`]: members,
    [`PUT ${ENGINE}/tenant/acme/group-members/accounting`]: () => empty(),
    [`DELETE ${ENGINE}/tenant/acme`]: () => empty(),

    [`OPTIONS ${ENGINE}/authorization`]: WRITABLE,
    [`GET ${ENGINE}/authorization`]: (request) =>
      json(
        authorizations.filter(
          (entry) =>
            String(entry.resourceType) === request.query.get('resourceType') &&
            (!request.query.get('userIdIn') || entry.userId === request.query.get('userIdIn')) &&
            (!request.query.get('groupIdIn') || entry.groupId === request.query.get('groupIdIn')),
        ),
      ),
    [`GET ${ENGINE}/authorization/count`]: (request) =>
      json({
        count: authorizations.filter((entry) => String(entry.resourceType) === request.query.get('resourceType')).length,
      }),
    [`POST ${ENGINE}/authorization/create`]: (request) => {
      const created = { ...(request.body as Omit<Authorization, 'id'>), id: `a${authorizations.length + 1}` };
      authorizations.push(created);
      return json(created);
    },
    // the built-in authorization of the administrators may only be read by this user
    [`OPTIONS ${ENGINE}/authorization/a1`]: links('self'),
    [`OPTIONS ${ENGINE}/authorization/a2`]: item,
    [`PUT ${ENGINE}/authorization/a2`]: () => empty(),
    [`DELETE ${ENGINE}/authorization/a2`]: () => empty(),

    [`GET ${ENGINE}/telemetry/data`]: () =>
      json({
        installation: 'install-1',
        product: {
          name: 'Camunda BPM Runtime',
          version: '7.24.0',
          edition: 'community',
          internals: {
            database: { vendor: 'h2', version: '2.3.232' },
            'application-server': { vendor: 'Apache Tomcat', version: 'Apache Tomcat/10.1' },
            jdk: { vendor: 'Oracle', version: '21.0.2' },
            webapps: ['admin', 'cockpit'],
            'camunda-integration': ['spring-boot-starter', 'custom-thing'],
          },
        },
      }),
    [`GET ${ENGINE}/metrics/root-process-instance-start/sum`]: () => json({ result: 1234 }),
    [`GET ${ENGINE}/metrics/activity-instance-start/sum`]: () => json({ result: 5678 }),
    [`GET ${ENGINE}/metrics/task-users/sum`]: () => json({ type: 'AuthorizationException', message: 'no' }, 403),
    ...options.routes,
  });
  return server;
}

function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers queryClient={createQueryClient()} locale="en">
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe('access', () => {
  it('is closed to users who may not use the administration', async () => {
    const server = backend({ apps: ['tasklist', 'welcome'] });
    const router = renderApp('/admin/users');

    // back to the welcome page
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(screen.queryByRole('link', { name: 'Admin' })).toBeNull();
    expect(server.requests.some((request) => request.path === `${ENGINE}/user` || request.path.startsWith(`${ENGINE}/group`))).toBe(
      false,
    );
  });

  it('is a section of the application for administrators', async () => {
    backend();
    const router = renderApp('/admin');

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
    expect(screen.getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
    const sections = screen.getByRole('navigation', { name: 'Administration' });
    for (const name of ['Users', 'Groups', 'Tenants', 'Authorizations', 'System']) {
      expect(within(sections).getByRole('link', { name })).toBeVisible();
    }
  });
});

describe('users', () => {
  it('lists the users and searches them', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/users');

    expect(await screen.findByRole('link', { name: 'mary' })).toHaveAttribute('href', '/admin/users/mary');
    expect(screen.getByText('2 users')).toBeVisible();

    // no user has the id: the first names are searched next
    await user.type(screen.getByRole('searchbox', { name: 'Search by exact id, name or e-mail' }), 'mar');
    await waitFor(() => expect(screen.queryByRole('link', { name: 'demo' })).toBeNull());
    expect(screen.getByRole('link', { name: 'mary' })).toBeVisible();
    expect(server.requests.some((request) => request.query.get('id') === 'mar')).toBe(true);
    expect(server.requests.some((request) => request.query.get('firstNameLike') === '%mar%')).toBe(true);
  });

  it('tells a search without result from an empty list and clears the search', async () => {
    const user = userEvent.setup();
    const searched = (request: FakeRequest) => [...request.query.values()].some((value) => value.includes('nobody'));
    const server = backend();
    const all = [
      { id: 'demo', firstName: 'Demo', lastName: 'User', email: 'demo@example.org' },
      { id: 'mary', firstName: 'Mary', lastName: 'Anne', email: 'mary@example.org' },
    ];
    server.on(`GET ${ENGINE}/user`, (request) => json(searched(request) ? [] : all));
    server.on(`GET ${ENGINE}/user/count`, (request) => json({ count: searched(request) ? 0 : 2 }));
    const router = renderApp('/admin/users?q=nobody');

    expect(await screen.findByText('No user matches the search')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(await screen.findByRole('link', { name: 'mary' })).toBeVisible();
    expect(screen.getByRole('searchbox', { name: 'Search by exact id, name or e-mail' })).toHaveValue('');
    await waitFor(() => expect(router.state.location.search).not.toContain('q='));
  });

  it('follows the search of an address restored by the browser history', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/admin/users');
    const search = await screen.findByRole('searchbox', { name: 'Search by exact id, name or e-mail' });
    await user.type(search, 'mar');
    await waitFor(() => expect(router.state.location.search).toContain('q=mar'));

    await act(() => router.navigate('/admin/users'));
    await waitFor(() => expect(search).toHaveValue(''));
    // the old text is not written back after the delay of typing
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(router.state.location.search).not.toContain('q=');
  });

  it('creates a user after checking the form', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/admin/users');

    await user.click(await screen.findByRole('button', { name: 'Create user' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/User id/), 'new user');
    await user.type(within(dialog).getByLabelText(/^Password/), 'secret-1');
    await user.type(within(dialog).getByLabelText(/Repeat the password/), 'secret-2');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await within(dialog).findByText('Use letters, digits and . _ - @ only.')).toBeVisible();
    await user.clear(within(dialog).getByLabelText(/User id/));
    await user.type(within(dialog).getByLabelText(/User id/), 'new.user');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));
    expect(await within(dialog).findByText('The two passwords differ.')).toBeVisible();

    await user.clear(within(dialog).getByLabelText(/Repeat the password/));
    await user.type(within(dialog).getByLabelText(/Repeat the password/), 'secret-1');
    await user.type(within(dialog).getByLabelText(/First name/), 'New');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users/new.user'));
    const [created] = server.find('POST', `${ENGINE}/user/create`);
    expect(created?.body).toEqual({
      profile: { id: 'new.user', firstName: 'New', lastName: '', email: '' },
      credentials: { password: 'secret-1' },
    });
    expect(created?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
  });

  it('reports what the password policy of the engine refuses', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`POST ${ENGINE}/identity/password-policy`]: () =>
          json({
            valid: false,
            rules: [
              { placeholder: 'PASSWORD_POLICY_LENGTH', parameter: { minLength: '10' }, valid: false },
              { placeholder: 'PASSWORD_POLICY_DIGIT', parameter: { minDigit: '1' }, valid: false },
              { placeholder: 'PASSWORD_POLICY_UPPERCASE', parameter: { minUpperCase: '1' }, valid: true },
            ],
          }),
      },
    });
    renderApp('/admin/users');

    await user.click(await screen.findByRole('button', { name: 'Create user' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/User id/), 'shorty');
    await user.type(within(dialog).getByLabelText(/^Password/), 'abc');
    await user.type(within(dialog).getByLabelText(/Repeat the password/), 'abc');
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }));

    expect(await within(dialog).findByText('At least 10 characters.')).toBeVisible();
    expect(within(dialog).getByText('At least 1 digit.')).toBeVisible();
    expect(within(dialog).queryByText('At least 1 upper-case letter.')).toBeNull();
    expect(server.find('POST', `${ENGINE}/user/create`)).toHaveLength(0);
  });

  it('edits the profile, changes the password and deletes the user', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/admin/users/mary');

    const firstName = await screen.findByLabelText('First name');
    expect(screen.getByRole('heading', { name: 'Mary Major' })).toBeVisible();
    await user.clear(firstName);
    await user.type(firstName, 'Maria');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/user/mary/profile`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/user/mary/profile`)[0]?.body).toEqual({
      id: 'mary',
      firstName: 'Maria',
      lastName: 'Major',
      email: '',
    });

    await user.click(screen.getByRole('tab', { name: 'Password' }));
    await user.type(await screen.findByLabelText(/New password/), 'n3w-secret');
    await user.type(screen.getByLabelText(/Repeat the password/), 'n3w-secret');
    await user.type(screen.getByLabelText(/Your password \(demo\)/), 'demo');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/user/mary/credentials`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/user/mary/credentials`)[0]?.body).toEqual({
      password: 'n3w-secret',
      authenticatedUserPassword: 'demo',
    });

    await user.click(screen.getByRole('button', { name: 'Delete user' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('User “mary” is deleted');
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
    expect(server.find('DELETE', `${ENGINE}/user/mary`)).toHaveLength(1);
  });

  it('adds the user to a group', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/users/mary?tab=groups');

    expect(await screen.findByText('The user belongs to no group')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Add to groups' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(await within(dialog).findByRole('button', { name: 'Add Accounting' }));

    await waitFor(() => expect(server.find('PUT', `${ENGINE}/group/accounting/members/mary`)).toHaveLength(1));
    expect(await within(dialog).findByText('Added')).toBeVisible();
  });

  it('asks for each group whether the user may be added to it', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`GET ${ENGINE}/group`]: (request) =>
          json(request.query.get('member') ? [] : [
            { id: 'accounting', name: 'Accounting', type: 'WORKFLOW' },
            { id: 'sales', name: 'Sales', type: 'WORKFLOW' },
          ]),
        [`GET ${ENGINE}/group/count`]: (request) => json({ count: request.query.get('member') ? 0 : 2 }),
        // the permission covers the accounting group only
        [`OPTIONS ${ENGINE}/group/sales/members`]: links(),
      },
    });
    renderApp('/admin/users/mary?tab=groups');

    await user.click(await screen.findByRole('button', { name: 'Add to groups' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('button', { name: 'Add Accounting' })).toBeVisible();
    expect(await within(dialog).findByText('Not allowed')).toBeVisible();
    expect(within(dialog).queryByRole('button', { name: 'Add Sales' })).toBeNull();
    expect(server.find('OPTIONS', `${ENGINE}/group/sales/members`)).toHaveLength(1);
  });

  it('offers no changes if the identity provider is read-only', async () => {
    backend({ readOnly: true });
    renderApp('/admin/users');

    expect(await screen.findByText('Read-only identity provider')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Create user' })).toBeNull();
  });

  it('shows a user of a read-only identity provider without the means to change it', async () => {
    backend({ readOnly: true });
    renderApp('/admin/users/mary');

    const firstName = await screen.findByLabelText('First name');
    await waitFor(() => expect(firstName).toBeDisabled());
    expect(screen.queryByRole('tab', { name: 'Password' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete user' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });
});

describe('groups', () => {
  it('creates a group and adds a member', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/admin/groups');

    expect(await screen.findByRole('link', { name: 'accounting' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Create group' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Group id/), 'sales');
    await user.type(within(dialog).getByLabelText('Name'), 'Sales');
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/groups/sales'));
    expect(server.find('POST', `${ENGINE}/group/create`)[0]?.body).toEqual({
      id: 'sales',
      name: 'Sales',
      type: 'WORKFLOW',
    });

    await router.navigate('/admin/groups/accounting?tab=members');
    expect(await screen.findByRole('link', { name: 'demo' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Add members' }));
    const add = await screen.findByRole('dialog');
    // the member is not offered again
    expect(await within(add).findByRole('button', { name: 'Add Mary Major' })).toBeVisible();
    expect(within(add).queryByRole('button', { name: 'Add Demo User' })).toBeNull();
    await user.click(within(add).getByRole('button', { name: 'Add Mary Major' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/group/accounting/members/mary`)).toHaveLength(1));
  });

  it('says when the candidates to add are only the first matches', async () => {
    const user = userEvent.setup();
    const server = backend();
    server.on(`GET ${ENGINE}/user/count`, () => json({ count: 60 }));
    renderApp('/admin/groups/accounting?tab=members');
    await user.click(await screen.findByRole('button', { name: 'Add members' }));
    const add = await screen.findByRole('dialog');
    expect(await within(add).findByText(/Only the first \d+ of 60 matches are shown/)).toBeVisible();
  });

  it('deletes a group after asking', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/admin/groups/accounting');

    await user.click(await screen.findByRole('button', { name: 'Delete group' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/groups'));
    expect(server.find('DELETE', `${ENGINE}/group/accounting`)).toHaveLength(1);
  });
});

describe('tenants', () => {
  it('creates a tenant and adds a group to another', async () => {
    const user = userEvent.setup();
    const server = backend();
    const router = renderApp('/admin/tenants');

    await user.click(await screen.findByRole('button', { name: 'Create tenant' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Tenant id/), 'globex');
    await user.click(within(dialog).getByRole('button', { name: 'Create tenant' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/tenant/create`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/tenant/create`)[0]?.body).toEqual({ id: 'globex', name: null });

    await router.navigate('/admin/tenants/acme?tab=groups');
    await user.click(await screen.findByRole('button', { name: 'Add groups' }));
    await user.click(await within(await screen.findByRole('dialog')).findByRole('button', { name: 'Add Accounting' }));
    await waitFor(() =>
      expect(server.find('PUT', `${ENGINE}/tenant/acme/group-members/accounting`)).toHaveLength(1),
    );
  });
});

describe('authorizations', () => {
  it('lists the authorizations of a resource type', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/authorizations');

    const table = await screen.findByRole('table', { name: 'Application' });
    expect(within(table).getByText('camunda-admin')).toBeVisible();
    expect(within(table).getByText('All permissions')).toBeVisible();
    expect(within(table).getByText('ACCESS')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Process definition' }));
    expect(await screen.findByText('No authorization exists for this resource type')).toBeVisible();
    expect(server.requests.some((request) => request.query.get('resourceType') === '6')).toBe(true);
  });

  it('checks whether a user may do something and names the rules behind it', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`GET ${ENGINE}/authorization/check`]: () => json({ permissionName: 'ACCESS', resourceName: '0', resourceId: 'cockpit', authorized: true }),
        [`GET ${ENGINE}/group`]: () => json([{ id: 'accounting', name: 'Accounting', type: 'WORKFLOW' }]),
        [`GET ${ENGINE}/group/count`]: () => json({ count: 1 }),
        [`GET ${ENGINE}/authorization`]: (request) =>
          json(
            request.query.get('groupIdIn') === 'accounting'
              ? [{ id: 'a9', type: 1, permissions: ['ACCESS'], userId: null, groupId: 'accounting', resourceType: 0, resourceId: 'cockpit' }]
              : [],
          ),
      },
    });
    renderApp('/admin/authorizations');

    await user.click(await screen.findByRole('button', { name: 'Check access' }));
    const dialog = await screen.findByRole('dialog', { name: 'May this user do it?' });
    await user.type(within(dialog).getByRole('combobox', { name: /User/ }), 'mary');
    await user.clear(within(dialog).getByLabelText('Resource ID'));
    await user.type(within(dialog).getByLabelText('Resource ID'), 'cockpit');
    await user.click(within(dialog).getByRole('button', { name: 'Check' }));

    expect(await within(dialog).findByText('mary may ACCESS on cockpit.')).toBeVisible();
    expect(within(dialog).getByRole('list', { name: 'Rules that take part' })).toHaveTextContent('group accounting · cockpit · ACCESS');
    expect(server.find('GET', `${ENGINE}/authorization/check`)[0]?.query.get('userId')).toBe('mary');
  });

  it('creates an authorization with the chosen permissions', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/authorizations?resource=6');

    await user.click(await screen.findByRole('button', { name: 'Create authorization' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('Resource type: Process definition');
    await user.click(within(dialog).getByRole('radio', { name: /Group/ }));
    await user.type(within(dialog).getByLabelText(/Group id/), 'accounting');
    await user.clear(within(dialog).getByLabelText(/Resource id/));
    await user.type(within(dialog).getByLabelText(/Resource id/), 'invoice');
    await user.click(within(dialog).getByRole('checkbox', { name: /All permissions/ }));
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText('Choose at least one permission.')).toBeVisible();

    await user.click(within(dialog).getByRole('checkbox', { name: 'READ' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'CREATE_INSTANCE' }));
    await user.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/authorization/create`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/authorization/create`)[0]?.body).toEqual({
      type: 1,
      permissions: ['READ', 'CREATE_INSTANCE'],
      userId: null,
      groupId: 'accounting',
      resourceType: 6,
      resourceId: 'invoice',
    });
  });

  it('keeps an authorization without permissions when it is edited', async () => {
    const user = userEvent.setup();
    const server = backend();
    server.on(`GET ${ENGINE}/authorization`, () =>
      json([{ id: 'a2', type: 1, permissions: ['NONE'], userId: 'mary', groupId: null, resourceType: 0, resourceId: 'tasklist' }]),
    );
    renderApp('/admin/authorizations');

    const table = await screen.findByRole('table', { name: 'Application' });
    const row = within(table).getByText('mary').closest('tr') as HTMLElement;
    await user.click(await within(row).findByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');
    await user.clear(within(dialog).getByLabelText(/Resource id/));
    await user.type(within(dialog).getByLabelText(/Resource id/), 'cockpit');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/authorization/a2`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/authorization/a2`)[0]?.body).toMatchObject({ permissions: ['NONE'], resourceId: 'cockpit' });
  });

  it('edits and deletes an authorization', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/authorizations');

    const table = await screen.findByRole('table', { name: 'Application' });
    const row = within(table).getByText('mary').closest('tr') as HTMLElement;
    await user.click(await within(row).findByRole('button', { name: 'Edit' }));
    const readOnly = within(table).getByText('camunda-admin').closest('tr') as HTMLElement;
    expect(within(readOnly).queryByRole('button')).toBeNull();
    const dialog = await screen.findByRole('dialog');
    // the engine keeps the type of an authorization
    expect(within(dialog).getByRole('radio', { name: /Revoke/ })).toBeDisabled();
    await user.clear(within(dialog).getByLabelText(/Resource id/));
    await user.type(within(dialog).getByLabelText(/Resource id/), 'cockpit');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/authorization/a2`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/authorization/a2`)[0]?.body).toMatchObject({
      id: 'a2',
      type: 1,
      permissions: ['ACCESS'],
      userId: 'mary',
      groupId: null,
      resourceId: 'cockpit',
    });

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    await user.click(within(row).getByRole('button', { name: 'Delete authorization' }));
    const confirm = await screen.findByRole('dialog');
    expect(confirm).toHaveTextContent('The authorization of “mary” for “tasklist” is deleted.');
    await user.click(within(confirm).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(server.find('DELETE', `${ENGINE}/authorization/a2`)).toHaveLength(1));
  });
});

describe('system', () => {
  it('shows the versions and the environment of the engine', async () => {
    backend();
    renderApp('/admin/system');

    expect(await screen.findByText('7.24.0')).toBeVisible();
    expect(screen.getByText('h2 2.3.232')).toBeVisible();
    // the name is not repeated when the version holds it already
    expect(screen.getByText('Apache Tomcat/10.1')).toBeVisible();
    expect(screen.getByText('Fasti BPM')).toBeVisible();
    expect(screen.getByText(/not sent anywhere/)).toBeVisible();
    expect(screen.getByText('Spring Boot starter, custom-thing')).toBeVisible();
  });

  it('shows the execution metrics of a chosen period', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/admin/system');

    expect(await screen.findByText('1,234')).toBeVisible();
    expect(screen.getByText('5,678')).toBeVisible();
    const first = server.find('GET', `${ENGINE}/metrics/root-process-instance-start/sum`)[0];
    expect(first?.query.get('startDate')).toMatch(/-01T00:00:00\.000[+-]\d{4}$/);
    await user.selectOptions(screen.getByLabelText('Period'), 'Last month');
    await waitFor(() =>
      expect(server.find('GET', `${ENGINE}/metrics/root-process-instance-start/sum`)).toHaveLength(2),
    );
  });

  it('explains when the diagnostic data may not be read', async () => {
    backend({
      routes: { [`GET ${ENGINE}/telemetry/data`]: () => json({ type: 'AuthorizationException', message: 'no' }, 403) },
    });
    renderApp('/admin/system');

    expect(await screen.findByText('You may not read the diagnostic data of the engine.')).toBeVisible();
  });
});

describe('initial setup', () => {
  it('creates the first administrator without a session', async () => {
    const user = userEvent.setup();
    const server = fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      [`GET ${AUTH}`]: () => json({ type: 'NotFound' }, 404),
      [`POST ${ENGINE}/identity/password-policy`]: () => json({ valid: true, rules: [] }),
      'POST /admin/setup/default/user/create': () => empty(),
    });
    renderApp('/setup');

    expect(await screen.findByRole('heading', { name: 'Create the first administrator' })).toBeVisible();
    await user.type(screen.getByLabelText(/Setup token/), ' token-from-the-log ');
    await user.type(screen.getByLabelText(/User id/), 'admin');
    await user.type(screen.getByLabelText(/^Password/), 'Adm1n-secret');
    await user.type(screen.getByLabelText(/Repeat the password/), 'Adm1n-secret');
    await user.click(screen.getByRole('button', { name: 'Create administrator' }));

    expect(await screen.findByText('Administrator “admin” created')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Go to sign in' })).toHaveAttribute('href', '/fasti/app/next/default/#/login');
    const sent = server.find('POST', '/admin/setup/default/user/create')[0];
    expect(sent?.body).toEqual({
      profile: { id: 'admin', firstName: '', lastName: '', email: '' },
      credentials: { password: 'Adm1n-secret' },
    });
    expect(sent?.headers.get('X-Fasti-Setup-Token')).toBe('token-from-the-log');
  });

  it('says so when the server refuses the setup', async () => {
    const user = userEvent.setup();
    fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      [`POST ${ENGINE}/identity/password-policy`]: () => json({ valid: true, rules: [] }),
      'POST /admin/setup/default/user/create': () => json({ type: 'InvalidRequestException', message: 'no' }, 403),
    });
    renderApp('/setup');

    await user.type(await screen.findByLabelText(/Setup token/), 'wrong-token');
    await user.type(screen.getByLabelText(/User id/), 'admin');
    await user.type(screen.getByLabelText(/^Password/), 'secret');
    await user.type(screen.getByLabelText(/Repeat the password/), 'secret');
    await user.click(screen.getByRole('button', { name: 'Create administrator' }));

    expect(await screen.findByText(/Check the setup token/)).toBeVisible();
  });
});

describe('helpers', () => {
  it('validates ids and e-mail addresses', () => {
    expect(validId('mary.major@acme')).toBe(true);
    expect(validId('mary major')).toBe(false);
    expect(validId('<script>')).toBe(false);
    expect(validEmail('')).toBe(true);
    expect(validEmail('mary@example.org')).toBe(true);
    expect(validEmail('demo@localhost')).toBe(true);
    expect(validEmail('mary@')).toBe(false);
  });

  it('summarises permissions', () => {
    expect(permissionSummary(['ALL'])).toBe('all');
    expect(permissionSummary(['NONE'])).toBe('none');
    expect(permissionSummary(['NONE', 'READ'])).toEqual(['READ']);
  });

  it('falls back to applications for an unknown resource type', () => {
    expect(resourceType(99).id).toBe(0);
    expect(resourceType(6).permissions).toContain('MIGRATE_INSTANCE');
  });
});
