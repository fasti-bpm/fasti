/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Keeps the classic web applications (Cockpit, Tasklist, Admin and Welcome,
 * built on AngularJS 1.8 and Bootstrap 3, both end of life) from being served,
 * while the new web UI below <code>/app/next/</code> works as before.
 *
 * <ul>
 *   <li>Pages of a classic application, e.g. <code>/app/cockpit/default/</code>
 *   or a bookmark of it, are redirected (302) to the matching section of the new
 *   web UI, e.g. <code>/app/next/default/#/cockpit</code>. The fragment of the
 *   redirect replaces the route of the classic application, which the new web UI
 *   would not know.</li>
 *   <li>Every other request below a classic application, above all its scripts,
 *   styles and translations, and the static files of the plugins of the classic
 *   applications (<code>/api/{app}/plugin/{id}/static/...</code>), are answered
 *   with 404, as are the same files below the web jar path that Spring Boot
 *   serves as well (<code>/webjars/{name}/app/{app}/...</code>).</li>
 *   <li>Excepted are the scripts of the classic Tasklist that the form frame of
 *   the new web UI loads for embedded forms: <code>/app/tasklist/scripts/config.js</code>
 *   and the custom scripts it names. The bundle of the classic Tasklist itself is
 *   not served.</li>
 * </ul>
 *
 * <p>The REST resources below <code>/api/</code> stay available: the new web UI
 * uses them.</p>
 */
public class ClassicWebappsDisabledFilter implements Filter {

  public static final String APPLICATION_PATH_PARAM = "applicationPath";

  /** the classic applications and the section of the new web UI that replaces each */
  protected static final Map<String, String> SECTIONS = Map.of(
      "cockpit", "cockpit",
      "tasklist", "tasklist",
      "admin", "admin",
      "welcome", "");

  /** the files of the classic applications in a web jar, below /webjars/{name}/app/ */
  protected static final Pattern WEBJAR_CLASSIC_APP =
      Pattern.compile("/webjars/[^/]+/app/(cockpit|tasklist|admin|welcome)(/.*)?", Pattern.DOTALL);

  /** the application bundle of the classic Tasklist, below /app/tasklist */
  protected static final String TASKLIST_BUNDLE = "/camunda-tasklist-bootstrap.js";

  protected static final Pattern CLASSIC_APP =
      Pattern.compile("/app/(cockpit|tasklist|admin|welcome)(/.*)?", Pattern.DOTALL);

  protected static final Pattern CLASSIC_PLUGIN_STATIC =
      Pattern.compile("/api/(cockpit|tasklist|admin|welcome)/plugin/[^/]+/static(/.*)?", Pattern.DOTALL);

  /**
   * an engine name that can be put into the redirect as it is (no characters that need
   * encoding); not . or ..
   */
  protected static final Pattern ENGINE_SEGMENT = Pattern.compile("(?!\\.{1,2}$)[\\w.~-]+");

  protected String applicationPath = "";

  public ClassicWebappsDisabledFilter() {
  }

  public ClassicWebappsDisabledFilter(String applicationPath) {
    this.applicationPath = applicationPath == null ? "" : applicationPath;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    String path = filterConfig.getInitParameter(APPLICATION_PATH_PARAM);
    if (path != null) {
      applicationPath = path;
    }
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) resp;

    // the decoded and normalized path that the resource handlers resolve as well (no path
    // parameters, no percent-encoding); case-insensitive, like some file systems
    String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
    String servletPath = (request.getServletPath() == null ? "" : request.getServletPath())
        + (request.getPathInfo() == null ? "" : request.getPathInfo());
    servletPath = servletPath.replace('\\', '/').replaceAll("/{2,}", "/");

    // Spring Boot serves the same web jar below /webjars/{name}/ as well
    Matcher webjar = WEBJAR_CLASSIC_APP.matcher(servletPath.toLowerCase(Locale.ROOT));
    if (webjar.matches()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (!servletPath.toLowerCase(Locale.ROOT).startsWith(applicationPath.toLowerCase(Locale.ROOT))) {
      chain.doFilter(request, response);
      return;
    }
    String path = servletPath.substring(applicationPath.length());
    String lowerPath = path.toLowerCase(Locale.ROOT);
    String prefix = contextPath + applicationPath;

    if (CLASSIC_PLUGIN_STATIC.matcher(lowerPath).matches()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    Matcher app = CLASSIC_APP.matcher(lowerPath);
    if (!app.matches()) {
      chain.doFilter(request, response);
      return;
    }
    String appName = app.group(1);
    // the rest in its original case: engine and script names are case-sensitive
    String rest = app.start(2) < 0 ? "" : path.substring(app.start(2));
    String method = request.getMethod();
    boolean read = "GET".equals(method) || "HEAD".equals(method);

    if (read && "tasklist".equals(appName) && isFormFrameScript(rest)) {
      chain.doFilter(request, response);
      return;
    }

    if (read && isPage(rest)) {
      response.setStatus(HttpServletResponse.SC_FOUND);
      response.setHeader("Location", newUiLocation(prefix, appName, rest));
      response.setHeader("Cache-Control", "no-store");
      response.setContentLength(0);
      return;
    }

    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  /**
   * A page of a classic application: its root, an engine page such as
   * <code>/default/</code>, a route below it or <code>index.html</code>. Files
   * have a dot in their last path segment.
   */
  protected boolean isPage(String rest) {
    String last = rest.substring(rest.lastIndexOf('/') + 1);
    return last.isEmpty() || "index.html".equals(last) || last.indexOf('.') < 0;
  }

  /**
   * The scripts below <code>/app/tasklist/</code> that the form frame may load:
   * the configuration and the custom scripts it names, which can have any name.
   * Only the application bundle of the classic Tasklist is not one of them.
   */
  protected boolean isFormFrameScript(String rest) {
    String lower = rest.toLowerCase(Locale.ROOT);
    return lower.endsWith(".js") && !TASKLIST_BUNDLE.equals(lower);
  }

  protected String newUiLocation(String prefix, String appName, String rest) {
    String engine = null;
    String[] segments = rest.split("/");
    // rest is "", "/", "/index.html", "/{engine}" or "/{engine}/..."
    if (segments.length > 1 && !"index.html".equals(segments[1])
        && ENGINE_SEGMENT.matcher(segments[1]).matches()) {
      engine = segments[1];
    }
    StringBuilder location = new StringBuilder(prefix).append("/app/next/");
    if (engine != null) {
      location.append(engine).append('/');
    }
    return location.append("#/").append(SECTIONS.get(appName)).toString();
  }

  @Override
  public void destroy() {
  }
}
