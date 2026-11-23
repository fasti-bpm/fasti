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
package dev.fasti.engine.absence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/** Without history there are no comments; the substitution and its variables still happen. */
public class AbsenceWithoutHistoryTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-absence-nohistory;DB_CLOSE_DELAY=10");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("absence-nohistory");
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_NONE);
    configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
  });
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @After
  public void tearDown() {
    engineRule.getIdentityService().deleteUser("alice");
    engineRule.getIdentityService().deleteUser("bob");
  }

  @Test
  public void shouldSubstituteWithoutComment() {
    IdentityService identityService = engineRule.getIdentityService();
    identityService.saveUser(identityService.newUser("alice"));
    identityService.saveUser(identityService.newUser("bob"));
    Absences.of(engineRule.getProcessEngine())
        .setAbsence("alice", null, Date.from(Instant.now().plus(Duration.ofDays(1))), "bob", null);
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask().camundaAssignee("alice").endEvent().done());

    engineRule.getRuntimeService().startProcessInstanceByKey("process");

    Task task = engineRule.getTaskService().createTaskQuery().singleResult();
    assertThat(task.getAssignee()).isEqualTo("bob");
    assertThat(engineRule.getTaskService().getVariableLocal(task.getId(), AbsenceService.VARIABLE_ABSENT_ASSIGNEE))
        .isEqualTo("alice");
  }
}
