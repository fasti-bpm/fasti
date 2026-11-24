/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.run;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import org.apache.catalina.filters.CorsFilter;
import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.run.property.CamundaBpmRunAuthenticationProperties;
import org.camunda.bpm.run.property.CamundaBpmRunCorsProperty;
import org.camunda.bpm.run.property.CamundaBpmRunProperties;
import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestInitializer;
import org.camunda.bpm.spring.boot.starter.rest.CamundaJerseyResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jersey.autoconfigure.JerseyApplicationPath;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Collections;

@EnableConfigurationProperties(CamundaBpmRunProperties.class)
@Configuration
@AutoConfigureAfter({ CamundaBpmAutoConfiguration.class })
@ConditionalOnClass(CamundaBpmRestInitializer.class)
public class CamundaBpmRunRestConfiguration {

  @Autowired
  CamundaBpmRunProperties camundaBpmRunProperties;

  @Autowired
  Environment environment;

  protected static final String BEARER_ENABLED_PROPERTY = "fasti.security.oidc.rest.bearer-enabled";
  /** Fasti: how long a successful basic authentication of the REST API is cached. */
  protected static final String AUTH_CACHE_TTL_PROPERTY = "fasti.rest.auth-cache-ttl";
  protected static final Duration DEFAULT_AUTH_CACHE_TTL = Duration.ofSeconds(30);
  protected static final String ANALYTICS_CACHE_TTL_PROPERTY = "fasti.analytics.cache-ttl";
  protected static final String ANALYTICS_MAX_CONCURRENT_PROPERTY = "fasti.analytics.max-concurrent";
  protected static final String ANALYTICS_MAX_WAIT_PROPERTY = "fasti.analytics.max-wait";

  protected static final String BEARER_AUTHENTICATION_PROVIDER =
      "org.camunda.bpm.spring.boot.starter.security.oauth2.impl.BearerTokenAuthenticationProvider";

  /*
   * The CORS and Authentication filters need to run before other camunda
   * filters because they potentially block the request and this should be done
   * as early as possible.
   * 
   * The default order parameter for spring-boot managed filters is
   * org.springframework.core.Ordered.LOWEST_PRECEDENCE = Integer.MAX_VALUE.
   * Order can range from -Integer.MAX_VALUE to Integer.MAX_VALUE.
   * 
   * The CORS filter must run before the Authentication filter because CORS
   * preflight requests must not contain authentication. The CORS filter will
   * not invoke the next filter in the chain for preflight requests.
   */
  private static int CORS_FILTER_PRECEDENCE = 0;
  private static int AUTH_FILTER_PRECEDENCE = 1;

  @Bean
  @ConditionalOnProperty(name = "enabled", havingValue = "true", prefix = CamundaBpmRunAuthenticationProperties.PREFIX)
  public FilterRegistrationBean<Filter> processEngineAuthenticationFilter(JerseyApplicationPath applicationPath) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setName("camunda-auth");
    registration.setFilter(new ProcessEngineAuthenticationFilter());
    registration.setOrder(AUTH_FILTER_PRECEDENCE);

    String restApiPathPattern = applicationPath.getUrlMapping();
    registration.addUrlPatterns(restApiPathPattern);

