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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

import dev.fasti.engine.audit.impl.AuditDeploymentInterceptor;
import dev.fasti.engine.audit.impl.AuditHistoryEventHandler;
import dev.fasti.engine.audit.impl.AuditSession;
import dev.fasti.engine.audit.impl.AuditSettings;
import dev.fasti.engine.audit.impl.AuditStore;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.history.handler.CompositeHistoryEventHandler;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An immutable, tamper-evident audit trail. Next to the normal history, the history events the
 * engine produces (process instances, user tasks with assignments, variables, incidents,
 * decisions, the user operation log with identity and authorization changes) and every
 * deployment become records in the optional table {@code FASTI_AUDIT_EVENT}, written in the
 * transaction of what they record, with the authenticated user, the time of the engine clock,
 * the engine and the tenant.
 *
 * <p>Each record carries {@code SHA-256(hash of the previous record ‖ canonical record)}, so the
 * records of a chain can only be changed, removed or inserted by rewriting every record after
 * them; signed checkpoints (HMAC with a key that is never stored in the database) make even
 * that detectable. Each node writes to {@code chains} chains of its own, so nodes and threads
 * do not wait for one global lock. The application never updates or deletes records, except for
 * an explicit purge of old records, which leaves a purge record in the chain.</p>
 *
 * <p>The tables are created at startup when the engine may create its schema
 * ({@code databaseSchemaUpdate} true or create); the scripts are in
 * {@code dev/fasti/engine/audit/db/create}. The tables of Camunda 7.24 are not changed, so Camunda
 * 7.24 nodes on the same database ignore the audit trail.</p>
 */
