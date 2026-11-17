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
package org.camunda.bpm.spring.boot.starter.webapp.apppath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.camunda.bpm.spring.boot.starter.webapp.WebappTestApp;
import org.camunda.bpm.spring.boot.starter.webapp.filter.util.HttpClientRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Requests for the former application path /camunda are sent to /fasti.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { WebappTestApp.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LegacyPathRedirectIT {

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Test
  public void shouldRedirectClassicApp() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/camunda/app/tasklist/default/?x=1"));

    assertThat(con.getResponseCode()).isEqualTo(301);
    assertThat(con.getHeaderField("Location")).isEqualTo("/fasti/app/tasklist/default/?x=1");
    assertThat(con.getHeaderField("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  public void shouldRedirectLegacyRoot() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/camunda/"));

    assertThat(con.getResponseCode()).isEqualTo(301);
    assertThat(con.getHeaderField("Location")).isEqualTo("/fasti/");
  }

  @Test
  public void shouldRedirectApiPostKeepingTheMethod() throws IOException {
    HttpURLConnection con = httpClientRule.performPostRequest(
        url("/camunda/api/admin/auth/user/default/login/welcome"),
        "Content-Type", "application/x-www-form-urlencoded", "username=demo&password=demo");

    assertThat(con.getResponseCode()).isEqualTo(308);
    assertThat(con.getHeaderField("Location")).isEqualTo("/fasti/api/admin/auth/user/default/login/welcome");
  }

  @Test
  public void shouldLandOnTheNewWebUiWhenFollowed() throws IOException {
    HttpURLConnection con = new HttpClientRule().followRedirects(true).performRequest(url("/camunda"));

    // the new web UI, or its setup page as long as no administrator exists
    assertThat(con.getResponseCode()).isEqualTo(200);
    assertThat(con.getURL().getPath()).startsWith("/fasti/app/next/default/");
  }

  @Test
  public void shouldServeApiBelowTheNewPath() throws IOException {
    HttpURLConnection con = new HttpClientRule().followRedirects(true)
        .performRequest(url("/camunda/api/engine/engine/"));

    assertThat(con.getResponseCode()).isEqualTo(200);
    assertThat(con.getURL().getPath()).isEqualTo("/fasti/api/engine/engine/");
  }

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }
}
