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

/** The result of verifying the audit trail: per chain, whether it is intact and where it first breaks. */
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
    protected final Break firstBreak;

    public Chain(String chainId, long records, long firstSequence, long lastSequence, String lastHash,
        long checkpoints, long checkpointsVerified, long purgedThrough, Break firstBreak) {
      this.chainId = chainId;
      this.records = records;
      this.firstSequence = firstSequence;
      this.lastSequence = lastSequence;
      this.lastHash = lastHash;
      this.checkpoints = checkpoints;
      this.checkpointsVerified = checkpointsVerified;
      this.purgedThrough = purgedThrough;
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

    /** Checkpoints whose HMAC was checked with the configured key. */
    public long getCheckpointsVerified() {
      return checkpointsVerified;
    }

    /** Position up to which records were purged (attested by a purge record); 0 if none. */
    public long getPurgedThrough() {
      return purgedThrough;
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
