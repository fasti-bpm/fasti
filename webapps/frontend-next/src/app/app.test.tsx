/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Locale } from '../i18n';
import { empty, fakeServer, json, text, type FakeHandler, type FakeServer } from '../test/server';
import { Providers, createQueryClient, routes } from './App';
import { preloadRoutes } from '../test/routes';
import { encodeSearch } from '../tasklist/searchLinks';
import { env } from '../env';
import { draftKey } from '../forms/embedded/broker';

preloadRoutes(routes);

const ENGINE = '/engine/engine/default';
const AUTH = '/admin/auth/user/default';

const GENERATED_FORM = `<form name="generatedForm" role="form">
  <div class="form-group"><label for="amount">Amount</label>
    <input class="form-control" name="amount" cam-variable-type="Long" cam-variable-name="amount" required="true" type="text" /></div>
  <div class="form-group"><label for="approved">Approved</label>
    <input class="form-control" name="approved" cam-variable-type="Boolean" cam-variable-name="approved" type="checkbox" /></div>
</form>`;

function task(id: string, name: string, assignee: string | null = null) {
  return {
    id,
    name,
    assignee,
    owner: null as string | null,
    created: '2026-03-01T09:30:00.000+0700',
    due: null,
    followUp: null,
    delegationState: null as string | null,
    description: `Description of ${name}`,
    executionId: 'e1',
    parentTaskId: null,
    priority: 50,
    processDefinitionId: 'invoice:1:abc',
    processInstanceId: 'p1',
    caseDefinitionId: null,
    caseInstanceId: null,
    caseExecutionId: null,
    taskDefinitionKey: 'approve',
    formKey: null,
    tenantId: null,
  };
}

interface Backend extends FakeServer {
  state: { loggedIn: boolean; tasks: ReturnType<typeof task>[] };
}

/** The part of the REST API the user interface talks to, with a little state. */
function backend(options: { loggedIn?: boolean; routes?: Record<string, FakeHandler> } = {}): Backend {
  const state = {
    loggedIn: options.loggedIn ?? true,
    tasks: [task('t1', 'Approve invoice'), task('t2', 'Prepare transfer', 'mary')],
  };
  const secured =
    (handler: FakeHandler): FakeHandler =>
    (request) =>
      state.loggedIn ? handler(request) : empty(401);
  const find = (id: string) => state.tasks.find((candidate) => candidate.id === id);

  const server = fakeServer({
    'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
    [`GET ${AUTH}`]: () =>
      state.loggedIn
        ? json({ userId: 'demo', authorizedApps: ['tasklist', 'cockpit', 'welcome'] })
        : empty(404),
    [`POST ${AUTH}/login/welcome`]: (request) => {
      const form = request.body as { username: string; password: string };
      if (form.username !== 'demo' || form.password !== 'secret') {
        return empty(401);
      }
      state.loggedIn = true;
      return json({ userId: 'demo', authorizedApps: ['tasklist', 'cockpit', 'welcome'] });
    },
    [`POST ${AUTH}/logout`]: () => {
      state.loggedIn = false;
      return empty(200);
    },
    [`GET ${ENGINE}/user/demo/profile`]: secured(() =>
      json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    ),
    [`GET ${ENGINE}/filter`]: secured(() =>
      json([
        { id: 'f2', name: 'All tasks', resourceType: 'Task', owner: null, properties: { priority: 10 }, itemCount: 7 },
        {
          id: 'f1',
          name: 'My group',
          resourceType: 'Task',
          owner: null,
          properties: { priority: -5, description: 'Work of my group', variables: [{ name: 'amount', label: 'Amount' }] },
          itemCount: 2,
        },
      ]),
    ),
    [`POST ${ENGINE}/filter/f1/list`]: secured((request) => {
      const first = Number(request.query.get('firstResult') ?? 0);
      const max = Number(request.query.get('maxResults') ?? 1000);
      return json({
        count: state.tasks.length,
        _embedded: {
          task: state.tasks.slice(first, first + max).map((item) => ({
            ...item,
            _embedded: { variable: [{ name: 'amount', value: 900, type: 'Long' }] },
          })),
          assignee: [{ id: 'mary', firstName: 'Mary', lastName: 'Anne' }],
          processDefinition: [{ id: 'invoice:1:abc', name: 'Invoice Receipt', key: 'invoice' }],
        },
      });
    }),
    [`POST ${ENGINE}/filter/f2/list`]: secured(() => json({ count: 0, _embedded: null })),
    [`GET ${ENGINE}/task/t1`]: secured(() => {
      const found = find('t1');
      return found ? json(found) : json({ type: 'RestException', message: 'No matching task' }, 404);
    }),
    [`GET ${ENGINE}/task/t1/identity-links`]: secured(() =>
      json([{ userId: null, groupId: 'accounting', type: 'candidate' }]),
    ),
    [`GET ${ENGINE}/task/t1/form`]: secured(() =>
      json({ key: 'embedded:engine://engine/:engine/task/t1/rendered-form', contextPath: '/invoice' }),
    ),
    [`GET ${ENGINE}/task/t1/rendered-form`]: secured(() => text(GENERATED_FORM, 'application/xhtml+xml')),
    [`GET ${ENGINE}/task/t1/form-variables`]: secured(() => json({})),
    [`POST ${ENGINE}/task/t1/claim`]: secured(() => {
      const found = find('t1');
      if (found) found.assignee = 'demo';
      return empty();
    }),
    [`POST ${ENGINE}/task/t1/unclaim`]: secured(() => {
      const found = find('t1');
      if (found) found.assignee = null;
      return empty();
    }),
    [`POST ${ENGINE}/task/t1/submit-form`]: secured(() => {
      // like the engine: a delegated task is resolved and goes back to its owner
      const delegated = find('t1');
      if (delegated?.delegationState === 'PENDING') {
        Object.assign(delegated, { assignee: delegated.owner, delegationState: 'RESOLVED' });
        return empty();
      }
      state.tasks = state.tasks.filter((candidate) => candidate.id !== 't1');
      return empty();
    }),
    [`GET ${ENGINE}/task/t1/comment`]: secured(() => json([])),
    [`GET ${ENGINE}/history/user-operation`]: secured(() => json([])),
    [`GET ${ENGINE}/process-definition/count`]: secured(() => json({ count: 1 })),
    [`GET ${ENGINE}/process-definition`]: secured(() =>
      json([{ id: 'invoice:1:abc', key: 'invoice', name: 'Invoice Receipt', description: null, version: 1, versionTag: null, tenantId: null }]),
    ),
    [`GET ${ENGINE}/process-definition/invoice:1:abc`]: secured(() =>
      json({ id: 'invoice:1:abc', key: 'invoice', name: 'Invoice Receipt', description: null, version: 1, versionTag: null, tenantId: null }),
    ),
    [`GET ${ENGINE}/process-definition/invoice:1:abc/startForm`]: secured(() =>
      json({ key: null, contextPath: null }),
    ),
    [`POST ${ENGINE}/process-definition/invoice:1:abc/submit-form`]: secured(() =>
      json({ id: 'p2', definitionId: 'invoice:1:abc', businessKey: 'INV-7' }),
    ),
    ...options.routes,
  });
  return Object.assign(server, { state });
}

