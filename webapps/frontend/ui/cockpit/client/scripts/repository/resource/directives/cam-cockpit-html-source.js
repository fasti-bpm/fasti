/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* eslint-disable  no-unused-vars*/

'use strict';

var DOMPurify = require('dompurify');

var template = require('./cam-cockpit-html-source.html?raw');

/**
 * The preview renders the markup of a deployed resource inside of Cockpit.
 * A resource is arbitrary content provided by whoever is allowed to deploy,
 * so it must not be able to run scripts or to restyle and redirect the
 * application of the user who looks at it. Scripts, event handlers and
 * script URLs are removed by the sanitizer; in addition style sheets, inline
 * styles and form targets are dropped.
 */
var SANITIZER_CONFIG = {
  FORBID_TAGS: ['style'],
  FORBID_ATTR: ['style', 'action', 'formaction']
};

function sanitizePreview(source) {
  return DOMPurify.sanitize(
    typeof source === 'string' ? source : '',
    SANITIZER_CONFIG
  );
}

module.exports = [
  '$sce',
  'configuration',
  function($sce, configuration) {
    return {
      restrict: 'A',

      scope: {
        name: '=',
        source: '='
      },

      template: template,

      link: function($scope) {
        $scope.showHtml = configuration.getPreviewHtml();
        if ($scope.showHtml) {
          $scope.trustedHtml = $sce.trustAsHtml(sanitizePreview($scope.source));
        }
      }
    };
  }
];
