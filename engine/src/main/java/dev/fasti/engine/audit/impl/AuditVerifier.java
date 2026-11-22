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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditVerification;
import dev.fasti.engine.audit.AuditVerification.Break;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;

/**
 * Recomputes the chains. Each chain is read in pages, each page in its own read-only command, so
 * that a long chain does not hold one long transaction. Records appended while the verification
 * runs are followed up to the head, which is read after the records.
 *
 * <p>Without a key, a chain is intact when its hashes are consistent from its first record to its
 * head; a start that a purge record names as removed is accepted, but reported as not attested,
 * because anyone who can write to the database can write such a record.</p>
 *
 * <p>With a key, what the key signs must be there and must match. A checkpoint signs the hash of
 * the record before it, and through the hashes every record before that, so a change of a record
 * needs every later checkpoint changed or removed. Therefore every checkpoint must carry a valid
 * signature of a key this engine holds (the current one or a retired one); checkpoints must
 * follow each other, from the first record on, at the distance of the interval they were written
 * with, and no more records than that may follow the last one, so that removed checkpoints are
 * missed even after the engine has written new ones over a rewritten chain; and a removed start
 * needs a purge record signed with such a key. A signature that is missing or of an unknown key
 * is a break, unless its key id is listed as retired ({@code unsigned} for none); what is accepted
 * that way is not checked, so the chain is intact but not authenticated, and it is accepted only
 * before the first checkpoint of the current key.</p>
 *
 * <p>Not covered by a signature: the records after the last checkpoint of a chain
 * ({@link AuditVerification.Chain#getAuthenticatedThrough()}), which only the hash chain and the
 * head protect until the next checkpoint.</p>
 */
public class AuditVerifier {

  static final int PAGE = 1000;
  /**
   * Readings of one chain at most: a chain is read again when a purge removed records while it was
   * read, and a purge commits a batch at a time at the start of the chain, which the reading may
   * meet several times. Each new reading needs a purge that went on since the one before.
   */
  static final int MAX_READINGS = 10_000;

  /** Records read at a time. */
  protected int pageSize = PAGE;

  protected final CommandExecutor commandExecutor;
  protected final AuditSettings settings;

  public AuditVerifier(CommandExecutor commandExecutor, AuditSettings settings) {
    this.commandExecutor = commandExecutor;
    this.settings = settings;
  }

  public AuditVerification.Chain verifyChain(String chainId) {
    AuditVerification.Chain result = null;
    for (int reading = 1; result == null; reading++) {
      result = verifyChain(chainId, reading < MAX_READINGS);
    }
    return result;
  }

