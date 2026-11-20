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
package dev.fasti.spring.boot.observability;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics of the process engine published to Micrometer ({@code fasti.metrics.engine.*}).
 */
@ConfigurationProperties(FastiEngineMetricsProperties.PREFIX)
public class FastiEngineMetricsProperties {

  public static final String PREFIX = "fasti.metrics.engine";

  /**
   * Whether the counters and gauges of the process engine are published to Micrometer.
   */
  private boolean enabled = true;

  private final Gauges gauges = new Gauges();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Gauges getGauges() {
    return gauges;
  }

  public static class Gauges {

    /**
     * Whether the gauges that count rows in the database (running process instances, open
     * tasks, incidents, jobs and external tasks) are published.
     */
    private boolean enabled = true;

    /**
     * How long the values of the gauges are reused before the database is queried again.
     */
    private Duration refreshInterval = Duration.ofSeconds(30);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getRefreshInterval() {
      return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
      this.refreshInterval = refreshInterval;
    }
  }
}
