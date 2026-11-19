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
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A task completed by two transactions at once: the one that loses the race must
 * fail with an {@link OptimisticLockingException}, not a NullPointerException.
 */
public class ConcurrentTaskCompletionTest {

  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected ProcessEngineConfigurationImpl configuration;

  @Before
  public void setUp() {
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    configuration = engineRule.getProcessEngineConfiguration();
  }

  @Test
  public void shouldReportConcurrentCompletionAsOptimisticLocking() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("process")
        .startEvent().userTask("task").endEvent().done();
    testRule.deploy(model);
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("process");
    String taskId = taskService.createTaskQuery().singleResult().getId();

    // the losing transaction reads the task, then the winning one completes it
    // and commits before the loser goes on to complete it as well
    assertThatThrownBy(() -> configuration.getCommandExecutorTxRequired()
        .execute(commandContext -> {
          TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
          configuration.getCommandExecutorTxRequiresNew().execute(winner -> {
            taskService.complete(taskId);
            return null;
          });
          task.complete();
          return null;
        }))
        .isInstanceOf(OptimisticLockingException.class)
        .hasMessageContaining(taskId);

    assertThat(runtimeService.createProcessInstanceQuery()
        .processInstanceId(instance.getId()).count()).isZero();
    Task remaining = taskService.createTaskQuery().taskId(taskId).singleResult();
    assertThat(remaining).isNull();
  }
}
