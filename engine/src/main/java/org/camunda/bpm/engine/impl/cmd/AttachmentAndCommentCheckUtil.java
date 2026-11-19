/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.impl.cmd;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.AttachmentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricProcessInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.HistoricTaskInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;

/**
 * Performs the {@link CommandChecker command checks} of the commands which
 * access attachments and comments. Attachments and comments do not have
 * permissions on their own, the checks are performed on the task or the
 * process instance they belong to:
 *
 * <ul>
 *   <li>If the task (process instance) still exists, the checks for the
 *   task (process instance) are performed.</li>
 *   <li>If it only exists in the history, e.g. because the task has been
 *   completed, the checks for the historic task instance (historic process
 *   instance) are performed.</li>
 *   <li>If it does not exist at all, there is nothing a check can be based
 *   on and no check is performed.</li>
 * </ul>
 */
public class AttachmentAndCommentCheckUtil {

  /**
   * Checks if it is allowed to read the attachments, comments and events of
   * the task with the given id.
   *
   * @return <code>false</code> if neither a task nor a historic task instance
   *         exists for the given id, so that no check could be performed
   */
  public static boolean checkReadByTaskId(CommandContext commandContext, String taskId) {
    if (taskId == null) {
      return false;
    }

    TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
    if (task != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkReadTaskAttachmentsAndComments(task);
      }
      return true;
    }

