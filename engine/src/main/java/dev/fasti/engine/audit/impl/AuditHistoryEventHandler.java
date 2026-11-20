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

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.fasti.engine.audit.AuditService;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.history.event.HistoricActivityInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricDecisionEvaluationEvent;
import org.camunda.bpm.engine.impl.history.event.HistoricDecisionInstanceEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricIdentityLinkLogEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricIncidentEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricProcessInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricTaskInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoricVariableUpdateEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.UserOperationLogEntryEventEntity;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricProcessInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;

/**
 * Turns the history events the engine produces into audit records of the current command, next
 * to the handler that writes the history (which is left as it is). Which events the engine
 * produces depends on its history level; user operations need level {@code full}.
 */
public class AuditHistoryEventHandler implements HistoryEventHandler {

  protected static final int MAX_VALUE_LENGTH = 4000;

  protected final AuditSettings settings;

  public AuditHistoryEventHandler(AuditSettings settings) {
    this.settings = settings;
  }

  @Override
  public void handleEvent(HistoryEvent historyEvent) {
    CommandContext commandContext = Context.getCommandContext();
    if (commandContext == null || historyEvent == null) {
      return;
    }
    AuditRecord record = toRecord(commandContext, historyEvent);
    if (record != null) {
      session(commandContext).add(record, !(historyEvent instanceof UserOperationLogEntryEventEntity));
    }
  }

  @Override
  public void handleEvents(List<HistoryEvent> historyEvents) {
    if (historyEvents != null) {
      for (HistoryEvent historyEvent : historyEvents) {
        handleEvent(historyEvent);
      }
    }
  }

  /** The audit session of the command, opened after the sessions of the engine. */
  public static AuditSession session(CommandContext commandContext) {
    commandContext.getDbEntityManager();
    commandContext.getDbSqlSession();
    return commandContext.getSession(AuditSession.class);
  }

  protected AuditRecord toRecord(CommandContext commandContext, HistoryEvent event) {
    if (event instanceof UserOperationLogEntryEventEntity) {
      return settings.isRecorded(AuditService.TYPE_OPERATION) ? operation(commandContext, (UserOperationLogEntryEventEntity) event) : null;
    }
    if (event instanceof HistoricProcessInstanceEventEntity) {
      HistoricProcessInstanceEventEntity process = (HistoricProcessInstanceEventEntity) event;
      if (process.getProcessInstanceId() != null && process.getBusinessKey() != null) {
        BusinessKeys.of(commandContext).put(process.getProcessInstanceId(), process.getBusinessKey());
      }
      return settings.isRecorded(AuditService.TYPE_PROCESS) ? process(process) : null;
    }
    if (event instanceof HistoricTaskInstanceEventEntity) {
      return settings.isRecorded(AuditService.TYPE_TASK) ? task(commandContext, (HistoricTaskInstanceEventEntity) event) : null;
    }
    if (event instanceof HistoricIdentityLinkLogEventEntity) {
      return settings.isRecorded(AuditService.TYPE_TASK) ? identityLink(commandContext, (HistoricIdentityLinkLogEventEntity) event) : null;
    }
    if (event instanceof HistoricVariableUpdateEventEntity) {
      return settings.isRecorded(AuditService.TYPE_VARIABLE) ? variable(commandContext, (HistoricVariableUpdateEventEntity) event) : null;
    }
    if (event instanceof HistoricIncidentEventEntity) {
      return settings.isRecorded(AuditService.TYPE_INCIDENT) ? incident(commandContext, (HistoricIncidentEventEntity) event) : null;
    }
    if (event instanceof HistoricDecisionEvaluationEvent) {
      return settings.isRecorded(AuditService.TYPE_DECISION) ? decision(commandContext, (HistoricDecisionEvaluationEvent) event) : null;
    }
    if (event instanceof HistoricActivityInstanceEventEntity) {
      return settings.isRecorded(AuditService.TYPE_ACTIVITY) ? activity(commandContext, (HistoricActivityInstanceEventEntity) event) : null;
    }
    return null;
  }

