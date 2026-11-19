/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.run;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds the response headers that the header security filter of the web
 * applications does not cover, on every path of Run, including the REST API:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} (REST answers such as
 *   attachment contents are never sniffed as HTML),</li>
 *   <li>{@code Referrer-Policy: same-origin} (no URLs with ids leak to other
 *   sites),</li>
 *   <li>{@code Permissions-Policy} denying browser features the UI never
 *   uses,</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin}.</li>
 * </ul>
 * Headers already set by the application are left alone. Switched off with
 * {@code fasti.http.security-headers.enabled=false}.
 */
public class FastiSecurityHeadersFilter implements Filter {

  public static final String PERMISSIONS_POLICY =
      "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=(), bluetooth=()";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse http) {
      setIfAbsent(http, "X-Content-Type-Options", "nosniff");
      setIfAbsent(http, "Referrer-Policy", "same-origin");
      setIfAbsent(http, "Permissions-Policy", PERMISSIONS_POLICY);
      setIfAbsent(http, "Cross-Origin-Opener-Policy", "same-origin");
    }
    chain.doFilter(request, response);
  }

  protected static void setIfAbsent(HttpServletResponse response, String name, String value) {
    if (!response.containsHeader(name)) {
      response.setHeader(name, value);
    }
  }
}
