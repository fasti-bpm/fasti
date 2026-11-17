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
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

/**
 * The filter must only ever redirect to a path on the same host: a request URI
 * that a browser would interpret as an absolute or protocol-relative URL must
 * not end up in the Location header (open redirect).
 */
public class AppendTrailingSlashFilterTest {

  protected AppendTrailingSlashFilter filter = new AppendTrailingSlashFilter();

  protected HttpServletRequest request;
  protected HttpServletResponse response;
  protected FilterChain chain;

  @Before
  public void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
  }

  @Test
  public void shouldRedirectLocalPathWithTrailingSlash() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/fasti/app");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(response).sendRedirect("/fasti/app/");
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  public void shouldNotRedirectToProtocolRelativeUrl() throws Exception {
    assertNoRedirect("//example.org/camunda/app");
  }

  @Test
  public void shouldNotRedirectToProtocolRelativeUrlWithBackslash() throws Exception {
    // browsers treat a backslash like a forward slash
    assertNoRedirect("/\\example.org/camunda/app");
    assertNoRedirect("\\\\example.org/camunda/app");
  }

  @Test
  public void shouldNotRedirectToAbsoluteUrl() throws Exception {
    assertNoRedirect("https://example.org/camunda/app");
  }

  @Test
  public void shouldNotRedirectToRelativePath() throws Exception {
    assertNoRedirect("app");
  }

  @Test
  public void shouldRedirectNestedLocalPath() throws Exception {
    // given
    when(request.getRequestURI()).thenReturn("/fasti/app/cockpit");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(response).sendRedirect("/fasti/app/cockpit/");
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  public void shouldNotRedirectToUrlWithOtherScheme() throws Exception {
    assertNoRedirect("javascript:alert(1)");
    assertNoRedirect("http:example.org");
  }

  @Test
  public void shouldNotRedirectBackslashAnywhereInPath() throws Exception {
    assertNoRedirect("/fasti\\app");
    assertNoRedirect("/fasti/app\\");
  }

  @Test
  public void shouldNotRedirectWhitespaceOrControlCharacters() throws Exception {
    // browsers strip tabs and line breaks before they parse a URL
    assertNoRedirect("/\t/example.org/camunda/app");
    assertNoRedirect("/\n/example.org/camunda/app");
    assertNoRedirect("/\r/example.org/camunda/app");
    assertNoRedirect("\t//example.org/camunda/app");
    assertNoRedirect(" //example.org/camunda/app");
    assertNoRedirect("/fasti/app\r\nSet-Cookie: a=b");
    assertNoRedirect("/fasti/app" + (char) 0);
    assertNoRedirect("/fasti/app" + (char) 127);
  }

  @Test
  public void shouldNotRedirectPercentEncodedSlashOrBackslashPrefix() throws Exception {
    assertNoRedirect("/%2Fexample.org/camunda/app");
    assertNoRedirect("/%2f%2fexample.org/camunda/app");
    assertNoRedirect("/%5Cexample.org/camunda/app");
    assertNoRedirect("/%5cexample.org/camunda/app");
  }

  @Test
  public void shouldNotRedirectForEmptyRequestUri() throws Exception {
    assertNoRedirect("");
  }

  @Test
  public void shouldNotRedirectForMissingRequestUri() throws Exception {
    assertNoRedirect(null);
  }

  protected void assertNoRedirect(String requestUri) throws Exception {
    // given
    setUp();
    when(request.getRequestURI()).thenReturn(requestUri);

    // when
    filter.doFilter(request, response, chain);

    // then the request is passed on unchanged
    verify(response, never()).sendRedirect(anyString());
    verify(chain).doFilter(request, response);
  }

}
