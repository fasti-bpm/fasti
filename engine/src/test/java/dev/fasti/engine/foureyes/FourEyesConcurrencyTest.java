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
package dev.fasti.engine.foureyes;

import static dev.fasti.engine.foureyes.FourEyesModels.CANDIDATES;
import static dev.fasti.engine.foureyes.FourEyesModels.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cmd.CompleteTaskCmd;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.concurrency.ConcurrencyTestHelper;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A checked step and the step that checks it, completed by the same user at the same time in
 * parallel branches: neither transaction sees the other's record, so the control makes them
 * conflict (both update the process instance) and only one commits.
 */
public class FourEyesConcurrencyTest extends ConcurrencyTestHelper {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new FourEyesPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Override
  @Before
  public void init() {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    super.init();
  }

  static class CompleteAsCmd extends ControllableCommand<Void> {
    protected final String taskId;
    protected final String userId;

    CompleteAsCmd(String taskId, String userId) {
      this.taskId = taskId;
      this.userId = userId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
      monitor.sync();
      commandContext.getProcessEngineConfiguration().getIdentityService().setAuthenticatedUserId(userId);
      try {
        commandContext.getProcessEngineConfiguration().getCommandExecutorTxRequired()
            .execute(new CompleteTaskCmd(taskId, null));
      } finally {
        commandContext.getProcessEngineConfiguration().getIdentityService().clearAuthentication();
      }
      monitor.sync();
      return null;
    }
  }

  @Test
  public void shouldLetOnlyOneOfTwoConcurrentCompletionsCommit() {
    // each branch goes on to another task, so the two completions change no common row of the
    // engine by themselves: only the update of the process instance by the control conflicts
    testRule.deploy(rule(Bpmn.createExecutableProcess("parallel").camundaHistoryTimeToLive(180)
        .startEvent().parallelGateway("fork")
        .userTask("checkA").camundaAssignee("alice").userTask("afterA").camundaCandidateUsers(CANDIDATES).endEvent("endA")
        .moveToNode("fork")
        .userTask("checkB").camundaAssignee("alice").userTask("afterB").camundaCandidateUsers(CANDIDATES).endEvent("endB")
        .done(), "checkB", "checkA"));
    String pi = engineRule.getRuntimeService().startProcessInstanceByKey("parallel").getId();
    TaskService taskService = engineRule.getTaskService();
    Task checkA = taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("checkA").singleResult();
    Task checkB = taskService.createTaskQuery().processInstanceId(pi).taskDefinitionKey("checkB").singleResult();

    ThreadControl first = executeControllableCommand(new CompleteAsCmd(checkA.getId(), "alice"));
    first.reportInterrupts();
    first.waitForSync();
    ThreadControl second = executeControllableCommand(new CompleteAsCmd(checkB.getId(), "alice"));
    second.reportInterrupts();
    second.waitForSync();

    // both complete in their transaction without seeing the other
    first.makeContinue();
    first.waitForSync();
    second.makeContinue();
    second.waitForSync();

    first.makeContinue();
    first.waitUntilDone();
    second.makeContinue();
    second.waitUntilDone();

    assertThat(first.getException()).isNull();
    assertThat(second.getException()).isInstanceOf(OptimisticLockingException.class);
    // retried, the completion is refused now that checkA is recorded
    engineRule.getIdentityService().setAuthenticatedUserId("alice");
    try {
      assertThatThrownBy(() -> taskService.complete(checkB.getId())).isInstanceOf(FourEyesViolationException.class);
    } finally {
      engineRule.getIdentityService().clearAuthentication();
    }
  }
}
