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
package org.camunda.bpm.webapp.impl.security.filter.headersec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.ATTR_CSP_FILTER_NONCE;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_NAME;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The nonce of the content security policy authorizes the scripts of exactly
 * one response: the page that is rendered for a request must carry the nonce
 * that is announced in the header of the same response.
 */
public class ContentSecurityPolicyNonceTest {

  protected static final Pattern NONCE_PATTERN = Pattern.compile("'nonce-([-_a-zA-Z\\d]+)'");

  protected MockServletContext servletContext;
  protected HttpHeaderSecurityFilter filter;

  @Before
  public void setUp() {
    servletContext = new MockServletContext();
    filter = new HttpHeaderSecurityFilter();
    filter.init(new MockFilterConfig(servletContext));
  }

  @Test
  public void shouldProvideNonceOfTheSameResponseToTheRequest() throws Exception {
    // given two requests that are processed at the same time
    final MockHttpServletRequest firstRequest = new MockHttpServletRequest(servletContext);
    final MockHttpServletResponse firstResponse = new MockHttpServletResponse();
    final MockHttpServletRequest secondRequest = new MockHttpServletRequest(servletContext);
    final MockHttpServletResponse secondResponse = new MockHttpServletResponse();

    final String[] nonceOfFirstRequest = new String[1];

    FilterChain chainOfFirstRequest = (request, response) -> {
      // the second request passes the filter before the page of the first one is rendered
      filter.doFilter(secondRequest, secondResponse, (req, res) -> { });

      nonceOfFirstRequest[0] = (String) request.getAttribute(ATTR_CSP_FILTER_NONCE);
    };

    // when
    filter.doFilter(firstRequest, firstResponse, chainOfFirstRequest);

    // then
    assertThat(nonceOfFirstRequest[0]).isNotNull();
    assertThat(nonceOfFirstRequest[0]).isEqualTo(nonceInHeader(firstResponse));
    assertThat(secondRequest.getAttribute(ATTR_CSP_FILTER_NONCE)).isEqualTo(nonceInHeader(secondResponse));
    assertThat(nonceInHeader(firstResponse)).isNotEqualTo(nonceInHeader(secondResponse));
  }

  @Test
  public void shouldNotShareNonceInServletContext() throws Exception {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, (req, res) -> { });

    // then
    assertThat(servletContext.getAttribute(ATTR_CSP_FILTER_NONCE)).isNull();
  }

  @Test
  public void shouldCreateNonceForEveryRequest() throws Exception {
    // given
    Set<String> nonces = new HashSet<>();

    // when
    for (int i = 0; i < 100; i++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(new MockHttpServletRequest(servletContext), response, (req, res) -> { });
      nonces.add(nonceInHeader(response));
    }

    // then
    assertThat(nonces).hasSize(100);
    // at least 128 bit of entropy (base64 encoded)
    assertThat(nonces).allMatch(nonce -> nonce.length() >= 22);
  }

  protected String nonceInHeader(MockHttpServletResponse response) {
    String header = response.getHeader(HEADER_NAME);
    assertThat(header).isNotNull();

    Matcher matcher = NONCE_PATTERN.matcher(header);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

}
