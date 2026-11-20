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
package dev.fasti.engine.absence.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import dev.fasti.engine.absence.Absence;
import dev.fasti.engine.absence.AbsenceService.Handover;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.authorization.Permission;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.TaskQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.task.Task;

/** The commands of the absence service. */
public final class AbsenceCommands {

  static final int MAX_ID_LENGTH = 64;
  static final int MAX_NOTE_LENGTH = 255;

  private AbsenceCommands() {
  }

  /** A user manages their own absence; others need the permission on the user. */
  static void checkPermission(CommandContext commandContext, Permission permission, String userId) {
    String current = commandContext.getAuthenticatedUserId();
    if (current != null && current.equals(userId)) {
      return;
    }
    commandContext.getAuthorizationManager().checkAuthorization(permission, Resources.USER, userId);
  }

  static void requireUserId(String userId) {
    if (userId == null || userId.isEmpty()) {
      throw new BadUserRequestException("A user id is required.");
    }
  }

  static AbsenceSettings requireEnabled(AbsenceSettings settings) {
    if (settings == null) {
      throw new BadUserRequestException("Absence substitution is not enabled on this process engine "
          + "(fasti.absence.enabled, or the plugin dev.fasti.engine.absence.AbsenceSubstitutionPlugin).");
    }
    if (!settings.isAvailable()) {
      throw new BadUserRequestException("Absence substitution is not available: " + settings.getUnavailableReason());
    }
    return settings;
  }

  /** Reads the absence of a user. */
  public static class GetAbsenceCmd implements Command<Absence> {
    protected final AbsenceSettings settings;
    protected final String userId;

    public GetAbsenceCmd(AbsenceSettings settings, String userId) {
      this.settings = settings;
      this.userId = userId;
    }

    @Override
    public Absence execute(CommandContext commandContext) {
      requireUserId(userId);
      checkPermission(commandContext, Permissions.READ, userId);
      return storageReadable(commandContext, settings) ? AbsenceStore.find(commandContext, userId) : null;
    }
  }

  /**
   * Whether ACT_ID_INFO can be read: not when the plugin found it missing; without the plugin (an
   * absence set by another node can still be seen) when the engine uses the identity tables.
   */
  static boolean storageReadable(CommandContext commandContext, AbsenceSettings settings) {
    return settings != null ? settings.isAvailable() : commandContext.getProcessEngineConfiguration().isDbIdentityUsed();
  }

  /** Validates and stores the absence of a user. */
  public static class SetAbsenceCmd implements Command<Absence> {
    protected final AbsenceSettings settings;
    protected final String userId;
    protected final Date from;
    protected final Date until;
    protected final String substituteId;
    protected final String note;

    public SetAbsenceCmd(AbsenceSettings settings, String userId, Date from, Date until, String substituteId, String note) {
      this.settings = settings;
      this.userId = userId;
      this.from = from;
      this.until = until;
      this.substituteId = substituteId;
      this.note = note;
    }

    @Override
    public Absence execute(CommandContext commandContext) {
      requireEnabled(settings);
      requireUserId(userId);
      checkPermission(commandContext, Permissions.UPDATE, userId);

      Date now = ClockUtil.getCurrentTime();
      Date start = from != null ? from : now;
      if (until == null) {
        throw new BadUserRequestException("The end of the absence (until) is required.");
      }
      if (!until.after(start)) {
        throw new BadUserRequestException("The end of the absence must be after its start.");
      }
      if (!until.after(now)) {
        throw new BadUserRequestException("The end of the absence must be in the future.");
      }
      if (substituteId == null || substituteId.isEmpty()) {
        throw new BadUserRequestException("A substitute is required.");
      }
      if (substituteId.equals(userId)) {
        throw new BadUserRequestException("A user cannot be their own substitute.");
      }
      if (substituteId.length() > MAX_ID_LENGTH) {
        throw new BadUserRequestException("The substitute id is too long.");
      }
      String trimmedNote = note == null || note.trim().isEmpty() ? null : note.trim();
      if (trimmedNote != null && (trimmedNote.length() > MAX_NOTE_LENGTH || tooLongForDb2(commandContext, trimmedNote))) {
        throw new BadUserRequestException("The note may have at most " + MAX_NOTE_LENGTH + " characters.");
      }
      if (!AbsenceResolver.userExists(commandContext, userId)) {
        throw new BadUserRequestException("User '" + userId + "' does not exist.");
      }
      if (!AbsenceResolver.userExists(commandContext, substituteId)) {
        throw new BadUserRequestException("The substitute '" + substituteId + "' does not exist.");
      }
      checkNoCycle(commandContext, start);

      Absence absence = new Absence(userId, start, until, substituteId, trimmedNote);
      AbsenceStore.save(commandContext, absence);
      return absence;
    }

    /** Following the substitutes absent at the same time must not lead back to the user. */
    protected void checkNoCycle(CommandContext commandContext, Date start) {
      String current = substituteId;
      for (int hop = 0; hop < settings.getMaxHops() * 2 && current != null; hop++) {
        Absence next = AbsenceStore.find(commandContext, current);
        if (next == null || !next.overlaps(start, until)) {
          return;
        }
        if (userId.equals(next.getSubstituteId())) {
          throw new BadUserRequestException("The substitute '" + substituteId + "' is absent at the same time and "
              + "their substitutes lead back to '" + userId + "'; choose someone else.");
        }
        current = next.getSubstituteId();
      }
    }

