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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

/**
 * Turns the few observability switches of Fasti BPM Run into the Spring Boot settings behind
 * them. Everything is off unless switched on; settings made explicitly in any configuration
 * source win over the defaults derived here.
 * <ul>
 * <li>{@code fasti.logging.format}: {@code text} (default) or {@code json} (Elastic Common
 * Schema, the same as {@code ecs}), {@code logstash}, {@code gelf}. Sets the structured log
 * format of Spring Boot for the console and the log file.</li>
 * <li>{@code fasti.metrics.prometheus.enabled} (default {@code false}): adds the
 * {@code prometheus} actuator endpoint to the exposed web endpoints. While it is off, the
 * endpoint is not available at all, even if the exposure list names it or uses {@code *}.
 * {@link FastiPrometheusAccessGuard} requires a scrape token or a separate management port.</li>
 * <li>{@code fasti.tracing.enabled} (default {@code false}): keeps the OpenTelemetry SDK and
 * the export of spans off until switched on. Spans are only sent once an OTLP endpoint is
 * configured.</li>
 * </ul>
 */
public class FastiObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String LOGGING_FORMAT = "fasti.logging.format";
  public static final String PROMETHEUS_ENABLED = "fasti.metrics.prometheus.enabled";
  public static final String TRACING_ENABLED = "fasti.tracing.enabled";

  /** Lowest precedence: values that only apply while nothing else sets them. */
  public static final String DEFAULTS_SOURCE = "fastiObservabilityDefaults";
  /** Highest precedence: the exposure list with {@code prometheus} added to the effective one. */
  public static final String OVERRIDES_SOURCE = "fastiObservabilityOverrides";

  public static final String SERVICE_NAME = "fasti-bpm";

  protected static final String EXPOSURE_INCLUDE = "management.endpoints.web.exposure.include";

  /** After the configuration files have been loaded. */
  public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> defaults = new LinkedHashMap<>();
    Map<String, Object> overrides = new LinkedHashMap<>();

    applyLogging(environment, defaults);
    applyMetrics(environment, defaults, overrides);
    applyTracing(environment, defaults);

    MutablePropertySources sources = environment.getPropertySources();
    if (!defaults.isEmpty()) {
      sources.addLast(new MapPropertySource(DEFAULTS_SOURCE, defaults));
    }
    if (!overrides.isEmpty()) {
      sources.addFirst(new MapPropertySource(OVERRIDES_SOURCE, overrides));
    }
  }

  protected void applyLogging(ConfigurableEnvironment environment, Map<String, Object> defaults) {
    String format = structuredLogFormat(environment.getProperty(LOGGING_FORMAT));
    if (format == null) {
      return;
    }
    defaults.put("logging.structured.format.console", format);
    defaults.put("logging.structured.format.file", format);
    if (!StringUtils.hasText(environment.getProperty("spring.application.name"))) {
      defaults.put("logging.structured.ecs.service.name", SERVICE_NAME);
      defaults.put("logging.structured.gelf.host", SERVICE_NAME);
    }
  }

  /**
   * The structured log format of Spring Boot for a value of {@code fasti.logging.format}, or
   * {@code null} for plain text.
   */
  public static String structuredLogFormat(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String format = value.trim().toLowerCase(Locale.ROOT);
    switch (format) {
      case "text":
      case "plain":
        return null;
      case "json":
      case "ecs":
        return "ecs";
      case "logstash":
      case "gelf":
        return format;
      default:
        throw new IllegalStateException(LOGGING_FORMAT + " must be text, json, ecs, logstash or gelf, but is '"
            + value + "'");
    }
  }

  protected void applyMetrics(ConfigurableEnvironment environment, Map<String, Object> defaults,
                              Map<String, Object> overrides) {
    if (!environment.getProperty(PROMETHEUS_ENABLED, Boolean.class, false)) {
      // the endpoint did not exist before Fasti shipped the Prometheus registry; keep it that
      // way for configurations that expose every endpoint
      defaults.put("management.endpoint.prometheus.access", "none");
      defaults.put("management.prometheus.metrics.export.enabled", "false");
      return;
    }
    defaults.put("management.endpoint.prometheus.access", "read-only");
    // bound like Spring Boot does, so that YAML lists (include[0], include[1]) count as well
    Set<String> endpoints = new LinkedHashSet<>();
    Set<String> include = Binder.get(environment)
        .bind(EXPOSURE_INCLUDE, Bindable.setOf(String.class))
        .orElseGet(() -> Set.of("health"));
    include.stream().map(String::trim).filter(StringUtils::hasText).forEach(endpoints::add);
    if (!endpoints.contains("*") && !endpoints.contains("prometheus")) {
      endpoints.add("prometheus");
      overrides.put(EXPOSURE_INCLUDE, String.join(",", endpoints));
    }
  }

  protected void applyTracing(ConfigurableEnvironment environment, Map<String, Object> defaults) {
    if (!environment.getProperty(TRACING_ENABLED, Boolean.class, false)) {
      defaults.put("management.opentelemetry.enabled", "false");
      defaults.put("management.tracing.export.enabled", "false");
      return;
    }
    // every request is traced unless configured otherwise; lower it for high request rates
    defaults.put("management.tracing.sampling.probability", "1.0");
    // spans only; logs and metrics are not sent over OTLP
    defaults.put("management.logging.export.otlp.enabled", "false");
    if (!StringUtils.hasText(environment.getProperty("spring.application.name"))
        && !StringUtils.hasText(System.getenv("OTEL_SERVICE_NAME"))
        && !String.valueOf(System.getenv("OTEL_RESOURCE_ATTRIBUTES")).contains("service.name=")) {
      defaults.put("management.opentelemetry.resource-attributes.service.name", SERVICE_NAME);
    }
  }
}
