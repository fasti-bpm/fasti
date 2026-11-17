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
package org.camunda.bpm.oidc.web;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.oidc.OidcSettings;
import org.camunda.bpm.webapp.impl.security.auth.Authentication;
import org.camunda.bpm.webapp.impl.security.auth.AuthenticationUtil;
import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.camunda.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;

/**
 * Turns the {@link OidcLogin} of a session into the authentication of the web
 * applications for the engine a request addresses: user, groups and tenants
 * from the claims, the applications from the authorizations. The user does not
 * need to exist in the database of the engine.
 * <p>
 * Must be mapped after the {@code AuthenticationFilter} of the web
 * applications. That filter refreshes authentications from the database and
 * drops those of users it does not know; the authentication built here is
 * therefore marked as never expiring there and refreshed here instead (every
 * {@code authorization-refresh-seconds}, default 300), so that groups always
 * come from the claims.
 */
public class OidcAuthenticationFilter extends ContainerBasedAuthenticationFilter {

  /** Same as {@code AuthenticationUtil}: never refreshed by the authentication filter. */
  protected static final Date NEVER = new Date(Long.MAX_VALUE);

  protected static final Pattern API_AUTH_USER = Pattern.compile("/api/admin/auth/user/([^/]+)");
  protected static final Pattern NEXT_APP = Pattern.compile("/app/next/([^/]+)/.*");

  protected boolean enabled;
  protected long refreshMillis;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    init(OidcSettings.load(filterConfig.getInitParameter(OidcSettings.CONFIG_FILE_PARAM)));
  }

  public void init(OidcSettings settings) {
    this.enabled = settings.isLoginEnabled();
    this.refreshMillis = Math.max(0, settings.getLong("authorization-refresh-seconds", 300)) * 1000L;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!enabled) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    HttpSession session = req.getSession(false);
    OidcLogin login = OidcLogin.of(session);
    String engineName = login == null ? null : extractEngineName(req);
    ProcessEngine engine = engineName == null ? null : getAddressedEngine(engineName);
    if (engine == null) {
      chain.doFilter(request, response);
      return;
    }
    Authentications authentications = Authentications.getCurrent();
    if (authentications == null) {
      authentications = AuthenticationUtil.getAuthsFromSession(session);
    }
    boolean built = false;
    synchronized (session) {
      Authentication current = authentications.getAuthenticationForProcessEngine(engineName);
      Long builtAt = login.getBuiltAt().get(engine.getName());
      boolean stale = builtAt == null || System.currentTimeMillis() - builtAt > refreshMillis;
      if (current == null || !login.getUserId().equals(current.getIdentityId()) || stale) {
        authentications.addOrReplace(createAuthentication(engine, login));
        login.getBuiltAt().put(engine.getName(), System.currentTimeMillis());
        AuthenticationUtil.updateSession(session, authentications);
        built = true;
      }
    }
    if (!built) {
      chain.doFilter(request, response);
      return;
    }
    // the authentication filter has already set the engine identity of this
    // request, without the authentication built just now
    IdentityService identityService = engine.getIdentityService();
    identityService.setAuthentication(login.getUserId(), groups(engine, login), tenants(engine, login));
    try {
      chain.doFilter(request, response);
    } finally {
      identityService.clearAuthentication();
    }
  }

  @Override
  protected String extractEngineName(String requestMethod, String requestUri) {
    String engineName = super.extractEngineName(requestMethod, requestUri);
    if (engineName == null && requestUri != null) {
      Matcher matcher = API_AUTH_USER.matcher(requestUri);
      if (matcher.matches()) {
        return matcher.group(1);
      }
      matcher = NEXT_APP.matcher(requestUri);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return engineName;
  }

  protected UserAuthentication createAuthentication(ProcessEngine engine, OidcLogin login) {
    String userId = login.getUserId();
    List<String> groups = groups(engine, login);
    List<String> tenants = tenants(engine, login);
    IdentityService identityService = engine.getIdentityService();
    // checked without the identity of the request, as AuthenticationUtil does
    identityService.clearAuthentication();
    Set<String> apps = new LinkedHashSet<>();
    apps.add(AuthenticationUtil.APP_WELCOME);
    AuthorizationService authorizationService = engine.getAuthorizationService();
    for (String app : AuthenticationUtil.APPS) {
      if (!engine.getProcessEngineConfiguration().isAuthorizationEnabled()
          || authorizationService.isUserAuthorized(userId, groups, Permissions.ACCESS, Resources.APPLICATION, app)) {
        apps.add(app);
      }
    }
    UserAuthentication authentication = new UserAuthentication(userId, engine.getName());
    authentication.setGroupIds(groups);
    authentication.setTenantIds(tenants);
    authentication.setAuthorizedApps(apps);
    authentication.setCacheValidationTime(NEVER);
    return authentication;
  }

  /** Groups from the claims or, if they are not mapped, those of the database. */
  protected List<String> groups(ProcessEngine engine, OidcLogin login) {
    return login.getGroups() != null ? login.getGroups() : AuthenticationUtil.getGroupsOfUser(engine, login.getUserId());
  }

  protected List<String> tenants(ProcessEngine engine, OidcLogin login) {
    return login.getTenants() != null ? login.getTenants() : AuthenticationUtil.getTenantsOfUser(engine, login.getUserId());
  }
}
