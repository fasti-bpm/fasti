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
package org.camunda.bpm.engine.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * An engine whose MyBatis mappings lack the workload statements (an application replaced the
 * default mappings.xml) answers 501, which clients take as "count per group and user instead".
 */
public class TaskWorkloadUnmappedRestServiceTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule rule = new TestContainerRule();

  protected static final String WORKLOAD_URL = TEST_RESOURCE_ROOT_PATH + "/task/workload";

  protected static ProcessEngine engine;

  @BeforeClass
  public static void buildEngine() {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration() {
      @Override
      protected InputStream getMyBatisXmlConfigurationSteam() {
        try (InputStream defaults = ReflectUtil.getResourceAsStream(DEFAULT_MYBATIS_MAPPING_FILE)) {
          String mappings = new String(defaults.readAllBytes(), StandardCharsets.UTF_8)
              .replaceAll("<mapper resource=\"dev/fasti/engine/workload/TaskWorkload.xml\"\\s*/>", "");
          return new ByteArrayInputStream(mappings.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };
    configuration.setProcessEngineName("workloadUnmappedRestEngine");
    configuration.setJdbcUrl("jdbc:h2:mem:workloadUnmappedRestEngine");
    configuration.setJobExecutorActivate(false);
    engine = configuration.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @Before
  public void setUpEngine() {
    when(processEngine.getProcessEngineConfiguration()).thenReturn(engine.getProcessEngineConfiguration());
  }

  @Test
  public void shouldAnswerNotImplemented() {
    given().contentType("application/json").body("{\"groupIdIn\": [\"sales\"]}")
    .then().expect()
        .statusCode(Status.NOT_IMPLEMENTED.getStatusCode())
        .body("type", equalTo("RestException"))
        .body("message", containsString("TaskWorkload.xml"))
    .when().post(WORKLOAD_URL);
  }
}
