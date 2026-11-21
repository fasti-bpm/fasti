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

import java.util.List;

import dev.fasti.engine.foureyes.FourEyesService;
import dev.fasti.engine.foureyes.FourEyesStatus;
import org.camunda.bpm.engine.AuthorizationException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.exception.NotFoundException;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;

/** The four-eyes service of one engine. */
public class FourEyesServiceImpl implements FourEyesService {

  protected final ProcessEngineConfigurationImpl configuration;
  protected final boolean enabled;

  public FourEyesServiceImpl(ProcessEngineConfigurationImpl configuration) {
    this.configuration = configuration;
    this.enabled = isRegistered(configuration);
  }

  /** Whether the plugin registered its parse listener with the engine. */
  static boolean isRegistered(ProcessEngineConfigurationImpl configuration) {
    List<BpmnParseListener> listeners = configuration.getCustomPreBPMNParseListeners();
    if (listeners != null) {
      for (BpmnParseListener listener : listeners) {
        if (listener instanceof FourEyesBpmnParseListener) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public FourEyesStatus getStatus(String taskId) {
    if (taskId == null || taskId.isEmpty()) {
      throw new BadUserRequestException("A task id is required.");
    }
    return configuration.getCommandExecutorTxRequired().execute(new GetStatusCmd(taskId, enabled));
  }

  /**
   * Reads the status of a task for the authenticated user; a task the user may not read is
   * reported as not found.
   */
  public static class GetStatusCmd implements Command<FourEyesStatus> {
    protected final String taskId;
    protected final boolean enabled;

    public GetStatusCmd(String taskId, boolean enabled) {
      this.taskId = taskId;
      this.enabled = enabled;
    }

    protected NotFoundException notFound() {
      return new NotFoundException("Cannot find task with id " + taskId);
    }

    @Override
    public FourEyesStatus execute(CommandContext commandContext) {
      TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
      if (task == null) {
        throw notFound();
      }
      try {
        for (CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
          checker.checkReadTask(task);
        }
      } catch (ProcessEngineException e) {
        // a task the user may not read (authorization, tenant) is answered like one that does not
        // exist, as the task query does: the answer does not tell whether the id is in use
        if (e instanceof AuthorizationException || e.getClass() == ProcessEngineException.class) {
          throw notFound();
        }
        throw e;
      }
      String userId = commandContext.getAuthenticatedUserId();
      if (!enabled) {
        return new FourEyesStatus(false, task.getId(), null, userId, null, task.getAssignee(), null, false);
      }
      return FourEyesGuard.status(commandContext, task, FourEyesRule.of(task), userId);
    }
  }
}
