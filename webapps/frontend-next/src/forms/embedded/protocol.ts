/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { FormTarget, ProcessInstanceRef } from '../../api/types';

/*
 * The messages between this application (host) and the form frame, which
 * renders embedded HTML forms (frontend/ui/forms/client/scripts/protocol.js
 * is the other side). The host tells the frame which task or start event to
 * render. With transport `direct` (forms isolation `same-origin`) the frame
 * loads the form itself with the session of the user, like the classic
 * Tasklist did. With transport `broker` (the isolated modes) the frame has
 * no session: it sends every request to the host, which performs it only if
 * the allow-list of ./broker.ts permits it.
 */

export const HOST_SOURCE = 'fasti-form-host';
export const FRAME_SOURCE = 'fasti-form-frame';

export type HostMessage =
  | {
      type: 'init';
      target: FormTarget;
      disabled: boolean;
      theme: 'light' | 'dark';
      csrfCookieName: string;
      transport: Transport;
      /** the stored entered values of this form (transport `broker` only) */
      storedValues: string | null;
    }
  | {
      type: 'response';
      id: number;
      status: number;
      contentType: string;
      body: string;
    }
  | { type: 'submit' }
  | { type: 'store' }
  | { type: 'disabled'; disabled: boolean }
  | { type: 'theme'; theme: 'light' | 'dark' };

export type Transport = 'direct' | 'broker';

export type FrameMessage =
  /** the frame is ready to receive `init` */
  | { type: 'ready' }
  /** the form is rendered and its variables are applied */
  | { type: 'loaded' }
  /** the height of the rendered form in CSS pixels */
  | { type: 'resize'; height: number }
  /** the validation state of the form */
  | { type: 'state'; valid: boolean; dirty: boolean }
  /** the task was completed or the process started */
  | { type: 'submitted'; processInstance: ProcessInstanceRef | null }
  /** submitting failed, or a script of the form prevented it */
  | { type: 'failed'; prevented: boolean; message: string }
  /** the form could not be loaded */
  | { type: 'error'; message: string }
  /** the session of the user ended */
  | { type: 'unauthorized' }
  /** a request the frame asks the host to perform (transport `broker`) */
  | {
      type: 'request';
      id: number;
      method: string;
      url: string;
      accept: string;
      contentType: string;
      body: string | null;
    }
  /** the user asked for the file of a variable (transport `broker`) */
  | { type: 'download'; url: string; filename: string }
  /** the entered values of the form were stored or removed (transport `broker`) */
  | { type: 'storage'; value: string | null };

/** Upper bound of the frame height; protects the layout from absurd values. */
export const MAX_FRAME_HEIGHT = 20_000;

const MAX_TEXT = 2_000;
const MAX_URL = 8_192;
/** Upper bound of a request body, e.g. a file upload encoded as base64. */
export const MAX_BODY = 32 * 1024 * 1024;
const ID = /^[\w.:-]{1,255}$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function text(value: unknown): string {
  return typeof value === 'string' ? value.slice(0, MAX_TEXT) : '';
}

/** A header value of a brokered request: printable ASCII only, bounded. */
function headerValue(value: unknown): string {
  return typeof value === 'string' && /^[ -~]{0,200}$/.test(value) ? value : '';
}

function optionalId(value: unknown): string | null {
  return typeof value === 'string' && ID.test(value) ? value : null;
}

function processInstance(value: unknown): ProcessInstanceRef | null {
  if (!isRecord(value)) {
    return null;
  }
  const id = optionalId(value.id);
  if (!id) {
    return null;
  }
  return {
    id,
    definitionId: optionalId(value.definitionId) ?? '',
    businessKey: typeof value.businessKey === 'string' ? value.businessKey.slice(0, 255) : null,
  };
}

/**
 * Checks a message that claims to come from the form frame. Returns a copy
 * with the known properties only, or null for anything else.
 */
export function parseFrameMessage(data: unknown): FrameMessage | null {
  if (!isRecord(data) || data.source !== FRAME_SOURCE) {
    return null;
  }
  switch (data.type) {
    case 'ready':
    case 'loaded':
    case 'unauthorized':
      return { type: data.type };
    case 'resize': {
      const height = data.height;
      if (typeof height !== 'number' || !Number.isFinite(height) || height < 0) {
        return null;
      }
      return { type: 'resize', height: Math.min(Math.ceil(height), MAX_FRAME_HEIGHT) };
    }
    case 'state':
      return { type: 'state', valid: data.valid === true, dirty: data.dirty === true };
    case 'submitted':
      return { type: 'submitted', processInstance: processInstance(data.processInstance) };
    case 'failed':
      return { type: 'failed', prevented: data.prevented === true, message: text(data.message) };
    case 'error':
      return { type: 'error', message: text(data.message) };
    case 'request': {
      const { id, method, url, accept, contentType, body } = data;
      if (
        typeof id !== 'number' ||
        !Number.isSafeInteger(id) ||
        id <= 0 ||
        typeof method !== 'string' ||
        !/^[A-Za-z]{1,10}$/.test(method) ||
        typeof url !== 'string' ||
        url.length > MAX_URL ||
        (body !== null && body !== undefined && (typeof body !== 'string' || body.length > MAX_BODY))
      ) {
        return null;
      }
      return {
        type: 'request',
        id,
        method: method.toUpperCase(),
        url,
        accept: headerValue(accept),
        contentType: headerValue(contentType),
        body: typeof body === 'string' ? body : null,
      };
    }
    case 'download': {
      if (typeof data.url !== 'string' || data.url.length > MAX_URL) {
        return null;
      }
      return { type: 'download', url: data.url, filename: text(data.filename).slice(0, 255) };
    }
    case 'storage': {
      const value = data.value;
      if (value !== null && typeof value !== 'string') {
        return null;
      }
      return { type: 'storage', value };
    }
    default:
      return null;
  }
}

/**
 * Accepts a message event only if it was sent by the given frame window and
 * from the origin the frame is expected to have: the origin of this page
 * (same-origin), `null` (sandboxed, an opaque origin) or the forms origin.
 * Everything else, e.g. messages of other frames, windows or origins, is
 * ignored.
 */
export function acceptFrameEvent(
  event: MessageEvent,
  frame: Window | null | undefined,
  origin: string = window.location.origin,
): FrameMessage | null {
  if (!frame || event.source !== frame || event.origin !== origin) {
    return null;
  }
  return parseFrameMessage(event.data);
}

/**
 * Sends a message to the frame. The target origin is the origin of the frame
 * (`*` for a frame with an opaque origin, which cannot be addressed by
 * origin; the host never sends secrets to the frame, and responses go to
 * the frame window that asked for them).
 */
export function postToFrame(
  frame: Window | null | undefined,
  message: HostMessage,
  origin: string = window.location.origin,
): void {
  frame?.postMessage({ ...message, source: HOST_SOURCE }, origin);
}
