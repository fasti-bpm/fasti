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

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The result of verifying the audit trail: per chain, whether it is intact and where it first breaks.
 *
 * <p>Two verdicts:</p>
 * <ul>
 * <li><b>intact</b>: nothing that can be checked with the configuration of the engine failed. The
 * hashes of the records are consistent from the first record to the head of the chain; with a
 * signing key, every checkpoint and every purge record that explains a removed start carries a
 * valid signature (or one that the configuration lists as retired), and no checkpoint is missing.</li>
 * <li><b>authenticated</b>: intact, a key is configured, and every checkpoint and the purge record
 * of a removed start were checked with a key the engine holds. Only this verdict rules out that
 * someone with write access to the database rewrote the chain or removed its start. It is
 * {@code false} without a key, and while signatures are accepted unchecked (retired key ids).</li>
 * </ul>
 * <p>A signature covers the records before its checkpoint. The records after the last checkpoint
 * of a chain ({@link Chain#getAuthenticatedThrough()}) are protected by the hash chain and the head
 * only, until the next checkpoint is written; a chain shorter than the checkpoint interval has no
 * checkpoint yet. Keep the reported last hashes outside the database to cover them.</p>
 */
public class AuditVerification {

  protected final Date verifiedAt;
  protected final List<Chain> chains;

  public AuditVerification(Date verifiedAt, List<Chain> chains) {
    this.verifiedAt = verifiedAt;
    this.chains = Collections.unmodifiableList(chains);
  }

  public Date getVerifiedAt() {
    return verifiedAt;
  }

  public List<Chain> getChains() {
    return chains;
  }

  /** Whether every chain verified. */
  public boolean isIntact() {
    for (Chain chain : chains) {
      if (!chain.isIntact()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether every chain verified and every signature it needs was checked with a key of the engine;
   * {@code false} without a signing key.
   */
  public boolean isAuthenticated() {
    for (Chain chain : chains) {
      if (!chain.isAuthenticated()) {
        return false;
      }
    }
    return !chains.isEmpty();
  }

  public long getRecords() {
    long records = 0;
    for (Chain chain : chains) {
      records += chain.getRecords();
    }
    return records;
  }

  /** The first break of the first broken chain, or {@code null}. */
  public Break getFirstBreak() {
    for (Chain chain : chains) {
      if (chain.getFirstBreak() != null) {
        return chain.getFirstBreak();
      }
    }
    return null;
  }

  /** The verification of one chain. */
  public static class Chain {
    protected final String chainId;
    protected final long records;
    protected final long firstSequence;
    protected final long lastSequence;
    protected final String lastHash;
    protected final long checkpoints;
    protected final long checkpointsVerified;
    protected final long purgedThrough;
    protected final boolean purgeAttested;
    protected final boolean authenticated;
    protected final long authenticatedThrough;
    protected final Break firstBreak;

    public Chain(String chainId, long records, long firstSequence, long lastSequence, String lastHash,
        long checkpoints, long checkpointsVerified, long purgedThrough, Break firstBreak) {
      this(chainId, records, firstSequence, lastSequence, lastHash, checkpoints, checkpointsVerified, purgedThrough, false,
          false, 0, firstBreak);
    }

    public Chain(String chainId, long records, long firstSequence, long lastSequence, String lastHash,
        long checkpoints, long checkpointsVerified, long purgedThrough, boolean purgeAttested, boolean authenticated,
        long authenticatedThrough, Break firstBreak) {
      this.chainId = chainId;
      this.records = records;
      this.firstSequence = firstSequence;
      this.lastSequence = lastSequence;
      this.lastHash = lastHash;
      this.checkpoints = checkpoints;
      this.checkpointsVerified = checkpointsVerified;
      this.purgedThrough = purgedThrough;
      this.purgeAttested = purgeAttested;
      this.authenticated = authenticated;
      this.authenticatedThrough = authenticatedThrough;
      this.firstBreak = firstBreak;
    }

    public String getChainId() {
      return chainId;
    }

    /** Records read. */
    public long getRecords() {
      return records;
    }

    public long getFirstSequence() {
      return firstSequence;
    }

    public long getLastSequence() {
      return lastSequence;
    }

    /** Hash of the last record; keep it outside the database to detect later truncation. */
    public String getLastHash() {
      return lastHash;
    }

    public long getCheckpoints() {
      return checkpoints;
    }

    /** Checkpoints whose HMAC was checked with the configured key or a retired key the engine holds. */
    public long getCheckpointsVerified() {
      return checkpointsVerified;
    }

    /** Position up to which records were purged (named by a purge record); 0 if none. */
    public long getPurgedThrough() {
      return purgedThrough;
    }

    /**
     * Whether the purge record that names the removed start carries a signature that was checked.
     * {@code false} without a key or for a record accepted unchecked: the chain may then have lost
     * its start to someone with write access to the database. Meaningless without a purge.
     */
    public boolean isPurgeAttested() {
      return purgeAttested;
    }

    /**
     * Whether the chain is intact and every signature it needs was checked with a key of the engine.
     * What the signatures cover ends at {@link #getAuthenticatedThrough()}.
     */
    public boolean isAuthenticated() {
      return authenticated;
    }

    /**
     * The position of the last record that a checked signature covers: the record before the last
     * checkpoint of an authenticated chain. 0 if the chain is not authenticated or has no checkpoint
     * yet. The records after it are protected by the hash chain and the head only.
     */
    public long getAuthenticatedThrough() {
      return authenticatedThrough;
    }

    public Break getFirstBreak() {
      return firstBreak;
    }

    public boolean isIntact() {
      return firstBreak == null;
    }
  }

  /** Where and why a chain does not verify. */
  public static class Break {

    /** The stored hash differs from the one computed over the record: the record was changed. */
    public static final String HASH_MISMATCH = "hash-mismatch";
    /** The record does not name the hash of the record before it: one was changed, removed or inserted. */
    public static final String PREVIOUS_HASH_MISMATCH = "previous-hash-mismatch";
    /** Positions are missing: records were removed. */
    public static final String SEQUENCE_GAP = "sequence-gap";
    /** The chain does not start at 1 and no purge record attests the removal. */
    public static final String MISSING_START = "missing-start";
    /** The chain head names another last record: records at the end were removed, or the head was changed. */
    public static final String HEAD_MISMATCH = "head-mismatch";
    /** Records exist for a chain without head. */
    public static final String HEAD_MISSING = "head-missing";
    /** A checkpoint's HMAC does not match: the chain before it was rewritten. */
    public static final String CHECKPOINT_MISMATCH = "checkpoint-mismatch";
    /** A key is configured and a checkpoint carries no signature. */
    public static final String CHECKPOINT_UNSIGNED = "checkpoint-unsigned";
    /** A key is configured and a checkpoint names a key that is neither the configured nor a retired one. */
    public static final String CHECKPOINT_UNKNOWN_KEY = "checkpoint-unknown-key";
    /** A key is configured and a checkpoint is not where the interval puts it: checkpoints were removed. */
    public static final String CHECKPOINT_MISSING = "checkpoint-missing";
    /** A key is configured, the chain does not start at 1 and the purge record that names the removal is not signed with it. */
    public static final String PURGE_UNATTESTED = "purge-unattested";

    protected final String chainId;
    protected final long sequence;
    protected final String recordId;
    protected final String reason;
    protected final String message;

    public Break(String chainId, long sequence, String recordId, String reason, String message) {
      this.chainId = chainId;
      this.sequence = sequence;
      this.recordId = recordId;
      this.reason = reason;
      this.message = message;
    }

    public String getChainId() {
      return chainId;
    }

    /** Position in the chain where the break was found. */
    public long getSequence() {
      return sequence;
    }

    /** The record at that position, or {@code null} if it is missing. */
    public String getRecordId() {
      return recordId;
    }

    /** One of the constants of this class. */
    public String getReason() {
      return reason;
    }

    public String getMessage() {
      return message;
    }
  }
}
