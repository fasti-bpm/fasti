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
package org.camunda.bpm.engine.rest.dto.audit;

import java.util.Date;
import dev.fasti.engine.audit.AuditEvent;

/** A record of the audit trail. */
public class AuditEventDto {

  protected String id;
  protected String chainId;
  protected long sequence;
  protected Date timestamp;
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

  public static AuditEventDto fromEvent(AuditEvent event) {
    AuditEventDto dto = new AuditEventDto();
    dto.id = event.getId();
    dto.chainId = event.getChainId();
    dto.sequence = event.getSequence();
    dto.timestamp = event.getTimestamp();
    dto.time = event.getTimestamp().getTime();
    dto.type = event.getType();
    dto.action = event.getAction();
    dto.actor = event.getActor();
    dto.engineName = event.getEngineName();
    dto.tenantId = event.getTenantId();
    dto.processDefinitionKey = event.getProcessDefinitionKey();
    dto.processDefinitionId = event.getProcessDefinitionId();
    dto.processInstanceId = event.getProcessInstanceId();
    dto.businessKey = event.getBusinessKey();
    dto.entityType = event.getEntityType();
    dto.entityId = event.getEntityId();
    dto.entityName = event.getEntityName();
    dto.details = event.getDetails();
    dto.previousHash = event.getPreviousHash();
    dto.hash = event.getHash();
    return dto;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getChainId() {
    return chainId;
  }

  public void setChainId(String chainId) {
    this.chainId = chainId;
  }

  public long getSequence() {
    return sequence;
  }

  public void setSequence(long sequence) {
    this.sequence = sequence;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  /** The time in milliseconds since 1970-01-01T00:00Z, as hashed. */
  public long getTime() {
    return time;
  }

  public void setTime(long time) {
    this.time = time;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  /** The authenticated user, or {@code null} for the engine itself. */
  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  /** The details as JSON text, exactly as hashed. */
  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public String getPreviousHash() {
    return previousHash;
  }

  public void setPreviousHash(String previousHash) {
    this.previousHash = previousHash;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }
}
