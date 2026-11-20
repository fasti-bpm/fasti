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

import dev.fasti.engine.absence.impl.AbsenceStore;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/** The default engine of the tests, without the plugin: the feature is off and does nothing. */
public class AbsenceWithoutPluginTest {

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected IdentityService identityService;
  protected AbsenceService absences;

  @Before
  public void setUp() {
    identityService = engineRule.getIdentityService();
    absences = Absences.of(engineRule.getProcessEngine());
    identityService.saveUser(identityService.newUser("alice"));
    identityService.saveUser(identityService.newUser("bob"));
  }

  @After
  public void tearDown() {
    identityService.deleteUser("alice");
    identityService.deleteUser("bob");
  }

  @Test
  public void shouldReportTheFeatureAsOff() {
    assertThat(absences.isEnabled()).isFalse();
    assertThat(absences.getUnavailableReason()).contains("not enabled");
    Date until = Date.from(Instant.now().plus(Duration.ofDays(1)));
    assertThatThrownBy(() -> absences.setAbsence("alice", null, until, "bob", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("fasti.absence.enabled");
    assertThatThrownBy(() -> absences.handOverTasks("alice", 10))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("not enabled");
    assertThat(absences.getAbsence("alice")).isNull();
    assertThat(absences.getAbsencesSubstitutedBy("bob")).isEmpty();
    assertThat(absences.resolveSubstitute("alice")).isNull();
  }

  @Test
  public void shouldNotSubstituteWithoutThePlugin() {
    // an absence stored by a node with the plugin
    identityService.setUserInfo("alice", AbsenceStore.KEY_FROM, Instant.now().minus(Duration.ofHours(1)).toString());
    identityService.setUserInfo("alice", AbsenceStore.KEY_UNTIL, Instant.now().plus(Duration.ofDays(1)).toString());
    identityService.setUserInfo("alice", AbsenceStore.KEY_SUBSTITUTE, "bob");
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask().camundaAssignee("alice").endEvent().done());

    engineRule.getRuntimeService().startProcessInstanceByKey("process");

    assertThat(engineRule.getTaskService().createTaskQuery().singleResult().getAssignee()).isEqualTo("alice");
    // can still be seen and removed
    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
    absences.clearAbsence("alice");
    assertThat(absences.getAbsence("alice")).isNull();
  }
}
