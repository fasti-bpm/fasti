/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */

'use strict';

/**
 * The frame side of the broker (transport `broker`). The form frame has no
 * session of its own: it is sandboxed with an opaque origin, or served from
 * another origin. Every request a form makes through the forms SDK (the
 * `camForm` client) or through AngularJS (`$http`) is sent to the host
 * window, which performs it with the session of the user if its allow-list
 * permits it, and answers with the response. Refused requests are answered
 * with status 403 and reported on the console.
 */

var Events = require('camunda-bpm-sdk-js/lib/events');
var utils = require('camunda-bpm-sdk-js/lib/utils');

/** Requests that are not answered in time fail. */
var TIMEOUT = 5 * 60 * 1000;

/**
 * Creates the broker client.
 *
 * @param {Function} post            sends a message to the host: post(type, payload)
 * @param {Function} onUnauthorized  called when a response has status 401
 */
function createBroker(post, onUnauthorized) {
  var nextId = 1;
  var pending = {};

  function isForeign(url) {
    try {
      return (
        new URL(url, window.location.href).origin !== window.location.origin
      );
    } catch (e) {
      return true;
    }
  }

  /**
   * The URL as the host gets it: path and query only. The host resolves it
   * against its own origin, also if the frame is served from another one.
   */
  function hostUrl(url) {
    var parsed = new URL(url, window.location.href);
    return parsed.pathname + parsed.search;
  }

  /**
   * Sends a request to the host.
   *
   * @return {Promise} resolves to {status, contentType, body}; status 0 if
   *                   the request failed without a response
   */
  function request(method, url, options) {
    options = options || {};
    var id = nextId++;
    return new Promise(function(resolve) {
      var timer = setTimeout(function() {
        delete pending[id];
        resolve({status: 0, contentType: '', body: ''});
      }, TIMEOUT);
      pending[id] = function(response) {
        clearTimeout(timer);
        if (response.status === 401) {
          onUnauthorized();
        }
        if (
          response.status === 403 &&
          /FormRequestRefused/.test(response.body)
        ) {
          // eslint-disable-next-line no-console
          console.error(
            'The request ' +
              method +
              ' ' +
              url +
              ' of this form was refused: ' +
              'an isolated form may only use the REST API of its own task or ' +
              'start event.'
          );
        }
        resolve(response);
      };
      post('request', {
        id: id,
        method: method,
        url: hostUrl(url),
        accept: options.accept || 'application/json',
        contentType: options.contentType || '',
        body:
          options.body === undefined || options.body === null
            ? null
            : options.body
      });
    });
  }

  /** Called with the `response` messages of the host. */
  function receive(message) {
    var handler = pending[message.id];
    if (handler) {
      delete pending[message.id];
      handler(message);
    }
  }

  return {
    isForeign: isForeign,
    hostUrl: hostUrl,
    request: request,
    receive: receive,
    HttpClient: createHttpClient(request, isForeign)
  };
}

/** Serializes a query object like superagent, which the SDK client uses. */
function queryString(query) {
  var pairs = [];
  function push(key, value) {
    if (value === undefined) {
      return;
    }
    if (Array.isArray(value)) {
      value.forEach(function(item) {
        push(key, item);
      });
      return;
    }
    if (value !== null && typeof value === 'object') {
      Object.keys(value).forEach(function(sub) {
        push(key + '[' + sub + ']', value[sub]);
      });
      return;
    }
    pairs.push(
      encodeURIComponent(key) +
        (value === null ? '' : '=' + encodeURIComponent(value))
    );
  }
  Object.keys(query || {}).forEach(function(key) {
    push(key, query[key]);
  });
  return pairs.length ? pairs.join('&') : '';
}

function withQuery(url, query) {
  var search = queryString(query);
  if (!search) {
    return url;
  }
  return url + (url.indexOf('?') === -1 ? '?' : '&') + search;
}

/**
 * An HTTP client for the SDK (`CamSDK.Client({HttpClient})`) with the
 * interface and the callback semantics of its own client, which sends the
 * requests through the host.
 */
