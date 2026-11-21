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

import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.db.sql.DbSqlSessionFactory;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.test.RequiredDatabase;
import org.camunda.bpm.engine.test.concurrency.ConcurrencyTestHelper;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Two users name each other as substitute at the same time. Neither transaction sees the absence
 * of the other, so the check for a way back must run under the lock that serializes the writes:
 * the second one then waits, sees the first absence and is refused.
 */
public class AbsenceConcurrencyTest extends ConcurrencyTestHelper {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin()));
  protected ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected IdentityService identityService;
  protected AbsenceService absences;

  @Override
  @Before
  public void init() {
    processEngineConfiguration = engineRule.getProcessEngineConfiguration();
    super.init();
    identityService = engineRule.getIdentityService();
    absences = Absences.of(engineRule.getProcessEngine());
    for (String id : List.of("alice", "bob")) {
      User user = identityService.newUser(id);
      identityService.saveUser(user);
    }
  }

  @After
  public void deleteUsers() {
    for (User user : identityService.createUserQuery().list()) {
      identityService.deleteUser(user.getId());
    }
  }

  /** Sets the absence within its command and waits before the command commits. */
  class SetAbsenceCmd extends ControllableCommand<Void> {
    protected final String userId;
    protected final String substituteId;

    SetAbsenceCmd(String userId, String substituteId) {
      this.userId = userId;
      this.substituteId = substituteId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
      monitor.sync();
      absences.setAbsence(userId, null, new Date(System.currentTimeMillis() + 86_400_000L), substituteId, null);
      monitor.sync();
      return null;
    }
  }

  @Test
  // H2 has no exclusive lock (see DbSqlSession#lock)
  @RequiredDatabase(excludes = DbSqlSessionFactory.H2)
  public void shouldRefuseTheSecondOfTwoAbsencesThatNameEachOther() throws InterruptedException {
    ThreadControl first = executeControllableCommand(new SetAbsenceCmd("alice", "bob"));
    first.reportInterrupts();
    first.waitForSync();
    ThreadControl second = executeControllableCommand(new SetAbsenceCmd("bob", "alice"));
    second.reportInterrupts();
    second.waitForSync();

    // alice stores her absence and holds the lock; her transaction is still open
    first.makeContinue();
    first.waitForSync();
    // bob waits for the lock before his absence is checked
    second.ignoreFutureSyncs();
    second.makeContinue();
    Thread.sleep(1500);
    // alice commits, bob goes on
    first.waitUntilDone();
    second.join();

    assertThat(first.getException()).isNull();
    assertThat(second.getException()).isInstanceOf(BadUserRequestException.class);
    assertThat(absences.getAbsence("alice").getSubstituteId()).isEqualTo("bob");
    assertThat(absences.getAbsence("bob")).isNull();
  }
}
