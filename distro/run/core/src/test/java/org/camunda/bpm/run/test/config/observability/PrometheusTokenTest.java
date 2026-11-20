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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The Prometheus endpoint on the port of the application, protected by the scrape token.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "fasti.metrics.prometheus.enabled=true",
    "fasti.metrics.prometheus.token=test-scrape-token"
})
public class PrometheusTokenTest extends AbstractRestTest {

  @Test
  public void shouldRequireTheToken() {
    ResponseEntity<String> response = testRestTemplate.getForEntity("/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    assertThat(scrape("wrong-token", "/actuator/prometheus").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(scrape("wrong-token", "/actuator/prometheus/").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(scrape("wrong-token", "/actuator/%70rometheus").getStatusCode()).isNotEqualTo(HttpStatus.OK);
  }

  @Test
  public void shouldServeEngineAndJvmMetricsWithTheToken() {
    // something for the engine and HTTP metrics to count
    testRestTemplate.getForEntity(CONTEXT_PATH + "/process-definition/count", String.class);

    ResponseEntity<String> response = scrape("test-scrape-token", "/actuator/prometheus");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString()).startsWith("text/plain");
    assertThat(response.getBody())
        .contains("fasti_engine_process_instances_started_total{engine=\"default\"}")
        .contains("fasti_engine_jobs_executed_total{engine=\"default\",result=\"failed\"}")
        .contains("fasti_engine_incidents_open{engine=\"default\"}")
        .contains("fasti_engine_jobs_acquisition_attempts_total")
        .contains("jvm_memory_used_bytes")
        .contains("hikaricp_connections")
        .contains("http_server_requests_seconds_count");
  }

  @Test
  public void shouldKeepHealthOpenAndOtherEndpointsClosed() {
    assertThat(testRestTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(scrape("test-scrape-token", "/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(scrape("test-scrape-token", "/actuator/env").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  protected ResponseEntity<String> scrape(String token, String path) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return testRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }
}
