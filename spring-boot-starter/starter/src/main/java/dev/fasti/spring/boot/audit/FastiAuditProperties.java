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
package dev.fasti.spring.boot.audit;

import dev.fasti.engine.audit.impl.AuditSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The immutable audit trail ({@code fasti.audit.*}).
 */
@ConfigurationProperties(FastiAuditProperties.PREFIX)
public class FastiAuditProperties {

  public static final String PREFIX = "fasti.audit";

  /**
   * Whether the audit trail records what happens in the engine, in the tables FASTI_AUDIT_EVENT and
   * FASTI_AUDIT_HEAD (created on startup when the engine may create its schema).
   */
  private boolean enabled = false;

  /**
   * Identifies this node in the chain ids; default {@code <engine name>@<host name>}. Keep it stable
   * and unique per node of a cluster.
   */
  private String nodeId;

  /**
   * Chains this node writes to in parallel.
   */
  private int chains = AuditSettings.DEFAULT_CHAINS;

  /**
   * Categories to record, comma separated (process, task, variable, deployment, operation, incident,
   * decision, activity), or "all".
   */
  private String types = AuditSettings.DEFAULT_TYPES;

  /**
   * How variable values are recorded: "hash" (SHA-256, or HMAC with the key), "value" (in clear) or
   * "none".
   */
  private String variableValues = AuditSettings.VALUES_HASH;

  /**
   * Name patterns (*, ?, case-insensitive, comma separated) of variables whose values are never
   * recorded, not even as a hash.
   */
  private String maskedVariables = AuditSettings.DEFAULT_MASKED;

  /**
   * Key (at least 32 bytes) that signs checkpoints; best given as the environment variable
   * FASTI_AUDIT_HMAC_KEY. It is never written to the database.
   */
  private String hmacKey;

  /**
   * A file that holds the key that signs checkpoints, instead of hmac-key.
   */
  private String hmacKeyFile;

  /**
   * Records between two checkpoints of a chain; 0 for none.
   */
  private int checkpointInterval = AuditSettings.DEFAULT_CHECKPOINT_INTERVAL;

  /**
   * Records younger than this many days cannot be purged.
   */
  private int retentionDays = AuditSettings.DEFAULT_RETENTION_DAYS;

  /**
   * Earlier keys, comma separated: what they signed is still checked after hmac-key was replaced.
   * Best given as the environment variable FASTI_AUDIT_RETIRED_HMAC_KEYS.
   */
  private String retiredHmacKeys;

  /**
   * Ids (12 hex characters, as the settings and checkpoints show them) of earlier keys that are no
   * longer at hand, comma separated, and "unsigned" for checkpoints and purge records written before
   * a key was set. With a key configured they are accepted without being checked, before the first
   * checkpoint of the current key; chains that hold them verify as intact, but not as authenticated.
   */
  private String retiredKeyIds;

  /**
   * Seconds a command waits for the head of its chain before it fails; 0 for the limit of the database.
   */
  private int lockTimeoutSeconds = AuditSettings.DEFAULT_LOCK_TIMEOUT_SECONDS;

  /**
   * Seconds a query or export of the audit trail may run; 0 for no limit.
   */
  private int queryTimeoutSeconds = AuditSettings.DEFAULT_QUERY_TIMEOUT_SECONDS;

  /**
   * Records a purge removes from a chain in one transaction: the head of the chain is held for one
   * batch at a time, and each batch leaves a purge record.
   */
  private int purgeBatchSize = AuditSettings.DEFAULT_PURGE_BATCH_SIZE;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public int getChains() {
    return chains;
  }

  public void setChains(int chains) {
    this.chains = chains;
  }

  public String getTypes() {
    return types;
  }

  public void setTypes(String types) {
    this.types = types;
  }

  public String getVariableValues() {
    return variableValues;
  }

  public void setVariableValues(String variableValues) {
    this.variableValues = variableValues;
  }

  public String getMaskedVariables() {
    return maskedVariables;
  }

  public void setMaskedVariables(String maskedVariables) {
    this.maskedVariables = maskedVariables;
  }

  public String getHmacKey() {
    return hmacKey;
  }

  public void setHmacKey(String hmacKey) {
    this.hmacKey = hmacKey;
  }

  public String getHmacKeyFile() {
    return hmacKeyFile;
  }

  public void setHmacKeyFile(String hmacKeyFile) {
    this.hmacKeyFile = hmacKeyFile;
  }

  public int getCheckpointInterval() {
    return checkpointInterval;
  }

  public void setCheckpointInterval(int checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  public String getRetiredHmacKeys() {
    return retiredHmacKeys;
  }

  public void setRetiredHmacKeys(String retiredHmacKeys) {
    this.retiredHmacKeys = retiredHmacKeys;
  }

  public String getRetiredKeyIds() {
    return retiredKeyIds;
  }

  public void setRetiredKeyIds(String retiredKeyIds) {
    this.retiredKeyIds = retiredKeyIds;
  }

  public int getLockTimeoutSeconds() {
    return lockTimeoutSeconds;
  }

  public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
    this.lockTimeoutSeconds = lockTimeoutSeconds;
  }

  public int getQueryTimeoutSeconds() {
    return queryTimeoutSeconds;
  }

  public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
    this.queryTimeoutSeconds = queryTimeoutSeconds;
  }

  public int getPurgeBatchSize() {
    return purgeBatchSize;
  }

  public void setPurgeBatchSize(int purgeBatchSize) {
    this.purgeBatchSize = purgeBatchSize;
  }

  @Override
  public String toString() {
    // never the key
    return "FastiAuditProperties[enabled=" + enabled + ", nodeId=" + nodeId + ", chains=" + chains + ", types=" + types + "]";
  }
}
