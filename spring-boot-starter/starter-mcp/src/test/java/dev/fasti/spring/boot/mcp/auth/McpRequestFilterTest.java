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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import org.camunda.bpm.engine.ProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpRequestFilterTest {

  static final McpIdentity ALICE = new McpIdentity("alice", List.of("staff"), List.of());

  /** Accepts basic credentials alice:pw only. */
  static final McpAuthenticator FAKE = new BasicMcpAuthenticator() {
    @Override
    public McpIdentity authenticate(String authorizationHeader, ProcessEngine engine) {
      String expected = "Basic " + Base64.getEncoder().encodeToString("alice:pw".getBytes(StandardCharsets.UTF_8));
      return expected.equals(authorizationHeader) ? ALICE : null;
    }
  };

  McpRequestFilter filter(List<String> origins, List<String> hosts, McpAuthenticator... authenticators) {
    return new McpRequestFilter(null, List.of(authenticators), origins, hosts, List.of("2025-06-18", "2025-11-25"));
  }

  static MockHttpServletRequest request(String authorization) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    return request;
  }

  static String alice() {
    return "Basic " + Base64.getEncoder().encodeToString("alice:pw".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void passesAuthenticatedRequestsWithTheIdentity() throws Exception {
    AtomicReference<Object> seen = new AtomicReference<>();
    MockHttpServletRequest request = request(alice());
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter(List.of(), List.of(), FAKE).doFilter(request, response, new MockFilterChain() {
      @Override
      public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
        seen.set(((HttpServletRequest) req).getAttribute(McpIdentity.REQUEST_ATTRIBUTE));
      }
    });

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(seen.get()).isEqualTo(ALICE);
    // the identity does not outlive the request
    assertThat(request.getAttribute(McpIdentity.REQUEST_ATTRIBUTE)).isNull();
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void challengesWithEveryAcceptedScheme() throws Exception {
    McpAuthenticator bearer = new BearerMcpAuthenticator(token -> null, "sub", req -> "http://localhost/meta");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter(List.of(), List.of(), bearer, FAKE).doFilter(request(null), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getHeaders("WWW-Authenticate"))
        .containsExactly("Bearer realm=\"fasti-mcp\", resource_metadata=\"http://localhost/meta\"",
            "Basic realm=\"fasti-mcp\", charset=\"UTF-8\"");
  }

  @Test
  void refusesWrongCredentials() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter(List.of(), List.of(), FAKE).doFilter(request("Basic Ym9iOnB3"), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void refusesOriginsThatAreNotAllowedBeforeAuthentication() throws Exception {
    MockHttpServletRequest foreign = request(alice());
    foreign.addHeader("Origin", "http://attacker.example");
    MockHttpServletRequest allowed = request(alice());
    allowed.addHeader("Origin", "http://localhost:6274");
    MockHttpServletRequest sneaky = request(alice());
    sneaky.addHeader("Origin", "http://localhost.attacker.example");
    McpRequestFilter filter = filter(List.of("http://localhost:*"), List.of(), FAKE);

    MockHttpServletResponse foreignResponse = new MockHttpServletResponse();
    filter.doFilter(foreign, foreignResponse, new MockFilterChain());
    MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
    filter.doFilter(allowed, allowedResponse, new MockFilterChain());
    MockHttpServletResponse sneakyResponse = new MockHttpServletResponse();
    filter.doFilter(sneaky, sneakyResponse, new MockFilterChain());

    assertThat(foreignResponse.getStatus()).isEqualTo(403);
    assertThat(allowedResponse.getStatus()).isEqualTo(200);
    assertThat(sneakyResponse.getStatus()).isEqualTo(403);
  }

  @Test
  void checksTheHostWhenConfigured() throws Exception {
    MockHttpServletRequest rebound = request(alice());
    rebound.addHeader("Host", "rebind.attacker.example:8080");
    MockHttpServletRequest local = request(alice());
    local.addHeader("Host", "localhost:8080");
    McpRequestFilter filter = filter(List.of(), List.of("localhost:*", "127.0.0.1:*"), FAKE);

    MockHttpServletResponse reboundResponse = new MockHttpServletResponse();
    filter.doFilter(rebound, reboundResponse, new MockFilterChain());
    MockHttpServletResponse localResponse = new MockHttpServletResponse();
    filter.doFilter(local, localResponse, new MockFilterChain());

    assertThat(reboundResponse.getStatus()).isEqualTo(421);
    assertThat(localResponse.getStatus()).isEqualTo(200);
  }

  @Test
  void refusesUnknownProtocolVersions() throws Exception {
    MockHttpServletRequest request = request(alice());
    request.addHeader(McpRequestFilter.PROTOCOL_VERSION_HEADER, "2024-01-01");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter(List.of(), List.of(), FAKE).doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void answers503WhenCredentialsCannotBeChecked() throws Exception {
    McpAuthenticator unavailable = new BearerMcpAuthenticator(token -> {
      throw new IllegalStateException("connection refused");
    }, "sub", null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter(List.of(), List.of(), unavailable).doFilter(request("Bearer abc"), response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(503);
  }

  @Test
  void matchesPortWildcardsOnlyForPorts() {
    assertThat(McpRequestFilter.matches(List.of("http://localhost:*"), "http://localhost")).isTrue();
    assertThat(McpRequestFilter.matches(List.of("http://localhost:*"), "http://localhost:3000")).isTrue();
    assertThat(McpRequestFilter.matches(List.of("http://localhost:*"), "http://localhost:3000.evil")).isFalse();
    assertThat(McpRequestFilter.matches(List.of("https://app.example"), "HTTPS://APP.EXAMPLE")).isTrue();
    assertThat(McpRequestFilter.matches(List.of(), "null")).isFalse();
  }
}
