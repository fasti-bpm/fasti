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

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Matches a POST to the given path that a browser did not send on behalf of
 * another site. Spring Security's CSRF protection is off because the web
 * applications have their own; this keeps the logout endpoint, which is
 * outside of them, from being triggered by another site.
 */
public class SameOriginPostRequestMatcher implements RequestMatcher {

  protected final String path;

  public SameOriginPostRequestMatcher(String path) {
    this.path = path;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return false;
    }
    String uri = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.equals(uri)) {
      return false;
    }
    String fetchSite = request.getHeader("Sec-Fetch-Site");
    if (fetchSite != null) {
      return "same-origin".equals(fetchSite);
    }
    // browsers without fetch metadata: a cross-site request carries a foreign Origin
    String origin = request.getHeader("Origin");
    if (origin == null) {
      return true;
    }
    String host = request.getHeader("Host");
    return host != null && (origin.equals("https://" + host) || origin.equals("http://" + host));
  }
}
