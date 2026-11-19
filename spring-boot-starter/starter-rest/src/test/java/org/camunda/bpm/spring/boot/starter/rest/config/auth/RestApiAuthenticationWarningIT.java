/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.rest.config.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.camunda.bpm.engine.rest.security.auth.ProcessEngineAuthenticationFilter;
import org.camunda.bpm.engine.rest.security.auth.RestApiAuthenticationCheck;
import org.camunda.bpm.engine.rest.security.auth.impl.HttpBasicAuthenticationProvider;
import org.camunda.bpm.spring.boot.starter.rest.test.TestRestApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;

/**
 * The REST API is not authenticated by default; the application must point
 * that out when it starts.
 */
public class RestApiAuthenticationWarningIT {

  protected static final Logger CHECK_LOGGER = Logger.getLogger(RestApiAuthenticationCheck.class.getName());

  protected RecordingHandler handler = new RecordingHandler();
  protected ConfigurableApplicationContext applicationContext;

  @Before
  public void recordLogs() {
    CHECK_LOGGER.addHandler(handler);
  }

  @After
  public void tearDown() {
    CHECK_LOGGER.removeHandler(handler);
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  public void shouldWarnWhenRestApiIsNotAuthenticated() {
    // when
    applicationContext = start(TestRestApplication.class);

    // then
    assertThat(handler.warnings).hasSize(1);
    assertThat(handler.warnings.get(0)).contains("WITHOUT AUTHENTICATION");
  }

  @Test
  public void shouldNotWarnWhenWarningIsDisabled() {
    // when
    applicationContext = start(TestRestApplication.class,
        "camunda.bpm.rest-api.authentication-warning-enabled=false");

    // then
    assertThat(handler.warnings).isEmpty();
  }

  @Test
  public void shouldNotWarnWhenAuthenticationFilterIsRegistered() {
    // when
    applicationContext = start(AuthenticatedRestApplication.class);

    // then
    assertThat(handler.warnings).isEmpty();
  }

  protected ConfigurableApplicationContext start(Class<?> application, String... properties) {
    return new SpringApplicationBuilder(application)
        .properties("server.port=0")
        .properties(properties)
        .run();
  }

  @Configuration
  public static class AuthenticatedRestApplication extends TestRestApplication {

    @Bean
    public FilterRegistrationBean<Filter> processEngineAuthenticationFilter() {
      FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
      registration.setName("camunda-auth");
      registration.setFilter(new ProcessEngineAuthenticationFilter());
      registration.addInitParameter("authentication-provider", HttpBasicAuthenticationProvider.class.getName());
      registration.addUrlPatterns("/engine-rest/*");
      return registration;
    }
  }

  protected static class RecordingHandler extends Handler {

    protected List<String> warnings = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
        warnings.add(record.getMessage());
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
  }

}