    // if nothing is set, use Http Basic authentication
    CamundaBpmRunAuthenticationProperties properties = camundaBpmRunProperties.getAuth();
    if (properties.getAuthentication() == null || CamundaBpmRunAuthenticationProperties.DEFAULT_AUTH.equals(properties.getAuthentication())) {
      // with bearer tokens enabled (oauth2 module), basic authentication keeps working
      String provider = environment.getProperty(BEARER_ENABLED_PROPERTY, Boolean.class, false)
          ? BEARER_AUTHENTICATION_PROVIDER
          : "org.camunda.bpm.engine.rest.security.auth.impl.HttpBasicAuthenticationProvider";
      registration.addInitParameter("authentication-provider", provider);
    }
    // Fasti: successful basic authentications are cached briefly (0 turns it off)
    Duration cacheTimeToLive = environment.getProperty(AUTH_CACHE_TTL_PROPERTY, Duration.class, DEFAULT_AUTH_CACHE_TTL);
    if (cacheTimeToLive.isNegative()) {
      throw new IllegalArgumentException(AUTH_CACHE_TTL_PROPERTY + " must not be negative: " + cacheTimeToLive);
    }
    registration.addInitParameter(ProcessEngineAuthenticationFilter.BASIC_AUTHENTICATION_CACHE_TTL_PARAM,
        String.valueOf(cacheTimeToLive.toMillis()));
    return registration;
  }

  /** Fasti: the answer cache and the turns of the process analytics, with or without REST authentication. */
  @PostConstruct
  public void analyticsSettings() {
    Duration analyticsCache = seconds(ANALYTICS_CACHE_TTL_PROPERTY);
    if (analyticsCache != null) {
      dev.fasti.engine.analytics.ProcessAnalytics.setCacheTimeToLive(analyticsCache);
    }
    Integer analyticsConcurrent = environment.getProperty(ANALYTICS_MAX_CONCURRENT_PROPERTY, Integer.class);
    if (analyticsConcurrent != null) {
      dev.fasti.engine.analytics.ProcessAnalytics.setMaxConcurrent(analyticsConcurrent);
    }
    Duration analyticsWait = seconds(ANALYTICS_MAX_WAIT_PROPERTY);
    if (analyticsWait != null) {
      dev.fasti.engine.analytics.ProcessAnalytics.setMaxWait(analyticsWait);
    }
  }

  /** A duration setting where a bare number means seconds ({@code 60} is {@code 60s}); null when not set. */
  protected Duration seconds(String property) {
    String value = environment.getProperty(property);
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.matches("\\d{10,}")) {
      throw new IllegalArgumentException(property + " is too long: " + value);
    }
    Duration duration = trimmed.matches("\\d+")
        ? Duration.ofSeconds(Long.parseLong(trimmed))
        : environment.getProperty(property, Duration.class);
    if (duration.isNegative()) {
      throw new IllegalArgumentException(property + " must not be negative: " + value);
    }
    return duration;
  }

  @Bean
  @ConditionalOnProperty(name = "enabled", havingValue = "true", prefix = CamundaBpmRunCorsProperty.PREFIX)
  public FilterRegistrationBean<Filter> corsFilter(JerseyApplicationPath applicationPath) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setName("camunda-cors");
    CorsFilter corsFilter = new CorsFilter();
    registration.setFilter(corsFilter);
    registration.setOrder(CORS_FILTER_PRECEDENCE);

    String restApiPathPattern = applicationPath.getUrlMapping();
    registration.addUrlPatterns(restApiPathPattern);

    registration.addInitParameter(CorsFilter.PARAM_CORS_ALLOWED_ORIGINS,
                                  camundaBpmRunProperties.getCors().getAllowedOrigins());
    registration.addInitParameter(CorsFilter.PARAM_CORS_ALLOWED_METHODS,
                                  CamundaBpmRunCorsProperty.DEFAULT_HTTP_METHODS);
    registration.addInitParameter(CorsFilter.PARAM_CORS_ALLOWED_HEADERS,
                                  camundaBpmRunProperties.getCors().getAllowedHeaders());
    registration.addInitParameter(CorsFilter.PARAM_CORS_EXPOSED_HEADERS,
                                  camundaBpmRunProperties.getCors().getExposedHeaders());
    registration.addInitParameter(CorsFilter.PARAM_CORS_SUPPORT_CREDENTIALS,
                                  String.valueOf(camundaBpmRunProperties.getCors().getAllowCredentials()));
    registration.addInitParameter(CorsFilter.PARAM_CORS_PREFLIGHT_MAXAGE,
                                  camundaBpmRunProperties.getCors().getPreflightMaxAge());

    return registration;
  }

  @Bean
  public CamundaJerseyResourceConfig camundaRunJerseyResourceConfig() {
    CamundaJerseyResourceConfig camundaJerseyResourceConfig = new CamundaJerseyResourceConfig();
    camundaJerseyResourceConfig.setProperties(Collections.singletonMap("jersey.config.server.wadl.disableWadl", camundaBpmRunProperties.getRest().isDisableWadl()));
    return camundaJerseyResourceConfig;
  }

}