function renderApp(path: string, locale: Locale = 'en') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers queryClient={createQueryClient()} locale={locale}>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe('login', () => {
  it('sends an anonymous user to the login page and back after signing in', async () => {
    const user = userEvent.setup();
    const server = backend({ loggedIn: false });
    const router = renderApp('/tasklist?filter=f1');

    // the first test of the file also pays for loading the modules
    expect(await screen.findByRole('heading', { name: 'Sign in' }, { timeout: 5000 })).toBeVisible();
    expect(router.state.location.pathname).toBe('/login');

    // a wrong password is reported and nothing else happens
    await user.type(screen.getByLabelText('User name'), 'demo');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('The user name or password is wrong.')).toBeVisible();

    await user.clear(screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'secret');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('option', { name: /Approve invoice/ })).toBeVisible();
    expect(router.state.location.pathname).toBe('/tasklist');
    expect(router.state.location.search).toContain('filter=f1');

    const [login] = server.find('POST', `${AUTH}/login/welcome`).slice(-1);
    expect(login?.body).toEqual({ username: 'demo', password: 'secret' });
    expect(login?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');
    expect(login?.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    // the credentials never appear in a URL
    expect(server.requests.some((request) => request.url.includes('secret'))).toBe(false);
  });

  it('validates the login form before sending it', async () => {
    const user = userEvent.setup();
    const server = backend({ loggedIn: false });
    renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));

    expect(await screen.findAllByText('This field is required.')).toHaveLength(2);
    expect(server.find('POST', `${AUTH}/login/welcome`)).toHaveLength(0);
  });

  it('returns to the login page when the session expires', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1');
    await screen.findByRole('option', { name: /Approve invoice/ });

    server.state.loggedIn = false;
    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible();
  });

  it('signs out', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Account' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible();
    expect(server.find('POST', `${AUTH}/logout`)).toHaveLength(1);
  });

  it('removes the entered form values of the user on sign out', async () => {
    const user = userEvent.setup();
    backend();
    const own = draftKey(env.engine, 'demo', { type: 'task', taskId: 't1' });
    const other = draftKey(env.engine, 'mary', { type: 'task', taskId: 't1' });
    window.localStorage.setItem(own, '{"amount":1}');
    window.localStorage.setItem(other, '{"amount":2}');
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Account' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible();
    expect(window.localStorage.getItem(own)).toBeNull();
    expect(window.localStorage.getItem(other)).toBe('{"amount":2}');
    window.localStorage.removeItem(other);
  });
});

describe('single sign-on', () => {
  const SSO_CONFIG = {
    enabled: true,
    label: 'Keycloak',
    loginUrl: '/fasti/sso/login',
    logoutUrl: '/logout',
  };

  it('offers the identity provider instead of the password form', async () => {
    backend({ loggedIn: false, routes: { 'GET /fasti/sso/config': () => json(SSO_CONFIG) } });
    renderApp('/tasklist');

    // back to the page that was asked for after the identity provider
    const link = await screen.findByRole('link', { name: 'Sign in with Keycloak' });
    expect(link).toHaveAttribute('href', '/fasti/sso/login?return=%2Ffasti%2Fapp%2Fnext%2Fdefault%2F%23%2Ftasklist');
    expect(screen.queryByLabelText('Password')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Sign in' })).toBeNull();
  });

  it('names single sign-on when the server gives no label', async () => {
    backend({ loggedIn: false, routes: { 'GET /fasti/sso/config': () => json({ ...SSO_CONFIG, label: '' }) } });
    renderApp('/login', 'vi');

    expect(await screen.findByRole('link', { name: 'Đăng nhập một lần (SSO)' })).toBeVisible();
  });

  it('ignores a configuration that points to another host', async () => {
    backend({
      loggedIn: false,
      routes: { 'GET /fasti/sso/config': () => json({ ...SSO_CONFIG, loginUrl: '//evil.example/login' }) },
    });
    renderApp('/login');

    expect(await screen.findByLabelText('Password')).toBeVisible();
    expect(screen.queryByRole('link', { name: /Keycloak/ })).toBeNull();
  });

  it('keeps the password form when the server has no single sign-on', async () => {
    backend({ loggedIn: false });
    renderApp('/login');

    expect(await screen.findByLabelText('Password')).toBeVisible();
  });

  it('signs out at the identity provider', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign } });
    try {
      const server = backend({
        routes: {
          'GET /fasti/sso/config': () => json(SSO_CONFIG),
          'POST /logout': () => json({ redirectUrl: 'https://idp.example/logout?id_token_hint=x' }),
        },
      });
      renderApp('/tasklist?filter=f1');

      await user.click(await screen.findByRole('button', { name: 'Account' }));
      await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }));

      await waitFor(() => expect(assign).toHaveBeenCalledWith('https://idp.example/logout?id_token_hint=x'));
      const [logout] = server.find('POST', '/logout');
      expect(logout?.headers.get('Accept')).toBe('application/json');
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: original });
    }
  });

  it('never navigates to a script URL returned by the logout', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    const original = window.location;
    Object.defineProperty(window, 'location', { configurable: true, value: { ...original, assign } });
    try {
      backend({
        routes: {
          'GET /fasti/sso/config': () => json(SSO_CONFIG),
          'POST /logout': () => json({ redirectUrl: 'javascript:alert(1)' }),
        },
      });
      renderApp('/tasklist?filter=f1');

      await user.click(await screen.findByRole('button', { name: 'Account' }));
      await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }));

      await waitFor(() => expect(assign).toHaveBeenCalledWith(`${original.origin}/`));
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: original });
    }
  });
});

