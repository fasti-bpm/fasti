/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The new web UI is located in <code>/app/next</code> of the web resources.
 * Its index page is served like the index pages of the classic apps: below
 * the name of an engine and with the placeholders for the application root
 * and the nonce of the content security policy replaced. Everything else in
 * that folder is a static file that the filter does not touch.
 */
@RunWith(Parameterized.class)
public class NextAppIndexPageTest {

  protected static final String CONTEXT_PATH = "/fasti";
  protected static final String NONCE = "dGVzdC1ub25jZQ";

  /** what the build of the new web UI produces, reduced to the placeholders */
  protected static final String NEXT_INDEX_PAGE = "<!doctype html>\n"
      + "<html>\n"
      + "<head>\n"
      + "<meta name=\"fasti-app-root\" content=\"$APP_ROOT\" />\n"
      + "<meta property=\"csp-nonce\" $CSP_NONCE />\n"
      + "<script $CSP_NONCE type=\"module\" src=\"$APP_ROOT/app/next/assets/index.js\"></script>\n"
      + "<link $CSP_NONCE rel=\"modulepreload\" href=\"$APP_ROOT/app/next/assets/ui.js\">\n"
      + "</head>\n"
      + "<body><div id=\"root\"></div></body>\n"
      + "</html>\n";

  @Parameters(name = "application path: \"{0}\"")
  public static Collection<String> applicationPaths() {
    return Arrays.asList("", "/my-custom/application/path");
  }

  @Parameter
  public String applicationPath;

  protected ProcessEnginesFilter filter;
  protected MockServletContext servletContext;

  /** the web resources the filter asked for */
  protected List<String> requestedResources;

  @Before
  public void setUp() throws ServletException {
    requestedResources = new ArrayList<>();

    filter = new ProcessEnginesFilter() {
      @Override
      protected String getWebResourceContents(String name) throws IOException {
        requestedResources.add(name);
        return NEXT_INDEX_PAGE;
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

    servletContext = new MockServletContext();
    ServletContextUtil.setAppPath(applicationPath, servletContext);
    filter.init(new MockFilterConfig(servletContext));
  }

  @Test
  public void shouldRedirectToDefaultEngine() throws Exception {
    // given
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    filter.doFilter(request("/app/next/"), response, chain);

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT_PATH + applicationPath + "/app/next/default/");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldServeIndexPageForEngine() throws Exception {
    // given
    MockHttpServletRequest request = request("/app/next/default/");
    request.setAttribute(ContentSecurityPolicyProvider.ATTR_CSP_FILTER_NONCE, NONCE);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    filter.doFilter(request, response, chain);

    // then
    String appRoot = CONTEXT_PATH + applicationPath;
    assertThat(requestedResources).containsExactly("/app/next/index.html");
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).startsWith("text/html");
    assertThat(response.getContentAsString())
      .contains("<meta name=\"fasti-app-root\" content=\"" + appRoot + "\" />")
      .contains("<meta property=\"csp-nonce\" nonce=\"" + NONCE + "\" />")
      .contains("<script nonce=\"" + NONCE + "\" type=\"module\" src=\"" + appRoot + "/app/next/assets/index.js\">")
      .contains("<link nonce=\"" + NONCE + "\" rel=\"modulepreload\" href=\"" + appRoot + "/app/next/assets/ui.js\">")
      .doesNotContain("$");
  }

  @Test
  public void shouldNotRenderNonceAttributeWithoutNonce() throws Exception {
    // given a request that did not pass the header security filter
    MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request("/app/next/default/"), response, new MockFilterChain());

    // then
    assertThat(response.getContentAsString())
      .contains("<script  type=\"module\"")
      .doesNotContain("nonce=")
      .doesNotContain("$");
  }

  @Test
  public void shouldPassStaticFilesDownTheChain() throws Exception {
    String[] staticFiles = {
      "/app/next/assets/index-abc123.js",
      "/app/next/assets/index-abc123.css",
      "/app/next/assets/favicon.svg",
      "/app/next/config.json"
    };

    for (String staticFile : staticFiles) {
      // given
      MockHttpServletRequest request = request(staticFile);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then the file is served by the container
      assertThat(chain.getRequest()).as(staticFile).isSameAs(request);
      assertThat(response.getContentAsString()).as(staticFile).isEmpty();
      assertThat(response.getRedirectedUrl()).as(staticFile).isNull();
    }
    assertThat(requestedResources).isEmpty();
  }

  protected MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET",
        CONTEXT_PATH + applicationPath + path);
    request.setContextPath(CONTEXT_PATH);
    return request;
  }

}
