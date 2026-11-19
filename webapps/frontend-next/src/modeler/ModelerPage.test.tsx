/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useImperativeHandle, type Ref } from 'react';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { Providers, createQueryClient, routes } from '../app/App';
import { preloadRoutes } from '../test/routes';
import { fakeServer, json } from '../test/server';
import { blankProcess, type EditorHandle, type EditorProps } from './kinds';

preloadRoutes(routes);

vi.mock('./BpmnEditor', () => ({
  default: function Editor({ onChange, source, ref }: EditorProps & { ref?: Ref<EditorHandle> }) {
    useImperativeHandle(ref, () => ({ save: () => Promise.resolve(source) }), [source]);
    return (
      <button type="button" onClick={onChange}>
        Draw something
      </button>
    );
  },
}));

const ENGINE = '/engine/engine/default';

describe('modeler navigation guard', () => {
  it('asks before an in-app link drops unsaved edits', async () => {
    const user = userEvent.setup();
    fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    });
    const router = createMemoryRouter(routes, { initialEntries: ['/cockpit/modeler'] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );

    await user.click(await screen.findByRole('button', { name: 'Draw something' }));
    await act(() => router.navigate('/cockpit/batches'));
    const dialog = await screen.findByRole('dialog', { name: 'Leave the modeler?' });
    expect(router.state.location.pathname).toBe('/cockpit/modeler');

    await user.click(within(dialog).getByRole('button', { name: 'Back' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(router.state.location.pathname).toBe('/cockpit/modeler');
    // the focus goes back to where it was when the dialog opened
    expect(screen.getByRole('button', { name: 'Draw something' })).toHaveFocus();

    await act(() => router.navigate('/cockpit/batches'));
    await user.click(within(await screen.findByRole('dialog', { name: 'Leave the modeler?' })).getByRole('button', { name: 'Discard and leave' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/batches'));
  });
});

describe('opening another deployed model', () => {
  it('asks before another deployed model replaces unsaved edits, and then opens it', async () => {
    const user = userEvent.setup();
    const server = fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
      [`GET ${ENGINE}/process-definition/a:1:x`]: () => json({ id: 'a:1:x', key: 'a', tenantId: null }),
      [`GET ${ENGINE}/process-definition/a:1:x/xml`]: () => json({ id: 'a:1:x', bpmn20Xml: '<definitions><process id="a" /></definitions>' }),
      [`GET ${ENGINE}/process-definition/b:1:y`]: () => json({ id: 'b:1:y', key: 'b', tenantId: null }),
      [`GET ${ENGINE}/process-definition/b:1:y/xml`]: () => json({ id: 'b:1:y', bpmn20Xml: '<definitions><process id="b" /></definitions>' }),
    });
    const router = createMemoryRouter(routes, { initialEntries: ['/cockpit/modeler?definition=a:1:x'] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );

    await user.click(await screen.findByRole('button', { name: 'Draw something' }));
    await act(() => router.navigate('/cockpit/modeler?definition=b:1:y'));
    const dialog = await screen.findByRole('dialog', { name: 'Leave the modeler?' });
    expect(router.state.location.search).toContain('a:1:x');

    await user.click(within(dialog).getByRole('button', { name: 'Discard and leave' }));
    await waitFor(() => expect(server.find('GET', `${ENGINE}/process-definition/b:1:y/xml`)).toHaveLength(1));
    expect(screen.getByText('Draw a process, decision or form and deploy it to the engine.')).toBeVisible();
  });
});

describe('deployed model without its tenant', () => {
  it('says why it cannot be deployed and offers a retry', async () => {
    fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
      [`GET ${ENGINE}/process-definition/a:1:x`]: () => json({ type: 'InvalidRequestException', message: 'broken' }, 400),
      [`GET ${ENGINE}/process-definition/a:1:x/xml`]: () => json({ id: 'a:1:x', bpmn20Xml: '<definitions><process id="a" /></definitions>' }),
    });
    const router = createMemoryRouter(routes, { initialEntries: ['/cockpit/modeler?definition=a:1:x'] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );
    await screen.findByRole('button', { name: 'Draw something' });
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Deploy' })).toBeDisabled();
  });
});

describe('deploying a model of a tenant', () => {
  it('deploys the new version for the tenant of the deployed one and says so', async () => {
    const user = userEvent.setup();
    const DEF = 'order:1:a';
    const server = fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
      [`GET ${ENGINE}/process-definition/${DEF}`]: () => json({ id: DEF, key: 'order', name: 'Order', version: 1, tenantId: 'acme', deploymentId: 'd1', suspended: false }),
      [`GET ${ENGINE}/process-definition/${DEF}/xml`]: () => json({ id: DEF, bpmn20Xml: blankProcess('order') }),
      [`POST ${ENGINE}/deployment/create`]: () => json({ id: 'd2', name: 'order', source: null, deploymentTime: '2026-03-01T09:30:00.000+0700', tenantId: 'acme' }),
    });
    const router = createMemoryRouter(routes, { initialEntries: [`/cockpit/modeler?definition=${encodeURIComponent(DEF)}`] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );

    await screen.findByRole('button', { name: 'Draw something' });
    const deploy = screen.getByRole('button', { name: 'Deploy' });
    await waitFor(() => expect(deploy).toBeEnabled());
    await user.click(deploy);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Tenant: acme.')).toBeVisible();
    // the checks find what is worth a look, nothing that stops the deployment
    expect(await within(dialog).findByText(/Can be deployed; worth a look/)).toBeVisible();
    const checks = within(dialog).getByRole('list', { name: 'Checks before deploying' });
    expect(within(checks).getByText('The process has no end event.')).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Deploy' }));
    await waitFor(() => expect(server.find('POST', `${ENGINE}/deployment/create`)).toHaveLength(1));
    const parts = server.find('POST', `${ENGINE}/deployment/create`)[0]?.body as FormData;
    expect(parts.get('tenant-id')).toBe('acme');
  });

  it('starts an instance of the deployed version and opens it', async () => {
    const user = userEvent.setup();
    const server = fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
      [`POST ${ENGINE}/deployment/create`]: () =>
        json({
          id: 'd3',
          name: 'order',
          source: null,
          deploymentTime: '2026-03-01T09:30:00.000+0700',
          tenantId: null,
          deployedProcessDefinitions: { 'order:2:b': { id: 'order:2:b', key: 'order' } },
        }),
      [`POST ${ENGINE}/process-definition/order:2:b/start`]: () => json({ id: 'pi-9', definitionId: 'order:2:b' }),
    });
    const router = createMemoryRouter(routes, { initialEntries: ['/cockpit/modeler'] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );

    // there are edits, and yet the new instance opens without asking to leave
    await user.click(await screen.findByRole('button', { name: 'Draw something' }));
    await user.click(screen.getByRole('button', { name: 'Deploy' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('checkbox', { name: /Start an instance and open it/ }));
    await within(dialog).findByText(/Can be deployed/);
    await user.click(within(dialog).getByRole('button', { name: 'Deploy' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/cockpit/processes/instance/pi-9'));
    expect(server.find('POST', `${ENGINE}/process-definition/order:2:b/start`)[0]?.body).toEqual({});
    expect(screen.queryByRole('dialog', { name: 'Leave the modeler?' })).toBeNull();
  });

  it('does not deploy a model the engine would refuse and says why', async () => {
    const user = userEvent.setup();
    const DEF = 'broken:1:a';
    const server = fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
      [`GET ${ENGINE}/process-definition/${DEF}`]: () => json({ id: DEF, key: 'broken', name: 'Broken', version: 1, tenantId: null, deploymentId: 'd1', suspended: false }),
      [`GET ${ENGINE}/process-definition/${DEF}/xml`]: () =>
        json({
          id: DEF,
          bpmn20Xml: blankProcess('broken').replace('<bpmn:startEvent id="StartEvent_1" />', '<bpmn:startEvent id="StartEvent_1" /><bpmn:serviceTask id="Pay" name="Pay" />'),
        }),
    });
    const router = createMemoryRouter(routes, { initialEntries: [`/cockpit/modeler?definition=${encodeURIComponent(DEF)}`] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );

    await screen.findByRole('button', { name: 'Draw something' });
    const deploy = screen.getByRole('button', { name: 'Deploy' });
    await waitFor(() => expect(deploy).toBeEnabled());
    await user.click(deploy);
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText(/^Not ready: errors 1, warnings/)).toBeVisible();
    const checks = within(dialog).getByRole('list', { name: 'Checks before deploying' });
    expect(within(checks).getAllByRole('listitem')[0]).toHaveTextContent(/^Error: Pay: Says nothing about what runs/);
    expect(within(dialog).getByRole('button', { name: 'Deploy' })).toBeDisabled();
    expect(server.find('POST', `${ENGINE}/deployment/create`)).toHaveLength(0);
  });
});

describe('modeler drafts', () => {
  const start = () => {
    fakeServer({
      'GET /engine/engine': () => json([{ name: 'default' }], 200, { 'X-XSRF-TOKEN': 'csrf-1' }),
      'GET /admin/auth/user/default': () => json({ userId: 'demo', authorizedApps: ['cockpit', 'tasklist', 'welcome'] }),
      [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User', email: null }),
    });
    const router = createMemoryRouter(routes, { initialEntries: ['/cockpit/modeler'] });
    render(
      <Providers queryClient={createQueryClient()} locale="en">
        <RouterProvider router={router} />
      </Providers>,
    );
  };
  const KEY = 'fasti.embeddedForm.default/demo/modeler';

  it('keeps unsaved edits in the browser while they are made', async () => {
    const user = userEvent.setup();
    start();
    await user.click(await screen.findByRole('button', { name: 'Draw something' }));
    await waitFor(() => expect(window.localStorage.getItem(KEY)).toContain('"kind":"model"'), { timeout: 4000 });
  });

  it('offers a kept model again and restores it', async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(
      KEY,
      JSON.stringify({ kind: 'model', values: { kind: 'bpmn', text: '<definitions id="kept"/>', source: '' }, savedAt: Date.now() }),
    );
    start();
    expect(await screen.findByText(/was not deployed or downloaded/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Restore it' }));
    await waitFor(() => expect(screen.queryByText(/was not deployed or downloaded/)).toBeNull());
    expect(window.localStorage.getItem(KEY)).not.toBeNull();
  });
});
