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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SsoLoginSupportFilterTest {

  private final SsoLoginSupportFilter filter = new SsoLoginSupportFilter("/fasti", "keycloak", "Keycloak <Corp>");

  private MockHttpServletResponse get(String uri, String returnUrl, MockHttpServletRequest[] holder) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    if (returnUrl != null) {
      request.setParameter("return", returnUrl);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    if (holder != null) {
      holder[0] = request;
    }
    return response;
  }

  @Test
  public void announcesConfiguration() throws Exception {
    MockHttpServletResponse response = get("/fasti/sso/config", null, null);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getContentAsString()).isEqualTo(
        "{\"enabled\":true,\"label\":\"Keycloak \\u003cCorp\\u003e\",\"loginUrl\":\"/fasti/sso/login\",\"logoutUrl\":\"/logout\"}");
  }

  @Test
  public void remembersPageOfNewWebApplication() throws Exception {
    MockHttpServletRequest[] holder = new MockHttpServletRequest[1];
    MockHttpServletResponse response = get("/fasti/sso/login", "/fasti/app/next/default/", holder);

    assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/keycloak");
    assertThat(holder[0].getSession().getAttribute(SsoLoginSupportFilter.RETURN_URL_ATTRIBUTE))
        .isEqualTo("/fasti/app/next/default/");
  }

  @Test
  public void keepsThePageOfTheApplicationInTheFragment() throws Exception {
    String url = "/fasti/app/next/default/#/tasklist?filter=%C3%A9&task=1";
    MockHttpServletRequest[] holder = new MockHttpServletRequest[1];
    get("/fasti/sso/login", url, holder);
    assertThat(holder[0].getSession().getAttribute(SsoLoginSupportFilter.RETURN_URL_ATTRIBUTE)).isEqualTo(url);
  }

  @Test
  public void ignoresForeignOrTrickyReturnUrls() throws Exception {
    String[] rejected = {
        "https://evil.example/fasti/app/next/default/",
        "//evil.example/",
        "/fasti/app/next//evil.example",
        "/fasti/app/next/../../admin",
        "/fasti/app/next/%2e%2e/x",
        "/fasti/app/next/\\evil",
        "/fasti/app/cockpit/default/",
        "/fasti/app/next/a b",
        "/fasti/app/next/x#y",
        "/fasti/app/next/default/#//evil.example",
        "/fasti/app/next/default/#/a#b",
        "/fasti/app/next/default/#/a b",
        "/fasti/app/next/default/#/a\\b",
        "/fasti/app/next/%2e%2e/#/tasklist",
        "/fasti/app/cockpit/default/#/tasklist",
    };
    for (String url : rejected) {
      MockHttpServletRequest[] holder = new MockHttpServletRequest[1];
      MockHttpServletResponse response = get("/fasti/sso/login", url, holder);
      assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/keycloak");
      assertThat(holder[0].getSession(false) == null
          || holder[0].getSession(false).getAttribute(SsoLoginSupportFilter.RETURN_URL_ATTRIBUTE) == null)
          .as(url).isTrue();
    }
  }

  @Test
  public void answersOnlyKnownEndpoints() throws Exception {
    assertThat(get("/fasti/sso/other", null, null).getStatus()).isEqualTo(404);

    MockHttpServletRequest post = new MockHttpServletRequest("POST", "/fasti/sso/login");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(post, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(404);
  }
}
