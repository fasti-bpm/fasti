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

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Requires the scrape token on the Prometheus endpoint, wherever the actuator is served: on the
 * port of the application or, with {@code management.server.port}, on the management port. Any
 * non-blank value is a token (also "false"); without one the endpoint is only served on a
 * management port of its own.
 */
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty(FastiObservabilityEnvironmentPostProcessor.PROMETHEUS_ENABLED)
public class FastiPrometheusManagementContextConfiguration {

  @Bean
  public FilterRegistrationBean<FastiPrometheusTokenFilter> fastiPrometheusTokenFilter(
      Environment environment, ObjectProvider<PathMappedEndpoints> pathMappedEndpoints,
      ObjectProvider<DispatcherServletPath> dispatcherServletPath) {
    String token = environment.getProperty(FastiPrometheusAccessGuard.TOKEN);
    FilterRegistrationBean<FastiPrometheusTokenFilter> registration = new FilterRegistrationBean<>(
        new FastiPrometheusTokenFilter(token));
    registration.setName("FastiPrometheusToken");
    if (!StringUtils.hasText(token)) {
      // only possible on a management port of its own (FastiPrometheusAccessGuard)
      registration.setEnabled(false);
      return registration;
    }
    PathMappedEndpoints endpoints = pathMappedEndpoints.getIfAvailable();
    String path = endpoints == null ? null : endpoints.getPath(EndpointId.of("prometheus"));
    if (path == null) {
      // the endpoint is not exposed over HTTP
      registration.setEnabled(false);
      return registration;
    }
    Set<String> paths = new LinkedHashSet<>();
    paths.add(path);
    // the endpoint is served below the path of the DispatcherServlet (spring.mvc.servlet.path)
    DispatcherServletPath servletPath = dispatcherServletPath.getIfAvailable();
    if (servletPath != null) {
      paths.add(servletPath.getRelativePath(path));
    }
    for (String endpointPath : paths) {
      // "/path/*" also matches "/path" itself, a trailing slash and anything below
      registration.addUrlPatterns(endpointPath.endsWith("/") ? endpointPath + "*" : endpointPath + "/*");
    }
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
