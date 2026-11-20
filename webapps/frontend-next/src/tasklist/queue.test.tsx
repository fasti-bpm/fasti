/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tooltip } from 'radix-ui';
import { describe, expect, it, vi } from 'vitest';
import { detectVariableType } from '../api/engine';
import { I18nProvider } from '../i18n';
import { toEngineDate } from '../lib/dates';
import { fakeServer, json } from '../test/server';
import { DueBadge } from './TaskList';
import { minuteOf, queueQuery, queueSorting, slaUsed } from './queue';
import { VariableSortDialog } from './VariableSortDialog';

const ENGINE = '/engine/engine/default';
const NOW = new Date(2026, 2, 1, 9, 30, 0);

describe('urgency views', () => {
  it('ask for late tasks, tasks due within some hours and unclaimed ones', () => {
    expect(queueQuery('all', NOW)).toEqual({});
    expect(queueQuery('overdue', NOW)).toEqual({ dueBefore: toEngineDate(NOW) });
    expect(queueQuery('due4', NOW)).toEqual({
      dueAfter: toEngineDate(NOW),
      dueBefore: toEngineDate(new Date(2026, 2, 1, 13, 30, 0)),
    });
    expect(queueQuery('unclaimed', NOW)).toEqual({ unassigned: true });
  });

  it('bring their order: soonest due, or longest waiting first', () => {
    expect(queueSorting('due24')).toEqual({ sortBy: 'dueDate', sortOrder: 'asc' });
    expect(queueSorting('unclaimed')).toEqual({ sortBy: 'created', sortOrder: 'asc' });
    expect(queueSorting('all')).toBeNull();
    expect(minuteOf(new Date(2026, 2, 1, 9, 30, 41, 500))).toEqual(NOW);
  });

  it('measure how much of the time to the due date is used', () => {
    const created = new Date(2026, 2, 1, 8, 0);
    const due = new Date(2026, 2, 1, 12, 0);
    expect(slaUsed(created, due, new Date(2026, 2, 1, 9, 0))).toBe(0.25);
    expect(slaUsed(created, due, new Date(2026, 2, 1, 13, 0))).toBe(1);
    expect(slaUsed(created, null, NOW)).toBeNull();
  });

  it('show the clock on the due badge of a card', () => {
    const due = new Date(Date.now() + 60 * 60 * 1000);
    const created = new Date(Date.now() - 3 * 60 * 60 * 1000);
    render(
      <I18nProvider initial="en">
        <DueBadge due={toEngineDate(due)} created={toEngineDate(created)} />
      </I18nProvider>,
    );
    const meter = screen.getByRole('meter', { name: 'Share of the time until the due date used' });
    expect(meter).toHaveAttribute('aria-valuenow', '75');
    expect(meter.closest('.badge')).toHaveClass('badge--soon');
  });
});

describe('sorting by a variable', () => {
  it('detects the type most values of a variable are stored with', async () => {
    const server = fakeServer({
      [`POST ${ENGINE}/variable-instance`]: () => json([{ type: 'Long' }, { type: 'Integer' }, { type: 'Long' }, { type: 'Object' }]),
    });
    expect(await detectVariableType('amount')).toBe('Long');
    expect(server.find('POST', `${ENGINE}/variable-instance`)[0]?.body).toEqual({ variableName: 'amount' });
  });

  it('sorts with the detected type so that the order is right', async () => {
    fakeServer({ [`POST ${ENGINE}/variable-instance`]: () => json([{ type: 'Double' }]) });
    const onSort = vi.fn();
    render(
      <I18nProvider initial="en">
        <Tooltip.Provider>
          <VariableSortDialog open current={{ sortBy: 'created', sortOrder: 'desc' }} onOpenChange={() => undefined} onSort={onSort} />
        </Tooltip.Provider>
      </I18nProvider>,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Variable name'), 'score');
    await user.tab();
    await waitFor(() => expect(screen.getByText('Stored as Double')).toBeInTheDocument());
    // dismissed like every other dialog, before the main action
    const actions = screen.getAllByRole('button').map((button) => button.textContent);
    expect(actions.indexOf('Back')).toBeGreaterThan(-1);
    expect(actions.indexOf('Back')).toBeLessThan(actions.indexOf('Sort'));
    await user.click(screen.getByRole('button', { name: 'Sort' }));
    expect(onSort).toHaveBeenCalledWith({
      sortBy: 'processVariable',
      sortOrder: 'desc',
      parameters: { variable: 'score', type: 'Double' },
    });
  });
});
