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

import java.util.Objects;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Refuses to start when {@code fasti.metrics.prometheus.enabled=true} would serve the metrics
 * on the port of the web applications and the REST API without a scrape token.
 * <p>
 * The metrics are readable either with {@code Authorization: Bearer <token>}
 * ({@code fasti.metrics.prometheus.token}) or on a separate management port
 * ({@code management.server.port}), which is meant to be reachable from the monitoring
 * network only. The check runs before any bean is created.
 */
public class FastiPrometheusAccessGuard implements BeanFactoryPostProcessor {

  public static final String TOKEN = "fasti.metrics.prometheus.token";

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
    if (!environment.getProperty(FastiObservabilityEnvironmentPostProcessor.PROMETHEUS_ENABLED, Boolean.class, false)) {
      return null;
    }
    if (StringUtils.hasText(environment.getProperty(TOKEN)) || hasSeparateManagementPort(environment)) {
      return null;
    }
    return "The Prometheus metrics (fasti.metrics.prometheus.enabled=true) would be readable by anyone who "
        + "reaches the port of the web applications. Set a scrape token (" + TOKEN
        + ", FASTI_METRICS_PROMETHEUS_TOKEN) or serve the actuator on a separate management port "
        + "(management.server.port) that only the monitoring network reaches.";
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
