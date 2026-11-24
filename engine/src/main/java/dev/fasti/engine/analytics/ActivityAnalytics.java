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

/** What happened at one activity of a process: how often, how long, what is waiting there now. */
public class ActivityAnalytics {

  protected String activityId;
  protected String activityType;
  protected String activityName;
  protected long finished;
  protected long canceled;
  protected long running;
  protected long runningOverP90;
  protected Date oldestRunningStart;
  protected long repeatedInstances;
  protected long repeatedExecutions;
  protected long openIncidents;
  protected long incidents;
  protected Long timeAboveMedian;
  protected DurationStatistics durations;

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public String getActivityType() {
    return activityType;
  }

  public void setActivityType(String activityType) {
    this.activityType = activityType;
  }

  public String getActivityName() {
    return activityName;
  }

  public void setActivityName(String activityName) {
    this.activityName = activityName;
  }

  /** Finished in the range, not canceled. */
  public long getFinished() {
    return finished;
  }

  public void setFinished(long finished) {
    this.finished = finished;
  }

  /** Canceled in the range, for example by a boundary event or a modification. */
  public long getCanceled() {
    return canceled;
  }

  public void setCanceled(long canceled) {
    this.canceled = canceled;
  }

  /** Running now, whatever the range. */
  public long getRunning() {
    return running;
  }

  public void setRunning(long running) {
    this.running = running;
  }

  /** Running now for longer than nine in ten finished ones took (the P90 of the range). */
  public long getRunningOverP90() {
    return runningOverP90;
  }

  public void setRunningOverP90(long runningOverP90) {
    this.runningOverP90 = runningOverP90;
  }

  public Date getOldestRunningStart() {
    return oldestRunningStart;
  }

  public void setOldestRunningStart(Date oldestRunningStart) {
    this.oldestRunningStart = oldestRunningStart;
  }

  /** Scopes (process or subprocess instances) that ran the activity more than once in the range: rework or loops. */
  public long getRepeatedInstances() {
    return repeatedInstances;
  }

  public void setRepeatedInstances(long repeatedInstances) {
    this.repeatedInstances = repeatedInstances;
  }

  /** The runs beyond the first in those scopes. */
  public long getRepeatedExecutions() {
    return repeatedExecutions;
  }

  public void setRepeatedExecutions(long repeatedExecutions) {
    this.repeatedExecutions = repeatedExecutions;
  }

  /** Incidents open now. */
  public long getOpenIncidents() {
    return openIncidents;
  }

  public void setOpenIncidents(long openIncidents) {
    this.openIncidents = openIncidents;
  }

  /** Incidents created in the range. */
  public long getIncidents() {
    return incidents;
  }

  public void setIncidents(long incidents) {
    this.incidents = incidents;
  }

  /** The time the finished runs spent beyond the median, together: what speeding up the slow ones would save. */
  public Long getTimeAboveMedian() {
    return timeAboveMedian;
  }

  public void setTimeAboveMedian(Long timeAboveMedian) {
    this.timeAboveMedian = timeAboveMedian;
  }

  /** Durations of the runs finished in the range, canceled ones not included. */
  public DurationStatistics getDurations() {
    return durations;
  }

  public void setDurations(DurationStatistics durations) {
    this.durations = durations;
  }
}
