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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Test;

public class LegacyPathRedirectFilterTest {

  protected LegacyPathRedirectFilter filter = new LegacyPathRedirectFilter("/camunda", "/fasti");

  @Test
  public void shouldRedirectGetWithMovedPermanently() throws Exception {
    assertRedirect("GET", "", "/camunda/app/tasklist/default/", null, 301, "/fasti/app/tasklist/default/");
  }

  @Test
  public void shouldRedirectHeadWithMovedPermanently() throws Exception {
    assertRedirect("HEAD", "", "/camunda/app/", null, 301, "/fasti/app/");
  }

  @Test
  public void shouldRedirectOtherMethodsWithPermanentRedirect() throws Exception {
    // 308 makes the client repeat the request with the same method and body
    assertRedirect("POST", "", "/camunda/api/admin/auth/user/default/login/welcome", null, 308,
        "/fasti/api/admin/auth/user/default/login/welcome");
    assertRedirect("PUT", "", "/camunda/api/engine/engine/default/task/1/variables/x", null, 308,
        "/fasti/api/engine/engine/default/task/1/variables/x");
  }

  @Test
  public void shouldKeepQueryString() throws Exception {
    assertRedirect("GET", "", "/camunda/api/engine/engine/default/task", "a=1&b=x%20y", 301,
        "/fasti/api/engine/engine/default/task?a=1&b=x%20y");
  }

  @Test
  public void shouldRedirectLegacyRoot() throws Exception {
    assertRedirect("GET", "", "/camunda", null, 301, "/fasti");
    assertRedirect("GET", "", "/camunda/", null, 301, "/fasti/");
  }

  @Test
  public void shouldKeepContextPath() throws Exception {
    assertRedirect("GET", "/ctx", "/ctx/camunda/app/", null, 301, "/ctx/fasti/app/");
  }

  @Test
  public void shouldRedirectToRootForEmptyApplicationPath() throws Exception {
    filter = new LegacyPathRedirectFilter("/camunda", "");
    assertRedirect("GET", "", "/camunda", null, 301, "/");
    assertRedirect("GET", "", "/camunda/app/", null, 301, "/app/");
  }

  @Test
  public void shouldNotRedirectToOtherHost() throws Exception {
    // without an application path, /camunda//example.org would become //example.org
    filter = new LegacyPathRedirectFilter("/camunda", "");
    assertRedirect("GET", "", "/camunda//example.org/x", null, 301, "/example.org/x");
    assertRedirect("GET", "", "/camunda/\\example.org/x", null, 301, "/example.org/x");
  }

  @Test
  public void shouldIgnoreOtherPaths() throws Exception {
    assertNoRedirect("/camundafoo/app/");
    assertNoRedirect("/fasti/app/");
    assertNoRedirect("/engine-rest/engine");
  }

  protected void assertRedirect(String method, String contextPath, String uri, String query,
                                int status, String location) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getContextPath()).thenReturn(contextPath);
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getQueryString()).thenReturn(query);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(status);
    verify(response).setHeader("Location", location);
    verify(response).setHeader("Cache-Control", "no-store");
    verify(chain, never()).doFilter(request, response);
  }

  protected void assertNoRedirect(String uri) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getMethod()).thenReturn("GET");
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn(uri);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setHeader(eq("Location"), anyString());
  }
}
