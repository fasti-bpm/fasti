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
package org.camunda.bpm.spring.boot.starter.webapp.setup;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.spring.boot.starter.webapp.filter.util.HttpClientRule;
import org.junit.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Requests against the initial setup of the web applications, i.e. the page
 * and the endpoint that create the first administrator without authentication.
 */
public abstract class AbstractInitialSetupIT {

  protected static final String INITIAL_USER_ID = "initialAdmin";

  protected static final String INITIAL_USER = "{\"profile\": {\"id\": \"" + INITIAL_USER_ID + "\", "
      + "\"firstName\": \"Initial\", \"lastName\": \"Administrator\", \"email\": \"admin@example.org\"}, "
      + "\"credentials\": {\"password\": \"aSecretPassword\"}}";

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Autowired
  protected IdentityService identityService;

  /**
   * @return the redirect target of a request for the Tasklist of the default engine,
   *         <code>null</code> if the request is not redirected
   */
  protected String requestTasklist() {
    return httpClientRule.performRequest(url("/camunda/app/tasklist/default/")).getHeaderField("Location");
  }

  /**
   * @return the redirect target of a request for the setup page of the default engine,
   *         <code>null</code> if the request is not redirected
   */
  protected String requestSetupPage() {
    return httpClientRule.performRequest(url("/camunda/app/admin/default/setup/")).getHeaderField("Location");
  }

  /**
   * Sends a valid request to create the initial user, as the setup page does.
   *
   * @return the status code of the response
   */
  protected int createInitialUser() throws IOException {
    // obtain a session and a CSRF token, otherwise the request does not reach the setup endpoint
    httpClientRule.performRequest(url("/camunda/api/engine/engine/"));

    Map<String, String> headers = new HashMap<>();
    headers.put("X-XSRF-TOKEN", httpClientRule.getHeaderXsrfToken());
    headers.put("Cookie", httpClientRule.getSessionCookieValue());
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");

    HttpURLConnection connection = httpClientRule.performPostRequest(
        url("/camunda/api/admin/setup/default/user/create"), headers, INITIAL_USER);
    return connection.getResponseCode();
  }

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

}
