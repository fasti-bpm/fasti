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
package dev.fasti.engine.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import dev.fasti.engine.performance.impl.ConfigAdvisor;
import dev.fasti.engine.performance.impl.ExistingIndex;
import dev.fasti.engine.performance.impl.IndexAdvisor;
import dev.fasti.engine.performance.impl.IndexCatalog;
import dev.fasti.engine.performance.impl.RecommendedIndex;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Test;

/**
 * The checks at engine start against an H2 database of their own: the optional index scripts, the
 * detection of missing and present indexes, and the configuration warnings.
 */
public class PerformanceAdvisorPluginTest {

  protected static final String SCRIPTS = "dev/fasti/engine/performance/db/optional/";
  protected static final String CREATE_SCRIPT = SCRIPTS + "fasti.h2.create.perf.indexes.sql";
  protected static final String DROP_SCRIPT = SCRIPTS + "fasti.h2.drop.perf.indexes.sql";

  protected final String jdbcUrl = "jdbc:h2:mem:fasti-perf-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
  protected final List<ProcessEngine> engines = new ArrayList<>();

  @After
  public void closeEngines() {
    for (int i = engines.size() - 1; i >= 0; i--) {
      engines.get(i).close();
    }
    execute("drop all objects");
  }

  // indexes ///////////////////////////////////////////////////////////////////////////

  @Test
  public void shouldListEveryOptionalIndexOfAStockSchemaInOneWarning() {
    PerformanceAdvisorPlugin plugin = start(configuration -> { });

    List<String> warnings = warnings(plugin, PerformanceAdvisorPlugin.MISSING_INDEXES);
    assertThat(warnings).hasSize(1);
    for (RecommendedIndex index : IndexAdvisor.recommendedIndexes("h2")) {
      assertThat(warnings.get(0)).contains(index.getName()).contains(index.getTable());
    }
    assertThat(warnings.get(0))
        .contains("(configuration/sql/optional/h2_fasti_perf_indexes_create.sql")
        .contains("/fasti/configuration/sql/optional/h2_fasti_perf_indexes_create.sql")
        .contains("fasti.performance.index-check=off");
  }

  @Test
  public void shouldFindTheIndexesOfTheCreateScriptAndMissThemAfterTheDropScript() {
    start(configuration -> { });

    runScript(CREATE_SCRIPT);
    // both scripts can be run again
    runScript(CREATE_SCRIPT);
    assertThat(warnings(start(configuration -> { }), PerformanceAdvisorPlugin.MISSING_INDEXES)).isEmpty();

    runScript(DROP_SCRIPT);
    runScript(DROP_SCRIPT);
    List<String> warnings = warnings(start(configuration -> { }), PerformanceAdvisorPlugin.MISSING_INDEXES);
    assertThat(warnings).hasSize(1);
    for (RecommendedIndex index : IndexAdvisor.recommendedIndexes("h2")) {
      assertThat(warnings.get(0)).contains(index.getName());
    }
  }

