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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Arrays;

import org.camunda.bpm.run.CamundaBpmRun;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Run refuses to start when the metrics would be public on the port of the application, or
 * protected by a token that is no secret.
 */
public class PrometheusWithoutTokenTest {

  @Test
  public void shouldRefuseToStart() {
    assertThat(startFailure("without-token", "fasti.metrics.prometheus.enabled=true"))
        .hasMessageContaining("fasti.metrics.prometheus.token")
        .hasMessageContaining("fasti.metrics.prometheus.enabled=true");
  }

  @Test
  public void shouldRefuseToStartWhenTheEndpointIsSwitchedOnWithSpringBootSettingsAlone() {
    // without the switch of Fasti, which would require the token
    assertThat(startFailure("spring-settings",
        "management.endpoint.prometheus.access=read-only",
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoints.web.exposure.include=health,prometheus"))
        .hasMessageContaining("fasti.metrics.prometheus.token")
        .hasMessageContaining("management.* settings");
  }

  @Test
  public void shouldRefuseToStartWithAShortToken() {
    assertThat(startFailure("short-token",
        "fasti.metrics.prometheus.enabled=true", "fasti.metrics.prometheus.token=secret"))
        .hasMessageContaining("fasti.metrics.prometheus.token")
        .hasMessageContaining("too short");
  }

  @Test
  public void shouldRefuseToStartWithThePlaceholderToken() {
    assertThat(startFailure("placeholder-token",
        "fasti.metrics.prometheus.enabled=true", "fasti.metrics.prometheus.token=change-me"))
        .hasMessageContaining("fasti.metrics.prometheus.token")
        .hasMessageContaining("placeholder");
  }

  /**
   * Starts Run with the properties and returns the refusal of the access guard. The properties
   * are passed as command line arguments, which win over the defaults Run derives from its
   * switches, like values of a configuration file or of the environment do.
   */
  protected Throwable startFailure(String database, String... properties) {
    String[] arguments = Arrays.stream(properties).map(property -> "--" + property).toArray(String[]::new);
    Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(CamundaBpmRun.class)
        .properties("server.port=0", "spring.datasource.url=jdbc:h2:mem:prometheus-" + database)
        .run(arguments)
        .close());

    assertThat(failure).isNotNull();
    Throwable cause = failure;
    while (cause.getCause() != null && !(cause instanceof IllegalStateException
        && cause.getMessage() != null && cause.getMessage().contains("fasti.metrics.prometheus.token"))) {
      cause = cause.getCause();
    }
    return cause;
  }
}
