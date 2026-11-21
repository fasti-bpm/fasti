/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { exchange, requestBlob, type RawResponse } from '../../api/http';
import { getTask } from '../../api/engine';
import type { FormTarget } from '../../api/types';
import { env } from '../../env';
import type { ResolvedForm } from '../types';
import {
  MAX_STORED_VALUE,
  authorize,
  authorizeDownload,
  formResourcePath,
  draftKey,
  type BrokerContext,
} from './broker';
import type { FrameMessage } from './protocol';

type RequestMessage = Extract<FrameMessage, { type: 'request' }>;

/** At most this many requests of one frame are performed at the same time. */
export const MAX_PENDING = 8;

const JSON_TYPE = /^application\/(?:[\w.+-]+\+)?json\s*(?:;\s*charset=utf-8\s*)?$/i;

function refused(reason: string): RawResponse {
  return {
    status: 403,
    contentType: 'application/json',
    text: JSON.stringify({
      type: 'FormRequestRefused',
      message: `Fasti refused this request of the embedded form: ${reason}`,
    }),
  };
}

function logRefusal(method: string, url: string, reason: string): void {
  // eslint-disable-next-line no-console
  console.warn(`Refused a request of an embedded form: ${method} ${url.slice(0, 300)} (${reason})`);
}

/**
 * Performs the requests of one isolated form frame, after checking them
 * against the allow-list of ./broker.ts.
 */
export class FormBroker {
  private context: Promise<BrokerContext> | null = null;
  private pending = 0;

  constructor(
    private readonly target: FormTarget,
    private readonly form: ResolvedForm,
    /** The user whose entered values are kept; without a user nothing is kept. */
    private readonly userId: string | null,
    private readonly origin: string = window.location.origin,
  ) {}

  /** The process or case instance and definition of a task, needed by the allow-list. */
  private loadContext(): Promise<BrokerContext> {
    this.context ??= (async () => {
      const context: BrokerContext = {
        appRoot: env.appRoot,
        engine: env.engine,
        origin: this.origin,
        target: this.target,
        formPath: formResourcePath(this.form, this.target, env.appRoot, env.engine, this.origin),
      };
      if (this.target.type === 'task') {
        try {
          const task = await getTask(this.target.taskId);
          context.processInstanceId = task.processInstanceId ?? null;
          context.processDefinitionId = task.processDefinitionId ?? null;
          context.caseInstanceId = task.caseInstanceId ?? null;
          context.caseDefinitionId = task.caseDefinitionId ?? null;
        } catch {
          // the requests of the task itself are allowed nevertheless
        }
      }
      return context;
    })();
    return this.context;
  }

  async request(message: RequestMessage): Promise<RawResponse> {
    if (this.pending >= MAX_PENDING) {
      logRefusal(message.method, message.url, 'too many requests at once');
      return refused('too many requests at once');
    }
    this.pending++;
    try {
      const context = await this.loadContext();
      const decision = authorize(context, message);
      if (!decision.allowed) {
        logRefusal(message.method, message.url, decision.reason);
        return refused(decision.reason);
      }
      const accept = message.accept || 'application/json';
      if (decision.kind === 'resource') {
        const response = await fetch(decision.path + decision.search, {
          credentials: 'same-origin',
          redirect: 'error',
          headers: { Accept: accept },
        });
        return {
          status: response.status,
          contentType: response.headers.get('Content-Type') ?? '',
          text: await response.text(),
        };
      }
      const hasBody = decision.method !== 'GET' && message.body !== null;
      if (hasBody && message.contentType && !JSON_TYPE.test(message.contentType)) {
        logRefusal(message.method, message.url, 'only JSON bodies are brokered');
        return refused('only JSON bodies are brokered');
      }
      return await exchange(decision.path, {
        method: decision.method,
        search: decision.search,
        accept,
        raw: hasBody ? { body: message.body ?? '', contentType: 'application/json' } : undefined,
      });
    } catch {
      return { status: 0, contentType: '', text: '' };
    } finally {
      this.pending--;
    }
  }

  /** Downloads the file of a variable and saves it in this window. */
  async download(url: string, filename: string): Promise<void> {
    const context = await this.loadContext();
    const decision = authorizeDownload(context, url);
    if (!decision.allowed || decision.kind !== 'api') {
      logRefusal('GET', url, decision.allowed ? 'not a file' : decision.reason);
      return;
    }
    const result = await requestBlob(decision.path, { search: decision.search, accept: '*/*' });
    saveBlob(result.blob, safeFilename(result.filename ?? filename));
  }

  /** The stored entered values of this form, if any. */
  storedValues(): string | null {
    if (!this.userId) {
      return null;
    }
    try {
      return window.localStorage.getItem(draftKey(env.engine, this.userId, this.target));
    } catch {
      return null;
    }
  }

  /** Stores (or with `null` removes) the entered values of this form. */
  store(value: string | null): void {
    if (!this.userId) {
      return;
    }
    const key = draftKey(env.engine, this.userId, this.target);
    try {
      if (value === null) {
        window.localStorage.removeItem(key);
      } else if (value.length <= MAX_STORED_VALUE) {
        window.localStorage.setItem(key, value);
      }
    } catch {
      // storage unavailable or full: the values are not kept
    }
  }
}

/** A file name without path separators or control characters. */
export function safeFilename(name: string): string {
  // eslint-disable-next-line no-control-regex
  const cleaned = name.replace(/[\u0000-\u001f\u007f/\\:*?"<>|]+/g, '_').trim();
  return cleaned.replace(/^\.+/, '').slice(0, 200) || 'download';
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}
