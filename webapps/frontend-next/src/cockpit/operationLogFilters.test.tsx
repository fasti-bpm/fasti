/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tooltip } from 'radix-ui';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '../i18n';
import { fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { AuditPage } from './AuditPage';

const ENGINE = '/engine/engine/default';

function renderPage(path: string) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <I18nProvider initial="en">
        <ToastProvider>
          <Tooltip.Provider>
            <MemoryRouter initialEntries={[path]}>
              <AuditPage />
            </MemoryRouter>
          </Tooltip.Provider>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

describe('the filters of the operation log', () => {
  it('keep the focus in the field when a filter is applied with Enter', async () => {
    const server = fakeServer({
      [`GET ${ENGINE}/history/user-operation`]: () => json([]),
      [`GET ${ENGINE}/history/user-operation/count`]: () => json({ count: 0 }),
    });
    renderPage('/cockpit/audit?op=Claim');
    const user = await screen.findByRole('textbox', { name: 'User' });
    const operation = screen.getByRole('textbox', { name: 'Operation type' });
    // a filter of the address is shown
    expect(operation).toHaveValue('Claim');

    await userEvent.type(user, ' bob {Enter}');
    await waitFor(() =>
      expect(server.find('GET', `${ENGINE}/history/user-operation`).some((request) => request.query.get('userId') === 'bob')).toBe(true),
    );
    expect(user).toBeInTheDocument();
    expect(user).toHaveFocus();
    // the field shows the filter as it was applied
    expect(user).toHaveValue('bob');
    // the other fields are the ones they were, with their values
    expect(screen.getByRole('textbox', { name: 'Operation type' })).toBe(operation);
    expect(operation).toHaveValue('Claim');

    // leaving a field applies it as well
    await userEvent.clear(operation);
    await userEvent.tab();
    await waitFor(() =>
      expect(server.find('GET', `${ENGINE}/history/user-operation`).at(-1)?.query.get('operationType')).toBeNull(),
    );
  });
});
