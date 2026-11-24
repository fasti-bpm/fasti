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

import java.util.Date;

/** One period of a trend: instances started and completed, and how long the completed ones took. */
public class PeriodAnalytics {

  protected final Date start;
  protected final Date end;
  protected final long started;
  protected final DurationStatistics durations;

  public PeriodAnalytics(Date start, Date end, long started, DurationStatistics durations) {
    this.start = start;
    this.end = end;
    this.started = started;
    this.durations = durations;
  }

  public Date getStart() {
    return start;
  }

  /** Exclusive. */
  public Date getEnd() {
    return end;
  }

  public long getStarted() {
    return started;
  }

  /** Durations of the instances completed in the period; their count is the number completed. */
  public DurationStatistics getDurations() {
    return durations;
  }
}
