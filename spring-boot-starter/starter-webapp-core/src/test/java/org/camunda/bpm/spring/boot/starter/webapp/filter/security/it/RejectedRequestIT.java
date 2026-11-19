/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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

  protected static final String PROTECTED_RESOURCE = "/camunda/api/engine/engine/default/user";

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
    HttpURLConnection connection = request("/camunda/lib/deps.js");

    // then
    assertThat(connection.getResponseCode()).isEqualTo(200);
    assertThat(connection.getHeaderField("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  protected HttpURLConnection request(String path) {
    return httpClientRule.performRequest("http://localhost:" + port + path);
  }

}
