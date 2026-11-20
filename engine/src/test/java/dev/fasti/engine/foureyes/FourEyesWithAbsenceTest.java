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

import static dev.fasti.engine.foureyes.FourEyesModels.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import dev.fasti.engine.absence.AbsenceService;
import dev.fasti.engine.absence.AbsenceSubstitutionPlugin;
import dev.fasti.engine.absence.Absences;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/** With substitutes during absence, the substitute who completes a step is the one who did it. */
public class FourEyesWithAbsenceTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
    configuration.getProcessEnginePlugins().add(new FourEyesPlugin());
  });
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected IdentityService identityService;
  protected TaskService taskService;
  protected RuntimeService runtimeService;
  protected AbsenceService absences;

  @Before
  public void setUp() {
    identityService = engineRule.getIdentityService();
    taskService = engineRule.getTaskService();
    runtimeService = engineRule.getRuntimeService();
    absences = Absences.of(engineRule.getProcessEngine());
    for (String id : new String[] { "alice", "bob" }) {
      identityService.saveUser(identityService.newUser(id));
    }
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    for (User user : identityService.createUserQuery().list()) {
      identityService.deleteUser(user.getId());
    }
  }

  @Test
  public void shouldTreatTheSubstituteAsTheOneWhoDidTheStep() {
    testRule.deploy(rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").camundaAssignee("alice")
        .userTask("approve").camundaAssignee("alice")
        .endEvent().done(), "approve", "draft"));
    absences.setAbsence("alice", null, Date.from(Instant.now().plus(Duration.ofDays(1))), "bob", null);
    String pi = runtimeService.startProcessInstanceByKey("fourEyes").getId();

    // bob drafts for alice
    Task draft = taskService.createTaskQuery().processInstanceId(pi).singleResult();
    assertThat(draft.getAssignee()).isEqualTo("bob");
    identityService.setAuthenticatedUserId("bob");
    taskService.complete(draft.getId());
    identityService.clearAuthentication();

    // the approval goes to bob as well, who may not approve his own draft
    Task approve = taskService.createTaskQuery().processInstanceId(pi).singleResult();
    assertThat(approve.getAssignee()).isEqualTo("bob");
    identityService.setAuthenticatedUserId("bob");
    assertThat(FourEyes.of(engineRule.getProcessEngine()).getStatus(approve.getId()).isBlocked()).isTrue();
    assertThatThrownBy(() -> taskService.complete(approve.getId())).isInstanceOf(FourEyesViolationException.class);
    identityService.clearAuthentication();

    // alice, back, did not draft and may approve
    absences.clearAbsence("alice");
    taskService.setAssignee(approve.getId(), "alice");
    identityService.setAuthenticatedUserId("alice");
    taskService.complete(approve.getId());
    identityService.clearAuthentication();
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
  }
}
