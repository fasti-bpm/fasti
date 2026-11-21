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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import dev.fasti.engine.audit.impl.AuditStore;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

/** The create and drop scripts, and how the plugin treats missing tables. */
public class AuditSchemaTest {

  static final List<String> DATABASES = List.of("h2", "postgres", "mysql", "oracle", "mssql", "db2");

  /** The indexes of the event table as this version creates them. */
  static final Set<String> INDEXES = Set.of("FASTI_IDX_AUDIT_CHAIN", "FASTI_IDX_AUDIT_ORDER", "FASTI_IDX_AUDIT_PROCINST",
      "FASTI_IDX_AUDIT_BUSKEY", "FASTI_IDX_AUDIT_ACTOR_ORDER", "FASTI_IDX_AUDIT_TYPE_ORDER");

  /** The indexes of the event table as 1.0 and 1.1 created them. */
  static final Set<String> INDEXES_1_1 = Set.of("FASTI_IDX_AUDIT_CHAIN", "FASTI_IDX_AUDIT_TIME", "FASTI_IDX_AUDIT_PROCINST",
      "FASTI_IDX_AUDIT_BUSKEY", "FASTI_IDX_AUDIT_ACTOR", "FASTI_IDX_AUDIT_TYPE");

  ProcessEngine engine;

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  static boolean present(ProcessEngine engine) {
    return ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getCommandExecutorTxRequired()
        .execute(AuditStore::isTablePresent);
  }

