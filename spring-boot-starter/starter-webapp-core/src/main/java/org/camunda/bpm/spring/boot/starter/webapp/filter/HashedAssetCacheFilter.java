/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Lets browsers keep the content-hashed build output of the new web UI
 * (<code>/app/next/assets/name-HASH.ext</code>). A changed file gets a new name, so such a
 * response never goes stale and is cached for a year without revalidation. Every other
 * GET below <code>/app/next/</code> gets <code>no-cache</code>, above all
 * <code>index.html</code>, which names the current assets. Error responses are never
 * marked cacheable.
 */
public class HashedAssetCacheFilter implements Filter {

  public static final String IMMUTABLE = "public, max-age=31536000, immutable";

  /** Vite names a chunk {@code name-HASH.ext}; the hash has 8 URL-safe base64 characters. */
  protected static final Pattern HASHED_ASSET =
      Pattern.compile(".*/app/next/assets/[\\w.-]+-[\\w-]{8}\\.(?:js|css|woff2?|svg|png|jpg|webp)");

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) resp;
    String method = request.getMethod();
    if (("GET".equals(method) || "HEAD".equals(method)) && request.getQueryString() == null
        && HASHED_ASSET.matcher(request.getRequestURI()).matches()) {
      response.setHeader("Cache-Control", IMMUTABLE);
      chain.doFilter(request, new ErrorNotCached(response));
    } else {
      if ("GET".equals(method) || "HEAD".equals(method)) {
        // index.html and the other pages must be revalidated: they name the current assets
        response.setHeader("Cache-Control", "no-cache");
      }
      chain.doFilter(request, response);
    }
  }

  /**
   * Keeps the long cache lifetime against filters further down the chain that set
   * <code>Cache-Control</code> on every GET, and takes it back when the response turns out
   * to be an error.
   */
  protected static class ErrorNotCached extends HttpServletResponseWrapper {

    protected boolean error;

    ErrorNotCached(HttpServletResponse response) {
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
