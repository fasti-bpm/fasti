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
package dev.fasti.run.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class FastiPrometheusTokenFilterTest {

  protected MockHttpServletResponse filter(String token, String authorization) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    new FastiPrometheusTokenFilter(token).doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  public void shouldAcceptTheBearerToken() throws Exception {
    assertThat(filter("secret", "Bearer secret").getStatus()).isEqualTo(200);
    assertThat(filter("secret", "bearer  secret ").getStatus()).isEqualTo(200);
  }

  @Test
  public void shouldIgnoreLineEndsOfTokenFiles() throws Exception {
    assertThat(filter("secret\r\n", "Bearer secret").getStatus()).isEqualTo(200);
  }

  @Test
  public void shouldRejectEverythingElse() throws Exception {
    MockHttpServletResponse response = filter("secret", null);
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
    assertThat(filter("secret", "Bearer other").getStatus()).isEqualTo(401);
    assertThat(filter("secret", "Basic c2VjcmV0OnNlY3JldA==").getStatus()).isEqualTo(401);
    assertThat(filter("secret", "Bearer ").getStatus()).isEqualTo(401);
    assertThat(filter("", "Bearer ").getStatus()).isEqualTo(401);
  }
}
