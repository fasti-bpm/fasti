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

import org.camunda.bpm.run.test.AbstractRestTest;
import org.junit.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * With a management port of its own the actuator, metrics included, moves off the port of the
 * application. No token is required there; a configured one is still checked.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "fasti.metrics.prometheus.enabled=true",
    "fasti.metrics.prometheus.token=management-token",
    "management.server.port=0"
})
public class PrometheusManagementPortTest extends AbstractRestTest {

  @LocalManagementPort
  protected int managementPort;

  @Test
  public void shouldServeMetricsOnTheManagementPortOnly() {
    assertThat(managementPort).isNotEqualTo(localPort);
    String url = "http://localhost:" + managementPort + "/actuator/prometheus";

    assertThat(testRestTemplate.getForEntity(url, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("management-token");
    ResponseEntity<String> metrics = testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(metrics.getBody()).contains("fasti_engine_process_instances_started_total");

    assertThat(testRestTemplate.getForEntity("/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(testRestTemplate.getForEntity("http://localhost:" + managementPort + "/actuator/health", String.class)
        .getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
