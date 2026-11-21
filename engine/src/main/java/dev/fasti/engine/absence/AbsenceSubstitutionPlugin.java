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
package dev.fasti.engine.absence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.fasti.engine.absence.impl.AbsenceBpmnParseListener;
import dev.fasti.engine.absence.impl.AbsenceCache;
import dev.fasti.engine.absence.impl.AbsenceCmmnTransformListener;
import dev.fasti.engine.absence.impl.AbsenceSettings;
import dev.fasti.engine.performance.impl.ExistingIndex;
import dev.fasti.engine.performance.impl.IndexAdvisor;
import dev.fasti.engine.performance.impl.RecommendedIndex;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Substitutes during absence: while a user is absent, user tasks (BPMN and CMMN) that are created
 * for or assigned to that user go to the substitute of the absence, and the substitute is added
 * as candidate user where the absent user is one. Each substitution is recorded as a task comment
 * and in the local task variables {@value AbsenceService#VARIABLE_ABSENT_ASSIGNEE} and
 * {@value AbsenceService#VARIABLE_SUBSTITUTE}.
 *
 * <p>Absences are stored as user info of the identity service (table {@code ACT_ID_INFO}), which
 * stays in the engine database also with LDAP or single sign-on, so the database schema is that of
 * Camunda 7.24.</p>
 *
 * <p>Properties: {@code maxHops} (default 5), how many absent substitutes in a row are followed;
 * {@code substituteCandidates} (default {@code true}), whether substitutes become candidate users.</p>
 */
public class AbsenceSubstitutionPlugin implements ProcessEnginePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(AbsenceSubstitutionPlugin.class);

  protected int maxHops = AbsenceSettings.DEFAULT_MAX_HOPS;
  protected boolean substituteCandidates = true;
  protected AbsenceSettings settings;
  protected AbsenceCache.Factory cacheFactory;

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    settings = new AbsenceSettings(maxHops, substituteCandidates);
    if (configuration.getCustomPreBPMNParseListeners() == null) {
      configuration.setCustomPreBPMNParseListeners(new ArrayList<>());
    }
    configuration.getCustomPreBPMNParseListeners().add(new AbsenceBpmnParseListener(settings));
    if (configuration.getCustomPreCmmnTransformListeners() == null) {
      configuration.setCustomPreCmmnTransformListeners(new ArrayList<>());
    }
    configuration.getCustomPreCmmnTransformListeners().add(new AbsenceCmmnTransformListener(settings));
    if (configuration.getCustomSessionFactories() == null) {
      configuration.setCustomSessionFactories(new ArrayList<>());
    }
    cacheFactory = new AbsenceCache.Factory();
    configuration.getCustomSessionFactories().add(cacheFactory);
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl configuration) {
    // nothing to do
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    if (!configuration.isDbIdentityUsed()) {
      // the identity tables (with ACT_ID_INFO) are only certain to exist when the engine uses them
      boolean present = configuration.getCommandExecutorTxRequired()
          .execute(commandContext -> commandContext.getDbSqlSession().isIdentityTablePresent());
      if (!present) {
        settings.markUnavailable("The identity tables (ACT_ID_INFO) of the engine database are missing, "
            + "so absences cannot be stored.");
      }
    }
    if (settings.isAvailable()) {
      Boolean userIndex = userIndexPresent(configuration);
      cacheFactory.setUserIndexPresent(userIndex);
      LOG.info("Absence substitution is enabled on process engine '{}' ({})", processEngine.getName(),
          Boolean.FALSE.equals(userIndex)
              ? "ACT_ID_INFO has no index on USER_ID_: a command that looks up several users reads all absences at once"
              : Boolean.TRUE.equals(userIndex) ? "absences are read user by user through the index on ACT_ID_INFO (USER_ID_)"
                  : "absences are read user by user");
    } else {
      LOG.warn("Absence substitution is not available on process engine '{}': {}", processEngine.getName(),
          settings.getUnavailableReason());
    }
  }

  /**
   * Whether ACT_ID_INFO has an index that serves the lookup of one user (USER_ID_ leading, as the
   * optional FASTI_IDX_ID_INFO_USER); {@code null} when the catalog cannot tell. Read once at engine
   * start: an index created later is used by the lookups only after the next start of the node.
   */
  protected Boolean userIndexPresent(ProcessEngineConfigurationImpl configuration) {
    try {
      RecommendedIndex userIndex = new RecommendedIndex(IndexAdvisor.ID_INFO_USER, "ACT_ID_INFO", "USER_ID_");
      Map<String, List<ExistingIndex>> indexes = IndexAdvisor.readIndexes(configuration, Collections.singleton("ACT_ID_INFO"));
      return IndexAdvisor.isPresent(userIndex, indexes);
    } catch (RuntimeException | LinkageError e) {
      LOG.debug("Could not read the indexes of ACT_ID_INFO", e);
      return null;
    }
  }

  public int getMaxHops() {
    return maxHops;
  }

  public void setMaxHops(int maxHops) {
    if (maxHops < 1) {
      throw new IllegalArgumentException("maxHops must be at least 1");
    }
    this.maxHops = maxHops;
  }

  public boolean isSubstituteCandidates() {
    return substituteCandidates;
  }

  public void setSubstituteCandidates(boolean substituteCandidates) {
    this.substituteCandidates = substituteCandidates;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
