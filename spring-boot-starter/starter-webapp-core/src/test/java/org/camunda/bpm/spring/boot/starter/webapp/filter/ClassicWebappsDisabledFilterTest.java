/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class ClassicWebappsDisabledFilterTest {

  protected ClassicWebappsDisabledFilter filter = new ClassicWebappsDisabledFilter("/fasti");

  @Test
  public void shouldRedirectPagesToTheMatchingSectionOfTheNewWebUi() throws Exception {
    assertRedirect("/fasti/app/cockpit/default/", "/fasti/app/next/default/#/cockpit");
    assertRedirect("/fasti/app/tasklist/default/", "/fasti/app/next/default/#/tasklist");
    assertRedirect("/fasti/app/admin/default/", "/fasti/app/next/default/#/admin");
    assertRedirect("/fasti/app/welcome/default/", "/fasti/app/next/default/#/");
  }

  @Test
  public void shouldKeepTheEngineOfABookmark() throws Exception {
    assertRedirect("/fasti/app/cockpit/other-engine/", "/fasti/app/next/other-engine/#/cockpit");
    assertRedirect("/fasti/app/admin/my.engine/setup/", "/fasti/app/next/my.engine/#/admin");
    assertRedirect("/fasti/app/tasklist/default/index.html", "/fasti/app/next/default/#/tasklist");
  }

  @Test
  public void shouldLeaveTheEngineToTheNewWebUiWithoutOne() throws Exception {
    assertRedirect("/fasti/app/cockpit/", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/cockpit", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/tasklist/index.html", "/fasti/app/next/#/tasklist");
  }

  @Test
  public void shouldNotTakeDotSegmentsOrMarkupAsEngine() throws Exception {
    assertRedirect("/fasti/app/cockpit/../", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/cockpit/a\"b/", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/cockpit/a b/", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/cockpit/a%22b/", "/fasti/app/next/#/cockpit");
    assertRedirect("/fasti/app/cockpit/a\r\nX-Injected: 1/", "/fasti/app/next/#/cockpit");
  }

  @Test
  public void shouldKeepTheContextPath() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ctx/fasti/app/cockpit/default/");
    request.setContextPath("/ctx");
    request.setServletPath("/fasti/app/cockpit/default/");

    MockHttpServletResponse response = perform(request);

    assertThat(response.getStatus()).isEqualTo(302);
    assertThat(response.getHeader("Location")).isEqualTo("/ctx/fasti/app/next/default/#/cockpit");
  }

  @Test
  public void shouldNotServeTheFilesOfTheClassicApplications() throws Exception {
    assertNotFound("GET", "/fasti/app/cockpit/camunda-cockpit-bootstrap.js");
    assertNotFound("GET", "/fasti/app/admin/scripts/config.js");
    assertNotFound("GET", "/fasti/app/welcome/locales/en.json");
    assertNotFound("GET", "/fasti/app/tasklist/camunda-tasklist-bootstrap.js");
    assertNotFound("GET", "/fasti/app/tasklist/camunda-tasklist-bootstrap.css");
    assertNotFound("GET", "/fasti/app/tasklist/styles/user-styles.css");
    assertNotFound("GET", "/fasti/app/tasklist/locales/en.json");
  }

  @Test
  public void shouldNotServeTheStaticFilesOfClassicPlugins() throws Exception {
    assertNotFound("GET", "/fasti/api/cockpit/plugin/base/static/app/plugin.js");
    assertNotFound("GET", "/fasti/api/tasklist/plugin/tasklist-plugins/static/app/plugin.css");
    assertNotFound("GET", "/fasti/api/admin/plugin/adminPlugins/static");
  }

  @Test
  public void shouldMatchTheNormalizedPathCaseInsensitively() throws Exception {
    // the container decodes the path and drops path parameters before the resource handlers
    // see it; the filter looks at the same path
    MockHttpServletRequest encoded = new MockHttpServletRequest("GET",
        "/fasti/app/%63ockpit;x=1/camunda-cockpit-bootstrap.js");
    encoded.setServletPath("/fasti/app/cockpit/camunda-cockpit-bootstrap.js");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(encoded, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(404);

    assertNotFound("GET", "/fasti/app/Cockpit/camunda-cockpit-bootstrap.js");
    assertNotFound("GET", "/fasti/app//cockpit/camunda-cockpit-bootstrap.js");
    assertNotFound("GET", "/FASTI/app/admin/camunda-admin-bootstrap.js");
    assertNotFound("GET", "/fasti/app/tasklist/Camunda-Tasklist-Bootstrap.JS");
    assertNotFound("GET", "/fasti/API/cockpit/plugin/base/static/app/plugin.js");
    assertRedirect("/fasti/app/TaskList/Default/", "/fasti/app/next/Default/#/tasklist");
  }

  @Test
  public void shouldNotServeTheClassicFilesBelowTheWebjarPath() throws Exception {
    assertNotFound("GET", "/webjars/camunda/app/cockpit/camunda-cockpit-bootstrap.js");
    assertNotFound("GET", "/webjars/camunda/app/tasklist/index.html");
    assertNotFound("GET", "/webjars/Camunda/App/Admin/scripts/config.js");
    assertPassed("GET", "/webjars/camunda/app/next/assets/index-C-_R5Ddf.js");
    assertPassed("GET", "/webjars/other/1.0/other.js");
  }

  @Test
  public void shouldAnswerOtherMethodsWithNotFound() throws Exception {
    assertNotFound("POST", "/fasti/app/cockpit/default/");
    assertNotFound("PUT", "/fasti/app/tasklist/scripts/config.js");
  }

  @Test
  public void shouldServeTheTasklistScriptsOfTheFormFrame() throws Exception {
    assertPassed("GET", "/fasti/app/tasklist/scripts/config.js");
    assertPassed("HEAD", "/fasti/app/tasklist/scripts/config.js");
    assertPassed("GET", "/fasti/app/tasklist/custom/my-form-helpers.js");
    assertPassed("GET", "/fasti/app/tasklist/custom/camunda-tasklist-helpers.js");
  }

  @Test
  public void shouldPassTheNewWebUiAndTheRestResources() throws Exception {
    assertPassed("GET", "/fasti/app/next/default/");
    assertPassed("GET", "/fasti/app/next/assets/index-C-_R5Ddf.js");
    assertPassed("GET", "/fasti/app/forms/default/");
    assertPassed("GET", "/fasti/app/forms/fasti-form-frame.js");
    assertPassed("GET", "/fasti/app/");
    assertPassed("GET", "/fasti/app/cockpitx/default/");
    assertPassed("GET", "/fasti/api/cockpit/plugin/base/default/process-definition/statistics");
    assertPassed("POST", "/fasti/api/admin/auth/user/default/login/cockpit");
    assertPassed("GET", "/fasti/api/engine/engine/default/task");
    assertPassed("GET", "/fasti/lib/deps.js");
  }

  @Test
  public void shouldPassRequestsOutsideTheApplicationPath() throws Exception {
    assertPassed("GET", "/camunda/app/cockpit/default/");
    assertPassed("GET", "/other/app/cockpit/default/");
  }

  protected void assertRedirect(String uri, String location) throws Exception {
    for (String method : new String[] { "GET", "HEAD" }) {
      MockHttpServletResponse response = perform(request(method, uri));

      assertThat(response.getStatus()).as(method + " " + uri).isEqualTo(302);
      assertThat(response.getHeader("Location")).as(method + " " + uri).isEqualTo(location);
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
  }

  protected void assertNotFound(String method, String uri) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request(method, uri), response, chain);

    assertThat(response.getStatus()).as(method + " " + uri).isEqualTo(404);
    assertThat(chain.getRequest()).as(method + " " + uri).isNull();
  }

  protected void assertPassed(String method, String uri) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request(method, uri), response, chain);

    assertThat(chain.getRequest()).as(method + " " + uri).isNotNull();
    assertThat(response.getStatus()).as(method + " " + uri).isEqualTo(200);
  }

  protected MockHttpServletResponse perform(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  /** a request without context path, as Spring Boot maps it: the servlet path is the path */
  protected MockHttpServletRequest request(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setServletPath(uri);
    return request;
  }
}