describe('shell', () => {
  it('is Vietnamese first and switches to English', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/tasklist?filter=f1', 'vi');

    expect(await screen.findByRole('link', { name: 'Công việc' })).toBeVisible();
    expect(document.documentElement.lang).toBe('vi');

    await user.click(screen.getByRole('button', { name: 'Ngôn ngữ' }));
    await user.click(await screen.findByRole('menuitemradio', { name: 'English' }));

    expect(await screen.findByRole('link', { name: 'Tasks' })).toBeVisible();
    expect(document.documentElement.lang).toBe('en');
    expect(window.localStorage.getItem('fasti.next.locale')).toBe('"en"');
  });

  it('goes to a section with G and a key and lists those shortcuts in the help', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/tasklist?filter=f1');
    await screen.findByRole('option', { name: /Approve invoice/ });

    await user.keyboard('gi');
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/incidents'));
    await user.keyboard('gh');
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    // the user of this test may not use the administration: no such shortcut is offered
    await user.keyboard('?');
    const help = await screen.findByRole('dialog', { name: 'Keyboard shortcuts' });
    const navigation = within(help).getByRole('region', { name: 'Go to (press G, then the key)' });
    expect(within(navigation).getByText('Go to incidents')).toBeVisible();
    expect(within(navigation).getByText('G I')).toBeVisible();
  });

  it('switches the theme and remembers the choice', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Theme' }));
    await user.click(await screen.findByRole('menuitemradio', { name: 'Dark' }));

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(window.localStorage.getItem('fasti.next.theme')).toBe('"dark"');
  });

  it('offers the sections the user may use and the way back to the welcome page', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/tasklist?filter=f1');

    // monitoring is a section of this application, not a link to the classic one
    const cockpit = await screen.findByRole('link', { name: 'Cockpit' });
    expect(cockpit).toHaveAttribute('href', '/cockpit');
    // the user of this test has no access to the administration
    expect(screen.queryByRole('link', { name: /Admin/ })).toBeNull();

    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/');

    // the classic Welcome application is no longer the entry of the user menu
    await user.click(screen.getByRole('button', { name: 'Account' }));
    expect(await screen.findByRole('menuitem', { name: 'Home and profile' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('menuitem', { name: /classic/i })).toBeNull();
  });

  it('chooses the business fields of a process for its task cards', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/preferences');

    const settings = await screen.findByRole('group', { name: 'Business fields on task cards' });
    expect(within(settings).getByText('No process has its own fields yet.')).toBeVisible();
    await user.type(within(settings).getByLabelText('Process key'), 'invoice');
    await user.type(within(settings).getByLabelText('Variable name'), 'amount');
    await user.type(within(settings).getByLabelText('Shown as (optional)'), 'Amount{Enter}');

    const fields = await within(settings).findByRole('list', { name: 'Fields of Invoice Receipt' });
    expect(within(fields).getByText('Amount')).toBeVisible();
    expect(JSON.parse(window.localStorage.getItem('fasti.next.tasklist.processFields') ?? '{}')).toEqual({
      invoice: [{ name: 'amount', label: 'Amount' }],
    });
    // the same variable cannot be added twice
    await user.type(within(settings).getByLabelText('Variable name'), 'amount');
    expect(within(settings).getByRole('button', { name: 'Add field' })).toBeDisabled();
    expect(within(settings).getByText('This variable is already a field of the process.')).toBeVisible();

    await user.click(within(fields).getByRole('button', { name: 'Remove Amount from Invoice Receipt' }));
    expect(await within(settings).findByText('No process has its own fields yet.')).toBeVisible();
  });

  it('groups the top bar by goal and reaches every monitoring page from it', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/tasklist?filter=f1');

    const work = await screen.findByRole('group', { name: 'Work' });
    expect(within(work).getByRole('link', { name: 'Tasks' })).toBeInTheDocument();
    const operate = screen.getByRole('group', { name: 'Operate' });
    expect(within(operate).getByRole('link', { name: 'Cockpit' })).toBeInTheDocument();

    await user.click(within(operate).getByRole('button', { name: 'All monitoring pages' }));
    const menu = await screen.findByRole('menu');
    expect(within(menu).getByText('Build and maintain')).toBeInTheDocument();
    await user.keyboard('{End}{Enter}');
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/cleanup'));
  }, 15_000);
});

