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

import java.util.List;

import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/**
 * Settings of the absence plugin of one engine. The plugin registers them with its parse
 * listener, where the service finds them again; without the plugin there are none.
 */
public class AbsenceSettings {

  public static final int DEFAULT_MAX_HOPS = 5;

  protected final int maxHops;
  protected final boolean substituteCandidates;
  protected volatile String unavailableReason;

  public AbsenceSettings(int maxHops, boolean substituteCandidates) {
    this.maxHops = maxHops;
    this.substituteCandidates = substituteCandidates;
  }

  /** The settings of the plugin registered with the engine, or {@code null}. */
  public static AbsenceSettings of(ProcessEngineConfigurationImpl configuration) {
    List<BpmnParseListener> listeners = configuration.getCustomPreBPMNParseListeners();
    if (listeners != null) {
      for (BpmnParseListener listener : listeners) {
        if (listener instanceof AbsenceBpmnParseListener) {
          return ((AbsenceBpmnParseListener) listener).getSettings();
        }
      }
    }
    return null;
  }

  public int getMaxHops() {
    return maxHops;
  }

  public boolean isSubstituteCandidates() {
    return substituteCandidates;
  }

  public boolean isAvailable() {
    return unavailableReason == null;
  }

  public String getUnavailableReason() {
    return unavailableReason;
  }

  public void markUnavailable(String reason) {
    this.unavailableReason = reason;
  }
}
