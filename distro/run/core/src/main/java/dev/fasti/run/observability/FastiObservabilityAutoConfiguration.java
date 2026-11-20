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

import io.micrometer.observation.ObservationPredicate;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Observability of Fasti BPM Run: see {@link FastiObservabilityEnvironmentPostProcessor} for
 * the switches, {@link FastiPrometheusAccessGuard} and
 * {@link FastiPrometheusManagementContextConfiguration} for the protection of the metrics.
 */
@AutoConfiguration
public class FastiObservabilityAutoConfiguration {

  @Bean
  public static FastiPrometheusAccessGuard fastiPrometheusAccessGuard(Environment environment) {
    return new FastiPrometheusAccessGuard(environment);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBooleanProperty(FastiObservabilityEnvironmentPostProcessor.TRACING_ENABLED)
  @ConditionalOnClass({ ObservationPredicate.class, ServerRequestObservationContext.class })
  static class TracingConfiguration {

    /**
     * Health checks and metric scrapes would otherwise start a trace every few seconds; with
     * tracing on, requests to the actuator are not observed (and not counted in
     * {@code http.server.requests} either).
     */
    @Bean
    ObservationPredicate fastiActuatorRequestsNotObserved(Environment environment) {
      String basePath = environment.getProperty("management.endpoints.web.base-path", "/actuator");
      // the actuator is served below the path of the DispatcherServlet (spring.mvc.servlet.path)
      String servletPath = environment.getProperty("spring.mvc.servlet.path", "");
      String actuatorPath = servletPath.endsWith("/")
          ? servletPath.substring(0, servletPath.length() - 1) + basePath
          : servletPath + basePath;
      return (name, context) -> !(context instanceof ServerRequestObservationContext serverContext)
          || !isBelow(pathWithinApplication(serverContext.getCarrier()), actuatorPath);
    }

    static String pathWithinApplication(HttpServletRequest request) {
      String pathInfo = request.getPathInfo();
      return request.getServletPath() + (pathInfo == null ? "" : pathInfo);
    }

    static boolean isBelow(String path, String basePath) {
      if (path == null || basePath == null || basePath.isEmpty() || basePath.endsWith("/")) {
        // an actuator at the root of a management port of its own cannot be told apart by path
        return false;
      }
      return path.equals(basePath) || path.startsWith(basePath + "/");
    }
  }
}