function createHttpClient(request, isForeign) {
  var CamSDK = require('camunda-bpm-sdk-js/lib/angularjs/index');

  function BrokerHttpClient(config) {
    config = config || {};
    config.headers = config.headers || {};
    if (!config.headers.Accept) {
      config.headers.Accept = 'application/hal+json, application/json; q=0.5';
    }
    if (!config.baseUrl) {
      throw new Error('HttpClient needs a `baseUrl` configuration property.');
    }
    Events.attach(this);
    this.config = config;
    // a form URL of another origin is loaded without the host, as before
    this._direct = new CamSDK.Client.HttpClient(config);
  }

  BrokerHttpClient.prototype.isForeignOrigin = function(url) {
    return isForeign(url);
  };

  BrokerHttpClient.prototype._send = function(method, url, options, body) {
    var self = this;
    options = options || {};
    var done = options.done || function() {};
    var headers = options.headers || this.config.headers;
    var accept = options.accept || headers.Accept || this.config.headers.Accept;
    var payload = null;
    if (body !== undefined) {
      payload = typeof body === 'string' ? body : JSON.stringify(body);
    }

    return request(method, url, {
      accept: accept,
      contentType: payload === null ? '' : 'application/json',
      body: payload
    }).then(function(response) {
      var contentType = response.contentType || '';
      var parsed = null;
      if (/json/i.test(contentType) && response.body) {
        try {
          parsed = JSON.parse(response.body);
        } catch (e) {
          parsed = null;
        }
      }
      var resultHeaders = {'content-type': contentType};
      var ok = response.status >= 200 && response.status < 300;
      if (!ok) {
        var err = new Error(
          response.status
            ? 'The ' + method + ' request on ' + url + ' failed'
            : 'The ' + method + ' request on ' + url + ' could not be sent'
        );
        err.status = response.status;
        err.response = {
          status: response.status,
          body: parsed,
          text: response.body
        };
        if (parsed && parsed.message) {
          err.message = parsed.message;
        }
        self.trigger('error', err);
        done(err, null, resultHeaders);
        throw err;
      }
      if (parsed && /application\/hal\+json/i.test(contentType)) {
        parsed = utils.solveHALEmbedded(parsed);
      }
      var result = parsed !== null ? parsed : response.body || '';
      done(null, result, resultHeaders);
      return result;
    });
  };

  function url(client, path) {
    return client.config.baseUrl + (path ? '/' + path : '');
  }

  function quiet(promise) {
    // the SDK reports errors through `done`; an unobserved rejection is noise
    promise.catch(function() {});
    return promise;
  }

  BrokerHttpClient.prototype.post = function(path, options) {
    options = options || {};
    if (options.fields || options.attachments) {
      var err = new Error('Multipart requests are not supported by forms.');
      (options.done || function() {})(err);
      return quiet(Promise.reject(err));
    }
    return quiet(
      this._send(
        'POST',
        withQuery(url(this, path), options.query),
        options,
        options.data || {}
      )
    );
  };

  BrokerHttpClient.prototype.get = function(path, options) {
    return this.load(url(this, path), options);
  };

  BrokerHttpClient.prototype.load = function(target, options) {
    options = options || {};
    if (isForeign(target)) {
      return this._direct.load(target, options);
    }
    return quiet(
      this._send('GET', withQuery(target, options.data), options, undefined)
    );
  };

  BrokerHttpClient.prototype.put = function(path, options) {
    options = options || {};
    return quiet(
      this._send('PUT', url(this, path), options, options.data || {})
    );
  };

  BrokerHttpClient.prototype.del = function(path, options) {
    options = options || {};
    return quiet(
      this._send('DELETE', url(this, path), options, options.data || {})
    );
  };

  BrokerHttpClient.prototype.options = function(path, options) {
    options = options || {};
    return quiet(this._send('OPTIONS', url(this, path), options, undefined));
  };

  return BrokerHttpClient;
}

/**
 * A replacement of the local storage for a frame without one (an opaque
 * origin has no storage). The forms SDK keeps the entered values of a form
 * under `camForm:{task or process definition id}`; that entry is kept by the
 * host, every other entry lives as long as the frame.
 *
 * @param {String}   formKey  the key of the entry of this form
 * @param {String}   stored   its value, as stored by the host
 * @param {Function} persist  called with the new value (null: removed)
 */
function createStorage(formKey, stored, persist) {
  var items = {};
  if (typeof stored === 'string') {
    items[formKey] = stored;
  }
  var storage = {
    getItem: function(key) {
      key = String(key);
      return Object.prototype.hasOwnProperty.call(items, key)
        ? items[key]
        : null;
    },
    setItem: function(key, value) {
      key = String(key);
      items[key] = String(value);
      if (key === formKey) {
        persist(items[key]);
      }
    },
    removeItem: function(key) {
      key = String(key);
      if (Object.prototype.hasOwnProperty.call(items, key)) {
        delete items[key];
        if (key === formKey) {
          persist(null);
        }
      }
    },
    clear: function() {
      var had = Object.prototype.hasOwnProperty.call(items, formKey);
      items = {};
      if (had) {
        persist(null);
      }
    },
    key: function(index) {
      var keys = Object.keys(items);
      return index >= 0 && index < keys.length ? keys[index] : null;
    }
  };
  Object.defineProperty(storage, 'length', {
    get: function() {
      return Object.keys(items).length;
    }
  });
  return storage;
}

/** @return {Boolean} whether the frame can use a local storage of its own */
function hasOwnStorage() {
  try {
    var storage = window.localStorage;
    return !!storage;
  } catch (e) {
    return false;
  }
}

/**
 * Installs the replacement of the local storage, if the frame has none.
 */
function installStorage(formKey, stored, persist) {
  if (hasOwnStorage()) {
    return false;
  }
  var storage = createStorage(formKey, stored, persist);
  try {
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      get: function() {
        return storage;
      }
    });
    return true;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('The entered values of this form cannot be stored', e);
    return false;
  }
}

/**
 * Downloads of files of variables (`cam-file-download` and links to
 * `.../variables/{name}/data`) are performed by the host: the frame has no
 * session to download them with.
 *
 * @param {Function} download  called with (url, filename)
 */
function interceptDownloads(download, isForeign) {
  document.addEventListener(
    'click',
    function(event) {
      var target = event.target;
      var link = target && target.closest ? target.closest('a[href]') : null;
      if (!link) {
        return;
      }
      var href = link.getAttribute('href');
      if (!href || isForeign(href)) {
        return;
      }
      var path;
      try {
        path = new URL(href, window.location.href).pathname;
      } catch (e) {
        return;
      }
      if (!/\/api\/engine\//.test(path)) {
        return;
      }
      event.preventDefault();
      if (!/\/(variables|localVariables)\/[^/]+\/data$/.test(path)) {
        // eslint-disable-next-line no-console
        console.error(
          'Links of isolated forms to the REST API are not followed: ' + href
        );
        return;
      }
      var filename =
        link.getAttribute('download') || (link.textContent || '').trim();
      download(href, filename);
    },
    true
  );
}

module.exports = {
  createBroker: createBroker,
  createStorage: createStorage,
  installStorage: installStorage,
  interceptDownloads: interceptDownloads,
  queryString: queryString
};
