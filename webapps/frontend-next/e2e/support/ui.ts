/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { expect } from '@playwright/test';
import type { Locator, Page, Response } from '@playwright/test';

/** The open modal dialog, whichever role the dialog component uses. */
export function dialog(page: Page): Locator {
  return page.getByRole('dialog').or(page.getByRole('alertdialog'));
}

/** Waits for the answer of the server to a request of the page, e.g. `answered(page, 'DELETE', /\/filter\/[^/]+$/)`. */
export function answered(page: Page, method: string, path: RegExp): Promise<Response> {
  return page.waitForResponse((response) => response.request().method() === method && path.test(new URL(response.url()).pathname));
}

/**
 * Claims the open task with its Claim button and waits until the server made the claim. The
 * page shows a claim before that; what a test does next to the task itself (completing it,
 * reading it through the API) is about a claimed task only from here on.
 */
export async function claimOpenTask(page: Page): Promise<void> {
  const details = page.getByRole('region', { name: 'Task details' });
  const claimed = answered(page, 'POST', /\/task\/[^/]+\/claim$/);
  await details.getByRole('button', { name: 'Claim', exact: true }).first().click();
  expect((await claimed).ok()).toBe(true);
  await expect(details.getByRole('button', { name: 'Unclaim' })).toBeVisible();
}
