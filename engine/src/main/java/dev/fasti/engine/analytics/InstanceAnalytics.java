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

/** The process instances of the range: how many ended which way, and how long the completed ones took. */
public class InstanceAnalytics {

  protected long completed;
  protected long terminated;
  protected long running;
  protected DurationStatistics durations;

  public InstanceAnalytics(long completed, long terminated, long running, DurationStatistics durations) {
    this.completed = completed;
    this.terminated = terminated;
    this.running = running;
    this.durations = durations;
  }

  /** Completed through an end event in the range. */
  public long getCompleted() {
    return completed;
  }

  /** Terminated in the range, internally (terminate end event) or externally (canceled). */
  public long getTerminated() {
    return terminated;
  }

  /** Running now, whatever the range. */
  public long getRunning() {
    return running;
  }

  /** Durations of the instances completed in the range. */
  public DurationStatistics getDurations() {
    return durations;
  }
}
