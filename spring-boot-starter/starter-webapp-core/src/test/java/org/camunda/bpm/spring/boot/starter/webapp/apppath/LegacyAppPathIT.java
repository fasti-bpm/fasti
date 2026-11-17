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
 * With the former application path restored, the web applications are served
 * below /camunda and nothing is redirected.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { WebappTestApp.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "camunda.bpm.webapp.application-path=/camunda")
public class LegacyAppPathIT {

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Test
  public void shouldServeWebappsBelowLegacyPath() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/camunda/app/admin/styles/user-styles.css"));

    assertThat(con.getResponseCode()).isEqualTo(200);
  }

  @Test
  public void shouldServeApiBelowLegacyPath() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/camunda/api/engine/engine/"));

    assertThat(con.getResponseCode()).isEqualTo(200);
  }

  @Test
  public void shouldNotServeNewPath() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/fasti/app/admin/styles/user-styles.css"));

    assertThat(con.getResponseCode()).isEqualTo(404);
  }

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }
}
