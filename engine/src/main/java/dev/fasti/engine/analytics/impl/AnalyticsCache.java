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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Answers of the analytics statements for a short time: the same view opened again, by the same
 * or another user, within the time to live is answered without reading the history again.
 *
 * <p>The key holds everything the answer depends on, including the tenants the user may read; the
 * permission is checked on every call before the cache is asked. An answer is shared between the
 * callers that ask the same, so it must not be changed.</p>
 */
public final class AnalyticsCache {

  /** {@code fasti.analytics.cacheTtlSeconds}: the time to live in seconds; 0 turns the cache off. */
  public static final String TTL_PROPERTY = "fasti.analytics.cacheTtlSeconds";
  static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
  static final int MAX_ENTRIES = 256;

  private static volatile Duration timeToLive = initialTimeToLive();
  private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

  private AnalyticsCache() {
  }

  private static final class Entry {
    final long expires;
    final Object value;

    Entry(long expires, Object value) {
      this.expires = expires;
      this.value = value;
    }
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
    return timeToLive;
  }

  /** Sets the time to live of answers given from now on; zero or negative turns the cache off and empties it. */
  public static void setTimeToLive(Duration ttl) {
    timeToLive = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
    if (timeToLive.isZero()) {
      ENTRIES.clear();
    }
  }

  public static void clear() {
    ENTRIES.clear();
  }

  @SuppressWarnings("unchecked")
  static <T> T get(String key, Supplier<T> compute) {
    Duration ttl = timeToLive;
    if (ttl.isZero()) {
      return compute.get();
    }
    long now = System.nanoTime();
    Entry entry = ENTRIES.get(key);
    if (entry != null && entry.expires - now > 0) {
      return (T) entry.value;
    }
    T value = compute.get();
    if (ENTRIES.size() >= MAX_ENTRIES) {
      ENTRIES.values().removeIf(old -> old.expires - now <= 0);
      if (ENTRIES.size() >= MAX_ENTRIES) {
        ENTRIES.clear();
      }
    }
    ENTRIES.put(key, new Entry(now + ttl.toNanos(), value));
    return value;
  }
}
