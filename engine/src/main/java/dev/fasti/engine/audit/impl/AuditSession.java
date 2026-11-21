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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.Session;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The audit records of one command context. They are collected while the command runs and
 * appended to a chain when the command context flushes, in its transaction.
 *
 * <p>When: after the engine has sent its own statements. Appending locks the head of the chain
 * until the transaction ends, so every other transaction of that chain waits meanwhile; written
 * last, the records hold the head for their own statements and the commit only. The sessions of a
 * command context are flushed in the reverse order of their opening, so the audit session is
 * opened {@linkplain #open(CommandContext) first}, by the interceptor of the plugin, before the
 * command runs. A session that is opened later (in a command context that did not pass the
 * interceptor) still writes its records in the transaction of the command, only before the
 * statements of the engine, holding the head while they run.</p>
 *
 * <p>Which chain: one of the {@code chains} slots of this node, chosen by the thread, so that one
 * transaction that spans several commands (an outer Spring transaction) keeps appending to the
 * chain it already locked. A transaction that starts on a thread while outer ones of the same
 * thread hold chains (a nested "requires new") takes a slot of the next band: with {@code n}
 * chains, the outermost transactions use the slots {@code 0..n-1}, transactions nested in them
 * {@code n..2n-1}, and so on. A transaction therefore only ever waits for transactions of its own
 * nesting depth, never for a suspended outer one (which would wait for it in turn), so nested
 * transactions of several threads cannot block each other in a ring that the database cannot see.</p>
 */
public class AuditSession implements Session {

  private static final Logger LOG = LoggerFactory.getLogger(AuditSession.class);

  /**
   * The chains held by the open transactions of this thread. The list is safe for concurrent use,
   * because a transaction manager may complete a transaction on another thread (a timeout).
   */
  static final ThreadLocal<List<Held>> HELD = ThreadLocal.withInitial(CopyOnWriteArrayList::new);

  /** A chain held by a transaction: of one engine (its settings) on one connection. */
  static final class Held {
    final AuditSettings settings;
    final Object connection;
    final String chainId;
    final int depth;
    final List<Held> owner;

    Held(AuditSettings settings, Object connection, String chainId, int depth, List<Held> owner) {
      this.settings = settings;
      this.connection = connection;
      this.chainId = chainId;
      this.depth = depth;
      this.owner = owner;
    }
  }

  protected final AuditSettings settings;
  protected final List<AuditRecord> records = new ArrayList<>();
  /** Business keys of process instances, looked up once per command context. */
  protected Map<String, String> businessKeys;
  /** Deployments recorded in this command context ({@code <id>:<action>}). */
  protected final Set<String> deployments = new HashSet<>();
  /** The assignee of each task as last recorded in this command context, to tell assignments apart. */
  protected final Map<String, String> assignees = new HashMap<>();

  public AuditSession(AuditSettings settings) {
    this.settings = settings;
  }

  /**
   * The audit session of the command context, opened if the command context has none yet. Call
   * it before the command runs: the sessions of a command context are flushed in the reverse order
   * of their opening, and the records are to be written after everything the engine writes.
   */
  public static AuditSession open(CommandContext commandContext) {
    return commandContext.getSession(AuditSession.class);
  }

  /** Adds a record, stamped with the clock of the engine, the authenticated user (unless set) and the engine. */
  public void add(AuditRecord record, boolean stampActor) {
    if (record.getTime() == 0) {
      record.setTime(ClockUtil.getCurrentTime().getTime());
    }
    record.setEngineName(settings.getEngineName());
    if (stampActor && record.getActor() == null) {
      CommandContext commandContext = Context.getCommandContext();
      if (commandContext != null) {
        record.setActor(commandContext.getAuthenticatedUserId());
      }
    }
    records.add(record);
  }

  /** Whether the deployment change was recorded in this command context already; marks it as recorded. */
  public boolean isDeploymentRecorded(String key) {
    return !deployments.add(key);
  }

  public List<AuditRecord> getRecords() {
    return records;
  }

  @Override
  public void flush() {
    if (records.isEmpty()) {
      return;
    }
    CommandContext commandContext = Context.getCommandContext();
    List<AuditRecord> pending = new ArrayList<>(records);
    records.clear();
    String chainId = chainFor(commandContext);
    List<AuditRecord> checkpoints = AuditStore.append(commandContext, settings, chainId, pending);
    for (AuditRecord checkpoint : checkpoints) {
      LOG.info("Audit trail checkpoint {}#{}: record hash {}, checkpoint hash {}", checkpoint.getChainId(),
          checkpoint.getSequence() - 1, checkpoint.getPreviousHash(), checkpoint.getHash());
    }
  }

  /** Whether a transaction of this thread holds a chain of the engine (has written records not yet committed). */
  public static boolean holdsChain(AuditSettings settings) {
    List<Held> held = HELD.get();
    if (held.isEmpty()) {
      HELD.remove();
      return false;
    }
    for (Held entry : held) {
      if (entry.settings == settings) {
        return true;
      }
    }
    return false;
  }

  /** The chain of the transaction of the command context, taken on its first use. */
  protected String chainFor(CommandContext commandContext) {
    Object connection = physical(AuditStore.connection(commandContext));
    List<Held> held = HELD.get();
    // below the deepest transaction of this thread that holds a chain of this engine (not their
    // number: an outer one may have been ended from another thread while its nested one goes on)
    int depth = 0;
    for (Held entry : held) {
      if (entry.settings == settings) {
        if (entry.connection == connection) {
          return entry.chainId;
        }
        depth = Math.max(depth, entry.depth + 1);
      }
    }
    String chainId = settings.chainId(slot(Thread.currentThread().getId(), settings.getChains(), depth));
    Held entry = new Held(settings, connection, chainId, depth, held);
    held.add(entry);
    commandContext.getTransactionContext().addTransactionListener(TransactionState.COMMITTED, context -> release(entry));
    commandContext.getTransactionContext().addTransactionListener(TransactionState.ROLLED_BACK, context -> release(entry));
    return chainId;
  }

  /**
   * The slot of a transaction of the thread at a nesting depth (0 for the outermost that holds a
   * chain): the slot of the thread in the band of the depth.
   */
  static int slot(long threadId, int chains, int depth) {
    return depth * chains + Math.floorMod(Long.hashCode(threadId), chains);
  }

  /** Lets go of the chain when its transaction ended, on whichever thread that is reported. */
  static void release(Held entry) {
    entry.owner.remove(entry);
    if (HELD.get().isEmpty()) {
      HELD.remove();
    }
  }

  /**
   * The connection behind wrappers (such as the transaction-aware proxy of Spring, which hands
   * out a new proxy per command): the metadata of a connection names the connection it belongs to.
   */
  static Object physical(Connection connection) {
    try {
      Connection inner = connection.getMetaData().getConnection();
      return inner == null ? connection : inner;
    } catch (SQLException | RuntimeException e) {
      return connection;
    }
  }

  @Override
  public void close() {
    records.clear();
    businessKeys = null;
    deployments.clear();
    assignees.clear();
  }

  /** Opens an {@link AuditSession} per command context and holds the settings of the plugin. */
  public static class Factory implements SessionFactory {

    protected final AuditSettings settings;

    public Factory(AuditSettings settings) {
      this.settings = settings;
    }

    public AuditSettings getSettings() {
      return settings;
    }

    @Override
    public Class<?> getSessionType() {
      return AuditSession.class;
    }

    @Override
    public Session openSession() {
      return new AuditSession(settings);
    }
  }
}
