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
package dev.fasti.spring.boot.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstanceQuery;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.camunda.bpm.engine.runtime.TransitionInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;

import dev.fasti.spring.boot.mcp.tools.ToolSupport.Effect;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/** Process definitions, running and finished process instances, starting an instance. */
public class ProcessTools {

  protected static final int MAX_ACTIVITY_INSTANCES = 200;
  protected static final int MAX_INCIDENTS_PER_INSTANCE = 20;

  protected final ToolSupport support;

  public ProcessTools(ToolSupport support) {
    this.support = support;
  }

  public List<SyncToolSpecification> readTools() {
    return List.of(listProcessDefinitions(), getProcessDefinition(), listProcessInstances(), getProcessInstance(),
        listHistoricProcessInstances());
  }

  public List<SyncToolSpecification> writeTools() {
    return List.of(startProcessInstance());
  }

  protected RepositoryService repository() {
    return support.engine().getRepositoryService();
  }

  protected RuntimeService runtime() {
    return support.engine().getRuntimeService();
  }

  protected HistoryService history() {
    return support.engine().getHistoryService();
  }

  // --- process definitions ----------------------------------------------------------------------

  protected SyncToolSpecification listProcessDefinitions() {
    return support.tool("list_process_definitions", "List process definitions",
        "Lists the deployed BPMN process definitions the user may read, by default only the latest "
            + "version of each. Returns id, key, name, version and state; use the id or key with "
            + "get_process_definition, list_process_instances or start_process_instance.",
        Schemas.object()
            .string("key", "Exact process definition key.")
            .string("keyLike", "Part of the key.")
            .string("nameLike", "Part of the name.")
            .bool("latestVersionOnly", "Only the latest version of each key (default true).")
            .string("tenantId", "Only definitions of this tenant.")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          ProcessDefinitionQuery query = repository().createProcessDefinitionQuery();
          String key = arguments.string("key");
          if (key != null) {
            query.processDefinitionKey(key);
          }
          String keyLike = arguments.string("keyLike");
          if (keyLike != null) {
            query.processDefinitionKeyLike(ToolSupport.like(keyLike));
          }
          String nameLike = arguments.string("nameLike");
          if (nameLike != null) {
            query.processDefinitionNameLike(ToolSupport.like(nameLike));
          }
          if (arguments.bool("latestVersionOnly", true)) {
            query.latestVersion();
          }
          String tenantId = arguments.string("tenantId");
          if (tenantId != null) {
            query.tenantIdIn(tenantId);
          }
          query.orderByProcessDefinitionKey().asc().orderByProcessDefinitionVersion().desc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (ProcessDefinition definition : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            items.add(definition(definition));
          }
          return Json.page(items, first, query.count());
        });
  }

  protected SyncToolSpecification getProcessDefinition() {
    return support.tool("get_process_definition", "Get a process definition",
        "Returns one process definition by id, or the latest (or given) version of a key, with the "
            + "number of its running instances the user may see. With includeXml=true also the BPMN "
            + "2.0 XML of the model (cut at the configured length), which shows its activities, user "
            + "tasks, gateways and the variables they use.",
        Schemas.object()
            .string("id", "Process definition id.")
            .string("key", "Process definition key, used when no id is given.")
            .integer("version", "Version of the key; default the latest.", 1, Integer.MAX_VALUE)
            .string("tenantId", "Tenant of the key.")
            .bool("includeXml", "Include the BPMN XML (default false).")
            .build(),
        Effect.READ, (arguments, identity) -> {
          ProcessDefinition definition = findDefinition(arguments);
          Json result = definition(definition);
          result.with("runningInstances",
              runtime().createProcessInstanceQuery().processDefinitionId(definition.getId()).count());
          if (arguments.bool("includeXml", false)) {
            result.with("bpmn", support.model(() -> repository().getProcessModel(definition.getId())));
          }
          return result;
        });
  }

  protected ProcessDefinition findDefinition(ToolArguments arguments) {
    String id = arguments.string("id");
    if (id != null) {
      // checks READ on the definition and throws if it is missing
      return repository().getProcessDefinition(id);
    }
    String key = arguments.string("key");
    if (key == null) {
      throw new ToolException("Give the id or the key of the process definition");
    }
    ProcessDefinitionQuery query = repository().createProcessDefinitionQuery().processDefinitionKey(key);
    Integer version = arguments.integer("version", null, 1, Integer.MAX_VALUE);
    if (version != null) {
      query.processDefinitionVersion(version);
    } else {
      query.latestVersion();
    }
    String tenantId = arguments.string("tenantId");
    if (tenantId != null) {
      query.tenantIdIn(tenantId);
    }
    List<ProcessDefinition> definitions = query.listPage(0, 2);
    if (definitions.isEmpty()) {
      throw ToolException.notFound("Process definition with key", key);
    }
    if (definitions.size() > 1) {
      throw new ToolException("The key '" + key + "' exists for several tenants; give the tenantId or the id");
    }
    return definitions.get(0);
  }

  protected Json definition(ProcessDefinition definition) {
    return Json.obj()
        .with("id", definition.getId())
        .with("key", definition.getKey())
        .with("name", definition.getName())
        .with("version", definition.getVersion())
        .with("versionTag", definition.getVersionTag())
        .with("description", definition.getDescription())
        .with("deploymentId", definition.getDeploymentId())
        .with("resourceName", definition.getResourceName())
        .with("tenantId", definition.getTenantId())
        .with("suspended", definition.isSuspended())
        .with("hasStartForm", definition.hasStartFormKey())
        .with("historyTimeToLive", definition.getHistoryTimeToLive());
  }

  // --- process instances ------------------------------------------------------------------------

  protected SyncToolSpecification listProcessInstances() {
    return support.tool("list_process_instances", "List running process instances",
        "Lists running (not yet ended) process instances the user may read, filtered by process "
            + "definition, business key, state or incidents. For ended instances use "
            + "list_historic_process_instances; for the details of one use get_process_instance.",
        Schemas.object()
            .string("processDefinitionKey", "Key of the process definition.")
            .string("processDefinitionId", "Id of the process definition.")
            .string("businessKey", "Exact business key.")
            .string("businessKeyLike", "Part of the business key.")
            .enumeration("state", "active, suspended or any (default).", "any", "active", "suspended")
            .bool("withIncidentsOnly", "Only instances with an incident (default false).")
            .string("superProcessInstanceId", "Only sub-process instances of this instance.")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          ProcessInstanceQuery query = runtime().createProcessInstanceQuery();
          String value = arguments.string("processDefinitionKey");
          if (value != null) {
            query.processDefinitionKey(value);
          }
          value = arguments.string("processDefinitionId");
          if (value != null) {
            query.processDefinitionId(value);
          }
          value = arguments.string("businessKey");
          if (value != null) {
            query.processInstanceBusinessKey(value);
          }
          value = arguments.string("businessKeyLike");
          if (value != null) {
            query.processInstanceBusinessKeyLike(ToolSupport.like(value));
          }
          String state = arguments.string("state");
          if ("active".equals(state)) {
            query.active();
          } else if ("suspended".equals(state)) {
            query.suspended();
          }
          if (arguments.bool("withIncidentsOnly", false)) {
            query.withIncident();
          }
          value = arguments.string("superProcessInstanceId");
          if (value != null) {
            query.superProcessInstanceId(value);
          }
          query.orderByProcessInstanceId().asc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (ProcessInstance instance : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            items.add(instance(instance));
          }
          return Json.page(items, first, query.count());
        });
  }

  protected Json instance(ProcessInstance instance) {
    return Json.obj()
        .with("id", instance.getId())
        .with("processDefinitionId", instance.getProcessDefinitionId())
        .with("businessKey", instance.getBusinessKey())
        .with("suspended", instance.isSuspended())
        .with("ended", instance.isEnded())
        .with("rootProcessInstanceId", instance.getRootProcessInstanceId())
        .with("tenantId", instance.getTenantId());
  }

  protected SyncToolSpecification getProcessInstance() {
    return support.tool("get_process_instance", "Get a process instance",
        "Returns one process instance, running or ended: for a running one its active activities "
            + "(activity instance tree, flattened), open incidents and variables; for an ended one its "
            + "historic data and variables. Variables whose names look secret (password, secret, "
            + "token, ...) are masked; long values are cut. With includeActivityHistory=true also the "
            + "activities it passed through, in order.",
        Schemas.object()
            .string("id", "Process instance id.")
            .bool("includeVariables", "Include the variables (default true).")
            .bool("includeActivityHistory", "Include the executed activities (default false).")
            .required("id")
            .build(),
        Effect.READ, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          boolean includeVariables = arguments.bool("includeVariables", true);
          Json result;
          ProcessInstance instance = runtime().createProcessInstanceQuery().processInstanceId(id).singleResult();
          if (instance != null) {
            result = instance(instance).with("state", instance.isSuspended() ? "SUSPENDED" : "ACTIVE");
            result.with("activities", activities(runtime().getActivityInstance(id)));
            result.with("incidents", incidents(id));
            if (includeVariables) {
              addRuntimeVariables(result, id);
            }
          } else {
            HistoricProcessInstance historic = history().createHistoricProcessInstanceQuery().processInstanceId(id)
                .singleResult();
            if (historic == null) {
              throw ToolException.notFound("Process instance", id);
            }
            result = historicInstance(historic);
            if (includeVariables) {
              addHistoricVariables(result, id);
            }
          }
          if (arguments.bool("includeActivityHistory", false)) {
            result.with("activityHistory", activityHistory(id));
          }
          return result;
        });
  }

  protected List<Map<String, Object>> activities(ActivityInstance root) {
    List<Map<String, Object>> activities = new ArrayList<>();
    if (root != null) {
      collect(root, activities);
    }
    return activities;
  }

  protected void collect(ActivityInstance activity, List<Map<String, Object>> activities) {
    if (activities.size() >= MAX_ACTIVITY_INSTANCES) {
      return;
    }
    activities.add(Json.obj()
        .with("activityInstanceId", activity.getId())
        .with("activityId", activity.getActivityId())
        .with("activityName", activity.getActivityName())
        .with("activityType", activity.getActivityType())
        .with("parentActivityInstanceId", activity.getParentActivityInstanceId()));
    for (TransitionInstance transition : activity.getChildTransitionInstances()) {
      if (activities.size() >= MAX_ACTIVITY_INSTANCES) {
        return;
      }
      activities.add(Json.obj()
          .with("transitionInstanceId", transition.getId())
          .with("activityId", transition.getActivityId())
          .with("activityName", transition.getActivityName())
          .with("activityType", transition.getActivityType())
          .with("parentActivityInstanceId", transition.getParentActivityInstanceId())
          .with("note", "waiting for asynchronous continuation"));
    }
    for (ActivityInstance child : activity.getChildActivityInstances()) {
      collect(child, activities);
    }
  }

  protected List<Map<String, Object>> incidents(String processInstanceId) {
    List<Map<String, Object>> incidents = new ArrayList<>();
    for (Incident incident : runtime().createIncidentQuery().processInstanceId(processInstanceId)
        .orderByIncidentTimestamp().desc().listPage(0, MAX_INCIDENTS_PER_INSTANCE)) {
      incidents.add(IncidentTools.incident(incident));
    }
    return incidents;
  }

  protected void addRuntimeVariables(Json result, String processInstanceId) {
    VariableInstanceQuery query = runtime().createVariableInstanceQuery().processInstanceIdIn(processInstanceId)
        .disableBinaryFetching().disableCustomObjectDeserialization();
    query.orderByVariableName().asc();
    int max = support.properties().getMaxVariables();
    List<Map<String, Object>> variables = new ArrayList<>();
    for (VariableInstance variable : query.listPage(0, max)) {
      Map<String, Object> described = support.values().variable(variable.getName(), variable.getTypedValue(),
          variable.getErrorMessage());
      if (!processInstanceId.equals(variable.getActivityInstanceId())) {
        described.put("activityInstanceId", variable.getActivityInstanceId());
      }
      variables.add(described);
    }
    result.with("variables", variables);
    if (variables.size() == max && query.count() > max) {
      result.with("variablesTruncated", true);
    }
  }

  protected void addHistoricVariables(Json result, String processInstanceId) {
    HistoricVariableInstanceQuery query = history().createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId).disableBinaryFetching().disableCustomObjectDeserialization();
    query.orderByVariableName().asc();
    int max = support.properties().getMaxVariables();
    List<Map<String, Object>> variables = new ArrayList<>();
    for (HistoricVariableInstance variable : query.listPage(0, max)) {
      variables.add(support.values().variable(variable.getName(), variable.getTypedValue(), variable.getErrorMessage()));
    }
    result.with("variables", variables);
    if (variables.size() == max && query.count() > max) {
      result.with("variablesTruncated", true);
    }
  }

  protected List<Map<String, Object>> activityHistory(String processInstanceId) {
    List<Map<String, Object>> activities = new ArrayList<>();
    for (HistoricActivityInstance activity : history().createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .orderByHistoricActivityInstanceStartTime().asc()
        .orderPartiallyByOccurrence().asc()
        .listPage(0, support.maxResults())) {
      activities.add(Json.obj()
          .with("activityId", activity.getActivityId())
          .with("activityName", activity.getActivityName())
          .with("activityType", activity.getActivityType())
          .with("assignee", activity.getAssignee())
          .with("startTime", activity.getStartTime())
          .with("endTime", activity.getEndTime())
          .with("durationInMillis", activity.getDurationInMillis())
          .with("canceled", activity.isCanceled() ? Boolean.TRUE : null));
    }
    return activities;
  }

  // --- history ----------------------------------------------------------------------------------

  protected SyncToolSpecification listHistoricProcessInstances() {
    return support.tool("list_historic_process_instances", "Query process instance history",
        "Searches the history for process instances, running or ended, newest first: by process "
            + "definition key, business key, state and start or end time (ISO-8601). Returns start "
            + "and end time, duration, state and the user who started each instance.",
        Schemas.object()
            .string("processDefinitionKey", "Key of the process definition.")
            .string("businessKey", "Exact business key.")
            .string("businessKeyLike", "Part of the business key.")
            .enumeration("state", "any (default), running or finished.", "any", "running", "finished")
            .bool("withIncidentsOnly", "Only instances that have had incidents (default false).")
            .string("startedAfter", "ISO-8601 time, e.g. 2026-11-01T00:00:00Z.")
            .string("startedBefore", "ISO-8601 time.")
            .string("finishedAfter", "ISO-8601 time.")
            .string("finishedBefore", "ISO-8601 time.")
            .string("startedBy", "Id of the user who started the instance.")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          HistoricProcessInstanceQuery query = history().createHistoricProcessInstanceQuery();
          String value = arguments.string("processDefinitionKey");
          if (value != null) {
            query.processDefinitionKey(value);
          }
          value = arguments.string("businessKey");
          if (value != null) {
            query.processInstanceBusinessKey(value);
          }
          value = arguments.string("businessKeyLike");
          if (value != null) {
            query.processInstanceBusinessKeyLike(ToolSupport.like(value));
          }
          String state = arguments.string("state");
          if ("running".equals(state)) {
            query.unfinished();
          } else if ("finished".equals(state)) {
            query.finished();
          }
          if (arguments.bool("withIncidentsOnly", false)) {
            query.withIncidents();
          }
          if (arguments.date("startedAfter") != null) {
            query.startedAfter(arguments.date("startedAfter"));
          }
          if (arguments.date("startedBefore") != null) {
            query.startedBefore(arguments.date("startedBefore"));
          }
          if (arguments.date("finishedAfter") != null) {
            query.finishedAfter(arguments.date("finishedAfter"));
          }
          if (arguments.date("finishedBefore") != null) {
            query.finishedBefore(arguments.date("finishedBefore"));
          }
          value = arguments.string("startedBy");
          if (value != null) {
            query.startedBy(value);
          }
          query.orderByProcessInstanceStartTime().desc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (HistoricProcessInstance instance : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            items.add(historicInstance(instance));
          }
          return Json.page(items, first, query.count());
        });
  }

  protected Json historicInstance(HistoricProcessInstance instance) {
    return Json.obj()
        .with("id", instance.getId())
        .with("processDefinitionId", instance.getProcessDefinitionId())
        .with("processDefinitionKey", instance.getProcessDefinitionKey())
        .with("processDefinitionName", instance.getProcessDefinitionName())
        .with("processDefinitionVersion", instance.getProcessDefinitionVersion())
        .with("businessKey", instance.getBusinessKey())
        .with("state", instance.getState())
        .with("startTime", instance.getStartTime())
        .with("endTime", instance.getEndTime())
        .with("durationInMillis", instance.getDurationInMillis())
        .with("startUserId", instance.getStartUserId())
        .with("startActivityId", instance.getStartActivityId())
        .with("endActivityId", instance.getEndActivityId())
        .with("deleteReason", instance.getDeleteReason())
        .with("superProcessInstanceId", instance.getSuperProcessInstanceId())
        .with("tenantId", instance.getTenantId());
  }

  // --- write ------------------------------------------------------------------------------------

  protected SyncToolSpecification startProcessInstance() {
    return support.tool("start_process_instance", "Start a process instance",
        "Starts a new instance of a process definition (by id, or the latest version of a key) as "
            + "the authenticated user, with an optional business key and variables (strings, numbers, "
            + "booleans or null). Needs CREATE on process instances and CREATE_INSTANCE on the "
            + "definition. Returns the new instance id and whether it already ended.",
        Schemas.object()
            .string("processDefinitionId", "Id of the process definition.")
            .string("processDefinitionKey", "Key of the process definition (latest version), used when no id is given.")
            .string("tenantId", "Tenant of the key.")
            .string("businessKey", "Business key of the new instance.")
            .variables("variables", "Process variables, e.g. {\"amount\": 120, \"approved\": false}.",
                support.properties().getMaxInputVariables())
            .build(),
        Effect.WRITE, (arguments, identity) -> {
          String id = arguments.string("processDefinitionId");
          String key = arguments.string("processDefinitionKey");
          ProcessInstantiationBuilder builder;
          if (id != null) {
            builder = runtime().createProcessInstanceById(id);
          } else if (key != null) {
            builder = runtime().createProcessInstanceByKey(key);
            String tenantId = arguments.string("tenantId");
            if (tenantId != null) {
              builder.processDefinitionTenantId(tenantId);
            }
          } else {
            throw new ToolException("Give processDefinitionId or processDefinitionKey");
          }
          String businessKey = arguments.string("businessKey");
          if (businessKey != null) {
            builder.businessKey(businessKey);
          }
          builder.setVariables(support.values().toVariables(arguments.object("variables")));
          ProcessInstance instance = builder.execute();
          return instance(instance);
        });
  }
}
