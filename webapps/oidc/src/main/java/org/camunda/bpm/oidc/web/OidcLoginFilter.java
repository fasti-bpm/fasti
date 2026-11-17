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
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.nimbusds.openid.connect.sdk.claims.LogoutTokenClaimsSet;
import org.camunda.bpm.oidc.ClaimRules;
import org.camunda.bpm.oidc.OidcSettings;
import org.camunda.bpm.oidc.web.OidcClient.LoginResult;
import org.camunda.bpm.oidc.web.OidcClient.OidcException;
import org.camunda.bpm.oidc.web.OidcClient.PendingLogin;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;

/**
 * Single sign-on for the web applications in a servlet container. Answers
 * below {@code {app}/sso/}:
 * <ul>
 * <li>{@code GET config}: tells the login page of the new web application to
 * offer the identity provider;</li>
 * <li>{@code GET login?return=...}: starts the authorization code flow (state,
 * nonce, PKCE S256); the page to return to must be one of the new web
 * application on this server;</li>
 * <li>{@code GET callback}: completes it, replaces the session id and stores
 * the {@link OidcLogin};</li>
 * <li>{@code POST logout}: same-origin only; ends the session and continues at
 * the logout endpoint of the provider;</li>
 * <li>{@code POST backchannel-logout}: ends the sessions of a logout token.</li>
 * </ul>
 * Password logins of the web applications are refused, the initial setup is
 * off, and a classic application opened without a session starts the login.
 * Without a configuration the filter does nothing.
 * <p>
 * Must be mapped to {@code /*} before the authentication, security and CSRF
 * filters of the web applications; {@link OidcAuthenticationFilter} turns
 * the login into the authentication of the web applications.
 */
public class OidcLoginFilter implements Filter {

  private static final Logger LOG = Logger.getLogger(OidcLoginFilter.class.getName());

  protected static final String PENDING_ATTRIBUTE = OidcLoginFilter.class.getName() + ".PENDING";
  protected static final int MAX_PENDING = 10;
  protected static final long PENDING_TTL_MILLIS = 10 * 60 * 1000L;

  protected static final Pattern PASSWORD_LOGIN = Pattern.compile("/api/admin/auth/user/[^/]+/login(/.*)?");
  protected static final Pattern CLASSIC_APP = Pattern.compile("/app/(cockpit|admin|tasklist|welcome)/[^/]+/.*");
  protected static final Pattern CLASSIC_LOGOUT = Pattern.compile("/api/admin/auth/user/[^/]+/logout/?");
  protected static final Pattern PATH_PARAMETERS = Pattern.compile(";[^/]*");