    protected boolean tooLongForDb2(CommandContext commandContext, String value) {
      // DB2 counts VARCHAR lengths in bytes
      return "db2".equals(commandContext.getProcessEngineConfiguration().getDatabaseType())
          && value.getBytes(StandardCharsets.UTF_8).length > MAX_NOTE_LENGTH;
    }
  }

  /** Removes the absence of a user. */
  public static class ClearAbsenceCmd implements Command<Void> {
    protected final AbsenceSettings settings;
    protected final String userId;

    public ClearAbsenceCmd(AbsenceSettings settings, String userId) {
      this.settings = settings;
      this.userId = userId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
      requireUserId(userId);
      checkPermission(commandContext, Permissions.UPDATE, userId);
      if (storageReadable(commandContext, settings)) {
        AbsenceStore.delete(commandContext, userId);
      }
      return null;
    }
  }

  /** The active absences whose tasks go to a user now. */
  public static class GetSubstitutedAbsencesCmd implements Command<List<Absence>> {
    protected final AbsenceSettings settings;
    protected final String userId;

    public GetSubstitutedAbsencesCmd(AbsenceSettings settings, String userId) {
      this.settings = settings;
      this.userId = userId;
    }

    @Override
    public List<Absence> execute(CommandContext commandContext) {
      requireUserId(userId);
      checkPermission(commandContext, Permissions.READ, userId);
      List<Absence> result = new ArrayList<>();
      if (settings == null || !settings.isAvailable()) {
        return result;
      }
      Date now = ClockUtil.getCurrentTime();
      for (String absentUser : AbsenceStore.findUserIdsWithAbsence(commandContext)) {
        Absence absence = AbsenceStore.find(commandContext, absentUser);
        if (absence != null && absence.isActiveAt(now)
            && userId.equals(AbsenceResolver.follow(commandContext, settings.getMaxHops(), absentUser,
                absence.getSubstituteId(), now))) {
          result.add(absence);
        }
      }
      return result;
    }
  }

  /** The present substitute of a user who is absent now. */
  public static class ResolveSubstituteCmd implements Command<String> {
    protected final AbsenceSettings settings;
    protected final String userId;

    public ResolveSubstituteCmd(AbsenceSettings settings, String userId) {
      this.settings = settings;
      this.userId = userId;
    }

    @Override
    public String execute(CommandContext commandContext) {
      requireUserId(userId);
      checkPermission(commandContext, Permissions.READ, userId);
      if (settings == null || !settings.isAvailable()) {
        return null;
      }
      return AbsenceResolver.resolve(commandContext, settings.getMaxHops(), userId, ClockUtil.getCurrentTime());
    }
  }

  /**
   * Hands the active tasks of a user over to the substitute of their absence (also an upcoming
   * one). The tasks are those assigned to the user; the check is that the caller may manage the
   * absence of the user, not the permissions on each task. Each task is assigned like with the
   * task API (operation log entry "Assign" when a user is authenticated) and gets a comment and
   * the substitution marks.
   */
  public static class HandOverTasksCmd implements Command<Handover> {
    protected final AbsenceSettings settings;
    protected final String userId;
    protected final int maxTasks;

    public HandOverTasksCmd(AbsenceSettings settings, String userId, int maxTasks) {
      this.settings = settings;
      this.userId = userId;
      this.maxTasks = maxTasks;
    }

    @Override
    public Handover execute(CommandContext commandContext) {
      requireEnabled(settings);
      requireUserId(userId);
      checkPermission(commandContext, Permissions.UPDATE, userId);
      Date now = ClockUtil.getCurrentTime();
      Absence absence = AbsenceStore.find(commandContext, userId);
      if (absence == null || !now.before(absence.getUntil())) {
        throw new BadUserRequestException("User '" + userId + "' has no current or upcoming absence.");
      }
      String substitute = AbsenceResolver.follow(commandContext, settings.getMaxHops(), userId, absence.getSubstituteId(), now);
      if (substitute == null) {
        throw new BadUserRequestException("No present substitute can be found for '" + userId + "'.");
      }

      TaskQueryImpl all = new TaskQueryImpl();
      all.taskAssignee(userId);
      long assigned = withoutChecks(commandContext, () -> all.executeCount(commandContext));
      TaskQueryImpl active = new TaskQueryImpl();
      active.taskAssignee(userId).active().orderByTaskCreateTime().asc();
      // the task manager pages with the values of the query
      active.setFirstResult(0);
      active.setMaxResults(Math.max(1, maxTasks));
      List<Task> tasks = withoutChecks(commandContext, () -> active.executeList(commandContext, null));
      String message = "Handed over to " + substitute + " as substitute for " + userId + ", who is absent from "
          + absence.getFrom().toInstant() + " until " + absence.getUntil().toInstant() + ".";
      int count = 0;
      for (Task each : tasks) {
        TaskEntity task = (TaskEntity) each;
        SubstitutionRecorder.markSubstituted(task, userId, substitute);
        SubstitutionRecorder.comment(commandContext, task, message);
        task.setAssignee(substitute);
        task.triggerUpdateEvent();
        task.logUserOperation(UserOperationLogEntry.OPERATION_TYPE_ASSIGN);
        count++;
      }
      return new Handover(substitute, count, Math.max(0, assigned - count));
    }

    protected <T> T withoutChecks(CommandContext commandContext, Callable<T> callable) {
      boolean tenantCheck = commandContext.isTenantCheckEnabled();
      commandContext.disableTenantCheck();
      try {
        return commandContext.runWithoutAuthorization(callable);
      } finally {
        if (tenantCheck) {
          commandContext.enableTenantCheck();
        }
      }
    }
  }
}
