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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.fasti.engine.performance.impl.ConfigAdvisor;
import dev.fasti.engine.performance.impl.ExistingIndex;
import dev.fasti.engine.performance.impl.IndexAdvisor;
import dev.fasti.engine.performance.impl.IndexCatalog;
import dev.fasti.engine.performance.impl.RecommendedIndex;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance hints at engine start (docs/performance.md). Logs one warning that lists the
 * optional indexes of {@code sql/optional/<database>_fasti_perf_indexes_create.sql} that the
 * engine database does not have, and one warning for each engine setting that is known to cost
 * performance as the data grows; on H2, a development database, these are logged at INFO. It only
 * reads the database catalog (with a query timeout) and the configuration: nothing is created or
 * changed, and a check that cannot be done is skipped, never an error. An unknown value of a
 * switch is logged and treated as {@code warn}.
 *
 * <p>Properties: {@code indexCheck} and {@code configCheck}, each {@code warn} (default) or
 * {@code off}; {@code jobExecutorActive} for integrations that start the job executor
 * themselves.</p>
 */
public class PerformanceAdvisorPlugin implements ProcessEnginePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(PerformanceAdvisorPlugin.class);

  public static final String WARN = "warn";
  public static final String OFF = "off";

  public static final String MISSING_INDEXES = "FASTI-PERF-001";

  /** Where operators read about the hints; a path in the source repository would not help them. */
  public static final String DOCS_URL = "https://github.com/fasti-bpm/fasti/blob/main/docs/performance.md";

  protected String indexCheck = WARN;
  protected String configCheck = WARN;
  protected Boolean jobExecutorActive;
  protected List<String> warnings = Collections.emptyList();
  /** Whether the last index check found an index that serves job acquisition; {@code null} if not known. */
  protected Boolean jobIndexPresent;

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    // nothing to do
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl configuration) {
    // nothing to do
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    List<String> found = new ArrayList<>();
    jobIndexPresent = null;
    // hints only: whatever goes wrong in a check must not keep the engine from starting
    if (WARN.equals(indexCheck)) {
      try {
        String missingIndexes = checkIndexes(configuration, processEngine.getName());
        if (missingIndexes != null) {
          found.add(missingIndexes);
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("The index check of process engine '{}' could not be completed", processEngine.getName(), e);
      }
    }
    if (WARN.equals(configCheck)) {
      try {
        found.addAll(ConfigAdvisor.check(configuration, jobExecutorActive, jobIndexPresent));
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("The configuration check of process engine '{}' could not be completed", processEngine.getName(), e);
      }
    }
    warnings = Collections.unmodifiableList(found);
    // H2 serves development, tests and the demo setups, where the data stays small: hints, not warnings
    boolean development = IndexCatalog.H2.equals(configuration.getDatabaseType());
    for (String warning : found) {
      if (development) {
        LOG.info("{} (process engine '{}'; see {})", warning, processEngine.getName(), DOCS_URL);
      } else {
        LOG.warn("{} (process engine '{}'; see {})", warning, processEngine.getName(), DOCS_URL);
      }
    }
  }

  /** The message about missing indexes, or {@code null} when none is missing or it cannot be told. */
  protected String checkIndexes(ProcessEngineConfigurationImpl configuration, String engineName) {
    List<RecommendedIndex> recommended = IndexAdvisor.recommendedIndexes(configuration);
    Set<String> tables = IndexAdvisor.tables(recommended);
    Map<String, List<ExistingIndex>> indexes = IndexAdvisor.readIndexes(configuration, tables);
    if (indexes == null) {
      return null;
    }
    for (RecommendedIndex index : recommended) {
      if (IndexAdvisor.JOB_DUE_DATE.equals(index.getName())) {
        jobIndexPresent = IndexAdvisor.isPresent(index, indexes);
      }
    }
    if (indexes.isEmpty()) {
      IndexCatalog catalog = new IndexCatalog(configuration.getDatabaseType(), configuration.getDatabaseSchema(),
          configuration.getDatabaseTablePrefix());
      LOG.info("The index check of process engine '{}' found none of the tables {} in the catalog (schema: {}, table name "
          + "prefix: '{}'), so it says nothing about indexes. If the engine tables are in another schema than the default "
          + "schema of the connection, set databaseSchema or databaseTablePrefix to it.", engineName, tables,
          catalog.getSchema() != null ? catalog.getSchema() : "the default of the connection", catalog.getNamePrefix());
      return null;
    }
    List<RecommendedIndex> missing = IndexAdvisor.missing(recommended, indexes);
    if (!missing.isEmpty()) {
      return missingIndexesMessage(configuration.getDatabaseType(), missing);
    }
    LOG.info("The optional performance indexes are present in the database of process engine '{}'", engineName);
    return null;
  }

  protected String missingIndexesMessage(String databaseType, List<RecommendedIndex> missing) {
    StringBuilder message = new StringBuilder(MISSING_INDEXES).append(" Optional performance indexes are missing: ");
    for (int i = 0; i < missing.size(); i++) {
      message.append(i == 0 ? "" : "; ").append(missing.get(i));
    }
    String file = databaseType + "_fasti_perf_indexes_create.sql";
    String script = "sql/optional/" + file;
    message.append(". The engine works without them; they speed up job acquisition and task lists on large tables. To add "
        + "them, let a database administrator run ").append(file).append(": configuration/").append(script)
        .append(" in Fasti BPM Run, /fasti/configuration/").append(script).append(" in the container image, ").append(script)
        .append(" in the Tomcat and WildFly distributions, folder sql/optional of the Maven artifact "
            + "dev.fasti.platform.distro:fasti-sql-scripts for an application with the Spring Boot starter. The indexes are "
            + "additive and safe next to Camunda 7.24 nodes; the matching drop script removes them. Set "
            + "fasti.performance.index-check=off (plugin property indexCheck) to silence this message.");
    return message.toString();
  }

  /**
   * The findings of the last engine start, for tests and diagnostics. Each is logged as a warning,
   * on H2 (development, tests, demo) at INFO.
   */
  public List<String> getWarnings() {
    return warnings;
  }

  public String getIndexCheck() {
    return indexCheck;
  }

  public void setIndexCheck(String indexCheck) {
    this.indexCheck = mode("indexCheck", indexCheck);
  }

  public String getConfigCheck() {
    return configCheck;
  }

  public void setConfigCheck(String configCheck) {
    this.configCheck = mode("configCheck", configCheck);
  }

  public Boolean getJobExecutorActive() {
    return jobExecutorActive;
  }

  /**
   * Whether this node executes jobs, for an integration that starts the job executor itself and
   * therefore leaves {@code jobExecutorActivate} of the engine configuration off (the Spring Boot
   * starter). Not set: the engine configuration says.
   */
  public void setJobExecutorActive(Boolean jobExecutorActive) {
    this.jobExecutorActive = jobExecutorActive;
  }

  /**
   * YAML reads a bare {@code off} as the boolean false, so the boolean spellings are accepted too.
   * Any other value is a typo: it is logged and the check stays on, because the hints must never
   * keep the engine from starting.
   */
  protected static String mode(String property, String value) {
    String mode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    switch (mode) {
      case WARN:
      case "on":
      case "true":
        return WARN;
      case OFF:
      case "false":
        return OFF;
      default:
        LOG.warn("The performance hint switch {} must be '{}' or '{}', not '{}'; using '{}'", property, WARN, OFF, value, WARN);
        return WARN;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
