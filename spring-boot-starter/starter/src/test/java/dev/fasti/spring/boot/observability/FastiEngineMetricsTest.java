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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.HashSet;
import java.util.Set;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "camunda.bpm.process-engine-name=metricsEngine",
    "camunda.bpm.job-execution.enabled=false",
    "fasti.metrics.engine.gauges.refresh-interval=0s"
})
public class FastiEngineMetricsTest {

  @Autowired
  protected MeterRegistry meterRegistry;

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Autowired
  protected ManagementService managementService;

  protected final Set<String> deployments = new HashSet<>();

  @Before
  public void deploy() {
    BpmnModelInstance userTask = Bpmn.createExecutableProcess("metricsUserTask")
        .camundaHistoryTimeToLive(1)
        .startEvent().userTask("task").endEvent().done();
    BpmnModelInstance asyncTasks = Bpmn.createExecutableProcess("metricsAsync")
        .camundaHistoryTimeToLive(1)
        .startEvent()
        .serviceTask("ok").camundaAsyncBefore().camundaExpression("${true}")
        .serviceTask("broken").camundaAsyncBefore().camundaClass("dev.fasti.does.not.Exist")
        .endEvent().done();
    deployments.add(repositoryService.createDeployment()
        .addModelInstance("metricsUserTask.bpmn", userTask)
        .addModelInstance("metricsAsync.bpmn", asyncTasks)
        .deploy().getId());
  }

  @After
  public void undeploy() {
    deployments.forEach(id -> repositoryService.deleteDeployment(id, true));
  }

  @Test
  public void shouldCountProcessInstancesAndTasks() {
    double started = counter("fasti.engine.process.instances.started");
    double completed = counter("fasti.engine.process.instances.ended", "state", "completed");
    double canceled = counter("fasti.engine.process.instances.ended", "state", "canceled");
    double activities = counter("fasti.engine.activity.instances.started");

    String first = runtimeService.startProcessInstanceByKey("metricsUserTask").getId();
    String second = runtimeService.startProcessInstanceByKey("metricsUserTask").getId();

    assertThat(counter("fasti.engine.process.instances.started")).isEqualTo(started + 2);
    assertThat(counter("fasti.engine.activity.instances.started")).isGreaterThanOrEqualTo(activities + 4);
    assertThat(gauge("fasti.engine.tasks.open")).isEqualTo(2);
    assertThat(gauge("fasti.engine.process.instances.active")).isEqualTo(2);

    taskService.complete(taskService.createTaskQuery().processInstanceId(first).singleResult().getId());
    runtimeService.deleteProcessInstance(second, "test");

    assertThat(counter("fasti.engine.process.instances.ended", "state", "completed")).isEqualTo(completed + 1);
    assertThat(counter("fasti.engine.process.instances.ended", "state", "canceled")).isEqualTo(canceled + 1);
    assertThat(gauge("fasti.engine.tasks.open")).isZero();
    assertThat(gauge("fasti.engine.process.instances.active")).isZero();
  }

  @Test
  public void shouldCountJobsAndIncidents() {
    double successful = counter("fasti.engine.jobs.executed", "result", "successful");
    double failed = counter("fasti.engine.jobs.executed", "result", "failed");

    String instance = runtimeService.startProcessInstanceByKey("metricsAsync").getId();
    assertThat(gauge("fasti.engine.jobs.executable")).isEqualTo(1);

    managementService.executeJob(job(instance).getId());
    assertThat(counter("fasti.engine.jobs.executed", "result", "successful")).isEqualTo(successful + 1);

    Job broken = job(instance);
    managementService.setJobRetries(broken.getId(), 1);
    assertThat(catchThrowable(() -> managementService.executeJob(broken.getId()))).isNotNull();

    assertThat(counter("fasti.engine.jobs.executed", "result", "failed")).isEqualTo(failed + 1);
    assertThat(gauge("fasti.engine.jobs.failed")).isEqualTo(1);
    assertThat(gauge("fasti.engine.incidents.open")).isEqualTo(1);

    runtimeService.deleteProcessInstance(instance, "test");
    assertThat(gauge("fasti.engine.incidents.open")).isZero();
  }

  @Test
  public void shouldTagEveryMeterWithTheEngineOnly() {
    for (Meter meter : meterRegistry.getMeters()) {
      if (meter.getId().getName().startsWith("fasti.engine.")) {
        assertThat(meter.getId().getTag("engine")).isEqualTo("metricsEngine");
        assertThat(meter.getId().getTags()).hasSizeLessThanOrEqualTo(2);
      }
    }
    assertThat(meterRegistry.find("fasti.engine.external.tasks.open").gauge()).isNotNull();
    assertThat(meterRegistry.find("fasti.engine.jobs.acquisition.attempts").counter()).isNotNull();
  }

  protected Job job(String processInstanceId) {
    return managementService.createJobQuery().processInstanceId(processInstanceId).singleResult();
  }

  protected double counter(String name, String... tags) {
    return meterRegistry.get(name).tag("engine", "metricsEngine").tags(tags).counter().count();
  }

  protected double gauge(String name) {
    return meterRegistry.get(name).tag("engine", "metricsEngine").gauge().value();
  }
}
