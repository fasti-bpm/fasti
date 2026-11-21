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

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import dev.fasti.engine.foureyes.FourEyesService;
import dev.fasti.engine.foureyes.ReservedVariableException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.core.variable.CoreVariableInstance;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.value.TypedValue;

/**
 * Keeps the record of the four-eyes control ({@value FourEyesService#VARIABLE_PREFIX}{@code <step>})
 * from being changed by anyone but the control itself.
 *
 * <p>The variable scopes of the engine ask here before a variable is set or removed by name. On
 * an engine with the four-eyes plugin, a variable with the reserved prefix in an execution of a
 * process instance (the process instance itself included) can only be written by
 * {@link FourEyesGuard#record}; every other write or removal is refused with a
 * {@link ReservedVariableException}, whichever way it comes: the task, runtime, form or external
 * task API, a message or signal with variables, a modification, a batch, or code of the model
 * (delegates, listeners, scripts, mappings). The task API matters most: setting a variable of a
 * task needs only the permission every candidate and assignee has, and reaches the process
 * instance.</p>
 *
 * <p>Two things are let through quietly, so that existing models and workers keep running:
 * setting a record to the value it already has (a worker or form that sends back every variable
 * it read) changes nothing, and copies of all variables made by the engine (call activity
 * {@code variables="all"}, restarting a process instance) leave the records out, see
 * {@link #withoutRecords(Map)}. Variables that go when their scope ends are not affected.</p>
 */
public final class ReservedVariables {

  /** Set while the four-eyes control itself writes on this thread. */
  private static final ThreadLocal<Boolean> CONTROL_WRITES = new ThreadLocal<>();

  private ReservedVariables() {
  }

  /** Whether the name is that of a four-eyes record. */
  public static boolean isRecord(String variableName) {
    return variableName != null && variableName.startsWith(FourEyesService.VARIABLE_PREFIX);
  }

  /**
   * Called before a variable is set in a scope.
   *
   * @return {@code false} if the variable is to be left as it is
   * @throws ReservedVariableException if the variable is a four-eyes record that would change
   */
  public static boolean beforeSet(AbstractVariableScope scope, String variableName, TypedValue value) {
    if (!isRecord(variableName) || !isProtected(scope)) {
      return true;
    }
    CoreVariableInstance existing = scope.getVariableInstanceLocal(variableName);
    if (existing != null && value != null && !value.isTransient()) {
      TypedValue current = existing.getTypedValue(false);
      if (current != null && current.getValue() instanceof String
          && Objects.equals(current.getValue(), value.getValue())) {
        return false;
      }
    }
    throw refusal(variableName);
  }

  /**
   * Called before a variable is removed from a scope by name.
   *
   * @throws ReservedVariableException if the variable is a four-eyes record
   */
  public static void beforeRemove(AbstractVariableScope scope, String variableName) {
    if (isRecord(variableName) && isProtected(scope)) {
      throw refusal(variableName);
    }
  }

  /**
   * Whether the variable stays when all variables of the scope are removed at once
   * ({@code VariableScope#removeVariables()}).
   */
  public static boolean isKept(AbstractVariableScope scope, String variableName) {
    return isRecord(variableName) && isProtected(scope);
  }

  /**
   * Removes the four-eyes records from variables the engine copies as a whole to another process
   * instance (on an engine with the plugin): the records belong to the instance they were written
   * in.
   */
  public static <T extends Map<String, ?>> T withoutRecords(T variables) {
    if (variables != null && variables.keySet().stream().anyMatch(ReservedVariables::isRecord) && isEnabled()) {
      variables.keySet().removeIf(ReservedVariables::isRecord);
    }
    return variables;
  }

  /** Runs the action as the four-eyes control, which may write its records. */
  public static <T> T asControl(Supplier<T> action) {
    Boolean before = CONTROL_WRITES.get();
    CONTROL_WRITES.set(Boolean.TRUE);
    try {
      return action.get();
    } finally {
      if (before == null) {
        CONTROL_WRITES.remove();
      }
    }
  }

  static boolean isProtected(AbstractVariableScope scope) {
    return scope instanceof ExecutionEntity && CONTROL_WRITES.get() == null && isEnabled();
  }

  static boolean isEnabled() {
    ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
    return configuration != null && FourEyesServiceImpl.isRegistered(configuration);
  }

  static ReservedVariableException refusal(String variableName) {
    return new ReservedVariableException(variableName, "Four-eyes control: variable '" + variableName
        + "' records who completed a step and is written by the engine only; it cannot be set or removed.");
  }
}
