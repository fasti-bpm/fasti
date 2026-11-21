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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.core.variable.CoreVariableInstance;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.type.ValueType;

/**
 * The variables visible to a task (its own, then those of the enclosing scopes up to the process
 * instance), at most {@code max + 1} of them, described without loading what is not returned:
 * binary values and files are not fetched, Java-serialized objects not read, masked variables
 * not read at all. Checks the same permission as {@code TaskService#getVariables}
 * ({@code READ_VARIABLE} or {@code READ} on the task).
 */
public class TaskVariablesCmd implements Command<List<Map<String, Object>>> {

  protected static final String JAVA_SERIALIZATION = "application/x-java-serialized-object";

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
    List<Map<String, Object>> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    AbstractVariableScope scope = task;
    while (scope != null && result.size() <= max) {
      for (CoreVariableInstance instance : scope.getVariableInstancesLocal()) {
        if (result.size() > max) {
          break;
        }
        // an inner scope hides a variable of the same name further out
        if (seen.add(instance.getName())) {
          result.add(describe(instance));
        }
      }
      scope = scope.getParentVariableScope();
    }
    return result;
  }

  protected Map<String, Object> describe(CoreVariableInstance instance) {
    String name = instance.getName();
    if (!(instance instanceof VariableInstanceEntity entity)) {
      return values.variable(name, instance.getTypedValue(false), null);
    }
    String typeName = entity.getTypeName();
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
      if (ValueType.FILE.getName().equals(typeName)) {
        binary.put("filename", entity.getTextValue());
      }
      binary.put("note", "binary content is not returned");
      return binary;
    }
    if (entity.getSerializer() != null && JAVA_SERIALIZATION.equals(entity.getSerializer().getSerializationDataformat())) {
      Map<String, Object> java = new LinkedHashMap<>();
      java.put("name", name);
      java.put("type", typeName);
      java.put("serializationDataFormat", JAVA_SERIALIZATION);
      java.put("objectTypeName", entity.getTextValue2());
      java.put("note", "Java-serialized objects are not returned");
      return java;
    }
    try {
      return values.variable(name, entity.getTypedValue(false), entity.getErrorMessage());
    } catch (RuntimeException e) {
      return values.variable(name, null, "the value cannot be read");
    }
  }
}
