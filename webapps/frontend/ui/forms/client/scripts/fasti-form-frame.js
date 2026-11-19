/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */

'use strict';

/**
 * The form frame renders one embedded HTML form (form key `embedded:...`)
 * for the new web UI. Embedded forms contain HTML and scripts of whoever
 * deployed the process and rely on AngularJS, `eval` and inline scripts.
 * The new web UI forbids those; it therefore shows the form in a frame that
 * is served with the policy of the classic applications.
 *
 * The frame uses the embedded forms SDK the classic Tasklist uses (CamForm
 * with AngularJS), so forms behave as they did there: cam-variable-name and
 * cam-variable-type, cam-script with the camForm API and `$scope` and
 * `inject`, cam-business-key, cam-file-download, file uploads, error and
 * escalation buttons and the local storage of entered values.
 *
 * The frame talks to its parent window only, by postMessage, with the
 * messages defined in ./protocol.js. Messages are accepted from the parent
 * window of the host origin only and sent to that origin only. The host
 * origin is the origin of this page, unless the frame is served from a
 * separate forms origin: then the host passes its origin in the fragment
 * (`#host=...`).
 *
 * In the isolated modes (transport `broker`) the frame has no session: it
 * is sandboxed with an opaque origin or served from another origin. All
 * requests of the SDK and of `$http` are then performed by the host, which
 * only allows those of the task or start event of the form
 * (./broker-client.js).
 */

require('../styles/form-frame.less');

var $ = (window.$ = window.jQuery = require('jquery'));
var angular = require('camunda-commons-ui/vendor/angular');
var CamSDK = require('camunda-bpm-sdk-js/lib/angularjs/index');
require('angular-ui-bootstrap');
require('camunda-commons-ui/lib/util/index');
var protocol = require('./protocol');
var brokerClient = require('./broker-client');
var amd = require('exports-loader?exports=requirejs,define,require!requirejs/require');

var ORIGIN = window.location.origin;

