/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Api, waitFor } from './api';
import { cleanUp, seed } from './data';
import { baseUrl, engine } from './env';

export default async function globalSetup(): Promise<void> {
  // the distribution may still be starting; wait for it without trying to
  // log in, since failed logins lock the user for a while
  await waitFor(
    async () => {
      try {
        const response = await fetch(new URL(`api/engine/engine/${engine}/version`, baseUrl), { redirect: 'manual' });
        return response.status > 0 && response.status < 500;
      } catch {
        return false;
      }
    },
    `the server at ${baseUrl}`,
    180_000,
  );
  const api = await Api.login();
  try {
    await cleanUp(api);
    await seed(api);
  } finally {
    await api.dispose();
  }
}
