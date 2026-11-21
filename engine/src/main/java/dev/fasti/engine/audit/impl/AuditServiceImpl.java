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
    if (AuditSession.holdsChain()) {
      // it locks the heads of all chains in order; inside a transaction that already holds one, two
      // purges could wait for each other
      throw new BadUserRequestException("Purge the audit trail in a transaction of its own, not inside one that has "
          + "written audit records.");
    }
    return requiresNew.execute(commandContext -> purge(commandContext, settings, before));
  }

  protected AuditPurgeResult purge(CommandContext commandContext, AuditSettings settings, Date before) {
    AuditAuthorization.checkPurge(commandContext);
    Map<String, Long> purgedThrough = new LinkedHashMap<>();
    long removed = 0;
    // chains in sorted order, so that two purges at the same time lock their heads in the same order
    for (String chainId : AuditStore.chainIds(commandContext, settings)) {
      long through = AuditStore.purgeBoundary(commandContext, settings, chainId, before.getTime());
      List<AuditRecord> first = AuditStore.readChain(commandContext, settings, chainId, 0, 1);
      if (through <= 0 || first.isEmpty() || through < first.get(0).getSequence()) {
        continue;
      }
      AuditRecord last = AuditStore.find(commandContext, settings, chainId, through);
      if (last == null) {
        LOG.warn("Audit trail chain {} has no record {}; it is not purged. Verify the audit trail.", chainId, through);
        continue;
      }
      long count = through - first.get(0).getSequence() + 1;
      AuditRecord purge = new AuditRecord(AuditService.TYPE_AUDIT, AuditService.ACTION_PURGE);
      purge.setTime(ClockUtil.getCurrentTime().getTime());
      purge.setEngineName(settings.getEngineName());
      purge.setActor(commandContext.getAuthenticatedUserId());
      purge.setEntityType("chain");
      purge.setEntityName(chainId);
      purge.setPurge(through, last.getHash(), AuditJson.object()
          .put("before", before.toInstant().toString())
          .put("purgedFromSeq", first.get(0).getSequence())
          .put("removed", count));
      // the purge record first: appending locks the head of the chain
      AuditStore.append(commandContext, settings, chainId, List.of(purge));
      long deleted = AuditStore.deleteThrough(commandContext, settings, chainId, through);
      purgedThrough.put(chainId, through);
      removed += deleted;
      LOG.info("Audit trail chain {}: purged records {} to {} (last hash {})", chainId, first.get(0).getSequence(), through,
          last.getHash());
    }
    return new AuditPurgeResult(before, purgedThrough, removed);
  }
}
