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

import java.util.Date;
import java.util.List;

import dev.fasti.engine.absence.Absence;
import dev.fasti.engine.absence.AbsenceService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;

/** Runs the absence commands on an engine. */
public class AbsenceServiceImpl implements AbsenceService {

  protected final AbsenceSettings settings;
  protected final CommandExecutor commandExecutor;

  public AbsenceServiceImpl(ProcessEngineConfigurationImpl configuration) {
    this.settings = AbsenceSettings.of(configuration);
    this.commandExecutor = configuration.getCommandExecutorTxRequired();
  }

  @Override
  public boolean isEnabled() {
    return settings != null && settings.isAvailable();
  }

  @Override
  public String getUnavailableReason() {
    if (settings == null) {
      return "Absence substitution is not enabled on this process engine.";
    }
    return settings.getUnavailableReason();
  }

  @Override
  public Absence getAbsence(String userId) {
    return commandExecutor.execute(new AbsenceCommands.GetAbsenceCmd(settings, userId));
  }

  @Override
  public Absence setAbsence(String userId, Date from, Date until, String substituteId, String note) {
    return commandExecutor.execute(new AbsenceCommands.SetAbsenceCmd(settings, userId, from, until, substituteId, note));
  }

  @Override
  public void clearAbsence(String userId) {
    commandExecutor.execute(new AbsenceCommands.ClearAbsenceCmd(settings, userId));
  }

  @Override
  public List<Absence> getAbsencesSubstitutedBy(String userId) {
    return commandExecutor.execute(new AbsenceCommands.GetSubstitutedAbsencesCmd(settings, userId));
  }

  @Override
  public String resolveSubstitute(String userId) {
    return commandExecutor.execute(new AbsenceCommands.ResolveSubstituteCmd(settings, userId));
  }

  @Override
  public Handover handOverTasks(String userId, int maxTasks) {
    return commandExecutor.execute(new AbsenceCommands.HandOverTasksCmd(settings, userId, maxTasks));
  }
}
