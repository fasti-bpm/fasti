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

import org.camunda.bpm.run.CamundaBpmRun;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Run refuses to start when the metrics would be public on the port of the application.
 */
public class PrometheusWithoutTokenTest {

  @Test
  public void shouldRefuseToStart() {
    Throwable failure = catchThrowable(() -> new SpringApplicationBuilder(CamundaBpmRun.class)
        .properties("server.port=0", "fasti.metrics.prometheus.enabled=true",
            "spring.datasource.url=jdbc:h2:mem:prometheus-without-token")
        .run()
        .close());

    assertThat(failure).isNotNull();
    Throwable cause = failure;
    while (cause.getCause() != null && !(cause instanceof IllegalStateException
        && cause.getMessage() != null && cause.getMessage().contains("fasti.metrics.prometheus.token"))) {
      cause = cause.getCause();
    }
    assertThat(cause).hasMessageContaining("fasti.metrics.prometheus.token");
  }
}
