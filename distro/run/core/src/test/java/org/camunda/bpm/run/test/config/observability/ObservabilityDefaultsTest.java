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
package org.camunda.bpm.run.test.config.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.camunda.bpm.run.test.AbstractRestTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Without the Fasti switches nothing of the metrics or tracing is reachable, even when the
 * configuration exposes every actuator endpoint (as some existing Run configurations do).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "management.endpoints.web.exposure.include=*")
public class ObservabilityDefaultsTest extends AbstractRestTest {

  @Autowired
  protected ApplicationContext context;

  @Test
  public void shouldNotServePrometheusMetrics() {
    assertThat(testRestTemplate.getForEntity("/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldNotTrace() {
    // the OpenTelemetry SDK is disabled: no tracer provider, span processor or exporter
    assertThat(context.getBeansOfType(SdkTracerProvider.class)).isEmpty();
    assertThat(context.getBeansOfType(SpanProcessor.class)).isEmpty();
    assertThat(context.getBeansOfType(SpanExporter.class)).isEmpty();
  }

  @Test
  public void shouldPublishEngineMetricsButNotObserveCommands() {
    assertThat(context.containsBean("fastiEngineMetricsPlugin")).isTrue();
    assertThat(context.containsBean("fastiEngineObservationPlugin")).isFalse();
  }
}
