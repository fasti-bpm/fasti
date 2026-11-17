/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Sends requests for the former application path (<code>/camunda</code>) to
 * the same path below the current application path, keeping the rest of the
 * path and the query string.
 *
 * <p>GET and HEAD requests are answered with 301 (Moved Permanently), which
 * every client follows. All other methods are answered with 308 (Permanent
 * Redirect), so that clients repeat them with the same method and body, e.g.
 * a POST to <code>/camunda/api/...</code>. The redirects are not stored by
 * caches, so that restoring the former application path takes effect at
 * once.</p>
 */
public class LegacyPathRedirectFilter implements Filter {

  public static final String LEGACY_PATH_PARAM = "legacyPath";
  public static final String TARGET_PATH_PARAM = "targetPath";

  protected String legacyPath;
  protected String targetPath;

  public LegacyPathRedirectFilter() {
  }

  public LegacyPathRedirectFilter(String legacyPath, String targetPath) {
    this.legacyPath = legacyPath;
    this.targetPath = targetPath;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    String legacy = filterConfig.getInitParameter(LEGACY_PATH_PARAM);
    if (legacy != null) {
      legacyPath = legacy;
    }
    String target = filterConfig.getInitParameter(TARGET_PATH_PARAM);
    if (target != null) {
      targetPath = target;
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    String location = redirectLocation(req);
    if (location == null) {
      chain.doFilter(request, response);
      return;
    }

    String method = req.getMethod();
    boolean safe = "GET".equals(method) || "HEAD".equals(method);
    resp.setStatus(safe ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
    resp.setHeader("Location", location);
    resp.setHeader("Cache-Control", "no-store");
    resp.setContentLength(0);
  }

  /**
   * @return the path to send the request to, or <code>null</code> if the
   * request is not below the legacy path
   */
  protected String redirectLocation(HttpServletRequest request) {
    String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
    String uri = request.getRequestURI();
    String prefix = contextPath + legacyPath;
    if (uri == null || !uri.startsWith(prefix)) {
      return null;
    }

    String rest = uri.substring(prefix.length());
    if (!rest.isEmpty() && rest.charAt(0) != '/') {
      // e.g. /camundafoo: not below the legacy path
      return null;
    }
    // a single leading slash, so that the location never becomes a
    // protocol-relative URL pointing to another host
    rest = rest.replaceFirst("^[/\\\\]+", "/");

    String location = contextPath + targetPath + rest;
    if (location.isEmpty()) {
      location = "/";
    }

    String query = request.getQueryString();
    if (query != null && !query.isEmpty()) {
      location += "?" + query;
    }
    return location;
  }

  @Override
  public void destroy() {
  }

}
