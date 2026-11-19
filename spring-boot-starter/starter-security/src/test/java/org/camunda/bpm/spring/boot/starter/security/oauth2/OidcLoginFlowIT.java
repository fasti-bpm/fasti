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
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.security.SampleApplication;
import org.camunda.bpm.spring.boot.starter.security.oauth2.impl.BearerTokenAuthenticationProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, group mapping, logout and bearer tokens against an OpenID provider
 * that runs inside the test.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SampleApplication.class, OidcLoginFlowIT.Config.class },
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
    "server.port=" + OidcLoginFlowIT.APP_PORT,
    "spring.security.oauth2.client.registration.mock.client-id=" + MockOidcProvider.CLIENT_ID,
    "spring.security.oauth2.client.registration.mock.client-secret=secret",
    "spring.security.oauth2.client.registration.mock.client-name=Mock IdP",
    "spring.security.oauth2.client.registration.mock.scope=openid,profile,email",
    "spring.security.oauth2.client.provider.mock.issuer-uri=http://localhost:" + OidcLoginFlowIT.IDP_PORT,
    "spring.security.oauth2.client.provider.mock.user-name-attribute=preferred_username",
    "camunda.bpm.oauth2.identity-provider.group-name-attribute=groups",
    "camunda.bpm.oauth2.sso-logout.enabled=true",
    "camunda.bpm.oauth2.sso-logout.post-logout-redirect-uri={baseUrl}/fasti/app/next/default/",
    "fasti.security.oidc.groups.admin=idp-admins",
    "fasti.security.oidc.groups.mapping.idp-sales=sales",
    "fasti.security.oidc.tenants.claim=tenants",
    "fasti.security.oidc.rest.bearer-enabled=true",
    "fasti.security.oidc.rest.issuer-uri=http://localhost:" + OidcLoginFlowIT.IDP_PORT,
    "fasti.security.oidc.rest.audiences=" + MockOidcProvider.API_AUDIENCE,
    "fasti.security.oidc.rest.user-id-claim=preferred_username",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OidcLoginFlowIT {

  static final int IDP_PORT = 18531;
  static final int APP_PORT = 18532;
  static final String APP = "http://localhost:" + APP_PORT;

  static final MockOidcProvider IDP = new MockOidcProvider(IDP_PORT);

  @AfterClass
  public static void stopProvider() {
    IDP.stop();
  }

  @TestConfiguration
  static class Config {

    /** What the run distribution registers when bearer tokens are enabled. */
    @Bean
    FilterRegistrationBean<ProcessEngineAuthenticationFilter> restAuthenticationFilter() {
      FilterRegistrationBean<ProcessEngineAuthenticationFilter> registration =
          new FilterRegistrationBean<>(new ProcessEngineAuthenticationFilter());
      registration.addUrlPatterns("/engine-rest/*");
      registration.addInitParameter(ProcessEngineAuthenticationFilter.AUTHENTICATION_PROVIDER_PARAM,
          BearerTokenAuthenticationProvider.class.getName());
      registration.addInitParameter(ProcessEngineAuthenticationFilter.SERVLET_PATH_PREFIX, "/engine-rest");
      registration.setOrder(1);
      return registration;
    }

    @Bean
    WhoAmIController whoAmIController(IdentityService identityService) {
      return new WhoAmIController(identityService);
    }
  }

  /** Shows the engine's view of the user that is logged in to the web applications. */
  @RestController
  static class WhoAmIController {

    private final IdentityService identityService;

    WhoAmIController(IdentityService identityService) {
      this.identityService = identityService;
    }

    @GetMapping("/fasti/api/test/{engine}/whoami")
    public Map<String, Object> whoami(@PathVariable("engine") String engine) {
      String userId = org.springframework.security.core.context.SecurityContextHolder.getContext()
          .getAuthentication().getName();
      User user = identityService.createUserQuery().userId(userId).singleResult();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", userId);
      result.put("email", user != null ? user.getEmail() : null);
      result.put("firstName", user != null ? user.getFirstName() : null);
      result.put("groups", identityService.createGroupQuery().groupMember(userId).list().stream()
          .map(Group::getId).sorted().toList());
      result.put("tenants", identityService.createTenantQuery().userMember(userId).list().stream()
          .map(Tenant::getId).sorted().toList());
      return result;
    }
  }

  @Autowired
  private CamundaBpmProperties camundaBpmProperties;

  @Autowired
  IdentityService identityService;

  private HttpClient browser;
  private CookieManager cookies;

  @Before
  public void setUp() {
    cookies = new CookieManager();
    browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  private HttpResponse<String> get(String path, String... headers) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : APP + path)).GET();
    if (headers.length > 0) {
      request.headers(headers);
    }
    return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String... headers) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(APP + path))
        .POST(HttpRequest.BodyPublishers.noBody());
    if (headers.length > 0) {
      request.headers(headers);
    }
    return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static Map<String, String> query(String location) {
    Map<String, String> result = new HashMap<>();
    String query = URI.create(location).getRawQuery();
    for (String pair : query.split("&")) {
      int idx = pair.indexOf('=');
      result.put(pair.substring(0, idx), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
    }
    return result;
  }

  private String sessionId() {
    return cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals("JSESSIONID")).map(HttpCookie::getValue).findFirst().orElse(null);
  }

  /** Runs the authorization code flow; returns the location after the callback. */
  private String login(Map<String, Object> userClaims) throws Exception {
    HttpResponse<String> start = get("/fasti/sso/login?return=/fasti/app/next/default/");
    assertThat(start.statusCode()).isEqualTo(302);
    assertThat(start.headers().firstValue("Location").orElseThrow()).endsWith(":" + APP_PORT + "/oauth2/authorization/mock");

    HttpResponse<String> authorize = get("/oauth2/authorization/mock");
    assertThat(authorize.statusCode()).isEqualTo(302);
    String location = authorize.headers().firstValue("Location").orElseThrow();
    assertThat(location).startsWith(IDP.getIssuer() + "/authorize?");
    Map<String, String> params = query(location);
    assertThat(params).containsEntry("response_type", "code")
        .containsEntry("client_id", MockOidcProvider.CLIENT_ID)
        .containsEntry("code_challenge_method", "S256")
        .containsEntry("redirect_uri", APP + "/login/oauth2/code/mock")
        .containsKeys("state", "nonce", "code_challenge");

    String sessionBefore = sessionId();
    IDP.nextNonce = params.get("nonce");
    IDP.nextUserClaims = userClaims;
    HttpResponse<String> callback = get("/login/oauth2/code/mock?code=abc&state=" + params.get("state"));
    assertThat(callback.statusCode()).as(callback.body()).isEqualTo(302);

    // PKCE: the verifier sent to the token endpoint matches the challenge
    String verifier = IDP.lastTokenRequest.get("code_verifier");
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    assertThat(challenge).isEqualTo(params.get("code_challenge"));
    // session fixation: the login replaces the session id
    assertThat(sessionId()).isNotEqualTo(sessionBefore);
    return callback.headers().firstValue("Location").orElseThrow();
  }

  private static Map<String, Object> alice() {
    return Map.of(
        "sub", "a1b2",
        "preferred_username", "alice",
        "given_name", "Alice",
        "email", "alice@example.org",
        "groups", List.of("idp-admins", "idp-sales", "camunda-admin", "operators"),
        "tenants", List.of("tenant-a"));
  }

  @Test
  public void announcesSingleSignOn() throws Exception {
    HttpResponse<String> config = get("/fasti/sso/config");

    assertThat(config.statusCode()).isEqualTo(200);
    assertThat(config.body()).contains("\"enabled\":true", "\"label\":\"Mock IdP\"",
        "\"loginUrl\":\"/fasti/sso/login\"", "\"logoutUrl\":\"/logout\"");
  }

  @Test
  public void keepsApiClosedWithoutLogin() throws Exception {
    assertThat(get("/fasti/api/admin/auth/user/default").statusCode()).isEqualTo(401);
    assertThat(get("/fasti/api/test/default/whoami", "Accept", "application/json").statusCode()).isEqualTo(401);
    // a browser opening a classic application is sent to the identity provider
    HttpResponse<String> classic = get("/fasti/app/cockpit/default/", "Accept", "text/html");
    assertThat(classic.statusCode()).isEqualTo(302);
    assertThat(classic.headers().firstValue("Location").orElseThrow()).endsWith("/oauth2/authorization/mock");
  }

  @Test
  public void rejectsCallbackWithWrongState() throws Exception {
    get("/oauth2/authorization/mock");
    HttpResponse<String> callback = get("/login/oauth2/code/mock?code=abc&state=forged");

    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("Location").orElse("")).contains("error");
    assertThat(get("/fasti/api/test/default/whoami").statusCode()).isEqualTo(401);
  }

  @Test
  public void logsInMapsClaimsAndLogsOut() throws Exception {
    String target = login(alice());
    assertThat(target).isEqualTo(APP + "/fasti/app/next/default/");

    HttpResponse<String> whoami = get("/fasti/api/test/default/whoami", "Accept", "application/json");
    assertThat(whoami.statusCode()).as(whoami.body()).isEqualTo(200);
    assertThat(whoami.body())
        .contains("\"id\":\"alice\"", "\"email\":\"alice@example.org\"", "\"firstName\":\"Alice\"",
            "\"tenants\":[\"tenant-a\"]")
        // idp-admins grants the administrators group, the raw camunda-admin value does not count on its own
        .contains("\"groups\":[\"camunda-admin\",\"idp-admins\",\"operators\",\"sales\"]");

    // a logout from another site is ignored
    HttpResponse<String> crossSite = post("/logout", "Accept", "application/json", "Origin", "https://evil.example");
    assertThat(crossSite.body()).doesNotContain("redirectUrl");
    assertThat(get("/fasti/api/test/default/whoami").statusCode()).isEqualTo(200);
    // GET does not log out
    get("/logout");
    assertThat(get("/fasti/api/test/default/whoami").statusCode()).isEqualTo(200);

    HttpResponse<String> logout = post("/logout", "Accept", "application/json", "Origin", APP);
    assertThat(logout.statusCode()).isEqualTo(200);
    assertThat(logout.body()).contains("\"redirectUrl\":\"" + IDP.getIssuer() + "/logout?id_token_hint=")
        .contains("post_logout_redirect_uri=" + "http://localhost:" + APP_PORT + "/fasti/app/next/default/");

    assertThat(get("/fasti/api/test/default/whoami").statusCode()).isEqualTo(401);
  }

  @Test
  public void adminOnlyThroughMapping() throws Exception {
    login(Map.of("sub", "b", "preferred_username", "bob", "groups", List.of("camunda-admin")));

    HttpResponse<String> whoami = get("/fasti/api/test/default/whoami");
    assertThat(whoami.body()).contains("\"groups\":[]");
  }

  @Test
  public void initialSetupIsOffWhileUsersComeFromTheToken() throws Exception {
    // with authorization enabled, a user outside camunda-admin sees no administrator
    // and would be sent to a setup page that cannot create a user
    assertThat(camundaBpmProperties.getWebapp().isInitialSetupEnabled()).isFalse();

    login(Map.of("sub", "c", "preferred_username", "carol", "groups", List.of("operators")));
    HttpResponse<String> page = get("/fasti/app/next/default/", "Accept", "text/html");
    assertThat(page.statusCode()).as(page.headers().firstValue("Location").orElse("")).isEqualTo(200);
  }

  @Test
  public void browserSessionDoesNotAuthenticateRestApi() throws Exception {
    login(alice());

    assertThat(get("/engine-rest/engine/default/identity/groups?userId=alice").statusCode()).isEqualTo(401);
  }

  private HttpResponse<String> rest(String token) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create(APP + "/engine-rest/engine/default/identity/groups?userId=alice"))
        .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private static Map<String, Object> accessToken() {
    return Map.of("sub", "a1b2", "preferred_username", "alice", "aud", List.of(MockOidcProvider.API_AUDIENCE),
        "groups", List.of("idp-sales"));
  }

  @Test
  public void acceptsValidBearerToken() throws Exception {
    HttpResponse<String> response = rest(IDP.sign(accessToken(), Instant.now().plusSeconds(120)));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).contains("\"id\":\"sales\"");
  }

  @Test
  public void rejectsInvalidBearerTokens() throws Exception {
    Map<String, Object> wrongAudience = new HashMap<>(accessToken());
    wrongAudience.put("aud", List.of(MockOidcProvider.CLIENT_ID));

    assertThat(rest(IDP.sign(wrongAudience, Instant.now().plusSeconds(120))).statusCode()).isEqualTo(401);
    assertThat(rest(IDP.sign(accessToken(), Instant.now().minusSeconds(600))).statusCode()).isEqualTo(401);
    // a token without expiry is never accepted
    assertThat(rest(IDP.sign(accessToken(), null)).statusCode()).isEqualTo(401);
    assertThat(rest(IDP.signForIssuer("http://localhost:1/other", accessToken())).statusCode()).isEqualTo(401);
    assertThat(rest(IDP.signWithForeignKey(accessToken())).statusCode()).isEqualTo(401);
    assertThat(rest(IDP.unsigned(accessToken())).statusCode()).isEqualTo(401);
    assertThat(rest(IDP.hmacSigned(accessToken())).statusCode()).isEqualTo(401);
    assertThat(rest("not-a-token").statusCode()).isEqualTo(401);
  }

  @Test
  public void keepsBasicAuthenticationAndChallengesBoth() throws Exception {
    if (identityService.createUserQuery().userId("demo").count() == 0) {
      User demo = identityService.newUser("demo");
      demo.setPassword("demo-password");
      identityService.saveUser(demo);
    }
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(
        URI.create(APP + "/engine-rest/engine/default/user")).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.headers().allValues("WWW-Authenticate")).anyMatch(v -> v.startsWith("Basic"))
        .anyMatch(v -> v.startsWith("Bearer"));

    String basic = Base64.getEncoder().encodeToString("demo:demo-password".getBytes(StandardCharsets.UTF_8));
    HttpResponse<String> withBasic = client.send(HttpRequest.newBuilder(
            URI.create(APP + "/engine-rest/engine/default/user/demo/profile"))
        .header("Authorization", "Basic " + basic).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(withBasic.statusCode()).as(withBasic.body()).isEqualTo(200);
  }
}
