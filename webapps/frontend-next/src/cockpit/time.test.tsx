/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '../i18n';
import { formatUtc } from '../lib/dates';
import { setTimeDisplay, timeDisplay } from '../lib/userPrefs';
import { Time } from './common';

const VALUE = '2026-03-01T09:30:00.000+0000';

function shown(): HTMLElement {
  render(
    <I18nProvider initial="en">
      <Time value={VALUE} />
    </I18nProvider>,
  );
  return screen.getByText((_, element) => element?.tagName === 'TIME');
}

describe('times', () => {
  it('are local by default with the other forms in the tooltip', () => {
    expect(timeDisplay()).toBe('local');
    const time = shown();
    expect(time).toHaveAttribute('datetime', '2026-03-01T09:30:00.000Z');
    expect(time.title).toContain('Mar 1, 2026, 9:30 AM UTC');
    expect(time.title).toContain('ago');
  });

  it('are shown in UTC or relative to now as the user chose', () => {
    setTimeDisplay('utc');
    expect(shown()).toHaveTextContent('Mar 1, 2026, 9:30 AM UTC');
    setTimeDisplay('relative');
    render(
      <I18nProvider initial="en">
        <Time value={VALUE} />
      </I18nProvider>,
    );
    expect(screen.getAllByText(/ago/).length).toBeGreaterThan(0);
  });

  it('formats UTC independently of the time zone of the browser', () => {
    expect(formatUtc(new Date(Date.UTC(2026, 0, 2, 23, 5)), 'en')).toBe('Jan 2, 2026, 11:05 PM UTC');
  });
});
