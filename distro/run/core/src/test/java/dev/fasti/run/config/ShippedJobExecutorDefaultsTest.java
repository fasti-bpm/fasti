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
package dev.fasti.run.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.camunda.bpm.spring.boot.starter.configuration.impl.DefaultJobConfiguration;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.camunda.bpm.spring.boot.starter.property.JobExecutionProperty;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The job executor and connection pool settings of the configuration files that Run and the
 * container image ship (docs/tuning-runtime.md), read as Spring Boot reads them.
 */
public class ShippedJobExecutorDefaultsTest {

  static final String[] SHIPPED = {
      "../assembly/resources/default.yml",
      "../assembly/resources/production.yml",
      "../../docker/fasti-docker.yml"
  };

  @Test
  public void shouldShipTenThreadsAQueueOfTenAndAPoolOfTwenty() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of());
      JobExecutionProperty jobExecution = jobExecution(environment);

      assertThat(jobExecution.getCorePoolSize()).as(file).isEqualTo(10);
      assertThat(jobExecution.getMaxPoolSize()).as(file).isEqualTo(10);
      assertThat(jobExecution.getQueueCapacity()).as(file).isEqualTo(10);
      assertThat(jobExecution.getMaxJobsPerAcquisition()).as(file).isEqualTo(10);
      // the lock of Camunda 7.24 nodes; an exclusive batch of ten jobs runs under it
      assertThat(jobExecution.getLockTimeInMillis()).as(file).isEqualTo(300_000);
      assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class)).as(file).isEqualTo(20);
      // an idle node keeps the connections of 1.1, not the maximum
      assertThat(environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class)).as(file).isEqualTo(10);
    }
  }

  @Test
  public void shouldStartWhenOnlyTheMaximumIsLowered() throws IOException {
    for (String file : SHIPPED) {
      // CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE=4, a common setting for a small database
      StandardEnvironment environment = environment(file, Map.of("CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE", "4"));
      JobExecutionProperty jobExecution = jobExecution(environment);

      assertThat(jobExecution.getMaxPoolSize()).as(file).isEqualTo(4);
      assertThat(jobExecution.getCorePoolSize()).as(file).isEqualTo(4);
      assertTaskExecutorStarts(environment, 4, 4);
    }
  }

  @Test
  public void shouldKeepCoreThreadsThatAreSetExplicitly() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of(
          "CAMUNDA_BPM_JOB_EXECUTION_CORE_POOL_SIZE", "3",
          "CAMUNDA_BPM_JOB_EXECUTION_MAX_POOL_SIZE", "16"));
      assertTaskExecutorStarts(environment, 3, 16);

      // more core threads than the maximum: the starter uses the maximum instead of failing
      environment = environment(file, Map.of("CAMUNDA_BPM_JOB_EXECUTION_CORE_POOL_SIZE", "12"));
      assertTaskExecutorStarts(environment, 10, 10);
    }
  }

  protected static void assertTaskExecutorStarts(StandardEnvironment environment, int core, int max) {
    CamundaBpmProperties properties = Binder.get(environment).bind("camunda.bpm", CamundaBpmProperties.class).get();
    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) DefaultJobConfiguration.JobConfiguration.camundaTaskExecutor(properties);
    try {
      executor.initialize();
      assertThat(executor.getCorePoolSize()).isEqualTo(core);
      assertThat(executor.getMaxPoolSize()).isEqualTo(max);
    } finally {
      executor.shutdown();
    }
  }

  protected static JobExecutionProperty jobExecution(StandardEnvironment environment) {
    return Binder.get(environment).bind("camunda.bpm", CamundaBpmProperties.class).get().getJobExecution();
  }

  /** The file below environment variables, as in Run and the image. */
  protected static StandardEnvironment environment(String file, Map<String, Object> variables) throws IOException {
    Path path = Path.of(file);
    assertThat(path).as("shipped configuration file").exists();
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("variables", variables));
    for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new FileSystemResource(path))) {
      environment.getPropertySources().addLast(source);
    }
    ConfigurationPropertySources.attach(environment);
    return environment;
  }
}
