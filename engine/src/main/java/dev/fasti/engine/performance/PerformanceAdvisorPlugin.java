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
 * engine database does not have (on H2, a development database, at INFO), and one warning for
 * each engine setting that is known to cost performance as the data grows. It only reads the database catalog and the configuration:
 * nothing is created or changed, and a check that cannot be done is skipped, never an error.
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

  protected String indexCheck = WARN;
  protected String configCheck = WARN;
  protected Boolean jobExecutorActive;
  protected List<String> warnings = Collections.emptyList();

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
    String missingIndexes = null;
    // hints only: whatever goes wrong in a check must not keep the engine from starting
    if (WARN.equals(indexCheck)) {
      try {
        missingIndexes = checkIndexes(configuration, processEngine.getName());
        if (missingIndexes != null) {
          found.add(missingIndexes);
        }
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("The index check of process engine '{}' could not be completed", processEngine.getName(), e);
      }
    }
    if (WARN.equals(configCheck)) {
      try {
        found.addAll(ConfigAdvisor.check(configuration, jobExecutorActive));
      } catch (RuntimeException | LinkageError e) {
        LOG.debug("The configuration check of process engine '{}' could not be completed", processEngine.getName(), e);
      }
    }
    warnings = Collections.unmodifiableList(found);
    for (String warning : found) {
      if (warning == missingIndexes && IndexCatalog.H2.equals(configuration.getDatabaseType())) {
        // H2 serves development and tests, where the data stays small: a hint, not a warning
        LOG.info("{} (process engine '{}'; see docs/performance.md)", warning, processEngine.getName());
      } else {
        LOG.warn("{} (process engine '{}'; see docs/performance.md)", warning, processEngine.getName());
      }
    }
  }

  /** The message about missing indexes, or {@code null} when none is missing or it cannot be told. */
  protected String checkIndexes(ProcessEngineConfigurationImpl configuration, String engineName) {
    List<RecommendedIndex> recommended = IndexAdvisor.recommendedIndexes(configuration.getDatabaseType());
    Map<String, List<ExistingIndex>> indexes = IndexAdvisor.readIndexes(configuration, IndexAdvisor.tables(recommended));
    if (indexes == null) {
      return null;
    }
    List<RecommendedIndex> missing = IndexAdvisor.missing(recommended, indexes);
    if (!missing.isEmpty()) {
      return missingIndexesMessage(configuration.getDatabaseType(), missing);
    }
    if (!indexes.isEmpty()) {
      LOG.info("The optional performance indexes are present in the database of process engine '{}'", engineName);
    }
    return null;
  }

  protected String missingIndexesMessage(String databaseType, List<RecommendedIndex> missing) {
    StringBuilder message = new StringBuilder(MISSING_INDEXES).append(" Optional performance indexes are missing: ");
    for (int i = 0; i < missing.size(); i++) {
      message.append(i == 0 ? "" : "; ").append(missing.get(i));
    }
    String script = "sql/optional/" + databaseType + "_fasti_perf_indexes_create.sql";
    message.append(". The engine works without them; they speed up job acquisition and task lists on large tables. To add "
        + "them, let a database administrator run ").append(script).append(" of the distribution (configuration/").append(script)
        .append(" in Fasti BPM Run, /fasti/configuration/").append(script).append(" in the container image, ").append(script)
        .append(" in the Tomcat and WildFly distributions). The indexes are additive and safe next to Camunda 7.24 nodes; "
            + "the matching drop script removes them. Set fasti.performance.index-check=off (plugin property indexCheck) to "
            + "silence this message.");
    return message.toString();
  }

  /**
   * The findings of the last engine start, for tests and diagnostics. Each is logged as a warning,
   * except missing indexes on H2 (development and tests), which are logged at INFO.
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
        throw new IllegalArgumentException(property + " must be '" + WARN + "' or '" + OFF + "', not '" + value + "'");
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
