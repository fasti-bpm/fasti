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
package dev.fasti.engine.performance.impl;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks for engine settings that are known to cost performance as the data grows. Each finding is
 * one message; nothing is changed and nothing here can fail the start of the engine.
 */
public class ConfigAdvisor {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigAdvisor.class);

  public static final String HISTORY_GROWS = "FASTI-PERF-002";
  public static final String END_TIME_CLEANUP = "FASTI-PERF-003";
  public static final String SMALL_POOL = "FASTI-PERF-004";
  public static final String JOBS_WITHOUT_DUE_DATE = "FASTI-PERF-005";
  public static final String DUE_DATE_ORDER_WITH_NULLS = "FASTI-PERF-006";

  /** Connections the engine needs besides one per job thread: job acquisition and one request. */
  protected static final int POOL_MARGIN = 2;

  /**
   * @param jobExecutorActive whether this node executes jobs, where the engine configuration does
   *        not say so (the Spring Boot starter starts the job executor itself); {@code null} to
   *        take it from the configuration
   */
  public static List<String> check(ProcessEngineConfigurationImpl configuration, Boolean jobExecutorActive) {
    return check(configuration, jobExecutorActive, null);
  }

  /**
   * @param jobExecutorActive see {@link #check(ProcessEngineConfigurationImpl, Boolean)}
   * @param jobIndexPresent whether the index check found an index that serves job acquisition with
   *        the settings of this engine ({@code FASTI_IDX_JOB_DUEDATE}); {@code null} if not known
   */
  public static List<String> check(ProcessEngineConfigurationImpl configuration, Boolean jobExecutorActive, Boolean jobIndexPresent) {
    List<String> warnings = new ArrayList<>();
    historyCleanup(configuration, warnings);
    boolean executesJobs = jobExecutorActive != null ? jobExecutorActive : configuration.isJobExecutorActivate();
    if (executesJobs) {
      connectionPool(configuration, warnings);
    }
    jobsWithoutDueDate(configuration, warnings);
    if (executesJobs) {
      acquisitionByDueDate(configuration, jobIndexPresent, warnings);
    }
    return warnings;
  }

  // history ///////////////////////////////////////////////////////////////////////////

  protected static void historyCleanup(ProcessEngineConfigurationImpl configuration, List<String> warnings) {
    HistoryLevel historyLevel = configuration.getHistoryLevel();
    if (historyLevel == null || historyLevel.getId() == HistoryLevel.HISTORY_LEVEL_NONE.getId()) {
      return;
    }
    String strategy = configuration.getHistoryCleanupStrategy();
    Map<?, ?> batchWindows = configuration.getHistoryCleanupBatchWindows();
    if (!configuration.isHistoryCleanupEnabled()) {
      warnings.add(HISTORY_GROWS + " The history level is '" + historyLevel.getName() + "' and this node does not run history "
          + "cleanup (historyCleanupEnabled=false). Unless another node of the cluster runs it (a dedicated cleanup node is a "
          + "common setup, and then this is fine), the history tables grow without limit, and with them every history query "
          + "and the backup (Spring Boot, Run and the container image: "
          + "camunda.bpm.generic-properties.properties.history-cleanup-enabled).");
    } else if (configuration.getHistoryCleanupBatchWindowStartTime() == null && (batchWindows == null || batchWindows.isEmpty())) {
      warnings.add(HISTORY_GROWS + " The history level is '" + historyLevel.getName() + "' and no history cleanup batch window "
          + "is configured (historyCleanupBatchWindowStartTime/EndTime), so cleanup only runs when someone starts it: the "
          + "history tables grow without limit, and with them every history query and the backup. Configure a batch window "
          + "and a history time to live (Spring Boot, Run and the container image: "
          + "camunda.bpm.generic-properties.properties.history-cleanup-batch-window-start-time and ...-end-time, for example in "
          + "a mounted YAML file or SPRING_APPLICATION_JSON), or lower the history level if the history is not needed. Cleanup "
          + "then removes the history whose time to live has passed.");
    }
    if (configuration.isHistoryCleanupEnabled()
        && ProcessEngineConfiguration.HISTORY_CLEANUP_STRATEGY_END_TIME_BASED.equals(strategy)) {
      warnings.add(END_TIME_CLEANUP + " historyCleanupStrategy is 'endTimeBased': cleanup then selects what to remove by "
          + "computing end time plus time to live for each finished instance, which no index supports. The default "
          + "'removalTimeBased' reads the indexed REMOVAL_TIME_ columns instead.");
    }
  }

  // connection pool ///////////////////////////////////////////////////////////////////

  protected static void connectionPool(ProcessEngineConfigurationImpl configuration, List<String> warnings) {
    if (configuration.getJobExecutor() == null) {
      return;
    }
    Integer jobThreads = jobExecutorThreads(configuration.getJobExecutor());
    Integer poolSize = poolSize(configuration);
    if (jobThreads == null || poolSize == null || poolSize <= 0 || jobThreads <= 0) {
      return;
    }
    if (poolSize < jobThreads + POOL_MARGIN) {
      warnings.add(SMALL_POOL + " The connection pool allows " + poolSize + " connections and the job executor up to "
          + jobThreads + " threads: when every job thread is busy, job acquisition and requests wait for a connection. "
          + "Allow at least " + (jobThreads + POOL_MARGIN) + " connections (job threads + " + POOL_MARGIN
          + ", plus the requests served at the same time), or use fewer job threads.");
    }
  }

  protected static Integer jobExecutorThreads(Object jobExecutor) {
    Integer threads = intProperty(jobExecutor, "getMaxPoolSize");
    if (threads == null) {
      // the job executor of the Spring integration delegates to a task executor
      Object taskExecutor = property(jobExecutor, "getTaskExecutor");
      threads = taskExecutor == null ? null : intProperty(taskExecutor, "getMaxPoolSize");
    }
    if (threads == null) {
      Object threadPool = property(jobExecutor, "getThreadPoolExecutor");
      threads = threadPool == null ? null : intProperty(threadPool, "getMaximumPoolSize");
    }
    return threads;
  }

  protected static Integer poolSize(ProcessEngineConfigurationImpl configuration) {
    Object dataSource = configuration.getDataSource();
    // proxies of the Spring integration and of the starter
    for (int i = 0; i < 5 && dataSource != null; i++) {
      Object target = property(dataSource, "getTargetDataSource");
      if (target == null || target == dataSource) {
        break;
      }
      dataSource = target;
    }
    if (dataSource == null) {
      return null;
    }
    // HikariCP, the pool of MyBatis (standalone engine), Tomcat JDBC, Commons DBCP 2
    for (String getter : new String[] { "getMaximumPoolSize", "getPoolMaximumActiveConnections", "getMaxActive", "getMaxTotal" }) {
      Integer size = intProperty(dataSource, getter);
      if (size != null) {
        return size;
      }
    }
    return null;
  }

  protected static Object property(Object target, String getter) {
    try {
      Method method = target.getClass().getMethod(getter);
      return method.invoke(target);
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  protected static Integer intProperty(Object target, String getter) {
    Object value = property(target, getter);
    return value instanceof Integer ? (Integer) value : null;
  }

  // jobs //////////////////////////////////////////////////////////////////////////////

  protected static void jobsWithoutDueDate(ProcessEngineConfigurationImpl configuration, List<String> warnings) {
    if (!configuration.isEnsureJobDueDateNotNull()) {
      return;
    }
    String table = (configuration.getDatabaseTablePrefix() == null ? "" : configuration.getDatabaseTablePrefix()) + "ACT_RU_JOB";
    Boolean found = configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
      Connection connection = commandContext.getDbSqlSession().getSqlSession().getConnection();
      try (Statement statement = connection.createStatement()) {
        statement.setMaxRows(1);
        statement.setQueryTimeout(10);
        try (ResultSet rows = statement.executeQuery("select ID_ from " + table
            + " where DUEDATE_ is null and RETRIES_ > 0 and SUSPENSION_STATE_ = 1")) {
          return rows.next();
        }
      } catch (Exception e) {
        LOG.debug("Could not look for jobs without due date", e);
        return null;
      }
    });
    if (Boolean.TRUE.equals(found)) {
      warnings.add(JOBS_WITHOUT_DUE_DATE + " ensureJobDueDateNotNull is true, but " + table + " holds executable jobs without "
          + "due date. This engine never acquires them. They come from a node that runs without the setting (Camunda 7.24 "
          + "and Fasti nodes with the default). To get them executed, keep at least one node without the setting until none "
          + "are left, or give them a due date (ManagementService#setJobDuedate, or a database administrator runs: update "
          + table + " set DUEDATE_ = <now> where DUEDATE_ is null). Turn the setting on only when every node of the cluster "
          + "sets it.");
    }
  }

  /**
   * Only on the databases that sort NULL last (PostgreSQL, Oracle, Db2): MySQL, MariaDB, SQL Server
   * and H2 sort NULL first, so there "due or without due date, ordered by due date" is one range
   * of an ascending index.
   */
  protected static void acquisitionByDueDate(ProcessEngineConfigurationImpl configuration, Boolean jobIndexPresent,
      List<String> warnings) {
    if (!IndexAdvisor.dueDateOrderWithNulls(configuration)) {
      return;
    }
    String databaseType = configuration.getDatabaseType();
    String advice;
    if (IndexCatalog.POSTGRES.equals(databaseType)) {
      if (Boolean.TRUE.equals(jobIndexPresent)) {
        // the NULLS FIRST index of the script: the planner fetches the due jobs and sorts them
        return;
      }
      advice = "With an ascending index on DUEDATE_, PostgreSQL then walks the whole index whenever nothing is due; the "
          + "optional script creates FASTI_IDX_JOB_DUEDATE NULLS FIRST, which avoids that (FASTI-PERF-001 lists it while it "
          + "is missing or ascending).";
    } else if (IndexCatalog.ORACLE.equals(databaseType) || IndexCatalog.DB2.equals(databaseType)) {
      advice = "The database sorts NULL last, as PostgreSQL does, where an index in due date order made the planner walk the "
          + "whole index whenever nothing was due: check the plan of the acquisition statement on a large ACT_RU_JOB.";
    } else {
      return;
    }
    warnings.add(DUE_DATE_ORDER_WITH_NULLS + " jobExecutorAcquireByDueDate is true and ensureJobDueDateNotNull is false: each "
        + "acquisition asks for the jobs that are due or have no due date, ordered by due date. " + advice
        + " ensureJobDueDateNotNull=true would make it one range, but a node with that setting never acquires the jobs without "
        + "due date that Camunda 7.24 nodes and Fasti nodes with the default write (FASTI-PERF-005), so set it only on every "
        + "node of the cluster at once.");
  }
}
