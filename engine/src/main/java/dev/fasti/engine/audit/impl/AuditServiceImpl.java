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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dev.fasti.engine.audit.AuditEventQuery;
import dev.fasti.engine.audit.AuditPurgeResult;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditStatus;
import dev.fasti.engine.audit.AuditVerificationRunningException;
import dev.fasti.engine.audit.AuditVerification;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The audit trail service of one engine. */
public class AuditServiceImpl implements AuditService {

  private static final Logger LOG = LoggerFactory.getLogger(AuditServiceImpl.class);

  protected final AuditSettings settings;
  protected final CommandExecutor commandExecutor;
  protected final CommandExecutor requiresNew;

  public AuditServiceImpl(ProcessEngineConfigurationImpl configuration) {
    this.settings = settingsOf(configuration);
    this.commandExecutor = configuration.getCommandExecutorTxRequired();
    this.requiresNew = configuration.getCommandExecutorTxRequiresNew();
  }

  /** The settings of the plugin registered with the engine, or {@code null}. */
  public static AuditSettings settingsOf(ProcessEngineConfigurationImpl configuration) {
    Map<Class<?>, SessionFactory> factories = configuration.getSessionFactories();
    SessionFactory factory = factories == null ? null : factories.get(AuditSession.class);
    return factory instanceof AuditSession.Factory ? ((AuditSession.Factory) factory).getSettings() : null;
  }

  @Override
  public boolean isEnabled() {
    return settings != null;
  }

  @Override
  public AuditStatus getStatus() {
    return settings;
  }

  @Override
  public boolean isReadAuthorized() {
    if (settings == null) {
      return false;
    }
    return commandExecutor.execute(commandContext -> {
      try {
        AuditAuthorization.checkRead(commandContext);
        return true;
      } catch (AuthorizationException e) {
        return false;
      }
    });
  }

  protected AuditSettings requireEnabled() {
    if (settings == null) {
      throw new BadUserRequestException("The audit trail is not enabled on this process engine (fasti.audit.enabled).");
    }
    return settings;
  }

  @Override
  public AuditEventQuery createQuery() {
    return new AuditEventQueryImpl(commandExecutor, requireEnabled());
  }

  @Override
  public AuditVerification verify() {
    return verify(null);
  }

  @Override
  public AuditVerification verify(String chainId) {
    AuditSettings settings = requireEnabled();
    List<String> chains = commandExecutor.execute(commandContext -> {
      AuditAuthorization.checkRead(commandContext);
      return AuditStore.chainIds(commandContext, settings);
    });
    if (chainId != null && !chainId.isBlank()) {
      if (!chains.contains(chainId)) {
        throw new BadUserRequestException("The audit trail has no chain '" + chainId + "'.");
      }
      chains = List.of(chainId);
    }
    // one at a time: a verification reads and hashes every record
    if (!settings.startVerifying()) {
      throw new AuditVerificationRunningException();
    }
    AuditVerification verification;
    try {
      AuditVerifier verifier = new AuditVerifier(commandExecutor, settings);
      List<AuditVerification.Chain> results = new ArrayList<>();
      for (String chain : chains) {
        results.add(verifier.verifyChain(chain));
      }
      verification = new AuditVerification(ClockUtil.getCurrentTime(), results);
      try {
        record(AuditService.ACTION_VERIFY, verifyDetails(verification, chainId));
      } catch (RuntimeException e) {
        // a broken chain may not take another record (a record inserted from outside holds the next
        // position); the verification is still reported
        LOG.warn("The verification of the audit trail could not be recorded: {}", e.getMessage());
      }
    } finally {
      settings.endVerifying();
    }
    if (verification.isIntact()) {
      LOG.info("Audit trail verified: {} records in {} chains are intact{}", verification.getRecords(),
          verification.getChains().size(), verification.isAuthenticated() ? " and authenticated"
              : settings.isSigned() ? ", but not every signature could be checked" : " (not signed)");
    } else {
      LOG.warn("Audit trail verification found a break: {}", verification.getFirstBreak().getMessage());
    }
    return verification;
  }

  static AuditJson verifyDetails(AuditVerification verification, String chainId) {
    AuditJson details = AuditJson.object()
        .put("intact", verification.isIntact())
        .put("authenticated", verification.isAuthenticated())
        .put("chains", verification.getChains().size())
        .put("records", verification.getRecords())
        .put("chainId", chainId);
    if (verification.getFirstBreak() != null) {
      details.put("breakChainId", verification.getFirstBreak().getChainId())
          .put("breakSequence", verification.getFirstBreak().getSequence())
          .put("breakReason", verification.getFirstBreak().getReason());
    }
    return details;
  }

  @Override
  public void recordExport(String format, String filter, long count) {
    requireEnabled();
    record(AuditService.ACTION_EXPORT, AuditJson.object().put("format", format).put("filter", filter).put("count", count));
  }

