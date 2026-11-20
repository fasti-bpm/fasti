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
        .withProperty("fasti.metrics.prometheus.token", "secret"))).isNull();
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("management.server.port", "9464"))).isNull();
    assertThat(FastiPrometheusAccessGuard.check(new MockEnvironment()
        .withProperty("fasti.metrics.prometheus.enabled", "true")
        .withProperty("server.port", "9464")
        .withProperty("management.server.port", "8080"))).isNull();
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
