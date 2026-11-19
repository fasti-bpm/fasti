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
