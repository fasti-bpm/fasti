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
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signs the ends of chains. A chain gets a checkpoint every {@code checkpointInterval} records;
 * the records after the last one are covered by no signature. A node that writes few records, or
 * one that starts often under a new node id (a pod), may never reach the interval. So, with a key,
 * a checkpoint is also written after the last record of each chain that has records after its last
 * checkpoint:
 * <ul>
 * <li>the chains of this node every {@code checkpointIntervalSeconds} and when the engine closes;</li>
 * <li>the chains of other node ids every {@code checkpointIntervalSeconds}, once their head has
 * not moved for {@link #orphanIdleMillis()} (three intervals, at least 15 minutes): chains that a
 * node left behind (an earlier node id, a pod that was killed, a node of an earlier version), which
 * would otherwise stay unsigned for good. At most {@value #ORPHANS_PER_ROUND} of them per round.</li>
 * </ul>
 *
 * <p>Each chain is signed in a transaction of its own that holds its head only for the checkpoint
 * and waits for it at most {@value #LOCK_TIMEOUT_SECONDS} seconds: a chain that a transaction holds
 * for longer is signed the next time. Under the lock the last record must still be the one the
 * head names, unchanged, and not a checkpoint; a chain of another node must still be where it was
 * when it was found idle. So nodes that sign the same chain at the same time write one checkpoint:
 * the second finds a checkpoint at the end. A checkpoint written this way comes earlier than the
 * interval puts one, which verification accepts (also that of earlier versions): only a missing
 * checkpoint is a break. A late checkpoint attests the records as they are when it is written.</p>
 *
 * <p>The last pass runs when the engine closes, after its own job executor has stopped, and within
 * {@value #CLOSE_SECONDS} seconds in all; it signs only the chains of this node. Commands that still
 * run then (a request being served, a job of a job executor shared with other engines, which goes
 * on when this engine leaves it) can append records after that checkpoint, and a chain not signed
 * in time stays as it is: they are signed when the node runs again with the same node id, or as a
 * chain left behind by another node.</p>
 */
public class AuditCheckpointer {

  private static final Logger LOG = LoggerFactory.getLogger(AuditCheckpointer.class);

  /** Seconds a checkpoint waits for the head of its chain. */
  static final int LOCK_TIMEOUT_SECONDS = 5;
  /** Seconds the engine waits for its last checkpoints when it closes, in all. */
  static final int CLOSE_SECONDS = 10;
  /** Chains of other node ids tried per round, so that many old chains do not hold up a round. */
  static final int ORPHANS_PER_ROUND = 100;
  /** The least time a chain of another node id must be idle before it is signed. */
  static final long MIN_ORPHAN_IDLE_MILLIS = TimeUnit.MINUTES.toMillis(15);

  protected final AuditSettings settings;
  protected final CommandExecutor commandExecutor;
  protected final Pattern ownChains;
  /** The position of each chain at its last checkpoint seen: a chain that has not moved since is not read again. */
  protected final Map<String, Long> signedAt = new ConcurrentHashMap<>();
  protected ScheduledExecutorService scheduler;
  /** Set when the engine closes: a round of the timer that runs then stops after its current chain. */
  protected volatile boolean closing;

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

  /** How long the head of a chain of another node id must not have moved before it is signed. */
  protected long orphanIdleMillis() {
    return Math.max(MIN_ORPHAN_IDLE_MILLIS, TimeUnit.SECONDS.toMillis(3L * settings.getCheckpointIntervalSeconds()));
  }

  /** Signs chains every so many seconds, from now on (nothing with 0). */
  public synchronized void start(int seconds) {
    if (!isActive() || seconds <= 0 || scheduler != null) {
      return;
    }
    scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "fasti-audit-checkpoints-" + settings.getEngineName());
      thread.setDaemon(true);
      return thread;
    });
    scheduler.scheduleWithFixedDelay(() -> signQuietly(Long.MAX_VALUE, true), seconds, seconds, TimeUnit.SECONDS);
  }

  /**
   * Stops signing every so many seconds and signs the chains of this node a last time, within
   * {@value #CLOSE_SECONDS} seconds in all.
   */
  public void close() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLOSE_SECONDS);
    closing = true;
    synchronized (this) {
      if (scheduler != null) {
        scheduler.shutdown();
        try {
          // a round that runs stops after its current chain, which waits at most the lock timeout
          if (!scheduler.awaitTermination(LOCK_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
          }
        } catch (InterruptedException e) {
          scheduler.shutdownNow();
          Thread.currentThread().interrupt();
        }
        scheduler = null;
      }
    }
    signQuietly(deadline, false);
  }

  protected void signQuietly(long deadline, boolean orphans) {
    try {
      if (isActive() && commandExecutor.execute(AuditStore::isTablePresent)) {
        sign(deadline, orphans);
      }
    } catch (RuntimeException e) {
      LOG.warn("The audit trail could not sign the ends of chains of node {}: {}", settings.getNodeId(), e.getMessage());
    }
  }

  /** One round without a deadline: the chains of this node, and idle chains of other node ids; how many were signed. */
  public int sign() {
    return sign(Long.MAX_VALUE, true);
  }

  /**
   * Writes a checkpoint after the last record of each chain of this node that needs one and, with
   * {@code orphans}, of up to {@value #ORPHANS_PER_ROUND} idle chains of other node ids; stops at
   * the deadline ({@link System#nanoTime()}, {@link Long#MAX_VALUE} for none). How many were written.
   */
  protected int sign(long deadline, boolean orphans) {
    if (!isActive()) {
      return 0;
    }
    List<AuditStore.Head> heads = commandExecutor.execute(commandContext -> AuditStore.heads(commandContext, settings));
    long idleBefore = ClockUtil.getCurrentTime().getTime() - orphanIdleMillis();
    int written = 0;
    int orphansTried = 0;
    for (AuditStore.Head head : heads) {
      if (head.sequence <= 0 || Long.valueOf(head.sequence).equals(signedAt.get(head.chainId))) {
        continue;
      }
      boolean own = ownChains.matcher(head.chainId).matches();
      if (!own && (!orphans || head.time > idleBefore || orphansTried >= ORPHANS_PER_ROUND)) {
        continue;
      }
      int lockTimeout = LOCK_TIMEOUT_SECONDS;
      if (deadline != Long.MAX_VALUE) {
        lockTimeout = (int) Math.min(LOCK_TIMEOUT_SECONDS, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()));
      }
      // a round of the timer stops when the engine closes; the last pass stops at its deadline
      if ((orphans && closing) || lockTimeout < 1) {
        LOG.info("The audit trail stopped signing the ends of chains before chain {}; they are signed later", head.chainId);
        break;
      }
      if (!own) {
        orphansTried++;
      }
      int timeout = lockTimeout;
      try {
        // a chain of another node only where it was found idle: one that moved since is in use
        AuditRecord checkpoint = commandExecutor.execute(commandContext -> AuditStore.checkpointTail(commandContext, settings,
            head.chainId, timeout, own ? -1 : head.sequence));
        if (checkpoint == null) {
          // the end is a checkpoint, the chain moved, or it cannot be signed: not read again until it moves
          signedAt.put(head.chainId, head.sequence);
        } else {
          signedAt.put(head.chainId, checkpoint.getSequence());
          written++;
          LOG.info("Audit trail checkpoint {}#{}{}: record hash {}, checkpoint hash {}", checkpoint.getChainId(),
              checkpoint.getSequence() - 1, own ? "" : " (a chain of another node, idle)", checkpoint.getPreviousHash(),
              checkpoint.getHash());
        }
      } catch (RuntimeException e) {
        LOG.warn("The audit trail could not sign the end of chain {}; it is signed later: {}", head.chainId, e.getMessage());
      }
    }
    return written;
  }
}
