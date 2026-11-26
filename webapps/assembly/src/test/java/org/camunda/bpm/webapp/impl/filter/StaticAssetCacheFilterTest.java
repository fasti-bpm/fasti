/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.camunda.bpm.engine.impl.util.ProductPropertiesUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class StaticAssetCacheFilterTest {

  protected static final String VERSION = "1.3.1";

  protected StaticAssetCacheFilter filter;

  @Before
  public void setUp() throws Exception {
    filter = new StaticAssetCacheFilter();
    filter.versionQuery = StaticAssetCacheFilter.versionQuery(VERSION);
  }

  protected MockHttpServletResponse get(String path, String query) throws Exception {
    return get(path, query, new MockFilterChain());
  }

  protected MockHttpServletResponse get(String path, String query, MockFilterChain chain) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    request.setQueryString(query);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  @Test
  public void shouldCacheBundlesOfThisReleaseForAYear() throws Exception {
    assertThat(get("/lib/deps.js", "bust=1.3.1").getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
    assertThat(get("/app/forms/fasti-form-frame.js", "bust=1.3.1").getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
    assertThat(get("/app/tasklist/camunda-tasklist-bootstrap.css", "bust=1.3.1").getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
    assertThat(get("/assets/bpmn.woff2", "bust=1.3.1").getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
  }

  @Test
  public void shouldCacheHashedAssetsOfTheNewWebUi() throws Exception {
    assertThat(get("/app/next/assets/index-bpn4tOkO.js", null).getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
  }

  @Test
  public void shouldHonourTheApplicationPath() throws Exception {
    MockFilterConfig config = new MockFilterConfig();
    config.addInitParameter(StaticAssetCacheFilter.APPLICATION_PATH_PARAM, "/fasti");
    filter.init(config);

    assertThat(get("/fasti/lib/deps.js", "bust=1.3.1").getHeader("Cache-Control"))
        .isEqualTo(StaticAssetCacheFilter.IMMUTABLE);
    assertThat(get("/other/lib/deps.js", "bust=1.3.1").getHeader("Cache-Control")).isNull();
  }

  @Test
  public void shouldLeaveRequestsWithoutTheVersionOfThisRelease() throws Exception {
    assertThat(get("/lib/deps.js", null).getHeader("Cache-Control")).isNull();
    assertThat(get("/lib/deps.js", "bust=1.3.0").getHeader("Cache-Control")).isNull();
    assertThat(get("/lib/deps.js", "bust=1.3.1&x=1").getHeader("Cache-Control")).isNull();
    assertThat(get("/lib/deps.js", "bust=1764163200000").getHeader("Cache-Control")).isNull();
  }

  @Test
  public void shouldLeaveFilesOperatorsEdit() throws Exception {
    assertThat(get("/app/tasklist/scripts/config.js", "bust=1.3.1").getHeader("Cache-Control")).isNull();
    assertThat(get("/app/tasklist/styles/user-styles.css", "bust=1.3.1").getHeader("Cache-Control")).isNull();
    assertThat(get("/app/tasklist/locales/vi.json", "bust=1.3.1").getHeader("Cache-Control")).isNull();
    assertThat(get("/app/cockpit/scripts/custom.js", "bust=1.3.1").getHeader("Cache-Control")).isNull();
    assertThat(get("/app/tasklist/index.html", "bust=1.3.1").getHeader("Cache-Control")).isNull();
  }

  @Test
  public void shouldNotCacheOtherPaths() throws Exception {
    assertThat(get("/lib/../app/tasklist/scripts/config.js", "bust=1.3.1").getHeader("Cache-Control")).isNull();
    assertThat(get("/api/cockpit/plugin/x/static/app/plugin.js", "bust=1.3.1").getHeader("Cache-Control"))
        .isNull();
  }

  @Test
  public void shouldNotCacheWhenTheReleaseIsUnknown() throws Exception {
    filter.versionQuery = StaticAssetCacheFilter.versionQuery(null);

    assertThat(get("/lib/deps.js", "bust=null").getHeader("Cache-Control")).isNull();
  }

  @Test
  public void shouldRevalidatePagesOfTheNewWebUi() throws Exception {
    assertThat(get("/app/next/default/", null).getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldNotCacheMissingFiles() throws Exception {
    MockFilterChain chain = new MockFilterChain() {
      @Override
      public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response) {
        try {
          ((HttpServletResponse) response).sendError(404);
        } catch (java.io.IOException e) {
          throw new IllegalStateException(e);
        }
      }
    };

    MockHttpServletResponse response = get("/lib/gone.js", "bust=1.3.1", chain);

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
  }

  @Test
  public void shouldLeavePostAlone() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lib/deps.js");
    request.setServletPath("/lib/deps.js");
    request.setQueryString("bust=1.3.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("Cache-Control")).isNull();
  }

  /**
   * The bundles of the classic applications name the version of the frontend build
   * (webapps/frontend/package.json) in their query string; it has to be the version of the
   * release, or they are never cached for long.
   */
  @Test
  public void shouldBuildTheClassicFrontendWithTheVersionOfTheRelease() throws Exception {
    String packageJson = new String(Files.readAllBytes(new File("../frontend/package.json").toPath()),
        StandardCharsets.UTF_8);
    Matcher version = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(packageJson);

    assertThat(version.find()).isTrue();
    assertThat(version.group(1)).isEqualTo(ProductPropertiesUtil.getProductVersion());
  }
}
