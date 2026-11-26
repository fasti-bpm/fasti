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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.fasti.engine.performance.impl.ConfigAdvisor;
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
import org.slf4j.LoggerFactory;

/**
 * The checks at engine start against an H2 database of their own: the optional index scripts, the
 * detection of missing and present indexes, and the configuration warnings.
 */
public class PerformanceAdvisorPluginTest {

  protected static final String SCRIPTS = "dev/fasti/engine/performance/db/optional/";
  protected static final String CREATE_SCRIPT = SCRIPTS + "fasti.h2.create.perf.indexes.sql";
  protected static final String DROP_SCRIPT = SCRIPTS + "fasti.h2.drop.perf.indexes.sql";

  protected final String jdbcUrl = "jdbc:h2:mem:fasti-perf-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=10";
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
        .contains("configuration/sql/optional/h2_fasti_perf_indexes_create.sql in Fasti BPM Run")
        .contains("/fasti/configuration/sql/optional/h2_fasti_perf_indexes_create.sql")
        .contains("dev.fasti.platform.distro:fasti-sql-scripts")
        .contains("fasti.performance.index-check=off");
  }

  @Test
  public void shouldFindTheIndexesOfTheCreateScriptAndMissThemAfterTheDropScript() {
    start(configuration -> { });
    List<String> stock = indexSnapshot();

    runScript(CREATE_SCRIPT, false);
    // both scripts can be run again
    runScript(CREATE_SCRIPT, false);
    assertThat(warnings(start(configuration -> { }), PerformanceAdvisorPlugin.MISSING_INDEXES)).isEmpty();
    assertThat(indexSnapshot()).hasSize(stock.size() + IndexAdvisor.recommendedIndexes("h2").size());

    runScript(DROP_SCRIPT, false);
    runScript(DROP_SCRIPT, false);
    List<String> warnings = warnings(start(configuration -> { }), PerformanceAdvisorPlugin.MISSING_INDEXES);
    assertThat(warnings).hasSize(1);
    for (RecommendedIndex index : IndexAdvisor.recommendedIndexes("h2")) {
      assertThat(warnings.get(0)).contains(index.getName());
    }
    assertThat(indexSnapshot()).isEqualTo(stock);
  }

  @Test
  public void shouldRunTheScriptsWithTheCommentedGroupTwiceAndLeaveTheStockSchema() {
    start(configuration -> { });
    List<String> stock = indexSnapshot();

    // with the "-- " of the second group removed, as the script header says
    runScript(CREATE_SCRIPT, true);
    runScript(CREATE_SCRIPT, true);
    List<String> created = indexSnapshot();
    assertThat(created).hasSize(stock.size() + 8).anyMatch(index -> index.startsWith("ACT_HI_PROCINST FASTI_IDX_HI_PI_PDEF_START "));

    runScript(DROP_SCRIPT, false);
    runScript(DROP_SCRIPT, false);
    assertThat(indexSnapshot()).isEqualTo(stock);
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
  public void shouldSkipTheIndexCheckOfADatabaseWithoutCatalogQuery() {
    start(configuration -> { });
    IndexCatalog catalog = new IndexCatalog("unknown", null, null);

    assertThatThrownBy(() -> {
      try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
        catalog.readFromCatalog(connection, Arrays.asList("ACT_RU_TASK"));
      }
    }).isInstanceOf(SQLException.class).hasMessageContaining("no catalog query");
    assertThat(IndexAdvisor.readIndexes((ProcessEngineConfigurationImpl) engines.get(0).getProcessEngineConfiguration(), catalog,
        Collections.singleton("ACT_RU_TASK"))).isNull();
  }

  @Test
  public void shouldLeaveATraceWhenTheCatalogShowsNoneOfTheTables() {
    start(configuration -> { });
    ProcessEngine engine = engines.get(0);
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    execute("create schema EMPTY");
    // as if the tables were in another schema than the configuration says
    configuration.setDatabaseTablePrefix("EMPTY.");
    PerformanceAdvisorPlugin plugin = new PerformanceAdvisorPlugin();
    try (LogCapture log = new LogCapture()) {
      plugin.postProcessEngineBuild(engine);

      assertThat(warnings(plugin, PerformanceAdvisorPlugin.MISSING_INDEXES)).isEmpty();
      assertThat(log.messages(Level.INFO)).anySatisfy(message -> assertThat(message)
          .contains("found none of the tables").contains("schema: EMPTY").contains("databaseTablePrefix"));
    } finally {
      configuration.setDatabaseTablePrefix("");
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
  }

  @Test
  public void shouldKeepWarningOnAnUnknownSwitchValue() {
    PerformanceAdvisorPlugin plugin = new PerformanceAdvisorPlugin();
    try (LogCapture log = new LogCapture()) {
      plugin.setIndexCheck("off");
      // a typo must not keep the engine from starting
      plugin.setIndexCheck("warning");

      assertThat(plugin.getIndexCheck()).isEqualTo("warn");
      assertThat(log.messages(Level.WARN)).anySatisfy(message -> assertThat(message).contains("indexCheck").contains("'warning'"));
    }
    start(plugin, configuration -> { });
    assertThat(warnings(plugin, PerformanceAdvisorPlugin.MISSING_INDEXES)).hasSize(1);
  }

  @Test
  public void shouldLogTheFindingsAtInfoOnH2WithTheAddressOfTheDocumentation() {
    PerformanceAdvisorPlugin plugin;
    try (LogCapture log = new LogCapture()) {
      plugin = start(configuration -> configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL));

      assertThat(log.messages(Level.WARN)).isEmpty();
      assertThat(log.messages(Level.INFO)).anySatisfy(message -> assertThat(message).startsWith(ConfigAdvisor.HISTORY_GROWS + " ")
          .contains(PerformanceAdvisorPlugin.DOCS_URL));
      assertThat(log.messages(Level.INFO)).anySatisfy(message -> assertThat(message).startsWith(PerformanceAdvisorPlugin.MISSING_INDEXES));
    }
    assertThat(PerformanceAdvisorPlugin.DOCS_URL).startsWith("https://github.com/fasti-bpm/fasti/blob/main/docs/performance.md");
    assertThat(plugin.getWarnings()).isNotEmpty();
  }

  // configuration /////////////////////////////////////////////////////////////////////

  @Test
  public void shouldWarnAboutHistoryWithoutCleanupWindow() {
    PerformanceAdvisorPlugin plugin = start(configuration -> configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL));

    List<String> warnings = warnings(plugin, ConfigAdvisor.HISTORY_GROWS);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).contains("'full'").contains("historyCleanupBatchWindowStartTime")
        .contains("camunda.bpm.generic-properties.properties.history-cleanup-batch-window-start-time");
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
    assertThat(warnings(disabled, ConfigAdvisor.HISTORY_GROWS).get(0)).contains("historyCleanupEnabled=false")
        .contains("Unless another node of the cluster runs it");

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
    assertThat(warnings(withJob, ConfigAdvisor.JOBS_WITHOUT_DUE_DATE).get(0)).contains("ACT_RU_JOB").contains("never acquires")
        .contains("ManagementService#setJobDuedate").contains("keep at least one node without the setting");
    // the default does not look at the jobs at all
    assertThat(warnings(start(configuration -> { }), ConfigAdvisor.JOBS_WITHOUT_DUE_DATE)).isEmpty();
  }

  @Test
  public void shouldNotWarnAboutAcquisitionByDueDateOnADatabaseThatSortsNullFirst() {
    // H2, like MySQL, MariaDB and SQL Server, sorts NULL first: one range of the index
    PerformanceAdvisorPlugin byDueDate = start(configuration -> {
      configuration.setJobExecutorAcquireByDueDate(true);
      configuration.setJobExecutorActivate(true);
    });
    assertThat(warnings(byDueDate, ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS)).isEmpty();
  }

  @Test
  public void shouldWarnAboutAcquisitionByDueDateOnlyWhereNullsSortLastAndJobsAreExecuted() {
    assertThat(dueDateOrderWarnings(IndexCatalog.POSTGRES, true, true, null)).hasSize(1).first().asString()
        .contains("NULLS FIRST").contains("never acquires the jobs without due date").contains("FASTI-PERF-005");
    assertThat(dueDateOrderWarnings(IndexCatalog.ORACLE, true, true, null)).hasSize(1);
    assertThat(dueDateOrderWarnings(IndexCatalog.DB2, true, true, null)).hasSize(1);
    // the NULLS FIRST index of the script is there
    assertThat(dueDateOrderWarnings(IndexCatalog.POSTGRES, true, true, true)).isEmpty();
    // NULL first anyway
    for (String databaseType : Arrays.asList(IndexCatalog.MYSQL, IndexCatalog.MSSQL, IndexCatalog.H2)) {
      assertThat(dueDateOrderWarnings(databaseType, true, true, null)).as(databaseType).isEmpty();
    }
    // no job executor on this node, or no ordering by due date
    assertThat(dueDateOrderWarnings(IndexCatalog.POSTGRES, false, true, null)).isEmpty();
    assertThat(dueDateOrderWarnings(IndexCatalog.POSTGRES, true, false, null)).isEmpty();
  }

  @Test
  public void shouldNotWarnAboutAcquisitionByDueDateWhenTheDueDateIsAlwaysSet() {
    PerformanceAdvisorPlugin withDueDates = start(configuration -> {
      configuration.setJobExecutorAcquireByDueDate(true);
      configuration.setEnsureJobDueDateNotNull(true);
      configuration.setJobExecutorActivate(true);
      configuration.setDatabaseType(IndexCatalog.H2);
    });
    assertThat(warnings(withDueDates, ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS)).isEmpty();
  }

  /** The FASTI-PERF-006 findings for a configuration that is not built (no database is read). */
  protected static List<String> dueDateOrderWarnings(String databaseType, boolean jobExecutorActive, boolean acquireByDueDate,
      Boolean jobIndexPresent) {
    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setDatabaseType(databaseType);
    configuration.setJobExecutorAcquireByDueDate(acquireByDueDate);
    List<String> warnings = new ArrayList<>();
    for (String warning : ConfigAdvisor.check(configuration, jobExecutorActive, jobIndexPresent)) {
      if (warning.startsWith(ConfigAdvisor.DUE_DATE_ORDER_WITH_NULLS + " ")) {
        warnings.add(warning);
      }
    }
    return warnings;
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

  /**
   * Runs the statements of a script.
   *
   * @param secondGroup whether the commented statements of the second group are run too, as an
   *        administrator would after removing their "-- "
   */
  protected void runScript(String resource, boolean secondGroup) {
    StringBuilder sql = new StringBuilder();
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (secondGroup && trimmed.startsWith("-- create index")) {
          sql.append(trimmed.substring(3)).append('\n');
        } else if (!trimmed.startsWith("--")) {
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

  /** The indexes of the public schema with their columns, sorted. */
  protected List<String> indexSnapshot() {
    List<String> indexes = new ArrayList<>();
    try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("select TABLE_NAME, INDEX_NAME, listagg(COLUMN_NAME, ',') within group "
            + "(order by ORDINAL_POSITION) from INFORMATION_SCHEMA.INDEX_COLUMNS where TABLE_SCHEMA = 'PUBLIC' "
            + "group by TABLE_NAME, INDEX_NAME order by TABLE_NAME, INDEX_NAME")) {
      while (rows.next()) {
        indexes.add(rows.getString(1) + " " + rows.getString(2) + " (" + rows.getString(3) + ")");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    return indexes;
  }

  /** Collects what the loggers of the performance hints write while it is open. */
  protected static class LogCapture implements AutoCloseable {

    protected final Logger logger = (Logger) LoggerFactory.getLogger("dev.fasti.engine.performance");
    protected final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture() {
      appender.start();
      logger.addAppender(appender);
    }

    public List<String> messages(Level level) {
      List<String> messages = new ArrayList<>();
      for (ILoggingEvent event : appender.list) {
        if (event.getLevel() == level) {
          messages.add(event.getFormattedMessage());
        }
      }
      return messages;
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
