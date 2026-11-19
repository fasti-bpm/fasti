/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { Providers, createQueryClient } from '../app/App';
import { empty, fakeServer, json } from '../test/server';
import { BatchReceipts } from './BatchReceipts';
import { addReceipt, useReceipts, withReceipt } from './receipts';

const ENGINE = '/engine/engine/default';

function stats(failedJobs: number, completedJobs: number) {
  return {
    id: 'b1',
    type: 'instance-suspension',
    totalJobs: 4,
    suspended: false,
    createUserId: 'demo',
    startTime: null,
    batchJobDefinitionId: 'jd1',
    remainingJobs: 4 - completedJobs,
    completedJobs,
    failedJobs,
  };
}

function renderReceipts() {
  const router = createMemoryRouter([{ path: '*', element: <BatchReceipts /> }]);
  render(
    <Providers queryClient={createQueryClient()} locale="en">
      <RouterProvider router={router} />
    </Providers>,
  );
}

function Count() {
  return <span>{useReceipts().length}</span>;
}

describe('receipts of bulk changes', () => {
  it('keeps a receipt of what a call started and ignores calls that start no batch', async () => {
    render(<Count />);
    await act(() => withReceipt(() => Promise.resolve(stats(0, 0)), 'Suspend 4 instances')());
    await act(() => withReceipt(() => Promise.resolve(undefined), 'Migrate')());
    expect(screen.getByText('1')).toBeInTheDocument();
    act(() => ['b2', 'b3', 'b4', 'b5', 'b6'].forEach((id) => addReceipt({ id }, id)));
    // only the newest few are kept
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('shows the progress, names the failures and retries them', async () => {
    const user = userEvent.setup();
    let failed = 2;
    const server = fakeServer({
      [`GET ${ENGINE}/batch/statistics`]: () => json([stats(failed, 2)]),
      [`PUT ${ENGINE}/job-definition/jd1/retries`]: () => {
        failed = 0;
        return empty();
      },
      [`GET ${ENGINE}/job`]: () => json([]),
    });
    act(() => addReceipt(stats(0, 0), 'Suspend 4 instances'));
    renderReceipts();

    const receipts = await screen.findByRole('region', { name: 'Receipts of bulk changes' });
    expect(within(receipts).getByText('Suspend 4 instances')).toBeInTheDocument();
    expect(await within(receipts).findByText(/Waiting for you: 2 of 4 jobs done, 2 failed/)).toBeInTheDocument();
    expect(server.find('GET', `${ENGINE}/batch/statistics`)[0]?.query.get('batchId')).toBe('b1');

    await user.click(within(receipts).getByRole('button', { name: 'Retry failed (2)' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/job-definition/jd1/retries`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/job-definition/jd1/retries`)[0]?.body).toEqual({ retries: 1 });
    expect(await within(receipts).findByText(/Running: 2 of 4 jobs done, failed: 0/)).toBeInTheDocument();

    await user.click(within(receipts).getByRole('button', { name: 'Dismiss the receipt of Suspend 4 instances' }));
    expect(screen.queryByRole('region', { name: 'Receipts of bulk changes' })).toBeNull();
  });

  it('says when the batch has ended', async () => {
    fakeServer({ [`GET ${ENGINE}/batch/statistics`]: () => json([]) });
    act(() => addReceipt(stats(0, 0), 'Delete 4 instances'));
    renderReceipts();
    expect(await screen.findByText('No longer running: the batch finished or was removed')).toBeInTheDocument();
  });
});
