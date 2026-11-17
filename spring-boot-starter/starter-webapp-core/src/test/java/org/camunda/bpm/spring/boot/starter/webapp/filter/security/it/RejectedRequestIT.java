/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter.security.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.camunda.bpm.spring.boot.starter.webapp.filter.util.FilterTestApp;
import org.camunda.bpm.spring.boot.starter.webapp.filter.util.HttpClientRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * A request that is rejected by the security filter is answered by this
 * filter itself. The security headers and the attributes of the session
 * cookie must be present on such a response as on any other, and on the
 * static resources of the web applications.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { FilterTestApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "camunda.bpm.webapp.session-cookie.enable-secure-cookie=true" })
public class RejectedRequestIT {

  protected static final String PROTECTED_RESOURCE = "/fasti/api/engine/engine/default/user";

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Test
  public void shouldSetSecurityHeadersOnRejectedRequest() throws IOException {
    // when
    HttpURLConnection connection = request(PROTECTED_RESOURCE);

    // then
    assertThat(connection.getResponseCode()).isEqualTo(401);
    assertThat(connection.getHeaderField("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(connection.getHeaderField("X-XSS-Protection")).isEqualTo("1; mode=block");
    assertThat(connection.getHeaderField("Content-Security-Policy")).isNotNull();
  }

  @Test
  public void shouldConfigureSessionCookieOfRejectedRequest() throws IOException {
    // when
    HttpURLConnection connection = request(PROTECTED_RESOURCE);

    // then
    assertThat(connection.getResponseCode()).isEqualTo(401);
    assertThat(httpClientRule.getSessionCookie()).matches(httpClientRule.getSessionCookieRegex("Lax", true));
  }

  @Test
  public void shouldSetSecurityHeadersOnStaticResources() throws IOException {
    // when
    HttpURLConnection connection = request("/fasti/lib/deps.js");

    // then
    assertThat(connection.getResponseCode()).isEqualTo(200);
    assertThat(connection.getHeaderField("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  protected HttpURLConnection request(String path) {
    return httpClientRule.performRequest("http://localhost:" + port + path);
  }

}
