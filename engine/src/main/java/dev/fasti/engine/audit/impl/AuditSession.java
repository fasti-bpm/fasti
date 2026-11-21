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

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
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
 * <p>When: appending locks the head of the chain until the transaction ends, so every other
 * transaction of that chain waits meanwhile. The sessions of a command context are flushed in the
 * reverse order of their opening.</p>
 * <ul>
 * <li>A command context whose transaction ends with it (the engine begins and commits it: a
 * standalone engine, a "requires new" command, or a command that found no transaction to join)
 * writes its records {@linkplain #isLast() last}, after the statements of the engine: the
 * interceptor of the plugin {@linkplain #openFirst(CommandContext) opens} the session before the
 * command runs. The head is then the last lock the command takes and is held for the audit
 * statements and the commit, including whatever the transaction manager writes at commit (with
 * JPA, the entity changes Hibernate flushes then).</li>
 * <li>Any other command context (one that joins a transaction that goes on after it, such as an
 * outer Spring transaction, or one that did not pass the interceptor) writes its records first,
 * before the statements of the engine, as up to 1.1: the session is
 * {@linkplain #of(CommandContext) opened} after the sessions of the engine. Its transaction holds
 * the head while it writes engine rows in this and in later commands; one that wrote an engine
 * row first and then waited for the head could wait for a transaction that waits for that row.</li>
 * </ul>
 *
 * <p>Which chain: one of the {@code chains} slots of this node, chosen by the thread, so that one
 * transaction that spans several commands (an outer Spring transaction) keeps appending to the
 * chain it already locked. The two kinds of transactions above use chains of their own
 * ({@code <node>/end-<slot>} for those that write last, {@code <node>/<slot>} for those that write
 * first): a transaction that writes its records last holds engine rows while it waits for the
 * head, so it must never wait for a head that a transaction holds which goes on to write engine
 * rows. Nodes of 1.0 and 1.1 write every record first on {@code <node>/<slot>}, so a node of this
 * version and one of 1.1 that share a node id (during an upgrade or a rollback) still never mix
 * the two kinds on one chain. An engine that cannot tell where its transactions end
 * ({@link AuditTransactionScope}) writes all records first, on the chains {@code <node>/<slot>}.
 * The heads of the slots of the first band are created at startup; any other head is created in a
 * transaction of its own on first use. A transaction that starts on a thread
 * while outer ones of the same thread hold chains (a nested "requires new") takes a slot of the
 * next band: with {@code n} chains, the outermost transactions use the slots {@code 0..n-1},
 * transactions nested in them {@code n..2n-1}, and so on. A transaction therefore only ever waits
 * for transactions of its own nesting depth, never for a suspended outer one (which would wait for
 * it in turn), so nested transactions of several threads cannot block each other in a ring that
 * the database cannot see.</p>
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
    /** Whether the transaction writes its records last: it ends with the command that took the chain. */
    final boolean last;
    /** The transaction context of the command context that took the chain (not kept alive by this). */
    final WeakReference<Object> transaction;
    final List<Held> owner;

    Held(AuditSettings settings, Object connection, String chainId, int depth, boolean last, Object transaction,
        List<Held> owner) {
      this.settings = settings;
      this.connection = connection;
      this.chainId = chainId;
      this.depth = depth;
      this.last = last;
      this.transaction = new WeakReference<>(transaction);
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

  /** Whether the records are written after the statements of the engine (see the class comment). */
  protected boolean last;

  public AuditSession(AuditSettings settings) {
    this.settings = settings;
  }

  /**
   * Opens the audit session of a command context whose transaction ends with it, before the
   * command runs: as the sessions of a command context are flushed in the reverse order of their
   * opening, its records are written after everything the engine writes. A session the command
   * context has already is returned as it is; one opened after the entity manager of the engine
   * (which sends all statements of the engine when it is flushed) writes its records first.
   */
  public static AuditSession openFirst(CommandContext commandContext) {
    Map<Class<?>, Session> sessions = commandContext.getSessions();
    if (sessions.containsKey(AuditSession.class)) {
      return commandContext.getSession(AuditSession.class);
    }
    boolean beforeEngine = !sessions.containsKey(DbEntityManager.class);
    AuditSession session = commandContext.getSession(AuditSession.class);
    session.last = beforeEngine;
    return session;
  }

  /**
   * The audit session of the command context. If the command context has none yet, it is opened
   * after the sessions of the engine, so that its records are written before the statements of
   * the engine (see the class comment).
   */
  public static AuditSession of(CommandContext commandContext) {
    if (!commandContext.getSessions().containsKey(AuditSession.class)) {
      commandContext.getDbEntityManager();
      commandContext.getDbSqlSession();
    }
    return commandContext.getSession(AuditSession.class);
  }

  /** Whether the records are written after the statements of the engine, at the end of the transaction. */
  public boolean isLast() {
    return last;
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
          if (entry.transaction.get() == commandContext.getTransactionContext() || (!last && !entry.last)) {
            // this command context took it before, or an earlier command of the transaction it joins
            return entry.chainId;
          }
          // a transaction that writes last began in this command, and one that wrote last ended
          // with its command: either way the transaction that held the chain on this connection
          // has ended without giving it back (see dropHeldSince), and a pool handed the connection
          // out again. Its chain may be of the other kind.
          held.remove(entry);
          LOG.debug("Audit trail chain {} was still marked as held by an ended transaction; it is given back", entry.chainId);
          continue;
        }
        depth = Math.max(depth, entry.depth + 1);
      }
    }
    int slot = slot(Thread.currentThread().getId(), settings.getChains(), depth);
    String chainId = last ? settings.endChainId(slot) : settings.chainId(slot);
    Held entry = new Held(settings, connection, chainId, depth, last, commandContext.getTransactionContext(), held);
    held.add(entry);
    commandContext.getTransactionContext().addTransactionListener(TransactionState.COMMITTED, context -> release(entry));
    commandContext.getTransactionContext().addTransactionListener(TransactionState.ROLLED_BACK, context -> release(entry));
    // also when the transaction ends in another state (Spring: a commit or rollback that failed)
    Consumer<Runnable> completion = settings.getTransactionCompletion();
    if (completion != null) {
      completion.accept(() -> release(entry));
    }
    return chainId;
  }

  /**
   * The slot of a transaction of the thread at a nesting depth (0 for the outermost that holds a
   * chain): the slot of the thread in the band of the depth.
   */
  static int slot(long threadId, int chains, int depth) {
    return depth * chains + Math.floorMod(Long.hashCode(threadId), chains);
  }

  /** The chains the transactions of this thread hold now, for {@link #dropHeldSince(List, AuditSettings)}. */
  static List<Held> heldSnapshot() {
    List<Held> held = HELD.get();
    if (held.isEmpty()) {
      HELD.remove();
      return List.of();
    }
    return new ArrayList<>(held);
  }

  /**
   * Gives back the chains of the engine ({@code null}: of every engine) that transactions of this
   * thread took after the snapshot. For a command that began its transaction and returns: that
   * transaction and every one nested in it have ended, but one that ended neither committed nor
   * rolled back did not give its chain back. Spring reports a commit or a rollback that failed (a
   * connection lost, a failover) as "unknown", and an afterCommit callback that throws skips those
   * after it. Left behind, such an entry would put every later transaction of the thread into the
   * next band, and its connection, handed out again by the pool, would bring back its chain.
   */
  static void dropHeldSince(List<Held> snapshot, AuditSettings settings) {
    List<Held> held = HELD.get();
    for (Held entry : held) {
      if ((settings == null || entry.settings == settings) && !snapshot.contains(entry)) {
        held.remove(entry);
        LOG.debug("Audit trail chain {} was not given back when its transaction ended; it is given back now", entry.chainId);
      }
    }
    if (held.isEmpty()) {
      HELD.remove();
    }
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
