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
package dev.fasti.engine.analytics.impl;

import java.util.Date;

/** The rows of the analytics statements; MyBatis fills the fields. */
public final class AnalyticsRows {

  private AnalyticsRows() {
  }

  /** Durations of one key (activity, period) in one bucket. */
  public static class BucketRow {
    String key;
    int bucket;
    long count;
    long total;
    Long minimum;
    Long maximum;
    Long withinTarget;
  }

  /** Counts of one activity. */
  public static class ActivityCountRow {
    String activityId;
    String activityType;
    String activityName;
    long finished;
    long canceled;
    long running;
    Date oldestRunning;
  }

  /** Two counts of one key. */
  public static class KeyCountRow {
    String key;
    long count;
    long extra;
  }

  /** Counts of the process instances. */
  public static class InstanceCountRow {
    long completed;
    long terminated;
    long running;
  }

  /** One completed user task. */
  public static class UserTaskRow {
    String taskDefinitionKey;
    String name;
    String assignee;
    Date startTime;
    Date endTime;
    Date firstAssignment;
  }
}
