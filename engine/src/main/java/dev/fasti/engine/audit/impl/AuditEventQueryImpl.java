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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditEventQuery;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;

/** Filters of a query of the audit trail; runs each request as a command. */
public class AuditEventQueryImpl implements AuditEventQuery {

  protected final transient CommandExecutor commandExecutor;
  protected final transient AuditSettings settings;

  protected String processInstanceId;
  protected String businessKey;
  protected String actor;
  protected boolean systemActor;
  protected List<String> types;
  protected String action;
  protected String tenantId;
  protected String processDefinitionKey;
  protected String entityId;
  protected String chainId;
  protected Date from;
  protected Date to;
  protected boolean oldestFirst;

  public AuditEventQueryImpl(CommandExecutor commandExecutor, AuditSettings settings) {
    this.commandExecutor = commandExecutor;
    this.settings = settings;
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  @Override
  public AuditEventQuery processInstanceId(String processInstanceId) {
    this.processInstanceId = blankToNull(processInstanceId);
    return this;
  }

  @Override
  public AuditEventQuery businessKey(String businessKey) {
    this.businessKey = blankToNull(businessKey);
    return this;
  }

  @Override
  public AuditEventQuery actor(String actor) {
    this.actor = blankToNull(actor);
    return this;
  }

  @Override
  public AuditEventQuery systemActor() {
    this.systemActor = true;
    return this;
  }

  @Override
  public AuditEventQuery types(String... types) {
    List<String> values = new ArrayList<>();
    if (types != null) {
      for (String type : Arrays.asList(types)) {
        if (blankToNull(type) != null) {
          values.add(type.trim());
        }
      }
    }
    this.types = values.isEmpty() ? null : values;
    return this;
  }

  @Override
  public AuditEventQuery action(String action) {
    this.action = blankToNull(action);
    return this;
  }

  @Override
  public AuditEventQuery tenantId(String tenantId) {
    this.tenantId = blankToNull(tenantId);
    return this;
  }

  @Override
  public AuditEventQuery processDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = blankToNull(processDefinitionKey);
    return this;
  }

  @Override
  public AuditEventQuery entityId(String entityId) {
    this.entityId = blankToNull(entityId);
    return this;
  }

  @Override
  public AuditEventQuery chainId(String chainId) {
    this.chainId = blankToNull(chainId);
    return this;
  }

  @Override
  public AuditEventQuery from(Date from) {
    this.from = from;
    return this;
  }

  @Override
  public AuditEventQuery to(Date to) {
    this.to = to;
    return this;
  }

  @Override
  public AuditEventQuery oldestFirst() {
    this.oldestFirst = true;
    return this;
  }

  public boolean isOldestFirst() {
    return oldestFirst;
  }

  @Override
  public long count() {
    return count(0);
  }

  @Override
  public long count(long limit) {
    return commandExecutor.execute(commandContext -> {
      AuditAuthorization.checkRead(commandContext);
      return AuditStore.count(commandContext, settings, this, AuditAuthorization.readableTenants(commandContext), limit);
    });
  }

  @Override
  public List<AuditEvent> list() {
    return listPage(0, Integer.MAX_VALUE);
  }

  @Override
  public List<AuditEvent> listPage(int firstResult, int maxResults) {
    int first = Math.max(firstResult, 0);
    int max = maxResults < 0 ? Integer.MAX_VALUE : maxResults;
    return commandExecutor.execute(commandContext -> {
      AuditAuthorization.checkRead(commandContext);
      return new ArrayList<AuditEvent>(AuditStore.select(commandContext, settings, this,
          AuditAuthorization.readableTenants(commandContext), first, max));
    });
  }

  @Override
  public long forEach(int maxResults, Consumer<? super AuditEvent> consumer) {
    return commandExecutor.execute(commandContext -> {
      AuditAuthorization.checkRead(commandContext);
      return AuditStore.forEach(commandContext, settings, this, AuditAuthorization.readableTenants(commandContext),
          maxResults, consumer::accept);
    });
  }

  /** A readable description of the filters, for the export record. */
  public String describe() {
    List<String> parts = new ArrayList<>();
    describe(parts, "processInstanceId", processInstanceId);
    describe(parts, "businessKey", businessKey);
    describe(parts, "actor", actor);
    if (systemActor) {
      parts.add("actor=system");
    }
    if (types != null) {
      parts.add("types=" + String.join("|", types));
    }
    describe(parts, "action", action);
    describe(parts, "tenantId", tenantId);
    describe(parts, "processDefinitionKey", processDefinitionKey);
    describe(parts, "entityId", entityId);
    describe(parts, "chainId", chainId);
    if (from != null) {
      parts.add("from=" + from.toInstant());
    }
    if (to != null) {
      parts.add("to=" + to.toInstant());
    }
    return parts.isEmpty() ? "all" : String.join(", ", parts);
  }

  static void describe(List<String> parts, String name, String value) {
    if (value != null) {
      parts.add(name + "=" + value);
    }
  }
}