describe('tasklist', () => {
  it('lists the filters with their counts and the tasks of the selected filter', async () => {
    const server = backend();
    renderApp('/tasklist');

    const filters = await screen.findByRole('list', { name: 'Filters' });
    const names = within(filters)
      .getAllByRole('button')
      .map((button) => button.textContent);
    // ordered by priority; the first one is selected
    expect(names).toEqual(['My groupWork of my group2', 'All tasks7']);
    expect(within(filters).getByRole('button', { name: /My group/ })).toHaveAttribute('aria-current', 'true');

    const first = await screen.findByRole('option', { name: /Approve invoice/ });
    expect(within(first).getByText('Invoice Receipt')).toBeVisible();
    expect(within(first).getByText('Unassigned')).toBeVisible();
    expect(within(first).getByText('900')).toBeVisible();
    expect(within(screen.getByRole('option', { name: /Prepare transfer/ })).getByText('Mary Anne')).toBeVisible();
    expect(screen.getByText('2 tasks')).toBeVisible();

    const [list] = server.find('POST', `${ENGINE}/filter/f1/list`);
    expect(list?.headers.get('Accept')).toBe('application/hal+json');
    expect(list?.query.get('firstResult')).toBe('0');
    expect(list?.body).toEqual({ sorting: [{ sortBy: 'created', sortOrder: 'desc' }] });
  });

  it('searches, sorts and remembers both', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1');
    await screen.findByRole('option', { name: /Approve invoice/ });

    await user.type(screen.getByRole('searchbox', { name: 'Search value' }), 'invoice{Enter}');
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).toMatchObject({
        nameLike: '%invoice%',
      }),
    );

    await user.selectOptions(screen.getByRole('combobox', { name: 'Search by' }), 'Process variable');
    await user.type(screen.getByRole('textbox', { name: 'Variable name' }), 'amount');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Comparison' }), '≥');
    await user.type(screen.getByRole('searchbox', { name: 'Search value' }), '500{Enter}');
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).toMatchObject({
        nameLike: '%invoice%',
        processVariables: [{ name: 'amount', operator: 'gteq', value: 500 }],
      }),
    );

    await user.click(screen.getByRole('button', { name: 'Descending' }));
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).toMatchObject({
        sorting: [{ sortBy: 'created', sortOrder: 'asc' }],
      }),
    );

    await user.click(screen.getByRole('button', { name: 'Mine' }));
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).toMatchObject({ assignee: 'demo' }),
    );

    const stored = JSON.parse(window.localStorage.getItem('fasti.next.tasklist.demo') ?? '{}');
    expect(stored).toMatchObject({
      filterId: null,
      onlyMine: true,
      sorting: { sortBy: 'created', sortOrder: 'asc' },
    });
    expect(stored.criteria).toHaveLength(2);
  });

  it('opens a task, claims it, fills in the generated form and completes it', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('option', { name: /Approve invoice/ }));

    expect(await screen.findByRole('heading', { name: 'Approve invoice' })).toBeVisible();
    // the form can be read, but not changed before the task is claimed
    const amount = await screen.findByLabelText(/Amount/);
    expect(amount).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Complete' })).toBeDisabled();
    expect(await screen.findByText('accounting')).toBeVisible();

    await user.click(screen.getAllByRole('button', { name: 'Claim' })[0] as HTMLElement);

    await waitFor(() => expect(amount).toBeEnabled());
    expect(await screen.findByText('Task claimed')).toBeVisible();
    const [claim] = server.find('POST', `${ENGINE}/task/t1/claim`);
    expect(claim?.body).toEqual({ userId: 'demo' });
    expect(claim?.headers.get('X-XSRF-TOKEN')).toBe('csrf-1');

    // the form is validated before it is sent
    await user.click(screen.getByRole('button', { name: 'Complete' }));
    expect(await screen.findByText('This field is required.')).toBeVisible();
    await user.type(amount, 'many');
    expect(await screen.findByText('Enter a whole number.')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/task/t1/submit-form`)).toHaveLength(0);

    await user.clear(amount);
    await user.type(amount, '1200');
    await user.click(screen.getByLabelText('Approved'));
    await user.click(screen.getByRole('button', { name: 'Complete' }));

    expect(await screen.findByText('Completed “Approve invoice”')).toBeVisible();
    const [submit] = server.find('POST', `${ENGINE}/task/t1/submit-form`);
    expect(submit?.body).toEqual({
      variables: {
        amount: { value: 1200, type: 'Long' },
        approved: { value: true, type: 'Boolean' },
      },
    });
    // the completed task leaves the list
    await waitFor(() => expect(screen.queryByRole('option', { name: /Approve invoice/ })).toBeNull());
  });

  it('opens the next task after completing one on a wide screen', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/tasklist?filter=f1&task=t1');

    await user.click((await screen.findAllByRole('button', { name: 'Claim' }))[0] as HTMLElement);
    const amount = await screen.findByLabelText(/Amount/);
    await waitFor(() => expect(amount).toBeEnabled());
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Complete' }));

    await waitFor(() => expect(router.state.location.search).toContain('task=t2'));
  });

  it('says that another person must complete a task whose earlier step the user did', async () => {
    const fourEyes = (assignee: string | null) => ({
      taskId: 't1',
      enabled: true,
      guarded: true,
      earlierSteps: [{ id: 'prepare', name: 'Prepare invoice' }],
      userId: 'demo',
      userExcluded: true,
      userSteps: ['prepare'],
      assignee,
      assigneeExcluded: assignee === 'demo',
      assigneeSteps: assignee === 'demo' ? ['prepare'] : [],
      blocked: true,
    });
    const server = backend({
      routes: {
        [`GET ${ENGINE}/task/t1/four-eyes`]: () => json(fourEyes(server.state.tasks[0]?.assignee ?? null)),
      },
    });
    server.state.tasks = [task('t1', 'Approve invoice', 'demo'), task('t2', 'Prepare transfer', 'mary')];
    renderApp('/tasklist?filter=f1&task=t1');

    expect(await screen.findByText('Four-eyes check')).toBeVisible();
    expect(
      screen.getByText(
        'You completed an earlier step of this case that this task checks (Prepare invoice), so another person must complete it.',
      ),
    ).toBeVisible();
    expect(await screen.findByText('Another person must complete this task (four-eyes check).')).toBeVisible();
    expect(await screen.findByLabelText(/Amount/)).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Complete' })).toBeDisabled();
    // nothing to complete, so nothing to complete and go on with
    expect(screen.queryByLabelText('Complete and get next work')).toBeNull();
  });

  it('names the assignee who did an earlier step', async () => {
    const server = backend({
      routes: {
        [`GET ${ENGINE}/task/t1/four-eyes`]: () =>
          json({
            taskId: 't1',
            enabled: true,
            guarded: true,
            earlierSteps: [{ id: 'prepare', name: null }],
            userId: 'demo',
            userExcluded: false,
            userSteps: [],
            assignee: 'mary',
            assigneeExcluded: true,
            assigneeSteps: ['prepare'],
            blocked: true,
          }),
        [`GET ${ENGINE}/user/mary/profile`]: () => json({ id: 'mary', firstName: 'Mary', lastName: 'Anne' }),
      },
    });
    server.state.tasks = [task('t1', 'Approve invoice', 'mary'), task('t2', 'Prepare transfer', 'mary')];
    renderApp('/tasklist?filter=f1&task=t1');

    expect(
      await screen.findByText(
        'Mary Anne completed an earlier step of this case that this task checks (prepare) and cannot complete it. Assign it to another person.',
      ),
    ).toBeVisible();
  });

  it('tells who may not complete a task with a four-eyes rule', async () => {
    backend({
      routes: {
        [`GET ${ENGINE}/task/t1/four-eyes`]: () =>
          json({
            taskId: 't1',
            enabled: true,
            guarded: true,
            earlierSteps: [
              { id: 'prepare', name: 'Prepare invoice' },
              { id: 'review', name: 'Review invoice' },
            ],
            userId: 'demo',
            userExcluded: false,
            userSteps: [],
            assignee: null,
            assigneeExcluded: false,
            assigneeSteps: [],
            blocked: false,
          }),
      },
    });
    renderApp('/tasklist?filter=f1&task=t1');

    expect(
      await screen.findByText(
        'Whoever completed an earlier step of this case that this task checks (Prepare invoice and Review invoice) cannot complete it.',
      ),
    ).toBeVisible();
  });

  it('explains when the four-eyes control refuses a completion', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`POST ${ENGINE}/task/t1/submit-form`]: () =>
          json(
            {
              type: 'FourEyesViolationException',
              message: "Four-eyes control: user 'demo' cannot complete task 'Approve invoice' (approve)",
            },
            403,
          ),
      },
    });
    renderApp('/tasklist?filter=f1&task=t1');

    await user.click((await screen.findAllByRole('button', { name: 'Claim' }))[0] as HTMLElement);
    const amount = await screen.findByLabelText(/Amount/);
    await waitFor(() => expect(amount).toBeEnabled());
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Complete' }));

    expect(await screen.findByText(/You or the assignee completed an earlier step that this task checks/)).toBeVisible();
    expect(screen.getByText(/Four-eyes control: user 'demo'/)).toBeVisible();
  });

  it('returns to the list after completing a task when the user prefers it', async () => {
    const user = userEvent.setup();
    backend();
    window.localStorage.setItem('fasti.next.afterComplete', '"list"');
    const router = renderApp('/tasklist?filter=f1&task=t1');

    await user.click((await screen.findAllByRole('button', { name: 'Claim' }))[0] as HTMLElement);
    const amount = await screen.findByLabelText(/Amount/);
    await waitFor(() => expect(amount).toBeEnabled());
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Complete' }));

    await waitFor(() => expect(router.state.location.search).not.toContain('task='));
    await waitFor(() => expect(screen.getByRole('option', { name: /Prepare transfer/ })).toHaveFocus());
  });

  it('returns to the list after completing a task on a narrow screen', async () => {
    const user = userEvent.setup();
    backend();
    // the width at which the task covers the list
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query === '(max-width: 760px)',
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }));
    const router = renderApp('/tasklist?filter=f1&task=t1');

    await user.click((await screen.findAllByRole('button', { name: 'Claim' }))[0] as HTMLElement);
    const amount = await screen.findByLabelText(/Amount/);
    await waitFor(() => expect(amount).toBeEnabled());
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Complete' }));

    expect(await screen.findByText('Completed “Approve invoice”')).toBeVisible();
    await waitFor(() => expect(router.state.location.search).not.toContain('task='));
    expect(await screen.findByRole('option', { name: /Prepare transfer/ })).toBeVisible();
  });

  it('names the case of each task by its business key', async () => {
    const server = backend();
    server.on(`GET ${ENGINE}/process-instance`, () => json([{ id: 'p1', businessKey: 'INV-1001' }]));
    renderApp('/tasklist?filter=f1');
    const list = await screen.findByRole('listbox');
    expect((await within(list).findAllByText('INV-1001')).length).toBeGreaterThan(0);
  });

  it('shows the business fields chosen for the process of each task first', async () => {
    window.localStorage.setItem(
      'fasti.next.tasklist.processFields',
      JSON.stringify({ invoice: [{ name: 'creditor', label: 'Supplier' }], other: [{ name: 'secret', label: '' }] }),
    );
    const server = backend();
    server.on(`POST ${ENGINE}/variable-instance`, (request) => {
      const name = (request.body as { variableName: string }).variableName;
      return json(name === 'creditor' ? [{ name, type: 'String', value: 'Acme Ltd.', processInstanceId: 'p1' }] : []);
    });
    renderApp('/tasklist?filter=f1');
    const list = await screen.findByRole('listbox');
    expect((await within(list).findAllByText('Acme Ltd.')).length).toBe(2);
    expect(within(list).getAllByText('Supplier')[0]?.tagName).toBe('DT');
    // only the variables of processes in the list are read
    const names = server.find('POST', `${ENGINE}/variable-instance`).map((request) => (request.body as { variableName: string }).variableName);
    expect(names).toEqual(['creditor']);
  });

  it('keeps the list in place while a task is open and shows new tasks on request', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1&task=t1');
    const list = await screen.findByRole('listbox');
    await within(list).findByRole('option', { name: /Prepare transfer/ });

    server.state.tasks.unshift(task('t9', 'Check delivery'));
    await user.keyboard('r');
    expect(await screen.findByText('1 new task arrived')).toBeVisible();
    expect(within(list).queryByRole('option', { name: /Check delivery/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Show them' }));
    expect(await within(list).findByRole('option', { name: /Check delivery/ })).toBeVisible();
    expect(screen.queryByText('1 new task arrived')).not.toBeInTheDocument();
  });

  it('undoes a claim from the notification', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1&task=t1');

    await user.click((await screen.findAllByRole('button', { name: 'Claim' }))[0] as HTMLElement);
    await user.click(await screen.findByRole('button', { name: 'Undo' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/task/t1/unclaim`)).toHaveLength(1));
    await waitFor(() => expect(server.state.tasks[0]?.assignee).toBeNull());
  });

  it('suggests users for the assignee and delegates a task of the user', async () => {
    const user = userEvent.setup();
    const server = backend({
      routes: {
        [`GET ${ENGINE}/user`]: (request) =>
          json(request.query.get('firstNameLike') ? [{ id: 'mary', firstName: 'Mary', lastName: 'Anne' }] : []),
        [`POST ${ENGINE}/task/t1/delegate`]: (request) => {
          const found = server.state.tasks[0];
          if (found) {
            found.owner = 'demo';
            found.assignee = (request.body as { userId: string }).userId;
            found.delegationState = 'PENDING';
          }
          return empty();
        },
      },
    });
    const first = server.state.tasks[0];
    if (first) first.assignee = 'demo';
    renderApp('/tasklist?filter=f1&task=t1');

    await user.click(await screen.findByRole('button', { name: /^Assignee/ }));
    const field = await screen.findByLabelText('User id');
    await user.clear(field);
    await user.type(field, 'Mar');
    await waitFor(() => expect(document.querySelector('datalist option[value="mary"]')).not.toBeNull());
    expect(document.querySelector('datalist option[value="mary"]')?.textContent).toBe('Mary Anne');
    await user.clear(field);
    await user.type(field, 'mary');
    await user.click(screen.getByRole('button', { name: 'Delegate' }));

    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/task/t1/delegate`)[0]?.body).toEqual({ userId: 'mary' }),
    );
    expect(await screen.findByRole('button', { name: /mary \(delegated by demo\)/ })).toBeVisible();
  });

  it('moves through the list with the keyboard and opens a task with Enter', async () => {
    const user = userEvent.setup();
    backend();
    const router = renderApp('/tasklist?filter=f1');
    await screen.findByRole('option', { name: /Approve invoice/ });

    // the first key enters the list, the next one moves on
    await user.keyboard('j');
    expect(screen.getByRole('option', { name: /Approve invoice/ })).toHaveFocus();
    await user.keyboard('j');
    expect(screen.getByRole('option', { name: /Prepare transfer/ })).toHaveFocus();
    await user.keyboard('k');
    expect(screen.getByRole('option', { name: /Approve invoice/ })).toHaveFocus();
    await user.keyboard('{Enter}');

    expect(await screen.findByRole('heading', { name: 'Approve invoice' })).toBeVisible();
    expect(router.state.location.search).toContain('task=t1');

    // the shortcut claims the open task
    await user.keyboard('c');
    expect(await screen.findByText('Task claimed')).toBeVisible();
  });

  it('says that the open task is gone when it was completed elsewhere', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/tasklist?filter=f1&task=t1');
    expect(await screen.findByRole('heading', { name: 'Approve invoice' })).toBeVisible();

    server.on(`GET ${ENGINE}/task/t1`, () => json({ type: 'NotFound', message: 'gone' }, 404));
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    expect(await screen.findByText('This task is no longer there')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Approve invoice' })).toBeNull();
  });

  it('resolves a delegated task and says that it went back to its owner', async () => {
    const user = userEvent.setup();
    const server = backend();
    Object.assign(server.state.tasks[0] as object, { assignee: 'demo', owner: 'mary', delegationState: 'PENDING' });
    renderApp('/tasklist?filter=f1&task=t1');

    const amount = await screen.findByLabelText(/Amount/);
    await waitFor(() => expect(amount).toBeEnabled());
    await user.type(amount, '5');
    await user.click(screen.getByRole('button', { name: 'Resolve and return' }));
    expect(await screen.findByText('Returned “Approve invoice” to mary')).toBeVisible();
    expect(server.find('POST', `${ENGINE}/task/t1/submit-form`)).toHaveLength(1);
    // the task is still there, now with its owner
    expect(server.state.tasks.find((item) => item.id === 't1')).toMatchObject({ assignee: 'mary', delegationState: 'RESOLVED' });
    expect(await screen.findByRole('option', { name: /Approve invoice/ })).toBeVisible();
  });

  it('goes back to the last page when the shown page has no tasks any more', async () => {
    const user = userEvent.setup();
    const server = backend();
    // 26 tasks: the second page shows one
    server.state.tasks = Array.from({ length: 26 }, (_, index) => task(`x${index}`, `Task ${index}`));
    const router = renderApp('/tasklist?filter=f1&page=2');
    expect(await screen.findByRole('option', { name: /Task 25/ })).toBeVisible();

    // the last task of that page is done elsewhere
    server.state.tasks = server.state.tasks.slice(0, 25);
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    await waitFor(() => expect(router.state.location.search).not.toContain('page='));
    expect(await screen.findByRole('option', { name: /Task 0/ })).toBeVisible();
  });

  it('keeps a tab stop in the list when the open task is not on the shown page', async () => {
    backend();
    renderApp('/tasklist?filter=f1&task=elsewhere');
    const first = await screen.findByRole('option', { name: /Approve invoice/ });
    expect(first).toHaveAttribute('tabindex', '0');
  });

  it('shows the search of a shared link without replacing the remembered one', async () => {
    const user = userEvent.setup();
    const server = backend();
    const q = encodeSearch([{ id: 'x', field: 'name', value: 'invoice' }], false);
    renderApp(`/tasklist?filter=f1&q=${encodeURIComponent(q)}`);

    expect(await screen.findByText(/shows the search of a shared link/)).toBeVisible();
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).toMatchObject({ nameLike: '%invoice%' }),
    );
    expect(JSON.stringify(window.localStorage)).not.toContain('invoice');

    await user.click(screen.getByRole('button', { name: 'Back to my search' }));
    await waitFor(() =>
      expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).not.toHaveProperty('nameLike'),
    );
    expect(screen.queryByText(/shows the search of a shared link/)).toBeNull();
  });

  it('clears every criterion of a shared search that finds nothing', async () => {
    const user = userEvent.setup();
    const server = backend();
    server.on(`POST ${ENGINE}/filter/f1/list`, (request) =>
      json(
        (request.body as { nameLike?: string }).nameLike
          ? { count: 0, _embedded: { task: [] } }
          : { count: 1, _embedded: { task: [task('t1', 'Approve invoice')] } },
      ),
    );
    const q = encodeSearch([{ id: 'x', field: 'name', value: 'nothing' }], true);
    renderApp(`/tasklist?filter=f1&q=${encodeURIComponent(q)}`);

    await user.click(await screen.findByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).not.toHaveProperty('nameLike'));
    expect(server.find('POST', `${ENGINE}/filter/f1/list`).at(-1)?.body).not.toHaveProperty('assignee');
    expect(await screen.findByRole('option', { name: /Approve invoice/ })).toBeVisible();
  });

  it('keeps the search of a shared link when asked to', async () => {
    const user = userEvent.setup();
    backend();
    const q = encodeSearch([{ id: 'x', field: 'name', value: 'invoice' }], false);
    renderApp(`/tasklist?filter=f1&q=${encodeURIComponent(q)}`);
    await user.click(await screen.findByRole('button', { name: 'Keep this search' }));
    await waitFor(() => expect(JSON.stringify(window.localStorage)).toContain('invoice'));
    expect(screen.queryByText(/shows the search of a shared link/)).toBeNull();
  });

  it('checks the focused task with Space for the bulk actions', async () => {
    const user = userEvent.setup();
    backend();
    renderApp('/tasklist?filter=f1');
    await screen.findByRole('option', { name: /Approve invoice/ });

    await user.keyboard('j');
    expect(screen.getByRole('option', { name: /Approve invoice/ })).toHaveFocus();
    await user.keyboard(' ');
    expect(screen.getByRole('checkbox', { name: 'Select Approve invoice' })).toBeChecked();
    expect(screen.getByText('1 selected')).toBeVisible();
    await user.keyboard(' ');
    expect(screen.getByRole('checkbox', { name: 'Select Approve invoice' })).not.toBeChecked();
  });

  it('renders embedded forms in the form frame and shows the description', async () => {
    const user = userEvent.setup();
    backend({
      routes: {
        [`GET ${ENGINE}/task/t1/form`]: () =>
          json({ key: 'embedded:app:forms/approve-invoice.html', contextPath: '/invoice' }),
      },
    });
    renderApp('/tasklist?filter=f1&task=t1');

    expect(await screen.findByTitle('Form')).toHaveAttribute('src', '/fasti/app/forms/default/');
    expect(screen.queryByText(/classic Tasklist/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Description' }));
    expect(await screen.findByText('Description of Approve invoice')).toBeVisible();
  });

  it('never renders text of the server as markup', async () => {
    backend({
      routes: {
        [`POST ${ENGINE}/filter/f1/list`]: () =>
          json({
            count: 1,
            _embedded: {
              task: [{ ...task('t9', '<img src=x onerror="window.__xss = 1"><b>bold</b>'), _embedded: { variable: [] } }],
            },
          }),
      },
    });
    renderApp('/tasklist?filter=f1');

    expect(await screen.findByText('<img src=x onerror="window.__xss = 1"><b>bold</b>')).toBeVisible();
    expect(document.querySelector('img[src="x"]')).toBeNull();
    expect((window as unknown as { __xss?: number }).__xss).toBeUndefined();
  });

  it('creates ready-made task lists for a user without filters', async () => {
    const user = userEvent.setup();
    let created = 0;
    const server = backend({
      routes: {
        [`GET ${ENGINE}/filter`]: () => json([]),
        [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [{ rel: 'create' }] }),
        [`POST ${ENGINE}/filter/create`]: () => json({ id: `n${++created}`, name: 'x' }),
      },
    });
    renderApp('/tasklist');

    await user.click(await screen.findAllByRole('button', { name: 'Create my task lists' }).then((buttons) => buttons[0] as HTMLElement));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/filter/create`)).toHaveLength(4));
    const bodies = server.find('POST', `${ENGINE}/filter/create`).map((request) => request.body as { name: string; owner: string; query: object });
    expect(bodies.map((body) => body.name)).toEqual(['My tasks', 'Available to my groups', 'My overdue tasks', 'All tasks']);
    expect(bodies[0]).toMatchObject({ owner: 'demo', query: { assigneeExpression: '${currentUser()}' } });
  });

  it('explains an empty filter list and reports failures with a way to retry', async () => {
    const user = userEvent.setup();
    let failures = 1;
    backend({
      routes: {
        [`POST ${ENGINE}/filter/f1/list`]: () =>
          failures-- > 0
            ? json({ type: 'AuthorizationException', message: 'The user demo has no permission' }, 403)
            : json({ count: 0, _embedded: null }),
      },
    });
    renderApp('/tasklist?filter=f1');

    expect(await screen.findByText('You are not allowed to do this.')).toBeVisible();
    await user.click(screen.getByText('Technical details'));
    expect(screen.getByText('The user demo has no permission')).toBeVisible();

    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('No tasks')).toBeVisible();
  });

  it('names a task of a case (CMMN) by its case, links the case instance and draws the case', async () => {
    const user = userEvent.setup();
    const caseTask = {
      ...task('c9', 'Review claim'),
      executionId: null,
      processDefinitionId: null,
      processInstanceId: null,
      caseDefinitionId: 'claim:1:a',
      caseInstanceId: 'ci1',
      caseExecutionId: 'cx1',
      taskDefinitionKey: 'PI_review',
    };
    const CASE_MODEL = `<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"
        xmlns:cmmndi="http://www.omg.org/spec/CMMN/20151109/CMMNDI" xmlns:dc="http://www.omg.org/spec/CMMN/20151109/DC">
      <case id="claim"><casePlanModel id="m" name="Claim">
        <planItem id="PI_review" definitionRef="t" /><humanTask id="t" name="Review claim" />
      </casePlanModel></case>
      <cmmndi:CMMNDI><cmmndi:CMMNDiagram id="d">
        <cmmndi:CMMNShape id="s1" cmmnElementRef="m"><dc:Bounds x="0" y="30" width="300" height="200" /></cmmndi:CMMNShape>
        <cmmndi:CMMNShape id="s2" cmmnElementRef="PI_review"><dc:Bounds x="40" y="80" width="100" height="80" /></cmmndi:CMMNShape>
      </cmmndi:CMMNDiagram></cmmndi:CMMNDI>
    </definitions>`;
    const server = backend({
      routes: {
        [`POST ${ENGINE}/filter/f1/list`]: () =>
          json({
            count: 1,
            _embedded: {
              task: [{ ...caseTask, _embedded: { variable: [] } }],
              caseDefinition: [{ id: 'claim:1:a', key: 'claim', name: 'Claim case' }],
            },
          }),
        [`GET ${ENGINE}/history/case-instance`]: () => json([{ id: 'ci1', businessKey: 'CL-1' }]),
        [`GET ${ENGINE}/task/c9`]: () => json(caseTask),
        [`GET ${ENGINE}/task/c9/identity-links`]: () => json([]),
        [`GET ${ENGINE}/task/c9/form`]: () => json({ key: null, contextPath: null }),
        [`GET ${ENGINE}/task/c9/form-variables`]: () => json({}),
        [`GET ${ENGINE}/task/c9/comment`]: () => json([]),
        [`GET ${ENGINE}/case-definition/claim:1:a/xml`]: () => json({ id: 'claim:1:a', cmmnXml: CASE_MODEL }),
      },
    });
    renderApp('/tasklist?filter=f1&task=c9');

    const card = await screen.findByRole('option', { name: /Review claim/ });
    expect(await within(card).findByText('Claim case')).toBeVisible();
    expect(await within(card).findByText(/CL-1/)).toBeVisible();
    expect(server.find('GET', `${ENGINE}/history/case-instance`)[0]?.query.get('caseInstanceIds')).toBe('ci1');

    const details = await screen.findByRole('region', { name: 'Task details' });
    expect(within(details).getByRole('link', { name: 'Open the case in Cockpit' })).toHaveAttribute('href', '/cockpit/cases/instance/ci1');
    // a case task has no case tab of a process
    expect(within(details).queryByRole('tab', { name: 'Case' })).toBeNull();

    await user.click(within(details).getByRole('tab', { name: 'Diagram' }));
    const diagram = await within(details).findByRole('img', { name: 'Case diagram, the plan item of the task is highlighted' });
    expect(diagram.querySelector('[data-element-id="PI_review"]')).toHaveClass('is-selected');
  });
});

