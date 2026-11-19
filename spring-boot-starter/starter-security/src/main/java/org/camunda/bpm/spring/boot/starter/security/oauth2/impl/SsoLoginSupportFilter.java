/*
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Endpoints of the login page of the web applications:
 * <ul>
 * <li>{@code GET {app}/sso/config}: whether single sign-on is enabled, the
 * label of the button and the URLs to start a login or logout;</li>
 * <li>{@code GET {app}/sso/login?return=...}: remembers the page to return to
 * after the login and starts the authorization code flow.</li>
 * </ul>
 * The page to return to must be a page of the new web application on this
 * server; anything else is ignored, so the endpoint cannot be used as an open
 * redirect.
 */
public class SsoLoginSupportFilter extends OncePerRequestFilter {

  public static final String RETURN_URL_ATTRIBUTE = SsoLoginSupportFilter.class.getName() + ".RETURN_URL";

  protected static final int MAX_RETURN_URL_LENGTH = 2048;

  protected final String applicationPath;
  protected final String registrationId;
  protected final String label;

  public SsoLoginSupportFilter(String applicationPath, String registrationId, String label) {
    this.applicationPath = applicationPath;
    this.registrationId = registrationId;
    this.label = label;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String contextPath = request.getContextPath();
    String path = request.getRequestURI().substring(contextPath.length());
    String base = applicationPath + "/sso/";
    if (!path.startsWith(base)) {
      chain.doFilter(request, response);
      return;
    }
    String endpoint = path.substring(base.length());
    boolean known = "config".equals(endpoint) || "login".equals(endpoint);
    if (!known || !"GET".equals(request.getMethod())) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if ("config".equals(endpoint)) {
      Map<String, Object> config = new LinkedHashMap<>();
      config.put("enabled", Boolean.TRUE);
      config.put("label", label);
      config.put("loginUrl", contextPath + base + "login");
      config.put("logoutUrl", contextPath + "/logout");
      SsoJson.write(response, config);
      return;
    }

    String returnUrl = request.getParameter("return");
    if (isAllowedReturnUrl(contextPath, returnUrl)) {
      request.getSession().setAttribute(RETURN_URL_ATTRIBUTE, returnUrl);
    } else {
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.removeAttribute(RETURN_URL_ATTRIBUTE);
      }
    }
    response.setHeader("Cache-Control", "no-store");
    response.sendRedirect(contextPath + "/oauth2/authorization/"
        + URLEncoder.encode(registrationId, StandardCharsets.UTF_8));
  }

  /**
   * Only absolute paths below the new web application of this server, without
   * dot segments, percent-encoding, doubled slashes, back slashes or control
   * characters. A fragment ({@code #/tasklist?...}) is allowed: it names the
   * page of the application, stays in the browser and cannot change the
   * target of the redirect; it must start with {@code #/} and may contain
   * percent-encoding, but no back slash and no second {@code #}.
   */
  protected boolean isAllowedReturnUrl(String contextPath, String url) {
    if (url == null || url.isEmpty() || url.length() > MAX_RETURN_URL_LENGTH) {
      return false;
    }
    int hash = url.indexOf('#');
    String path = hash < 0 ? url : url.substring(0, hash);
    if (hash >= 0 && !isAllowedFragment(url.substring(hash + 1))) {
      return false;
    }
    if (!path.startsWith(contextPath + applicationPath + "/app/next/")) {
      return false;
    }
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c < 0x21 || c >= 0x7f || c == '\\' || c == '%') {
        return false;
      }
    }
    return !path.contains("//") && !path.contains("/./") && !path.contains("/../")
        && !path.endsWith("/.") && !path.endsWith("/..");
  }

  protected boolean isAllowedFragment(String fragment) {
    if (!fragment.startsWith("/") || fragment.startsWith("//")) {
      return false;
    }
    for (int i = 0; i < fragment.length(); i++) {
      char c = fragment.charAt(i);
      if (c < 0x21 || c >= 0x7f || c == '\\' || c == '#') {
        return false;
      }
    }
    return true;
  }
}
