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

import static dev.fasti.engine.foureyes.FourEyesModels.draftApprove;
import static dev.fasti.engine.foureyes.FourEyesModels.rule;
import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/** Without the plugin the property is an ordinary extension property: nothing is checked or recorded. */
public class FourEyesWithoutPluginTest {

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Test
  public void shouldNotCheckOrRecord() {
    testRule.deploy(draftApprove());
    RuntimeService runtimeService = engineRule.getRuntimeService();
    TaskService taskService = engineRule.getTaskService();
    IdentityService identityService = engineRule.getIdentityService();
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();

    identityService.setAuthenticatedUserId("alice");
    try {
      Task draft = taskService.createTaskQuery().processInstanceId(pi).singleResult();
      taskService.claim(draft.getId(), "alice");
      taskService.complete(draft.getId());
      assertThat(runtimeService.getVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft")).isNull();

      Task approve = taskService.createTaskQuery().processInstanceId(pi).singleResult();
      taskService.claim(approve.getId(), "alice");
      taskService.complete(approve.getId());
    } finally {
      identityService.clearAuthentication();
    }
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }

  @Test
  public void shouldNotValidateTheProperty() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent().userTask("approve").endEvent().done(), "approve", "nothing"));
  }

  @Test
  public void shouldReportTheServiceAsDisabled() {
    testRule.deploy(draftApprove());
    String pi = engineRule.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
    Task draft = engineRule.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();

    FourEyesService fourEyes = FourEyes.of(engineRule.getProcessEngine());

    assertThat(fourEyes.isEnabled()).isFalse();
    FourEyesStatus status = fourEyes.getStatus(draft.getId());
    assertThat(status.isEnabled()).isFalse();
    assertThat(status.isGuarded()).isFalse();
    assertThat(status.isBlocked()).isFalse();
  }
}
