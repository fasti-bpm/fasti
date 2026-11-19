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
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.DefaultRedirectStrategy;

/**
 * Redirects as usual, but answers a script that asks for JSON with the target
 * as {@code {"redirectUrl": ...}}. A script cannot follow a redirect to the
 * identity provider itself (another origin), so it navigates there instead.
 */
public class JsonAwareRedirectStrategy extends DefaultRedirectStrategy {

  @Override
  public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
    if (wantsJson(request)) {
      String target = calculateRedirectUrl(request.getContextPath(), url);
      SsoJson.write(response, Map.of("redirectUrl", target == null ? "/" : target));
      return;
    }
    super.sendRedirect(request, response, url);
  }

  protected static boolean wantsJson(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains("application/json");
  }
}
