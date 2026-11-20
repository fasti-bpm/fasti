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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>Which chain: one of the {@code chains} slots of this node, chosen by the thread, so that one
 * transaction that spans several commands (an outer Spring transaction) keeps appending to the
 * chain it already locked. A transaction that starts on a thread while an outer one of the same
 * thread holds a chain (a nested "requires new") takes another slot, so it cannot wait for the
 * lock of its own outer transaction.</p>
 */
public class AuditSession implements Session {

  private static final Logger LOG = LoggerFactory.getLogger(AuditSession.class);

  /** The chains held by the open transactions of this thread, by their connection. */
  static final ThreadLocal<List<Held>> HELD = ThreadLocal.withInitial(ArrayList::new);

  static final class Held {
    final Object connection;
    final String chainId;

    Held(Object connection, String chainId) {
      this.connection = connection;
      this.chainId = chainId;
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

  /** The chain of the transaction of the command context, taken on its first use. */
  protected String chainFor(CommandContext commandContext) {
    Object connection = physical(AuditStore.connection(commandContext));
    List<Held> held = HELD.get();
    for (Held entry : held) {
      if (entry.connection == connection) {
        return entry.chainId;
      }
    }
    int preferred = Math.floorMod(Long.hashCode(Thread.currentThread().getId()), settings.getChains());
    String chainId = null;
    for (int i = 0; i < settings.getChains() && chainId == null; i++) {
      String candidate = settings.chainId((preferred + i) % settings.getChains());
      boolean taken = false;
      for (Held entry : held) {
        taken |= entry.chainId.equals(candidate);
      }
      if (!taken) {
        chainId = candidate;
      }
    }
    if (chainId == null) {
      // more nested transactions on this thread than chains: share the preferred one
      chainId = settings.chainId(preferred);
    }
    Held entry = new Held(connection, chainId);
    held.add(entry);
    commandContext.getTransactionContext().addTransactionListener(TransactionState.COMMITTED, context -> release(entry));
    commandContext.getTransactionContext().addTransactionListener(TransactionState.ROLLED_BACK, context -> release(entry));
    return chainId;
  }

  static void release(Held entry) {
    List<Held> held = HELD.get();
    for (Iterator<Held> iterator = held.iterator(); iterator.hasNext();) {
      if (iterator.next() == entry) {
        iterator.remove();
      }
    }
    if (held.isEmpty()) {
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
