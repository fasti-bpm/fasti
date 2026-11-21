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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares the indexes of the engine database with those of the optional scripts
 * {@code sql/optional/<database>_fasti_perf_indexes_create.sql}. An index counts as present when
 * any usable index of the table starts with the same key columns in the same order, whatever its
 * name, so an index a database administrator created counts too.
 */
public class IndexAdvisor {

  private static final Logger LOG = LoggerFactory.getLogger(IndexAdvisor.class);

  public static final String JOB_DUE_DATE = "FASTI_IDX_JOB_DUEDATE";
  public static final String ID_INFO_USER = "FASTI_IDX_ID_INFO_USER";

  protected static final String JOB_PREDICATE = "RETRIES_ > 0 and SUSPENSION_STATE_ = 1";

  /**
   * The active indexes of the script of the given database type (the commented second group of
   * the scripts is not checked), as job acquisition with the default settings needs them. Keep in
   * step with {@code engine/src/main/resources/dev/fasti/engine/performance/db/optional}.
   */
  public static List<RecommendedIndex> recommendedIndexes(String databaseType) {
    return recommendedIndexes(databaseType, false);
  }

  /** The same for the settings of the engine: see {@link #recommendedIndexes(String, boolean)}. */
  public static List<RecommendedIndex> recommendedIndexes(ProcessEngineConfigurationImpl configuration) {
    return recommendedIndexes(configuration.getDatabaseType(), dueDateOrderWithNulls(configuration));
  }

  /**
   * @param dueDateOrderWithNulls whether job acquisition asks for the jobs that are due or have no
   *        due date ordered by due date ({@code jobExecutorAcquireByDueDate=true},
   *        {@code ensureJobDueDateNotNull=false}): on PostgreSQL the job index must then be
   *        {@code NULLS FIRST}, or the planner walks all of it whenever nothing is due
   */
  public static List<RecommendedIndex> recommendedIndexes(String databaseType, boolean dueDateOrderWithNulls) {
    RecommendedIndex jobDueDate;
    if (IndexCatalog.POSTGRES.equals(databaseType)) {
      // partial, so failed and suspended jobs stay out of it
      jobDueDate = new RecommendedIndex(JOB_DUE_DATE, "ACT_RU_JOB", "DUEDATE_").withPartialPredicate(JOB_PREDICATE);
      if (dueDateOrderWithNulls) {
        jobDueDate = jobDueDate.withNullsFirst();
      }
    } else {
      // the databases without partial indexes: suspended jobs are skipped by the leading column,
      // failed ones inside the index by the last; SUSPENSION_STATE_ is NOT NULL, so the jobs
      // without due date are in the index on Oracle as well
      jobDueDate = new RecommendedIndex(JOB_DUE_DATE, "ACT_RU_JOB", "SUSPENSION_STATE_", "DUEDATE_", "RETRIES_");
    }
    return Collections.unmodifiableList(Arrays.asList(
        jobDueDate,
        new RecommendedIndex("FASTI_IDX_TASK_DUE", "ACT_RU_TASK", "DUE_DATE_"),
        new RecommendedIndex("FASTI_IDX_TASK_ASSIGNEE_DUE", "ACT_RU_TASK", "ASSIGNEE_", "DUE_DATE_"),
        new RecommendedIndex(ID_INFO_USER, "ACT_ID_INFO", "USER_ID_", "KEY_")));
  }

  /** Whether acquisition orders the due jobs and the jobs without due date by due date. */
  public static boolean dueDateOrderWithNulls(ProcessEngineConfigurationImpl configuration) {
    return configuration.isJobExecutorAcquireByDueDate() && !configuration.isEnsureJobDueDateNotNull();
  }

  /** The recommendation of that name for the engine. */
  public static RecommendedIndex recommendedIndex(ProcessEngineConfigurationImpl configuration, String name) {
    for (RecommendedIndex index : recommendedIndexes(configuration)) {
      if (index.getName().equals(name)) {
        return index;
      }
    }
    throw new IllegalArgumentException(name);
  }

  /**
   * The recommended indexes that are missing. A recommendation for a table of which no index at
   * all is known (the table does not exist, or the catalog could not be read) is not reported.
   */
  public static List<RecommendedIndex> missing(List<RecommendedIndex> recommended, Map<String, List<ExistingIndex>> existing) {
    List<RecommendedIndex> missing = new ArrayList<>();
    for (RecommendedIndex index : recommended) {
      if (Boolean.FALSE.equals(isPresent(index, existing))) {
        missing.add(index);
      }
    }
    return missing;
  }

  /**
   * Whether an index serves the recommendation; {@code null} when nothing is known about its table
   * (the table does not exist, or the catalog could not be read).
   */
  public static Boolean isPresent(RecommendedIndex recommended, Map<String, List<ExistingIndex>> existing) {
    List<ExistingIndex> ofTable = existing == null ? null : existing.get(recommended.getTable());
    if (ofTable == null || ofTable.isEmpty()) {
      return null;
    }
    for (ExistingIndex candidate : ofTable) {
      if (recommended.isSatisfiedBy(candidate)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The indexes of the given tables of the engine database, or {@code null} when they cannot be
   * read: no catalog query for the database, no permission, or the catalog did not answer within
   * {@link IndexCatalog#QUERY_TIMEOUT_SECONDS} (a lock wait). Runs in a command of its own, because
   * on PostgreSQL a refused statement aborts the transaction. There is no fallback to JDBC
   * metadata: those calls cannot be given a timeout and wait for the same locks.
   */
  public static Map<String, List<ExistingIndex>> readIndexes(ProcessEngineConfigurationImpl configuration, Set<String> tables) {
    IndexCatalog catalog = new IndexCatalog(configuration.getDatabaseType(), configuration.getDatabaseSchema(),
        configuration.getDatabaseTablePrefix());
    return readIndexes(configuration, catalog, tables);
  }

  public static Map<String, List<ExistingIndex>> readIndexes(ProcessEngineConfigurationImpl configuration, IndexCatalog catalog,
      Set<String> tables) {
    return configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
      try {
        return catalog.readFromCatalog(connection(commandContext), tables);
      } catch (SQLException | RuntimeException e) {
        if (IndexCatalog.isTimeout(e)) {
          LOG.info("The index catalog of the {} database did not answer within {} seconds (waiting for a lock?); the index "
              + "check is skipped", configuration.getDatabaseType(), IndexCatalog.QUERY_TIMEOUT_SECONDS);
          LOG.debug("Timeout of the catalog query", e);
        } else {
          LOG.debug("The index catalog of the {} database could not be read; the index check is skipped",
              configuration.getDatabaseType(), e);
        }
        return null;
      }
    });
  }

  public static Set<String> tables(List<RecommendedIndex> recommended) {
    Set<String> tables = new LinkedHashSet<>();
    for (RecommendedIndex index : recommended) {
      tables.add(index.getTable());
    }
    return tables;
  }

  protected static Connection connection(CommandContext commandContext) {
    return commandContext.getDbSqlSession().getSqlSession().getConnection();
  }
}
