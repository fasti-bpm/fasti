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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The token is also required when the DispatcherServlet, and with it the actuator, is mapped
 * below a path of its own.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "fasti.metrics.prometheus.enabled=true",
    "fasti.metrics.prometheus.token=servlet-path-token",
    "spring.mvc.servlet.path=/api"
})
public class PrometheusServletPathTest extends AbstractRestTest {

  @Test
  public void shouldRequireTheTokenBelowTheServletPath() {
    assertThat(testRestTemplate.getForEntity("/api/actuator/prometheus", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("servlet-path-token");
    assertThat(testRestTemplate.exchange("/api/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(headers),
        String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
