/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { Locator, Page } from '@playwright/test';

/** The open modal dialog, whichever role the dialog component uses. */
export function dialog(page: Page): Locator {
  return page.getByRole('dialog').or(page.getByRole('alertdialog'));
}
