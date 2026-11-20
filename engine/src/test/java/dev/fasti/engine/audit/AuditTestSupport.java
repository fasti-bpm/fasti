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
package dev.fasti.engine.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import dev.fasti.engine.audit.impl.AuditStore;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

/**
 * Engines for the audit trail tests. They use the database of the test configuration
 * ({@code camunda.cfg.xml}, so a database profile such as {@code postgresql} applies); an H2
 * in-memory database gets a name of its own per test class, so that the audit tables of one class
 * do not meet another. Other databases are shared: the tests remove their audit tables at the end.
 */
public final class AuditTestSupport {

  private AuditTestSupport() {
  }

  public static ProcessEngineConfigurationImpl configuration(String name) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
        .createProcessEngineConfigurationFromResource("camunda.cfg.xml");
    if (configuration.getJdbcUrl().startsWith("jdbc:h2:mem:")) {
      configuration.setJdbcUrl("jdbc:h2:mem:fasti-audit-" + name + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }
    configuration.setProcessEngineName("audit-" + name);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
    configuration.setJobExecutorActivate(false);
    configuration.setDbMetricsReporterActivate(false);
    configuration.setTaskMetricsEnabled(false);
    configuration.setEnforceHistoryTimeToLive(false);
    return configuration;
  }

  public static ProcessEngine engine(String name, Consumer<AuditTrailPlugin> plugin) {
    return engine(name, plugin, configuration -> { });
  }

  public static ProcessEngine engine(String name, Consumer<AuditTrailPlugin> plugin,
      Consumer<ProcessEngineConfigurationImpl> setup) {
    ProcessEngineConfigurationImpl configuration = configuration(name);
    AuditTrailPlugin auditTrail = new AuditTrailPlugin();
    auditTrail.setNodeId("node-" + name);
    plugin.accept(auditTrail);
    configuration.getProcessEnginePlugins().add(auditTrail);
    setup.accept(configuration);
    return configuration.buildProcessEngine();
  }

  /** Removes what the tests created, and the audit tables (with {@code dropTables}). */
  public static void cleanUp(ProcessEngine engine, boolean dropTables) {
    if (engine == null) {
      return;
    }
    try {
      engine.getIdentityService().clearAuthentication();
      for (Deployment deployment : engine.getRepositoryService().createDeploymentQuery().list()) {
        engine.getRepositoryService().deleteDeployment(deployment.getId(), true);
      }
      for (User user : engine.getIdentityService().createUserQuery().list()) {
        engine.getIdentityService().deleteUser(user.getId());
      }
      for (Group group : engine.getIdentityService().createGroupQuery().list()) {
        engine.getIdentityService().deleteGroup(group.getId());
      }
      if (dropTables) {
        dropTables(engine);
      }
    } finally {
      engine.close();
    }
  }

  public static void dropTables(ProcessEngine engine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    boolean present = configuration.getCommandExecutorTxRequired().execute(AuditStore::isTablePresent);
    if (present) {
      configuration.getCommandExecutorTxRequired().execute(commandContext -> {
        AuditStore.dropTables(commandContext);
        return null;
      });
    }
  }

  /** Runs SQL on the database of the engine, as an intruder with database access would. */
  public static int sql(ProcessEngine engine, String sql, Object... parameters) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    return configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      Connection connection = commandContext.getDbSqlSession().getSqlSession().getConnection();
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        for (int i = 0; i < parameters.length; i++) {
          statement.setObject(i + 1, parameters[i]);
        }
        return statement.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
    });
  }

  public static BpmnModelInstance userTaskProcess(String key) {
    return Bpmn.createExecutableProcess(key).camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("review").name("Review").camundaCandidateUsers("alice,bob")
        .endEvent()
        .done();
  }

  public static ProcessInstance startAs(ProcessEngine engine, String userId, String key, String businessKey) {
    engine.getIdentityService().setAuthenticatedUserId(userId);
    try {
      return engine.getRuntimeService().startProcessInstanceByKey(key, businessKey);
    } finally {
      engine.getIdentityService().clearAuthentication();
    }
  }

  public static void as(ProcessEngine engine, String userId, Runnable action) {
    engine.getIdentityService().setAuthenticatedUserId(userId);
    try {
      action.run();
    } finally {
      engine.getIdentityService().clearAuthentication();
    }
  }

  public static List<AuditEvent> records(ProcessEngine engine) {
    return AuditTrail.of(engine).createQuery().oldestFirst().list();
  }
}
