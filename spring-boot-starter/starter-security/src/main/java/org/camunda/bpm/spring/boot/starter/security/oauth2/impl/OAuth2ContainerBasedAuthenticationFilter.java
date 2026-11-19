/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;

/**
 * Also signs the user in to the web applications when the new web application
 * asks who is logged in ({@code GET /api/admin/auth/user/{engine}}): its pages
 * are not matched by the patterns of the classic applications, so this is the
 * first request that names the engine.
 */
public class OAuth2ContainerBasedAuthenticationFilter extends ContainerBasedAuthenticationFilter {

  public static final Pattern API_AUTH_USER_PATTERN = Pattern.compile("/api/admin/auth/user/([^/]+)");

  @Override
  protected String extractEngineName(String requestMethod, String requestUri) {
    String engineName = super.extractEngineName(requestMethod, requestUri);
    if (engineName == null && requestUri != null && "GET".equals(requestMethod)) {
      Matcher matcher = API_AUTH_USER_PATTERN.matcher(requestUri);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return engineName;
  }
}
