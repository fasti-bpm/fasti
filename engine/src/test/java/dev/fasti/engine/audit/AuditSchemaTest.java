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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fasti.engine.audit.impl.AuditStore;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Test;

/** The create and drop scripts, and how the plugin treats missing tables. */
public class AuditSchemaTest {

  static final List<String> DATABASES = List.of("h2", "postgres", "mysql", "oracle", "mssql", "db2");

  ProcessEngine engine;

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  static boolean present(ProcessEngine engine) {
    return ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getCommandExecutorTxRequired()
        .execute(AuditStore::isTablePresent);
  }

  @Test
  public void shouldCreateAndDropTheTablesOnTheTestDatabase() {
    engine = AuditTestSupport.engine("schema", plugin -> { });
    assertThat(present(engine)).isTrue();
    AuditTestSupport.dropTables(engine);
    assertThat(present(engine)).isFalse();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      AuditStore.createTables(commandContext);
      return null;
    });
    assertThat(present(engine)).isTrue();
  }

  @Test
  public void shouldRefuseToStartWhenTheTablesAreMissingAndMayNotBeCreated() {
    // the engine schema exists (created by a plain engine), the audit tables do not
    ProcessEngineConfigurationImpl plain = AuditTestSupport.configuration("noschema");
    ProcessEngine plainEngine = plain.buildProcessEngine();
    try {
      ProcessEngineConfigurationImpl configuration = AuditTestSupport.configuration("noschema");
      configuration.setProcessEngineName("audit-noschema-2");
      configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
      configuration.getProcessEnginePlugins().add(new AuditTrailPlugin());
      assertThatThrownBy(configuration::buildProcessEngine).isInstanceOf(ProcessEngineException.class)
          .hasMessageContaining("FASTI_AUDIT_EVENT").hasMessageContaining("fasti_audit.sql");
      assertThat(present(plainEngine)).isFalse();
    } finally {
      AuditTestSupport.cleanUp(plainEngine, false);
    }
    engine = AuditTestSupport.engine("schema-after", plugin -> { });
  }

  @Test
  public void shouldStartWithTablesCreatedByTheScriptWhenTheSchemaMayNotBeChanged() {
    ProcessEngine creator = AuditTestSupport.engine("byscript", plugin -> { });
    ProcessEngineConfigurationImpl configuration = AuditTestSupport.configuration("byscript");
    configuration.setProcessEngineName("audit-byscript-2");
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
    AuditTrailPlugin plugin = new AuditTrailPlugin();
    plugin.setNodeId("second");
    configuration.getProcessEnginePlugins().add(plugin);
    engine = configuration.buildProcessEngine();
    try {
      assertThat(AuditTrail.of(engine).verify().getChains()).extracting(AuditVerification.Chain::getChainId)
          .contains("second/0", "second/3");
    } finally {
      creator.close();
    }
  }

  @Test
  public void shouldShipMatchingScriptsForEveryDatabase() throws IOException {
    for (String database : DATABASES) {
      String create = resource(AuditStore.createResource(database));
      String drop = resource(AuditStore.dropResource(database));
      assertThat(create).as(database).contains("create table FASTI_AUDIT_EVENT", "create table FASTI_AUDIT_HEAD",
          "create unique index FASTI_IDX_AUDIT_CHAIN");
      assertThat(drop).as(database).contains("drop table FASTI_AUDIT_EVENT", "drop table FASTI_AUDIT_HEAD");
      // every column of the event table, in every script, and every index is dropped again
      for (String column : AuditStoreColumns.COLUMNS) {
        assertThat(create).as(database + " " + column).containsPattern("\\n\\s+" + Pattern.quote(column) + " ");
      }
      Matcher indexes = Pattern.compile("create (?:unique )?index (\\w+)").matcher(create);
      while (indexes.find()) {
        assertThat(drop).as(database).contains(indexes.group(1));
      }
    }
  }

  static String resource(String name) throws IOException {
    try (InputStream in = AuditSchemaTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as(name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** The columns the store writes. */
  static final class AuditStoreColumns {
    static final List<String> COLUMNS = List.of("ID_", "CHAIN_ID_", "SEQ_", "TIME_", "TYPE_", "ACTION_", "ACTOR_", "ENGINE_",
        "TENANT_ID_", "PROC_DEF_KEY_", "PROC_DEF_ID_", "PROC_INST_ID_", "BUSINESS_KEY_", "ENTITY_TYPE_", "ENTITY_ID_",
        "ENTITY_NAME_", "DETAILS_", "PREV_HASH_", "HASH_");
  }
}