public class AuditTrailPlugin implements ProcessEnginePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuditTrailPlugin.class);

  static final Set<String> CREATING_SCHEMA_UPDATES = Set.of(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE,
      ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_CREATE, ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP,
      ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_DROP_CREATE);

  protected String nodeId;
  protected int chains = AuditSettings.DEFAULT_CHAINS;
  protected String types = AuditSettings.DEFAULT_TYPES;
  protected String variableValues = AuditSettings.VALUES_HASH;
  protected String maskedVariables = AuditSettings.DEFAULT_MASKED;
  protected String hmacKey;
  protected String hmacKeyFile;
  protected int checkpointInterval = AuditSettings.DEFAULT_CHECKPOINT_INTERVAL;
  protected int retentionDays = AuditSettings.DEFAULT_RETENTION_DAYS;

  protected AuditSettings settings;

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    settings = new AuditSettings(nodeId, chains, types, variableValues, maskedVariables, resolveKey(), checkpointInterval,
        retentionDays);
    if (configuration.getCustomSessionFactories() == null) {
      configuration.setCustomSessionFactories(new ArrayList<>());
    }
    configuration.getCustomSessionFactories().add(new AuditSession.Factory(settings));
    if (configuration.getCustomPostCommandInterceptorsTxRequired() == null) {
      configuration.setCustomPostCommandInterceptorsTxRequired(new ArrayList<>());
    }
    configuration.getCustomPostCommandInterceptorsTxRequired().add(new AuditDeploymentInterceptor(settings));
    if (configuration.getCustomPostCommandInterceptorsTxRequiresNew() == null) {
      configuration.setCustomPostCommandInterceptorsTxRequiresNew(new ArrayList<>());
    }
    configuration.getCustomPostCommandInterceptorsTxRequiresNew().add(new AuditDeploymentInterceptor(settings));
  }

  /** The HMAC key from {@code hmacKey} or the file {@code hmacKeyFile}, or {@code null}. */
  protected byte[] resolveKey() {
    if (hmacKey != null && !hmacKey.isBlank()) {
      return hmacKey.trim().getBytes(StandardCharsets.UTF_8);
    }
    if (hmacKeyFile != null && !hmacKeyFile.isBlank()) {
      try {
        String key = Files.readString(Path.of(hmacKeyFile.trim()), StandardCharsets.UTF_8).trim();
        if (key.isEmpty()) {
          throw new ProcessEngineException("The HMAC key file of the audit trail is empty: " + hmacKeyFile);
        }
        return key.getBytes(StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new ProcessEngineException("The HMAC key file of the audit trail cannot be read: " + hmacKeyFile, e);
      }
    }
    return null;
  }

  @Override
  public void postInit(ProcessEngineConfigurationImpl configuration) {
    settings.setEngineName(configuration.getProcessEngineName());
    settings.setTablePrefix(configuration.getDatabaseTablePrefix());
    if (settings.getNodeId() == null) {
      String hostname = configuration.getHostname();
      settings.setNodeId(configuration.getProcessEngineName() + "@" + (hostname == null ? "localhost" : hostname));
    }
    // keep the handler of the history (and any other) and record next to it
    configuration.setHistoryEventHandler(new CompositeHistoryEventHandler(configuration.getHistoryEventHandler(),
        new AuditHistoryEventHandler(settings)));
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    try {
      prepareTables(configuration);
    } catch (RuntimeException e) {
      processEngine.close();
      throw e;
    }
    HistoryLevel historyLevel = configuration.getHistoryLevel();
    settings.setHistoryLevel(historyLevel == null ? null : historyLevel.getName());
    LOG.info("Audit trail is enabled on process engine '{}': node {} with {} chains, recording {}, variable values as {}, "
        + "checkpoints {}", processEngine.getName(), settings.getNodeId(), settings.getChains(), settings.getTypes(),
        settings.getVariableValues(), settings.isSigned() ? "signed (key " + settings.getKeyId() + ")" : "not signed");
    if (historyLevel == null || historyLevel.getId() < HistoryLevel.HISTORY_LEVEL_FULL.getId()) {
      LOG.warn("The history level of process engine '{}' is '{}': the engine produces fewer events, and user operations "
          + "(who changed what, identities, authorizations) are only recorded at history level 'full'.",
          processEngine.getName(), historyLevel == null ? null : historyLevel.getName());
    }
  }

  protected void prepareTables(ProcessEngineConfigurationImpl configuration) {
    CommandExecutor executor = configuration.getCommandExecutorTxRequired();
    boolean present = executor.execute(AuditStore::isTablePresent);
    if (!present) {
      String schemaUpdate = configuration.getDatabaseSchemaUpdate();
      if (!CREATING_SCHEMA_UPDATES.contains(schemaUpdate)) {
        throw new ProcessEngineException("The tables of the audit trail (" + AuditStore.EVENT_TABLE + ", "
            + AuditStore.HEAD_TABLE + ") are missing and the engine may not create its schema (databaseSchemaUpdate="
            + schemaUpdate + "). Run " + AuditStore.createResource(configuration.getDatabaseType())
            + " (sql/create/" + configuration.getDatabaseType() + "_fasti_audit.sql in the distribution) or turn the audit trail off.");
      }
      try {
        executor.execute(commandContext -> {
          AuditStore.createTables(commandContext);
          return null;
        });
      } catch (RuntimeException e) {
        // another node may have created them at the same time
        if (!executor.execute(AuditStore::isTablePresent)) {
          throw e;
        }
      }
    }
    for (int slot = 0; slot < settings.getChains(); slot++) {
      String chainId = settings.chainId(slot);
      try {
        executor.execute(commandContext -> AuditStore.ensureHead(commandContext, settings, chainId));
      } catch (RuntimeException e) {
        // another engine with the same node id created it at the same time
        if (executor.execute(commandContext -> AuditStore.head(commandContext, settings, chainId)) == null) {
          throw e;
        }
      }
    }
  }

  public String getNodeId() {
    return nodeId;
  }

  /** Identifies this node in the chain ids; default {@code <engine name>@<host name>}. Keep it stable and unique per node. */
  public void setNodeId(String nodeId) {
    this.nodeId = nodeId == null || nodeId.isBlank() ? null : nodeId.trim();
  }

  public int getChains() {
    return chains;
  }

  /** Chains per node, written in parallel (default 4). */
  public void setChains(int chains) {
    this.chains = chains;
  }

  public String getTypes() {
    return types;
  }

  /** Categories to record, comma separated, or {@code all}. */
  public void setTypes(String types) {
    this.types = types;
  }

  public String getVariableValues() {
    return variableValues;
  }

  /** {@code hash} (default), {@code value} or {@code none}. */
  public void setVariableValues(String variableValues) {
    this.variableValues = variableValues;
  }

  public String getMaskedVariables() {
    return maskedVariables;
  }

  /** Name patterns ({@code *}, {@code ?}, case-insensitive, comma separated) of variables whose values are never recorded. */
  public void setMaskedVariables(String maskedVariables) {
    this.maskedVariables = maskedVariables;
  }

  /** The HMAC key for checkpoints, at least 32 bytes. Prefer {@link #setHmacKeyFile(String)} or an environment variable. */
  public void setHmacKey(String hmacKey) {
    this.hmacKey = hmacKey;
  }

  public String getHmacKeyFile() {
    return hmacKeyFile;
  }

  /** A file that holds the HMAC key for checkpoints. */
  public void setHmacKeyFile(String hmacKeyFile) {
    this.hmacKeyFile = hmacKeyFile;
  }

  public int getCheckpointInterval() {
    return checkpointInterval;
  }

  /** Records between two checkpoints of a chain (default 1000, 0 for none). */
  public void setCheckpointInterval(int checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  /** Records younger than this many days cannot be purged (default 365). */
  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