  protected OidcSettings settings;
  protected OidcClient client;
  protected ClaimRules claimRules;
  protected boolean enabled;
  protected String label;
  protected String userNameClaim;
  protected boolean providerLogout;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    init(OidcSettings.load(filterConfig.getInitParameter(OidcSettings.CONFIG_FILE_PARAM)), null);
    if (enabled) {
      // administrators come from the claims; the setup page cannot create them
      ServletContextUtil.setInitialSetupEnabled(false, filterConfig.getServletContext());
    }
  }

  /** Initializes the filter with the given settings and, for tests, client. */
  public void init(OidcSettings settings, OidcClient client) {
    this.settings = settings;
    this.enabled = settings.isLoginEnabled();
    if (!enabled) {
      return;
    }
    this.client = client != null ? client : new OidcClient(settings);
    this.claimRules = settings.claimRules();
    this.label = settings.get("login-label", "");
    this.userNameClaim = settings.get("user-name-claim", "sub");
    this.providerLogout = settings.getBoolean("logout.provider-logout", true);
    LOG.info("Single sign-on of the web applications with " + settings.getIssuerUri());
  }

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!enabled) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    String path = relativePath(req);
    // the rules are checked against both the path as sent and the path the container
    // resolved it to (decoded, without path parameters), so that e.g.
    // /api;x/admin/... cannot slip past them
    String resolved = resolvedPath(req);
    if (path != null && path.startsWith("/sso/")) {
      handleEndpoint(path.substring("/sso/".length()), req, resp);
      return;
    }
    ServletContextUtil.setInitialSetupEnabled(false, req.getServletContext());
    if (matches(PASSWORD_LOGIN, path, resolved)) {
      // users sign in at the identity provider only
      resp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    if ("GET".equals(req.getMethod()) && matches(CLASSIC_APP, path, resolved)
        && OidcLogin.of(req.getSession(false)) == null) {
      String returnUrl = req.getRequestURI();
      startLogin(req, resp, SsoRequests.isAllowedReturnUrl(appRoot(req) + "/app/", returnUrl) ? returnUrl : null);
      return;
    }
    if ("POST".equals(req.getMethod()) && matches(CLASSIC_LOGOUT, path, resolved)) {
      // the logout of the classic applications ends the single sign-on session too;
      // without, the next request would sign the user in again from the session
      try {
        chain.doFilter(request, response);
      } finally {
        endSession(req.getSession(false));
      }
      return;
    }
    chain.doFilter(request, response);
  }

  protected static boolean matches(Pattern pattern, String path, String resolved) {
    return (path != null && pattern.matcher(path).matches()) || (resolved != null && pattern.matcher(resolved).matches());
  }

  protected static void endSession(HttpSession session) {
    if (session == null) {
      return;
    }
    try {
      session.removeAttribute(OidcLogin.SESSION_ATTRIBUTE);
      session.invalidate();
    } catch (IllegalStateException e) {
      // already invalidated
    }
  }

  /** The path below the web applications as sent, without path parameters; null for other paths. */
  protected String relativePath(HttpServletRequest request) {
    String uri = PATH_PARAMETERS.matcher(request.getRequestURI()).replaceAll("");
    String root = appRoot(request);
    if (!uri.startsWith(root + "/")) {
      return null;
    }
    return uri.substring(root.length());
  }

  /** The path below the web applications as resolved by the container; null for other paths. */
  protected String resolvedPath(HttpServletRequest request) {
    String path = (request.getServletPath() == null ? "" : request.getServletPath())
        + (request.getPathInfo() == null ? "" : request.getPathInfo());
    String appPath = ServletContextUtil.getAppPath(request.getServletContext());
    if (!path.startsWith(appPath + "/")) {
      return null;
    }
    return path.substring(appPath.length());
  }

  /** Context path plus application path, e.g. {@code /fasti}. */
  protected String appRoot(HttpServletRequest request) {
    ServletContext context = request.getServletContext();
    return request.getContextPath() + ServletContextUtil.getAppPath(context);
  }

  protected void handleEndpoint(String endpoint, HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String method = req.getMethod();
    switch (endpoint) {
      case "config" -> {
        if (!"GET".equals(method)) {
          resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          return;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", Boolean.TRUE);
        config.put("label", label);
        config.put("loginUrl", appRoot(req) + "/sso/login");
        config.put("logoutUrl", appRoot(req) + "/sso/logout");
        SsoRequests.writeJson(resp, config);
      }
      case "login" -> {
        if (!"GET".equals(method)) {
          resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          return;
        }
        String returnUrl = req.getParameter("return");
        startLogin(req, resp, SsoRequests.isAllowedReturnUrl(appRoot(req) + "/app/next/", returnUrl) ? returnUrl : null);
      }
      case "callback" -> {
        if (!"GET".equals(method)) {
          resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          return;
        }
        completeLogin(req, resp);
      }
      case "logout" -> {
        if (!"POST".equals(method)) {
          resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          return;
        }
        if (!SsoRequests.isSameOriginPost(req)) {
          resp.sendError(HttpServletResponse.SC_FORBIDDEN);
          return;
        }
        logout(req, resp);
      }
      case "backchannel-logout" -> {
        if (!"POST".equals(method)) {
          resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
          return;
        }
        backChannelLogout(req, resp);
      }
      default -> resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  // ---------------------------------------------------------------- login

  protected void startLogin(HttpServletRequest req, HttpServletResponse resp, String returnUrl) throws IOException {
    URI authorization;
    PendingLogin pending = client.newPendingLogin(returnUrl);
    try {
      authorization = client.authorizationUri(pending, redirectUri(req));
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "Cannot start the single sign-on: " + e.getMessage(), e);
      resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }
    HttpSession session = req.getSession(true);
    synchronized (mutex(session)) {
      List<PendingLogin> list = pendingLogins(session);
      long now = System.currentTimeMillis();
      list.removeIf(p -> now - p.createdAt() > PENDING_TTL_MILLIS);
      while (list.size() >= MAX_PENDING) {
        list.remove(0);
      }
      list.add(pending);
      session.setAttribute(PENDING_ATTRIBUTE, list);
    }
    resp.setHeader("Cache-Control", "no-store");
    resp.sendRedirect(authorization.toString());
  }

  @SuppressWarnings("unchecked")
  protected List<PendingLogin> pendingLogins(HttpSession session) {
    Object value = session.getAttribute(PENDING_ATTRIBUTE);
    return value instanceof List<?> list ? new ArrayList<>((List<PendingLogin>) list) : new ArrayList<>();
  }

  protected PendingLogin takePending(HttpSession session, String state) {
    if (session == null || state == null) {
      return null;
    }
    synchronized (mutex(session)) {
      List<PendingLogin> list = pendingLogins(session);
      PendingLogin found = null;
      for (PendingLogin pending : list) {
        if (pending.state().equals(state)) {
          found = pending;
        }
      }
      if (found != null) {
        list.remove(found);
        session.setAttribute(PENDING_ATTRIBUTE, list);
        if (System.currentTimeMillis() - found.createdAt() > PENDING_TTL_MILLIS) {
          return null;
        }
      }
      return found;
    }
  }

  protected void completeLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Cache-Control", "no-store");
    HttpSession session = req.getSession(false);
    PendingLogin pending = takePending(session, req.getParameter("state"));
    String code = req.getParameter("code");
    if (pending == null || code == null || req.getParameter("error") != null) {
      if (req.getParameter("error") != null) {
        LOG.info("Single sign-on not completed: " + req.getParameter("error"));
      } else {
        LOG.info("Single sign-on callback without a matching login request");
      }
      resp.sendRedirect(appRoot(req) + "/");
      return;
    }
    LoginResult result;
    try {
      result = client.completeLogin(code, pending, redirectUri(req));
    } catch (OidcException | IllegalStateException e) {
      LOG.log(Level.WARNING, "Single sign-on failed: " + e.getMessage(), e);
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    Map<String, Object> claims = result.claims();
    String userId = ClaimRules.string(claims, userNameClaim);
    if (userId == null) {
      LOG.warning("Single sign-on failed: the ID token has no claim " + userNameClaim);
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    String groupsClaim = claimRules.getGroupsClaim();
    List<String> groups = claimRules.mapsGroups(groupsClaim) ? claimRules.groups(claims, groupsClaim) : null;
    List<String> tenants = claimRules.mapsTenants() ? claimRules.tenants(claims) : null;
    OidcLogin login = new OidcLogin(userId, ClaimRules.string(claims, "sub"), ClaimRules.string(claims, "sid"),
        result.idToken(), groups, tenants);

    // a new session id for the signed-in user, nothing of an earlier login kept
    req.changeSessionId();
    session.removeAttribute(OidcLogin.SESSION_ATTRIBUTE);
    // the authentications of the web applications (attribute of AuthenticationUtil)
    session.removeAttribute("authenticatedUser");
    session.setAttribute(OidcLogin.SESSION_ATTRIBUTE, login);
    LOG.fine(() -> "Single sign-on of user " + userId);

    String target = pending.returnUrl() != null ? pending.returnUrl() : appRoot(req) + "/";
    resp.sendRedirect(target);
  }

  protected URI redirectUri(HttpServletRequest req) {
    String configured = settings.get("redirect-uri");
    if (configured != null) {
      return URI.create(configured);
    }
    return URI.create(baseUrl(req) + appRoot(req) + "/sso/callback");
  }

  /** {@code scheme://host[:port]} of the request, or {@code base-url} if configured (behind a proxy). */
  protected String baseUrl(HttpServletRequest req) {
    String configured = settings.get("base-url");
    if (configured != null) {
      return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }
    String scheme = req.getScheme();
    int port = req.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return scheme + "://" + req.getServerName() + (defaultPort ? "" : ":" + port);
  }

  // ---------------------------------------------------------------- logout

  protected void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession session = req.getSession(false);
    OidcLogin login = OidcLogin.of(session);
    if (session != null) {
      try {
        session.invalidate();
      } catch (IllegalStateException e) {
        // already invalidated
      }
    }
    String home = baseUrl(req) + appRoot(req) + "/";
    String target = home;
    if (providerLogout && login != null) {
      try {
        URI endSession = client.endSessionUri(login.getIdToken(), settings.get("logout.post-logout-redirect-uri", home));
        if (endSession != null) {
          target = endSession.toString();
        }
      } catch (RuntimeException e) {
        LOG.log(Level.WARNING, "Cannot end the session at the identity provider: " + e.getMessage(), e);
      }
    }
    if (SsoRequests.wantsJson(req)) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("redirectUrl", target);
      SsoRequests.writeJson(resp, body);
    } else {
      resp.setHeader("Cache-Control", "no-store");
      resp.sendRedirect(target);
    }
  }

  protected void backChannelLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Cache-Control", "no-store");
    String token = req.getParameter("logout_token");
    if (token == null || token.isBlank()) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    LogoutTokenClaimsSet claims;
    try {
      claims = client.validateLogoutToken(token);
    } catch (OidcException | IllegalStateException e) {
      LOG.log(Level.INFO, "Back-channel logout rejected: {0}", e.getMessage());
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    String sid = claims.getSessionID() != null ? claims.getSessionID().getValue() : null;
    String sub = claims.getSubject() != null ? claims.getSubject().getValue() : null;
    int ended = 0;
    for (HttpSession session : OidcLogin.sessionsOf(sid, sub)) {
      try {
        session.invalidate();
        ended++;
      } catch (IllegalStateException e) {
        // already invalidated
      }
    }
    LOG.fine("Back-channel logout ended " + ended + " session(s)");
    resp.setStatus(HttpServletResponse.SC_OK);
  }

  protected static Object mutex(HttpSession session) {
    return session;
  }

  @Override
  public void destroy() {
  }
}
