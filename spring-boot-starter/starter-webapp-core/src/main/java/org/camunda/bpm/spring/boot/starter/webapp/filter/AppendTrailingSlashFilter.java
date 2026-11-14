/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 * Servlet filter to ensure request paths always have a trailing slash. Before Spring Boot 3, missing trailing slashes were
 * handled automatically. This filter implements the Spring Boot 2 behavior for the registered request patterns.
 *
 * <p>The redirect target is derived from the request URI, which is controlled by the client. To make sure that the
 * <code>Location</code> header can never point to another host, a redirect is only sent if the request URI is an
 * absolute path on this host (see {@link #isLocalPath(String)}). Any other request is passed down the filter chain
 * unchanged.</p>
 *
 * @see https://github.com/spring-projects/spring-framework/issues/28552
 */
public class AppendTrailingSlashFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String requestURI = ((HttpServletRequest) request).getRequestURI();

    if (isLocalPath(requestURI)) {
      ((HttpServletResponse) response).sendRedirect(requestURI + "/");
    } else {
      chain.doFilter(request, response);
    }
  }

  /**
   * Returns <code>true</code> if a browser resolves the given request URI to a path on the host that
   * served the request, i.e. the URI
   * <ul>
   *   <li>starts with exactly one slash: <code>//host</code> is a protocol-relative URL, a value without
   *       leading slash is either a relative path or an absolute URL (<code>scheme:...</code>),</li>
   *   <li>contains no backslash, because browsers treat it like a forward slash (<code>/\host</code>),
   *       neither literally nor percent-encoded,</li>
   *   <li>does not continue with a percent-encoded slash (<code>/%2Fhost</code>), which becomes
   *       protocol-relative as soon as any intermediary decodes the header value,</li>
   *   <li>contains no whitespace or control characters: browsers remove tabs and line breaks from a URL
   *       before parsing it (<code>/&lt;TAB&gt;/host</code>), and line breaks must never reach a response
   *       header.</li>
   * </ul>
   */
  protected boolean isLocalPath(String requestURI) {
    if (requestURI == null || requestURI.isEmpty() || requestURI.charAt(0) != '/') {
      return false;
    }

    if (requestURI.startsWith("//")) {
      return false;
    }

    for (int i = 0; i < requestURI.length(); i++) {
      char c = requestURI.charAt(i);
      if (c == '\\' || c <= ' ' || c == 0x7F) {
        return false;
      }
    }

    String lowerCaseURI = requestURI.toLowerCase(Locale.ROOT);
    return !lowerCaseURI.contains("%5c") && !lowerCaseURI.startsWith("/%2f");
  }

}
