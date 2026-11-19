/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.apppath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.camunda.bpm.spring.boot.starter.webapp.WebappTestApp;
import org.camunda.bpm.spring.boot.starter.webapp.filter.util.HttpClientRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * With fasti.webapp.classic.enabled=false (the default of the container image), the classic
 * web applications are not served; their pages and bookmarks lead to the new web UI.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { WebappTestApp.class },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "fasti.webapp.classic.enabled=false",
      // ignored with a warning: the classic Welcome application is not served
      "fasti.webapp.default-app=welcome"
    })
public class ClassicWebappsDisabledIT {

  @Rule
  public HttpClientRule httpClientRule = new HttpClientRule();

  @LocalServerPort
  public int port;

  @Test
  public void shouldRedirectClassicPageToTheNewWebUi() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/fasti/app/cockpit/default/"));

    assertThat(con.getResponseCode()).isEqualTo(302);
    assertThat(con.getHeaderField("Location")).isEqualTo("/fasti/app/next/default/#/cockpit");
    // the security headers are set on the redirect as well
    assertThat(con.getHeaderField("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  public void shouldLeadLegacyBookmarksToTheNewWebUi() throws IOException {
    HttpURLConnection con = new HttpClientRule().followRedirects(true)
        .performRequest(url("/camunda/app/tasklist/default/"));

    assertThat(con.getResponseCode()).isEqualTo(200);
    assertThat(con.getURL().getPath()).startsWith("/fasti/app/next/default/");
  }

  @Test
  public void shouldSendTheRootToTheNewWebUi() throws IOException {
    HttpURLConnection con = httpClientRule.performRequest(url("/fasti/app/"));

    assertThat(con.getResponseCode()).isEqualTo(302);
    assertThat(con.getHeaderField("Location")).endsWith("/fasti/app/next/default/");
  }

  @Test
  public void shouldNotServeClassicFiles() throws IOException {
    assertThat(httpClientRule.performRequest(url("/fasti/app/cockpit/camunda-cockpit-bootstrap.js"))
        .getResponseCode()).isEqualTo(404);
    assertThat(httpClientRule.performRequest(url("/fasti/app/tasklist/camunda-tasklist-bootstrap.js"))
        .getResponseCode()).isEqualTo(404);
  }

  @Test
  public void shouldNotServeClassicFilesBehindEncodingsOrPathParameters() throws IOException {
    String[] paths = {
      "/fasti/app/%63ockpit/camunda-cockpit-bootstrap.js",
      "/fasti/app/cockpit;jsessionid=x/camunda-cockpit-bootstrap.js",
      "/fasti/app//cockpit/camunda-cockpit-bootstrap.js",
      "/fasti/app/cockpit/./camunda-cockpit-bootstrap.js",
      "/fasti/app/next/../cockpit/camunda-cockpit-bootstrap.js",
      "/fasti/app/admin/locales/en.json",
      "/fasti/api/cockpit/plugin/base/static/app/plugin.js",
      "/fasti/api/%63ockpit/plugin/base/static/app/plugin.js",
      "/webjars/camunda/app/cockpit/camunda-cockpit-bootstrap.js",
      "/webjars/camunda/app/tasklist/index.html"
    };
    for (String path : paths) {
      assertThat(httpClientRule.performRequest(url(path)).getResponseCode()).as(path).isIn(400, 404);
    }
    HttpURLConnection head = (HttpURLConnection) new java.net.URL(
        url("/fasti/app/cockpit/camunda-cockpit-bootstrap.js")).openConnection();
    head.setRequestMethod("HEAD");
    assertThat(head.getResponseCode()).isEqualTo(404);
  }

  @Test
  public void shouldKeepTheRestResourcesOfTheNewWebUi() throws IOException {
    // answered by the REST resource (here: not authenticated), not by the filter
    HttpURLConnection con = httpClientRule.performRequest(
        url("/fasti/api/cockpit/plugin/base/default/process-definition/statistics"));
    assertThat(con.getResponseCode()).isIn(200, 401, 403);
    assertThat(httpClientRule.performRequest(url("/fasti/api/admin/auth/user/default"))
        .getResponseCode()).isIn(200, 401, 404);
  }

  @Test
  public void shouldServeTheNewWebUiAndTellItThatClassicAppsAreOff() throws IOException {
    // the new web UI, or its setup page as long as no administrator exists
    HttpClientRule client = new HttpClientRule().followRedirects(true);
    HttpURLConnection page = client.performRequest(url("/fasti/app/next/default/"));

    assertThat(page.getResponseCode()).isEqualTo(200);
    assertThat(page.getURL().getPath()).startsWith("/fasti/app/next/default/");
    assertThat(client.getContent())
        .contains("<meta name=\"fasti-classic-webapps\" content=\"false\" />");
  }

  @Test
  public void shouldServeTheTasklistConfigurationToTheFormFrame() throws IOException {
    assertThat(httpClientRule.performRequest(url("/fasti/app/tasklist/scripts/config.js"))
        .getResponseCode()).isEqualTo(200);
  }

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }
}
