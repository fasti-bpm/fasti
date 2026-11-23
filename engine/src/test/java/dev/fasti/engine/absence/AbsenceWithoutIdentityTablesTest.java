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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Rule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * An engine that does not use the identity tables and has none: absences cannot be stored, the
 * feature reports why, and tasks are created as usual.
 */
public class AbsenceWithoutIdentityTablesTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-absence-noidentity;DB_CLOSE_DELAY=10");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("absence-noidentity");
    configuration.setDbIdentityUsed(false);
    configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
  });
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  @Test
  public void shouldReportWhyAbsencesAreUnavailable() {
    AbsenceService absences = Absences.of(engineRule.getProcessEngine());

    assertThat(absences.isEnabled()).isFalse();
    assertThat(absences.getUnavailableReason()).contains("ACT_ID_INFO");
    Date until = Date.from(Instant.now().plus(Duration.ofDays(1)));
    assertThatThrownBy(() -> absences.setAbsence("alice", null, until, "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("ACT_ID_INFO");
    // reading and clearing do not touch the missing table
    assertThat(absences.getAbsence("alice")).isNull();
    absences.clearAbsence("alice");
    assertThat(absences.getAbsencesSubstitutedBy("bob")).isEmpty();
    assertThat(absences.resolveSubstitute("alice")).isNull();
  }

  @Test
  public void shouldCreateTasksAsUsual() {
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask().camundaAssignee("alice").endEvent().done());

    engineRule.getRuntimeService().startProcessInstanceByKey("process");

    assertThat(engineRule.getTaskService().createTaskQuery().singleResult().getAssignee()).isEqualTo("alice");
  }
}
