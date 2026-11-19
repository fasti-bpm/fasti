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

import org.camunda.bpm.spring.boot.starter.webapp.filter.util.FilterTestApp;
import org.camunda.bpm.spring.boot.starter.webapp.filter.util.HttpClientRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * The APIs of the web applications must not be accessible without
 * authentication, no matter how the URI of a protected resource is spelled:
 * the server resolves a resource from the normalized path of a request,
 * without path parameters.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { FilterTestApp.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityFilterRequestPathIT {

  protected static final int UNAUTHORIZED = 401;

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Test
  public void shouldRejectAnonymousRequestToEngineApi() throws IOException {
    assertThat(request("/camunda/api/engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
  }

  @Test
  public void shouldRejectAnonymousRequestWithPathParameters() throws IOException {
    assertThat(request("/camunda/api/engine/engine;foo=bar/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine/engine;/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine/engine;jsessionid=42/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine;foo=bar/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api;foo/engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine/engine/default;foo/user")).isEqualTo(UNAUTHORIZED);
  }

  @Test
  public void shouldRejectAnonymousRequestWithUnnormalizedPath() throws IOException {
    assertThat(request("/camunda//api/engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api//engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine//engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/./engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/app/../api/engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/cockpit/plugin/base/static/../../../../engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
  }

  @Test
  public void shouldRejectAnonymousRequestWithEncodedPath() throws IOException {
    assertThat(request("/camunda/api/%65ngine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/%61pi/engine/engine/default/user")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/engine/%65ngine/default/user")).isEqualTo(UNAUTHORIZED);
  }

  @Test
  public void shouldRejectAnonymousRequestToPluginApis() throws IOException {
    assertThat(request("/camunda/api/cockpit/plugin/base/default/process-definition/statistics-count")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/cockpit/plugin/base;foo/default/process-definition/statistics-count")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/cockpit/plugin;foo/base/default/process-definition/statistics-count")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/cockpit;foo/plugin/base/default/process-definition/statistics-count")).isEqualTo(UNAUTHORIZED);
    assertThat(request("/camunda/api/admin;foo/plugin/adminPlugins/default/metrics/aggregated")).isEqualTo(UNAUTHORIZED);
  }

  @Test
  public void shouldServeEngineNamesToAnonymousRequest() throws IOException {
    assertThat(request("/camunda/api/engine/engine/")).isEqualTo(200);
  }

  protected int request(String path) throws IOException {
    return httpClientRule.performRequest("http://localhost:" + port + path).getResponseCode();
  }

}
