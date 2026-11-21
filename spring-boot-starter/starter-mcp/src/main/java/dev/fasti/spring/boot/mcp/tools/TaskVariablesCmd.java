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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.VariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.variable.type.ValueType;

/**
 * The variables visible to a task (its own, then those of the enclosing scopes up to the process
 * instance; an inner variable hides an outer one of the same name), described without loading
 * what is not returned. Checks the same permission as {@code TaskService#getVariables}
 * ({@code READ_VARIABLE} or {@code READ} on the task, and the tenant), then reads at most a bounded
 * page of variables of those scopes with one query that fetches no binary content and
 * deserializes no objects; Java-serialized objects and masked variables are not read.
 */
public class TaskVariablesCmd implements Command<List<Map<String, Object>>> {

  protected static final String JAVA_SERIALIZATION = "application/x-java-serialized-object";
  /** Most enclosing scopes followed, and extra rows read for names hidden by inner scopes. */
  protected static final int MAX_SCOPES = 50;

  protected final String taskId;
  protected final int max;
  protected final Values values;

  public TaskVariablesCmd(String taskId, int max, Values values) {
    this.taskId = taskId;
    this.max = max;
    this.values = values;
  }

  @Override
  public List<Map<String, Object>> execute(CommandContext commandContext) {
    TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
    if (task == null) {
      throw ToolException.notFound("Task", taskId);
    }
    for (CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
      checker.checkReadTaskVariable(task);
    }

    // the task, then its execution and the enclosing executions, innermost first
    List<String> scopeIds = new ArrayList<>();
    AbstractVariableScope scope = task;
    while (scope != null && scopeIds.size() < MAX_SCOPES) {
      if (scope instanceof DbEntity entity) {
        scopeIds.add(entity.getId());
      }
      scope = scope.getParentVariableScope();
    }

    VariableInstanceQueryImpl query = new VariableInstanceQueryImpl();
    query.variableScopeIdIn(scopeIds.toArray(new String[0]))
        .disableBinaryFetching()
        .disableCustomObjectDeserialization()
        .orderByVariableName().asc();
    List<VariableInstance> rows;
    // the permission to read the variables of this task was checked above; the query would
    // otherwise ask for READ on the process instance, which a task worker may not have
    commandContext.disableAuthorizationCheck();
    try {
      rows = query.executeList(commandContext, new Page(0, max + 1 + MAX_SCOPES));
    } finally {
      commandContext.enableAuthorizationCheck();
    }

    Map<String, VariableInstance> visible = new LinkedHashMap<>();
    for (VariableInstance row : rows) {
      VariableInstance known = visible.get(row.getName());
      if (known == null || depth(scopeIds, row) < depth(scopeIds, known)) {
        visible.put(row.getName(), row);
      }
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (VariableInstance variable : visible.values()) {
      if (result.size() > max) {
        break;
      }
      result.add(describe(variable));
    }
    return result;
  }

  protected static int depth(List<String> scopeIds, VariableInstance variable) {
    String scopeId = variable instanceof VariableInstanceEntity entity ? entity.getVariableScopeId() : null;
    int index = scopeId == null ? -1 : scopeIds.indexOf(scopeId);
    return index < 0 ? Integer.MAX_VALUE : index;
  }

  protected Map<String, Object> describe(VariableInstance variable) {
    String name = variable.getName();
    String typeName = variable.getTypeName();
    if (values.isMasked(name)) {
      Map<String, Object> masked = new LinkedHashMap<>();
      masked.put("name", name);
      masked.put("type", typeName);
      masked.put("masked", true);
      return masked;
    }
    if (ValueType.BYTES.getName().equals(typeName) || ValueType.FILE.getName().equals(typeName)) {
      Map<String, Object> binary = new LinkedHashMap<>();
      binary.put("name", name);
      binary.put("type", typeName);
      if (ValueType.FILE.getName().equals(typeName) && variable instanceof VariableInstanceEntity entity) {
        binary.put("filename", entity.getTextValue());
      }
      binary.put("note", "binary content is not returned");
      return binary;
    }
    if (variable instanceof VariableInstanceEntity entity && entity.getSerializer() != null
        && JAVA_SERIALIZATION.equals(entity.getSerializer().getSerializationDataformat())) {
      Map<String, Object> java = new LinkedHashMap<>();
      java.put("name", name);
      java.put("type", typeName);
      java.put("serializationDataFormat", JAVA_SERIALIZATION);
      java.put("objectTypeName", entity.getTextValue2());
      java.put("note", "Java-serialized objects are not returned");
      return java;
    }
    return values.variable(name, variable.getTypedValue(), variable.getErrorMessage());
  }
}
