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

import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.variable.VariableMap;

import dev.fasti.spring.boot.mcp.auth.McpIdentity;
import dev.fasti.spring.boot.mcp.tools.ToolSupport.Effect;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;

/**
 * User tasks: list, read, claim, unclaim and complete. Write tools act for the authenticated user
 * only: a task is claimed for that user, and only a task assigned to that user can be unclaimed
 * or completed. The engine checks the permissions and the four-eyes rules.
 */
public class TaskTools {

  protected static final int MAX_DESCRIPTION_LENGTH = 2000;
  protected static final int MAX_NEXT_TASKS = 10;

  protected final ToolSupport support;

  public TaskTools(ToolSupport support) {
    this.support = support;
  }

  public List<SyncToolSpecification> readTools() {
    return List.of(listTasks(), getTask());
  }

  public List<SyncToolSpecification> writeTools() {
    return List.of(claimTask(), unclaimTask(), completeTask());
  }

  protected TaskService tasks() {
    return support.engine().getTaskService();
  }

  protected SyncToolSpecification listTasks() {
    return support.tool("list_tasks", "List user tasks",
        "Lists open user tasks the user may read, newest first. Use assignedToMe=true for the "
            + "user's own tasks and claimableByMe=true for unassigned tasks the user or one of the "
            + "user's groups is a candidate for. Further filters: assignee, candidate group, process "
            + "instance, process definition key, task definition key, name.",
        Schemas.object()
            .bool("assignedToMe", "Only tasks assigned to the authenticated user.")
            .bool("claimableByMe", "Only unassigned tasks the authenticated user may claim as a candidate.")
            .string("assignee", "Only tasks assigned to this user.")
            .string("candidateGroup", "Only tasks this group is a candidate for.")
            .bool("unassigned", "Only unassigned tasks.")
            .string("processInstanceId", "Only tasks of this process instance.")
            .string("processDefinitionKey", "Only tasks of this process definition key.")
            .string("taskDefinitionKey", "Only tasks of this activity id.")
            .string("nameLike", "Part of the task name.")
            .paging(support.defaultResults(), support.maxResults())
            .build(),
        Effect.READ, (arguments, identity) -> {
          TaskQuery query = tasks().createTaskQuery();
          if (arguments.bool("assignedToMe", false)) {
            query.taskAssignee(identity.userId());
          }
          if (arguments.bool("claimableByMe", false)) {
            query.taskUnassigned();
            if (identity.groups().isEmpty()) {
              query.taskCandidateUser(identity.userId());
            } else {
              query.or()
                  .taskCandidateUser(identity.userId())
                  .taskCandidateGroupIn(identity.groups())
                  .endOr();
            }
          }
          String value = arguments.string("assignee");
          if (value != null) {
            query.taskAssignee(value);
          }
          value = arguments.string("candidateGroup");
          if (value != null) {
            query.taskCandidateGroup(value);
          }
          if (arguments.bool("unassigned", false)) {
            query.taskUnassigned();
          }
          value = arguments.string("processInstanceId");
          if (value != null) {
            query.processInstanceId(value);
          }
          value = arguments.string("processDefinitionKey");
          if (value != null) {
            query.processDefinitionKey(value);
          }
          value = arguments.string("taskDefinitionKey");
          if (value != null) {
            query.taskDefinitionKey(value);
          }
          value = arguments.string("nameLike");
          if (value != null) {
            query.taskNameLike(ToolSupport.like(value));
          }
          query.orderByTaskCreateTime().desc();
          int first = arguments.firstResult();
          List<Map<String, Object>> items = new ArrayList<>();
          for (Task task : query.listPage(first, arguments.maxResults(support.defaultResults(), support.maxResults()))) {
            items.add(task(task));
          }
          return Json.page(items, first, query.count());
        });
  }

