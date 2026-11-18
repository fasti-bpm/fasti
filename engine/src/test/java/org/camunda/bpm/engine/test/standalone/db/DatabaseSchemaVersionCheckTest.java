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
package org.camunda.bpm.engine.test.standalone.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.db.DatabaseSchemaVersionCheck;
import org.camunda.bpm.engine.impl.db.DatabaseSchemaVersionCheck.Result;
import org.junit.After;
import org.junit.Test;

public class DatabaseSchemaVersionCheckTest {

  protected static final String URL = "jdbc:h2:mem:schemaVersionCheck;DB_CLOSE_DELAY=-1";

  protected ProcessEngine engine;

  @After
  public void tearDown() throws Exception {
    if (engine != null) {
      engine.close();
      engine = null;
    }
    try (Connection connection = DriverManager.getConnection(URL, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute("drop all objects");
    }
  }

  // the check itself //////////////////////////////////////////////

  @Test
  public void shouldAcceptTheRequiredMinorVersion() {
    assertThat(check(Arrays.asList("7.11.0", "7.23.0", "7.24.0"), true)).isEqualTo(Result.MATCHES);
    assertThat(check(Collections.singletonList("7.24.3"), true)).isEqualTo(Result.MATCHES);
  }

  @Test
  public void shouldUseTheHighestVersionWhateverTheOrder() {
    assertThat(check(Arrays.asList("7.24.0", "7.9.0", "7.23.1"), true)).isEqualTo(Result.MATCHES);
  }

  @Test
  public void shouldRejectAnOlderSchema() {
    assertThatThrownBy(() -> check(Arrays.asList("7.21.0", "7.22.0", "7.23.0"), true))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-03111")
        .hasMessageContaining("7.23.0")
        .hasMessageContaining("7.24")
        .hasMessageContaining("sql/upgrade/postgres_engine_");
  }

  @Test
  public void shouldRejectASchemaWithoutSchemaLog() {
    assertThatThrownBy(() -> check(Collections.emptyList(), true))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("created before 7.11");
  }

  @Test
  public void shouldOnlyWarnAboutAnOlderSchemaIfNotEnforced() {
    assertThat(check(Collections.singletonList("7.17.0"), false)).isEqualTo(Result.OLDER);
  }

  @Test
  public void shouldOnlyWarnAboutANewerSchema() {
    assertThat(check(Collections.singletonList("7.25.0"), true)).isEqualTo(Result.NEWER);
    assertThat(check(Collections.singletonList("8.0.0"), true)).isEqualTo(Result.NEWER);
  }

  @Test
  public void shouldIgnoreUnparsableEntriesNextToValidOnes() {
    assertThat(check(Arrays.asList("garbage", "7.24.0-SNAPSHOT"), true)).isEqualTo(Result.MATCHES);
  }

  @Test
  public void shouldReportUnknownIfTheSessionCannotTell() {
    assertThat(check(null, true)).isEqualTo(Result.UNKNOWN);
  }

  @Test
  public void shouldRequireTheVersionThatTheCreateScriptsWrite() throws Exception {
    for (String db : Arrays.asList("h2", "postgres", "mysql", "mssql", "oracle", "db2")) {
      String resource = "org/camunda/bpm/engine/db/create/activiti." + db + ".create.engine.sql";
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
        assertThat(in).as(resource).isNotNull();
        String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(script).as(resource).contains("'" + DatabaseSchemaVersionCheck.REQUIRED_VERSION + ".0'");
      }
    }
  }

  // the check during the engine bootstrap //////////////////////////

  @Test
  public void shouldStartOnASchemaOfTheRequiredVersion() {
    createSchemaThenClose();

    engine = configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE).buildProcessEngine();

    assertThat(engine.getManagementService().createSchemaLogQuery().count()).isPositive();
  }

  @Test
  public void shouldRefuseToStartOnAnOlderSchema() throws Exception {
    createSchemaThenClose();
    setSchemaLog("7.23.0");

    ProcessEngineConfigurationImpl configuration = configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);

    assertThatThrownBy(configuration::buildProcessEngine)
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-03111")
        .hasMessageContaining("7.23.0")
        .hasMessageContaining("sql/upgrade/h2_engine_");
  }

  @Test
  public void shouldRefuseToUpdateAnOlderSchema() throws Exception {
    createSchemaThenClose();
    setSchemaLog("7.20.0");

    ProcessEngineConfigurationImpl configuration = configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

    assertThatThrownBy(configuration::buildProcessEngine)
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("ENGINE-03111");
  }

  @Test
  public void shouldStartOnAnOlderSchemaIfNotEnforced() throws Exception {
    createSchemaThenClose();
    setSchemaLog("7.23.0");

    ProcessEngineConfigurationImpl configuration = configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
    configuration.setEnforceDatabaseSchemaVersion(false);

    engine = configuration.buildProcessEngine();

    assertThat(engine).isNotNull();
  }

  @Test
  public void shouldStillCreateAFreshSchemaWithSchemaUpdateTrue() {
    engine = configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).buildProcessEngine();

    assertThat(engine.getManagementService().createSchemaLogQuery().list())
        .extracting(entry -> entry.getVersion())
        .contains(DatabaseSchemaVersionCheck.REQUIRED_VERSION + ".0");
  }

  protected Result check(List<String> versions, boolean enforce) {
    return new DatabaseSchemaVersionCheck().check(versions, enforce, "postgres");
  }

  protected ProcessEngineConfigurationImpl configuration(String schemaUpdate) {
    ProcessEngineConfigurationImpl configuration = new StandaloneProcessEngineConfiguration();
    configuration.setJdbcUrl(URL)
        .setJdbcUsername("sa")
        .setJdbcPassword("")
        .setDatabaseSchemaUpdate(schemaUpdate)
        .setJobExecutorActivate(false)
        .setProcessEngineName("schemaVersionCheck");
    return configuration;
  }

  protected void createSchemaThenClose() {
    configuration(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE).buildProcessEngine().close();
  }

  protected void setSchemaLog(String version) throws Exception {
    try (Connection connection = DriverManager.getConnection(URL, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute("delete from ACT_GE_SCHEMA_LOG");
      statement.execute("insert into ACT_GE_SCHEMA_LOG values ('0', CURRENT_TIMESTAMP, '" + version + "')");
    }
  }
}
