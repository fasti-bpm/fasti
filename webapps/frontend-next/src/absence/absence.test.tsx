/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Absence } from '../api/absence';
import type { TaskListItem } from '../api/types';
import { SessionContext } from '../app/session';
import { I18nProvider } from '../i18n';
import { empty, fakeServer, json } from '../test/server';
import { ToastProvider } from '../ui/toast';
import { AbsenceBanner } from './AbsenceBanner';
import { AbsenceSettings, browserTimeZone, draftOf, draftProblems } from './AbsenceSettings';
import { withSubstitutionMarks } from './marks';
import { SubstituteNotice } from './SubstituteNotice';

const ENGINE = '/engine/engine/default';
const NOW = new Date('2026-11-21T08:00:00Z');

function absence(overrides: Partial<Absence> = {}): Absence {
  return {
    userId: 'demo',
    enabled: true,
    unavailableReason: null,
    status: 'none',
    from: null,
    until: null,
    substituteId: null,
    note: null,
    effectiveSubstituteId: null,
    ...overrides,
  };
}

const ACTIVE = absence({
  status: 'active',
  from: '2026-11-20T08:00:00.000+0000',
  until: '2099-11-28T08:00:00.000+0000',
  substituteId: 'mary',
  effectiveSubstituteId: 'mary',
  note: 'Wiki page',
});

const profiles = {
  [`GET ${ENGINE}/user/mary/profile`]: () => json({ id: 'mary', firstName: 'Mary', lastName: 'Major' }),
  [`GET ${ENGINE}/user/demo/profile`]: () => json({ id: 'demo', firstName: 'Demo', lastName: 'User' }),
  [`GET ${ENGINE}/user/anna/profile`]: () => json({ id: 'anna', firstName: 'Anna', lastName: 'Absent' }),
};

