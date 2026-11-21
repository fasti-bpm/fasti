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

  protected static final String JOB_PREDICATE = "RETRIES_ > 0 and SUSPENSION_STATE_ = 1";

  /**
   * The active indexes of the script of the given database type (the commented second group of
   * the scripts is not checked). Keep in step with
   * {@code engine/src/main/resources/dev/fasti/engine/performance/db/optional}.
   */
  public static List<RecommendedIndex> recommendedIndexes(String databaseType) {
    RecommendedIndex jobDueDate = new RecommendedIndex("FASTI_IDX_JOB_DUEDATE", "ACT_RU_JOB", "DUEDATE_");
    if (IndexCatalog.POSTGRES.equals(databaseType)) {
      jobDueDate = jobDueDate.withPartialPredicate(JOB_PREDICATE);
    } else if (IndexCatalog.ORACLE.equals(databaseType)) {
      // a single-column index would not contain the jobs without due date
      jobDueDate = jobDueDate.withNullsIndexed();
    }
    return Collections.unmodifiableList(Arrays.asList(
        jobDueDate,
        new RecommendedIndex("FASTI_IDX_TASK_DUE", "ACT_RU_TASK", "DUE_DATE_"),
        new RecommendedIndex("FASTI_IDX_TASK_ASSIGNEE_DUE", "ACT_RU_TASK", "ASSIGNEE_", "DUE_DATE_"),
        new RecommendedIndex("FASTI_IDX_ID_INFO_USER", "ACT_ID_INFO", "USER_ID_", "KEY_")));
  }

  /**
   * The recommended indexes that are missing. A recommendation for a table of which no index at
   * all is known (the table does not exist, or the catalog could not be read) is not reported.
   */
  public static List<RecommendedIndex> missing(List<RecommendedIndex> recommended, Map<String, List<ExistingIndex>> existing) {
    List<RecommendedIndex> missing = new ArrayList<>();
    for (RecommendedIndex index : recommended) {
      List<ExistingIndex> ofTable = existing.get(index.getTable());
      if (ofTable == null || ofTable.isEmpty()) {
        continue;
      }
      boolean present = false;
      for (ExistingIndex candidate : ofTable) {
        if (index.isSatisfiedBy(candidate)) {
          present = true;
          break;
        }
      }
      if (!present) {
        missing.add(index);
      }
    }
    return missing;
  }

  /**
   * The indexes of the given tables of the engine database, or {@code null} when they cannot be
   * read. Each attempt runs in a command of its own, because on PostgreSQL a refused statement
   * aborts the transaction.
   */
  public static Map<String, List<ExistingIndex>> readIndexes(ProcessEngineConfigurationImpl configuration, Set<String> tables) {
    IndexCatalog catalog = new IndexCatalog(configuration.getDatabaseType(), configuration.getDatabaseSchema(),
        configuration.getDatabaseTablePrefix());
    Map<String, List<ExistingIndex>> indexes = configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
      try {
        return catalog.readFromCatalog(connection(commandContext), tables);
      } catch (SQLException | RuntimeException e) {
        LOG.debug("The index catalog of the {} database could not be read, trying JDBC metadata", configuration.getDatabaseType(), e);
        return null;
      }
    });
    if (indexes == null) {
      indexes = configuration.getCommandExecutorTxRequiresNew().execute(commandContext -> {
        try {
          return catalog.readFromMetaData(connection(commandContext), tables);
        } catch (SQLException | RuntimeException e) {
          LOG.debug("The index metadata of the {} database could not be read", configuration.getDatabaseType(), e);
          return null;
        }
      });
    }
    return indexes;
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
