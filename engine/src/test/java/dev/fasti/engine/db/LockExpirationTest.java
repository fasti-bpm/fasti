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
package dev.fasti.engine.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cmd.AcquireJobsCmd;
import org.camunda.bpm.engine.impl.jobexecutor.AcquiredJobs;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A lock lasts at least as long as it was asked for, on every database. MySQL and MariaDB store
 * times without fraction, so the engine rounds the expiration of a lock up to the whole second
 * there; cut off, a lock of half a second taken at 12:00:00.100 would end at 12:00:00, before it
 * began.
 */
public class LockExpirationTest {

  /** A whole second; the tests lock 100 ms after it. */
  static final long SECOND = 1_795_000_000_000L;

  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl configuration;
  protected RuntimeService runtimeService;
  protected ExternalTaskService externalTaskService;
  protected ManagementService managementService;
  protected int lockTimeInMillis;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    runtimeService = engineRule.getRuntimeService();
    externalTaskService = engineRule.getExternalTaskService();
    managementService = engineRule.getManagementService();
    lockTimeInMillis = configuration.getJobExecutor().getLockTimeInMillis();
  }

  @After
  public void tearDown() {
    configuration.getJobExecutor().setLockTimeInMillis(lockTimeInMillis);
    ClockUtil.reset();
  }

  List<LockedExternalTask> fetchAndLock(String workerId, long lockDuration) {
    return externalTaskService.fetchAndLock(1, workerId).topic("topic", lockDuration).execute();
  }

  @Test
  public void shouldKeepAnExternalTaskLockedForItsLockDuration() {
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().serviceTask("task").camundaExternalTask("topic").endEvent().done());
    ClockUtil.setCurrentTime(new Date(SECOND + 100));
    runtimeService.startProcessInstanceByKey("process");

    List<LockedExternalTask> locked = fetchAndLock("first", 500);

    assertThat(locked).hasSize(1);
    // what the worker is told is what is stored, and not before the time asked for
    Date expiration = locked.get(0).getLockExpirationTime();
    assertThat(expiration.getTime()).isBetween(SECOND + 600, SECOND + 1000);
    assertThat(externalTaskService.createExternalTaskQuery().singleResult().getLockExpirationTime()).isEqualTo(expiration);

    // 400 ms into the 500 ms: still locked
    ClockUtil.setCurrentTime(new Date(SECOND + 500));
    assertThat(fetchAndLock("second", 500)).isEmpty();
    assertThat(externalTaskService.createExternalTaskQuery().locked().count()).isEqualTo(1);

    // afterwards another worker gets it
    ClockUtil.setCurrentTime(new Date(SECOND + 2100));
    assertThat(fetchAndLock("second", 500)).hasSize(1);
  }

  @Test
  public void shouldKeepAnExtendedLockForItsDuration() {
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().serviceTask("task").camundaExternalTask("topic").endEvent().done());
    ClockUtil.setCurrentTime(new Date(SECOND + 100));
    runtimeService.startProcessInstanceByKey("process");
    LockedExternalTask task = fetchAndLock("first", 200).get(0);

    ClockUtil.setCurrentTime(new Date(SECOND + 200));
    externalTaskService.extendLock(task.getId(), "first", 600);

    ClockUtil.setCurrentTime(new Date(SECOND + 700));
    assertThat(fetchAndLock("second", 500)).isEmpty();
  }

  @Test
  public void shouldKeepAJobLockedForTheLockTime() {
    JobExecutor jobExecutor = configuration.getJobExecutor();
    jobExecutor.setLockTimeInMillis(500);
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask("task").camundaAsyncBefore().endEvent().done());
    ClockUtil.setCurrentTime(new Date(SECOND + 100));
    runtimeService.startProcessInstanceByKey("process");
    Job job = managementService.createJobQuery().singleResult();

    AcquiredJobs acquired = configuration.getCommandExecutorTxRequired().execute(new AcquireJobsCmd(jobExecutor));

    assertThat(acquired.contains(job.getId())).isTrue();
    // not before the time asked for (cut off to the second it would be SECOND)
    Date expiration = ((JobEntity) managementService.createJobQuery().jobId(job.getId()).singleResult())
        .getLockExpirationTime();
    assertThat(expiration.getTime()).isBetween(SECOND + 600, SECOND + 1000);
    ClockUtil.setCurrentTime(new Date(SECOND + 500));
    assertThat(configuration.getCommandExecutorTxRequired().execute(new AcquireJobsCmd(jobExecutor)).size()).isZero();

    ClockUtil.setCurrentTime(new Date(SECOND + 2100));
    assertThat(configuration.getCommandExecutorTxRequired().execute(new AcquireJobsCmd(jobExecutor))
        .contains(job.getId())).isTrue();
  }
}
