/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// Counts the requests the web UI sends while a page is open, with the Chromium of Playwright
// against a running distribution. Used for docs/performance-web-ui.md.
//
//   node scripts/request-trace.mjs [--base http://localhost:18800/fasti/] [--user demo] [--password demo]
//                                  [--engine default] [--seed 46] [--window 120] [--idle 0] [--out trace.json]
//
// --seed N    creates N groups and N users (ids start with "perf") and a few tasks before the
//             trace and removes them afterwards, so that the workload board has full pages
// --window S  seconds a page is watched after it has loaded; the pointer moves every 10 s, as
//             with a user at the screen
// --idle S    additionally watches the task list and the dashboard after S seconds without any
//             input (0 = skip)
/* global document -- used in functions that run in the page */
import { writeFileSync } from 'node:fs';
import { chromium, request } from '@playwright/test';

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) args.set(process.argv[i].replace(/^--/, ''), process.argv[i + 1]);
const base = (args.get('base') ?? 'http://localhost:18800/fasti/').replace(/\/*$/, '/');
const user = args.get('user') ?? 'demo';
const password = args.get('password') ?? 'demo';
const engine = args.get('engine') ?? 'default';
const seed = Number(args.get('seed') ?? 46);
const windowSeconds = Number(args.get('window') ?? 120);
const idleSeconds = Number(args.get('idle') ?? 0);
const out = args.get('out');
const PREFIX = 'perf';
const two = (n) => String(n).padStart(2, '0');
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// --- REST -------------------------------------------------------------------

async function login() {
  const context = await request.newContext({ baseURL: base, extraHTTPHeaders: { Accept: 'application/json' } });
  const token = async () => {
    const response = await context.get(`api/admin/auth/user/${engine}`, { headers: { 'X-XSRF-TOKEN': 'Fetch' } });
    const value = response.headers()['x-xsrf-token'];
    return value && value !== 'Required' ? { 'X-XSRF-TOKEN': value } : {};
  };
  const response = await context.post(`api/admin/auth/user/${engine}/login/welcome`, {
    form: { username: user, password },
    headers: await token(),
  });
  if (!response.ok()) throw new Error(`login failed: ${response.status()}`);
  const headers = await token();
  const url = (path) => `api/engine/engine/${engine}/${path}`;
  const call = async (method, path, data) => {
    const answer = await context.fetch(url(path), { method, data, headers });
    if (!answer.ok() && answer.status() !== 404) throw new Error(`${method} ${path}: ${answer.status()} ${await answer.text()}`);
    return answer.status() === 204 || answer.status() === 404 ? undefined : answer.json().catch(() => undefined);
  };
  return { context, call, storage: () => context.storageState() };
}

async function seedData(api) {
  const run = Date.now().toString(36);
  await removeData(api);
  for (let i = 1; i <= seed; i++) {
    await api.call('POST', 'group/create', { id: `${PREFIX}g${two(i)}`, name: `Perf group ${two(i)}`, type: 'WORKFLOW' });
    await api.call('POST', 'user/create', {
      profile: { id: `${PREFIX}u${two(i)}`, firstName: 'Perf', lastName: `User ${two(i)}`, email: `${PREFIX}u${two(i)}@example.org` },
      credentials: { password: `Perf-${two(i)}-load-trace!` },
    });
  }
  // some work for a part of them, a third of it late; ids are new in every run, as the history keeps the old ones
  for (let i = 1; i <= Math.min(seed, 20); i++) {
    const id = `${PREFIX}task${run}${two(i)}`;
    await api.call('POST', 'task/create', {
      id,
      name: `Perf task ${two(i)}`,
      assignee: i % 2 ? `${PREFIX}u${two(i)}` : null,
      due: i % 3 === 0 ? '2020-01-01T00:00:00.000+0000' : null,
    });
    await api.call('POST', `task/${id}/identity-links`, { groupId: `${PREFIX}g${two(i)}`, type: 'candidate' });
  }
}

async function removeData(api) {
  const tasks = (await api.call('GET', `task?nameLike=${encodeURIComponent('Perf task %')}&maxResults=200`)) ?? [];
  for (const task of tasks) if (task.id.startsWith(`${PREFIX}task`)) await api.call('DELETE', `task/${task.id}`);
  for (let i = 1; i <= seed; i++) {
    await api.call('DELETE', `group/${PREFIX}g${two(i)}`);
    await api.call('DELETE', `user/${PREFIX}u${two(i)}`);
  }
}

// --- trace ------------------------------------------------------------------

/** Records every request of a page through the DevTools protocol, which also knows the bytes on the wire. */
async function record(page) {
  const session = await page.context().newCDPSession(page);
  await session.send('Network.enable');
  const requests = new Map();
  let log = [];
  let last = Date.now();
  session.on('Network.requestWillBeSent', (event) => {
    if (!/^https?:/.test(event.request.url)) return;
    last = Date.now();
    const entry = { url: event.request.url, method: event.request.method, bytes: 0, time: Date.now() };
    requests.set(event.requestId, entry);
    log.push(entry);
  });
  session.on('Network.loadingFinished', (event) => {
    const entry = requests.get(event.requestId);
    if (entry) entry.bytes = event.encodedDataLength;
    last = Date.now();
  });
  return {
    /** Resolves when no request started or ended for `quiet` ms. */
    async settled(quiet = 2500, limit = 45_000) {
      const start = Date.now();
      while (Date.now() - last < quiet && Date.now() - start < limit) await sleep(200);
    },
    take() {
      const taken = log;
      log = [];
      return taken;
    },
  };
}

function summarize(entries, seconds) {
  const isApi = (entry) => /\/api\//.test(new URL(entry.url).pathname);
  const api = entries.filter(isApi);
  const byPath = new Map();
  for (const entry of api) {
    const path = new URL(entry.url).pathname
      .replace(/^.*\/api\/(engine\/engine|cockpit\/plugin\/base|admin\/[a-z]+\/[a-z]+)\/[^/]+\//, '')
      .replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/g, '{id}')
      .replace(new RegExp(`${PREFIX}(g|u|task)[0-9a-z]+`, 'g'), '{id}');
    const key = `${entry.method} ${path}`;
    byPath.set(key, (byPath.get(key) ?? 0) + 1);
  }
  const bytes = (list) => list.reduce((sum, entry) => sum + entry.bytes, 0);
  const result = {
    requests: entries.length,
    apiRequests: api.length,
    bytes: bytes(entries),
    apiBytes: bytes(api),
    top: [...byPath.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12),
  };
  if (seconds) {
    result.seconds = seconds;
    result.apiRequestsPerMinute = Math.round((api.length * 600) / seconds) / 10;
    result.apiKilobytesPerMinute = Math.round((bytes(api) * 60) / seconds / 102.4) / 10;
  }
  return result;
}

/** Watches a page for `seconds`; with `active` the pointer moves every 10 s. */
async function watch(page, recorder, seconds, active) {
  recorder.take();
  const end = Date.now() + seconds * 1000;
  let step = 0;
  while (Date.now() < end) {
    await sleep(Math.min(10_000, Math.max(0, end - Date.now())));
    if (active) await page.mouse.move(300 + (step++ % 5) * 40, 300);
  }
  return summarize(recorder.take(), seconds);
}

async function tracePage(browser, storageState, name, route, extra) {
  const context = await browser.newContext({ baseURL: base, storageState, viewport: { width: 1440, height: 900 }, locale: 'en-US' });
  const page = await context.newPage();
  const recorder = await record(page);
  const result = { name, route };
  try {
    await page.goto(`app/next/${engine}/#/${route}`);
    await recorder.settled();
    result.load = summarize(recorder.take());
    result.active = await watch(page, recorder, windowSeconds, true);
    if (extra) Object.assign(result, await extra(page, recorder));
  } finally {
    await context.close();
  }
  console.log(JSON.stringify(result, null, 1));
  return result;
}

/** After `idleSeconds` without input: how much is still asked for. */
const idle = async (page, recorder) => {
  if (!idleSeconds) return {};
  await sleep(idleSeconds * 1000);
  return { idle: await watch(page, recorder, Math.max(windowSeconds, 240), false) };
};

/** A hidden tab, as the page sees it: `document.visibilityState` and the `visibilitychange` event. */
const hidden = async (page, recorder) => {
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
    document.dispatchEvent(new Event('visibilitychange'));
  });
  return { hidden: await watch(page, recorder, Math.min(windowSeconds, 90), false) };
};

