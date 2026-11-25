/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
 * With two client registrations only the one of the login page signs users in, through the whole
 * filter chain: neither the login filter nor the OAuth2 client filter starts an authorization for
 * the other one.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SampleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
    "server.port=" + OidcSecondRegistrationIT.APP_PORT,
    "spring.security.oauth2.client.registration.mock.client-id=" + MockOidcProvider.CLIENT_ID,
    "spring.security.oauth2.client.registration.mock.client-secret=secret",
    "spring.security.oauth2.client.registration.mock.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.mock.provider=mock",
    "spring.security.oauth2.client.registration.other.client-id=other-client",
    "spring.security.oauth2.client.registration.other.client-secret=secret",
    "spring.security.oauth2.client.registration.other.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.other.provider=mock",
    "spring.security.oauth2.client.provider.mock.issuer-uri=http://localhost:" + OidcSecondRegistrationIT.IDP_PORT,
    "spring.security.oauth2.client.provider.mock.user-name-attribute=preferred_username",
    "fasti.security.oidc.registration-id=mock",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OidcSecondRegistrationIT {

  static final int IDP_PORT = 18541;
  static final int APP_PORT = 18542;
  static final String APP = "http://localhost:" + APP_PORT;

  static final MockOidcProvider IDP = new MockOidcProvider(IDP_PORT);

  @AfterClass
  public static void stopProvider() {
    IDP.stop();
  }

  private HttpClient browser;

  @Before
  public void setUp() {
    browser = HttpClient.newBuilder().cookieHandler(new CookieManager())
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  private HttpResponse<String> get(String path) throws Exception {
    return browser.send(HttpRequest.newBuilder(URI.create(APP + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  public void startsTheLoginOfTheChosenRegistration() throws Exception {
    HttpResponse<String> authorize = get("/oauth2/authorization/mock");

    assertThat(authorize.statusCode()).isEqualTo(302);
    assertThat(authorize.headers().firstValue("Location").orElseThrow()).startsWith(IDP.getIssuer() + "/authorize?");
  }

  @Test
  public void doesNotStartALoginThroughAnotherRegistration() throws Exception {
    HttpResponse<String> authorize = get("/oauth2/authorization/other");

    assertThat(authorize.headers().firstValue("Location").orElse(""))
        .as("no redirect to the identity provider for the other registration")
        .doesNotStartWith(IDP.getIssuer());
  }

  @Test
  public void refusesACallbackOfAnotherRegistration() throws Exception {
    HttpResponse<String> callback = get("/login/oauth2/code/other?code=abc&state=forged");

    assertThat(callback.statusCode()).isEqualTo(302);
    assertThat(callback.headers().firstValue("Location").orElse("")).contains("error");
  }
}
