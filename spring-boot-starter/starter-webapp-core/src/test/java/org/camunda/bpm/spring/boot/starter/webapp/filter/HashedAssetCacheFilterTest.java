/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;

public class HashedAssetCacheFilterTest {

  protected HashedAssetCacheFilter filter = new HashedAssetCacheFilter();
  protected HttpServletRequest request;
  protected MockHttpServletResponse response;
  protected FilterChain chain;

  @Before
  public void setUp() {
    request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("GET");
    response = new MockHttpServletResponse();
    chain = mock(FilterChain.class);
  }

  @Test
  public void shouldCacheHashedAssetForAYear() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/index-bpn4tOkO.js");
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isEqualTo(HashedAssetCacheFilter.IMMUTABLE);
  }

  @Test
  public void shouldKeepLongCacheAgainstLaterNoCache() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/index-bpn4tOkO.js");
    doAnswer(inv -> {
      // what the cache control filter further down the chain does on every GET
      ((HttpServletResponse) inv.getArgument(1, ServletResponse.class)).setHeader("Cache-Control", "no-cache");
      return null;
    }).when(chain).doFilter(any(), any());
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isEqualTo(HashedAssetCacheFilter.IMMUTABLE);
  }

  @Test
  public void shouldRevalidateIndexPage() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/default/");
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldRevalidateUnhashedAsset() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/logo.svg");
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldNotCacheMissingAsset() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/gone-AAAAAAAA.js");
    doAnswer(inv -> {
      ((HttpServletResponse) inv.getArgument(1, ServletResponse.class)).sendError(404);
      return null;
    }).when(chain).doFilter(any(), any());
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldNotCacheWithQueryString() throws Exception {
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/index-bpn4tOkO.js");
    when(request.getQueryString()).thenReturn("v=1");
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldLeavePostAlone() throws Exception {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/fasti/app/next/assets/index-bpn4tOkO.js");
    filter.doFilter(request, response, chain);
    assertThat(response.getHeader("Cache-Control")).isNull();
  }
}
