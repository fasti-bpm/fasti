/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tooltip } from 'radix-ui';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { auditDetails, auditParams, type AuditEvent, type AuditStatus, type AuditVerification } from '../api/audit';
import { I18nProvider } from '../i18n';
import { fakeServer, json, text } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { AuditTrailPage, AuditTrailTable } from './AuditTrailPage';

const ENGINE = '/engine/engine/default';

const STATUS: AuditStatus = {
  enabled: true,
  nodeId: 'node-1',
  chains: 4,
  types: ['process', 'task'],
  variableValues: 'hash',
  maskedVariables: ['*password*'],
  signed: true,
  keyId: '0123456789ab',
  checkpointInterval: 1000,
  retentionDays: 365,
  historyLevel: 'full',
};

function event(overrides: Partial<AuditEvent> = {}): AuditEvent {
  return {
    id: 'e1',
    chainId: 'node-1/0',
    sequence: 7,
    timestamp: '2026-11-21T08:00:00.000+0000',
    time: 1795248000000,
    type: 'task',
    action: 'complete',
    actor: 'alice',
    engineName: 'default',
    tenantId: null,
    processDefinitionKey: 'invoice',
    processDefinitionId: 'invoice:1:1',
    processInstanceId: 'pi-1',
    businessKey: 'order-4711',
    entityType: 'task',
    entityId: 'task-1',
    entityName: 'Approve invoice',
    details: '{"assignee":"alice","taskDefinitionKey":"approve"}',
    previousHash: 'a'.repeat(64),
    hash: 'b'.repeat(64),
    ...overrides,
  };
}

function wrap(children: ReactNode, path = '/cockpit/audit-trail') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider initial="en">
        <ToastProvider>
          <Tooltip.Provider>
            <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
          </Tooltip.Provider>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('audit trail helpers', () => {
  it('leave empty filters out of the query', () => {
    expect(auditParams({ processInstanceId: 'pi', businessKey: '', actor: undefined, type: 'task' })).toEqual({
      processInstanceId: 'pi',
      type: 'task',
    });
  });

  it('read the details of a record, and nothing that is not an object', () => {
    expect(auditDetails({ details: '{"a":1}' })).toEqual({ a: 1 });
    expect(auditDetails({ details: '[1]' })).toBeNull();
    expect(auditDetails({ details: 'not json' })).toBeNull();
    expect(auditDetails({ details: null })).toBeNull();
  });
});

