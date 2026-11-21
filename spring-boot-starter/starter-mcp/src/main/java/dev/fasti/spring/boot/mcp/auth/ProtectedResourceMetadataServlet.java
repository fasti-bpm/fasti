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
package dev.fasti.spring.boot.mcp.auth;

import java.io.IOException;
import java.util.function.Function;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * OAuth 2.0 protected resource metadata of the MCP endpoint (RFC 9728), as the MCP authorization
 * specification asks for: names the identity provider that issues the accepted bearer tokens.
 */
public class ProtectedResourceMetadataServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  protected final String issuer;
  protected final transient Function<HttpServletRequest, String> resourceUrl;

  public ProtectedResourceMetadataServlet(String issuer, Function<HttpServletRequest, String> resourceUrl) {
    this.issuer = issuer;
    this.resourceUrl = resourceUrl;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write("{\"resource\":" + quote(resourceUrl.apply(request))
        + ",\"authorization_servers\":[" + quote(issuer) + "]"
        + ",\"bearer_methods_supported\":[\"header\"]"
        + ",\"resource_name\":\"Fasti BPM MCP server\"}");
  }

  /** A JSON string literal. */
  protected static String quote(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder quoted = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        default -> {
          if (c < 0x20) {
            quoted.append(String.format("\\u%04x", (int) c));
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }
}