    HistoricTaskInstanceEntity historicTask = commandContext.getHistoricTaskInstanceManager().findHistoricTaskInstanceById(taskId);
    if (historicTask != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkReadHistoricTaskInstance(historicTask);
      }
      return true;
    }

    return false;
  }

  /**
   * Checks if it is allowed to read the attachments and comments of the
   * process instance with the given id.
   *
   * @return <code>false</code> if neither a process instance nor a historic
   *         process instance exists for the given id, so that no check could
   *         be performed
   */
  public static boolean checkReadByProcessInstanceId(CommandContext commandContext, String processInstanceId) {
    if (processInstanceId == null) {
      return false;
    }

    ExecutionEntity processInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId);
    if (processInstance != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkReadProcessInstanceAttachmentsAndComments(processInstance);
      }
      return true;
    }

    HistoricProcessInstanceEntity historicProcessInstance = commandContext.getHistoricProcessInstanceManager().findHistoricProcessInstance(processInstanceId);
    if (historicProcessInstance != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkReadHistoricProcessInstance(historicProcessInstance);
      }
      return true;
    }

    return false;
  }

  /**
   * Checks if it is allowed to read the given attachment: an attachment of a
   * task requires that the attachments of the task can be read, any other
   * attachment requires that the attachments of its process instance can be
   * read.
   */
  public static void checkRead(CommandContext commandContext, AttachmentEntity attachment) {
    if (!checkReadByTaskId(commandContext, attachment.getTaskId())) {
      checkReadByProcessInstanceId(commandContext, attachment.getProcessInstanceId());
    }
  }

  /**
   * Checks if it is allowed to create or change an attachment or a comment
   * of the given task or, if it does not belong to a task, of the given
   * process instance.
   */
  public static void checkUpdate(CommandContext commandContext, String taskId, String processInstanceId) {
    ensureProcessInstanceOfTask(commandContext, taskId, processInstanceId);
    if (!checkWriteByTaskId(commandContext, taskId, false)) {
      checkWriteByProcessInstanceId(commandContext, processInstanceId, false);
    }
  }

  /**
   * Rejects a process instance id that does not belong to the given task.
   * The checks above are performed on the task alone, so a foreign process
   * instance id would otherwise attach the comment or attachment to a process
   * instance the user may not change.
   */
  protected static void ensureProcessInstanceOfTask(CommandContext commandContext, String taskId, String processInstanceId) {
    if (taskId == null || processInstanceId == null || !isAuthorizationChecked(commandContext)) {
      // without an authorization check nothing can be bypassed: the upstream behaviour stays
      return;
    }

    boolean taskExists = false;
    String processInstanceOfTask = null;
    TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
    if (task != null) {
      taskExists = true;
      processInstanceOfTask = task.getProcessInstanceId();
    } else {
      HistoricTaskInstanceEntity historicTask = commandContext.getHistoricTaskInstanceManager().findHistoricTaskInstanceById(taskId);
      if (historicTask != null) {
        taskExists = true;
        processInstanceOfTask = historicTask.getProcessInstanceId();
      }
    }

    if (!taskExists || processInstanceId.equals(processInstanceOfTask)) {
      return;
    }
    // an id that names no process instance stays allowed (upstream behaviour, e.g. free text on a
    // standalone task); an id of another real process instance is not covered by the task check
    boolean foreignInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId) != null
        || commandContext.getHistoricProcessInstanceManager().findHistoricProcessInstance(processInstanceId) != null;
    if (foreignInstance) {
      throw new BadUserRequestException("Process instance '" + processInstanceId + "' is not the process instance of task '" + taskId + "'");
    }
  }

  protected static boolean isAuthorizationChecked(CommandContext commandContext) {
    return commandContext.getProcessEngineConfiguration().isAuthorizationEnabled()
        && commandContext.isAuthorizationCheckEnabled()
        && commandContext.getAuthenticatedUserId() != null;
  }

  /**
   * Checks if it is allowed to change the given attachment.
   */
  public static void checkUpdate(CommandContext commandContext, AttachmentEntity attachment) {
    checkUpdate(commandContext, attachment.getTaskId(), attachment.getProcessInstanceId());
  }

  /**
   * Checks if it is allowed to delete the given attachment.
   */
  public static void checkDelete(CommandContext commandContext, AttachmentEntity attachment) {
    if (!checkWriteByTaskId(commandContext, attachment.getTaskId(), true)) {
      checkWriteByProcessInstanceId(commandContext, attachment.getProcessInstanceId(), true);
    }
  }

  /**
   * As long as the task exists, its attachments and comments can be created,
   * changed and deleted by everyone who is allowed to work on the task. As
   * soon as the task only exists in the history, this is a change of the
   * history.
   */
  protected static boolean checkWriteByTaskId(CommandContext commandContext, String taskId, boolean delete) {
    if (taskId == null) {
      return false;
    }

    TaskEntity task = commandContext.getTaskManager().findTaskById(taskId);
    if (task != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkTaskWork(task);
      }
      return true;
    }

    HistoricTaskInstanceEntity historicTask = commandContext.getHistoricTaskInstanceManager().findHistoricTaskInstanceById(taskId);
    if (historicTask != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        if (delete) {
          checker.checkDeleteHistoricTaskInstance(historicTask);
        } else {
          checker.checkUpdateHistoricTaskInstance(historicTask);
        }
      }
      return true;
    }

    return false;
  }

  /**
   * As long as the process instance exists, its attachments and comments can
   * be created, changed and deleted by everyone who is allowed to update the
   * process instance. As soon as the process instance only exists in the
   * history, this is a change of the history.
   */
  protected static boolean checkWriteByProcessInstanceId(CommandContext commandContext, String processInstanceId, boolean delete) {
    if (processInstanceId == null) {
      return false;
    }

    ExecutionEntity processInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId);
    if (processInstance != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        checker.checkUpdateProcessInstance(processInstance);
      }
      return true;
    }

    HistoricProcessInstanceEntity historicProcessInstance = commandContext.getHistoricProcessInstanceManager().findHistoricProcessInstance(processInstanceId);
    if (historicProcessInstance != null) {
      for (CommandChecker checker : getCommandCheckers(commandContext)) {
        if (delete) {
          checker.checkDeleteHistoricProcessInstance(historicProcessInstance);
        } else {
          checker.checkUpdateHistoricProcessInstance(historicProcessInstance);
        }
      }
      return true;
    }

    return false;
  }

  protected static Iterable<CommandChecker> getCommandCheckers(CommandContext commandContext) {
    return commandContext.getProcessEngineConfiguration().getCommandCheckers();
  }

}
