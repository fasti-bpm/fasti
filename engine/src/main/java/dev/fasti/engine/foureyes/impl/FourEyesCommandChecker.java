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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.cmd.CompleteTaskCmd;
import org.camunda.bpm.engine.impl.cmd.HandleTaskBpmnErrorCmd;
import org.camunda.bpm.engine.impl.cmd.HandleTaskEscalationCmd;
import org.camunda.bpm.engine.impl.cmd.ResolveTaskCmd;
import org.camunda.bpm.engine.impl.cmd.SubmitTaskFormCmd;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInvocationContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.task.DelegationState;

/**
 * The command checker of the four-eyes control. The engine asks the command checkers before a
 * task is completed, its form submitted, or a BPMN error or escalation is thrown from it
 * ({@link CommandChecker#checkTaskWork}); a refusal there reaches the caller unwrapped, so the REST
 * API answers {@code 403}. The same check also runs before claims and comment changes, which the
 * control leaves alone: it acts only for the commands that end the task.
 *
 * <p>Every other check is a no-op. The checker is a dynamic proxy of the interface so that it
 * stays a no-op for checks added to the engine later.</p>
 */
public final class FourEyesCommandChecker implements InvocationHandler {

  private FourEyesCommandChecker() {
  }

  public static CommandChecker create() {
    return (CommandChecker) Proxy.newProxyInstance(CommandChecker.class.getClassLoader(),
        new Class<?>[] { CommandChecker.class }, new FourEyesCommandChecker());
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    if (method.getDeclaringClass() == Object.class) {
      switch (method.getName()) {
      case "equals":
        return proxy == args[0];
      case "hashCode":
        return System.identityHashCode(proxy);
      default:
        return "FourEyesCommandChecker";
      }
    }
    if ("checkTaskWork".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof TaskEntity) {
      checkTaskWork((TaskEntity) args[0]);
    }
    return null;
  }

  static void checkTaskWork(TaskEntity task) {
    CommandContext commandContext = Context.getCommandContext();
    if (commandContext == null || !endsTask(task)) {
      return;
    }
    FourEyesRule rule = FourEyesRule.of(task);
    if (rule != null) {
      FourEyesGuard.check(commandContext, task, rule);
      commandContext.getSession(CheckedTasks.class).add(task.getId());
    }
  }

  /** Whether the current command ends the task (and is not, for example, a claim). */
  static boolean endsTask(TaskEntity task) {
    CommandInvocationContext invocation = Context.getCommandInvocationContext();
    Command<?> command = invocation == null ? null : invocation.getCommand();
    if (command instanceof SubmitTaskFormCmd) {
      // the form of a delegated task resolves it (back to its owner), it does not complete it
      return task.getDelegationState() != DelegationState.PENDING;
    }
    if (command instanceof ResolveTaskCmd) {
      // a CompleteTaskCmd that hands the task back to its owner: not a completion
      return false;
    }
    return command instanceof CompleteTaskCmd
        || command instanceof HandleTaskBpmnErrorCmd
        || command instanceof HandleTaskEscalationCmd;
  }
}
