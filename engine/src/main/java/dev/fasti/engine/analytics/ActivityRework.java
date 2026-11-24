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

/**
 * Rework at one activity: scopes (process or subprocess instances) that ran it more than once in
 * the range, and the runs beyond the first. Gateways, start, end and boundary events and the
 * iterations of a multi-instance activity are not counted.
 */
public class ActivityRework {

  protected final String activityId;
  protected final long repeatedInstances;
  protected final long repeatedExecutions;

  public ActivityRework(String activityId, long repeatedInstances, long repeatedExecutions) {
    this.activityId = activityId;
    this.repeatedInstances = repeatedInstances;
    this.repeatedExecutions = repeatedExecutions;
  }

  public String getActivityId() {
    return activityId;
  }

  public long getRepeatedInstances() {
    return repeatedInstances;
  }

  public long getRepeatedExecutions() {
    return repeatedExecutions;
  }
}