  @Test
  public void shouldAcceptAnIndexWithAnotherNameAndFurtherColumns() {
    start(configuration -> { });
    execute("create index DBA_TASK_DUE on ACT_RU_TASK (DUE_DATE_, PRIORITY_)");
    execute("create index DBA_INFO on ACT_ID_INFO (USER_ID_, KEY_, TYPE_)");
    // the same columns in another order do not serve the same queries
    execute("create index DBA_DUE_ASSIGNEE on ACT_RU_TASK (DUE_DATE_, ASSIGNEE_)");

    List<String> warnings = warnings(start(configuration -> { }), PerformanceAdvisorPlugin.MISSING_INDEXES);

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).doesNotContain("FASTI_IDX_TASK_DUE ").doesNotContain("FASTI_IDX_ID_INFO_USER")
        .contains("FASTI_IDX_TASK_ASSIGNEE_DUE").contains("FASTI_IDX_JOB_DUEDATE");
  }

  @Test
  public void shouldFindIndexesBehindATablePrefix() {
    // the tables are in the schema BPM, which is not the default schema of the connections
    execute("create schema BPM");
    start(configuration -> configuration.setJdbcUrl(jdbcUrl + ";SCHEMA=BPM"));
    engines.remove(0).close();
    Consumer<ProcessEngineConfigurationImpl> prefixed = configuration -> {
      configuration.setDatabaseTablePrefix("BPM.");
      configuration.setDatabaseSchemaUpdate("false");
    };
    assertThat(warnings(start(prefixed), PerformanceAdvisorPlugin.MISSING_INDEXES)).hasSize(1);

    execute("create index BPM.DBA_TASK_DUE on BPM.ACT_RU_TASK (DUE_DATE_)");

    List<String> warnings = warnings(start(prefixed), PerformanceAdvisorPlugin.MISSING_INDEXES);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).doesNotContain("FASTI_IDX_TASK_DUE ").contains("FASTI_IDX_TASK_ASSIGNEE_DUE");
  }

  @Test
  public void shouldSayNothingAboutATableThatIsNotThere() {
    start(configuration -> { });
    engines.remove(0).close();
    execute("drop table ACT_ID_INFO");

    PerformanceAdvisorPlugin plugin = start(configuration -> {
      configuration.setDbIdentityUsed(false);
      configuration.setDatabaseSchemaUpdate("false");
    });

    List<String> warnings = warnings(plugin, PerformanceAdvisorPlugin.MISSING_INDEXES);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).doesNotContain("ACT_ID_INFO").contains("FASTI_IDX_TASK_DUE ");
  }

  @Test
  public void shouldReadIndexesThroughJdbcMetadataOfAnUnknownDatabase() throws SQLException {
    start(configuration -> { });
    execute("create index DBA_TASK_DUE on ACT_RU_TASK (DUE_DATE_, PRIORITY_)");
    IndexCatalog catalog = new IndexCatalog("unknown", null, null);

    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
      assertThatThrownBy(() -> catalog.readFromCatalog(connection, Arrays.asList("ACT_RU_TASK"))).isInstanceOf(SQLException.class);

      Map<String, List<ExistingIndex>> indexes = catalog.readFromMetaData(connection, Arrays.asList("ACT_RU_TASK", "NO_SUCH_TABLE"));

      assertThat(indexes).containsOnlyKeys("ACT_RU_TASK");
      assertThat(indexes.get("ACT_RU_TASK")).anySatisfy(index -> {
        assertThat(index.getName()).isEqualTo("DBA_TASK_DUE");
        assertThat(index.getColumns()).containsExactly("DUE_DATE_", "PRIORITY_");
      });
      assertThat(indexes.get("ACT_RU_TASK")).anySatisfy(index -> assertThat(index.getColumns()).containsExactly("ID_"));
    }
  }

  @Test
  public void shouldStaySilentWhenSwitchedOff() {
    PerformanceAdvisorPlugin plugin = new PerformanceAdvisorPlugin();
    plugin.setIndexCheck("off");
    plugin.setConfigCheck("OFF");

    start(plugin, configuration -> configuration.setHistoryCleanupStrategy("endTimeBased"));

    assertThat(plugin.getWarnings()).isEmpty();
  }

  @Test
  public void shouldAcceptTheBooleanSpellingsOfYaml() {
    PerformanceAdvisorPlugin plugin = new PerformanceAdvisorPlugin();
    plugin.setIndexCheck("false");
    plugin.setConfigCheck("true");

    assertThat(plugin.getIndexCheck()).isEqualTo("off");
    assertThat(plugin.getConfigCheck()).isEqualTo("warn");
    assertThatThrownBy(() -> plugin.setIndexCheck("fail")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("indexCheck");
  }

  // configuration /////////////////////////////////////////////////////////////////////

  @Test
  public void shouldWarnAboutHistoryWithoutCleanupWindow() {
    PerformanceAdvisorPlugin plugin = start(configuration -> configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL));

    List<String> warnings = warnings(plugin, ConfigAdvisor.HISTORY_GROWS);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).contains("'full'").contains("historyCleanupBatchWindowStartTime");
    assertThat(warnings(plugin, ConfigAdvisor.END_TIME_CLEANUP)).isEmpty();
  }

  @Test
  public void shouldNotWarnAboutHistoryWithCleanupWindow() {
    PerformanceAdvisorPlugin withWindow = start(configuration -> {
      configuration.setHistoryCleanupBatchWindowStartTime("22:00");
      configuration.setHistoryCleanupBatchWindowEndTime("05:00");
    });
    assertThat(warnings(withWindow, ConfigAdvisor.HISTORY_GROWS)).isEmpty();

    PerformanceAdvisorPlugin weekdayWindow = start(configuration -> {
      configuration.setSaturdayHistoryCleanupBatchWindowStartTime("10:00");
      configuration.setSaturdayHistoryCleanupBatchWindowEndTime("18:00");
    });
    assertThat(warnings(weekdayWindow, ConfigAdvisor.HISTORY_GROWS)).isEmpty();
  }

  @Test
  public void shouldNotWarnAboutCleanupWithoutHistory() {
    PerformanceAdvisorPlugin noHistory = start(configuration -> configuration.setHistory(ProcessEngineConfiguration.HISTORY_NONE));

    assertThat(warnings(noHistory, ConfigAdvisor.HISTORY_GROWS)).isEmpty();
  }

  @Test
  public void shouldWarnAboutDisabledCleanupAndEndTimeStrategy() {
    PerformanceAdvisorPlugin disabled = start(configuration -> {
      configuration.setHistoryCleanupBatchWindowStartTime("22:00");
      configuration.setHistoryCleanupEnabled(false);
    });
    assertThat(warnings(disabled, ConfigAdvisor.HISTORY_GROWS)).hasSize(1);
    assertThat(warnings(disabled, ConfigAdvisor.HISTORY_GROWS).get(0)).contains("historyCleanupEnabled=false");

    PerformanceAdvisorPlugin endTime = start(configuration -> {
      configuration.setHistoryCleanupBatchWindowStartTime("22:00");
      configuration.setHistoryCleanupStrategy(ProcessEngineConfiguration.HISTORY_CLEANUP_STRATEGY_END_TIME_BASED);
    });
    assertThat(warnings(endTime, ConfigAdvisor.HISTORY_GROWS)).isEmpty();
    assertThat(warnings(endTime, ConfigAdvisor.END_TIME_CLEANUP)).hasSize(1);
  }

  @Test
  public void shouldWarnWhenThePoolIsSmallerThanTheJobExecutorNeeds() {
    PerformanceAdvisorPlugin small = start(configuration -> {
      configuration.setJdbcMaxActiveConnections(10);
      configuration.setJobExecutor(jobExecutor(10));
      configuration.setJobExecutorActivate(true);
    });
    List<String> warnings = warnings(small, ConfigAdvisor.SMALL_POOL);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).contains("10 connections").contains("10 threads").contains("at least 12");

    PerformanceAdvisorPlugin enough = start(configuration -> {
      configuration.setJdbcMaxActiveConnections(12);
      configuration.setJobExecutor(jobExecutor(10));
      configuration.setJobExecutorActivate(true);
    });
    assertThat(warnings(enough, ConfigAdvisor.SMALL_POOL)).isEmpty();

    // without job execution on this node the pool only serves requests
    PerformanceAdvisorPlugin inactive = start(configuration -> {
      configuration.setJdbcMaxActiveConnections(2);
      configuration.setJobExecutor(jobExecutor(10));
    });
    assertThat(warnings(inactive, ConfigAdvisor.SMALL_POOL)).isEmpty();
  }

  @Test
  public void shouldWarnAboutJobsWithoutDueDateWhenDueDatesAreExpected() {
    start(configuration -> { });
    ProcessEngine engine = engines.get(0);
    engine.getRepositoryService().createDeployment().addModelInstance("async.bpmn",
        Bpmn.createExecutableProcess("async").camundaHistoryTimeToLive(1)
            .startEvent().camundaAsyncBefore().userTask().endEvent().done()).deploy();

    PerformanceAdvisorPlugin noJobs = start(configuration -> configuration.setEnsureJobDueDateNotNull(true));
    assertThat(warnings(noJobs, ConfigAdvisor.JOBS_WITHOUT_DUE_DATE)).isEmpty();

    // written by a node without the setting
    engine.getRuntimeService().startProcessInstanceByKey("async");

    PerformanceAdvisorPlugin withJob = start(configuration -> configuration.setEnsureJobDueDateNotNull(true));
    assertThat(warnings(withJob, ConfigAdvisor.JOBS_WITHOUT_DUE_DATE)).hasSize(1);
    assertThat(warnings(withJob, ConfigAdvisor.JOBS_WITHOUT_DUE_DATE).get(0)).contains("ACT_RU_JOB").contains("never acquires");
    // the default does not look at the jobs at all
    assertThat(warnings(start(configuration -> { }), ConfigAdvisor.JOBS_WITHOUT_DUE_DATE)).isEmpty();
  }

  @Test
  public void shouldWarnAboutAcquisitionByDueDateWhileJobsMayHaveNone() {
    PerformanceAdvisorPlugin byDueDate = start(configuration -> configuration.setJobExecutorAcquireByDueDate(true));
    assertThat(warnings(byDueDate, ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS)).hasSize(1);

    PerformanceAdvisorPlugin withDueDates = start(configuration -> {
      configuration.setJobExecutorAcquireByDueDate(true);
      configuration.setEnsureJobDueDateNotNull(true);
    });
    assertThat(warnings(withDueDates, ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS)).isEmpty();
    assertThat(warnings(start(configuration -> { }), ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS)).isEmpty();
  }

  // helpers ///////////////////////////////////////////////////////////////////////////

  protected PerformanceAdvisorPlugin start(Consumer<ProcessEngineConfigurationImpl> customizer) {
    return start(new PerformanceAdvisorPlugin(), customizer);
  }

  protected PerformanceAdvisorPlugin start(PerformanceAdvisorPlugin plugin, Consumer<ProcessEngineConfigurationImpl> customizer) {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setProcessEngineName("perf-" + UUID.randomUUID());
    configuration.setJdbcUrl(jdbcUrl);
    configuration.setDatabaseSchemaUpdate("true");
    configuration.setJobExecutorActivate(false);
    configuration.setEnforceHistoryTimeToLive(false);
    configuration.getProcessEnginePlugins().add(plugin);
    customizer.accept(configuration);
    engines.add(configuration.buildProcessEngine());
    return plugin;
  }

  protected static DefaultJobExecutor jobExecutor(int maxPoolSize) {
    DefaultJobExecutor jobExecutor = new DefaultJobExecutor();
    jobExecutor.setMaxPoolSize(maxPoolSize);
    return jobExecutor;
  }

  protected static List<String> warnings(PerformanceAdvisorPlugin plugin, String id) {
    List<String> warnings = new ArrayList<>();
    for (String warning : plugin.getWarnings()) {
      if (warning.startsWith(id + " ")) {
        warnings.add(warning);
      }
    }
    return warnings;
  }

  protected void execute(String sql) {
    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(sql, e);
    }
  }

  protected void runScript(String resource) {
    StringBuilder sql = new StringBuilder();
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.trim().startsWith("--")) {
          sql.append(line).append('\n');
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(resource, e);
    }
    int statements = 0;
    for (String statement : sql.toString().split(";")) {
      if (!statement.trim().isEmpty()) {
        execute(statement);
        statements++;
      }
    }
    assertThat(statements).isPositive();
  }
}
