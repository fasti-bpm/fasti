/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
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
package org.camunda.bpm.oidc.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Checks and answers shared by the single sign-on endpoints of Run/Spring Boot
 * and of the servlet container integration.
 */
public final class SsoRequests {

  public static final int MAX_RETURN_URL_LENGTH = 2048;

  private SsoRequests() {
  }

  /**
   * Whether the URL is an absolute path that starts with the given prefix
   * (e.g. {@code /fasti/app/next/}), without dot segments, percent-encoding,
   * doubled slashes, back slashes or control characters. A fragment
   * ({@code #/tasklist?...}) is allowed: it names the page of the application,
   * stays in the browser and cannot change the target of the redirect; it
   * must start with {@code #/} and may contain percent-encoding, but no back
   * slash and no second {@code #}.
   */
  public static boolean isAllowedReturnUrl(String prefix, String url) {
    if (url == null || url.isEmpty() || url.length() > MAX_RETURN_URL_LENGTH) {
      return false;
    }
    int hash = url.indexOf('#');
    String path = hash < 0 ? url : url.substring(0, hash);
    if (hash >= 0 && !isAllowedFragment(url.substring(hash + 1))) {
      return false;
    }
    if (!path.startsWith(prefix)) {
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

  protected static boolean isAllowedFragment(String fragment) {
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

  /**
   * Whether a POST was not sent by a browser on behalf of another site: with
   * fetch metadata it must be same-origin; without, a foreign {@code Origin}
   * is rejected. Requests of other servers (no browser headers) pass.
   */
  public static boolean isSameOriginPost(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return false;
    }
    String fetchSite = request.getHeader("Sec-Fetch-Site");
    if (fetchSite != null) {
      return "same-origin".equals(fetchSite);
    }
    String origin = request.getHeader("Origin");
    if (origin == null) {
      return true;
    }
    String host = request.getHeader("Host");
    return host != null && (origin.equals("https://" + host) || origin.equals("http://" + host));
  }

  public static boolean wantsJson(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains("application/json");
  }

  /** Writes a small JSON object of strings and booleans without a JSON library. */
  public static void writeJson(HttpServletResponse response, Map<String, Object> values) throws IOException {
    writeJson(response, HttpServletResponse.SC_OK, values);
  }

  public static void writeJson(HttpServletResponse response, int status, Map<String, Object> values) throws IOException {
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (!first) {
        json.append(',');
      }
      first = false;
      quote(json, entry.getKey());
      json.append(':');
      Object value = entry.getValue();
      if (value == null) {
        json.append("null");
      } else if (value instanceof Boolean) {
        json.append(value);
      } else {
        quote(json, value.toString());
      }
    }
    json.append('}');
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.getWriter().write(json.toString());
  }

  static void quote(StringBuilder json, String value) {
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '<' -> json.append("\\u003c");
        case '>' -> json.append("\\u003e");
        case '&' -> json.append("\\u0026");
        default -> {
          if (c < 0x20 || c == 0x2028 || c == 0x2029) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }
}