  /**
   * One reading of the chain; {@code null} if it is to be read again (with {@code mayReadAgain}):
   * it was empty when its reading began, or a purge removed records while it was read.
   */
  protected AuditVerification.Chain verifyChain(String chainId, boolean mayReadAgain) {
    long records = 0;
    long checkpoints = 0;
    long checkpointsVerified = 0;
    long purgedThrough = 0;
    boolean purgeAttested = false;
    // with a key: the last checkpoint (or the position before the first record), its interval, and
    // whether a missing checkpoint after it is a break
    long anchor = 0;
    long anchorInterval = 0;
    long authenticatedThrough = 0;
    boolean spacing = settings.isSigned() && !settings.acceptsUncheckedSignatures();
    boolean currentKeySeen = false;
    // no checkpoint where one is due, in a chain where that is tolerated: not authenticated
    boolean gap = false;
    long firstSequence = 0;
    long expected = 0;
    String previous = null;
    long last = 0;
    String lastHash = null;
    Break found = null;

    List<AuditRecord> page = read(chainId, 0);
    if (!page.isEmpty()) {
      AuditRecord first = page.get(0);
      firstSequence = first.getSequence();
      if (first.getSequence() == 1) {
        previous = AuditChain.GENESIS;
      } else {
        Purge purge = purgeBefore(chainId, first);
        if (purge == Purge.SIGNED || purge == Purge.UNATTESTED) {
          purgedThrough = first.getSequence() - 1;
          purgeAttested = purge == Purge.SIGNED;
          previous = first.getPreviousHash();
        } else if (purge == Purge.REJECTED) {
          found = new Break(chainId, 1, null, Break.PURGE_UNATTESTED, "The chain starts at record " + first.getSequence()
              + " and the purge record that names the records before it carries no valid signature of a key of this "
              + "engine: records at the start were removed, or the purge was signed with a key that is not configured.");
        } else {
          found = new Break(chainId, 1, null, Break.MISSING_START, "The chain starts at record " + first.getSequence()
              + " and no purge record names the records before it: records at the start were removed.");
        }
      }
      anchor = first.getSequence() - 1;
      expected = first.getSequence();
    }

    while (found == null) {
      for (AuditRecord record : page) {
        if (record.getSequence() != expected) {
          found = new Break(chainId, expected, null, Break.SEQUENCE_GAP, "Record " + expected
              + " is missing (the next one is " + record.getSequence() + "): records were removed.");
          break;
        }
        if (!AuditChain.same(previous, record.getPreviousHash())) {
          found = new Break(chainId, record.getSequence(), record.getId(), Break.PREVIOUS_HASH_MISMATCH, "Record "
              + record.getSequence() + " does not name the hash of record " + (record.getSequence() - 1)
              + ": one of the two was changed, or a record was removed or inserted between them.");
          break;
        }
        if (!AuditChain.same(AuditChain.hash(record), record.getHash())) {
          found = new Break(chainId, record.getSequence(), record.getId(), Break.HASH_MISMATCH, "Record "
              + record.getSequence() + " was changed after it was written: its hash does not match its content.");
          break;
        }
        if (AuditService.TYPE_AUDIT.equals(record.getType()) && AuditService.ACTION_CHECKPOINT.equals(record.getAction())) {
          checkpoints++;
          String hmac = text(record.getDetails(), "hmac");
          String keyId = text(record.getDetails(), "keyId");
          Long interval = number(record.getDetails(), "interval");
          byte[] key = settings.keyOf(keyId);
          if (key != null) {
            // of a key this engine holds: the signature must match
            String expectedHmac = AuditChain.hmacHex(key, AuditChain.checkpointText(chainId, record.getSequence() - 1,
                record.getPreviousHash(), interval == null ? 0 : interval));
            if (hmac == null || !AuditChain.same(expectedHmac, hmac)) {
              found = new Break(chainId, record.getSequence(), record.getId(), Break.CHECKPOINT_MISMATCH, "The signature of "
                  + "checkpoint " + record.getSequence() + " does not match: the chain before it was rewritten.");
              break;
            }
            // written an interval after the checkpoint before it (or the start of the chain); if the
            // interval was changed in between, up to both intervals after it
            long allowed = interval == null || interval <= 0 ? Long.MAX_VALUE
                : anchorInterval == 0 || anchorInterval == interval ? interval + 1 : anchorInterval + interval;
            boolean late = record.getSequence() - anchor > allowed;
            gap |= late;
            if (spacing && late) {
              found = new Break(chainId, anchor + allowed, null, Break.CHECKPOINT_MISSING, "No checkpoint between record "
                  + anchor + " and checkpoint " + record.getSequence() + ", which was written with one every " + interval
                  + " records: checkpoints were removed.");
              break;
            }
            checkpointsVerified++;
            spacing = true;
            currentKeySeen |= settings.getKeyId().equals(keyId);
            anchorInterval = interval == null ? 0 : interval;
            authenticatedThrough = record.getSequence() - 1;
          } else if (settings.isSigned()) {
            // no signature, or of a key this engine does not hold: only if listed as retired, and
            // not after a checkpoint of the current key (keys follow each other)
            if (!settings.isRetiredKeyId(keyId) || currentKeySeen) {
              found = keyId == null
                  ? new Break(chainId, record.getSequence(), record.getId(), Break.CHECKPOINT_UNSIGNED, "Checkpoint "
                      + record.getSequence() + " carries no signature, although a key is configured: its signature was "
                      + "removed, or it was written before the key was set (see fasti.audit.retired-key-ids).")
                  : new Break(chainId, record.getSequence(), record.getId(), Break.CHECKPOINT_UNKNOWN_KEY, "Checkpoint "
                      + record.getSequence() + " names key " + keyId + ", which is neither the configured key nor a "
                      + "retired one: its signature was replaced, or the key was changed (see fasti.audit.retired-key-ids).");
              break;
            }
          }
          anchor = record.getSequence();
          if (key == null) {
            anchorInterval = 0;
          }
        }
        records++;
        previous = record.getHash();
        last = record.getSequence();
        lastHash = record.getHash();
        expected++;
      }
      if (found != null) {
        break;
      }
      if (page.size() == pageSize) {
        page = read(chainId, last);
        continue;
      }
      // the end of what was committed when the page was read: compare with the head, read after it
      AuditStore.Head head = commandExecutor.execute(commandContext -> AuditStore.head(commandContext, settings, chainId));
      if (head == null) {
        if (records > 0) {
          found = new Break(chainId, last, null, Break.HEAD_MISSING, "The chain has records but no head: the head "
              + "row was removed, or the records were inserted from outside.");
        }
        break;
      }
      if (head.sequence > last) {
        List<AuditRecord> more = read(chainId, last);
        if (!more.isEmpty()) {
          if (records == 0 && firstSequence == 0 && mayReadAgain) {
            // the chain was empty when it was first read
            return null;
          }
          page = more;
          continue;
        }
      }
      if (head.sequence != last || (records > 0 && !AuditChain.same(head.hash, lastHash))) {
        found = new Break(chainId, Math.min(head.sequence, last) + 1, null, Break.HEAD_MISMATCH, "The head of the chain "
            + "names record " + head.sequence + " as the last one, but the last record is " + last
            + ": records at the end were removed, or the head was changed.");
      }
      break;
    }
    long due = Math.max(anchorInterval, settings.getCheckpointInterval());
    // a record at a multiple of the interval is written together with its checkpoint, so fewer than
    // an interval of records can follow the last checkpoint (or the start of the chain). The
    // interval: the one the last checkpoint was written with, or the one of this engine if larger.
    // Before the first checked checkpoint of a chain this is a break only if no signature is
    // accepted unchecked: such a chain may have had other settings.
    boolean overdue = settings.isSigned() && settings.getCheckpointInterval() > 0 && last - anchor >= due;
    gap |= overdue;
    if (found == null && spacing && overdue) {
      found = new Break(chainId, anchor + due + 1, null, Break.CHECKPOINT_MISSING, "No checkpoint after record " + anchor
          + ", although the chain goes on to record " + last + " and one is written every " + due
          + " records: checkpoints were removed.");
    }
    if (found != null && mayReadAgain && (Break.SEQUENCE_GAP.equals(found.getReason())
        || Break.MISSING_START.equals(found.getReason()) || Break.PURGE_UNATTESTED.equals(found.getReason()))
        && purgedSince(chainId, purgedThrough)) {
      // a purge removed records while the chain was read (a batch of it, since this reading began):
      // verify what is left
      return null;
    }
    boolean authenticated = found == null && settings.isSigned() && checkpointsVerified == checkpoints && !gap
        && (purgedThrough == 0 || purgeAttested);
    return new AuditVerification.Chain(chainId, records, firstSequence, last, lastHash, checkpoints, checkpointsVerified,
        purgedThrough, purgeAttested, authenticated, authenticated ? authenticatedThrough : 0, found);
  }

