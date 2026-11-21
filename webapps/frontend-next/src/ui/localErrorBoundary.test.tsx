/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useState, type ReactElement } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n';
import { isChunkLoadError, watchStaleBuild } from '../lib/staleBuild';
import { LocalErrorBoundary } from './index';

function Page({ load }: { load: () => Promise<{ default: () => ReactElement }> }) {
  const [Part] = useState(() => lazy(load));
  return (
    <I18nProvider initial="en">
      <label>
        Amount
        <input defaultValue="" />
      </label>
      <LocalErrorBoundary>
        <Suspense fallback={<p>loading</p>}>
          <Part />
        </Suspense>
      </LocalErrorBoundary>
    </I18nProvider>
  );
}

describe('a part of the page that fails', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('says that a newer version is available where a part of an old build could not be loaded, and keeps the rest', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const user = userEvent.setup();
    let fail: (error: Error) => void = () => undefined;
    const loading = new Promise<{ default: () => ReactElement }>((_resolve, reject) => (fail = reject));
    render(<Page load={() => loading} />);
    await user.type(screen.getByLabelText('Amount'), '900');

    fail(new TypeError('Failed to fetch dynamically imported module: /fasti/app/next/assets/CaseTimeline-Ab12Cd34.js'));

    expect(await screen.findByText('A newer version is available.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible();
    // what the user entered elsewhere on the page is still there
    expect(screen.getByLabelText('Amount')).toHaveValue('900');
  });

  it('tries any other failure again in place', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const user = userEvent.setup();
    let broken = true;
    function Fragile() {
      if (broken) throw new Error('broken part');
      return <p>part works</p>;
    }
    render(
      <I18nProvider initial="en">
        <LocalErrorBoundary>
          <Fragile />
        </LocalErrorBoundary>
      </I18nProvider>,
    );
    expect(await screen.findByText('Something unexpected went wrong.')).toBeVisible();

    broken = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(screen.getByText('part works')).toBeVisible();
  });

  it('knows the failures Vite reports, whatever the browser says', () => {
    const stop = watchStaleBuild();
    const error = new Error('something else');
    expect(isChunkLoadError(error)).toBe(false);

    const event = Object.assign(new Event('vite:preloadError', { cancelable: true }), { payload: error });
    window.dispatchEvent(event);

    expect(isChunkLoadError(error)).toBe(true);
    // the page is not reloaded by itself, and the import still fails as before
    expect(event.defaultPrevented).toBe(false);
    stop();
  });
});
