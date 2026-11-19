/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync } from 'node:fs';
import { basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { request, type APIRequestContext, type APIResponse } from '@playwright/test';
import { PREFIX, baseUrl, engine, password, user } from './env';

export const fixturesDir = fileURLToPath(new URL('../fixtures/', import.meta.url));

/**
 * Talks to the REST API through the web application, the way the user
 * interface does: with a session cookie and the CSRF token. This works the same
 * on every distribution, whether the plain REST API needs authentication or not.
 */
export class Api {
  private token: string | null = null;

  private constructor(readonly context: APIRequestContext) {}

  static async login(username = user, secret = password): Promise<Api> {
    // RESTEasy (Tomcat, WildFly) answers */* with application/octet-stream
    const context = await request.newContext({ baseURL: baseUrl, extraHTTPHeaders: { Accept: 'application/json' } });
    const api = new Api(context);
    await api.fetchToken();
    const response = await context.post(`api/admin/auth/user/${engine}/login/welcome`, {
      form: { username, password: secret },
      headers: api.headers(),
    });
    await check(response, 'login');
    // the login replaced the session, and with it the token
    await api.fetchToken();
    return api;
  }

  async dispose(): Promise<void> {
    await this.context.dispose();
  }

  private headers(): Record<string, string> {
    return this.token ? { 'X-XSRF-TOKEN': this.token } : {};
  }

  private async fetchToken(): Promise<void> {
    const response = await this.context.get(`api/admin/auth/user/${engine}`, {
      headers: { 'X-XSRF-TOKEN': 'Fetch' },
    });
    const token = response.headers()['x-xsrf-token'];
    if (token && token !== 'Required') {
      this.token = token;
    }
  }

  /** `path` is relative to the engine API, e.g. `process-definition/count`. */
  private url(path: string): string {
    return `api/engine/engine/${engine}/${path}`;
  }

  async get<T>(path: string, params?: Record<string, string | number | boolean>): Promise<T> {
    const response = await this.context.get(this.url(path), { params, headers: this.headers() });
    await check(response, `GET ${path}`);
    return (await response.json()) as T;
  }

  async post<T>(path: string, data?: unknown): Promise<T> {
    const response = await this.context.post(this.url(path), { data: data ?? {}, headers: this.headers() });
    await check(response, `POST ${path}`);
    return (response.status() === 204 ? undefined : await response.json()) as T;
  }

  async put(path: string, data?: unknown): Promise<void> {
    await check(await this.context.put(this.url(path), { data: data ?? {}, headers: this.headers() }), `PUT ${path}`);
  }

  async delete(path: string, allowMissing = true): Promise<void> {
    const response = await this.context.delete(this.url(path), { headers: this.headers() });
    if (allowMissing && response.status() === 404) {
      return;
    }
    await check(response, `DELETE ${path}`);
  }

  /** The preferences the logged-in user keeps on the server. */
  async preferences(): Promise<Record<string, unknown>> {
    const response = await this.context.get(`api/admin/auth/user/${engine}/preferences`, { headers: this.headers() });
    await check(response, 'load preferences');
    return (await response.json()) as Record<string, unknown>;
  }

  /** Replaces the preferences the logged-in user keeps on the server. */
  async savePreferences(preferences: Record<string, unknown>): Promise<void> {
    const response = await this.context.put(`api/admin/auth/user/${engine}/preferences`, {
      data: preferences,
      headers: this.headers(),
    });
    await check(response, 'save preferences');
  }

  /** Deploys files of the fixtures folder in one deployment. */
  async deploy(name: string, files: string[], tenantId?: string): Promise<Deployment> {
    const multipart: Record<string, string | { name: string; mimeType: string; buffer: Buffer }> = {
      'deployment-name': name,
      'deployment-source': PREFIX,
      'deploy-changed-only': 'false',
      'enable-duplicate-filtering': 'false',
    };
    if (tenantId) {
      multipart['tenant-id'] = tenantId;
    }
    for (const file of files) {
      multipart[basename(file)] = {
        name: basename(file),
        mimeType: 'application/octet-stream',
        buffer: readFileSync(join(fixturesDir, file)),
      };
    }
    const response = await this.context.post(this.url('deployment/create'), {
      multipart,
      headers: this.headers(),
    });
    await check(response, `deploy ${name}`);
    return (await response.json()) as Deployment;
  }

  async startProcess(key: string, variables: Record<string, unknown> = {}, businessKey?: string): Promise<Instance> {
    return this.post<Instance>(`process-definition/key/${key}/start`, {
      variables: toVariables(variables),
      ...(businessKey ? { businessKey } : {}),
    });
  }

  async tasks(processInstanceId: string): Promise<Task[]> {
    return this.get<Task[]>('task', { processInstanceId });
  }

  /** Runs the jobs of an instance until none is left that can run now. */
  async executeJobs(processInstanceId: string): Promise<void> {
    const jobs = await this.get<{ id: string }[]>('job', { processInstanceId, executable: true });
    for (const job of jobs) {
      await this.context.post(this.url(`job/${job.id}/execute`), { headers: this.headers() });
    }
  }
}

export interface Deployment {
  id: string;
  deployedProcessDefinitions?: Record<string, { id: string; key: string; version: number }>;
}

export interface Instance {
  id: string;
  definitionId: string;
  businessKey: string | null;
}

export interface Task {
  id: string;
  name: string;
  assignee: string | null;
  taskDefinitionKey: string;
}

export function toVariables(values: Record<string, unknown>): Record<string, { value: unknown; type?: string }> {
  return Object.fromEntries(
    Object.entries(values).map(([name, value]) => [
      name,
      typeof value === 'number' && Number.isInteger(value)
        ? { value, type: 'Integer' }
        : typeof value === 'number'
          ? { value, type: 'Double' }
          : { value },
    ]),
  );
}

async function check(response: APIResponse, what: string): Promise<void> {
  if (!response.ok()) {
    throw new Error(`${what} failed with ${response.status()}: ${(await response.text()).slice(0, 500)}`);
  }
}

/** Polls until `probe` returns a truthy value. */
export async function waitFor<T>(probe: () => Promise<T | null | undefined | false>, what: string, timeout = 30_000): Promise<T> {
  const end = Date.now() + timeout;
  for (;;) {
    const value = await probe();
    if (value) {
      return value;
    }
    if (Date.now() > end) {
      throw new Error(`timed out waiting for ${what}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}
