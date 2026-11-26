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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signs the ends of the chains of this node. A chain gets a checkpoint every
 * {@code checkpointInterval} records; the records after the last one are covered by no signature.
 * A node that writes few records, or one that starts often under a new node id (a pod), may never
 * reach the interval. So, with a key, a checkpoint is also written after the last record of each
 * chain of this node that has records after its last checkpoint: when the engine closes, and every
 * {@code checkpointIntervalSeconds} (0: only when it closes).
 *
 * <p>Each chain is signed in a transaction of its own that holds its head only for the checkpoint
 * and waits for it at most {@value #LOCK_TIMEOUT_SECONDS} seconds: a chain that a transaction holds
 * for longer is signed the next time. A checkpoint written this way comes earlier than the interval
 * puts one, which verification accepts (also that of earlier versions): only a missing checkpoint
 * is a break.</p>
 *
 * <p>The last pass runs when the engine closes, after its own job executor has stopped. Commands
 * that still run then (a request being served, a job of a job executor shared with other engines,
 * which goes on when this engine leaves it) can append records after that checkpoint; they are
 * signed when the node runs again with the same node id.</p>
 */
public class AuditCheckpointer {

  private static final Logger LOG = LoggerFactory.getLogger(AuditCheckpointer.class);

  /** Seconds a checkpoint waits for the head of its chain. */
  static final int LOCK_TIMEOUT_SECONDS = 5;

  protected final AuditSettings settings;
  protected final CommandExecutor commandExecutor;
  protected final Pattern ownChains;
  /** The position of each chain at its last checkpoint seen: a chain that has not moved since is not read again. */
  protected final Map<String, Long> signedAt = new ConcurrentHashMap<>();
  protected ScheduledExecutorService scheduler;

  /** With the executor of commands that run in a transaction of their own. */
  public AuditCheckpointer(AuditSettings settings, CommandExecutor commandExecutor) {
    this.settings = settings;
    this.commandExecutor = commandExecutor;
    // <node>/<slot> and <node>/end-<slot>: the chains this node writes to
    this.ownChains = Pattern.compile(Pattern.quote(settings.getNodeId()) + "/(" + Pattern.quote(AuditSettings.END_CHAIN_PREFIX)
        + ")?\\d+");
  }

  /** Whether checkpoints are written at all: with a key and checkpoints on. */
  public boolean isActive() {
    return settings.isSigned() && settings.getCheckpointInterval() > 0;
  }

  /** Signs the chains of this node every so many seconds, from now on (nothing with 0). */
  public synchronized void start(int seconds) {
    if (!isActive() || seconds <= 0 || scheduler != null) {
      return;
    }
    scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "fasti-audit-checkpoints-" + settings.getEngineName());
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleWithFixedDelay(this::signQuietly, seconds, seconds, TimeUnit.SECONDS);
  }

  /** Stops signing every so many seconds and signs the chains of this node a last time. */
  public void close() {
    synchronized (this) {
      if (scheduler != null) {
        scheduler.shutdown();
        try {
          if (!scheduler.awaitTermination(LOCK_TIMEOUT_SECONDS + 5L, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
          }
        } catch (InterruptedException e) {
          scheduler.shutdownNow();
          Thread.currentThread().interrupt();
        }
        scheduler = null;
      }
    }
    signQuietly();
  }

  protected void signQuietly() {
    try {
      if (isActive() && commandExecutor.execute(AuditStore::isTablePresent)) {
        sign();
      }
    } catch (RuntimeException e) {
      LOG.warn("The audit trail could not sign the ends of the chains of node {}: {}", settings.getNodeId(), e.getMessage());
    }
  }

  /** Writes a checkpoint after the last record of each chain of this node that needs one; how many. */
  public int sign() {
    if (!isActive()) {
      return 0;
    }
    List<AuditStore.Head> heads = commandExecutor.execute(commandContext -> AuditStore.heads(commandContext, settings));
    int written = 0;
    for (AuditStore.Head head : heads) {
      if (head.sequence <= 0 || !ownChains.matcher(head.chainId).matches()
          || Long.valueOf(head.sequence).equals(signedAt.get(head.chainId))) {
        continue;
      }
      try {
        AuditRecord checkpoint = commandExecutor.execute(commandContext -> AuditStore.checkpointTail(commandContext, settings,
            head.chainId, LOCK_TIMEOUT_SECONDS));
        if (checkpoint == null) {
          // the end is a checkpoint, or the chain cannot be signed: not read again until it moves
          signedAt.put(head.chainId, head.sequence);
        } else {
          signedAt.put(head.chainId, checkpoint.getSequence());
          written++;
          LOG.info("Audit trail checkpoint {}#{}: record hash {}, checkpoint hash {}", checkpoint.getChainId(),
              checkpoint.getSequence() - 1, checkpoint.getPreviousHash(), checkpoint.getHash());
        }
      } catch (RuntimeException e) {
        LOG.warn("The audit trail could not sign the end of chain {}; it is signed later: {}", head.chainId, e.getMessage());
      }
    }
    return written;
  }
}
