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

  /**
   * How many analytics reads of the history run at the same time on this node (2 by default);
   * the others wait for their turn without a database connection. 0 or less: no limit.
   */
  public static void setMaxConcurrent(int maxConcurrent) {
    dev.fasti.engine.analytics.impl.AnalyticsLimiter.setMaxConcurrent(maxConcurrent);
  }

  public static int getMaxConcurrent() {
    return dev.fasti.engine.analytics.impl.AnalyticsLimiter.getMaxConcurrent();
  }

  /** The longest wait for a turn (60 s by default); then the query fails with {@link AnalyticsBusyException}. */
  public static void setMaxWait(java.time.Duration maxWait) {
    dev.fasti.engine.analytics.impl.AnalyticsLimiter.setMaxWait(maxWait);
  }

  public static java.time.Duration getMaxWait() {
    return dev.fasti.engine.analytics.impl.AnalyticsLimiter.getMaxWait();
  }

  /**
   * How long one analytics statement may run (120 s by default, at most a day); then the query
   * fails with {@link AnalyticsTimeoutException}, and the turn and the connection are free again.
   * {@link java.time.Duration#ZERO}: no timeout of its own (the {@code jdbcStatementTimeout} of the
   * engine, if set, still applies; a lower one of those always does).
   */
  public static void setStatementTimeout(java.time.Duration statementTimeout) {
    dev.fasti.engine.analytics.impl.AnalyticsStatementTimeout.setTimeout(statementTimeout);
  }

  public static java.time.Duration getStatementTimeout() {
    return dev.fasti.engine.analytics.impl.AnalyticsStatementTimeout.getTimeout();
  }

  public static ProcessAnalyticsQuery query(ProcessEngine processEngine) {
    return new ProcessAnalyticsQueryImpl((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration());
  }
}
