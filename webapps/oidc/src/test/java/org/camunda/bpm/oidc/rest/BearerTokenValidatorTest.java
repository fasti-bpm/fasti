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
package org.camunda.bpm.oidc.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.oidc.MockOidcServer;
import org.camunda.bpm.oidc.OidcSettings;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class BearerTokenValidatorTest {

  static MockOidcServer idp;
  static OidcBearerAuthenticationProvider provider;

  @BeforeClass
  public static void start() throws Exception {
    idp = new MockOidcServer();
    provider = new OidcBearerAuthenticationProvider(settings("rest.basic-enabled", "false"));
  }

  @AfterClass
  public static void stop() {
    idp.close();
  }

  static OidcSettings settings(String... extra) {
    Properties p = new Properties();
    p.setProperty("fasti.security.oidc.rest.bearer-enabled", "true");
    p.setProperty("fasti.security.oidc.rest.issuer-uri", idp.getIssuer());
    p.setProperty("fasti.security.oidc.rest.audiences", "fasti-rest, other-allowed");
    p.setProperty("fasti.security.oidc.rest.user-id-claim", "preferred_username");
    p.setProperty("fasti.security.oidc.groups-claim", "groups");
    p.setProperty("fasti.security.oidc.groups.admin", "idp-admins");
    p.setProperty("fasti.security.oidc.tenants.claim", "tenants");
    p.setProperty("fasti.security.oidc.clock-skew-seconds", "0");
    for (int i = 0; i < extra.length; i += 2) {
      p.setProperty("fasti.security.oidc." + extra[i], extra[i + 1]);
    }
    return OidcSettings.of(p);
  }

  static Map<String, Object> alice() {
    return Map.of("sub", "a1", "preferred_username", "alice", "groups", List.of("idp-admins", "sales", "camunda-admin"),
        "tenants", List.of("t1"));
  }

  @Test
  public void validTokenGivesUserGroupsAndTenants() {
    AuthenticationResult result = provider.fromToken(idp.sign(alice(), "fasti-rest", 60));
    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getAuthenticatedUser()).isEqualTo("alice");
    // camunda-admin only through the admin rule, never because the claim says so
    assertThat(result.getGroups()).containsExactly("camunda-admin", "idp-admins", "sales");
    assertThat(result.getTenants()).containsExactly("t1");
  }

  @Test
  public void rejectsWrongAudienceExpiryKeyAlgorithmAndGarbage() {
    assertThat(provider.fromToken(idp.sign(alice(), "other-api", 60)).isAuthenticated()).isFalse();
    assertThat(provider.fromToken(idp.sign(alice(), "fasti-rest", -5)).isAuthenticated()).isFalse();
    assertThat(provider.fromToken(idp.signWithOtherKey(alice(), "fasti-rest")).isAuthenticated()).isFalse();
    assertThat(provider.fromToken(idp.signSymmetric(alice(), "fasti-rest")).isAuthenticated()).isFalse();
    assertThat(provider.fromToken("not-a-token").isAuthenticated()).isFalse();
    assertThat(provider.fromToken("").isAuthenticated()).isFalse();
    // no user id claim
    assertThat(provider.fromToken(idp.sign(Map.of("sub", "x"), "fasti-rest", 60)).isAuthenticated()).isFalse();
  }

  @Test
  public void anotherIssuerIsRejected() throws Exception {
    try (MockOidcServer other = new MockOidcServer()) {
      assertThat(provider.fromToken(other.sign(alice(), "fasti-rest", 60)).isAuthenticated()).isFalse();
    }
  }

  @Test
  public void symmetricAlgorithmsCannotBeConfigured() {
    assertThatThrownBy(() -> new OidcBearerAuthenticationProvider(settings("rest.jws-algorithms", "HS256")))
        .hasMessageContaining("HS256");
  }

  @Test
  public void audienceIsRequired() {
    Properties p = new Properties();
    p.setProperty("fasti.security.oidc.rest.bearer-enabled", "true");
    p.setProperty("fasti.security.oidc.rest.issuer-uri", idp.getIssuer());
    assertThatThrownBy(() -> new OidcBearerAuthenticationProvider(OidcSettings.of(p))).hasMessageContaining("audiences");
  }

  @Test
  public void filterWithoutConfigurationDoesNothing() throws Exception {
    OidcRestAuthenticationFilter filter = new OidcRestAuthenticationFilter();
    filter.init(OidcSettings.of(new Properties()), null);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/engine-rest/task");
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  public void engineListNeedsCredentials() throws Exception {
    OidcRestAuthenticationFilter filter = new OidcRestAuthenticationFilter();
    filter.init(settings("rest.basic-enabled", "false"), "");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/engine");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getHeaders("WWW-Authenticate")).anyMatch(h -> h.startsWith("Bearer"));

    // an engine that does not exist: 401 first, so that its existence is not revealed
    MockHttpServletRequest unknown = new MockHttpServletRequest("GET", "/engine/no-such-engine/task");
    unknown.addHeader("Authorization", "Bearer not-a-token");
    MockHttpServletResponse unknownResponse = new MockHttpServletResponse();
    filter.doFilter(unknown, unknownResponse, new MockFilterChain());
    assertThat(unknownResponse.getStatus()).isEqualTo(401);
  }
}