describe('the audit trail page', () => {
  it('explains how to turn the audit trail on', async () => {
    fakeServer({ [`GET ${ENGINE}/audit`]: () => json({ ...STATUS, enabled: false, nodeId: null }) });
    wrap(<AuditTrailPage />);
    expect(await screen.findByText('The audit trail is off')).toBeInTheDocument();
    expect(screen.getByText(/fasti\.audit\.enabled=true/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verify integrity' })).not.toBeInTheDocument();
  });

  it('treats a server without the audit trail as off', async () => {
    fakeServer({});
    wrap(<AuditTrailPage />);
    expect(await screen.findByText('The audit trail is off')).toBeInTheDocument();
  });

  it('lists records with who did what, and filters by user', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([event(), event({ id: 'e2', type: 'process', action: 'start', actor: null, entityName: null })]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 2 }),
    });
    wrap(<AuditTrailPage />);
    const table = await screen.findByRole('table', { name: 'Records of the audit trail' });
    expect(within(table).getByText('Approve invoice')).toBeInTheDocument();
    expect(within(table).getAllByText('alice').length).toBeGreaterThan(0);
    expect(within(table).getByText('Engine (no user)')).toBeInTheDocument();
    expect(within(table).getAllByRole('link', { name: 'order-4711' })[0]).toHaveAttribute('href', '/cockpit/history/instance/pi-1');
    expect(screen.getByText(/Node node-1 · 4 chains · variable values: hash · checkpoints signed/)).toBeInTheDocument();

    const user = screen.getByRole('textbox', { name: 'User ID, or "system"' });
    await userEvent.type(user, 'bob{Enter}');
    await waitFor(() => expect(server.find('GET', `${ENGINE}/audit/event`).some((request) => request.query.get('actor') === 'bob')).toBe(true));
    // the field is the same one and keeps the focus, so that a keyboard user can go on from it
    expect(user).toBeInTheDocument();
    expect(user).toHaveFocus();
    expect(user).toHaveValue('bob');
  });

  it('names one chain as one chain', async () => {
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json({ ...STATUS, chains: 1 }),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
    });
    wrap(<AuditTrailPage />);
    expect(await screen.findByText(/Node node-1 · 1 chain · variable values/)).toBeInTheDocument();
  });

  it('takes a filter from the address and gives it up when the filters are cleared', async () => {
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
    });
    wrap(<AuditTrailPage />, '/cockpit/audit-trail?user=bob');
    const user = await screen.findByRole('textbox', { name: 'User ID, or "system"' });
    expect(user).toHaveValue('bob');
    await userEvent.click(await screen.findByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(user).toHaveValue(''));
  });

  it('goes to the last page when the address names one past the end', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: (request) => json(request.query.get('firstResult') === '25' ? [event()] : []),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 30 }),
    });
    wrap(<AuditTrailPage />, '/cockpit/audit-trail?apage=9');
    const table = await screen.findByRole('table', { name: 'Records of the audit trail' });
    expect(await within(table).findByText('Approve invoice')).toBeInTheDocument();
    expect(server.find('GET', `${ENGINE}/audit/event`).map((request) => request.query.get('firstResult'))).toEqual(['200', '25']);
    expect(screen.queryByText('No records yet')).not.toBeInTheDocument();
  });

  it('shows the details and the place of a record in its chain', async () => {
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([event()]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 1 }),
    });
    wrap(<AuditTrailPage />);
    const summary = await screen.findByText('Show');
    await userEvent.click(summary);
    expect(screen.getByText('taskDefinitionKey')).toBeInTheDocument();
    expect(screen.getByText('Record 7 of chain node-1/0')).toBeInTheDocument();
  });

  it('reports an intact trail with the last hash of each chain', async () => {
    const result: AuditVerification = {
      intact: true,
      authenticated: true,
      verifiedAt: '2026-11-21T08:00:00.000+0000',
      records: 12,
      firstBreak: null,
      chains: [
        { chainId: 'node-1/1', intact: true, authenticated: true, authenticatedThrough: 0, records: 3, firstSequence: 1, lastSequence: 3, lastHash: 'd'.repeat(64), checkpoints: 0, checkpointsVerified: 0, purgedThrough: 0, purgeAttested: false, firstBreak: null },
        { chainId: 'node-1/0', intact: true, authenticated: true, authenticatedThrough: 10, records: 12, firstSequence: 1, lastSequence: 12, lastHash: 'c'.repeat(64), checkpoints: 1, checkpointsVerified: 1, purgedThrough: 0, purgeAttested: false, firstBreak: null },
      ],
    };
    const server = fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () => json(result),
    });
    wrap(<AuditTrailPage />);
    // the region that announces the outcome is there before there is one
    const verify = await screen.findByRole('button', { name: 'Verify integrity' });
    // (once the table has loaded, it is the only one)
    await screen.findByText('No records yet');
    const status = within(screen.getByRole('status'));
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
    await userEvent.click(verify);
    expect(await status.findByText('Intact: 12 records in 2 chains verified')).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Intact: 12 records in 2 chains verified');
    expect(screen.getByText('1 of 1 verified')).toBeInTheDocument();
    expect(screen.getByText(/^Authenticated: every checkpoint and purge record was checked/)).toBeInTheDocument();
    // how far the signatures reach, per chain
    expect(screen.getByText('signatures cover records up to 10')).toBeInTheDocument();
    expect(screen.getByText('no checkpoint yet: hashes only')).toBeInTheDocument();
    expect(server.find('POST', `${ENGINE}/audit/verify`)).toHaveLength(1);

    // verified again, the same outcome is announced again: the region is emptied meanwhile
    let finish: () => void = () => undefined;
    server.on(`POST ${ENGINE}/audit/verify`, async () => {
      await new Promise<void>((resolve) => {
        finish = resolve;
      });
      return json({ ...result, records: 1234 });
    });
    await userEvent.click(screen.getByRole('button', { name: 'Verify integrity' }));
    await waitFor(() => expect(screen.getByRole('status')).toBeEmptyDOMElement());
    finish();
    expect(await status.findByText('Intact: 1,234 records in 2 chains verified')).toBeInTheDocument();
  });

  it('says when an intact trail is not authenticated, and when a purge is not attested', async () => {
    const chain = { chainId: 'node-1/0', intact: true, authenticated: false, records: 12, firstSequence: 41, lastSequence: 52, lastHash: 'c'.repeat(64), checkpoints: 2, checkpointsVerified: 1, purgedThrough: 40, purgeAttested: false, firstBreak: null };
    const result: AuditVerification = { intact: true, authenticated: false, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 12, firstBreak: null, chains: [chain] };
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () => json(result),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    expect(await screen.findByRole('note')).toHaveTextContent('Intact: 12 records in 1 chain verified');
    expect(screen.getByText(/^Not authenticated: some checkpoints or purge records carry a signature the engine cannot check/)).toBeInTheDocument();
    expect(screen.queryByText(/^Authenticated:/)).not.toBeInTheDocument();
    expect(screen.getByText('1 of 2 verified')).toBeInTheDocument();
    expect(screen.getByText('Purged up to record 40; no checked signature attests it')).toBeInTheDocument();
  });

  async function verifyWith(result: AuditVerification) {
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () => json(result),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    await screen.findByRole('note');
  }

  const young = { chainId: 'node-1/1', intact: true, authenticated: false, authenticatedThrough: 0, records: 3, firstSequence: 1, lastSequence: 3, lastHash: 'd'.repeat(64), checkpoints: 0, checkpointsVerified: 0, purgedThrough: 0, purgeAttested: false, firstBreak: null };
  const signedChain = { chainId: 'node-1/0', intact: true, authenticated: true, authenticatedThrough: 10, records: 12, firstSequence: 1, lastSequence: 12, lastHash: 'c'.repeat(64), checkpoints: 1, checkpointsVerified: 1, purgedThrough: 0, purgeAttested: false, firstBreak: null };

  it('calls a young chain without a checkpoint intact but not signed yet, not unverifiable', async () => {
    await verifyWith({ intact: true, authenticated: false, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 15, firstBreak: null, chains: [young, signedChain] });

    expect(screen.getByText(/^Not signed yet: the records are intact, but no checkpoint has signed them so far/)).toBeInTheDocument();
    expect(screen.queryByText(/^Not authenticated:/)).not.toBeInTheDocument();
    expect(screen.getByText('intact, not signed yet: signed at the next checkpoint (every few minutes or at shutdown)')).toBeInTheDocument();
    expect(screen.getByText('signatures cover records up to 10')).toBeInTheDocument();
  });

  it('still says a signature cannot be checked when a checkpoint was not verified', async () => {
    const unchecked = { ...signedChain, authenticated: false, authenticatedThrough: 0, checkpoints: 2, checkpointsVerified: 1 };
    await verifyWith({ intact: true, authenticated: false, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 15, firstBreak: null, chains: [young, unchecked] });

    expect(screen.getByText(/^Not authenticated: some checkpoints or purge records carry a signature the engine cannot check/)).toBeInTheDocument();
    expect(screen.queryByText(/^Not signed yet:/)).not.toBeInTheDocument();
    // the young chain is still told apart
    expect(screen.getByText(/^intact, not signed yet/)).toBeInTheDocument();
  });

  it('keeps the answer of a 1.3.1 engine, which counts a young chain as authenticated', async () => {
    await verifyWith({ intact: true, authenticated: true, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 15, firstBreak: null, chains: [{ ...young, authenticated: true }, signedChain] });

    expect(screen.getByText(/^Authenticated: every checkpoint and purge record was checked/)).toBeInTheDocument();
    expect(screen.getByText('no checkpoint yet: hashes only')).toBeInTheDocument();
    expect(screen.queryByText(/not signed yet/)).not.toBeInTheDocument();
  });

  it('says that hashes alone do not rule out a rewrite when the trail is not signed', async () => {
    const chain = { chainId: 'node-1/0', intact: true, authenticated: false, records: 3, firstSequence: 1, lastSequence: 3, lastHash: 'c'.repeat(64), checkpoints: 0, checkpointsVerified: 0, purgedThrough: 0, purgeAttested: false, firstBreak: null };
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json({ ...STATUS, signed: false, keyId: null }),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () => json({ intact: true, authenticated: false, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 3, firstBreak: null, chains: [chain] }),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    expect(await screen.findByText(/^No signing key is set: the hashes are consistent/)).toBeInTheDocument();
  });

  it('names a removed start that no signed purge record explains', async () => {
    const found = { chainId: 'node-1/0', sequence: 1, recordId: null, reason: 'purge-unattested', message: 'The chain starts at record 4.' };
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () =>
        json({ intact: false, authenticated: false, verifiedAt: '2026-11-21T08:00:00.000+0000', records: 0, firstBreak: found, chains: [] }),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('Records at the start were removed without a signed purge record')).toBeInTheDocument();
  });

  it('tells that a verification is running already instead of failing', async () => {
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () => json({ type: 'InvalidRequestException', message: 'A verification of the audit trail is running.' }, 409),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    expect(await screen.findByText('A verification is already running on the engine. Try again when it is done.')).toBeInTheDocument();
    expect(screen.queryByText('Could not verify the audit trail')).not.toBeInTheDocument();
  });

  it('reports the first break as an alert', async () => {
    const found = { chainId: 'node-1/2', sequence: 5, recordId: 'r5', reason: 'hash-mismatch', message: 'Record 5 was changed after it was written.' };
    fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 0 }),
      [`POST ${ENGINE}/audit/verify`]: () =>
        json({
          intact: false,
          verifiedAt: '2026-11-21T08:00:00.000+0000',
          records: 9,
          firstBreak: found,
          chains: [{ chainId: 'node-1/2', intact: false, records: 4, firstSequence: 1, lastSequence: 4, lastHash: null, checkpoints: 0, checkpointsVerified: 0, purgedThrough: 0, firstBreak: found }],
        }),
    });
    wrap(<AuditTrailPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Verify integrity' }));
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('The audit trail is broken')).toBeInTheDocument();
    expect(within(alert).getByText('A record was changed')).toBeInTheDocument();
    expect(within(alert).getByText(/Chain node-1\/2, record 5/)).toBeInTheDocument();
    // the alert announces a break; the polite region stays silent
    await screen.findByText('No records yet');
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });

  it('exports the filtered records as a file of the server', async () => {
    const created = vi.fn(() => 'blob:audit');
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: created });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    const server = fakeServer({
      [`GET ${ENGINE}/audit`]: () => json(STATUS),
      [`GET ${ENGINE}/audit/event`]: () => json([event()]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 1 }),
      [`POST ${ENGINE}/audit/event/export`]: () => text('id,chainId\r\ne1,node-1/0\r\n', 'text/csv'),
    });
    wrap(<AuditTrailPage />, '/cockpit/audit-trail?bk=order-4711');
    await userEvent.click(await screen.findByRole('button', { name: 'Export CSV' }));
    await waitFor(() => expect(created).toHaveBeenCalled());
    // a POST, so that the CSRF filter of the web application covers it; nothing is exported with a GET
    expect(server.find('GET', `${ENGINE}/audit/event/export`)).toHaveLength(0);
    const request = server.find('POST', `${ENGINE}/audit/event/export`)[0];
    expect(request?.query.get('format')).toBe('csv');
    expect(request?.query.get('businessKey')).toBe('order-4711');
    expect(await screen.findByText('Exported; the export is on record in the audit trail.')).toBeInTheDocument();
  });
});

