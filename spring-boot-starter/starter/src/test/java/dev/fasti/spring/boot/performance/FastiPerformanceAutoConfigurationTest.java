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
package dev.fasti.spring.boot.performance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fasti.engine.performance.PerformanceAdvisorPlugin;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(Enclosed.class)
public class FastiPerformanceAutoConfigurationTest {

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=performanceEngine",
      "spring.datasource.hikari.maximum-pool-size=4",
      "camunda.bpm.job-execution.max-pool-size=6"
  })
  public static class WarnByDefault {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected PerformanceAdvisorPlugin plugin;

    @Test
    public void shouldReportMissingIndexesAndTheSmallPool() {
      assertThat(processEngine).isNotNull();
      assertThat(plugin.getIndexCheck()).isEqualTo("warn");
      assertThat(plugin.getConfigCheck()).isEqualTo("warn");
      assertThat(plugin.getWarnings()).anySatisfy(warning -> assertThat(warning)
          .startsWith("FASTI-PERF-001 ").contains("FASTI_IDX_TASK_DUE").contains("h2_fasti_perf_indexes_create.sql"));
      // the pool of the starter (HikariCP behind a proxy) and its job executor (a Spring task executor)
      assertThat(plugin.getWarnings()).anySatisfy(warning -> assertThat(warning)
          .startsWith("FASTI-PERF-004 ").contains("4 connections").contains("6 threads"));
    }
  }

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=quietPerformanceEngine",
      "camunda.bpm.job-execution.enabled=false",
      "fasti.performance.index-check=off",
      // what YAML makes of a bare off
      "fasti.performance.config-check=false"
  })
  public static class SwitchedOff {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected PerformanceAdvisorPlugin plugin;

    @Test
    public void shouldStaySilent() {
      assertThat(processEngine).isNotNull();
      assertThat(plugin.getIndexCheck()).isEqualTo("off");
      assertThat(plugin.getConfigCheck()).isEqualTo("off");
      assertThat(plugin.getWarnings()).isEmpty();
    }
  }
}
