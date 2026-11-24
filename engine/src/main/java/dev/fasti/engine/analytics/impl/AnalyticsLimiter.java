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

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.fasti.engine.analytics.AnalyticsBusyException;

/**
 * How many analytics reads of the history run at the same time on this node. A read of a long
 * range keeps a database connection and a few database CPUs busy for seconds; without a limit, a
 * few people looking at a year of history take the connections and the CPU the engine needs for
 * its jobs and tasks. Reads above the limit wait for their turn, in order, without holding a
 * connection, and give up after the longest wait.
 *
 * <p>The scans of the activities in the range (activities, rework) and the other reads (instances,
 * trend, runs, user tasks: smaller, or limited in SQL) take turns in two lines with the same
 * limit each, so the quick parts of a view do not wait behind the scans of the same view.</p>
 */
public final class AnalyticsLimiter {

  /** {@code fasti.analytics.maxConcurrent}: reads at the same time; 0 or less: no limit. */
  public static final String MAX_CONCURRENT_PROPERTY = "fasti.analytics.maxConcurrent";
  /** {@code fasti.analytics.maxWaitSeconds}: the longest wait for a turn. */
  public static final String MAX_WAIT_PROPERTY = "fasti.analytics.maxWaitSeconds";
  static final int DEFAULT_MAX_CONCURRENT = 2;
  static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(60);

  private static volatile int maxConcurrent = initial(MAX_CONCURRENT_PROPERTY, DEFAULT_MAX_CONCURRENT);
  private static volatile Semaphore scans = semaphore(maxConcurrent);
  private static volatile Semaphore others = semaphore(maxConcurrent);
  private static volatile Duration maxWait = Duration.ofSeconds(
      Math.max(0, initial(MAX_WAIT_PROPERTY, (int) DEFAULT_MAX_WAIT.getSeconds())));

  private AnalyticsLimiter() {
  }

  private static int initial(String property, int fallback) {
    String value = System.getProperty(property);
    if (value == null || value.trim().isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static Semaphore semaphore(int max) {
    return max > 0 ? new Semaphore(max, true) : null;
  }

  public static int getMaxConcurrent() {
    return maxConcurrent;
  }

  /** Reads from now on; those running keep their turn. 0 or less: no limit. */
  public static synchronized void setMaxConcurrent(int max) {
    int value = Math.max(0, max);
    if (value == maxConcurrent && (scans != null) == (value > 0)) {
      return;
    }
    maxConcurrent = value;
    scans = semaphore(value);
    others = semaphore(value);
  }

  public static Duration getMaxWait() {
    return maxWait;
  }

  /** At most a day. */
  public static void setMaxWait(Duration wait) {
    maxWait = wait == null || wait.isNegative() ? Duration.ZERO : wait.compareTo(MAX) > 0 ? MAX : wait;
  }

  static final Duration MAX = Duration.ofDays(1);

  /** Runs the read when its turn comes; {@code scan}: a scan of the activities in the range. */
  static <T> T run(boolean scan, Supplier<T> read) {
    Semaphore semaphore = scan ? scans : others;
    if (semaphore == null) {
      return read.get();
    }
    Duration wait = maxWait;
    boolean acquired;
    try {
      acquired = semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AnalyticsBusyException("Interrupted while waiting for a turn to read the analytics");
    }
    if (!acquired) {
      throw new AnalyticsBusyException("The analytics of this node are busy: " + semaphore.getQueueLength()
          + " reads wait after " + maxConcurrent + " running; no turn within " + wait.getSeconds()
          + " s. Ask again later.");
    }
    try {
      return read.get();
    } finally {
      semaphore.release();
    }
  }
}