describe('start process', () => {
  it('offers no process to a user who may not create process instances', async () => {
    backend({
      routes: {
        [`GET ${ENGINE}/authorization/check`]: () => json({ authorized: false }),
      },
    });
    renderApp('/processes');
    expect(await screen.findByText('You may not start processes')).toBeVisible();
    expect(screen.queryByRole('button', { name: /Invoice Receipt/ })).toBeNull();
  });

  it('lists the processes and starts one with variables and a business key', async () => {
    const user = userEvent.setup();
    const server = backend();
    renderApp('/processes');

    await user.click(await screen.findByRole('button', { name: /Invoice Receipt/ }));
    expect(await screen.findByRole('heading', { name: 'Invoice Receipt' })).toBeVisible();

    await user.type(await screen.findByLabelText('Business key'), 'INV-7');
    await user.click(screen.getByRole('button', { name: 'Add variable' }));
    await user.type(screen.getByLabelText('Variable name'), 'amount');
    await user.selectOptions(screen.getByLabelText('Type'), 'Long');
    await user.type(screen.getByLabelText('Value'), '250');
    await user.click(screen.getByRole('button', { name: 'Start' }));

    expect(await screen.findByText('Started the process “Invoice Receipt”')).toBeVisible();
    // the receipt names the new instance and offers the ways on
    const receipt = screen.getByRole('status');
    expect(within(receipt).getByText('INV-7')).toBeVisible();
    expect(within(receipt).getByText('p2')).toBeVisible();
    await user.click(within(receipt).getByRole('button', { name: 'Start another' }));
    expect(await screen.findByLabelText('Business key')).toBeVisible();
    const [start] = server.find('POST', `${ENGINE}/process-definition/invoice:1:abc/submit-form`);
    expect(start?.body).toEqual({
      variables: { amount: { value: 250, type: 'Long' } },
      businessKey: 'INV-7',
    });
    const [list] = server.find('GET', `${ENGINE}/process-definition`);
    expect(list?.query.get('startableInTasklist')).toBe('true');
    expect(list?.query.get('latest')).toBe('true');
  });
});

