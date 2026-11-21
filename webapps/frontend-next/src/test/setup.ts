/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { resetHttpState } from '../api/http';
import { resetWorkloadSupport } from '../api/workload';
import { clearReceipts } from '../cockpit/receipts';
import { config, defaultConfig } from '../config';
import { loadAllLocales } from '../i18n';

// A full-app test waits for several fake round trips and renders before an element appears. The
// one-second default of the library is wall-clock time and runs out when the test files share the
// processor; a met condition still returns at once, so only a real failure waits this long.
configure({ asyncUtilTimeout: 5_000 });

// the languages are loaded lazily in the browser; tests switch languages synchronously
beforeAll(loadAllLocales);

// what the components library expects from a browser and jsdom lacks
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;
Element.prototype.scrollIntoView ??= () => {};
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.setPointerCapture ??= () => {};
Element.prototype.releasePointerCapture ??= () => {};

beforeEach(() => {
  window.matchMedia ??= ((query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
    onchange: null,
  })) as unknown as typeof window.matchMedia;
});

afterEach(() => {
  cleanup();
  resetHttpState();
  resetWorkloadSupport();
  Object.assign(config, defaultConfig);
  window.localStorage.clear();
  clearReceipts();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  document.cookie = 'MY-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  delete document.documentElement.dataset.theme;
  vi.unstubAllGlobals();
});