  /** Whether a purge record now names more removed records than the verification started from. */
  protected boolean purgedSince(String chainId, long purgedThrough) {
    List<AuditRecord> purges = commandExecutor.execute(commandContext -> AuditStore.purges(commandContext, settings, chainId));
    for (AuditRecord purge : purges) {
      Long through = number(purge.getDetails(), "purgedThroughSeq");
      if (through != null && through > purgedThrough) {
        return true;
      }
    }
    return false;
  }

  protected List<AuditRecord> read(String chainId, long after) {
    return commandExecutor.execute(commandContext -> AuditStore.readChain(commandContext, settings, chainId, after, pageSize));
  }

  /** What the purge records of a chain say about the records before its first one. */
  enum Purge {
    /** No purge record names them. */
    NONE,
    /** A purge record names them and carries a valid signature of a key of this engine. */
    SIGNED,
    /** A purge record names them, without a signature that can be checked; accepted, but it proves nothing. */
    UNATTESTED,
    /** A purge record names them, but a key is configured and the record is not signed with an accepted one. */
    REJECTED
  }

  /** Whether a purge record of the chain names the record before the first one as the last removed one, and how. */
  protected Purge purgeBefore(String chainId, AuditRecord first) {
    List<AuditRecord> purges = commandExecutor.execute(commandContext -> AuditStore.purges(commandContext, settings, chainId));
    Purge result = Purge.NONE;
    for (AuditRecord purge : purges) {
      Long through = number(purge.getDetails(), "purgedThroughSeq");
      String hash = text(purge.getDetails(), "purgedThroughHash");
      if (through == null || through != first.getSequence() - 1 || !AuditChain.same(hash, first.getPreviousHash())) {
        continue;
      }
      if (!settings.isSigned()) {
        return Purge.UNATTESTED;
      }
      String keyId = text(purge.getDetails(), "keyId");
      String hmac = text(purge.getDetails(), "hmac");
      byte[] key = settings.keyOf(keyId);
      if (key != null && hmac != null && AuditChain.same(hmac, AuditChain.hmacHex(key,
          AuditChain.purgeText(chainId, through, hash, purge.getId(), purge.getSequence())))) {
        return Purge.SIGNED;
      }
      if (key == null && settings.isRetiredKeyId(keyId)) {
        result = Purge.UNATTESTED;
      } else if (result == Purge.NONE) {
        result = Purge.REJECTED;
      }
    }
    return result;
  }

  /** A text value of the details, as {@link AuditJson} writes them (hex values need no unescaping). */
  static String text(String details, String key) {
    if (details == null) {
      return null;
    }
    Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\":\"([^\"\\\\]*)\"").matcher(details);
    return matcher.find() ? matcher.group(1) : null;
  }

  static Long number(String details, String key) {
    if (details == null) {
      return null;
    }
    Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\":(-?\\d+)").matcher(details);
    return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
  }
}
