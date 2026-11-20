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
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.run.test.AbstractRestTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * With {@code fasti.tracing.enabled} REST requests, the engine commands they run and job
 * executions become spans. A test exporter collects them instead of an OTLP endpoint.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "fasti.tracing.enabled=true",
    "camunda.bpm.job-execution.enabled=false",
    "management.opentelemetry.tracing.export.schedule-delay=50ms"
})
@Import(TracingTest.CollectingExporterConfiguration.class)
public class TracingTest extends AbstractRestTest {

  protected static final AttributeKey<String> COMMAND = AttributeKey.stringKey("fasti.engine.command");

  @Autowired
  protected CollectingSpanExporter exporter;

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected ManagementService managementService;

  @Before
  public void clear() {
    exporter.spans.clear();
  }

  @Test
  public void shouldTraceRestRequestsWithTheirEngineCommands() {
    assertThat(testRestTemplate.getForEntity(CONTEXT_PATH + "/process-definition/count", String.class)
        .getStatusCode()).isEqualTo(HttpStatus.OK);

    SpanData server = await().atMost(Duration.ofSeconds(10)).until(
        () -> exporter.find(span -> span.getKind() == SpanKind.SERVER), Optional::isPresent).get();
    SpanData command = await().atMost(Duration.ofSeconds(10)).until(
        () -> exporter.find(span -> server.getTraceId().equals(span.getTraceId()) && span.getAttributes().get(COMMAND) != null),
        Optional::isPresent).get();

    assertThat(command.getParentSpanId()).isEqualTo(server.getSpanId());
    assertThat(command.getName()).isEqualTo(command.getAttributes().get(COMMAND));
    assertThat(server.getResource().getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("fasti-bpm");
  }

  @Test
  public void shouldNotTraceHealthChecks() {
    assertThat(testRestTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(testRestTemplate.getForEntity(CONTEXT_PATH + "/engine", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // the REST request is exported; the health check before it is not
    await().atMost(Duration.ofSeconds(10)).until(
        () -> exporter.find(span -> span.getKind() == SpanKind.SERVER), Optional::isPresent);
    assertThat(exporter.spans).filteredOn(span -> span.getKind() == SpanKind.SERVER).hasSize(1);
    assertThat(exporter.spans).noneMatch(span -> span.getName().contains("actuator"));
  }

  @Test
  public void shouldStartATraceForEachJobExecution() {
    String deployment = repositoryService.createDeployment()
        .addModelInstance("tracingAsync.bpmn", Bpmn.createExecutableProcess("tracingAsync")
            .camundaHistoryTimeToLive(1)
            .startEvent().serviceTask().camundaAsyncBefore().camundaExpression("${true}").endEvent().done())
        .deploy().getId();
    try {
      String instance = runtimeService.startProcessInstanceByKey("tracingAsync").getId();
      exporter.spans.clear();

      managementService.executeJob(
          managementService.createJobQuery().processInstanceId(instance).singleResult().getId());

      SpanData job = await().atMost(Duration.ofSeconds(10)).until(
          () -> exporter.find(span -> "job execution".equals(span.getName())), Optional::isPresent).get();
      assertThat(job.getParentSpanContext().isValid()).isFalse();
      assertThat(job.getAttributes().get(COMMAND)).isEqualTo("ExecuteJobsCmd");
    } finally {
      repositoryService.deleteDeployment(deployment, true);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CollectingExporterConfiguration {

    @Bean
    CollectingSpanExporter collectingSpanExporter() {
      return new CollectingSpanExporter();
    }
  }

  static class CollectingSpanExporter implements SpanExporter {

    final List<SpanData> spans = new CopyOnWriteArrayList<>();

    Optional<SpanData> find(java.util.function.Predicate<SpanData> predicate) {
      return spans.stream().filter(predicate).findFirst();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> batch) {
      spans.addAll(batch);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }
  }
}
