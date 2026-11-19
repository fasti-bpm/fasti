/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.camunda.bpm.engine.rest.security.auth.AuthenticationResult;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The filter may be mapped to paths outside of the web applications (e.g. to
 * all paths of a Spring Boot application with a custom application path). It
 * must leave requests alone that do not address the web applications, and it
 * must recognize requests that do, however their URI is spelled.
 */
public class ContainerBasedAuthenticationFilterRequestUriTest {

  protected static final String CONTEXT_PATH = "/ctx";
  protected static final String APPLICATION_PATH = "/camunda";

  protected MockServletContext servletContext;
  protected ContainerBasedAuthenticationFilter filter;

  @Before
  public void setUp() throws Exception {
    servletContext = new MockServletContext();
    ServletContextUtil.setAppPath(APPLICATION_PATH, servletContext);

    final ProcessEngine engine = mock(ProcessEngine.class);
    filter = new ContainerBasedAuthenticationFilter() {
      @Override
      protected ProcessEngine getAddressedEngine(String engineName) {
        return engine;
      }
    };

    MockFilterConfig filterConfig = new MockFilterConfig(servletContext);
    filterConfig.addInitParameter("authentication-provider",
        RejectingAuthenticationProvider.class.getName());
    filter.init(filterConfig);
  }

  @Test
  public void shouldPassRequestThatIsShorterThanApplicationPath() throws Exception {
    assertPassed(CONTEXT_PATH + "/x", "/x");
    assertPassed(CONTEXT_PATH + "/", "/");
    assertPassed(CONTEXT_PATH, "");
    assertPassed(CONTEXT_PATH + "/camund", "/camund");
  }

  @Test
  public void shouldPassRequestOutsideOfApplicationPath() throws Exception {
    assertPassed(CONTEXT_PATH + "/other/app/cockpit/default/", "/other/app/cockpit/default/");
    // same length as the application path
    assertPassed(CONTEXT_PATH + "/xxxxxxx/app/cockpit/default/", "/xxxxxxx/app/cockpit/default/");
    assertPassed(CONTEXT_PATH + "/xxxxxxx/api/engine/engine/default/task", "/xxxxxxx/api/engine/engine/default/task");
    assertPassed(CONTEXT_PATH + "/camundax/app/cockpit/default/", "/camundax/app/cockpit/default/");
  }

  @Test
  public void shouldAuthenticateRequestToWebapps() throws Exception {
    assertAuthenticationRequired(CONTEXT_PATH + "/camunda/app/cockpit/default/", "/camunda/app/cockpit/default/");
    assertAuthenticationRequired(CONTEXT_PATH + "/camunda/api/engine/engine/default/task", "/camunda/api/engine/engine/default/task");
    assertAuthenticationRequired(CONTEXT_PATH + "/camunda/api/cockpit/plugin/base/default/incident", "/camunda/api/cockpit/plugin/base/default/incident");
  }

  @Test
  public void shouldAuthenticateRequestToWebappsWithUnnormalizedUri() throws Exception {
    // the container resolves all of these to resources of the web applications
    assertAuthenticationRequired(CONTEXT_PATH + "/camunda;foo/app/cockpit/default/", "/camunda/app/cockpit/default/");
    assertAuthenticationRequired(CONTEXT_PATH + "/x/../camunda/api/engine/engine/default/task", "/camunda/api/engine/engine/default/task");
    assertAuthenticationRequired(CONTEXT_PATH + "//camunda/api/engine/engine/default/task", "/camunda/api/engine/engine/default/task");
    assertAuthenticationRequired(CONTEXT_PATH + "/camunda/api/engine;foo/engine/default/task", "/camunda/api/engine/engine/default/task");
    assertAuthenticationRequired(CONTEXT_PATH + "/%63amunda/api/engine/engine/default/task", "/camunda/api/engine/engine/default/task");
  }

  protected void assertPassed(String requestUri, String resolvedPath) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request(requestUri, resolvedPath), response, chain);

    assertThat(chain.getRequest()).as(requestUri).isNotNull();
    assertThat(response.getStatus()).as(requestUri).isEqualTo(200);
  }

  protected void assertAuthenticationRequired(String requestUri, String resolvedPath) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request(requestUri, resolvedPath), response, chain);

    assertThat(chain.getRequest()).as(requestUri).isNull();
    assertThat(response.getStatus()).as(requestUri).isEqualTo(401);
  }

  protected MockHttpServletRequest request(String requestUri, String resolvedPath) {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", requestUri);
    request.setContextPath(CONTEXT_PATH);
    // resources that are not served by a mapped servlet: the whole path is the servlet path
    request.setServletPath(resolvedPath);
    return request;
  }

  public static class RejectingAuthenticationProvider implements AuthenticationProvider {

    @Override
    public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {
      return AuthenticationResult.unsuccessful();
    }

    @Override
    public void augmentResponseByAuthenticationChallenge(HttpServletResponse response, ProcessEngine engine) {
      // no challenge
    }
  }

}