describe('the audit trail of one instance', () => {
  it('asks for the records of the instance, oldest first, without the case column', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/audit/event`]: () => json([event()]),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 1 }),
    });
    wrap(<AuditTrailTable query={{ processInstanceId: 'pi-1', sortOrder: 'asc' }} pageParam="tpage" showCase={false} />);
    const table = await screen.findByRole('table', { name: 'Records of the audit trail' });
    expect(within(table).queryByRole('columnheader', { name: 'Case' })).not.toBeInTheDocument();
    const request = server.find('GET', `${ENGINE}/audit/event`)[0];
    expect(request?.query.get('processInstanceId')).toBe('pi-1');
    expect(request?.query.get('sortOrder')).toBe('asc');
  });

  it('goes to the last page of the tab when the address names one past the end', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/audit/event`]: (request) => json(request.query.get('firstResult') === '25' ? [event()] : []),
      [`GET ${ENGINE}/audit/event/count`]: () => json({ count: 26 }),
    });
    wrap(
      <AuditTrailTable query={{ processInstanceId: 'pi-1', sortOrder: 'asc' }} pageParam="tpage" showCase={false} />,
      '/cockpit/history/instance/pi-1?tpage=4',
    );
    const table = await screen.findByRole('table', { name: 'Records of the audit trail' });
    expect(await within(table).findByText('Approve invoice')).toBeInTheDocument();
    expect(server.find('GET', `${ENGINE}/audit/event`).at(-1)?.query.get('firstResult')).toBe('25');
  });
});