  /** The names of the indexes (of this product) on the event table, upper case. */
  static Set<String> indexes(ProcessEngine engine) {
    return configuration(engine).getCommandExecutorTxRequired().execute(commandContext -> {
      Connection connection = commandContext.getDbSqlSession().getSqlSession().getConnection();
      Set<String> names = new TreeSet<>();
      try {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String table : List.of(AuditStore.EVENT_TABLE, AuditStore.EVENT_TABLE.toLowerCase(Locale.ROOT))) {
          try (ResultSet result = metaData.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
              String name = result.getString("INDEX_NAME");
              if (name != null && name.toUpperCase(Locale.ROOT).startsWith("FASTI_IDX_")) {
                names.add(name.toUpperCase(Locale.ROOT));
              }
            }
          }
        }
      } catch (SQLException e) {
        throw new IllegalStateException(e);
      }
      return names;
    });
  }

  static ProcessEngineConfigurationImpl configuration(ProcessEngine engine) {
    return (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
  }

  /** Runs a script of the class path as the engine runs its schema scripts, statement by statement. */
  static void run(ProcessEngine engine, String resource) {
    configuration(engine).getCommandExecutorTxRequired().execute(commandContext -> {
      commandContext.getDbSqlSession().executeSchemaResource("upgrade", "audit", resource, false);
      return null;
    });
  }

  static String upgradeResource(String databaseType) {
    return "dev/fasti/engine/audit/db/upgrade/fasti." + databaseType + ".upgrade.audit.after_1.1.sql";
  }

  static String resource11(String databaseType, String operation) {
    return "dev/fasti/engine/audit/db/v1.1/fasti." + databaseType + "." + operation + ".audit.sql";
  }

  @Test
  public void shouldCreateAndDropTheTablesOnTheTestDatabase() {
    engine = AuditTestSupport.engine("schema", plugin -> { });
    assertThat(present(engine)).isTrue();
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES);
    AuditTestSupport.dropTables(engine);
    assertThat(present(engine)).isFalse();
    // the indexes went with the table: their names are free again (on PostgreSQL, Oracle and DB2
    // index names are unique in the schema, so creating them again would fail otherwise)
    assertThat(indexes(engine)).isEmpty();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      AuditStore.createTables(commandContext);
      return null;
    });
    assertThat(present(engine)).isTrue();
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES);
  }

  @Test
  public void shouldUpgradeTheTablesOf11AndRunTheUpgradeAgain() {
    engine = AuditTestSupport.engine("upgrade", plugin -> { });
    String type = configuration(engine).getDatabaseType();
    // the scripts of PostgreSQL (CREATE INDEX CONCURRENTLY), Oracle, SQL Server and DB2 do not run
    // inside the transaction of a command; PostgreSQL was run with psql (see the report)
    Assume.assumeTrue(type.equals("h2") || type.equals("mysql"));
    AuditTestSupport.dropTables(engine);
    run(engine, resource11(type, "create"));
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES_1_1);
    AuditTestSupport.sql(engine, "insert into FASTI_AUDIT_EVENT (ID_, CHAIN_ID_, SEQ_, TIME_, TYPE_, PREV_HASH_, HASH_) "
        + "values ('r1', 'old/0', 1, 1, 'audit', 'p', 'h')");

    run(engine, upgradeResource(type));
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES);
    // once more, as after a run that stopped half-way: nothing to create, nothing to drop
    run(engine, upgradeResource(type));
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES);
    assertThat(AuditTestSupport.sql(engine, "update FASTI_AUDIT_EVENT set SEQ_ = 1 where ID_ = 'r1'")).isEqualTo(1);

    // the drop script of 1.1 names the indexes it replaced; the one of this version drops the tables
    assertThatThrownBy(() -> run(engine, resource11(type, "drop"))).isInstanceOf(ProcessEngineException.class);
    AuditTestSupport.dropTables(engine);
    assertThat(present(engine)).isFalse();
  }

  @Test
  public void shouldDropTheTablesOf11ThatWereNotUpgraded() {
    engine = AuditTestSupport.engine("drop11", plugin -> { });
    String type = configuration(engine).getDatabaseType();
    Assume.assumeTrue(type.equals("h2") || type.equals("mysql") || type.equals("postgres"));
    AuditTestSupport.dropTables(engine);
    run(engine, resource11(type, "create"));
    assertThat(indexes(engine)).containsExactlyInAnyOrderElementsOf(INDEXES_1_1);
    AuditTestSupport.dropTables(engine);
    assertThat(present(engine)).isFalse();
    assertThat(indexes(engine)).isEmpty();
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
          .contains("second/end-0", "second/end-3");
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
      // every column of the event table, in every script
      for (String column : AuditStoreColumns.COLUMNS) {
        assertThat(create).as(database + " " + column).containsPattern("\\n\\s+" + Pattern.quote(column) + " ");
      }
      // the indexes of this version, under their names
      assertThat(create).as(database).contains(
          "create index FASTI_IDX_AUDIT_ORDER on FASTI_AUDIT_EVENT(TIME_, CHAIN_ID_, SEQ_)",
          "create index FASTI_IDX_AUDIT_ACTOR_ORDER on FASTI_AUDIT_EVENT(ACTOR_, TIME_, CHAIN_ID_, SEQ_)",
          "create index FASTI_IDX_AUDIT_TYPE_ORDER on FASTI_AUDIT_EVENT(TYPE_, ACTION_, TIME_, CHAIN_ID_, SEQ_)");
      assertThat(create).as(database).doesNotContain("FASTI_IDX_AUDIT_TIME", "FASTI_IDX_AUDIT_ACTOR ", "FASTI_IDX_AUDIT_TYPE ");
      // the indexes go with the table, so the script drops the tables of every version, whichever
      // indexes they have
      assertThat(drop).as(database).doesNotContainPattern("(?im)^\\s*drop index");
      // the upgrade creates the same indexes, then drops the ones they replace
      String upgrade = resource(upgradeResource(database));
      for (String index : List.of("FASTI_IDX_AUDIT_ORDER", "FASTI_IDX_AUDIT_ACTOR_ORDER", "FASTI_IDX_AUDIT_TYPE_ORDER")) {
        assertThat(upgrade).as(database).containsPattern("create index (?:concurrently )?(?:if not exists )?" + index + " ");
      }
      int lastCreate = upgrade.lastIndexOf("create index");
      for (String index : List.of("FASTI_IDX_AUDIT_TIME", "FASTI_IDX_AUDIT_ACTOR", "FASTI_IDX_AUDIT_TYPE")) {
        assertThat(upgrade).as(database).containsPattern("drop index (?:concurrently )?(?:if exists )?" + index + "\\b");
      }
      assertThat(upgrade.indexOf("drop index", lastCreate)).as(database + ": the new indexes first").isPositive();
      assertThat(upgrade.substring(0, lastCreate)).as(database + ": the new indexes first")
          .doesNotContainPattern("(?im)^[^-\\n]*drop index");
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
