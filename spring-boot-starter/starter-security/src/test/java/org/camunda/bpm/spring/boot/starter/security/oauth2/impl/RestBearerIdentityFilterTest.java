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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.spring.boot.starter.security.oauth2.FastiOidcProperties;
import org.camunda.bpm.spring.boot.starter.security.oauth2.OAuth2Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class RestBearerIdentityFilterTest {

  private final ProcessEngine engine = mock(ProcessEngine.class);
  private final IdentityService identityService = mock(IdentityService.class);
  private OidcClaimMapper previous;

  @Before
  public void setUp() {
    when(engine.getIdentityService()).thenReturn(identityService);
    previous = OidcClaimMapper.current();
    OAuth2Properties oAuth2Properties = new OAuth2Properties();
    FastiOidcProperties properties = new FastiOidcProperties();
    properties.getRest().setGroupsClaim("groups");
    properties.getGroups().setIncludeUnmapped(true);
    properties.getTenants().setClaim("tenants");
    OidcClaimMapper.install(new OidcClaimMapper(oAuth2Properties, properties));
  }

  @After
  public void tearDown() {
    OidcClaimMapper.install(previous);
    SecurityContextHolder.clearContext();
  }

  private final ProcessEngine second = mock(ProcessEngine.class);
  private final IdentityService secondIdentity = mock(IdentityService.class);

  private RestBearerIdentityFilter filter() {
    return new RestBearerIdentityFilter(name -> {
      if (name == null || "default".equals(name)) {
        return engine;
      }
      return "second".equals(name) ? second : null;
    }, "/engine-rest");
  }

  private static MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setServletPath("/engine-rest");
    return request;
  }

  private void authenticateJwt() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("alice")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES, "alice"));
  }

  @Test
  public void setsIdentityOnTheAddressedEngine() throws Exception {
    when(second.getIdentityService()).thenReturn(secondIdentity);
    authenticateJwt();
    FilterChain chain = mock(FilterChain.class);

    filter().doFilter(request("/engine-rest/engine/second/task"), new MockHttpServletResponse(), chain);

    verify(chain).doFilter(any(), any());
    verify(secondIdentity).setAuthentication(org.mockito.ArgumentMatchers.eq("alice"), any(), any());
    verify(secondIdentity).clearAuthentication();
    verify(identityService, never()).setAuthentication(any(), any(), any());
  }

  @Test
  public void matchesTheEngineOnTheDecodedPath() throws Exception {
    when(second.getIdentityService()).thenReturn(secondIdentity);
    authenticateJwt();
    // JAX-RS routes /%65ngine/second/... to engine "second": the identity must be set there
    for (String uri : new String[] { "/engine-rest/%65ngine/second/task", "/engine-rest/engine/s%65cond/task",
        "/engine-rest/engine;x=y/second/task" }) {
      filter().doFilter(request(uri), new MockHttpServletResponse(), mock(FilterChain.class));
    }
    verify(secondIdentity, org.mockito.Mockito.times(3)).setAuthentication(org.mockito.ArgumentMatchers.eq("alice"), any(), any());
    verify(identityService, never()).setAuthentication(any(), any(), any());

    // UTF-8 whatever the request encoding says
    MockHttpServletRequest utf16 = request("/engine-rest/engine/%73%65%63%6f%6e%64/task");
    utf16.setCharacterEncoding("UTF-16");
    filter().doFilter(utf16, new MockHttpServletResponse(), mock(FilterChain.class));
    verify(secondIdentity, org.mockito.Mockito.times(4)).setAuthentication(org.mockito.ArgumentMatchers.eq("alice"), any(), any());

    // decoded once only, like JAX-RS: %2565 stays the literal text %65
    FilterChain chain = mock(FilterChain.class);
    filter().doFilter(request("/engine-rest/engine/s%2565cond/task"), new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    verify(secondIdentity, org.mockito.Mockito.times(4)).setAuthentication(any(), any(), any());
  }

  @Test
  public void usesTheDefaultEngineForPathsWithoutEngineName() throws Exception {
    authenticateJwt();
    filter().doFilter(request("/engine-rest/task"), new MockHttpServletResponse(), mock(FilterChain.class));
    verify(identityService).setAuthentication(org.mockito.ArgumentMatchers.eq("alice"), any(), any());
    filter().doFilter(request("/engine-rest/engine/default/task"), new MockHttpServletResponse(), mock(FilterChain.class));
    verify(identityService, org.mockito.Mockito.times(2)).setAuthentication(org.mockito.ArgumentMatchers.eq("alice"), any(), any());
  }

  @Test
  public void leavesUnknownEnginesToTheRestApi() throws Exception {
    authenticateJwt();
    FilterChain chain = mock(FilterChain.class);
    filter().doFilter(request("/engine-rest/engine/missing/task"), new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
    verify(identityService, never()).setAuthentication(any(), any(), any());
  }

  @Test
  public void rejectsInvalidBasicCredentialsForTheEngineList() throws Exception {
    when(identityService.checkPassword("demo", "wrong")).thenReturn(false);
    for (String header : new String[] { "Basic garbage", "Basic " + basic("demo:wrong"), "Basic " + basic("nocolon") }) {
      MockHttpServletRequest request = request("/engine-rest/engine");
      request.addHeader("Authorization", header);
      FilterChain chain = mock(FilterChain.class);
      MockHttpServletResponse response = new MockHttpServletResponse();

      filter().doFilter(request, response, chain);

      assertThat(response.getStatus()).as(header).isEqualTo(401);
      verify(chain, never()).doFilter(any(), any());
    }
  }

  @Test
  public void servesTheEngineListForValidBasicCredentialsOrJwt() throws Exception {
    when(identityService.checkPassword("demo", "demo")).thenReturn(true);
    MockHttpServletRequest request = request("/engine-rest/engine/");
    request.addHeader("Authorization", "Basic " + basic("demo:demo"));
    FilterChain chain = mock(FilterChain.class);
    filter().doFilter(request, new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());

    authenticateJwt();
    FilterChain jwtChain = mock(FilterChain.class);
    filter().doFilter(request("/engine-rest/engine"), new MockHttpServletResponse(), jwtChain);
    verify(jwtChain).doFilter(any(), any());
  }

  @Test
  public void leavesBasicCredentialsOfOtherPathsToTheEngineFilter() throws Exception {
    MockHttpServletRequest request = request("/engine-rest/engine/default/task");
    request.addHeader("Authorization", "Basic garbage");
    FilterChain chain = mock(FilterChain.class);
    filter().doFilter(request, new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
  }

  private static String basic(String credentials) {
    return java.util.Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  public void setsAndClearsEngineIdentityOfJwt() throws Exception {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("alice")
        .claim("groups", List.of("sales")).claim("tenants", List.of("tenant-a"))
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES, "alice"));

    AtomicBoolean called = new AtomicBoolean();
    FilterChain chain = (req, res) -> {
      verify(identityService).setAuthentication("alice", List.of("sales"), List.of("tenant-a"));
      verify(identityService, never()).clearAuthentication();
      called.set(true);
    };
    filter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(called).isTrue();
    verify(identityService).clearAuthentication();
  }

  @Test
  public void clearsIdentityWhenChainFails() throws Exception {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("alice")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES, "alice"));
    FilterChain chain = mock(FilterChain.class);
    doAnswer(i -> { throw new IllegalStateException("boom"); }).when(chain).doFilter(any(), any());

    try {
      filter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
    } catch (IllegalStateException expected) {
      // expected
    }
    verify(identityService).clearAuthentication();
  }

  @Test
  public void rejectsTokenWithoutUser() throws Exception {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("x")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES, " "));
    FilterChain chain = mock(FilterChain.class);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter().doFilter(new MockHttpServletRequest(), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verify(chain, never()).doFilter(any(), any());
    verify(identityService, never()).setAuthentication(any(), any(), any());
  }
}