function wrap(children: ReactNode, queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  const session = { userId: 'demo', displayName: 'Demo User', authorizedApps: ['tasklist'] } as unknown as React.ContextType<
    typeof SessionContext
  >;
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider initial="en">
        <SessionContext.Provider value={session}>
          <ToastProvider>
            <MemoryRouter>{children}</MemoryRouter>
          </ToastProvider>
        </SessionContext.Provider>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

describe('absence drafts', () => {
  const draft = { from: '', until: '2026-11-25T17:00', substituteId: 'mary', note: '' };

  it('need an end in the future, after the start', () => {
    expect(draftProblems(draft, 'demo', NOW)).toEqual({});
    expect(draftProblems({ ...draft, until: '' }, 'demo', NOW)).toEqual({ until: 'until' });
    expect(draftProblems({ ...draft, from: '2026-11-26T08:00' }, 'demo', NOW)).toEqual({ until: 'order' });
    expect(draftProblems({ ...draft, until: '2026-11-20T08:00' }, 'demo', NOW)).toEqual({ until: 'order' });
    expect(draftProblems({ ...draft, from: '2026-11-01T08:00', until: '2026-11-02T08:00' }, 'demo', NOW)).toEqual({ until: 'past' });
  });

  it('need a substitute other than the absent user and a short note', () => {
    expect(draftProblems({ ...draft, substituteId: ' ' }, 'demo', NOW)).toEqual({ substituteId: 'substitute' });
    expect(draftProblems({ ...draft, substituteId: 'demo' }, 'demo', NOW)).toEqual({ substituteId: 'self' });
    expect(draftProblems({ ...draft, note: 'x'.repeat(256) }, 'demo', NOW)).toEqual({ note: 'note' });
  });
});

describe('substitution marks', () => {
  it('are added to the tasks they belong to', () => {
    const tasks = [{ id: 't1' }, { id: 't2' }] as TaskListItem[];
    expect(withSubstitutionMarks(tasks, undefined)).toBe(tasks);
    const marked = withSubstitutionMarks(tasks, new Map([['t2', 'anna']]));
    expect(marked[0]).toBe(tasks[0]);
    expect(marked[1]?.absentAssignee).toBe('anna');
  });
});

describe('absence settings', () => {
  it('say when the server does not offer absences', async () => {
    fakeServer({ [`GET ${ENGINE}/user/demo/absence`]: () => json(absence({ enabled: false, unavailableReason: 'off' })) });
    wrap(<AbsenceSettings userId="demo" self />);
    expect(await screen.findByText(/not turned on for this server/)).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Save absence' })).toBeNull();
  });

  it('say the same for a server that does not know absences', async () => {
    fakeServer({});
    wrap(<AbsenceSettings userId="demo" self />);
    expect(await screen.findByText(/not turned on for this server/)).toBeVisible();
  });

  it('keep the start of an absence under way when it is changed', async () => {
    // the stored start has seconds the field cannot show
    const started = { ...ACTIVE, from: '2026-11-20T08:00:42.000+0000' };
    const server = fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(started),
      [`PUT ${ENGINE}/user/demo/absence`]: () => json(started),
    });
    wrap(<AbsenceSettings userId="demo" self />);
    const from = await screen.findByLabelText(/^From/);
    expect(from).toHaveValue(draftOf(started).from);
    expect(from).not.toHaveValue('');

    await userEvent.type(screen.getByLabelText('Note for your substitute'), ' and the handbook');
    await userEvent.click(screen.getByRole('button', { name: 'Save absence' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/user/demo/absence`)).toHaveLength(1));
    expect(server.find('PUT', `${ENGINE}/user/demo/absence`)[0]?.body).toMatchObject({
      from: '2026-11-20T08:00:42.000+0000',
      note: 'Wiki page and the handbook',
    });

    // a start that was changed is sent as entered, an emptied one means now
    await userEvent.clear(from);
    await userEvent.click(screen.getByRole('button', { name: 'Save absence' }));
    await waitFor(() => expect(server.find('PUT', `${ENGINE}/user/demo/absence`)).toHaveLength(2));
    expect((server.find('PUT', `${ENGINE}/user/demo/absence`)[1]?.body as { from: unknown }).from).toBeNull();
  });

  it('name the time zone of the date fields', async () => {
    fakeServer({ ...profiles, [`GET ${ENGINE}/user/demo/absence`]: () => json(absence()) });
    wrap(<AbsenceSettings userId="demo" self />);
    expect(await screen.findByText(`Times are in the time zone of this browser (${browserTimeZone()}).`)).toBeVisible();
    expect(browserTimeZone()).toContain(new Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('start a new form for another user, also with the same absence', async () => {
    fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(absence()),
      [`GET ${ENGINE}/user/anna/absence`]: () => json(absence({ userId: 'anna' })),
    });
    function Users() {
      const [userId, setUserId] = useState('demo');
      return (
        <>
          <button type="button" onClick={() => setUserId('anna')}>
            Next user
          </button>
          <AbsenceSettings userId={userId} self={false} />
        </>
      );
    }
    wrap(<Users />);
    await userEvent.type(await screen.findByLabelText(/^Substitute/), 'mary');
    await userEvent.click(screen.getByRole('button', { name: 'Next user' }));
    expect(await screen.findByText(/While Anna Absent is away/)).toBeVisible();
    expect(screen.getByLabelText(/^Substitute/)).toHaveValue('');
  });

  it('check the form and save an absence', async () => {
    const server = fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(absence()),
      [`PUT ${ENGINE}/user/demo/absence`]: () => json(ACTIVE),
    });
    wrap(<AbsenceSettings userId="demo" self />);
    await userEvent.click(await screen.findByRole('button', { name: 'Save absence' }));
    expect(screen.getByText('Choose when the absence ends.')).toBeVisible();
    expect(screen.getByText('Choose a substitute.')).toBeVisible();
    expect(server.find('PUT', `${ENGINE}/user/demo/absence`)).toHaveLength(0);

    await userEvent.type(screen.getByLabelText(/^Until/), '2099-11-28T08:00');
    await userEvent.type(screen.getByLabelText(/^Substitute/), 'mary');
    await userEvent.type(screen.getByLabelText('Note for your substitute'), '  Wiki page ');
    await userEvent.click(screen.getByRole('button', { name: 'Save absence' }));

    await waitFor(() => expect(server.find('PUT', `${ENGINE}/user/demo/absence`)).toHaveLength(1));
    const body = server.find('PUT', `${ENGINE}/user/demo/absence`)[0]?.body as Record<string, unknown>;
    expect(body.from).toBeNull();
    expect(body.substituteId).toBe('mary');
    expect(body.note).toBe('Wiki page');
    expect(new Date(String(body.until).replace(/([+-]\d{2})(\d{2})$/, '$1:$2')).getTime()).toBe(
      new Date('2099-11-28T08:00').getTime(),
    );
    expect(await screen.findByText('Absence saved')).toBeVisible();
  });

  it('show an active absence, end it and hand over the open tasks', async () => {
    const server = fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(ACTIVE),
      [`GET ${ENGINE}/task/count`]: () => json({ count: 3 }),
      [`POST ${ENGINE}/user/demo/absence/handover`]: () => json({ substituteId: 'mary', handedOver: 2, remaining: 1 }),
      [`DELETE ${ENGINE}/user/demo/absence`]: () => empty(),
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    wrap(<AbsenceSettings userId="demo" self />, queryClient);
    expect(await screen.findByText(/Tasks go to Mary Major/)).toBeVisible();
    expect(screen.getByLabelText(/^Substitute/)).toHaveValue('mary');

    await userEvent.click(await screen.findByRole('button', { name: 'Hand over my 3 open tasks now' }));
    const dialog = await screen.findByRole('dialog', { name: 'Hand over open tasks?' });
    expect(dialog).toHaveTextContent('The 3 open tasks of Demo User go to Mary Major now');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Hand over' }));
    expect(await screen.findByText('2 tasks handed over to Mary Major')).toBeVisible();
    // in the notification itself; its text is also read out in a region of its own for a moment
    expect(screen.getByText(/1 task stays assigned/, { selector: '.toast__detail' })).toBeVisible();
    // confirming the handover does not save the form of the absence the dialog was opened from
    expect(server.find('PUT', `${ENGINE}/user/demo/absence`)).toHaveLength(0);
    expect(server.find('GET', `${ENGINE}/task/count`)[0]?.query.get('assignee')).toBe('demo');
    // the task list reads the tasks and whom they are held for again
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['tasks'] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['substitution-marks'] });

    await userEvent.click(screen.getByRole('button', { name: 'End absence' }));
    await waitFor(() => expect(server.find('DELETE', `${ENGINE}/user/demo/absence`)).toHaveLength(1));
  });

  it('name the user in the administration', async () => {
    fakeServer({ ...profiles, [`GET ${ENGINE}/user/anna/absence`]: () => json(absence({ userId: 'anna' })) });
    wrap(<AbsenceSettings userId="anna" self={false} />);
    expect(await screen.findByText(/While Anna Absent is away/)).toBeVisible();
    expect(screen.getByLabelText('Note for the substitute')).toBeVisible();
  });
});

describe('absence banner', () => {
  it('says that you are away and for whom you stand in', async () => {
    fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(ACTIVE),
      [`GET ${ENGINE}/user/demo/absence/substituting`]: () =>
        json([absence({ userId: 'anna', status: 'active', until: '2099-12-01T08:00:00.000+0000', substituteId: 'demo' })]),
    });
    wrap(<AbsenceBanner />);
    expect(await screen.findByText(/You are away until .*Tasks for you go to Mary Major/)).toBeVisible();
    expect(await screen.findByText(/You stand in for Anna Absent until/)).toBeVisible();
    expect(screen.getByRole('link', { name: 'Absence settings' })).toHaveAttribute('href', '/preferences#absence');
  });

  it('stays away when absences are off', async () => {
    const server = fakeServer({ [`GET ${ENGINE}/user/demo/absence`]: () => json(absence({ enabled: false })) });
    wrap(<AbsenceBanner />);
    await waitFor(() => expect(server.find('GET', `${ENGINE}/user/demo/absence`)).toHaveLength(1));
    expect(screen.queryByText(/You are away/)).toBeNull();
    expect(screen.queryByRole('link', { name: 'Absence settings' })).toBeNull();
    expect(server.find('GET', `${ENGINE}/user/demo/absence/substituting`)).toHaveLength(0);
  });
});

describe('substitute notice', () => {
  it('says for whom a task is held', async () => {
    fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(absence()),
      [`GET ${ENGINE}/task/t1/localVariables/fasti_absentAssignee`]: () => json({ type: 'String', value: 'anna' }),
    });
    wrap(<SubstituteNotice task={{ id: 't1', assignee: 'demo' }} />);
    expect(await screen.findByText('Assigned to you as substitute for Anna Absent.')).toBeVisible();
  });

  it('is not shown for other tasks', async () => {
    const server = fakeServer({
      ...profiles,
      [`GET ${ENGINE}/user/demo/absence`]: () => json(absence()),
    });
    wrap(<SubstituteNotice task={{ id: 't2', assignee: 'demo' }} />);
    await waitFor(() => expect(server.find('GET', `${ENGINE}/task/t2/localVariables/fasti_absentAssignee`)).toHaveLength(1));
    expect(screen.queryByText(/as substitute for/)).toBeNull();
  });
});