  protected AuditRecord base(String type, String action, HistoryEvent event) {
    AuditRecord record = new AuditRecord(type, action);
    record.setProcessDefinitionKey(event.getProcessDefinitionKey());
    record.setProcessDefinitionId(event.getProcessDefinitionId());
    record.setProcessInstanceId(event.getProcessInstanceId());
    return record;
  }

  protected AuditRecord process(HistoricProcessInstanceEventEntity event) {
    AuditRecord record = base(AuditService.TYPE_PROCESS, event.getEventType(), event);
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(event.getBusinessKey());
    record.setEntityType("process-instance");
    record.setEntityId(event.getProcessInstanceId());
    record.setEntityName(event.getProcessDefinitionName());
    record.setDetails(AuditJson.object()
        .put("state", event.getState())
        .put("startUserId", event.getStartUserId())
        .put("startActivityId", event.getStartActivityId())
        .put("endActivityId", event.getEndActivityId())
        .put("deleteReason", event.getDeleteReason())
        .put("superProcessInstanceId", event.getSuperProcessInstanceId())
        .put("restartedProcessInstanceId", event.getRestartedProcessInstanceId())
        .put("processDefinitionVersion", event.getProcessDefinitionVersion())
        .toJson());
    return record;
  }

  protected AuditRecord task(CommandContext commandContext, HistoricTaskInstanceEventEntity event) {
    String action = event.getEventType();
    AuditJson details = AuditJson.object()
        .put("assignee", event.getAssignee())
        .put("owner", event.getOwner())
        .put("taskDefinitionKey", event.getTaskDefinitionKey())
        .put("deleteReason", event.getDeleteReason())
        .put("parentTaskId", event.getParentTaskId())
        .put("taskState", event.getTaskState());
    AuditSession session = session(commandContext);
    if ("update".equals(action)) {
      String previous = session.assignees.containsKey(event.getTaskId()) ? session.assignees.get(event.getTaskId())
          : AuditStore.historicAssignee(commandContext, session.settings, event.getTaskId());
      if (!Objects.equals(previous, event.getAssignee())) {
        action = "assign";
        details.put("previousAssignee", previous);
      }
    } else if ("complete".equals(action) && event.getDeleteReason() != null
        && !TaskEntity.DELETE_REASON_COMPLETED.equals(event.getDeleteReason())) {
      // a task that ends with its instance (deleted, cancelled, ended by an event) is not completed
      action = "delete";
    }
    session.assignees.put(event.getTaskId(), event.getAssignee());
    AuditRecord record = base(AuditService.TYPE_TASK, action, event);
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(businessKey(commandContext, event.getProcessInstanceId()));
    record.setEntityType("task");
    record.setEntityId(event.getTaskId());
    record.setEntityName(event.getName());
    record.setDetails(details.toJson());
    return record;
  }

  protected AuditRecord identityLink(CommandContext commandContext, HistoricIdentityLinkLogEventEntity event) {
    AuditRecord record = base(AuditService.TYPE_TASK, "identity-link-" + event.getOperationType(), event);
    record.setTenantId(event.getTenantId());
    if (record.getProcessInstanceId() == null) {
      TaskEntity task = cached(commandContext, TaskEntity.class, event.getTaskId());
      if (task != null) {
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setProcessDefinitionId(task.getProcessDefinitionId());
      }
    }
    record.setBusinessKey(businessKey(commandContext, record.getProcessInstanceId()));
    record.setEntityType("task");
    record.setEntityId(event.getTaskId());
    record.setEntityName(event.getType());
    record.setDetails(AuditJson.object()
        .put("type", event.getType())
        .put("userId", event.getUserId())
        .put("groupId", event.getGroupId())
        .put("assignerId", event.getAssignerId())
        .toJson());
    return record;
  }

