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
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.spring.boot.starter.security.SampleApplication;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * A back-channel logout of the identity provider ends the session, also with a renamed session
 * cookie (as in Fasti Run and the container image).
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { SampleApplication.class, OidcLoginFlowIT.Config.class },
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
    "server.port=" + OidcBackChannelLogoutIT.APP_PORT,
    "server.servlet.session.cookie.name=" + OidcBackChannelLogoutIT.COOKIE,
    "spring.security.oauth2.client.registration.mock.client-id=" + MockOidcProvider.CLIENT_ID,
    "spring.security.oauth2.client.registration.mock.client-secret=secret",
    "spring.security.oauth2.client.registration.mock.scope=openid,profile",
    "spring.security.oauth2.client.provider.mock.issuer-uri=http://localhost:" + OidcBackChannelLogoutIT.IDP_PORT,
    "spring.security.oauth2.client.provider.mock.user-name-attribute=preferred_username",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OidcBackChannelLogoutIT {

  static final int IDP_PORT = 18551;
  static final int APP_PORT = 18552;
  static final String APP = "http://localhost:" + APP_PORT;
  static final String COOKIE = "FASTI_SESSION";

  static final MockOidcProvider IDP = new MockOidcProvider(IDP_PORT);

  @AfterClass
  public static void stopProvider() {
    IDP.stop();
  }

  private HttpClient browser;
  private CookieManager cookies;

  @Before
  public void setUp() {
    cookies = new CookieManager();
    browser = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return browser.send(HttpRequest.newBuilder(URI.create(APP + path)).header("Accept", "application/json").GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static Map<String, String> query(String location) {
    Map<String, String> result = new HashMap<>();
    for (String pair : URI.create(location).getRawQuery().split("&")) {
      int idx = pair.indexOf('=');
      result.put(pair.substring(0, idx), URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
    }
    return result;
  }

  private void login(Map<String, Object> userClaims) throws Exception {
    assertThat(get("/fasti/sso/login?return=/fasti/app/next/default/").statusCode()).isEqualTo(302);
    HttpResponse<String> authorize = get("/oauth2/authorization/mock");
    assertThat(authorize.statusCode()).isEqualTo(302);
    Map<String, String> params = query(authorize.headers().firstValue("Location").orElseThrow());
    IDP.nextNonce = params.get("nonce");
    IDP.nextUserClaims = userClaims;
    HttpResponse<String> callback = get("/login/oauth2/code/mock?code=abc&state=" + params.get("state"));
    assertThat(callback.statusCode()).as(callback.body()).isEqualTo(302);
  }

  @Test
  public void endsTheSessionOnABackChannelLogout() throws Exception {
    login(Map.of("sub", "b1", "sid", "session-b1", "preferred_username", "bob"));
    assertThat(cookies.getCookieStore().getCookies()).anySatisfy(cookie -> assertThat(cookie.getName()).isEqualTo(COOKIE));
    HttpResponse<String> before = get("/fasti/api/test/default/whoami");
    assertThat(before.statusCode()).isEqualTo(200);
    assertThat(before.body()).contains("\"bob\"");

    String logoutToken = IDP.sign(Map.of(
        "aud", List.of(MockOidcProvider.CLIENT_ID),
        "sub", "b1",
        "sid", "session-b1",
        "events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of())), Instant.now().plusSeconds(120));
    // from the identity provider: no cookie of the browser
    HttpResponse<String> backChannel = HttpClient.newHttpClient().send(HttpRequest
        .newBuilder(URI.create(APP + "/logout/connect/back-channel/mock"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("logout_token=" + URLEncoder.encode(logoutToken, StandardCharsets.UTF_8)))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertThat(backChannel.statusCode()).as(backChannel.body()).isEqualTo(200);

    assertThat(get("/fasti/api/test/default/whoami").statusCode()).isEqualTo(401);
  }
}
