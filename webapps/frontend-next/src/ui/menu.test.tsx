/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DropdownMenu, Tooltip as RadixTooltip } from 'radix-ui';
import { useState } from 'react';
import { I18nProvider } from '../i18n';
import { Dialog, Tooltip } from './index';
import { MenuContent, isQuietFocus } from './menu';

function Menu() {
  return (
    <RadixTooltip.Provider delayDuration={0}>
      <DropdownMenu.Root>
        <Tooltip label="Language">
          <DropdownMenu.Trigger asChild>
            <button type="button">Open</button>
          </DropdownMenu.Trigger>
        </Tooltip>
        <DropdownMenu.Portal>
          <MenuContent>
            <DropdownMenu.Item className="menu__item">English</DropdownMenu.Item>
          </MenuContent>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    </RadixTooltip.Provider>
  );
}

function MenuWithDialog() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <button type="button">Actions</button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <MenuContent>
            <DropdownMenu.Item className="menu__item" onSelect={() => setOpen(true)}>
              Delete
            </DropdownMenu.Item>
          </MenuContent>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
      <Dialog open={open} onOpenChange={setOpen} title="Delete it">
        <p>Sure?</p>
      </Dialog>
    </>
  );
}

describe('MenuContent', () => {
  it('returns the focus quietly after a choice with the pointer', async () => {
    const user = userEvent.setup();
    render(<Menu />);
    await user.click(screen.getByRole('button', { name: 'Open' }));
    await user.click(await screen.findByRole('menuitem', { name: 'English' }));
    const button = screen.getByRole('button', { name: 'Open' });
    await waitFor(() => expect(button).toHaveFocus());
    expect(button).toHaveAttribute('data-quiet-focus');
    expect(isQuietFocus()).toBe(true);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('returns the focus visibly after a choice with the keyboard', async () => {
    const user = userEvent.setup();
    render(<Menu />);
    screen.getByRole('button', { name: 'Open' }).focus();
    await user.keyboard('{Enter}');
    await screen.findByRole('menuitem', { name: 'English' });
    await user.keyboard('{Enter}');
    const button = screen.getByRole('button', { name: 'Open' });
    await waitFor(() => expect(button).toHaveFocus());
    expect(button).not.toHaveAttribute('data-quiet-focus');
  });

  it('gives the focus back to the menu button when a dialog opened from an item closes', async () => {
    const user = userEvent.setup();
    render(
      <I18nProvider initial="en">
        <MenuWithDialog />
      </I18nProvider>,
    );
    screen.getByRole('button', { name: 'Actions' }).focus();
    await user.keyboard('{Enter}');
    await screen.findByRole('menuitem', { name: 'Delete' });
    await user.keyboard('{Enter}');
    await screen.findByRole('dialog', { name: 'Delete it' });
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Actions' })).toHaveFocus());
  });
});
