/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */

'use strict';

/**
 * The messages between the new web UI (host) and the form frame. Keep in
 * sync with frontend-next/src/forms/embedded/protocol.ts.
 *
 * host -> frame: init, response, submit, store, disabled, theme
 * frame -> host: ready, loaded, resize, state, submitted, failed, error,
 *                unauthorized, request, download, storage
 *
 * With transport `broker` (the isolated modes of the forms isolation) the
 * frame has no session; it sends its requests to the host (`request`), which
 * answers with `response` after checking them against its allow-list.
 */
var HOST = 'fasti-form-host';
var FRAME = 'fasti-form-frame';
var ID = /^[\w.:-]{1,255}$/;

function isObject(value) {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function validTarget(target) {
  if (!isObject(target)) {
    return null;
  }
  if (target.type === 'task' && ID.test(target.taskId)) {
    return {type: 'task', taskId: target.taskId};
  }
  if (target.type === 'start' && ID.test(target.processDefinitionId)) {
    return {type: 'start', processDefinitionId: target.processDefinitionId};
  }
  return null;
}

/**
 * Checks a message of the host. Returns a copy that only contains the known
 * properties, or null if the message is not one of the host.
 */
function parseHostMessage(data) {
  if (!isObject(data) || data.source !== HOST) {
    return null;
  }
  switch (data.type) {
    case 'init': {
      var target = validTarget(data.target);
      if (!target) {
        return null;
      }
      return {
        type: 'init',
        target: target,
        disabled: data.disabled === true,
        theme: data.theme === 'dark' ? 'dark' : 'light',
        csrfCookieName:
          typeof data.csrfCookieName === 'string' &&
          /^[\w-]{1,64}$/.test(data.csrfCookieName)
            ? data.csrfCookieName
            : 'XSRF-TOKEN',
        // only an explicit `direct` lets the frame use a session of its own
        transport: data.transport === 'direct' ? 'direct' : 'broker',
        storedValues:
          typeof data.storedValues === 'string' ? data.storedValues : null
      };
    }
    case 'response': {
      if (
        typeof data.id !== 'number' ||
        Math.floor(data.id) !== data.id ||
        data.id <= 0 ||
        typeof data.status !== 'number' ||
        Math.floor(data.status) !== data.status ||
        data.status < 0 ||
        data.status > 999
      ) {
        return null;
      }
      return {
        type: 'response',
        id: data.id,
        status: data.status,
        contentType:
          typeof data.contentType === 'string'
            ? data.contentType.slice(0, 200)
            : '',
        body: typeof data.body === 'string' ? data.body : ''
      };
    }
    case 'submit':
      return {type: 'submit'};
    case 'store':
      return {type: 'store'};
    case 'disabled':
      return {type: 'disabled', disabled: data.disabled === true};
    case 'theme':
      return {type: 'theme', theme: data.theme === 'dark' ? 'dark' : 'light'};
    default:
      return null;
  }
}

module.exports = {
  HOST: HOST,
  FRAME: FRAME,
  parseHostMessage: parseHostMessage
};
