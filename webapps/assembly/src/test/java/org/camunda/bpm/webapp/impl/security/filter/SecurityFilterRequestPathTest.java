/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.camunda.bpm.webapp.impl.security.auth.Authentications;
import org.camunda.bpm.webapp.impl.security.auth.UserAuthentication;
import org.camunda.bpm.webapp.impl.security.filter.util.FilterRules;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * A container resolves the servlet of a request from the decoded and
 * normalized path, without path parameters. A request URI that is spelled
 * differently, but resolved to a protected resource, must be authorized like
 * the plain spelling of that resource.
 */
@RunWith(Parameterized.class)
public class SecurityFilterRequestPathTest {

  protected static final String CONTEXT_PATH = "/camunda";

  /** a resource of the engine API, as resolved by the container */
  protected static final String SERVLET_PATH = "/api/engine";
  protected static final String PATH_INFO = "/engine/default/user";

  @Parameters(name = "{0}")
  public static Collection<String> requestUris() {
    return Arrays.asList(
        "/api/engine/engine/default/user",
        // path parameters
        "/api/engine;foo=bar/engine/default/user",
        "/api;foo/engine/engine/default/user",
        "/api/engine;jsessionid=42/engine/default/user",
        // empty and relative segments
        "//api/engine/engine/default/user",
        "/api//engine/engine/default/user",
        "/api/./engine/engine/default/user",
        "/app/../api/engine/engine/default/user",
        "/api/cockpit/plugin/base/static/../../../engine/engine/default/user",
        // encoded characters
        "/api/%65ngine/engine/default/user",
        "/%61pi/engine/engine/default/user",
        "/api/engine%2Fengine/default/user",
        "/api/%2e/engine/engine/default/user");
  }

  @Parameter
  public String requestUri;

  protected SecurityFilter securityFilter;

  @Before
  public void setUp() throws Exception {
    securityFilter = new SecurityFilter();
    try (InputStream rules = new FileInputStream(SecurityFilterRulesTest.FILTER_RULES_FILE)) {
      securityFilter.filterRules = FilterRules.load(rules, "");
    }
  }

  @After
  public void tearDown() {
    Authentications.clearCurrent();
  }

  @Test
  public void shouldRejectAnonymousRequest() throws Exception {
    // given
    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    securityFilter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldRejectRequestAuthenticatedForOtherEngine() throws Exception {
    // given
    authenticateForEngine("otherEngine");

    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    securityFilter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldPassAuthenticatedRequest() throws Exception {
    // given
    authenticateForEngine("default");

    MockHttpServletRequest request = request();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    securityFilter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isSameAs(request);
  }

  protected void authenticateForEngine(String engineName) {
    UserAuthentication authentication = new UserAuthentication("user", engineName);
    authentication.setGroupIds(Collections.<String> emptyList());
    authentication.setTenantIds(Collections.<String> emptyList());
    authentication.setAuthorizedApps(Collections.singleton("cockpit"));

    Authentications authentications = new Authentications();
    authentications.addOrReplace(authentication);
    Authentications.setCurrent(authentications);
  }

  protected MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + requestUri);
    request.setContextPath(CONTEXT_PATH);
    request.setServletPath(SERVLET_PATH);
    request.setPathInfo(PATH_INFO);
    return request;
  }

}