  protected AuditRecord variable(CommandContext commandContext, HistoricVariableUpdateEventEntity event) {
    String action = event.getEventType();
    if ("update-detail".equals(action)) {
      // the detail of an update that is recorded as "update" already
      return null;
    }
    AuditRecord record = base(AuditService.TYPE_VARIABLE, action, event);
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(businessKey(commandContext, event.getProcessInstanceId()));
    record.setEntityType("variable");
    record.setEntityId(event.getVariableInstanceId());
    record.setEntityName(event.getVariableName());
    AuditJson details = AuditJson.object()
        .put("type", event.getSerializerName())
        .put("taskId", event.getTaskId())
        .put("executionId", event.getExecutionId())
        .put("revision", event.getRevision());
    if (!"delete".equals(action)) {
      if (settings.isMasked(event.getVariableName())) {
        details.put("masked", Boolean.TRUE);
      } else if (AuditSettings.VALUES_HASH.equals(settings.getVariableValues())) {
        details.put("valueHash", valueHash(event));
      } else if (AuditSettings.VALUES_VALUE.equals(settings.getVariableValues())) {
        value(details, event);
      }
    }
    record.setDetails(details.toJson());
    return record;
  }

  /** SHA-256 of the stored value (HMAC-SHA256 with the key, if one is configured, so that short values cannot be guessed). */
  protected String valueHash(HistoricVariableUpdateEventEntity event) {
    StringBuilder value = new StringBuilder();
    value.append(event.getSerializerName()).append('\n')
        .append(event.getTextValue()).append('\n')
        .append(event.getTextValue2()).append('\n')
        .append(event.getLongValue()).append('\n')
        .append(event.getDoubleValue()).append('\n');
    if (event.getByteValue() != null) {
      value.append(Base64.getEncoder().encodeToString(event.getByteValue()));
    }
    if (settings.isSigned()) {
      return "hmac-sha256:" + AuditChain.hmacHex(settings.getHmacKey(), value.toString());
    }
    return "sha256:" + AuditChain.sha256Hex(value.toString());
  }

  protected void value(AuditJson details, HistoricVariableUpdateEventEntity event) {
    if (event.getByteValue() != null) {
      // binary and serialized values are not written in clear
      details.put("valueHash", valueHash(event));
      details.put("size", event.getByteValue().length);
    } else if (event.getLongValue() != null) {
      details.put("value", event.getLongValue());
    } else if (event.getDoubleValue() != null) {
      details.put("value", event.getDoubleValue());
    } else if (event.getTextValue() != null) {
      String text = event.getTextValue();
      if (text.length() > MAX_VALUE_LENGTH) {
        details.put("value", AuditRecord.fit(text, MAX_VALUE_LENGTH));
        details.put("truncated", Boolean.TRUE);
        details.put("valueHash", valueHash(event));
      } else {
        details.put("value", text);
      }
    }
  }

  protected AuditRecord incident(CommandContext commandContext, HistoricIncidentEventEntity event) {
    AuditRecord record = base(AuditService.TYPE_INCIDENT, event.getEventType(), event);
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(businessKey(commandContext, event.getProcessInstanceId()));
    record.setEntityType("incident");
    record.setEntityId(event.getId());
    record.setEntityName(event.getIncidentType());
    record.setDetails(AuditJson.object()
        .put("message", AuditRecord.fit(event.getIncidentMessage(), MAX_VALUE_LENGTH))
        .put("activityId", event.getActivityId())
        .put("failedActivityId", event.getFailedActivityId())
        .put("configuration", event.getConfiguration())
        .put("causeIncidentId", event.getCauseIncidentId())
        .put("state", event.isResolved() ? "resolved" : event.isDeleted() ? "deleted" : "open")
        .toJson());
    return record;
  }

  protected AuditRecord decision(CommandContext commandContext, HistoricDecisionEvaluationEvent event) {
    HistoricDecisionInstanceEntity root = event.getRootHistoricDecisionInstance();
    if (root == null) {
      return null;
    }
    AuditRecord record = new AuditRecord(AuditService.TYPE_DECISION, "evaluate");
    record.setProcessDefinitionKey(root.getProcessDefinitionKey());
    record.setProcessDefinitionId(root.getProcessDefinitionId());
    record.setProcessInstanceId(root.getProcessInstanceId());
    record.setTenantId(root.getTenantId());
    record.setBusinessKey(businessKey(commandContext, root.getProcessInstanceId()));
    record.setEntityType("decision");
    record.setEntityId(root.getId());
    record.setEntityName(root.getDecisionDefinitionKey());
    record.setDetails(AuditJson.object()
        .put("decisionDefinitionId", root.getDecisionDefinitionId())
        .put("activityId", root.getActivityId())
        .put("requiredDecisions", event.getRequiredHistoricDecisionInstances() == null ? null
            : event.getRequiredHistoricDecisionInstances().size())
        .toJson());
    return record;
  }

