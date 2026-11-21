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
package dev.fasti.spring.boot.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import io.micrometer.core.instrument.MeterRegistry;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The gauges with a refresh interval, as in production: the rounds run on the background thread
 * against the engine of a Spring context, and the thread ends when the context closes.
 */
public class EngineGaugesBackgroundTest {

  static final String ENGINE = "gaugesBackgroundEngine";
  static final String THREAD = "fasti-engine-gauges-" + ENGINE;

  @Test
  public void shouldReadInTheBackgroundAndStopWithTheContext() throws InterruptedException {
    SpringApplication application = new SpringApplication(TestApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setLogStartupInfo(false);
    ConfigurableApplicationContext context = application.run(
        "--camunda.bpm.process-engine-name=" + ENGINE,
        "--camunda.bpm.job-execution.enabled=false",
        "--fasti.metrics.engine.gauges.refresh-interval=1s");
    FastiEngineMetricsPlugin plugin;
    try {
      plugin = context.getBean(FastiEngineMetricsPlugin.class);
      assertThat(plugin.isRunning()).isTrue();
      MeterRegistry registry = context.getBean(MeterRegistry.class);
      context.getBean(RepositoryService.class).createDeployment()
          .addModelInstance("gauges.bpmn", Bpmn.createExecutableProcess("gauges")
              .camundaHistoryTimeToLive(1)
              .startEvent().userTask().endEvent().done())
          .deploy();
      context.getBean(RuntimeService.class).startProcessInstanceByKey("gauges");

      // a scrape returns at once; the value arrives with a later round
      assertThat(waitFor(() -> registry.get("fasti.engine.tasks.open").tag("engine", ENGINE).gauge().value() == 1,
          Duration.ofSeconds(30))).isTrue();
      assertThat(threadAlive()).isTrue();
    } finally {
      context.close();
    }

    assertThat(plugin.isRunning()).isFalse();
    assertThat(plugin.getGauges().get(ENGINE).closed).isTrue();
    assertThat(waitFor(() -> !threadAlive(), Duration.ofSeconds(10))).as("thread " + THREAD + " ended").isTrue();
  }

  static boolean threadAlive() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(thread -> thread.getName().equals(THREAD) && thread.isAlive());
  }

  static boolean waitFor(BooleanSupplier condition, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(50);
    }
    return true;
  }
}
