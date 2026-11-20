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
 */
public class AuditVerifier {

  static final int PAGE = 1000;

  protected final CommandExecutor commandExecutor;
  protected final AuditSettings settings;

  public AuditVerifier(CommandExecutor commandExecutor, AuditSettings settings) {
    this.commandExecutor = commandExecutor;
    this.settings = settings;
  }

  public AuditVerification.Chain verifyChain(String chainId) {
    long records = 0;
    long checkpoints = 0;
    long checkpointsVerified = 0;
    long purgedThrough = 0;
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
      } else if (isAttestedPurge(chainId, first)) {
        purgedThrough = first.getSequence() - 1;
        previous = first.getPreviousHash();
      } else {
        found = new Break(chainId, 1, null, Break.MISSING_START, "The chain starts at record " + first.getSequence()
            + " and no purge record names the records before it: records at the start were removed.");
      }
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
          if (hmac != null && settings.isSigned() && settings.getKeyId().equals(keyId)) {
            String expectedHmac = AuditChain.hmacHex(settings.getHmacKey(),
                AuditChain.checkpointText(chainId, record.getSequence() - 1, record.getPreviousHash()));
            if (!AuditChain.same(expectedHmac, hmac)) {
              found = new Break(chainId, record.getSequence(), record.getId(), Break.CHECKPOINT_MISMATCH, "The signature of "
                  + "checkpoint " + record.getSequence() + " does not match: the chain before it was rewritten.");
              break;
            }
            checkpointsVerified++;
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
      if (page.size() == PAGE) {
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
          if (records == 0 && firstSequence == 0) {
            // the chain was empty when it was first read
            return verifyChain(chainId);
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
    return new AuditVerification.Chain(chainId, records, firstSequence, last, lastHash, checkpoints, checkpointsVerified,
        purgedThrough, found);
  }

  protected List<AuditRecord> read(String chainId, long after) {
    return commandExecutor.execute(commandContext -> AuditStore.readChain(commandContext, settings, chainId, after, PAGE));
  }

  /** Whether a purge record of the chain names the record before the first one as the last removed one. */
  protected boolean isAttestedPurge(String chainId, AuditRecord first) {
    List<AuditRecord> purges = commandExecutor.execute(commandContext -> AuditStore.purges(commandContext, settings, chainId));
    for (AuditRecord purge : purges) {
      Long through = number(purge.getDetails(), "purgedThroughSeq");
      String hash = text(purge.getDetails(), "purgedThroughHash");
      if (through != null && through == first.getSequence() - 1 && AuditChain.same(hash, first.getPreviousHash())) {
        return true;
      }
    }
    return false;
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
