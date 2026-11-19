/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter.headersec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.ATTR_CSP_FILTER_NONCE;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_DEFAULT_VALUE;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_FORM_FRAME_DEFAULT_VALUE;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_NAME;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_NEXT_APP_DEFAULT_VALUE;
import static org.camunda.bpm.webapp.impl.security.filter.headersec.provider.impl.ContentSecurityPolicyProvider.HEADER_NONCE_PLACEHOLDER;

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
 * The new web UI is served with a stricter policy than the classic
 * applications: no eval and no inline scripts without the nonce.
 */
public class ContentSecurityPolicyNextAppTest {

  protected MockServletContext servletContext;

  @Before
  public void setUp() {
    servletContext = new MockServletContext();
  }

  @Test
  public void shouldServeStrictPolicyForNewWebUi() throws Exception {
    HttpHeaderSecurityFilter filter = filter(null);

    MockHttpServletRequest request = request("/fasti", "/app/next/default/");
    String header = perform(filter, request);

    assertThat(header).matches(pattern(HEADER_NEXT_APP_DEFAULT_VALUE));
    assertThat(header).contains("'nonce-" + request.getAttribute(ATTR_CSP_FILTER_NONCE) + "'");
    assertThat(scriptSrc(header)).contains("'strict-dynamic'")
        .doesNotContain("'unsafe-eval'")
        .doesNotContain("'unsafe-inline'")
        .doesNotContain("https:");
  }

  @Test
  public void shouldServeStrictPolicyForAssetsOfNewWebUi() throws Exception {
    String header = perform(filter(null), request("", "/app/next/assets/index.js"));

    assertThat(header).matches(pattern(HEADER_NEXT_APP_DEFAULT_VALUE));
  }

