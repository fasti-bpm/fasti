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

import java.util.ArrayList;
import java.util.List;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditStatus;

/** Whether and how the audit trail runs on the engine ({@code GET /audit}). Holds no secret. */
public class AuditStatusDto {

  protected boolean enabled;
  protected String nodeId;
  protected int chains;
  protected List<String> types;
  protected String variableValues;
  protected List<String> maskedVariables;
  protected boolean signed;
  protected String keyId;
  protected List<String> retiredKeyIds;
  protected int checkpointInterval;
  protected int retentionDays;
  protected String historyLevel;

  public static AuditStatusDto fromService(AuditService service) {
    AuditStatusDto dto = new AuditStatusDto();
    dto.enabled = service.isEnabled();
    AuditStatus status = service.getStatus();
    // whether it runs is no secret (the web UI asks to decide what to show); the settings, with the
    // host name in the node id, are for those who may read the trail
    if (status != null && service.isReadAuthorized()) {
      dto.nodeId = status.getNodeId();
      dto.chains = status.getChains();
      dto.types = new ArrayList<>(status.getTypes());
      dto.variableValues = status.getVariableValues();
      dto.maskedVariables = new ArrayList<>(status.getMaskedVariables());
      dto.signed = status.isSigned();
      dto.keyId = status.getKeyId();
      dto.retiredKeyIds = new ArrayList<>(status.getRetiredKeyIds());
      dto.checkpointInterval = status.getCheckpointInterval();
      dto.retentionDays = status.getRetentionDays();
      dto.historyLevel = status.getHistoryLevel();
    }
    return dto;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public int getChains() {
    return chains;
  }

  public void setChains(int chains) {
    this.chains = chains;
  }

  public List<String> getTypes() {
    return types;
  }

  public void setTypes(List<String> types) {
    this.types = types;
  }

  public String getVariableValues() {
    return variableValues;
  }

  public void setVariableValues(String variableValues) {
    this.variableValues = variableValues;
  }

  public List<String> getMaskedVariables() {
    return maskedVariables;
  }

  public void setMaskedVariables(List<String> maskedVariables) {
    this.maskedVariables = maskedVariables;
  }

  public boolean isSigned() {
    return signed;
  }

  public void setSigned(boolean signed) {
    this.signed = signed;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public List<String> getRetiredKeyIds() {
    return retiredKeyIds;
  }

  public void setRetiredKeyIds(List<String> retiredKeyIds) {
    this.retiredKeyIds = retiredKeyIds;
  }

  public int getCheckpointInterval() {
    return checkpointInterval;
  }

  public void setCheckpointInterval(int checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  public String getHistoryLevel() {
    return historyLevel;
  }

  public void setHistoryLevel(String historyLevel) {
    this.historyLevel = historyLevel;
  }
}
