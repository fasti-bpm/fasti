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
import java.util.List;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guards the MCP endpoint, before the protocol is processed:
 * <ol>
 * <li>a request with an {@code Origin} header that is not allowed is refused with 403, as the
 * MCP specification requires against DNS rebinding; the {@code Host} header is checked when
 * allowed hosts are configured;</li>
 * <li>a {@code MCP-Protocol-Version} header naming a version the server does not speak is
 * refused with 400;</li>
 * <li>every request must be authenticated (basic credentials of an engine user or, with OIDC, a
 * bearer token); otherwise 401 with the challenges of the accepted schemes. There is no anonymous
 * access, also when the REST API is configured without authentication.</li>
 * </ol>
 * The authenticated identity is left in a request attribute; each tool call sets it on the engine
 * for its duration only.
 */
public class McpRequestFilter extends HttpFilter {

  private static final long serialVersionUID = 1L;
  private static final Logger LOG = LoggerFactory.getLogger(McpRequestFilter.class);

  public static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

  protected final transient ProcessEngine engine;
  protected final transient List<McpAuthenticator> authenticators;
  protected final List<String> allowedOrigins;
  protected final List<String> allowedHosts;
  protected final List<String> protocolVersions;

  public McpRequestFilter(ProcessEngine engine, List<McpAuthenticator> authenticators, List<String> allowedOrigins,
                          List<String> allowedHosts, List<String> protocolVersions) {
    this.engine = engine;
    this.authenticators = List.copyOf(authenticators);
    this.allowedOrigins = normalize(allowedOrigins);
    this.allowedHosts = normalize(allowedHosts);
    this.protocolVersions = List.copyOf(protocolVersions);
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    response.setHeader("Cache-Control", "no-store");

    String origin = request.getHeader("Origin");
    if (origin != null && !matches(allowedOrigins, origin)) {
      LOG.debug("MCP request refused: origin {} is not allowed", origin);
      reject(response, HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
      return;
    }
    if (!allowedHosts.isEmpty()) {
      String host = request.getHeader("Host");
      if (host == null || !matches(allowedHosts, host)) {
        LOG.debug("MCP request refused: host {} is not allowed", host);
        reject(response, 421, "Host not allowed");
        return;
      }
    }
    String version = request.getHeader(PROTOCOL_VERSION_HEADER);
    if (version != null && !protocolVersions.contains(version.trim())) {
      reject(response, HttpServletResponse.SC_BAD_REQUEST, "Unsupported MCP protocol version");
      return;
    }

    McpIdentity identity;
    String authorization = request.getHeader("Authorization");
    McpAuthenticator authenticator = authenticatorFor(authorization);
    try {
      identity = authenticator == null ? null : authenticator.authenticate(authorization, engine);
    } catch (McpAuthenticationUnavailableException e) {
      LOG.warn("MCP request not authenticated: {}", e.getMessage());
      reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication is not available");
      return;
    }
    if (identity == null) {
      for (McpAuthenticator each : authenticators) {
        response.addHeader("WWW-Authenticate", each.challenge(request));
      }
      reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
      return;
    }

    request.setAttribute(McpIdentity.REQUEST_ATTRIBUTE, identity);
    try {
      chain.doFilter(request, response);
    } finally {
      request.removeAttribute(McpIdentity.REQUEST_ATTRIBUTE);
    }
  }

  protected McpAuthenticator authenticatorFor(String authorization) {
    if (authorization == null) {
      return null;
    }
    for (McpAuthenticator authenticator : authenticators) {
      if (authenticator.supports(authorization)) {
        return authenticator;
      }
    }
    return null;
  }

  /** Answers with a JSON-RPC error without id, as the MCP transport does for refused requests. */
  protected void reject(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,\"message\":\"" + message + "\"}}");
  }

  /**
   * Whether the value equals one of the patterns (case-insensitive); a pattern ending in
   * {@code :*} accepts any port.
   */
  protected static boolean matches(List<String> patterns, String value) {
    String candidate = value.trim().toLowerCase(Locale.ROOT);
    for (String pattern : patterns) {
      if (pattern.equals(candidate)) {
        return true;
      }
      if (pattern.endsWith(":*")) {
        String base = pattern.substring(0, pattern.length() - 2);
        if (candidate.equals(base)
            || (candidate.startsWith(base + ":") && candidate.substring(base.length() + 1).chars().allMatch(Character::isDigit))) {
          return true;
        }
      }
    }
    return false;
  }

  protected static List<String> normalize(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .map(value -> value.endsWith("/") ? value.substring(0, value.length() - 1) : value)
        .toList();
  }
}
