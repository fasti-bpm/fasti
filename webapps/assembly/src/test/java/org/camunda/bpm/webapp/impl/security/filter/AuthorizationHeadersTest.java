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
package org.camunda.bpm.webapp.impl.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.util.Collections;

import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The id of the authenticated user is written to a response header. User ids
 * are not restricted to US-ASCII (e.g. when users are managed in LDAP), so
 * the id must not be able to corrupt the header or to add further headers.
 */
public class AuthorizationHeadersTest {

  protected static final String USER_HEADER = "X-Authorized-User";

  @Test
  public void shouldKeepPlainUserId() {
    assertThat(userHeader("jonny1")).isEqualTo("jonny1");
    assertThat(userHeader("john.doe@example.org")).isEqualTo("john.doe@example.org");
    assertThat(userHeader("Mary Jane-O'Neil_2")).isEqualTo("Mary Jane-O'Neil_2");
  }

  @Test
  public void shouldEncodeNonAsciiUserId() throws Exception {
    // given
    String userId = "nguy\u1EC5n.v\u0103n";

    // when
    String header = userHeader(userId);

    // then
    assertThat(header).isEqualTo("nguy%E1%BB%85n.v%C4%83n");
    assertThat(header).matches("[\\x20-\\x7E]+");
    assertThat(URLDecoder.decode(header.replace("+", "%2B"), "UTF-8")).isEqualTo(userId);
  }

  @Test
  public void shouldEncodeSupplementaryCharacters() throws Exception {
    // given
    String userId = "user\uD83D\uDE00";

    // when
    String header = userHeader(userId);

    // then
    assertThat(header).isEqualTo("user%F0%9F%98%80");
  }

  @Test
  public void shouldNotAllowToInjectHeaders() {
    // given
    String userId = "john\r\nSet-Cookie: JSESSIONID=fixed\r\n\r\n<html>";

    // when
    String header = userHeader(userId);

    // then
    assertThat(header).doesNotContain("\r").doesNotContain("\n");
    assertThat(header).isEqualTo("john%0D%0ASet-Cookie: JSESSIONID=fixed%0D%0A%0D%0A<html>");
  }

  @Test
  public void shouldEncodeControlCharacters() {
    assertThat(userHeader("a\u0000b\tc\u007Fd")).isEqualTo("a%00b%09c%7Fd");
  }

  @Test
  public void shouldEncodeEscapeCharacter() {
    // the value can be decoded unambiguously
    assertThat(userHeader("100%")).isEqualTo("100%25");
    assertThat(userHeader("%C3%A4")).isEqualTo("%25C3%25A4");
  }

  @Test
  public void shouldEncodeEngineName() {
    // given
    UserAuthentication authentication = new UserAuthentication("jonny1", "engine\r\nX-Injected: 1");
    authentication.setAuthorizedApps(Collections.singleton("cockpit"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    Authorization.granted(authentication).attachHeaders(response);

    // then
    assertThat(response.getHeader("X-Authorized-Engine")).isEqualTo("engine%0D%0AX-Injected: 1");
    assertThat(response.getHeader("X-Authorized-Apps")).isEqualTo("cockpit");
  }

  protected String userHeader(String userId) {
    UserAuthentication authentication = new UserAuthentication(userId, "default");
    authentication.setAuthorizedApps(Collections.singleton("cockpit"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    Authorization.granted(authentication).attachHeaders(response);

    return response.getHeader(USER_HEADER);
  }

}