  protected AuditRecord activity(CommandContext commandContext, HistoricActivityInstanceEventEntity event) {
    AuditRecord record = base(AuditService.TYPE_ACTIVITY, event.getEventType(), event);
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(businessKey(commandContext, event.getProcessInstanceId()));
    record.setEntityType("activity-instance");
    record.setEntityId(event.getActivityInstanceId());
    record.setEntityName(event.getActivityId());
    record.setDetails(AuditJson.object()
        .put("activityType", event.getActivityType())
        .put("activityName", event.getActivityName())
        .put("taskAssignee", event.getTaskAssignee())
        .toJson());
    return record;
  }

  protected AuditRecord operation(CommandContext commandContext, UserOperationLogEntryEventEntity event) {
    AuditRecord record = base(AuditService.TYPE_OPERATION, event.getOperationType(), event);
    record.setActor(event.getUserId());
    record.setTenantId(event.getTenantId());
    record.setBusinessKey(businessKey(commandContext, event.getProcessInstanceId()));
    record.setEntityType(event.getEntityType());
    record.setEntityId(firstNonNull(event.getTaskId(), event.getJobId(), event.getDeploymentId(), event.getBatchId(),
        event.getExternalTaskId(), event.getProcessInstanceId(), event.getJobDefinitionId()));
    record.setEntityName(event.getProperty());
    record.setDetails(AuditJson.object()
        .put("operationId", event.getOperationId())
        .put("category", event.getCategory())
        .put("property", event.getProperty())
        .put("orgValue", AuditRecord.fit(event.getOrgValue(), MAX_VALUE_LENGTH))
        .put("newValue", AuditRecord.fit(event.getNewValue(), MAX_VALUE_LENGTH))
        .put("annotation", AuditRecord.fit(event.getAnnotation(), MAX_VALUE_LENGTH))
        .toJson());
    return record;
  }

  static String firstNonNull(String... values) {
    for (String value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  static <T extends org.camunda.bpm.engine.impl.db.DbEntity> T cached(CommandContext commandContext, Class<T> type, String id) {
    if (id == null) {
      return null;
    }
    return commandContext.getDbEntityManager().getCachedEntity(type, id);
  }

  /**
   * The business key of a process instance, from what the command has loaded already (or, once
   * per instance and command, from the database).
   */
  protected String businessKey(CommandContext commandContext, String processInstanceId) {
    if (processInstanceId == null) {
      return null;
    }
    Map<String, String> known = BusinessKeys.of(commandContext);
    if (known.containsKey(processInstanceId)) {
      return known.get(processInstanceId);
    }
    String businessKey = null;
    DbEntityManager entityManager = commandContext.getDbEntityManager();
    ExecutionEntity execution = entityManager.getCachedEntity(ExecutionEntity.class, processInstanceId);
    if (execution != null) {
      businessKey = execution.getBusinessKey();
    } else {
      HistoricProcessInstanceEntity historic = entityManager.getCachedEntity(HistoricProcessInstanceEntity.class, processInstanceId);
      if (historic != null) {
        businessKey = historic.getBusinessKey();
      } else {
        execution = commandContext.getExecutionManager().findExecutionById(processInstanceId);
        if (execution != null) {
          businessKey = execution.getBusinessKey();
        }
      }
    }
    known.put(processInstanceId, businessKey);
    return businessKey;
  }

  /** Business keys looked up in the current command context, also those of process start events. */
  static final class BusinessKeys {
    private BusinessKeys() {
    }

    static Map<String, String> of(CommandContext commandContext) {
      AuditSession session = session(commandContext);
      if (session.businessKeys == null) {
        session.businessKeys = new HashMap<>();
      }
      return session.businessKeys;
    }
  }
}
