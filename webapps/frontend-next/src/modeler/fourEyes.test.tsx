/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { FourEyesSection, type ExtensionAccess } from './ExtensionFields';
import type { Generic } from './extensions';

const create = (name: string, properties: Record<string, unknown>): Generic => ({ $type: `camunda:${name}`, ...properties });

function show(values: Generic[]) {
  const write = vi.fn<(values: Generic[]) => void>();
  const access: ExtensionAccess = { values, write, create };
  render(
    <I18nProvider initial="en">
      <FourEyesSection
        access={access}
        self={{ id: 'approve', name: 'Approve' }}
        candidates={[
          { id: 'draft', name: 'Draft contract' },
          { id: 'review', name: '' },
          { id: 'approve', name: 'Approve' },
        ]}
      />
    </I18nProvider>,
  );
  return write;
}

describe('four-eyes field of a user task', () => {
  it('offers the other user tasks and the task itself', async () => {
    const user = userEvent.setup();
    const write = show([]);

    const group = screen.getByRole('group', { name: 'Four-eyes check' });
    expect(group).toHaveAccessibleDescription(
      'Whoever completed a step ticked here cannot complete this task in the same case.',
    );
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
    await user.click(screen.getByRole('checkbox', { name: /Draft contract/ }));

    expect(write).toHaveBeenCalledWith([
      { $type: 'camunda:properties', $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft' }] },
    ]);
  });

  it('shows what is named and lets a wrong name be removed', async () => {
    const user = userEvent.setup();
    const write = show([
      {
        $type: 'camunda:properties',
        $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft,approve,ghost' }],
      },
    ]);

    expect(screen.getByRole('checkbox', { name: /Draft contract/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /This task itself/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /review/ })).not.toBeChecked();
    const ghost = screen.getByRole('checkbox', { name: 'ghost (not a user task of this process)' });
    expect(ghost).toBeChecked();

    await user.click(ghost);
    expect(write).toHaveBeenCalledWith([
      {
        $type: 'camunda:properties',
        $children: [{ $type: 'camunda:property', name: 'fasti:fourEyes', value: 'draft,approve' }],
      },
    ]);
  });
});
