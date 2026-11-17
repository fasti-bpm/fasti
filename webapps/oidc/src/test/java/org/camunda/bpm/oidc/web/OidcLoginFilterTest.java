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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.camunda.bpm.oidc.MockOidcServer;
import org.camunda.bpm.oidc.OidcSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

public class OidcLoginFilterTest {

  MockOidcServer idp;
  OidcLoginFilter filter;
  MockServletContext context;
  MockHttpSession session;

  @Before
  public void setUp() throws Exception {
    idp = new MockOidcServer();
    Properties p = new Properties();
    p.setProperty("fasti.security.oidc.issuer-uri", idp.getIssuer());
    p.setProperty("fasti.security.oidc.client-id", MockOidcServer.CLIENT_ID);
    p.setProperty("fasti.security.oidc.client-secret", "${TEST_SECRET}");
    p.setProperty("fasti.security.oidc.login-label", "Mock IdP");
    p.setProperty("fasti.security.oidc.user-name-claim", "preferred_username");
    p.setProperty("fasti.security.oidc.groups-claim", "groups");
    p.setProperty("fasti.security.oidc.groups.admin", "idp-admins");
    p.setProperty("fasti.security.oidc.groups.mapping.idp-sales", "sales");
    p.setProperty("fasti.security.oidc.tenants.claim", "tenants");
    OidcSettings settings = OidcSettings.of(p, name -> "TEST_SECRET".equals(name) ? MockOidcServer.CLIENT_SECRET : null);
    filter = new OidcLoginFilter();
    filter.init(settings, null);
    context = new MockServletContext();
    context.setContextPath("/fasti");
    session = new MockHttpSession(context);
  }

  @After
  public void tearDown() {
    idp.close();
  }

  MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(context, method, "/fasti" + path);
    request.setContextPath("/fasti");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setSession(session);
    return request;
  }

  MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  static Map<String, String> query(String uri) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String pair : URI.create(uri).getRawQuery().split("&")) {
      int eq = pair.indexOf('=');
      result.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
    }
    return result;
  }

  static Map<String, Object> alice() {
    return Map.of("sub", "a1", "sid", "s-1", "preferred_username", "alice",
        "groups", List.of("idp-admins", "idp-sales", "camunda-admin", "operators"), "tenants", List.of("t1"));
  }

  /** Runs login and callback; returns the response of the callback. */
  MockHttpServletResponse login(String returnUrl, Map<String, Object> claims) throws Exception {
    MockHttpServletRequest start = request("GET", "/sso/login");
    if (returnUrl != null) {
      start.setParameter("return", returnUrl);
    }
    MockHttpServletResponse redirect = run(start);
    assertThat(redirect.getStatus()).isEqualTo(302);
    String location = redirect.getRedirectedUrl();
    assertThat(location).startsWith(idp.getIssuer() + "/auth?");
    Map<String, String> params = query(location);
    assertThat(params).containsEntry("response_type", "code")
        .containsEntry("client_id", MockOidcServer.CLIENT_ID)
        .containsEntry("code_challenge_method", "S256")
        .containsEntry("redirect_uri", "http://localhost:8080/fasti/sso/callback")
        .containsKeys("state", "nonce", "code_challenge");
    assertThat(params.get("scope")).contains("openid");

    idp.expectCode("c-1", params.get("nonce"), params.get("code_challenge"), claims);
    MockHttpServletRequest callback = request("GET", "/sso/callback");
    callback.setParameter("code", "c-1");
    callback.setParameter("state", params.get("state"));
    return run(callback);
  }

  @Test
  public void announcesSingleSignOn() throws Exception {
    MockHttpServletResponse response = run(request("GET", "/sso/config"));
    assertThat(response.getContentAsString()).contains("\"enabled\":true", "\"label\":\"Mock IdP\"",
        "\"loginUrl\":\"/fasti/sso/login\"", "\"logoutUrl\":\"/fasti/sso/logout\"");
  }

  @Test
  public void signsInWithCodeFlowPkceStateAndNonce() throws Exception {
    String sessionBefore = session.getId();
    MockHttpServletResponse callback = login("/fasti/app/next/default/#/tasklist", alice());

    assertThat(callback.getRedirectedUrl()).isEqualTo("/fasti/app/next/default/#/tasklist");
    // PKCE: the verifier sent to the token endpoint matches the challenge
    String verifier = idp.lastTokenRequest.get("code_verifier");
    assertThat(verifier).isNotBlank();
    // session fixation: the login replaces the session id
    assertThat(session.getId()).isNotEqualTo(sessionBefore);

    OidcLogin login = OidcLogin.of(session);
    assertThat(login.getUserId()).isEqualTo("alice");
    // camunda-admin only through groups.admin; mapped and unmapped groups
    assertThat(login.getGroups()).containsExactly("camunda-admin", "idp-admins", "sales", "operators");
    assertThat(login.getTenants()).containsExactly("t1");
    assertThat(OidcLogin.sessionsOf("s-1", null)).contains(session);
  }

  @Test
  public void verifierMatchesChallenge() throws Exception {
    MockHttpServletResponse redirect = run(request("GET", "/sso/login"));
    Map<String, String> params = query(redirect.getRedirectedUrl());
    idp.expectCode("c-2", params.get("nonce"), params.get("code_challenge"), alice());
    MockHttpServletRequest callback = request("GET", "/sso/callback");
    callback.setParameter("code", "c-2");
    callback.setParameter("state", params.get("state"));
    run(callback);
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
        .digest(idp.lastTokenRequest.get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
    assertThat(challenge).isEqualTo(params.get("code_challenge"));
    assertThat(OidcLogin.of(session)).isNotNull();
  }

  @Test
  public void rejectsUnknownStateWrongNonceAndForeignReturnUrls() throws Exception {
    MockHttpServletResponse redirect = run(request("GET", "/sso/login"));
    Map<String, String> params = query(redirect.getRedirectedUrl());

    // a state that was never issued
    idp.expectCode("c-3", params.get("nonce"), params.get("code_challenge"), alice());
    MockHttpServletRequest forged = request("GET", "/sso/callback");
    forged.setParameter("code", "c-3");
    forged.setParameter("state", "forged");
    assertThat(run(forged).getRedirectedUrl()).isEqualTo("/fasti/");
    assertThat(OidcLogin.of(session)).isNull();

    // an ID token with another nonce
    idp.expectCode("c-4", "other-nonce", params.get("code_challenge"), alice());
    MockHttpServletRequest callback = request("GET", "/sso/callback");
    callback.setParameter("code", "c-4");
    callback.setParameter("state", params.get("state"));
    assertThat(run(callback).getStatus()).isEqualTo(401);
    assertThat(OidcLogin.of(session)).isNull();

    // the state is single use
    MockHttpServletRequest replay = request("GET", "/sso/callback");
    replay.setParameter("code", "c-4");
    replay.setParameter("state", params.get("state"));
    assertThat(run(replay).getRedirectedUrl()).isEqualTo("/fasti/");

    for (String url : new String[] { "https://evil.example/fasti/app/next/", "//evil.example/", "/fasti/app/next/../x",
        "/fasti/app/cockpit/default/", "/fasti/app/next/default/#//evil" }) {
      assertThat(login(url, alice()).getRedirectedUrl()).as(url).isEqualTo("/fasti/");
    }
  }

  @Test
  public void logoutIsSameOriginPostAndEndsTheProviderSession() throws Exception {
    login(null, alice());

    MockHttpServletRequest get = request("GET", "/sso/logout");
    assertThat(run(get).getStatus()).isEqualTo(405);
    MockHttpServletRequest crossSite = request("POST", "/sso/logout");
    crossSite.addHeader("Sec-Fetch-Site", "cross-site");
    assertThat(run(crossSite).getStatus()).isEqualTo(403);
    assertThat(OidcLogin.of(session)).isNotNull();

    MockHttpServletRequest logout = request("POST", "/sso/logout");
    logout.addHeader("Sec-Fetch-Site", "same-origin");
    logout.addHeader("Accept", "application/json");
    MockHttpServletResponse response = run(logout);
    assertThat(session.isInvalid()).isTrue();
    assertThat(response.getContentAsString()).contains("\"redirectUrl\":\"" + idp.getIssuer() + "/logout?id_token_hint=")
        .contains("post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Ffasti%2F");
  }

  @Test
  public void backChannelLogoutEndsTheSessionsOfTheProviderSession() throws Exception {
    login(null, alice());
    MockHttpServletRequest invalid = request("POST", "/sso/backchannel-logout");
    invalid.setSession(new MockHttpSession(context));
    invalid.setParameter("logout_token", idp.sign(Map.of("sub", "a1", "sid", "s-1"), MockOidcServer.CLIENT_ID, 60));
    assertThat(run(invalid).getStatus()).isEqualTo(400);
    assertThat(session.isInvalid()).isFalse();

    MockHttpServletRequest request = request("POST", "/sso/backchannel-logout");
    request.setSession(new MockHttpSession(context));
    request.setParameter("logout_token", idp.logoutToken("a1", "s-1"));
    assertThat(run(request).getStatus()).isEqualTo(200);
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  public void refusesPasswordLoginsAndSendsClassicAppsToTheProvider() throws Exception {
    assertThat(run(request("POST", "/api/admin/auth/user/default/login/welcome")).getStatus()).isEqualTo(403);
    MockHttpServletResponse classic = run(request("GET", "/app/cockpit/default/"));
    assertThat(classic.getRedirectedUrl()).startsWith(idp.getIssuer() + "/auth?");
  }

  @Test
  public void pathParametersDoNotSlipPastTheRules() throws Exception {
    // as sent: /fasti;x/api;y/admin/...; as resolved by the container: the login resource
    MockHttpServletRequest request = new MockHttpServletRequest(context, "POST", "/fasti;x/api;y/admin/auth/user/default/login/welcome");
    request.setContextPath("/fasti");
    request.setServletPath("/api/admin");
    request.setPathInfo("/auth/user/default/login/welcome");
    assertThat(run(request).getStatus()).isEqualTo(403);

    MockHttpServletRequest raw = request("POST", "/api;y/admin/auth/user/default/login/welcome");
    assertThat(run(raw).getStatus()).isEqualTo(403);
  }

  @Test
  public void logoutOfAClassicApplicationEndsTheSingleSignOnSession() throws Exception {
    login(null, alice());
    MockHttpServletRequest logout = request("POST", "/api/admin/auth/user/default/logout");
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(logout, new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isSameAs(logout);
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  public void withoutConfigurationNothingChanges() throws Exception {
    OidcLoginFilter plain = new OidcLoginFilter();
    plain.init(OidcSettings.of(new Properties()), null);
    MockHttpServletRequest request = request("POST", "/api/admin/auth/user/default/login/welcome");
    MockFilterChain chain = new MockFilterChain();
    plain.doFilter(request, new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isSameAs(request);
  }
}