/** the origin of the host window, see above */
var HOST_ORIGIN = (function() {
  var match = /(?:^#|&)host=([^&]+)/.exec(window.location.hash);
  if (!match) {
    return ORIGIN;
  }
  try {
    var origin = decodeURIComponent(match[1]);
    var url = new URL(origin);
    if (
      (url.protocol === 'https:' || url.protocol === 'http:') &&
      url.origin === origin
    ) {
      return origin;
    }
  } catch (e) {
    // falls back to the origin of this page
  }
  return ORIGIN;
})();
var base = document.querySelector('base');
var appRoot = base.getAttribute('app-root');
var engineApi = base.getAttribute('engine-api');
var engine = (function() {
  var match = /\/app\/forms\/([^/]+)\//.exec(window.location.pathname);
  return match ? decodeURIComponent(match[1]) : 'default';
})();

var EMBEDDED_KEY = 'embedded:';
var APP_KEY = 'app:';
var DEPLOYMENT_KEY = 'deployment:';
var ENGINE_KEY = 'engine:';

function post(type, payload) {
  var message = angular.extend({}, payload || {}, {
    source: protocol.FRAME,
    type: type
  });
  window.parent.postMessage(message, HOST_ORIGIN);
}

/** the requests of the frame are performed by the host (transport `broker`) */
var broker = null;

function describe(error) {
  if (!error) {
    return 'Unknown error';
  }
  if (error.response && error.response.body && error.response.body.message) {
    return String(error.response.body.message);
  }
  return String(error.message || error);
}

function readCookie(name) {
  var prefix = name + '=';
  var parts = document.cookie.split(';');
  for (var i = 0; i < parts.length; i++) {
    var cookie = parts[i].trim();
    if (cookie.indexOf(prefix) === 0) {
      return decodeURIComponent(cookie.substring(prefix.length));
    }
  }
  return null;
}

function engineUrl(path) {
  return engineApi + 'engine/' + encodeURIComponent(engine) + path;
}

function getJson(url) {
  if (broker) {
    return broker.request('GET', url).then(function(response) {
      var body = {};
      try {
        body = response.body ? JSON.parse(response.body) : {};
      } catch (e) {
        body = {};
      }
      if (response.status === 401) {
        throw new Error('Unauthorized');
      }
      if (response.status < 200 || response.status >= 300) {
        throw new Error(body.message || 'Request failed: ' + response.status);
      }
      return body;
    });
  }
  return fetch(url, {
    credentials: 'same-origin',
    headers: {Accept: 'application/json'}
  }).then(function(response) {
    if (response.status === 401) {
      post('unauthorized');
      throw new Error('Unauthorized');
    }
    if (!response.ok) {
      return response
        .json()
        .catch(function() {
          return {};
        })
        .then(function(body) {
          throw new Error(body.message || 'Request failed: ' + response.status);
        });
    }
    return response.json();
  });
}

/**
 * Determines the URL of the form the same way the classic Tasklist does.
 * Only embedded forms are rendered here.
 */
function resolveFormUrl(target, info) {
  var key = info.key;
  if (!key || key.indexOf(EMBEDDED_KEY) !== 0) {
    throw new Error('The form is not an embedded form');
  }
  key = key.substring(EMBEDDED_KEY.length);

  if (key.indexOf(APP_KEY) === 0) {
    if (!info.contextPath) {
      throw new Error('The process application has no context path');
    }
    return (info.contextPath + '/' + key.substring(APP_KEY.length)).replace(
      /\/([/]+)/,
      '/'
    );
  }
  if (key.indexOf(DEPLOYMENT_KEY) === 0) {
    return target.type === 'task'
      ? engineUrl(
          '/task/' + encodeURIComponent(target.taskId) + '/deployed-form'
        )
      : engineUrl(
          '/process-definition/' +
            encodeURIComponent(target.processDefinitionId) +
            '/deployed-start-form'
        );
  }
  if (key.indexOf(ENGINE_KEY) === 0) {
    return key
      .replace('engine://', engineApi)
      .replace(':engine', encodeURIComponent(engine));
  }
  return key;
}

/**
 * The HTTP client of the SDK, with the CSRF token of the session and with
 * callbacks inside an AngularJS digest, like the client of the classic
 * applications.
 */
function createHttpClient($rootScope, csrfCookieName) {
  function FrameHttpClient(config) {
    this._wrapped = broker
      ? new broker.HttpClient(config)
      : new CamSDK.Client.HttpClient(config);
  }

  ['post', 'get', 'load', 'put', 'del', 'options', 'head'].forEach(function(
    name
  ) {
    FrameHttpClient.prototype[name] = function(path, options) {
      var wrapped = this._wrapped;
      options = options || {};
      // the token must not reach another origin, e.g. a form URL of the form
      // key; with the broker, the host adds the token of its session
      if (!broker && (name !== 'load' || !wrapped.isForeignOrigin(path))) {
        var token = readCookie(csrfCookieName);
        if (token) {
          options.headers = options.headers || {};
          options.headers['X-XSRF-TOKEN'] = token;
        }
      }
      var original = angular.isFunction(options.done)
        ? options.done
        : angular.noop;
      options.done = function(err, result, headers) {
        function apply() {
          if (err && err.status === 401) {
            post('unauthorized');
          }
          original(err, result, headers);
        }
        var phase = $rootScope.$$phase;
        if (phase !== '$apply' && phase !== '$digest') {
          $rootScope.$apply(apply);
        } else {
          apply();
        }
      };
      return wrapped[name](path, options);
    };
  });

  ['on', 'once', 'off', 'trigger'].forEach(function(name) {
    FrameHttpClient.prototype[name] = function() {
      this._wrapped[name].apply(this._wrapped, arguments);
    };
  });

  return FrameHttpClient;
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
}

// messages of the host ////////////////////////////////////////////////////

var initMessage = null;
var handlers = {};

window.addEventListener('message', function(event) {
  if (event.source !== window.parent || event.origin !== HOST_ORIGIN) {
    return;
  }
  var message = protocol.parseHostMessage(event.data);
  if (!message) {
    return;
  }
  if (message.type === 'response') {
    if (broker) {
      broker.receive(message);
    }
    return;
  }
  if (message.type === 'theme') {
    applyTheme(message.theme);
    return;
  }
  if (message.type === 'init') {
    if (initMessage) {
      // a frame renders exactly one form
      return;
    }
    initMessage = message;
    applyTheme(message.theme);
    if (message.transport === 'broker') {
      startBroker(message);
    }
    loadCustomizations().then(function(modules) {
      bootstrap(message, modules);
    });
    return;
  }
  var handler = handlers[message.type];
  if (handler) {
    handler(message);
  }
});

// the broker //////////////////////////////////////////////////////////////

function startBroker(init) {
  broker = brokerClient.createBroker(post, function() {
    post('unauthorized');
  });
  var target = init.target;
  var formKey =
    'camForm:' +
    (target.type === 'task' ? target.taskId : target.processDefinitionId);
  brokerClient.installStorage(formKey, init.storedValues, function(value) {
    post('storage', {value: value});
  });
  brokerClient.interceptDownloads(function(url, filename) {
    post('download', {url: broker.hostUrl(url), filename: filename});
  }, broker.isForeign);
}

/**
 * Loads a module of the classic Tasklist (its configuration and custom
 * scripts). Without a session of its own and, in the sandboxed mode, with an
 * opaque origin, the frame cannot import them from the server (module
 * requests need CORS); the host fetches the source and the frame imports it
 * from a blob URL.
 */
function importModule(url) {
  if (!broker) {
    // eslint-disable-next-line
    return _import(url);
  }
  return broker
    .request('GET', url, {accept: 'text/javascript, */*'})
    .then(function(response) {
      if (response.status < 200 || response.status >= 300) {
        throw new Error('Request failed: ' + response.status);
      }
      var blobUrl = URL.createObjectURL(
        new Blob([response.body], {type: 'text/javascript'})
      );
      // eslint-disable-next-line
      return _import(blobUrl).then(
        function(module) {
          URL.revokeObjectURL(blobUrl);
          return module;
        },
        function(error) {
          URL.revokeObjectURL(blobUrl);
          throw error;
        }
      );
    });
}

// customizations of the classic Tasklist //////////////////////////////////

/**
 * Embedded forms may rely on what an installation added to the classic
 * Tasklist through app/tasklist/scripts/config.js: `customScripts` (modules
 * loaded for their side effects) and `requireJsConfig` (AMD modules and the
 * AngularJS modules they define, made available as `cam.tasklist.custom`).
 * The frame loads both the way the classic Tasklist does. Resolves to the
 * AngularJS modules the form application depends on; a configuration that
 * fails to load is reported on the console and otherwise ignored.
 */
function loadCustomizations() {
  var tasklistBase = appRoot + '/app/tasklist/';
  return importModule(
    tasklistBase + 'scripts/config.js?bust=' + new Date().getTime()
  )
    .then(function(module) {
      return (module && module.default) || {};
    })
    .catch(function(error) {
      // eslint-disable-next-line no-console
      console.error('The Tasklist configuration could not be loaded', error);
      return {};
    })
    .then(function(config) {
      var scripts = Array.isArray(config.customScripts)
        ? config.customScripts
        : [];
      return Promise.all(
        scripts.map(function(url) {
          var file = /\.js$/.test(url) ? url : url + '.js';
          return importModule(tasklistBase + file).catch(function(error) {
            // eslint-disable-next-line no-console
            console.error('A custom script could not be loaded: ' + url, error);
          });
        })
      ).then(function() {
        return config;
      });
    })
    .then(function(config) {
      if (config.csrfCookieName) {
        csrfCookieOverride = config.csrfCookieName;
      }
      var custom = config.requireJsConfig;
      if (!custom) {
        return [];
      }
      return loadAmdModules(custom, tasklistBase);
    });
}

function loadAmdModules(custom, tasklistBase) {
  var requirejs = amd.requirejs;
  // the relevant part of the RequireJS configuration, as in the classic Tasklist
  var conf = {baseUrl: tasklistBase};
  [
    'baseUrl',
    'paths',
    'bundles',
    'shim',
    'map',
    'config',
    'packages',
    'waitSeconds',
    'enforceDefine',
    'urlArgs',
    'scriptType'
  ].forEach(function(prop) {
    if (custom[prop]) {
      conf[prop] = custom[prop];
    }
  });
  // the libraries a custom AMD module may depend on
  amd.define('angular', [], function() {
    return angular;
  });
  amd.define('jquery', [], function() {
    return $;
  });
  amd.define('camunda-bpm-sdk-js', [], function() {
    return CamSDK;
  });
  window.define = amd.define;
  window.require = amd.require;
  requirejs.config(conf);
  return new Promise(function(resolve) {
    function done() {
      // AMD globals would make later third-party scripts register as modules
      window.define = undefined;
      window.require = undefined;
      var ngDeps = Array.isArray(custom.ngDeps) ? custom.ngDeps : [];
      angular.module('cam.tasklist.custom', ngDeps);
      resolve(['cam.tasklist.custom']);
    }
    requirejs(custom.deps || [], done, function(error) {
      // eslint-disable-next-line no-console
      console.error('The custom RequireJS modules could not be loaded', error);
      window.define = undefined;
      window.require = undefined;
      angular.module('cam.tasklist.custom', []);
      resolve(['cam.tasklist.custom']);
    });
  });
}

/** the CSRF cookie name the Tasklist configuration sets, if any */
var csrfCookieOverride = null;

// the application /////////////////////////////////////////////////////////

function bootstrap(init, customModules) {
  var ngModule = angular.module(
    'fasti.formFrame',
    ['cam.embedded.forms', 'cam.commons.util', 'ui.bootstrap'].concat(
      customModules || []
    )
  );
  var csrfCookieName = csrfCookieOverride || init.csrfCookieName;

  ngModule.config([
    '$provide',
    function($provide) {
      if (!broker) {
        return;
      }
      // `$http` of form scripts: requests to this origin go through the host
      $provide.decorator('$httpBackend', [
        '$delegate',
        function($delegate) {
          return function(method, url, data, callback, headers) {
            if (broker.isForeign(url)) {
              return $delegate.apply(this, arguments);
            }
            var accept = null;
            var contentType = null;
            angular.forEach(headers || {}, function(value, name) {
              var lower = name.toLowerCase();
              if (lower === 'accept') {
                accept = value;
              } else if (lower === 'content-type') {
                contentType = value;
              }
            });
            broker
              .request(method, url, {
                accept: accept,
                contentType:
                  data === undefined || data === null ? '' : contentType,
                body: data === undefined || data === null ? null : String(data)
              })
              .then(function(response) {
                callback(
                  response.status || -1,
                  response.body,
                  response.contentType
                    ? 'Content-Type: ' + response.contentType
                    : '',
                  '',
                  response.status ? 'complete' : 'error'
                );
              });
          };
        }
      ]);
    }
  ]);

  ngModule.config([
    '$httpProvider',
    'UriProvider',
    function($httpProvider, UriProvider) {
      // scripts of forms may use $http; it sends the token to the origin only
      $httpProvider.defaults.xsrfCookieName = csrfCookieName;
      $httpProvider.defaults.headers.get = {'X-Authorized-Engine': engine};

      UriProvider.replace(':appRoot', appRoot);
      UriProvider.replace(':appName', 'tasklist');
      UriProvider.replace('app://', appRoot + '/app/tasklist/' + engine + '/');
      UriProvider.replace('tasklistbase://', appRoot + '/app/tasklist/');
      UriProvider.replace('engine://', engineApi);
      UriProvider.replace(':engine', engine);
    }
  ]);

  ngModule.controller('fastiFormFrameCtrl', [
    '$scope',
    '$element',
    '$rootScope',
    function($scope, $element, $rootScope) {
      var container = $($element[0]).find('.form-container');
      var target = init.target;
      var HttpClient = createHttpClient($rootScope, csrfCookieName);
      var client = new CamSDK.Client({
        apiUri: appRoot + '/api/engine',
        engine: engine,
        HttpClient: HttpClient
      });
      var camForm = null;
      var ngForm = null;
      var initialVariables = null;
      var busy = false;

      $scope.disabled = init.disabled;

      function fail(error) {
        post('error', {message: describe(error)});
      }

      function reportState() {
        post('state', {
          valid: !ngForm || !!ngForm.$valid,
          dirty: !!(ngForm && ngForm.$dirty)
        });
      }

      function clearVariableManager() {
        var variables = camForm.variableManager.variables;
        for (var name in variables) {
          camForm.variableManager.destroyVariable(name);
        }
        camForm.variableManager.variables = angular.copy(initialVariables);
      }

      function loaded(err, form) {
        if (err) {
          return fail(err);
        }
        camForm = form;
        initialVariables = angular.copy(camForm.variableManager.variables);

        var formName = camForm.formElement.attr('name');
        var formScope = camForm.formElement.scope();
        ngForm = formScope ? formScope[formName] : null;
        if (ngForm) {
          ngForm.$setPristine();
          $scope.$watch(function() {
            return ngForm.$valid + ':' + ngForm.$dirty;
          }, reportState);
        }

        ['error-success', 'escalation-success'].forEach(function(name) {
          camForm.on(name, function() {
            post('submitted', {});
          });
        });
        ['error-failed', 'escalation-failed'].forEach(function(name) {
          camForm.on(name, function(error) {
            post('failed', {message: describe(error)});
          });
        });

        post('loaded');
        reportState();
      }

      handlers.disabled = function(message) {
        $scope.$apply(function() {
          $scope.disabled = message.disabled;
        });
      };

      handlers.store = function() {
        if (camForm) {
          try {
            camForm.store();
            if (ngForm) {
              $scope.$apply(function() {
                ngForm.$setPristine();
              });
            }
          } catch (error) {
            fail(error);
          }
        }
      };

      handlers.submit = function() {
        if (busy) {
          return;
        }
        if (!camForm || $scope.disabled) {
          post('failed', {prevented: true, message: 'The form is not ready'});
          return;
        }
        busy = true;
        camForm.submit(function(err, result) {
          busy = false;
          if (err) {
            clearVariableManager();
            post('failed', {
              prevented: err.message === 'camForm submission prevented',
              message: describe(err)
            });
            return;
          }
          var instance =
            result && typeof result.id === 'string'
              ? {
                  id: result.id,
                  definitionId: result.definitionId || null,
                  businessKey: result.businessKey || null
                }
              : null;
          post('submitted', {processInstance: instance});
        });
      };

      var infoUrl =
        target.type === 'task'
          ? engineUrl('/task/' + encodeURIComponent(target.taskId) + '/form')
          : engineUrl(
              '/process-definition/' +
                encodeURIComponent(target.processDefinitionId) +
                '/startForm'
            );

      Promise.all([
        getJson(infoUrl),
        getJson(appRoot + '/api/admin/auth/user/' + encodeURIComponent(engine))
      ])
        .then(function(results) {
          var formUrl = resolveFormUrl(target, results[0]);
          var urlParams = {
            userId: results[1].userId,
            engineName: engine
          };
          var options = {
            client: client,
            containerElement: container,
            formUrl: formUrl,
            urlParams: urlParams,
            done: loaded
          };
          if (target.type === 'task') {
            options.taskId = target.taskId;
            urlParams.taskId = target.taskId;
          } else {
            options.processDefinitionId = target.processDefinitionId;
          }
          $scope.$apply(function() {
            new CamSDK.Form(options);
          });
        })
        .catch(fail);
    }
  ]);

  // the size of the form, so that the host can size the frame
  var lastHeight = -1;
  function reportSize() {
    var frame = document.querySelector('.form-frame');
    var height = frame ? Math.ceil(frame.getBoundingClientRect().height) : 0;
    // content that overflows the form, e.g. an absolutely positioned popup
    height = Math.max(height, document.body.scrollHeight);
    // open popups, e.g. a date picker below the last field: the frame grows
    // while they are open, so that they are not clipped, and shrinks after
    var popups = document.querySelectorAll(
      '.dropdown-menu, .uib-datepicker-popup, .datepicker, .popover, [role="listbox"]'
    );
    for (var i = 0; i < popups.length; i++) {
      var rect = popups[i].getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        height = Math.max(
          height,
          Math.ceil(rect.bottom + window.pageYOffset) + 8
        );
      }
    }
    if (height !== lastHeight) {
      lastHeight = height;
      post('resize', {height: height});
    }
  }
  var scheduled = false;
  function scheduleReport() {
    if (!scheduled) {
      scheduled = true;
      window.requestAnimationFrame(function() {
        scheduled = false;
        reportSize();
      });
    }
  }
  if (typeof ResizeObserver === 'function') {
    new ResizeObserver(scheduleReport).observe(document.body);
  }
  // a popup that opens changes the markup but not the size of the body
  if (typeof MutationObserver === 'function') {
    new MutationObserver(scheduleReport).observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'style']
    });
  }
  window.addEventListener('load', reportSize);

  // without strictDi: scripts of forms may inject services by parameter name
  angular.bootstrap(document.body, ['fasti.formFrame']);
}

if (window.parent !== window) {
  post('ready');
}