  @Test
  public void shouldKeepPolicyOfClassicApplications() throws Exception {
    HttpHeaderSecurityFilter filter = filter(null);

    assertThat(perform(filter, request("/fasti", "/app/tasklist/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
    assertThat(perform(filter, request("/fasti", "/app/welcome/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
    assertThat(perform(filter, request("", "/api/engine/engine/default/task"))).matches(pattern(HEADER_DEFAULT_VALUE));
  }

  @Test
  public void shouldNotMistakeSimilarPathsForNewWebUi() throws Exception {
    HttpHeaderSecurityFilter filter = filter(null);

    assertThat(perform(filter, request("", "/app/nextgen/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
    assertThat(perform(filter, request("/app/next", "/app/cockpit/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
  }

  @Test
  public void shouldRespectApplicationPath() throws Exception {
    ServletContextUtil.setAppPath("/fasti", servletContext);
    HttpHeaderSecurityFilter filter = filter(null);

    assertThat(perform(filter, request("", "/fasti/app/next/default/"))).matches(pattern(HEADER_NEXT_APP_DEFAULT_VALUE));
    assertThat(perform(filter, request("", "/fasti/app/admin/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
  }

  @Test
  public void shouldApplyConfiguredPolicyToEveryApplication() throws Exception {
    HttpHeaderSecurityFilter filter = filter("default-src 'self'");

    assertThat(perform(filter, request("", "/app/next/default/"))).isEqualTo("default-src 'self'");
    assertThat(perform(filter, request("", "/app/cockpit/default/"))).isEqualTo("default-src 'self'");
  }

  @Test
  public void shouldServeClassicPolicyFramedBySameOriginForFormFrameInSameOriginMode() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SAME_ORIGIN, null, servletContext);
    HttpHeaderSecurityFilter filter = filter(null);

    MockHttpServletRequest request = request("/fasti", "/app/forms/default/");
    String header = perform(filter, request);

    assertThat(header).matches(pattern(HEADER_FORM_FRAME_DEFAULT_VALUE));
    assertThat(header).contains("'nonce-" + request.getAttribute(ATTR_CSP_FILTER_NONCE) + "'");
    assertThat(header).contains("frame-ancestors 'self'").doesNotContain("frame-ancestors 'none'");
    // embedded forms keep what the classic Tasklist allowed them
    assertThat(scriptSrc(header)).isEqualTo(scriptSrc(HEADER_DEFAULT_VALUE).replace(HEADER_NONCE_PLACEHOLDER,
        "'nonce-" + request.getAttribute(ATTR_CSP_FILTER_NONCE) + "'"));
    assertThat(directives(header)).containsAll(directives(HEADER_DEFAULT_VALUE.replace(HEADER_NONCE_PLACEHOLDER,
        "'nonce-" + request.getAttribute(ATTR_CSP_FILTER_NONCE) + "'")).stream()
        .filter(directive -> !directive.startsWith("frame-ancestors")).toList());
  }

  @Test
  public void shouldServeFormFramePolicyForAssetsOfFormFrame() throws Exception {
    assertThat(perform(filter(null), request("", "/app/forms/fasti-form-frame.js")))
        .matches(pattern(sandboxed(HEADER_FORM_FRAME_DEFAULT_VALUE)));
  }

  @Test
  public void shouldSandboxFormFrameWithoutSameOriginByDefault() throws Exception {
    HttpHeaderSecurityFilter filter = filter(null);

    MockHttpServletRequest request = request("/fasti", "/app/forms/default/");
    String header = perform(filter, request);

    assertThat(header).matches(pattern(sandboxed(HEADER_FORM_FRAME_DEFAULT_VALUE)));
    assertThat(sandbox(header)).isEqualTo("sandbox allow-forms allow-scripts allow-popups allow-downloads");
    assertThat(header).doesNotContain("allow-same-origin").contains("frame-ancestors 'self'");
  }

  @Test
  public void shouldAddSandboxToConfiguredPolicyWithoutOneInSandboxedMode() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SANDBOXED, null, servletContext);
    HttpHeaderSecurityFilter filter = filter("default-src 'self'");

    assertThat(perform(filter, request("", "/app/forms/default/")))
        .isEqualTo("default-src 'self';frame-ancestors 'self';sandbox allow-forms allow-scripts allow-popups allow-downloads");
  }

  @Test
  public void shouldStripSameOriginFromConfiguredSandboxInSandboxedMode() throws Exception {
    HttpHeaderSecurityFilter filter = filter("default-src 'self'; sandbox allow-scripts ALLOW-SAME-ORIGIN allow-forms");

    assertThat(perform(filter, request("", "/app/forms/default/")))
        .isEqualTo("default-src 'self';sandbox allow-scripts allow-forms;frame-ancestors 'self'");
    // the other applications keep the configured policy
    assertThat(perform(filter, request("", "/app/tasklist/default/")))
        .isEqualTo("default-src 'self'; sandbox allow-scripts ALLOW-SAME-ORIGIN allow-forms");
  }

  @Test
  public void shouldLetAnyOriginFrameFormFrameInSeparateOriginMode() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SEPARATE_ORIGIN, "https://forms.example.com", servletContext);
    HttpHeaderSecurityFilter filter = filter(null);

    String header = perform(filter, request("", "/app/forms/default/"));

    assertThat(header).contains("frame-ancestors *").doesNotContain("frame-ancestors 'self'");
    assertThat(sandbox(header)).contains("allow-same-origin");
  }

  @Test
  public void shouldLetNewWebUiFrameFormsOriginInSeparateOriginMode() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SEPARATE_ORIGIN, "https://forms.example.com", servletContext);

    assertThat(perform(filter(null), request("", "/app/next/default/")))
        .endsWith(";frame-src 'self' https://forms.example.com");
    assertThat(perform(filter("default-src 'self'; frame-src 'self'"), request("", "/app/next/default/")))
        .isEqualTo("default-src 'self';frame-src 'self' https://forms.example.com");
  }

  @Test
  public void shouldNotLetNewWebUiFrameOtherOriginsInOtherModes() throws Exception {
    assertThat(perform(filter(null), request("", "/app/next/default/"))).doesNotContain("frame-src");

    // an origin configured without the separate origin mode is ignored
    ServletContextUtil.setFormsIsolation(FormsIsolation.SAME_ORIGIN, "https://forms.example.com", servletContext);
    assertThat(perform(filter(null), request("", "/app/next/default/"))).doesNotContain("frame-src");
  }

  @Test
  public void shouldNotMistakeSimilarPathsForFormFrame() throws Exception {
    HttpHeaderSecurityFilter filter = filter(null);

    assertThat(perform(filter, request("", "/app/formsx/default/"))).matches(pattern(HEADER_DEFAULT_VALUE));
    assertThat(perform(filter, request("", "/app/tasklist/forms/"))).matches(pattern(HEADER_DEFAULT_VALUE));
  }

  @Test
  public void shouldAllowSameOriginFramingOfFormFrameWithConfiguredPolicy() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SAME_ORIGIN, null, servletContext);
    HttpHeaderSecurityFilter filter = filter("default-src 'self'; FRAME-ANCESTORS 'none'; object-src 'none'");

    assertThat(perform(filter, request("", "/app/forms/default/")))
        .isEqualTo("default-src 'self';object-src 'none';frame-ancestors 'self'");
    assertThat(perform(filter, request("", "/app/tasklist/default/")))
        .isEqualTo("default-src 'self'; FRAME-ANCESTORS 'none'; object-src 'none'");
  }

  @Test
  public void shouldRestrictFramingOfFormFrameToSameOrigin() throws Exception {
    ServletContextUtil.setFormsIsolation(FormsIsolation.SAME_ORIGIN, null, servletContext);
    HttpHeaderSecurityFilter filter = filter("default-src 'self'");

    assertThat(perform(filter, request("", "/app/forms/default/")))
        .isEqualTo("default-src 'self';frame-ancestors 'self'");
  }

  /** the form frame policy of the sandboxed mode */
  protected static String sandboxed(String policy) {
    return policy.replace("allow-same-origin ", "");
  }

  protected static String sandbox(String header) {
    for (String directive : header.split(";")) {
      if (directive.trim().startsWith("sandbox")) {
        return directive.trim();
      }
    }
    return "";
  }

  protected static java.util.List<String> directives(String header) {
    return java.util.Arrays.stream(header.split(";")).map(String::trim).filter(d -> !d.isEmpty()).toList();
  }

  protected HttpHeaderSecurityFilter filter(String value) {
    MockFilterConfig config = new MockFilterConfig(servletContext);
    if (value != null) {
      config.addInitParameter("contentSecurityPolicyValue", value);
    }
    HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
    filter.init(config);
    return filter;
  }

  protected MockHttpServletRequest request(String contextPath, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(servletContext, "GET", contextPath + path);
    request.setContextPath(contextPath);
    return request;
  }

  protected String perform(HttpHeaderSecurityFilter filter, MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getHeader(HEADER_NAME);
  }

  protected static String pattern(String policy) {
    return policy.replace(HEADER_NONCE_PLACEHOLDER, "'nonce-([-_a-zA-Z\\d]*)'");
  }

  protected static String scriptSrc(String header) {
    for (String directive : header.split(";")) {
      if (directive.trim().startsWith("script-src")) {
        return directive;
      }
    }
    return "";
  }

}
