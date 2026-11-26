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
package dev.fasti.engine.audit.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditStatus;

/**
 * Settings of the audit trail of one engine, fixed when the engine is built. The HMAC key lives
 * only here, in memory: it is never written to the database or shown.
 */
public class AuditSettings implements AuditStatus {

  public static final String VALUES_HASH = "hash";
  public static final String VALUES_VALUE = "value";
  public static final String VALUES_NONE = "none";

  public static final Set<String> ALL_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
      AuditService.TYPE_PROCESS, AuditService.TYPE_TASK, AuditService.TYPE_VARIABLE, AuditService.TYPE_DEPLOYMENT,
      AuditService.TYPE_OPERATION, AuditService.TYPE_INCIDENT, AuditService.TYPE_DECISION, AuditService.TYPE_ACTIVITY)));

  /** Everything but the start and end of every activity, which is mostly noise for an audit. */
  public static final String DEFAULT_TYPES = "process,task,variable,deployment,operation,incident,decision";
  public static final String DEFAULT_MASKED = "*password*,*secret*,*token*,*credential*,*apikey*,*api_key*,*privatekey*,*private_key*";
  public static final int DEFAULT_CHAINS = 4;
  public static final int DEFAULT_CHECKPOINT_INTERVAL = 1000;
  /** Seconds after which the end of a chain written since its last checkpoint is signed (with a key). */
  public static final int DEFAULT_CHECKPOINT_INTERVAL_SECONDS = 300;
  public static final int DEFAULT_RETENTION_DAYS = 365;
  /** HMAC keys shorter than this are refused. */
  public static final int MIN_KEY_BYTES = 32;
  /** Seconds an append waits for the head of its chain before it fails. */
  public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 60;
  /** Seconds a query of the audit trail may run. */
  public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 120;
  /**
   * In the list of retired key ids: accepts checkpoints and purge records without a signature,
   * written before a key was configured.
   */
  public static final String UNSIGNED_KEY_ID = "unsigned";
  /** Before the slot in the ids of the chains of transactions that write their records last. */
  public static final String END_CHAIN_PREFIX = "end-";
  /** Records a purge removes from a chain in one transaction. */
  public static final int DEFAULT_PURGE_BATCH_SIZE = 10_000;

  protected volatile String nodeId;
  protected final int chains;
  protected final Set<String> types;
  protected final String variableValues;
  protected final List<String> maskedVariables;
  protected final List<Pattern> maskPatterns;
  protected final byte[] hmacKey;
  protected final String keyId;
  protected final int checkpointInterval;
  protected final int retentionDays;
  protected volatile String engineName;
  protected volatile String historyLevel;
  protected volatile String tablePrefix = "";
  protected volatile boolean nationalCharacterColumns;
  protected volatile String databaseType;
  /** Retired keys this engine still holds, by key id: their signatures are checked. */
  protected volatile Map<String, byte[]> retiredKeys = Collections.emptyMap();
  /** Ids of retired keys this engine does not hold: their signatures are accepted unchecked. */
  protected volatile Set<String> retiredKeyIds = Collections.emptySet();
  protected volatile int lockTimeoutSeconds = DEFAULT_LOCK_TIMEOUT_SECONDS;
  protected volatile int queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;
  protected volatile boolean separateChains;
  protected volatile int purgeBatchSize = DEFAULT_PURGE_BATCH_SIZE;
  protected volatile int checkpointIntervalSeconds = DEFAULT_CHECKPOINT_INTERVAL_SECONDS;
  protected volatile Consumer<Runnable> transactionCompletion;
  /** Set while a verification of this engine runs: one at a time. */
  protected final AtomicBoolean verifying = new AtomicBoolean();

  public AuditSettings(String nodeId, int chains, String types, String variableValues, String maskedVariables,
      byte[] hmacKey, int checkpointInterval, int retentionDays) {
    this.nodeId = nodeId;
    if (chains < 1 || chains > 64) {
      throw new IllegalArgumentException("chains must be between 1 and 64");
    }
    this.chains = chains;
    this.types = parseTypes(types);
    String values = variableValues == null ? VALUES_HASH : variableValues.trim().toLowerCase(Locale.ROOT);
    if (!values.equals(VALUES_HASH) && !values.equals(VALUES_VALUE) && !values.equals(VALUES_NONE)) {
      throw new IllegalArgumentException("variableValues must be hash, value or none, not '" + variableValues + "'");
    }
    this.variableValues = values;
    this.maskedVariables = split(maskedVariables);
    List<Pattern> patterns = new ArrayList<>();
    for (String mask : this.maskedVariables) {
      patterns.add(glob(mask));
    }
    this.maskPatterns = patterns;
    if (hmacKey != null && hmacKey.length < MIN_KEY_BYTES) {
      throw new IllegalArgumentException("The HMAC key of the audit trail must have at least " + MIN_KEY_BYTES + " bytes");
    }
    this.hmacKey = hmacKey;
    this.keyId = hmacKey == null ? null : keyId(hmacKey);
    if (checkpointInterval < 0) {
      throw new IllegalArgumentException("checkpointInterval must not be negative");
    }
    this.checkpointInterval = checkpointInterval;
    if (retentionDays < 0) {
      throw new IllegalArgumentException("retentionDays must not be negative");
    }
    this.retentionDays = retentionDays;
  }

  /** The id of a key: the first characters of its SHA-256, which tell keys apart without revealing them. */
  public static String keyId(byte[] key) {
    return AuditChain.sha256Hex(key).substring(0, 12);
  }

  static Set<String> parseTypes(String value) {
    Set<String> result = new LinkedHashSet<>();
    for (String type : split(value == null ? DEFAULT_TYPES : value)) {
      String normalized = type.toLowerCase(Locale.ROOT);
      if (normalized.equals("all")) {
        result.addAll(ALL_TYPES);
      } else if (ALL_TYPES.contains(normalized)) {
        result.add(normalized);
      } else {
        throw new IllegalArgumentException("Unknown audit event type '" + type + "'; known: " + ALL_TYPES);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  static List<String> split(String value) {
    List<String> result = new ArrayList<>();
    if (value != null) {
      for (String part : value.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          result.add(trimmed);
        }
      }
    }
    return Collections.unmodifiableList(result);
  }

  /** A case-insensitive pattern for a name with {@code *} (any characters) and {@code ?} (one character). */
  static Pattern glob(String mask) {
    StringBuilder regex = new StringBuilder();
    for (char c : mask.toCharArray()) {
      if (c == '*') {
        regex.append(".*");
      } else if (c == '?') {
        regex.append('.');
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  public boolean isRecorded(String type) {
    return types.contains(type);
  }

  public boolean isMasked(String variableName) {
    if (variableName == null) {
      return false;
    }
    for (Pattern pattern : maskPatterns) {
      if (pattern.matcher(variableName).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * The chain of a slot of this node for transactions that write their records before the
   * statements of the engine, as every transaction of 1.0 and 1.1 did (see {@link AuditSession}).
   */
  public String chainId(int slot) {
    return nodeId + "/" + slot;
  }

  /**
   * The chain of a slot of this node for transactions that end with their command and write their
   * records after the statements of the engine. Fasti 1.0 and 1.1 never write to these chains, so
   * that a node of 1.1 with the same node id never holds one of them (see {@link AuditSession}).
   */
  public String endChainId(int slot) {
    return nodeId + "/" + END_CHAIN_PREFIX + slot;
  }

  /**
   * Whether transactions that write their records after the statements of the engine use chains
   * of their own ({@link #endChainId(int)}): when the engine can tell where its transactions end.
   */
  public boolean isSeparateChains() {
    return separateChains;
  }

  public void setSeparateChains(boolean separateChains) {
    this.separateChains = separateChains;
  }

  public byte[] getHmacKey() {
    return hmacKey;
  }

  @Override
  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public int getChains() {
    return chains;
  }

  @Override
  public Set<String> getTypes() {
    return types;
  }

  @Override
  public String getVariableValues() {
    return variableValues;
  }

  @Override
  public List<String> getMaskedVariables() {
    return maskedVariables;
  }

  @Override
  public boolean isSigned() {
    return hmacKey != null;
  }

  @Override
  public String getKeyId() {
    return keyId;
  }

  @Override
  public int getCheckpointInterval() {
    return checkpointInterval;
  }

  @Override
  public int getRetentionDays() {
    return retentionDays;
  }

  @Override
  public String getHistoryLevel() {
    return historyLevel;
  }

  public void setHistoryLevel(String historyLevel) {
    this.historyLevel = historyLevel;
  }

  /** The key with the id, the current one or a retired one this engine holds; {@code null} if it holds none. */
  public byte[] keyOf(String id) {
    if (id == null || hmacKey == null) {
      return null;
    }
    return id.equals(keyId) ? hmacKey : retiredKeys.get(id);
  }

  /** Whether signatures of the key id are accepted without being checked ({@code null}: no signature). */
  public boolean isRetiredKeyId(String id) {
    return retiredKeyIds.contains(id == null ? UNSIGNED_KEY_ID : id);
  }

  /** Whether some signatures are accepted without being checked (ids of retired keys are listed). */
  public boolean acceptsUncheckedSignatures() {
    return !retiredKeyIds.isEmpty();
  }

  @Override
  public List<String> getRetiredKeyIds() {
    List<String> ids = new ArrayList<>(retiredKeys.keySet());
    for (String id : retiredKeyIds) {
      if (!ids.contains(id)) {
        ids.add(id);
      }
    }
    return Collections.unmodifiableList(ids);
  }

  /** Retired keys (each at least {@value #MIN_KEY_BYTES} bytes): what they signed is still checked. */
  public void setRetiredKeys(List<byte[]> keys) {
    Map<String, byte[]> byId = new LinkedHashMap<>();
    if (keys != null) {
      for (byte[] key : keys) {
        if (key.length < MIN_KEY_BYTES) {
          throw new IllegalArgumentException("A retired HMAC key of the audit trail must have at least " + MIN_KEY_BYTES + " bytes");
        }
        byId.put(keyId(key), key);
      }
    }
    this.retiredKeys = Collections.unmodifiableMap(byId);
  }

  /** Ids of retired keys that are no longer at hand, comma separated; {@value #UNSIGNED_KEY_ID} for no signature. */
  public void setRetiredKeyIds(String ids) {
    Set<String> result = new LinkedHashSet<>();
    for (String id : split(ids)) {
      String normalized = id.toLowerCase(Locale.ROOT);
      if (!normalized.equals(UNSIGNED_KEY_ID) && !normalized.matches("[0-9a-f]{12}")) {
        throw new IllegalArgumentException("A retired key id of the audit trail is 12 hex characters (as verification "
            + "and the settings show it) or '" + UNSIGNED_KEY_ID + "', not '" + id + "'");
      }
      result.add(normalized);
    }
    this.retiredKeyIds = Collections.unmodifiableSet(result);
  }

  public int getLockTimeoutSeconds() {
    return lockTimeoutSeconds;
  }

  /** Seconds an append waits for the head of its chain; 0 for as long as the database allows. */
  public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
    if (lockTimeoutSeconds < 0) {
      throw new IllegalArgumentException("lockTimeoutSeconds must not be negative");
    }
    this.lockTimeoutSeconds = lockTimeoutSeconds;
  }

  public int getQueryTimeoutSeconds() {
    return queryTimeoutSeconds;
  }

  /** Seconds a query of the audit trail may run; 0 for no limit. */
  public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
    if (queryTimeoutSeconds < 0) {
      throw new IllegalArgumentException("queryTimeoutSeconds must not be negative");
    }
    this.queryTimeoutSeconds = queryTimeoutSeconds;
  }

  public int getPurgeBatchSize() {
    return purgeBatchSize;
  }

  /**
   * Records a purge removes from a chain in one transaction (at least 1): it holds the head of the
   * chain for one batch at a time and leaves a purge record per batch.
   */
  public void setPurgeBatchSize(int purgeBatchSize) {
    if (purgeBatchSize < 1) {
      throw new IllegalArgumentException("purgeBatchSize must be at least 1");
    }
    this.purgeBatchSize = purgeBatchSize;
  }

  public int getCheckpointIntervalSeconds() {
    return checkpointIntervalSeconds;
  }

  /**
   * Seconds after which the end of each chain of this node that has records after its last
   * checkpoint gets a checkpoint, with a key; 0 to sign the ends only when the engine closes.
   */
  public void setCheckpointIntervalSeconds(int checkpointIntervalSeconds) {
    if (checkpointIntervalSeconds < 0) {
      throw new IllegalArgumentException("checkpointIntervalSeconds must not be negative");
    }
    this.checkpointIntervalSeconds = checkpointIntervalSeconds;
  }

  public Consumer<Runnable> getTransactionCompletion() {
    return transactionCompletion;
  }

  /**
   * Registers a callback that runs when the transaction of the calling thread ends, whatever its
   * outcome; {@code null} when the transaction listeners of the engine are enough.
   */
  public void setTransactionCompletion(Consumer<Runnable> transactionCompletion) {
    this.transactionCompletion = transactionCompletion;
  }

  public String getDatabaseType() {
    return databaseType;
  }

  public void setDatabaseType(String databaseType) {
    this.databaseType = databaseType;
  }

  /** Marks a verification as running; {@code false} if one runs already. */
  public boolean startVerifying() {
    return verifying.compareAndSet(false, true);
  }

  public void endVerifying() {
    verifying.set(false);
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  public String getTablePrefix() {
    return tablePrefix;
  }

  /** Whether text columns hold national characters (NVARCHAR2 and NCLOB on Oracle). */
  public boolean isNationalCharacterColumns() {
    return nationalCharacterColumns;
  }

  public void setNationalCharacterColumns(boolean nationalCharacterColumns) {
    this.nationalCharacterColumns = nationalCharacterColumns;
  }

  public void setTablePrefix(String tablePrefix) {
    this.tablePrefix = tablePrefix == null ? "" : tablePrefix;
  }
}
