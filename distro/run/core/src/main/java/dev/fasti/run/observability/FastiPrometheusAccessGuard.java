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

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Refuses to start when the Prometheus metrics would be served on the port of the web
 * applications and the REST API without a scrape token.
 * <p>
 * The check looks at what is served, not only at the switch of Fasti: the endpoint is served
 * with {@code fasti.metrics.prometheus.enabled=true}, and also when the Spring Boot settings
 * behind the switch are set by hand (access to the {@code prometheus} endpoint, its exposure
 * over the web and the Prometheus registry).
 * <p>
 * The metrics are then readable either with {@code Authorization: Bearer <token>}
 * ({@code fasti.metrics.prometheus.token}) or on a separate management port
 * ({@code management.server.port}), which is meant to be reachable from the monitoring
 * network only. A token must have at least {@value #MIN_TOKEN_LENGTH} characters and must not
 * be the placeholder of the examples. The check runs before any bean is created.
 */
public class FastiPrometheusAccessGuard implements BeanFactoryPostProcessor {

  public static final String TOKEN = "fasti.metrics.prometheus.token";

  /** A token shorter than this is refused: it could be guessed by trying. */
  public static final int MIN_TOKEN_LENGTH = 16;

  /** The value the commented examples use; it is public, so it is refused. */
  public static final String PLACEHOLDER_TOKEN = "change-me";

  protected final Environment environment;

  public FastiPrometheusAccessGuard(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    String problem = check(environment);
    if (problem != null) {
      throw new IllegalStateException(problem);
    }
  }

  /** A description of the problem, or {@code null} if the configuration is acceptable. */
  public static String check(Environment environment) {
    boolean switchedOn = environment.getProperty(FastiObservabilityEnvironmentPostProcessor.PROMETHEUS_ENABLED,
        Boolean.class, false);
    if (!switchedOn && !isEndpointServed(environment)) {
      return null;
    }
    String token = environment.getProperty(TOKEN);
    if (StringUtils.hasText(token)) {
      return tokenProblem(token);
    }
    if (hasSeparateManagementPort(environment)) {
      return null;
    }
    String cause = switchedOn
        ? "fasti.metrics.prometheus.enabled=true"
        : "the prometheus endpoint is enabled and exposed by management.* settings";
    return "The Prometheus metrics (" + cause + ") would be readable by anyone who "
        + "reaches the port of the web applications. Set a scrape token (" + TOKEN
        + ", FASTI_METRICS_PROMETHEUS_TOKEN) or serve the actuator on a separate management port "
        + "(management.server.port) that only the monitoring network reaches.";
  }

  /** What is wrong with the scrape token, or {@code null} if it can be used. */
  public static String tokenProblem(String token) {
    String value = token == null ? "" : token.trim();
    if (PLACEHOLDER_TOKEN.equals(value.toLowerCase(Locale.ROOT))) {
      return "The scrape token (" + TOKEN + ", FASTI_METRICS_PROMETHEUS_TOKEN) is the placeholder of the examples, '"
          + PLACEHOLDER_TOKEN + "'. Set a random value of at least " + MIN_TOKEN_LENGTH
          + " characters, for example from 'openssl rand -hex 32'.";
    }
    if (value.length() < MIN_TOKEN_LENGTH) {
      return "The scrape token (" + TOKEN + ", FASTI_METRICS_PROMETHEUS_TOKEN) is too short: it needs at least "
          + MIN_TOKEN_LENGTH + " characters. Set a random value, for example from 'openssl rand -hex 32'.";
    }
    return null;
  }

  /**
   * Whether the settings of Spring Boot serve the {@code prometheus} endpoint over HTTP: access to
   * it is not {@code none}, the web exposure includes it, and the Prometheus registry is on.
   * With the switch of Fasti off, {@link FastiObservabilityEnvironmentPostProcessor} turns access
   * and registry off by default, so this only holds when they are set explicitly.
   */
  public static boolean isEndpointServed(Environment environment) {
    String access = environment.getProperty("management.endpoint.prometheus.access",
        environment.getProperty("management.endpoints.access.default", "unrestricted"));
    if (isNone(access) || isNone(environment.getProperty("management.endpoints.access.max-permitted"))) {
      return false;
    }
    Boolean exported = environment.getProperty("management.prometheus.metrics.export.enabled", Boolean.class);
    if (exported == null) {
      exported = environment.getProperty("management.defaults.metrics.export.enabled", Boolean.class, true);
    }
    if (!exported) {
      return false;
    }
    Binder binder = Binder.get(environment);
    Set<String> include = binder.bind("management.endpoints.web.exposure.include", Bindable.setOf(String.class))
        .orElseGet(() -> Set.of("health"));
    Set<String> exclude = binder.bind("management.endpoints.web.exposure.exclude", Bindable.setOf(String.class))
        .orElseGet(Set::of);
    return names(include) && !names(exclude);
  }

  private static boolean isNone(String access) {
    return access != null && "none".equals(access.trim().toLowerCase(Locale.ROOT));
  }

  private static boolean names(Set<String> endpoints) {
    for (String endpoint : endpoints) {
      String name = endpoint == null ? "" : endpoint.trim();
      if ("*".equals(name) || "prometheus".equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasSeparateManagementPort(Environment environment) {
    Integer managementPort = environment.getProperty("management.server.port", Integer.class);
    if (managementPort == null) {
      return false;
    }
    Integer serverPort = environment.getProperty("server.port", Integer.class, 8080);
    // -1 switches the HTTP endpoints of the actuator off; 0 picks a free port of its own
    return managementPort < 0 || managementPort == 0 || !Objects.equals(managementPort, serverPort);
  }

  /** Whether the configured token matches; both values are compared in constant time. */
  public static boolean tokenMatches(String expected, String presented) {
    if (!StringUtils.hasText(expected) || presented == null) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        presented.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
