/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.rest.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.junit.Before;
import org.junit.Test;

/**
 * Several authentication filters of the REST API in one chain: the first one
 * that authenticates a request decides, the others let it pass.
 */
public class ProcessEngineAuthenticationFilterChainTest {

  protected ProcessEngine engine;
  protected IdentityService identityService;
  protected Map<String, Object> attributes;
  protected HttpServletRequest request;
  protected HttpServletResponse response;

  @Before
  public void setUp() {
    engine = mock(ProcessEngine.class);
    identityService = mock(IdentityService.class);
    when(engine.getIdentityService()).thenReturn(identityService);

    attributes = new HashMap<>();
    request = mock(HttpServletRequest.class);
    when(request.getContextPath()).thenReturn("/engine-rest");
    when(request.getServletPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/engine-rest/engine/default/task");
    when(request.getAttribute(anyString())).thenAnswer(i -> attributes.get(i.<String>getArgument(0)));
    doAnswer(i -> attributes.put(i.getArgument(0), i.getArgument(1)))
        .when(request).setAttribute(anyString(), any());
    doAnswer(i -> attributes.remove(i.<String>getArgument(0)))
        .when(request).removeAttribute(anyString());
    response = mock(HttpServletResponse.class);
  }

  @Test
  public void shouldRejectRequestWithoutCredentials() throws Exception {
    FilterChain chain = mock(FilterChain.class);

    filter(provider(null)).doFilter(request, response, chain);

    verify(response).setStatus(401);
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void shouldLetRequestAuthenticatedByPrecedingFilterPass() throws Exception {
    AtomicReference<Object> markerInChain = new AtomicReference<>();
    AuthenticationProvider rejecting = provider(null);
    ProcessEngineAuthenticationFilter second = filter(rejecting);
    // the first filter (e.g. bearer tokens) authenticates, the second (basic) would reject
    ProcessEngineAuthenticationFilter first = filter(provider("kermit"));

    first.doFilter(request, response, (req, resp) -> second.doFilter(req, resp, (req2, resp2) ->
        markerInChain.set(req2.getAttribute(ProcessEngineAuthenticationFilter.AUTHENTICATED_REQUEST_ATTRIBUTE))));

    assertThat(markerInChain.get()).isEqualTo("kermit");
    verify(rejecting, never()).extractAuthenticatedUser(any(), any());
    verify(response, never()).setStatus(401);
    verify(identityService).setAuthentication("kermit", Collections.emptyList(), Collections.emptyList());
    // removed again once the request is processed
    assertThat(attributes).doesNotContainKey(ProcessEngineAuthenticationFilter.AUTHENTICATED_REQUEST_ATTRIBUTE);
    verify(identityService).clearAuthentication();
  }

  @Test
  public void shouldStillAuthenticateWhenPrecedingFilterLetRequestPassUnauthenticated() throws Exception {
    // e.g. the bearer token filter that is not configured: the basic filter must still decide
    FilterChain end = mock(FilterChain.class);
    ProcessEngineAuthenticationFilter basic = filter(provider(null));

    FilterChain passThrough = (req, resp) -> basic.doFilter(req, resp, end);
    passThrough.doFilter(request, response);

    verify(response).setStatus(401);
    verify(end, never()).doFilter(any(), any());
  }

  protected AuthenticationProvider provider(String user) {
    AuthenticationProvider provider = mock(AuthenticationProvider.class);
    AuthenticationResult result = user == null
        ? AuthenticationResult.unsuccessful()
        : new AuthenticationResult(user, true);
    if (user != null) {
      result.setGroups(Collections.<String>emptyList());
      result.setTenants(Collections.<String>emptyList());
    }
    when(provider.extractAuthenticatedUser(any(), any())).thenReturn(result);
    return provider;
  }

  protected ProcessEngineAuthenticationFilter filter(AuthenticationProvider provider) {
    ProcessEngineAuthenticationFilter filter = new ProcessEngineAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        return engine;
      }
    };
    filter.authenticationProvider = provider;
    filter.servletPathPrefix = "";
    return filter;
  }
}
