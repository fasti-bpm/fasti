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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.fasti.engine.absence.impl.AbsenceCache;
import dev.fasti.engine.absence.impl.AbsenceStore;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * How the absences of several users in one command are read: with the optional index on
 * ACT_ID_INFO (USER_ID_, ...) user by user (index seeks), without it all at once (the table is
 * scanned either way), and never into the entity cache of the command.
 */
public class AbsenceLookupStrategyTest {

  protected static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
  protected static final String IDENTITY_INFO_LOGGER = "org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity";

  protected final String jdbcUrl = "jdbc:h2:mem:fasti-absence-lookup-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=10";
  protected final List<ProcessEngine> engines = new ArrayList<>();
  protected final Logger statementLogger = (Logger) LoggerFactory.getLogger(IDENTITY_INFO_LOGGER);
  protected final ListAppender<ILoggingEvent> statements = new ListAppender<>();
  protected Level statementLevel;

  @Before
  public void captureStatements() {
    statementLevel = statementLogger.getLevel();
    statementLogger.setLevel(Level.DEBUG);
    statements.start();
    statementLogger.addAppender(statements);
    ClockUtil.setCurrentTime(Date.from(NOW));
  }

  @After
  public void closeEngines() {
    statementLogger.detachAppender(statements);
    statementLogger.setLevel(statementLevel);
    ClockUtil.reset();
    for (int i = engines.size() - 1; i >= 0; i--) {
      engines.get(i).close();
    }
    execute("drop all objects");
  }

  @Test
  public void shouldReadUserByUserWhereTheUserIndexIsPresent() {
    ProcessEngine first = start();
    engines.remove(first);
    first.close();
    execute("create index DBA_INFO_USER on ACT_ID_INFO (USER_ID_)");
    ProcessEngine engine = start();
    assertThat(cacheFactory(engine).getUserIndexPresent()).isTrue();
    setUpChain(engine);
    statements.list.clear();

    String pi = engine.getRuntimeService().startProcessInstanceByKey("process").getId();

    assertThat(engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult().getAssignee()).isEqualTo("dave");
    // alice, bob, carol and dave one by one through the index; never all absences, which would scan
    assertThat(statements("where USER_ID_ =")).isEqualTo(4);
    assertThat(statements("where KEY_ in")).isZero();
  }

  @Test
  public void shouldReadAllAbsencesAtTheSecondLookupWithoutTheUserIndex() {
    ProcessEngine engine = start();
    assertThat(cacheFactory(engine).getUserIndexPresent()).isFalse();
    setUpChain(engine);
    statements.list.clear();

    String pi = engine.getRuntimeService().startProcessInstanceByKey("process").getId();

    assertThat(engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult().getAssignee()).isEqualTo("dave");
    // each lookup of one user reads the whole table: alice alone, then everything at once
    assertThat(statements("where USER_ID_ =")).isEqualTo(1);
    assertThat(statements("where KEY_ in")).isEqualTo(1);
  }

  @Test
  public void shouldReadCandidatesUserByUserWhereTheUserIndexIsPresent() {
    ProcessEngine first = start();
    engines.remove(first);
    first.close();
    execute("create index DBA_INFO_USER on ACT_ID_INFO (USER_ID_, KEY_)");
    ProcessEngine engine = start();
    createUsers(engine);
    Absences.of(engine).setAbsence("alice", Date.from(NOW.minus(Duration.ofHours(1))), Date.from(NOW.plus(Duration.ofDays(1))),
        "erin", null);
    engine.getRepositoryService().createDeployment().addModelInstance("candidates.bpmn",
        Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
            .startEvent().userTask("task").camundaCandidateUsers("alice,bob,carol").endEvent().done()).deploy();
    statements.list.clear();

    String pi = engine.getRuntimeService().startProcessInstanceByKey("process").getId();

    Task task = engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
    assertThat(engine.getTaskService().getIdentityLinksForTask(task.getId())).extracting(link -> link.getUserId())
        .containsExactlyInAnyOrder("alice", "bob", "carol", "erin");
    assertThat(statements("where KEY_ in")).isZero();
  }

  @Test
  public void shouldReadUserByUserWhenTheIndexesCannotBeTold() {
    AbsenceCache.Factory factory = new AbsenceCache.Factory();
    assertThat(((AbsenceCache) factory.openSession()).isReadAllPreferred()).isFalse();
    factory.setUserIndexPresent(true);
    assertThat(((AbsenceCache) factory.openSession()).isReadAllPreferred()).isFalse();
    factory.setUserIndexPresent(false);
    assertThat(((AbsenceCache) factory.openSession()).isReadAllPreferred()).isTrue();
  }

  @Test
  public void shouldNotPutAllAbsenceRowsIntoTheEntityCache() {
    ProcessEngine engine = start();
    setUpChain(engine);
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();

    int[] cached = configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      Map<String, Absence> all = AbsenceStore.findAll(commandContext);
      int afterReadingAll = commandContext.getDbEntityManager().getDbEntityCache().getEntitiesByType(IdentityInfoEntity.class).size();
      return new int[] { all.size(), afterReadingAll };
    });

    assertThat(cached[0]).isEqualTo(3);
    assertThat(cached[1]).isZero();
  }

  // helpers ///////////////////////////////////////////////////////////////////////////

  protected void setUpChain(ProcessEngine engine) {
    createUsers(engine);
    AbsenceService absences = Absences.of(engine);
    Date from = Date.from(NOW.minus(Duration.ofHours(1)));
    Date until = Date.from(NOW.plus(Duration.ofDays(1)));
    absences.setAbsence("alice", from, until, "bob", null);
    absences.setAbsence("bob", from, until, "carol", null);
    absences.setAbsence("carol", from, until, "dave", null);
    engine.getRepositoryService().createDeployment().addModelInstance("chain.bpmn",
        Bpmn.createExecutableProcess("process").camundaHistoryTimeToLive(180)
            .startEvent().userTask("task").camundaAssignee("alice").endEvent().done()).deploy();
  }

  protected void createUsers(ProcessEngine engine) {
    for (String id : List.of("alice", "bob", "carol", "dave", "erin")) {
      engine.getIdentityService().saveUser(engine.getIdentityService().newUser(id));
    }
  }

  protected long statements(String where) {
    return statements.list.stream().map(ILoggingEvent::getFormattedMessage)
        .filter(message -> message.contains("Preparing:") && message.contains(where)).count();
  }

  protected ProcessEngine start() {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setProcessEngineName("absence-lookup-" + UUID.randomUUID());
    configuration.setJdbcUrl(jdbcUrl);
    configuration.setDatabaseSchemaUpdate("true");
    configuration.setJobExecutorActivate(false);
    configuration.setEnforceHistoryTimeToLive(false);
    configuration.getProcessEnginePlugins().add(new AbsenceSubstitutionPlugin());
    ProcessEngine engine = configuration.buildProcessEngine();
    engines.add(engine);
    return engine;
  }

  protected static AbsenceCache.Factory cacheFactory(ProcessEngine engine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    return configuration.getCustomSessionFactories().stream().filter(AbsenceCache.Factory.class::isInstance)
        .map(AbsenceCache.Factory.class::cast).findFirst().get();
  }

  protected void execute(String sql) {
    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(sql, e);
    }
  }
}
