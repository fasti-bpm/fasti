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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Sets the engine identity (user, groups, tenants) of a REST request that the
 * resource server authenticated with a bearer token, on the engine the request
 * addresses, and clears it afterwards.
 * <p>
 * This makes bearer authentication independent of whether a
 * {@code ProcessEngineAuthenticationFilter} is registered for the REST API
 * (it is not in a starter-only application, nor in Run unless
 * {@code camunda.bpm.run.auth.enabled=true}).
 * <p>
 * The engine filter lets the list of engines ({@code /engine}) pass without
 * checking credentials; in bearer mode basic credentials sent there are
 * checked here, so the list is never served for invalid credentials.
 */
public class RestBearerIdentityFilter extends OncePerRequestFilter {

  /** Same addressing as the engine authentication filter: {@code /engine/{name}/...}. */
  protected static final Pattern ENGINE_PATH = Pattern.compile("^/engine/([^/]+)(/|$)");
  protected static final Pattern ENGINE_LIST_PATH = Pattern.compile("^/engine/?$");
  protected static final Pattern PATH_PARAMETERS = Pattern.compile(";[^/]*");
  protected static final String BASIC_PREFIX = "Basic ";
  /** Decodes the path and removes ";" path parameters, like the servlet container does for routing. */
  protected static final UrlPathHelper PATH_HELPER = new UrlPathHelper() {
    // always UTF-8, like JAX-RS: the request's own character encoding must not change the path
    @Override
    protected String determineEncoding(jakarta.servlet.http.HttpServletRequest request) {
      return StandardCharsets.UTF_8.name();
    }
  };

  /** Looks up an engine by name; {@code null} stands for the default engine. */
  protected final Function<String, ProcessEngine> engines;
  /** The path of the REST API below the context path, e.g. {@code /engine-rest}. */
  protected final String restPath;

  public RestBearerIdentityFilter(Function<String, ProcessEngine> engines, String restPath) {
    this.engines = engines;
    String path = restPath == null ? "" : restPath;
    this.restPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = relativePath(request);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
      if (ENGINE_LIST_PATH.matcher(path).matches() && !validBasicCredentials(request)) {
        response.addHeader("WWW-Authenticate", "Basic realm=\"default\"");
        unauthorized(response);
        return;
      }
      // basic credentials: checked by the engine authentication filter
      chain.doFilter(request, response);
      return;
    }
    AuthenticationResult result = BearerTokenAuthenticationProvider.fromJwt(jwt);
    if (!result.isAuthenticated()) {
      unauthorized(response);
      return;
    }
    if (ENGINE_LIST_PATH.matcher(path).matches()) {
      chain.doFilter(request, response);
      return;
    }
    Matcher matcher = ENGINE_PATH.matcher(path);
    // the path is already decoded: decoding again would let %2565 name a different engine
    String engineName = matcher.find() ? matcher.group(1) : null;
    ProcessEngine engine = lookup(engineName);
    if (engine == null) {
      // an unknown engine: the REST API answers that no such engine exists
      chain.doFilter(request, response);
      return;
    }
    IdentityService identityService = engine.getIdentityService();
    String userId = result.getAuthenticatedUser();
    List<String> groups = result.getGroups();
    if (groups == null) {
      groups = identityService.createGroupQuery().groupMember(userId).list().stream().map(Group::getId).toList();
    }
    List<String> tenants = result.getTenants();
    if (tenants == null) {
      tenants = identityService.createTenantQuery().userMember(userId).includingGroupsOfUser(true)
          .list().stream().map(Tenant::getId).toList();
    }
    identityService.setAuthentication(userId, groups, tenants);
    try {
      chain.doFilter(request, response);
    } finally {
      identityService.clearAuthentication();
    }
  }

  protected ProcessEngine lookup(String engineName) {
    try {
      return engines.apply(engineName);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * The path of the request below the REST API, decoded and without path
   * parameters: the same path JAX-RS routes on. Matching on the raw URI would
   * let {@code /%65ngine/other/...} set the identity on the default engine
   * while the request is served by engine {@code other}.
   */
  protected String relativePath(HttpServletRequest request) {
    String path = PATH_HELPER.getPathWithinApplication(request);
    if (!restPath.isEmpty() && (path.equals(restPath) || path.startsWith(restPath + "/"))) {
      path = path.substring(restPath.length());
    }
    return PATH_PARAMETERS.matcher(path).replaceAll("");
  }

  /** Whether the request carries basic credentials of a user of the default engine. */
  protected boolean validBasicCredentials(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
      return false;
    }
    String decoded;
    try {
      decoded = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return false;
    }
    int colon = decoded.indexOf(':');
    if (colon <= 0) {
      return false;
    }
    ProcessEngine engine = lookup(null);
    return engine != null
        && engine.getIdentityService().checkPassword(decoded.substring(0, colon), decoded.substring(colon + 1));
  }

  protected void unauthorized(HttpServletResponse response) throws IOException {
    response.addHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }
}
