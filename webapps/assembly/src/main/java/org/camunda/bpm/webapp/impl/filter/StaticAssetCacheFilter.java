/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.filter;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.camunda.bpm.engine.impl.util.ProductPropertiesUtil;

/**
 * Lets browsers keep the static files of the web applications that cannot go stale, cached for
 * a year without revalidation (<code>Cache-Control: public, max-age=31536000, immutable</code>):
 * <ul>
 *   <li>the content-hashed build output of the new web UI
 *   (<code>/app/next/assets/name-HASH.ext</code>, without query string): a changed file gets a
 *   new name;</li>
 *   <li>the build output of the classic applications and of the form frame of the new web UI,
 *   requested as <code>?bust=VERSION</code> with the version of this release: scripts, styles,
 *   fonts and images below <code>/lib/</code> and <code>/assets/</code>, and the bundles of the
 *   applications (<code>/app/cockpit/camunda-cockpit-bootstrap.js</code>,
 *   <code>/app/forms/fasti-form-frame.js</code>, ...). The bundles name the release in the query
 *   string, so a new release is fetched anew. Files that operators edit
 *   (<code>scripts/config.js</code>, <code>styles/user-styles.css</code>, translations, custom
 *   scripts of any name) keep their headers, whatever the query string.</li>
 * </ul>
 * Every other GET below <code>/app/next/</code> gets <code>no-cache</code>, above all
 * <code>index.html</code>, which names the current assets; other requests are left alone.
 * Error responses are never marked cacheable.
 *
 * <p>The init parameter <code>applicationPath</code> gives the path of the web applications
 * within the servlet context (empty by default).
 */
public class StaticAssetCacheFilter implements Filter {

  public static final String IMMUTABLE = "public, max-age=31536000, immutable";

  /** init parameter: the path of the web applications within the servlet context */
  public static final String APPLICATION_PATH_PARAM = "applicationPath";

  /** Vite names a chunk {@code name-HASH.ext}; the hash has 8 URL-safe base64 characters. */
  protected static final Pattern HASHED_ASSET =
      Pattern.compile(".*/app/next/assets/[\\w.-]+-[\\w-]{8}\\.(?:js|css|woff2?|svg|png|jpg|webp)");

  protected static final Pattern NEXT_APP = Pattern.compile(".*/app/next/.*");

  /**
   * Build output of the classic applications, relative to the application path: the files below
   * {@code /lib/} and {@code /assets/} and the bundles of the applications by name. Other files
   * below an application can be custom scripts of operators, which the applications request
   * with the same query string.
   */
  protected static final Pattern VERSIONED_FILE = Pattern.compile(
      "/(?:(?:lib|assets)/(?:[\\w.-]+/)*[\\w.-]+\\.(?:js|css|woff2?|ttf|eot|svg|png|jpe?g|gif|ico|webp)"
      + "|app/(cockpit|tasklist|admin|welcome)/camunda-\\1-bootstrap\\.(?:js|css)"
      + "|app/forms/fasti-form-frame\\.(?:js|css))");

  protected String applicationPath = "";

  /** The query string of a file of this release, or {@code null} if the release is not known. */
  protected String versionQuery = versionQuery(ProductPropertiesUtil.getProductVersion());

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
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
    String method = request.getMethod();
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      chain.doFilter(request, response);
    } else if (isImmutable(request)) {
      response.setHeader("Cache-Control", IMMUTABLE);
      chain.doFilter(request, new ErrorNotCached(response));
    } else {
      if (request.getRequestURI() != null && NEXT_APP.matcher(request.getRequestURI()).matches()) {
        // index.html and the other pages must be revalidated: they name the current assets
        response.setHeader("Cache-Control", "no-cache");
      }
      chain.doFilter(request, response);
    }
  }

  protected boolean isImmutable(HttpServletRequest request) {
    String query = request.getQueryString();
    String uri = request.getRequestURI();
    if (query == null) {
      return uri != null && HASHED_ASSET.matcher(uri).matches();
    }
    if (versionQuery == null || !versionQuery.equals(query)) {
      return false;
    }
    // the decoded and normalized path the resource handlers resolve as well
    String path = (request.getServletPath() == null ? "" : request.getServletPath())
        + (request.getPathInfo() == null ? "" : request.getPathInfo());
    if (!path.startsWith(applicationPath + "/") || path.contains("/.") || path.contains("\\")) {
      return false;
    }
    return VERSIONED_FILE.matcher(path.substring(applicationPath.length())).matches();
  }

  protected static String versionQuery(String version) {
    return version == null || version.isBlank() ? null : "bust=" + version;
  }

  @Override
  public void destroy() {
  }

  /**
   * Keeps the long cache lifetime against filters further down the chain that set
   * <code>Cache-Control</code> on every GET, and takes it back when the response turns out
   * to be an error.
   */
  protected static class ErrorNotCached extends HttpServletResponseWrapper {

    protected boolean error;

    public ErrorNotCached(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setHeader(String name, String value) {
      if (error || !"Cache-Control".equalsIgnoreCase(name)) {
        super.setHeader(name, value);
      }
    }

    @Override
    public void addHeader(String name, String value) {
      if (error || !"Cache-Control".equalsIgnoreCase(name)) {
        super.addHeader(name, value);
      }
    }

    @Override
    public void setStatus(int sc) {
      uncacheIfError(sc);
      super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
      uncacheIfError(sc);
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      uncacheIfError(sc);
      super.sendError(sc, msg);
    }

    protected void uncacheIfError(int sc) {
      if (sc >= 300 && sc != HttpServletResponse.SC_NOT_MODIFIED && !isCommitted()) {
        error = true;
        super.setHeader("Cache-Control", "no-cache");
      }
    }
  }
}
