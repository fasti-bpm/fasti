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
package org.camunda.bpm.oidc.rest;

import java.io.IOException;
import java.util.logging.Logger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.engine.rest.security.auth.RestApiAuthenticationCheck;
import org.camunda.bpm.oidc.OidcSettings;

/**
 * The authentication filter of the REST API in a servlet container: accepts
 * bearer tokens (and basic authentication unless switched off) when
 * {@code fasti.security.oidc.rest.bearer-enabled=true} is set in the single
 * sign-on configuration, and does nothing otherwise, so that it can be part of
 * the {@code web.xml} of the distribution.
 * <p>
 * Unlike the plain engine filter, the list of engines ({@code /engine}) also
 * requires credentials.
 * <p>
 * It can be followed by the HTTP basic authentication filter of the engine,
 * which the distributions enable by default: requests that this filter has
 * authenticated pass that filter (see
 * {@link ProcessEngineAuthenticationFilter#AUTHENTICATED_REQUEST_ATTRIBUTE}).
 * <p>
 * Init-params: {@code config-file} (path of the configuration, optional) and
 * {@code rest-url-pattern-prefix} as for the engine filter.
 */
public class OidcRestAuthenticationFilter extends ProcessEngineAuthenticationFilter {

  private static final Logger LOG = Logger.getLogger(OidcRestAuthenticationFilter.class.getName());

  protected boolean enabled;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    OidcSettings settings = OidcSettings.load(filterConfig.getInitParameter(OidcSettings.CONFIG_FILE_PARAM));
    init(settings, filterConfig.getInitParameter(SERVLET_PATH_PREFIX));
    if (!enabled) {
      warnIfNoOtherAuthentication(filterConfig);
    }
  }

  /**
   * The check of the REST API for a missing authentication filter counts this
   * filter even when it does nothing; warn here in that case.
   */
  protected void warnIfNoOtherAuthentication(FilterConfig filterConfig) {
    ServletContext context = filterConfig.getServletContext();
    if (!RestApiAuthenticationCheck.isWarningEnabled(context)) {
      return;
    }
    try {
      for (FilterRegistration registration : context.getFilterRegistrations().values()) {
        String className = registration.getClassName();
        if (filterConfig.getFilterName().equals(registration.getName()) || className == null) {
          continue;
        }
        if (className.startsWith("org.springframework.security.")) {
          return;
        }
        Class<?> type = Class.forName(className, false, context.getClassLoader());
        if (ProcessEngineAuthenticationFilter.class.isAssignableFrom(type)
            && !OidcRestAuthenticationFilter.class.isAssignableFrom(type)) {
          return;
        }
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return;
    }
    LOG.warning("The REST API (context path '" + context.getContextPath() + "') is deployed WITHOUT AUTHENTICATION: "
        + "bearer tokens are not configured (" + OidcSettings.PREFIX + "rest.bearer-enabled) and no other "
        + "authentication filter is registered. Every client that can reach the REST API has unrestricted access. "
        + "Switch this warning off with the context parameter '" + RestApiAuthenticationCheck.WARNING_ENABLED_PARAM
        + "' or the system property '" + RestApiAuthenticationCheck.WARNING_ENABLED_SYSTEM_PROPERTY + "' set to false.");
  }

  public void init(OidcSettings settings, String servletPathPrefix) {
    this.enabled = settings.isRestBearerEnabled();
    this.servletPathPrefix = servletPathPrefix;
    if (enabled) {
      OidcBearerAuthenticationProvider provider = new OidcBearerAuthenticationProvider(settings);
      this.authenticationProvider = provider;
      LOG.info("REST API: bearer tokens required" + (provider.isBasicEnabled() ? " (or basic authentication)" : ""));
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!enabled || request.getAttribute(AUTHENTICATED_REQUEST_ATTRIBUTE) != null) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    String servletPath = servletPathPrefix != null ? servletPathPrefix : req.getServletPath();
    String requestUrl = removePathParameters(req.getRequestURI().substring(req.getContextPath().length() + servletPath.length()));
    // the list of engines, and engines that do not exist: credentials are checked against
    // the default engine first, so that nobody learns without them which engines exist
    if (!requiresEngineAuthentication(requestUrl) || lookup(extractEngineName(requestUrl)) == null) {
      ProcessEngine engine = lookup(DEFAULT_ENGINE_NAME);
      boolean authenticated = OidcBearerAuthenticationProvider.hasBearerToken(req)
          ? authenticationProvider.extractAuthenticatedUser(req, engine).isAuthenticated()
          : engine != null && authenticationProvider.extractAuthenticatedUser(req, engine).isAuthenticated();
      if (!authenticated) {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        authenticationProvider.augmentResponseByAuthenticationChallenge(resp, engine);
        return;
      }
      if (requiresEngineAuthentication(requestUrl)) {
        // authenticated: the engine filter answers that the engine does not exist
        super.doFilter(request, response, chain);
      } else {
        chain.doFilter(request, response);
      }
      return;
    }
    super.doFilter(request, response, chain);
  }

  protected ProcessEngine lookup(String engineName) {
    try {
      return getAddressedEngine(engineName);
    } catch (RuntimeException e) {
      return null;
    }
  }

  public boolean isEnabled() {
    return enabled;
  }
}
