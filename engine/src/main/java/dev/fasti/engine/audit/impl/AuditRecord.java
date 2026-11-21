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

import java.util.Date;

import dev.fasti.engine.audit.AuditEvent;

/**
 * A record of the audit trail, as written or read. Text fields are cut to the size of their
 * column and empty text is kept as {@code null} before the record is hashed, so that what the
 * database returns is exactly what was hashed (Oracle stores empty text as NULL).
 */
public class AuditRecord implements AuditEvent {

  static final int ID_LENGTH = 64;
  static final int NAME_LENGTH = 255;
  static final int TYPE_LENGTH = 32;
  static final int ACTION_LENGTH = 64;

  protected String id;
  protected String chainId;
  protected long sequence;
  protected long time;
  protected String type;
  protected String action;
  protected String actor;
  protected String engineName;
  protected String tenantId;
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected String entityType;
  protected String entityId;
  protected String entityName;
  protected String details;
  protected String previousHash;
  protected String hash;

  /** Of a purge record that is not written yet: what it removes, and its other details. Not stored as such. */
  protected transient long purgedThroughSequence;
  protected transient String purgedThroughHash;
  protected transient AuditJson purgeDetails;

  public AuditRecord() {
  }

  public AuditRecord(String type, String action) {
    setType(type);
    setAction(action);
  }

  static String fit(String value, int length) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    if (value.length() <= length) {
      return value;
    }
    // do not cut a surrogate pair in half
    int end = length;
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(0, end);
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = fit(id, ID_LENGTH);
  }

  @Override
  public String getChainId() {
    return chainId;
  }

  public void setChainId(String chainId) {
    this.chainId = fit(chainId, NAME_LENGTH);
  }

  @Override
  public long getSequence() {
    return sequence;
  }

  public void setSequence(long sequence) {
    this.sequence = sequence;
  }

  /** Milliseconds since 1970-01-01T00:00Z. */
  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  @Override
  public Date getTimestamp() {
    return new Date(time);
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = fit(type, TYPE_LENGTH);
  }

  @Override
  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = fit(action, ACTION_LENGTH);
  }

  @Override
  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = fit(actor, NAME_LENGTH);
  }

  @Override
  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = fit(engineName, NAME_LENGTH);
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = fit(tenantId, ID_LENGTH);
  }

  @Override
  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = fit(processDefinitionKey, NAME_LENGTH);
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = fit(processDefinitionId, ID_LENGTH);
  }

  @Override
  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = fit(processInstanceId, ID_LENGTH);
  }

  @Override
  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = fit(businessKey, NAME_LENGTH);
  }

  @Override
  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = fit(entityType, ACTION_LENGTH);
  }

  @Override
  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = fit(entityId, ID_LENGTH);
  }

  @Override
  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = fit(entityName, NAME_LENGTH);
  }

  @Override
  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details == null || details.isEmpty() ? null : details;
  }

  @Override
  public String getPreviousHash() {
    return previousHash;
  }

  public void setPreviousHash(String previousHash) {
    this.previousHash = previousHash;
  }

  @Override
  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  /** Makes this a purge record of the records up to the position; its details are completed when it is appended. */
  public void setPurge(long purgedThroughSequence, String purgedThroughHash, AuditJson details) {
    this.purgedThroughSequence = purgedThroughSequence;
    this.purgedThroughHash = purgedThroughHash;
    this.purgeDetails = details;
  }

  public long getPurgedThroughSequence() {
    return purgedThroughSequence;
  }

  public String getPurgedThroughHash() {
    return purgedThroughHash;
  }

  public AuditJson getPurgeDetails() {
    return purgeDetails;
  }

  @Override
  public String toString() {
    return "AuditRecord[" + chainId + "#" + sequence + " " + type + "/" + action + " by " + actor + "]";
  }
}
