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
package org.camunda.bpm.spring.boot.starter;

import static org.junit.Assert.assertEquals;

import com.jayway.jsonpath.JsonPath;

import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@AutoConfigureTestRestTemplate
@SpringBootTest(classes = { TestApplication.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CamundaBpmActuatorConfigurationIT extends AbstractCamundaAutoConfigurationIT{

  @Autowired
  private TestRestTemplate testRestTemplate;

  @Test
  public void jobExecutorHealthIndicatorTest() {
    final String body = getHealthBody();
    // property order inside a component is not fixed (Jackson 3 sorts alphabetically), so read by path
    assertEquals("wrong body " + body, "UP", JsonPath.read(body, "$.components.jobExecutor.status"));
  }

  @Test
  public void processEngineHealthIndicatorTest() {
    final String body = getHealthBody();
    assertEquals("wrong body " + body, "UP", JsonPath.read(body, "$.components.processEngine.status"));
    assertEquals("wrong body " + body, "testEngine", JsonPath.read(body, "$.components.processEngine.details.name"));
  }

  private String getHealthBody() {
    ResponseEntity<String> entity = testRestTemplate.getForEntity("/actuator/health", String.class);
    final String body = entity.getBody();
    return body;
  }
}