/** The requests after claiming and after releasing the first task of the list. */
const claim = async (page, recorder) => {
  const result = {};
  try {
    await page.getByRole('listbox').getByRole('option').first().click();
    const details = page.getByRole('region', { name: 'Task details' });
    await details.getByRole('button', { name: 'Claim', exact: true }).first().waitFor({ timeout: 15_000 });
    await recorder.settled();
    recorder.take();
    await details.getByRole('button', { name: 'Claim', exact: true }).first().click();
    await details.getByRole('button', { name: 'Unclaim' }).waitFor({ timeout: 15_000 });
    await recorder.settled(3500);
    result.afterClaim = summarize(recorder.take());
    await details.getByRole('button', { name: 'Unclaim' }).click();
    await details.getByRole('button', { name: 'Claim', exact: true }).first().waitFor({ timeout: 15_000 });
    await recorder.settled(3500);
    result.afterUnclaim = summarize(recorder.take());
  } catch (error) {
    result.claimError = String(error).split('\n')[0];
  }
  return result;
};

const api = await login();
if (seed > 0) await seedData(api);
const results = [];
const browser = await chromium.launch();
try {
  const storageState = await api.storage();
  const instances = await api.call('GET', 'process-instance?maxResults=1&sortBy=instanceId&sortOrder=asc');
  results.push(await tracePage(browser, storageState, 'workload', 'cockpit/workload', hidden));
  results.push(await tracePage(browser, storageState, 'dashboard', 'cockpit', async (page, recorder) => ({
    ...(await idle(page, recorder)),
    ...(await hidden(page, recorder)),
  })));
  results.push(await tracePage(browser, storageState, 'tasklist', 'tasklist', async (page, recorder) => ({
    ...(await claim(page, recorder)),
    ...(await idle(page, recorder)),
  })));
  if (instances?.[0]) {
    results.push(await tracePage(browser, storageState, 'instance', `cockpit/processes/instance/${instances[0].id}`));
  }
} finally {
  await browser.close();
  if (seed > 0) await removeData(api);
  await api.context.dispose();
}

const line = (result) =>
  `${result.name.padEnd(10)} load ${String(result.load.apiRequests).padStart(4)} api requests | active ${String(result.active.apiRequestsPerMinute).padStart(6)}/min ${String(result.active.apiKilobytesPerMinute).padStart(7)} kB/min` +
  (result.idle ? ` | idle ${result.idle.apiRequestsPerMinute}/min` : '') +
  (result.hidden ? ` | hidden ${result.hidden.apiRequestsPerMinute}/min` : '') +
  (result.afterClaim ? ` | claim ${result.afterClaim.apiRequests}, unclaim ${result.afterUnclaim?.apiRequests}` : '');
console.log(results.map(line).join('\n'));
if (out) writeFileSync(out, JSON.stringify({ base, seed, windowSeconds, idleSeconds, results }, null, 2));
