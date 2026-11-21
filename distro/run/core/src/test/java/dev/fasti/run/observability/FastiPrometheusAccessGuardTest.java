/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package dev.fasti.run.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

public class FastiPrometheusAccessGuardTest {

  @Test
  public void shouldAcceptDisabledMetrics() {
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment())).isNull();
  }

  @Test
  public void shouldRefuseMetricsWithoutTokenOnTheApplicationPort() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("fasti.metrics.prometheus.token");

    environment.setProperty("management.server.port", "8080");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNotNull();

    environment.setProperty("fasti.metrics.prometheus.token", " ");
    assertThatThrownBy(() -> new FastiPrometheusAccessGuard(environment).postProcessBeanFactory(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldAcceptTokenOrManagementPort() {
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("fasti.metrics.prometheus.token", "0123456789abcdef"))).isNull();
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("management.server.port", "9464"))).isNull();
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("server.port", "9464")
        .withProperty("management.server.port", "8080"))).isNull();
  }

  @Test
  public void shouldRefuseShortTokensAndThePlaceholder() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("fasti.metrics.prometheus.token", "a");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("too short").contains("16");

    environment.setProperty("fasti.metrics.prometheus.token", "0123456789abcde");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("too short");
    // white space around the token does not count
    environment.setProperty("fasti.metrics.prometheus.token", "  0123456789abcde \r\n");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("too short");
    environment.setProperty("fasti.metrics.prometheus.token", "0123456789abcdef\r\n");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNull();

    // the value of the commented example in default.yml and the documentation
    environment.setProperty("fasti.metrics.prometheus.token", "change-me");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("placeholder");
    environment.setProperty("fasti.metrics.prometheus.token", "Change-Me");
    assertThat(FastiPrometheusAccessGuard.check(environment)).contains("placeholder");

    // a token that is set must be usable, also next to a management port of its own
    environment.setProperty("management.server.port", "9464");
    assertThatThrownBy(() -> new FastiPrometheusAccessGuard(environment).postProcessBeanFactory(null))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("placeholder");
  }

  @Test
  public void shouldRefuseAnEndpointSwitchedOnWithSpringBootSettingsAlone() {
    // the switch of Fasti is off, but the endpoint is enabled and exposed by hand
    MockEnvironment environment = new MockEnvironment()
        .withProperty("management.endpoint.prometheus.access", "read-only")
        .withProperty("management.prometheus.metrics.export.enabled", "true")
        .withProperty("management.endpoints.web.exposure.include", "health,prometheus");
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(environment)).isTrue();
    assertThat(FastiPrometheusAccessGuard.check(environment))
        .contains("management.* settings").contains("fasti.metrics.prometheus.token");

    // every endpoint exposed
    environment.setProperty("management.endpoints.web.exposure.include", "*");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNotNull();
    environment.setProperty("management.endpoint.prometheus.access", "UNRESTRICTED");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNotNull();

    // with a token or a management port of its own it may be served
    environment.setProperty("fasti.metrics.prometheus.token", "0123456789abcdef");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNull();
    environment.setProperty("fasti.metrics.prometheus.token", "");
    environment.setProperty("management.server.port", "9464");
    assertThat(FastiPrometheusAccessGuard.check(environment)).isNull();
  }

  @Test
  public void shouldAcceptAnEndpointThatIsNotServed() {
    // as the environment post processor leaves it while the switch is off
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoint.prometheus.access", "none")
        .withProperty("management.prometheus.metrics.export.enabled", "false")
        .withProperty("management.endpoints.web.exposure.include", "*"))).isFalse();
    // not exposed
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoint.prometheus.access", "read-only"))).isFalse();
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoints.web.exposure.include", "*")
        .withProperty("management.endpoints.web.exposure.exclude", "prometheus"))).isFalse();
    // no registry, no endpoint
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoints.web.exposure.include", "prometheus")
        .withProperty("management.prometheus.metrics.export.enabled", "false"))).isFalse();
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoints.web.exposure.include", "prometheus")
        .withProperty("management.defaults.metrics.export.enabled", "false"))).isFalse();
    // access capped or off by default
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoints.web.exposure.include", "prometheus")
        .withProperty("management.endpoints.access.max-permitted", "none"))).isFalse();
    assertThat(FastiPrometheusAccessGuard.isEndpointServed(new MockEnvironment()
        .withProperty("management.endpoints.web.exposure.include", "prometheus")
        .withProperty("management.endpoints.access.default", "none"))).isFalse();
    // a token alone switches nothing on
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.token", "a"))).isNull();
  }

  @Test
  public void shouldCompareTokens() {
    assertThat(FastiPrometheusAccessGuard.tokenMatches("secret", "secret")).isTrue();
    assertThat(FastiPrometheusAccessGuard.tokenMatches("secret", "secreT")).isFalse();
    assertThat(FastiPrometheusAccessGuard.tokenMatches("secret", null)).isFalse();
    assertThat(FastiPrometheusAccessGuard.tokenMatches("", "")).isFalse();
    assertThat(FastiPrometheusAccessGuard.tokenMatches(null, "secret")).isFalse();
  }
}
