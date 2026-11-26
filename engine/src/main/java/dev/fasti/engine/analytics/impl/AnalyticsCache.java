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
import java.util.function.Supplier;

import dev.fasti.engine.analytics.AnalyticsBusyException;

/**
 * Answers of the analytics statements for a short time: the same view opened again, by the same
 * or another user, within the time to live is answered without reading the history again.
 *
 * <p>The key holds everything the answer depends on, including the tenants the user may read; the
 * permission is checked on every call before the cache is asked. An answer is shared between the
 * callers that ask the same, so it must not be changed. See {@link ReadCache}.</p>
 */
public final class AnalyticsCache {

  /** {@code fasti.analytics.cacheTtlSeconds}: the time to live in seconds; 0 turns the cache off. */
  public static final String TTL_PROPERTY = "fasti.analytics.cacheTtlSeconds";
  static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
  static final int MAX_ENTRIES = 256;
  /** The longest wait for the same read: its turn, plus as long as a read may take. */
  static final Duration READ_TIME = Duration.ofMinutes(5);

  private static final ReadCache CACHE = new ReadCache(initialTimeToLive(), AnalyticsLimiter.MAX, MAX_ENTRIES,
      () -> AnalyticsLimiter.getMaxWait().plus(READ_TIME), "analytics are", AnalyticsBusyException::new);

  private AnalyticsCache() {
  }

  private static Duration initialTimeToLive() {
    String value = System.getProperty(TTL_PROPERTY);
    if (value == null || value.trim().isEmpty()) {
      return DEFAULT_TTL;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    } catch (NumberFormatException e) {
      return DEFAULT_TTL;
    }
  }

  public static Duration getTimeToLive() {
    return CACHE.getTimeToLive();
  }

  /** Sets the time to live of answers given from now on; zero or negative turns the cache off and empties it. */
  public static void setTimeToLive(Duration ttl) {
    CACHE.setTimeToLive(ttl);
  }

  public static void clear() {
    CACHE.clear();
  }

  static int size() {
    return CACHE.size();
  }

  static <T> T get(Object key, Supplier<T> compute) {
    return CACHE.get(key, true, compute);
  }

  /** {@code keep}: whether the answer may be given to later callers, see {@link ReadCache#get}. */
  static <T> T get(Object key, boolean keep, Supplier<T> compute) {
    return CACHE.get(key, keep, compute);
  }

  static <T> T getWithoutWaiting(Object key, Supplier<T> compute) {
    return CACHE.getWithoutWaiting(key, true, compute);
  }

  static <T> T getWithoutWaiting(Object key, boolean keep, Supplier<T> compute) {
    return CACHE.getWithoutWaiting(key, keep, compute);
  }
}
