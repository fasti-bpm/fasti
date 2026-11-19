/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import javax.servlet.ServletException;

import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * Requests to the root of the web applications are sent to the new web UI,
 * unless another application is configured.
 */
public class ProcessEnginesFilterDefaultAppTest {

  protected ProcessEnginesFilter filter;
  protected MockServletContext servletContext;

  @Before
  public void setUp() {
    servletContext = new MockServletContext();
    filter = new ProcessEnginesFilter() {
      @Override
      protected String getDefaultEngineName() {
        return "default";
      }
    };
  }

  @Test
  public void shouldSendRootToNewWebUiByDefault() throws Exception {
    initFilter(null);

    assertThat(request("/fasti", "/app/").getRedirectedUrl()).isEqualTo("/fasti/app/next/default/");
  }

  @Test
  public void shouldSendRootToClassicWelcomeWhenConfigured() throws Exception {
    initFilter(" Welcome ");

    assertThat(request("/fasti", "/app/").getRedirectedUrl()).isEqualTo("/fasti/app/welcome/default/");
  }

  @Test
  public void shouldSendRootToConfiguredApplication() throws Exception {
    initFilter("tasklist");

    assertThat(request("", "/app/").getRedirectedUrl()).isEqualTo("/app/tasklist/default/");
  }

  @Test
  public void shouldUseApplicationPath() throws Exception {
    ServletContextUtil.setAppPath("/fasti", servletContext);
    initFilter(null);

    assertThat(request("", "/fasti/app/").getRedirectedUrl()).isEqualTo("/fasti/app/next/default/");
  }

  @Test
  public void shouldKeepClassicApplicationsReachable() throws Exception {
    initFilter(null);

    assertThat(request("", "/app/welcome/").getRedirectedUrl()).isEqualTo("/app/welcome/default/");
    assertThat(request("", "/app/tasklist/").getRedirectedUrl()).isEqualTo("/app/tasklist/default/");
  }

  @Test
  public void shouldUseSettingOfServletContextWhenParameterIsMissing() throws Exception {
    // given the default application was configured by other means, e.g. the Spring Boot starter
    ServletContextUtil.setDefaultApp("welcome", servletContext);

    // when
    initFilter(null);

    // then
    assertThat(request("", "/app/").getRedirectedUrl()).isEqualTo("/app/welcome/default/");
  }

  @Test
  public void shouldRejectUnknownApplication() {
    assertThatThrownBy(() -> initFilter("welcom"))
        .isInstanceOf(IllegalWebAppConfigurationException.class)
        .hasMessageContaining("defaultApp")
        .hasMessageContaining("welcom");
  }

  @Test
  public void shouldValidateApplicationNames() {
    assertThat(ProcessEnginesFilter.validDefaultApp(" NEXT ")).isEqualTo("next");
    assertThat(ProcessEnginesFilter.validDefaultApp("cockpit")).isEqualTo("cockpit");
    assertThatThrownBy(() -> ProcessEnginesFilter.validDefaultApp("../evil"))
        .isInstanceOf(IllegalWebAppConfigurationException.class);
  }

  protected void initFilter(String defaultApp) throws ServletException {
    MockFilterConfig filterConfig = new MockFilterConfig(servletContext);
    if (defaultApp != null) {
      filterConfig.addInitParameter(ProcessEnginesFilter.DEFAULT_APP_INIT_PARAM_NAME, defaultApp);
    }
    filter.init(filterConfig);
  }

  protected MockHttpServletResponse request(String contextPath, String path) throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", contextPath + path);
    request.setContextPath(contextPath);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

}
