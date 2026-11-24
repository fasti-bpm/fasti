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
package dev.fasti.engine.analytics;

import dev.fasti.engine.analytics.impl.ProcessAnalyticsQueryImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/** Entry point to the process analytics of a process engine. */
public final class ProcessAnalytics {

  private ProcessAnalytics() {
  }

  /** How long answers are kept for repeated queries; {@link java.time.Duration#ZERO} turns this off. */
  public static void setCacheTimeToLive(java.time.Duration timeToLive) {
    dev.fasti.engine.analytics.impl.AnalyticsCache.setTimeToLive(timeToLive);
  }

  public static java.time.Duration getCacheTimeToLive() {
    return dev.fasti.engine.analytics.impl.AnalyticsCache.getTimeToLive();
  }

  public static ProcessAnalyticsQuery query(ProcessEngine processEngine) {
    return new ProcessAnalyticsQueryImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
