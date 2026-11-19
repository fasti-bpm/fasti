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
package org.camunda.bpm.engine.test.bpmn.callactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A process that calls itself without a wait state creates process instances
 * until the memory is exhausted. The engine has to stop that with an exception.
 */
public class CallActivityRecursionTest {

  /** stops the recursion of the test processes in case the engine does not */
  protected static final int SAFETY_LIMIT = 5000;

  protected static final AtomicInteger STARTED_INSTANCES = new AtomicInteger();

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected RuntimeService runtimeService;

  @Before
  public void setUp() {
    runtimeService = engineRule.getRuntimeService();
    STARTED_INSTANCES.set(0);
  }

  @Test
  public void shouldStopProcessThatCallsItselfEndlessly() {
    // given
    testRule.deploy(recursiveProcess());

    // when/then
    assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("recursiveProcess"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("recursiveProcess")
      .hasMessageContaining("callItself")
      .hasMessageContaining("nested process instances");

    assertThat(STARTED_INSTANCES.get()).isLessThan(SAFETY_LIMIT);
    assertThat(runtimeService.createProcessInstanceQuery().count()).isZero();
  }

  @Test
  public void shouldStopProcessesThatCallEachOtherEndlessly() {
    // given
    testRule.deploy(
        Bpmn.createExecutableProcess("ping")
          .camundaHistoryTimeToLive(180)
          .startEvent()
            .camundaExecutionListenerClass(ExecutionListener.EVENTNAME_START, CountingListener.class)
          .callActivity("callPong")
            .calledElement("pong")
          .endEvent()
          .done(),
        Bpmn.createExecutableProcess("pong")
          .camundaHistoryTimeToLive(180)
          .startEvent()
          .callActivity("callPing")
            .calledElement("ping")
          .endEvent()
          .done());

    // when/then
    assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("ping"))
      .isInstanceOf(ProcessEngineException.class)
      .hasMessageContaining("nested process instances");

    assertThat(STARTED_INSTANCES.get()).isLessThan(SAFETY_LIMIT);
    assertThat(runtimeService.createProcessInstanceQuery().count()).isZero();
  }

  @Test
  public void shouldCompleteProcessThatCallsItselfWithEndCondition() {
    // given a recursion that ends after some levels
    testRule.deploy(Bpmn.createExecutableProcess("countdown")
        .camundaHistoryTimeToLive(180)
        .startEvent()
          .camundaExecutionListenerClass(ExecutionListener.EVENTNAME_START, CountingListener.class)
        .exclusiveGateway("levelsLeft")
          .condition("yes", "${level > 0}")
          .callActivity("callItself")
            .calledElement("countdown")
            .camundaIn("level", "level")
          .endEvent()
        .moveToNode("levelsLeft")
          .condition("no", "${level <= 0}")
          .endEvent()
        .done());

    // when
    runtimeService.startProcessInstanceByKey("countdown", Variables.createVariables().putValue("level", 50));

    // then
    assertThat(STARTED_INSTANCES.get()).isEqualTo(50);
    assertThat(runtimeService.createProcessInstanceQuery().count()).isZero();
  }

  public static class CountingListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) throws Exception {
      if (STARTED_INSTANCES.incrementAndGet() >= SAFETY_LIMIT) {
        throw new IllegalStateException("The engine did not stop the recursion of process " + execution.getProcessDefinitionId());
      }

      Object level = execution.getVariable("level");
      if (level != null) {
        // every instance of the process consumes one level
        execution.setVariable("level", ((Number) level).intValue() - 1);
      }
    }
  }

  @Test
  public void shouldStopRecursionAtConfiguredNestingDepth() {
    // given
    ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();
    int defaultNestingDepth = configuration.getMaxCallActivityNestingDepth();
    configuration.setMaxCallActivityNestingDepth(10);

    testRule.deploy(recursiveProcess());

    try {
      // when/then
      assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("recursiveProcess"))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("10 nested process instances");

      // the instance started by the API call and 10 nested instances
      assertThat(STARTED_INSTANCES.get()).isEqualTo(11);
    } finally {
      configuration.setMaxCallActivityNestingDepth(defaultNestingDepth);
    }
  }

  @Test
  public void shouldNotLimitNestingDepthIfDisabled() {
    // given
    ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();
    int defaultNestingDepth = configuration.getMaxCallActivityNestingDepth();
    configuration.setMaxCallActivityNestingDepth(0);

    testRule.deploy(recursiveProcess());

    try {
      // when/then the recursion is only stopped by the listener of the test process
      assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("recursiveProcess"))
        .hasMessageContaining("The engine did not stop the recursion");

      assertThat(STARTED_INSTANCES.get()).isEqualTo(SAFETY_LIMIT);
    } finally {
      configuration.setMaxCallActivityNestingDepth(defaultNestingDepth);
    }
  }

  @Test
  public void shouldCountNestingDepthPerCommand() {
    // given a recursion with a wait state on every level and a limit of two nested instances
    ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();
    int defaultNestingDepth = configuration.getMaxCallActivityNestingDepth();
    configuration.setMaxCallActivityNestingDepth(2);

    testRule.deploy(Bpmn.createExecutableProcess("waitingRecursiveProcess")
        .camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("wait")
        .callActivity("callItself")
          .calledElement("waitingRecursiveProcess")
        .endEvent()
        .done());

    try {
      // when every level is started by a command of its own
      runtimeService.startProcessInstanceByKey("waitingRecursiveProcess");
      for (int level = 1; level <= 5; level++) {
        Task task = engineRule.getTaskService().createTaskQuery().singleResult();
        engineRule.getTaskService().complete(task.getId());
      }

      // then the limit does not apply
      assertThat(runtimeService.createProcessInstanceQuery().count()).isEqualTo(6);
    } finally {
      configuration.setMaxCallActivityNestingDepth(defaultNestingDepth);
    }
  }

  protected BpmnModelInstance recursiveProcess() {
    return Bpmn.createExecutableProcess("recursiveProcess")
        .camundaHistoryTimeToLive(180)
        .startEvent()
          .camundaExecutionListenerClass(ExecutionListener.EVENTNAME_START, CountingListener.class)
        .callActivity("callItself")
          .calledElement("recursiveProcess")
        .endEvent()
        .done();
  }
}
