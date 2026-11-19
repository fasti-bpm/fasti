/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Api } from './api';
import { cleanUp } from './data';

export default async function globalTeardown(): Promise<void> {
  if (process.env.E2E_KEEP_DATA) {
    return;
  }
  const api = await Api.login();
  try {
    await cleanUp(api);
  } finally {
    await api.dispose();
  }
}
