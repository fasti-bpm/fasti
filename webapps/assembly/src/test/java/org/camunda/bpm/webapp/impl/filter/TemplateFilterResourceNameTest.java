/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.camunda.bpm.webapp.impl.engine.ProcessEnginesFilter;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/**
 * The template filter derives names of web resources from the raw request URI, which the
 * container does not normalize. A name that could leave the folder it names must never be looked
 * up, and a page below <code>/app</code> must never be forwarded to a resource outside of it.
 */
public class TemplateFilterResourceNameTest {

  protected static final String CONTEXT_PATH = "/fasti";

  /** a resource that must stay unreachable through the template filter */
  protected static final String PROTECTED_RESOURCE = "/WEB-INF/secret.html";
  protected static final String TEMPLATE = "/app/cockpit/pages/dashboard.html";

  protected ProcessEnginesFilter filter;
  protected List<String> lookedUp;
  protected MockServletContext servletContext;

  @Before
  public void setUp() throws ServletException {
    lookedUp = new ArrayList<>();

    // resolves names like a container does: normalized, relative to the root of the web resources
    servletContext = new MockServletContext() {
      @Override
      public URL getResource(String path) throws MalformedURLException {
        lookedUp.add(path);
        return exists(path) ? new URL("file:" + normalize(path)) : null;
      }

      @Override
      public InputStream getResourceAsStream(String path) {
        lookedUp.add(path);
        return exists(path) ? new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)) : null;
      }

      protected boolean exists(String path) {
        String normalized = normalize(path);
        return PROTECTED_RESOURCE.equals(normalized) || TEMPLATE.equals(normalized);
      }

      protected String normalize(String path) {
        return URI.create(path).normalize().getPath();
      }
    };
    ServletContextUtil.setAppPath("", servletContext);

    filter = new ProcessEnginesFilter() {
      @Override
      protected void setWebappInTelemetry(String engineName, String appName, ServletContext servletContext) {
        // no engine is available in this test
      }
    };
    filter.init(new MockFilterConfig(servletContext));
  }

  @Test
  public void shouldForwardTemplateOfApp() throws Exception {
    // given
    MockHttpServletRequest request = request("/app/cockpit/default/pages/dashboard.html");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getForwardedUrl()).isEqualTo(TEMPLATE);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldNotForwardTemplateOutsideOfApp() throws Exception {
    // given a raw URI that stays below /app, while the name without the engine leaves it
    MockHttpServletRequest request = request("/app/cockpit/default/../../WEB-INF/secret.html");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // when
    filter.doFilter(request, response, chain);

    // then the container handles the request as usual
    assertThat(response.getForwardedUrl()).isNull();
    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(lookedUp).isEmpty();
  }

  @Test
  public void shouldNotForwardTemplateWithBackslashOrEncodedCharacters() throws Exception {
    String[] uris = {
      "/app/cockpit/default/..\\..\\WEB-INF\\secret.html",
      "/app/cockpit/default/%2e%2e/%2e%2e/WEB-INF/secret.html",
    };

    for (String uri : uris) {
      // given
      MockHttpServletRequest request = request(uri);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(response.getForwardedUrl()).as(uri).isNull();
      assertThat(chain.getRequest()).as(uri).isSameAs(request);
    }
    assertThat(lookedUp).isEmpty();
  }

  @Test
  public void shouldNotLookUpResourceNamesThatLeaveTheirFolder() {
    String[] names = {
      "/app/../WEB-INF/secret.html",
      "/app/cockpit/..",
      "/app\\..\\WEB-INF\\secret.html",
      "/app/%2e%2e/WEB-INF/secret.html",
      "/app/cockpit/\u0000.html",
      null
    };

    for (String name : names) {
      assertThat(AbstractTemplateFilter.isTraversalFree(name)).as(name).isFalse();
      assertThat(filter.hasWebResource(name)).as(name).isFalse();
      assertThatThrownBy(() -> filter.getWebResourceContents(name)).as(name)
        .isInstanceOf(FileNotFoundException.class);
    }
    assertThat(lookedUp).isEmpty();
  }

  @Test
  public void shouldLookUpPlainResourceNames() throws Exception {
    assertThat(AbstractTemplateFilter.isTraversalFree("/app/cockpit/index.html")).isTrue();
    assertThat(filter.hasWebResource(TEMPLATE)).isTrue();
    assertThat(filter.getWebResourceContents(TEMPLATE)).isEqualTo("content\n");
  }

  @Test
  public void shouldReportMissingResource() {
    assertThatThrownBy(() -> filter.getWebResourceContents("/app/unknown/index.html"))
      .isInstanceOf(FileNotFoundException.class);
  }

  protected MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", CONTEXT_PATH + path);
    request.setContextPath(CONTEXT_PATH);
    return request;
  }

}
