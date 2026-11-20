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
import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.identity.db.DbReadOnlyIdentityServiceProvider;
import org.camunda.bpm.engine.impl.persistence.GenericManagerFactory;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
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

/**
 * With a read-only identity provider (as LDAP), users cannot be changed through the engine, but
 * their user info still lives in the engine database: absences work.
 */
public class AbsenceReadOnlyIdentityTest {

  static final Instant NOW = Instant.parse("2026-11-21T08:00:00Z");

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(configuration -> {
    configuration.setJdbcUrl("jdbc:h2:mem:fasti-absence-readonly;DB_CLOSE_DELAY=-1");
    configuration.setDatabaseSchemaUpdate("create-drop");
    configuration.setProcessEngineName("absence-readonly");
    configuration.setIdentityProviderSessionFactory(new GenericManagerFactory(DbReadOnlyIdentityServiceProvider.class));
    configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
  });
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected ProcessEngineConfigurationImpl configuration;
  protected AbsenceService absences;

  @Before
  public void setUp() {
    configuration = engineRule.getProcessEngineConfiguration();
    absences = Absences.of(engineRule.getProcessEngine());
    ClockUtil.setCurrentTime(Date.from(NOW));
    // users come from elsewhere (as from a directory)
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      for (String id : List.of("alice", "bob")) {
        UserEntity user = new UserEntity(id);
        commandContext.getDbEntityManager().insert(user);
      }
      return null;
    });
  }

  @After
  public void tearDown() {
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      for (String id : List.of("alice", "bob")) {
        commandContext.getIdentityInfoManager().deleteUserInfoByUserId(id);
        commandContext.getDbEntityManager().delete(commandContext.getDbEntityManager().selectById(UserEntity.class, id));
      }
      return null;
    });
    ClockUtil.reset();
  }

  @Test
  public void shouldSubstituteWithAReadOnlyIdentityProvider() {
    assertThat(engineRule.getIdentityService().isReadOnly()).isTrue();
    assertThat(absences.isEnabled()).isTrue();
    absences.setAbsence("alice", null, Date.from(NOW.plus(Duration.ofDays(1))), "bob", null);
    testRule.deploy(Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
        .startEvent().userTask().camundaAssignee("alice").endEvent().done());

    engineRule.getRuntimeService().startProcessInstanceByKey("process");

    assertThat(engineRule.getTaskService().createTaskQuery().singleResult().getAssignee()).isEqualTo("bob");
  }

  @Test
  public void shouldStillCheckThatTheSubstituteExists() {
    assertThatThrownBy(() -> absences.setAbsence("alice", null, Date.from(NOW.plus(Duration.ofDays(1))), "carol", null))
        .isInstanceOf(BadUserRequestException.class).hasMessageContaining("does not exist");
  }
}
