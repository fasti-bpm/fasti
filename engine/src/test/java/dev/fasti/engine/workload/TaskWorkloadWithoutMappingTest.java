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
package dev.fasti.engine.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.util.ReflectUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * An application that replaced the default {@code mappings.xml} with a copy of its own that lacks
 * the workload statements gets an error that says what is missing.
 */
public class TaskWorkloadWithoutMappingTest {

  static ProcessEngine engine;

  /** The default mappings without the file of the workload statements. */
  static class CustomMappingsConfiguration extends StandaloneInMemProcessEngineConfiguration {
    @Override
    protected InputStream getMyBatisXmlConfigurationSteam() {
      try (InputStream defaults = ReflectUtil.getResourceAsStream(DEFAULT_MYBATIS_MAPPING_FILE)) {
        String mappings = new String(defaults.readAllBytes(), StandardCharsets.UTF_8);
        String custom = mappings.replaceAll("<mapper resource=\"dev/fasti/engine/workload/TaskWorkload.xml\"\\s*/>", "");
        assertThat(custom).isNotEqualTo(mappings);
        return new ByteArrayInputStream(custom.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @BeforeClass
  public static void buildEngine() {
    ProcessEngineConfigurationImpl configuration = new CustomMappingsConfiguration();
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-workload-nomapping;DB_CLOSE_DELAY=-1");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("workload-nomapping");
    configuration.setJobExecutorActivate(false);
    engine = configuration.buildProcessEngine();
  }

  @AfterClass
  public static void closeEngine() {
    engine.close();
  }

  @Test
  public void shouldSayThatTheStatementsAreNotMapped() {
    assertThatThrownBy(() -> TaskWorkloads.query(engine).groupIdIn(Arrays.asList("sales")).read())
        .isInstanceOf(WorkloadNotAvailableException.class)
        .hasMessageContaining("dev/fasti/engine/workload/TaskWorkload.xml");
  }
}
