/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.servlet.ServletException;

import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The classic web applications in the WAR: off unless switched on.
 */
public class ClassicWebappsFilterTest {

  protected MockServletContext servletContext;
  protected String previousProperty;

  @Before
  public void setUp() {
    servletContext = new MockServletContext();
    servletContext.setContextPath("/fasti");
    previousProperty = System.clearProperty(ClassicWebappsFilter.ENABLED_PROPERTY);
  }

  @After
  public void tearDown() {
    if (previousProperty != null) {
      System.setProperty(ClassicWebappsFilter.ENABLED_PROPERTY, previousProperty);
    } else {
      System.clearProperty(ClassicWebappsFilter.ENABLED_PROPERTY);
    }
  }

  @Test
  public void shouldNotServeClassicAppsByDefault() throws Exception {
    // the environment of the build could switch them on; the decision without it is tested below
    ClassicWebappsFilter filter = init();
    if (System.getenv(ClassicWebappsFilter.ENABLED_ENVIRONMENT_VARIABLE) != null) {
      return;
    }

    assertThat(filter.isClassicEnabled()).isFalse();
    assertThat(ServletContextUtil.isClassicWebappsEnabled(servletContext)).isFalse();

    MockHttpServletResponse page = request(filter, "GET", "/app/cockpit/default/");
    assertThat(page.getStatus()).isEqualTo(302);
    assertThat(page.getHeader("Location")).isEqualTo("/fasti/app/next/default/#/cockpit");

    assertThat(request(filter, "GET", "/app/admin/scripts/admin-ui.js").getStatus()).isEqualTo(404);
    assertThat(request(filter, "GET", "/api/cockpit/plugin/base/static/app/plugin.js").getStatus()).isEqualTo(404);
    // the new web UI, the REST API and the scripts of the form frame stay available
    assertThat(passes(filter, "/app/next/default/")).isTrue();
    assertThat(passes(filter, "/api/engine/engine/default/filter")).isTrue();
    assertThat(passes(filter, "/app/tasklist/scripts/config.js")).isTrue();
  }

  @Test
  public void shouldServeClassicAppsWhenSwitchedOnByContextParameter() throws Exception {
    servletContext.addInitParameter(ClassicWebappsFilter.ENABLED_PROPERTY, "true");

    ClassicWebappsFilter filter = init();

    assertThat(filter.isClassicEnabled()).isTrue();
    assertThat(ServletContextUtil.isClassicWebappsEnabled(servletContext)).isTrue();
    assertThat(passes(filter, "/app/cockpit/default/")).isTrue();
    assertThat(passes(filter, "/app/admin/scripts/admin-ui.js")).isTrue();
  }

  @Test
  public void shouldLetSystemPropertyOverrideContextParameter() throws Exception {
    servletContext.addInitParameter(ClassicWebappsFilter.ENABLED_PROPERTY, "true");
    System.setProperty(ClassicWebappsFilter.ENABLED_PROPERTY, "false");

    assertThat(init().isClassicEnabled()).isFalse();
  }

  @Test
  public void shouldDecideByFirstValueThatIsSet() {
    assertThat(ClassicWebappsFilter.isEnabled(null, null, null)).isFalse();
    assertThat(ClassicWebappsFilter.isEnabled(null, " ", " TRUE ")).isTrue();
    assertThat(ClassicWebappsFilter.isEnabled("false", "true", "true")).isFalse();
    assertThat(ClassicWebappsFilter.isEnabled(null, "true", "false")).isTrue();
  }

  @Test
  public void shouldRefuseInvalidValue() {
    assertThatThrownBy(() -> ClassicWebappsFilter.isEnabled("yes"))
        .isInstanceOf(IllegalWebAppConfigurationException.class)
        .hasMessageContaining(ClassicWebappsFilter.ENABLED_PROPERTY);
  }

  protected ClassicWebappsFilter init() throws ServletException {
    ClassicWebappsFilter filter = new ClassicWebappsFilter();
    filter.init(new MockFilterConfig(servletContext));
    return filter;
  }

  protected MockHttpServletResponse request(ClassicWebappsFilter filter, String method, String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, method, "/fasti" + path);
    request.setContextPath("/fasti");
    request.setServletPath(path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  protected boolean passes(ClassicWebappsFilter filter, String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/fasti" + path);
    request.setContextPath("/fasti");
    request.setServletPath(path);
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return chain.getRequest() != null;
  }
}
