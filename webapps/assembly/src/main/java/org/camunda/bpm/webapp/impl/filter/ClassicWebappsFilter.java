/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.filter;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.WebappLogger;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;

/**
 * Decides whether the classic web applications (Cockpit, Tasklist, Admin and
 * Welcome, built on AngularJS 1.8 and Bootstrap 3, both end of life) are
 * served, and keeps them from being served if not, while the new web UI below
 * <code>/app/next/</code> works as before. For the WAR of the web
 * applications; Spring Boot registers the filter itself.
 *
 * <p>The classic applications are <b>not</b> served unless they are switched
 * on with the value <code>true</code> of, in this order: the system property
 * <code>fasti.webapp.classic.enabled</code>, the environment variable
 * <code>FASTI_CLASSIC_WEBAPPS</code> or the context parameter
 * <code>fasti.webapp.classic.enabled</code> of the web application. The
 * decision is also handed to the new web UI, which hides its links to the
 * classic applications if they are not served.</p>
 *
 * <p>If they are not served:</p>
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
public class ClassicWebappsFilter implements Filter {

  protected static final WebappLogger LOG = WebappLogger.INSTANCE;

  /** system property and context parameter that switch the classic applications on */
  public static final String ENABLED_PROPERTY = "fasti.webapp.classic.enabled";

  /** environment variable that switches the classic applications on (as for the container image) */
  public static final String ENABLED_ENVIRONMENT_VARIABLE = "FASTI_CLASSIC_WEBAPPS";

  /** init parameter: the path of the web applications within the servlet context */
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

  /** whether the classic applications are served; decided in {@link #init(FilterConfig)} */
  protected boolean classicEnabled;

  public ClassicWebappsFilter() {
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String path = filterConfig.getInitParameter(APPLICATION_PATH_PARAM);
    if (path != null) {
      applicationPath = path;
    }
    ServletContext servletContext = filterConfig.getServletContext();
    classicEnabled = isEnabled(System.getProperty(ENABLED_PROPERTY), System.getenv(ENABLED_ENVIRONMENT_VARIABLE),
        servletContext.getInitParameter(ENABLED_PROPERTY));
    ServletContextUtil.setClassicWebappsEnabled(classicEnabled, servletContext);
    if (classicEnabled) {
      LOG.warnClassicWebappsEnabled(servletContext.getContextPath() + applicationPath, ENABLED_PROPERTY);
    }
  }

  /**
   * The first value that is set decides; <code>false</code> if none is set.
   *
   * @throws IllegalWebAppConfigurationException if that value is neither
   *         <code>true</code> nor <code>false</code>
   */
  public static boolean isEnabled(String... values) {
    for (String value : values) {
      if (value == null || value.trim().isEmpty()) {
        continue;
      }
      String trimmed = value.trim();
      if ("true".equalsIgnoreCase(trimmed)) {
        return true;
      } else if ("false".equalsIgnoreCase(trimmed)) {
        return false;
      }
      throw new IllegalWebAppConfigurationException("'" + ENABLED_PROPERTY + "' (or " + ENABLED_ENVIRONMENT_VARIABLE
          + ") must be either 'true' or 'false' but is '" + value + "'.");
    }
    return false;
  }

  public boolean isClassicEnabled() {
    return classicEnabled;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    if (classicEnabled) {
      chain.doFilter(req, resp);
      return;
    }

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
