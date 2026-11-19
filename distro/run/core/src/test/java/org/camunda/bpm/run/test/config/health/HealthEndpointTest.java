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
package org.camunda.bpm.run.test.config.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.run.test.AbstractRestTest;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * The health endpoints that container platforms and load balancers poll, as the container
 * image configures them.
 */
@TestPropertySource(properties = {
    "management.endpoint.health.probes.enabled=true",
    "management.endpoint.health.group.readiness.include=readinessState,db,processEngine"
})
public class HealthEndpointTest extends AbstractRestTest {

  @Test
  public void shouldReportHealthWithoutAuthentication() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  public void shouldReportLiveness() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health/liveness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  public void shouldReportReadinessIncludingDatabaseAndEngine() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/health/readiness", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  public void shouldNotExposeOtherEndpoints() {
    assertThat(testRestTemplate.getForEntity("/actuator/env", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(testRestTemplate.getForEntity("/actuator/beans", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
