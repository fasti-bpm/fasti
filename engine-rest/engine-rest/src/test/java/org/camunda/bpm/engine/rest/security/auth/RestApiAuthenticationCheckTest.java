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
package org.camunda.bpm.engine.rest.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

import org.camunda.bpm.engine.rest.filter.CacheControlFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RestApiAuthenticationCheckTest {

  protected static final Logger CHECK_LOGGER = Logger.getLogger(RestApiAuthenticationCheck.class.getName());

  protected ServletContext servletContext;
  protected Map<String, FilterRegistration> filterRegistrations;
  protected RecordingHandler handler;

  @Before
  public void setUp() {
    filterRegistrations = new HashMap<>();

    servletContext = mock(ServletContext.class);
    when(servletContext.getContextPath()).thenReturn("/engine-rest");
    when(servletContext.getClassLoader()).thenReturn(getClass().getClassLoader());
    when(servletContext.getFilterRegistrations()).thenAnswer(invocation -> filterRegistrations);

    handler = new RecordingHandler();
    CHECK_LOGGER.addHandler(handler);
  }

  @After
  public void tearDown() {
    CHECK_LOGGER.removeHandler(handler);
    System.clearProperty(RestApiAuthenticationCheck.WARNING_ENABLED_SYSTEM_PROPERTY);
  }

  @Test
  public void shouldWarnWithoutAnyFilter() {
    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isTrue();
    assertThat(handler.warnings).hasSize(1);
    assertThat(handler.warnings.get(0)).contains("WITHOUT AUTHENTICATION");
  }

  @Test
  public void shouldWarnWhenOnlyOtherFiltersAreRegistered() {
    // given
    register("CacheControlFilter", CacheControlFilter.class.getName());
    register("unknown", "org.example.DoesNotExist");
    register("noClass", null);

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isTrue();
    assertThat(handler.warnings).hasSize(1);
  }

  @Test
  public void shouldNotWarnWithAuthenticationFilter() {
    // given
    register("camunda-auth", ProcessEngineAuthenticationFilter.class.getName());

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
    assertThat(handler.warnings).isEmpty();
  }

  @Test
  public void shouldWarnWhenAuthenticationFilterIsNotMapped() {
    // given
    register("camunda-auth", ProcessEngineAuthenticationFilter.class.getName());
    when(filterRegistrations.get("camunda-auth").getUrlPatternMappings()).thenReturn(java.util.List.of());

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isTrue();
  }

  @Test
  public void shouldNotWarnWithSubclassOfAuthenticationFilter() {
    // given
    register("custom-auth", CustomAuthenticationFilter.class.getName());

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
  }

  @Test
  public void shouldNotWarnWithSecurityFilterChain() {
    // given
    register("springSecurityFilterChain", "org.springframework.web.filter.DelegatingFilterProxy");

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
  }

  @Test
  public void shouldNotWarnWhenDisabledByContextParameter() {
    // given
    when(servletContext.getInitParameter(RestApiAuthenticationCheck.WARNING_ENABLED_PARAM)).thenReturn("false");

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
    assertThat(handler.warnings).isEmpty();
  }

  @Test
  public void shouldNotWarnWhenDisabledBySystemProperty() {
    // given
    System.setProperty(RestApiAuthenticationCheck.WARNING_ENABLED_SYSTEM_PROPERTY, "FALSE");

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
    assertThat(handler.warnings).isEmpty();
  }

  @Test
  public void shouldWarnWhenParameterIsNotFalse() {
    // given
    when(servletContext.getInitParameter(RestApiAuthenticationCheck.WARNING_ENABLED_PARAM)).thenReturn("true");

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isTrue();
  }

  @Test
  public void shouldNotWarnWhenFiltersCannotBeInspected() {
    // given
    when(servletContext.getFilterRegistrations()).thenThrow(new UnsupportedOperationException());

    // when
    boolean warned = RestApiAuthenticationCheck.warnIfUnauthenticated(servletContext);

    // then
    assertThat(warned).isFalse();
  }

  protected void register(String filterName, String className) {
    FilterRegistration registration = mock(FilterRegistration.class);
    when(registration.getName()).thenReturn(filterName);
    when(registration.getClassName()).thenReturn(className);
    when(registration.getUrlPatternMappings()).thenReturn(java.util.List.of("/*"));
    filterRegistrations.put(filterName, registration);
  }

  public static class CustomAuthenticationFilter extends ProcessEngineAuthenticationFilter {
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