  protected SyncToolSpecification getTask() {
    return support.tool("get_task", "Get a user task",
        "Returns one open user task with its form key, candidate users and groups and the variables "
            + "visible to it (masked and cut like those of a process instance).",
        Schemas.object()
            .string("id", "Task id.")
            .bool("includeVariables", "Include the variables (default true).")
            .required("id")
            .build(),
        Effect.READ, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          Task task = find(id, true);
          Json result = task(task).with("formKey", task.getFormKey());
          List<String> candidateUsers = new ArrayList<>();
          List<String> candidateGroups = new ArrayList<>();
          for (IdentityLink link : tasks().getIdentityLinksForTask(id)) {
            if (candidateUsers.size() + candidateGroups.size() >= support.maxResults()) {
              result.with("candidatesTruncated", true);
              break;
            }
            if ("candidate".equals(link.getType())) {
              if (link.getUserId() != null) {
                candidateUsers.add(link.getUserId());
              }
              if (link.getGroupId() != null) {
                candidateGroups.add(link.getGroupId());
              }
            }
          }
          result.with("candidateUsers", candidateUsers).with("candidateGroups", candidateGroups);
          if (arguments.bool("includeVariables", true)) {
            int max = support.properties().getMaxVariables();
            List<Map<String, Object>> variables = ((ProcessEngineConfigurationImpl) support.engine()
                .getProcessEngineConfiguration()).getCommandExecutorTxRequired()
                .execute(new TaskVariablesCmd(id, max, support.values()));
            result.with("variables", ToolSupport.limit(variables, max));
            if (variables.size() > max) {
              result.with("variablesTruncated", true);
            }
          }
          return result;
        });
  }

  protected Task find(String id, boolean formKeys) {
    TaskQuery query = tasks().createTaskQuery().taskId(id);
    if (formKeys) {
      query.initializeFormKeys();
    }
    Task task = query.singleResult();
    if (task == null) {
      throw ToolException.notFound("Task", id);
    }
    return task;
  }

  protected Json task(Task task) {
    String description = task.getDescription();
    if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
      description = description.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
    }
    return Json.obj()
        .with("id", task.getId())
        .with("name", task.getName())
        .with("description", description)
        .with("assignee", task.getAssignee())
        .with("owner", task.getOwner())
        .with("created", task.getCreateTime())
        .with("due", task.getDueDate())
        .with("followUp", task.getFollowUpDate())
        .with("priority", task.getPriority())
        .with("taskDefinitionKey", task.getTaskDefinitionKey())
        .with("processInstanceId", task.getProcessInstanceId())
        .with("processDefinitionId", task.getProcessDefinitionId())
        .with("delegationState", task.getDelegationState() == null ? null : task.getDelegationState().name())
        .with("suspended", task.isSuspended())
        .with("tenantId", task.getTenantId());
  }

  protected SyncToolSpecification claimTask() {
    return support.tool("claim_task", "Claim a user task",
        "Assigns an unassigned user task to the authenticated user (never to someone else). The "
            + "engine checks the permission (TASK_WORK or UPDATE) and four-eyes rules; a refusal is "
            + "returned as an error with the engine's reason.",
        Schemas.object().string("id", "Task id.").required("id").build(),
        Effect.WRITE, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          find(id, false);
          tasks().claim(id, identity.userId());
          return task(find(id, false)).with("claimed", true);
        });
  }

  protected SyncToolSpecification unclaimTask() {
    return support.tool("unclaim_task", "Unclaim a user task",
        "Returns a user task assigned to the authenticated user to the candidates (removes the "
            + "assignee). Only the user's own tasks can be unclaimed with this tool.",
        Schemas.object().string("id", "Task id.").required("id").build(),
        Effect.WRITE, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          requireAssignedTo(find(id, false), identity);
          tasks().claim(id, null);
          return task(find(id, false)).with("unclaimed", true);
        });
  }

  protected SyncToolSpecification completeTask() {
    return support.tool("complete_task", "Complete a user task",
        "Completes a user task assigned to the authenticated user (claim it first), with optional "
            + "variables (strings, numbers, booleans or null). The engine checks the permission and "
            + "four-eyes rules: a refusal is returned as an error with the engine's reason. Returns "
            + "the open tasks of the process instance that follow.",
        Schemas.object()
            .string("id", "Task id.")
            .variables("variables", "Variables to set, e.g. {\"approved\": true, \"comment\": \"ok\"}.",
                support.properties().getMaxInputVariables())
            .required("id")
            .build(),
        Effect.WRITE, (arguments, identity) -> {
          String id = arguments.requiredString("id");
          Task task = find(id, false);
          requireAssignedTo(task, identity);
          VariableMap variables = support.values().toVariables(arguments.object("variables"));
          tasks().complete(id, variables);
          Json result = Json.obj().with("completed", true).with("id", id);
          String processInstanceId = task.getProcessInstanceId();
          if (processInstanceId != null) {
            result.with("processInstanceId", processInstanceId);
            List<Map<String, Object>> next = new ArrayList<>();
            for (Task open : tasks().createTaskQuery().processInstanceId(processInstanceId)
                .orderByTaskCreateTime().desc().listPage(0, MAX_NEXT_TASKS)) {
              next.add(task(open));
            }
            result.with("nextTasks", next);
          }
          return result;
        });
  }

  protected void requireAssignedTo(Task task, McpIdentity identity) {
    if (!identity.userId().equals(task.getAssignee())) {
      throw new ToolException("Task '" + task.getId() + "' is not assigned to you"
          + (task.getAssignee() == null ? "; claim it first" : ""));
    }
  }
}
