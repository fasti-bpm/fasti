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
package dev.fasti.engine.foureyes.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.fasti.engine.foureyes.FourEyesService;
import dev.fasti.engine.foureyes.FourEyesStatus;
import dev.fasti.engine.foureyes.FourEyesViolationException;
import org.camunda.bpm.engine.EntityTypes;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.HistoricTaskInstanceQueryImpl;
import org.camunda.bpm.engine.impl.UserOperationLogQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.util.JsonUtil;
import org.camunda.bpm.engine.variable.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Who completed which checked step of a process instance, and whether a task with a rule may be
 * completed now.
 *
 * <p>A person "did" a step when they completed it (the authenticated user of the completion) or
 * held it at completion (its assignee). Both are recorded by {@link #record} in the process
 * instance variable {@value FourEyesService#VARIABLE_PREFIX}{@code <step>}, which exists at every
 * history level. Where history is kept, the assignees of the completed historic task instances
 * and the users of the {@code Complete} entries of the user operation log are added, so that steps
 * completed on a node without the plugin (Camunda 7.24) or a removed variable still count.</p>
 *
 * <p>The same two people make up the actors of the guarded task: the authenticated user who
 * completes it and its assignee. If one of them did a checked step, or neither exists while a
 * checked step was done, the completion is refused.</p>
 */
public final class FourEyesGuard {

  private static final Logger LOG = LoggerFactory.getLogger(FourEyesGuard.class);

  private FourEyesGuard() {
  }

  /** Throws a {@link FourEyesViolationException} if the actors of the task may not complete it. */
  public static void check(CommandContext commandContext, TaskEntity task, FourEyesRule rule) {
    ExecutionEntity processInstance = task.getProcessInstance();
    if (processInstance == null) {
      return;
    }
    // conflicts with a concurrent completion of a checked step of the same instance, which
    // force-updates the instance as well: one of the two transactions fails and is retried
    processInstance.forceUpdate();
    Map<String, Set<String>> earlier = earlierActors(commandContext, processInstance, rule.getStepIds());
    if (earlier.isEmpty()) {
      return;
    }
    String userId = commandContext.getAuthenticatedUserId();
    String assignee = task.getAssignee();
    String key = task.getTaskDefinitionKey();
    String name = task.getName() == null ? "'" + key + "'" : "'" + task.getName() + "' (" + key + ")";
    List<String> excluded = new ArrayList<>();
    Set<String> steps = new LinkedHashSet<>();
    for (String actor : actors(userId, assignee)) {
      Set<String> done = earlier.get(actor);
      if (done != null) {
        excluded.add(actor);
        steps.addAll(done);
      }
    }
    if (excluded.isEmpty() && (userId != null || assignee != null)) {
      return;
    }
    String message;
    if (excluded.isEmpty()) {
      message = "Four-eyes control: task " + name + " checks earlier steps (" + String.join(", ", rule.getStepIds())
          + "), so it can only be completed by an authenticated user or by its assignee.";
      steps.addAll(rule.getStepIds());
    } else if (userId != null && excluded.contains(userId)) {
      message = "Four-eyes control: user '" + userId + "' cannot complete task " + name
          + " after completing an earlier step it checks (" + String.join(", ", steps)
          + "). Another person must complete it.";
    } else {
      message = "Four-eyes control: task " + name + " is assigned to '" + assignee
          + "', who completed an earlier step it checks (" + String.join(", ", steps)
          + "). Assign it to another person before completing it.";
    }
    throw new FourEyesViolationException(message, task.getId(), key, excluded, new ArrayList<>(steps));
  }

  /** The status of the task for the given user (the authenticated user). */
  public static FourEyesStatus status(CommandContext commandContext, TaskEntity task, FourEyesRule rule, String userId) {
    String assignee = task.getAssignee();
    if (rule == null) {
      return new FourEyesStatus(true, task.getId(), null, userId, null, assignee, null, false);
    }
    ExecutionEntity processInstance = task.getProcessInstance();
    Map<String, Set<String>> earlier = processInstance == null
        ? Collections.emptyMap()
        : earlierActors(commandContext, processInstance, rule.getStepIds());
    List<String> userSteps = userId == null ? null : ordered(rule, earlier.get(userId));
    List<String> assigneeSteps = assignee == null ? null : ordered(rule, earlier.get(assignee));
    boolean blocked = !earlier.isEmpty()
        && ((userSteps != null && !userSteps.isEmpty())
            || (assigneeSteps != null && !assigneeSteps.isEmpty())
            || (userId == null && assignee == null));
    return new FourEyesStatus(true, task.getId(), rule.getSteps(), userId, userSteps, assignee, assigneeSteps, blocked);
  }

  /** Records the actors of a completed checked step in the variable of the process instance. */
  public static void record(CommandContext commandContext, TaskEntity task) {
    ExecutionEntity processInstance = task.getProcessInstance();
    Set<String> actors = actors(commandContext.getAuthenticatedUserId(), task.getAssignee());
    if (processInstance == null || actors.isEmpty()) {
      return;
    }
    processInstance.forceUpdate();
    String name = FourEyesService.VARIABLE_PREFIX + task.getTaskDefinitionKey();
    Set<String> users = new LinkedHashSet<>(readVariable(processInstance, name));
    if (users.addAll(actors)) {
      processInstance.setVariableLocal(name, Variables.stringValue(JsonUtil.asString(new ArrayList<>(users))));
    }
  }

  static Set<String> actors(String userId, String assignee) {
    Set<String> actors = new LinkedHashSet<>();
    if (userId != null) {
      actors.add(userId);
    }
    if (assignee != null) {
      actors.add(assignee);
    }
    return actors;
  }

  /** For each user who did one of the steps: the steps they did. */
  static Map<String, Set<String>> earlierActors(CommandContext commandContext, ExecutionEntity processInstance,
      Collection<String> stepIds) {
    Map<String, Set<String>> actors = new LinkedHashMap<>();
    for (String stepId : stepIds) {
      for (String user : readVariable(processInstance, FourEyesService.VARIABLE_PREFIX + stepId)) {
        actors.computeIfAbsent(user, k -> new LinkedHashSet<>()).add(stepId);
      }
    }
    boolean tenantCheck = commandContext.isTenantCheckEnabled();
    commandContext.disableTenantCheck();
    try {
      commandContext.runWithoutAuthorization(() -> {
        addFromHistory(commandContext, processInstance.getId(), stepIds, actors);
        return null;
      });
    } finally {
      commandContext.setTenantCheckEnabled(tenantCheck);
    }
    return actors;
  }

  static void addFromHistory(CommandContext commandContext, String processInstanceId, Collection<String> stepIds,
      Map<String, Set<String>> actors) {
    HistoricTaskInstanceQueryImpl taskQuery = new HistoricTaskInstanceQueryImpl();
    taskQuery.processInstanceId(processInstanceId);
    taskQuery.taskDefinitionKeyIn(stepIds.toArray(new String[0]));
    taskQuery.finished();
    List<HistoricTaskInstance> tasks = taskQuery.executeList(commandContext, null);
    Map<String, String> completed = new HashMap<>();
    for (HistoricTaskInstance task : tasks) {
      if (TaskEntity.DELETE_REASON_COMPLETED.equals(task.getDeleteReason())) {
        completed.put(task.getId(), task.getTaskDefinitionKey());
        if (task.getAssignee() != null) {
          actors.computeIfAbsent(task.getAssignee(), k -> new LinkedHashSet<>()).add(task.getTaskDefinitionKey());
        }
      }
    }
    if (completed.isEmpty()) {
      return;
    }
    // written at history level full, for authenticated users
    UserOperationLogQueryImpl logQuery = new UserOperationLogQueryImpl();
    logQuery.processInstanceId(processInstanceId);
    logQuery.entityType(EntityTypes.TASK);
    logQuery.operationType(UserOperationLogEntry.OPERATION_TYPE_COMPLETE);
    for (UserOperationLogEntry entry : logQuery.executeList(commandContext, null)) {
      String stepId = entry.getTaskId() == null ? null : completed.get(entry.getTaskId());
      if (stepId != null && entry.getUserId() != null) {
        actors.computeIfAbsent(entry.getUserId(), k -> new LinkedHashSet<>()).add(stepId);
      }
    }
  }

  static List<String> readVariable(ExecutionEntity processInstance, String name) {
    Object value = processInstance.getVariableLocal(name);
    if (value == null) {
      return Collections.emptyList();
    }
    if (value instanceof String) {
      try {
        JsonElement json = JsonParser.parseString((String) value);
        if (json.isJsonArray()) {
          // only string entries count; anything else (null, numbers, objects) was not written here
          List<String> users = new ArrayList<>();
          for (JsonElement entry : json.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
              users.add(entry.getAsString());
            }
          }
          return users;
        }
      } catch (JsonParseException e) {
        // reported below
      }
    }
    LOG.warn("Process instance {}: variable {} does not hold a JSON array of user ids and is ignored",
        processInstance.getId(), name);
    return Collections.emptyList();
  }

  static List<String> ordered(FourEyesRule rule, Set<String> steps) {
    List<String> result = new ArrayList<>();
    if (steps != null) {
      for (String id : rule.getStepIds()) {
        if (steps.contains(id)) {
          result.add(id);
        }
      }
    }
    return result;
  }
}
