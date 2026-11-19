/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.api.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;

import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.historycleanup.HistoryCleanupJobHandlerConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.historycleanup.HistoryCleanupSchedulerCmd;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * The history cleanup job can be removed while it is executed, e.g. when another
 * engine reduces the degree of parallelism. Rescheduling it afterwards must not fail.
 */
public class HistoryCleanupRemovedJobTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  protected CommandExecutor commandExecutor;

  @Before
  public void setUp() {
    commandExecutor = engineRule.getProcessEngineConfiguration().getCommandExecutorTxRequired();
  }

  @Test
  public void shouldNotFailToRescheduleRemovedJob() {
    // given
    HistoryCleanupJobHandlerConfiguration configuration = new HistoryCleanupJobHandlerConfiguration();

    // when/then
    assertThatCode(() -> commandExecutor.execute(
        new HistoryCleanupSchedulerCmd(false, new HashMap<>(), configuration, "removedJob")))
      .doesNotThrowAnyException();

    assertThat(engineRule.getManagementService().createJobQuery().count()).isZero();
  }

  @Test
  public void shouldNotFailToRescheduleRemovedJobImmediately() {
    // given
    HistoryCleanupJobHandlerConfiguration configuration = new HistoryCleanupJobHandlerConfiguration();

    // when/then
    assertThatCode(() -> commandExecutor.execute(
        new HistoryCleanupSchedulerCmd(true, new HashMap<>(), configuration, "removedJob")))
      .doesNotThrowAnyException();

    assertThat(engineRule.getManagementService().createJobQuery().count()).isZero();
  }

}