describe('filter management', () => {
  const filterRoutes = (allowed: string[]): Record<string, FakeHandler> => ({
    [`OPTIONS ${ENGINE}/filter`]: () => json({ links: [{ rel: 'create' }] }),
    [`OPTIONS ${ENGINE}/filter/f1`]: () => json({ links: allowed.map((rel) => ({ rel })) }),
    [`OPTIONS ${ENGINE}/authorization`]: () => json({ links: [{ rel: 'create' }] }),
    [`GET ${ENGINE}/filter/f1`]: () =>
      json({
        id: 'f1',
        name: 'My group',
        resourceType: 'Task',
        owner: 'demo',
        query: { candidateGroupsExpression: '${currentUserGroups()}' },
        properties: { priority: -5, description: 'Work of my group', variables: [{ name: 'amount', label: 'Amount' }] },
      }),
    [`GET ${ENGINE}/authorization`]: () =>
      json([
        { id: 'a0', type: 1, userId: 'demo', groupId: null, permissions: ['ALL'] },
        { id: 'a1', type: 1, userId: null, groupId: 'sales', permissions: ['READ'] },
      ]),
    [`PUT ${ENGINE}/filter/f1`]: () => empty(),
    [`DELETE ${ENGINE}/authorization/a1`]: () => empty(),
    [`POST ${ENGINE}/authorization/create`]: () => json({ id: 'a2' }),
    [`DELETE ${ENGINE}/filter/f1`]: () => empty(),
    [`POST ${ENGINE}/filter/create`]: () => json({ id: 'f9', name: 'Mine' }),
  });

  it('edits the criteria, variables and sharing of a filter', async () => {
    const user = userEvent.setup();
    const server = backend({ routes: filterRoutes(['update', 'delete']) });
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Settings of the filter “My group”' }));
    const dialog = await screen.findByRole('dialog', { name: 'Filter settings' });
    const name = await within(dialog).findByLabelText(/^Name/);
    await user.clear(name);
    await user.type(name, 'Team work');

    await user.click(within(dialog).getByRole('tab', { name: 'Criteria' }));
    expect(within(dialog).getByLabelText('Candidate groups (any of)')).toHaveValue('${currentUserGroups()}');
    await user.click(within(dialog).getByRole('button', { name: 'Add criterion' }));
    const criteria = within(dialog).getAllByLabelText('Criterion');
    await user.selectOptions(criteria[1] as HTMLElement, 'assignee');
    await user.type(within(dialog).getByLabelText('Assignee'), '${{currentUser()}');

    await user.click(within(dialog).getByRole('tab', { name: 'Sharing' }));
    await user.click(within(dialog).getByRole('button', { name: 'Stop sharing with Group sales' }));
    await user.click(within(dialog).getByLabelText(/Every user/));
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(server.find('PUT', `${ENGINE}/filter/f1`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/filter/f1`)[0]?.body).toMatchObject({
      name: 'Team work',
      owner: 'demo',
      resourceType: 'Task',
      query: { candidateGroupsExpression: '${currentUserGroups()}', assigneeExpression: '${currentUser()}' },
      properties: { priority: -5, refresh: true, variables: [{ name: 'amount', label: 'Amount' }] },
    });
    await waitFor(() => expect(server.find('POST', `${ENGINE}/authorization/create`)).toHaveLength(1));
    expect(server.find('DELETE', `${ENGINE}/authorization/a1`)).toHaveLength(1);
    expect(server.find('POST', `${ENGINE}/authorization/create`)[0]?.body).toEqual({
      type: 0,
      permissions: ['READ'],
      userId: '*',
      groupId: null,
      resourceType: 5,
      resourceId: 'f1',
    });
    // the engine's own grant for the owner is never offered for removal
    expect(server.find('DELETE', `${ENGINE}/authorization/a0`)).toHaveLength(0);
  });

  it('shows a filter read-only without the update permission', async () => {
    const user = userEvent.setup();
    backend({ routes: filterRoutes([]) });
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Settings of the filter “My group”' }));
    const dialog = await screen.findByRole('dialog', { name: 'Filter settings' });
    expect(await within(dialog).findByText('You may see this filter but not change it.')).toBeVisible();
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete filter' })).toBeNull();
  });

  it('deletes a filter after asking', async () => {
    const user = userEvent.setup();
    const server = backend({ routes: filterRoutes(['update', 'delete']) });
    renderApp('/tasklist?filter=f1');

    await user.click(await screen.findByRole('button', { name: 'Settings of the filter “My group”' }));
    const dialog = await screen.findByRole('dialog', { name: 'Filter settings' });
    await user.click(await within(dialog).findByRole('button', { name: 'Delete filter' }));
    const confirm = await screen.findByRole('dialog', { name: 'Delete filter?' });
    await user.click(within(confirm).getByRole('button', { name: 'Delete filter' }));

    await waitFor(() => expect(server.find('DELETE', `${ENGINE}/filter/f1`)).toHaveLength(1));
  });

  it('creates a filter owned by the user', async () => {
    const user = userEvent.setup();
    const server = backend({ routes: filterRoutes(['update']) });
    renderApp('/tasklist?filter=f1');

    await user.click((await screen.findAllByRole('button', { name: 'New filter' }))[0] as HTMLElement);
    const dialog = await screen.findByRole('dialog', { name: 'New filter' });
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('This field is required.')).toBeVisible();
    await user.type(within(dialog).getByLabelText(/^Name/), 'Mine');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(server.find('POST', `${ENGINE}/filter/create`)).toHaveLength(1));
    expect(server.find('POST', `${ENGINE}/filter/create`)[0]?.body).toMatchObject({
      name: 'Mine',
      owner: 'demo',
      resourceType: 'Task',
      query: {},
    });
  });
});

describe('task attachments', () => {
  it('lists, adds and deletes the attachments of a task', async () => {
    const user = userEvent.setup();
    const attachments = [
      { id: 'a1', name: 'invoice.pdf', description: 'Scan', type: 'application/pdf', url: null, taskId: 't1' },
      { id: 'a2', name: 'Wiki', description: null, type: 'url', url: 'https://example.org/wiki', taskId: 't1' },
      { id: 'a3', name: 'Bad', description: null, type: 'url', url: 'javascript:alert(1)', taskId: 't1' },
    ];
    const server = backend({
      routes: {
        [`GET ${ENGINE}/task/t1/attachment`]: () => json(attachments),
        [`POST ${ENGINE}/task/t1/attachment/create`]: () => json({ id: 'a4' }),
        [`DELETE ${ENGINE}/task/t1/attachment/a1`]: () => empty(),
      },
    });
    renderApp('/tasklist?filter=f1&task=t1&tab=attachments');

    const list = await screen.findByRole('list', { name: 'Attachments' });
    expect(within(list).getByRole('link', { name: 'Download invoice.pdf' })).toHaveAttribute(
      'href',
      '/fasti/api/engine/engine/default/task/t1/attachment/a1/data',
    );
    const wiki = within(list).getByRole('link', { name: 'Open Wiki in a new tab' });
    expect(wiki).toHaveAttribute('href', 'https://example.org/wiki');
    expect(wiki).toHaveAttribute('rel', 'noopener noreferrer');
    // a link to anything but a web page is not offered
    expect(within(list).queryByRole('link', { name: /Bad/ })).toBeNull();

    await user.click(screen.getByRole('radio', { name: 'Link' }));
    await user.type(screen.getByLabelText(/^Web address/), 'ftp://x');
    await user.click(screen.getByRole('button', { name: 'Attach' }));
    expect(await screen.findByText('Enter a web address starting with http:// or https://.')).toBeVisible();
    await user.clear(screen.getByLabelText(/^Web address/));
    await user.type(screen.getByLabelText(/^Web address/), 'https://example.org/spec');
    await user.click(screen.getByRole('button', { name: 'Attach' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/task/t1/attachment/create`)).toHaveLength(1));
    const body = server.find('POST', `${ENGINE}/task/t1/attachment/create`)[0]?.body as FormData;
    expect(body.get('attachment-type')).toBe('url');
    expect(body.get('url')).toBe('https://example.org/spec');
    expect(body.get('attachment-name')).toBe('https://example.org/spec');

    await user.click(screen.getByRole('radio', { name: 'File' }));
    await user.upload(screen.getByLabelText(/^File to attach/), new File(['x'], 'note.txt', { type: 'text/plain' }));
    await user.click(screen.getByRole('button', { name: 'Attach' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/task/t1/attachment/create`)).toHaveLength(2));
    const upload = server.find('POST', `${ENGINE}/task/t1/attachment/create`)[1]?.body as FormData;
    expect(upload.get('attachment-name')).toBe('note.txt');
    expect(upload.get('attachment-type')).toBe('text/plain');
    expect((upload.get('content') as File).name).toBe('note.txt');

    await user.click(within(list).getByRole('button', { name: 'Delete invoice.pdf' }));
    const confirm = await screen.findByRole('dialog', { name: 'Delete attachment?' });
    await user.click(within(confirm).getByRole('button', { name: 'Delete attachment' }));
    await waitFor(() => expect(server.find('DELETE', `${ENGINE}/task/t1/attachment/a1`)).toHaveLength(1));
  });
});
