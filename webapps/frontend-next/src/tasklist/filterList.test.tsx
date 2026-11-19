/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { TaskFilter } from '../api/types';
import { I18nProvider } from '../i18n';
import { FilterList, FilterSelect } from './FilterList';

function filter(id: string, name: string, itemCount?: number | null): TaskFilter {
  return { id, name, owner: null, resourceType: 'Task', properties: null, itemCount };
}

// the server leaves out the count of a filter whose query fails, and lists the others
const filters = [
  filter('mine', 'My tasks', 3),
  filter('groups', 'My group tasks'),
  filter('legacy', 'Legacy', null),
];

describe('the filter list when the count of a filter is missing', () => {
  it('lists every filter and shows only the counts it has', () => {
    render(
      <I18nProvider initial="en">
        <FilterList
          filters={filters}
          error={null}
          selectedId="groups"
          onSelect={() => {}}
          onRetry={() => {}}
          canCreate={false}
          creating={false}
          onCreate={() => {}}
        />
      </I18nProvider>,
    );
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(3);
    const [mine, groups, legacy] = items as [HTMLElement, HTMLElement, HTMLElement];
    expect(within(mine).getByText('3')).toBeVisible();
    expect(within(groups).getByText('My group tasks')).toBeVisible();
    expect(groups.querySelector('.count')).toBeNull();
    expect(legacy.querySelector('.count')).toBeNull();
    expect(legacy.textContent).not.toContain('null');
  });

  it('names the filters without a count in the compact choice', () => {
    render(
      <I18nProvider initial="en">
        <FilterSelect filters={filters} selectedId="mine" onSelect={() => {}} />
      </I18nProvider>,
    );
    const options = screen.getAllByRole('option').map((option) => option.textContent);
    expect(options).toEqual(['My tasks (3)', 'My group tasks', 'Legacy']);
  });
});
