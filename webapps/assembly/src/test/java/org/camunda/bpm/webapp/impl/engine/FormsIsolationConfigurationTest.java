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
package org.camunda.bpm.webapp.impl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import javax.servlet.ServletContext;

import org.camunda.bpm.webapp.impl.IllegalWebAppConfigurationException;
import org.camunda.bpm.webapp.impl.util.FormsIsolation;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The isolation of embedded forms is configured by init parameters of the
 * engines filter and announced to the new web UI by its index page.
 */
public class FormsIsolationConfigurationTest {

  protected static final String PAGE = "<meta name=\"fasti-forms-isolation\" content=\"$FORMS_ISOLATION\" />"
      + "<meta name=\"fasti-forms-origin\" content=\"$FORMS_ORIGIN\" />";

  protected MockServletContext servletContext;

  @Before
  public void setUp() {
    servletContext = new MockServletContext();
  }

  @Test
  public void shouldSandboxByDefault() throws Exception {
    ProcessEnginesFilter filter = filter(null, null);

    assertThat(ServletContextUtil.getFormsIsolation(servletContext)).isEqualTo(FormsIsolation.SANDBOXED);
    assertThat(ServletContextUtil.getFormsOrigin(servletContext)).isNull();
    assertThat(indexPage(filter))
        .contains("content=\"sandboxed\"")
        .contains("<meta name=\"fasti-forms-origin\" content=\"\" />");
  }

  @Test
  public void shouldAcceptSameOriginMode() throws Exception {
    ProcessEnginesFilter filter = filter(" Same-Origin ", null);

    assertThat(ServletContextUtil.getFormsIsolation(servletContext)).isEqualTo(FormsIsolation.SAME_ORIGIN);
    assertThat(indexPage(filter)).contains("content=\"same-origin\"");
  }

  @Test
  public void shouldAcceptSeparateOriginWithOrigin() throws Exception {
    ProcessEnginesFilter filter = filter("separate-origin", "HTTPS://Forms.Example.com:8443/");

    assertThat(ServletContextUtil.getFormsIsolation(servletContext)).isEqualTo(FormsIsolation.SEPARATE_ORIGIN);
    assertThat(ServletContextUtil.getFormsOrigin(servletContext)).isEqualTo("https://forms.example.com:8443");
    assertThat(indexPage(filter))
        .contains("content=\"separate-origin\"")
        .contains("content=\"https://forms.example.com:8443\"");
  }

  @Test
  public void shouldRejectMisspelledMode() {
    assertThatThrownBy(() -> filter("none", null))
        .isInstanceOf(IllegalWebAppConfigurationException.class)
        .hasMessageContaining("formsIsolation");
  }

  @Test
  public void shouldRejectSeparateOriginWithoutOrigin() {
    assertThatThrownBy(() -> filter("separate-origin", null))
        .isInstanceOf(IllegalWebAppConfigurationException.class)
        .hasMessageContaining("formsOrigin");
  }

  @Test
  public void shouldRejectOriginsThatAreNoOrigins() {
    String[] invalid = {
      "forms.example.com",
      "javascript:alert(1)",
      "https://forms.example.com/path",
      "https://user@forms.example.com",
      "https://forms.example.com?x=1",
      "https://forms.example.com#x",
      "ftp://forms.example.com",
      "https://forms.example.com\"><script>"
    };
    for (String origin : invalid) {
      servletContext = new MockServletContext();
      assertThatThrownBy(() -> filter("separate-origin", origin)).as(origin)
          .isInstanceOf(IllegalWebAppConfigurationException.class);
    }
  }

  @Test
  public void shouldIgnoreOriginOutsideSeparateOriginMode() throws Exception {
    ProcessEnginesFilter filter = filter("sandboxed", "https://forms.example.com");

    assertThat(ServletContextUtil.getFormsOrigin(servletContext)).isNull();
    assertThat(indexPage(filter)).doesNotContain("forms.example.com");
  }

  protected ProcessEnginesFilter filter(String isolation, String origin) throws Exception {
    ProcessEnginesFilter filter = new ProcessEnginesFilter() {
      @Override
      protected String getWebResourceContents(String name) throws IOException {
        return PAGE;
      }

      @Override
      protected String getDefaultEngineName() {
        return "default";
      }

      @Override
      protected boolean needsInitialUser(String engineName) {
        return false;
      }

      @Override
      protected void setWebappInTelemetry(String engineName, String appName, ServletContext servletContext) {
        // no engine is available in this test
      }
    };
    MockFilterConfig config = new MockFilterConfig(servletContext);
    if (isolation != null) {
      config.addInitParameter(ProcessEnginesFilter.FORMS_ISOLATION_INIT_PARAM_NAME, isolation);
    }
    if (origin != null) {
      config.addInitParameter(ProcessEnginesFilter.FORMS_ORIGIN_INIT_PARAM_NAME, origin);
    }
    filter.init(config);
    return filter;
  }

  protected String indexPage(ProcessEnginesFilter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", "/fasti/app/next/default/");
    request.setContextPath("/fasti");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getContentAsString();
  }
}