  /** Appends a record about the audit trail itself, as the authenticated user. */
  protected void record(String action, AuditJson details) {
    requireEnabled();
    commandExecutor.execute(commandContext -> {
      AuditAuthorization.checkRead(commandContext);
      AuditRecord record = new AuditRecord(AuditService.TYPE_AUDIT, action);
      record.setEntityType("audit-trail");
      record.setDetails(details.toJson());
      AuditHistoryEventHandler.session(commandContext).add(record, true);
      return null;
    });
  }

  @Override
  public AuditPurgeResult purge(Date before) {
    AuditSettings settings = requireEnabled();
    if (before == null) {
      throw new BadUserRequestException("A date is required: records before it are purged.");
    }
    long now = ClockUtil.getCurrentTime().getTime();
    long cutoff = now - TimeUnit.DAYS.toMillis(settings.getRetentionDays());
    if (before.getTime() > cutoff) {
      throw new BadUserRequestException("Records younger than " + settings.getRetentionDays()
          + " days are kept (fasti.audit.retention-days); choose a date before " + new Date(cutoff).toInstant() + ".");
    }
    if (AuditSession.holdsChain(settings)) {
      // it waits for the head of every chain in turn; inside a transaction that already holds one,
      // it would wait for the suspended transaction of its own thread
      throw new BadUserRequestException("Purge the audit trail in a transaction of its own, not inside one that has "
          + "written audit records.");
    }
    List<String> chainIds = requiresNew.execute(commandContext -> {
      AuditAuthorization.checkPurge(commandContext);
      return AuditStore.chainIds(commandContext, settings);
    });
    Map<String, Long> purgedThrough = new LinkedHashMap<>();
    List<String> failed = new ArrayList<>();
    long removed = 0;
    // one transaction per batch of a chain: a purge holds one head at a time, for one batch, and
    // nothing else meanwhile, so it never waits for a transaction while another waits for it (a
    // transaction holding a head of one kind of chain may wait for engine rows, see AuditSession),
    // and the writers of the chain wait for one batch at most
    for (String chainId : chainIds) {
      long[] progress = {0, 0};
      try {
        while (true) {
          long[] batch = requiresNew.execute(commandContext -> purgeBatch(commandContext, settings, before, chainId));
          if (batch == null) {
            break;
          }
          progress[0] = batch[0];
          progress[1] += batch[1];
          if (batch[2] != 0) {
            break;
          }
        }
      } catch (AuthorizationException e) {
        throw e;
      } catch (RuntimeException e) {
        // the other chains are purged all the same; this one stays as its last batch left it
        LOG.error("The audit trail could not purge chain {}; the other chains are purged. Purge again to complete it.",
            chainId, e);
        failed.add(chainId);
      }
      if (progress[0] > 0) {
        purgedThrough.put(chainId, progress[0]);
        removed += progress[1];
      }
    }
    return new AuditPurgeResult(before, purgedThrough, removed, failed);
  }

  /**
   * Purges one batch of the records of a chain before the date: from its first record, at most
   * {@code purgeBatchSize} records, up to the first record at or after the date. Returns the
   * position purged through, the number of records removed and whether the purge of the chain is
   * complete (1) or not (0), or {@code null} when nothing is left to purge.
   */
  protected long[] purgeBatch(CommandContext commandContext, AuditSettings settings, Date before, String chainId) {
    AuditAuthorization.checkPurge(commandContext);
    AuditStore.Head head;
    try {
      // the head first, so that what is read below is not purged at the same time by another purge
      head = AuditStore.lockHead(commandContext, settings, chainId, false);
    } catch (SQLException e) {
      throw AuditStore.failure("lock chain " + chainId + " for a purge", e);
    }
    if (head == null) {
      // records without a head: not recreated here, so that verifying still reports it
      LOG.warn("Audit trail chain {} has records but no head; it is not purged. Verify the audit trail.", chainId);
      throw new ProcessEngineException("The audit trail chain " + chainId + " has no head; verify the audit trail.");
    }
    AuditStore.PurgeRange range = AuditStore.purgeRange(commandContext, settings, chainId, before.getTime(),
        settings.getPurgeBatchSize());
    if (range == null) {
      return null;
    }
    long from = range.from;
    long through = range.through;
    long count = through - from + 1;
    AuditRecord purge = new AuditRecord(AuditService.TYPE_AUDIT, AuditService.ACTION_PURGE);
    purge.setTime(ClockUtil.getCurrentTime().getTime());
    purge.setEngineName(settings.getEngineName());
    purge.setActor(commandContext.getAuthenticatedUserId());
    purge.setEntityType("chain");
    purge.setEntityName(chainId);
    purge.setPurge(through, range.throughHash, AuditJson.object()
        .put("before", before.toInstant().toString())
        .put("purgedFromSeq", from)
        .put("removed", count));
    // the purge record first, then the records it attests
    AuditStore.append(commandContext, settings, chainId, List.of(purge));
    long deleted = AuditStore.deleteThrough(commandContext, settings, chainId, through);
    LOG.info("Audit trail chain {}: purged records {} to {} (last hash {})", chainId, from, through, range.throughHash);
    return new long[] { through, deleted, range.complete ? 1 : 0 };
  }
}
