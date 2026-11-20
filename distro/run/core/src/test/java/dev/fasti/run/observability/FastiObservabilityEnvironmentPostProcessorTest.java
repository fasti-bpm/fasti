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
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

public class FastiObservabilityEnvironmentPostProcessorTest {

  protected MockEnvironment process(String... properties) {
    MockEnvironment environment = new MockEnvironment();
    for (int i = 0; i < properties.length; i += 2) {
      environment.setProperty(properties[i], properties[i + 1]);
    }
    new FastiObservabilityEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
    return environment;
  }

  @Test
  public void shouldKeepEverythingOffByDefault() {
    MockEnvironment environment = process();

    assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    assertThat(environment.getProperty("management.endpoint.prometheus.access")).isEqualTo("none");
    assertThat(environment.getProperty("management.prometheus.metrics.export.enabled")).isEqualTo("false");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isNull();
    assertThat(environment.getProperty("management.opentelemetry.enabled")).isEqualTo("false");
    assertThat(environment.getProperty("management.tracing.export.enabled")).isEqualTo("false");
  }

  @Test
  public void shouldSwitchToJsonLogs() {
    MockEnvironment environment = process("fasti.logging.format", "JSON");

    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("ecs");
    assertThat(environment.getProperty("logging.structured.ecs.service.name")).isEqualTo("fasti-bpm");
  }

  @Test
  public void shouldKeepExplicitLogSettings() {
    MockEnvironment environment = process("fasti.logging.format", "json",
        "logging.structured.format.console", "logstash",
        "spring.application.name", "orders");

    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    assertThat(environment.getProperty("logging.structured.format.file")).isEqualTo("ecs");
    // Spring Boot takes the service name from spring.application.name
    assertThat(environment.getProperty("logging.structured.ecs.service.name")).isNull();
  }

  @Test
  public void shouldAcceptTheFormatsOfSpringBoot() {
    assertThat(FastiObservabilityEnvironmentPostProcessor.structuredLogFormat(null)).isNull();
    assertThat(FastiObservabilityEnvironmentPostProcessor.structuredLogFormat(" text ")).isNull();
    assertThat(FastiObservabilityEnvironmentPostProcessor.structuredLogFormat("ecs")).isEqualTo("ecs");
    assertThat(FastiObservabilityEnvironmentPostProcessor.structuredLogFormat("Logstash")).isEqualTo("logstash");
    assertThat(FastiObservabilityEnvironmentPostProcessor.structuredLogFormat("gelf")).isEqualTo("gelf");
    assertThatThrownBy(() -> process("fasti.logging.format", "xml"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fasti.logging.format");
  }

  @Test
  public void shouldExposePrometheusNextToHealth() {
    MockEnvironment environment = process("fasti.metrics.prometheus.enabled", "true");

    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
    assertThat(environment.getProperty("management.endpoint.prometheus.access")).isEqualTo("read-only");
    assertThat(environment.getProperty("management.prometheus.metrics.export.enabled")).isNull();
  }

  @Test
  public void shouldAddPrometheusToTheConfiguredExposure() {
    MockEnvironment environment = process("fasti.metrics.prometheus.enabled", "true",
        "management.endpoints.web.exposure.include", "health, info");

    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info,prometheus");

    // a YAML list
    environment = process("fasti.metrics.prometheus.enabled", "true",
        "management.endpoints.web.exposure.include[0]", "health",
        "management.endpoints.web.exposure.include[1]", "info");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info,prometheus");

    environment = process("fasti.metrics.prometheus.enabled", "true",
        "management.endpoints.web.exposure.include", "*");
    assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("*");
  }

  @Test
  public void shouldSwitchTracingOn() {
    MockEnvironment environment = process("fasti.tracing.enabled", "true");

    assertThat(environment.getProperty("management.opentelemetry.enabled")).isNull();
    assertThat(environment.getProperty("management.tracing.export.enabled")).isNull();
    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
    assertThat(environment.getProperty("management.logging.export.otlp.enabled")).isEqualTo("false");
  }

  @Test
  public void shouldKeepExplicitTracingSettings() {
    MockEnvironment environment = process("fasti.tracing.enabled", "true",
        "management.tracing.sampling.probability", "0.2");

    assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.2");
  }
}
