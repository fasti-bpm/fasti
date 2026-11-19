/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { I18nProvider } from '../i18n';
import { FollowUpBadge } from './TaskList';

describe('the follow-up date on a task card', () => {
  const now = new Date('2026-03-01T09:00:00Z');

  it('says until when a task is set aside', () => {
    render(
      <I18nProvider initial="en">
        <FollowUpBadge followUp="2026-03-03T09:00:00.000+0000" now={now} />
      </I18nProvider>,
    );
    expect(screen.getByText(/Set aside until/)).toBeVisible();
  });

  it('is gone once the date has come', () => {
    const { container } = render(
      <I18nProvider initial="en">
        <FollowUpBadge followUp="2026-02-01T09:00:00.000+0000" now={now} />
      </I18nProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
