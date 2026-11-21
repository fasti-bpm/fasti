/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { ConfirmDialog } from './controls';
import { Dialog } from './index';

describe('a dialog opened from a form', () => {
  it('confirms without submitting the form around it', async () => {
    const save = vi.fn((event: React.FormEvent) => event.preventDefault());
    const remove = vi.fn();
    render(
      <I18nProvider initial="en">
        <form onSubmit={save}>
          <button type="submit">Save</button>
          <ConfirmDialog
            open
            onOpenChange={() => undefined}
            title="Delete filter"
            text="The filter is deleted."
            confirmLabel="Delete"
            danger
            onConfirm={remove}
          />
        </form>
      </I18nProvider>,
    );

    await userEvent.setup().click(screen.getByRole('button', { name: 'Delete' }));
    expect(remove).toHaveBeenCalledTimes(1);
    expect(save).not.toHaveBeenCalled();
  });

  it('keeps the submit of any form in it from the form around it', async () => {
    const outer = vi.fn((event: React.FormEvent) => event.preventDefault());
    const inner = vi.fn((event: React.FormEvent) => event.preventDefault());
    render(
      <I18nProvider initial="en">
        <form onSubmit={outer}>
          <Dialog open onOpenChange={() => undefined} title="Rename">
            <form onSubmit={inner}>
              <input aria-label="Name" defaultValue="a" />
              <button type="submit">Apply</button>
            </form>
          </Dialog>
        </form>
      </I18nProvider>,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Apply' }));
    await user.type(screen.getByRole('textbox', { name: 'Name' }), '{Enter}');
    expect(inner).toHaveBeenCalledTimes(2);
    expect(outer).not.toHaveBeenCalled();
  });
});
