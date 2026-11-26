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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Answers of costly reads for a short time, and one read for the callers that ask the same at the
 * same time. The key holds everything an answer depends on; an answer is shared between the
 * callers that ask the same, so it must not be changed.
 *
 * <p>At most {@code maxEntries} answers are kept. When a new answer comes to a full cache, the
 * expired answers are removed first, then the oldest ones, so that one burst of different
 * questions does not drop everything else.</p>
 *
 * <p>Callers that ask the same while it is being read wait for that read instead of reading it
 * too, also with the cache turned off and for answers that are not kept.</p>
 */
public final class ReadCache {

  private final int maxEntries;
  private final Duration maxTimeToLive;
  private final Supplier<Duration> longestWait;
  private final String subject;
  private final Function<String, ? extends RuntimeException> busy;
  private volatile Duration timeToLive;
  private final Map<Object, Entry> entries = new ConcurrentHashMap<>();
  private final Map<Object, CompletableFuture<Object>> reading = new ConcurrentHashMap<>();
  private final Object writeLock = new Object();

  /**
   * @param timeToLive how long an answer is kept; zero turns keeping off
   * @param maxTimeToLive the longest time to live {@link #setTimeToLive} accepts
   * @param maxEntries how many answers are kept at most
   * @param longestWait the longest wait for the read of another caller
   * @param subject what is read, for the messages ("analytics are", "workload is")
   * @param busy the failure of a caller that waited that long, from its message
   */
  public ReadCache(Duration timeToLive, Duration maxTimeToLive, int maxEntries, Supplier<Duration> longestWait,
      String subject, Function<String, ? extends RuntimeException> busy) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    this.maxEntries = maxEntries;
    this.maxTimeToLive = maxTimeToLive;
    this.longestWait = longestWait;
    this.subject = subject;
    this.busy = busy;
    setTimeToLive(timeToLive);
  }

  private static final class Entry {
    final long stored;
    final long expires;
    final Object value;

    Entry(long stored, long expires, Object value) {
      this.stored = stored;
      this.expires = expires;
      this.value = value;
    }

    boolean expired(long now) {
      return expires - now <= 0;
    }
  }

  public Duration getTimeToLive() {
    return timeToLive;
  }

  /** The time to live of answers kept from now on; zero or negative turns keeping off and empties the cache. */
  public void setTimeToLive(Duration ttl) {
    timeToLive = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl.compareTo(maxTimeToLive) > 0 ? maxTimeToLive : ttl;
    if (timeToLive.isZero()) {
      entries.clear();
    }
  }

  public void clear() {
    entries.clear();
  }

  /** The number of answers kept, expired ones included until they are removed. */
  public int size() {
    return entries.size();
  }

  /**
   * The kept answer, or the answer of the same read of another caller, or a read now.
   *
   * @param keep whether the answer may be kept and given to later callers; when not, the answer is
   *        only shared with the callers that ask the same while it is being read
   */
  @SuppressWarnings("unchecked")
  public <T> T get(Object key, boolean keep, Supplier<T> compute) {
    T cached = keep ? cached(key) : null;
    if (cached != null) {
      return cached;
    }
    CompletableFuture<Object> mine = new CompletableFuture<>();
    CompletableFuture<Object> other = reading.putIfAbsent(key, mine);
    if (other != null) {
      return (T) await(other);
    }
    try {
      // a read that ended between the look into the cache and taking the key
      T value = keep ? cached(key) : null;
      if (value == null) {
        value = compute.get();
        if (keep) {
          put(key, value);
        }
      }
      mine.complete(value);
      return value;
    } catch (RuntimeException | Error e) {
      mine.completeExceptionally(e);
      throw e;
    } finally {
      reading.remove(key, mine);
    }
  }

  /**
   * The kept answer, or a read now without waiting for the same read of another caller: for a
   * caller inside a transaction of the engine, which would hold its connection while it waits.
   */
  public <T> T getWithoutWaiting(Object key, boolean keep, Supplier<T> compute) {
    T cached = keep ? cached(key) : null;
    if (cached != null) {
      return cached;
    }
    T value = compute.get();
    if (keep) {
      put(key, value);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private <T> T cached(Object key) {
    if (timeToLive.isZero()) {
      return null;
    }
    Entry entry = entries.get(key);
    if (entry == null) {
      return null;
    }
    if (entry.expired(System.nanoTime())) {
      entries.remove(key, entry);
      return null;
    }
    return (T) entry.value;
  }

  private Object await(CompletableFuture<Object> other) {
    Duration wait = longestWait.get();
    try {
      return other.get(wait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw busy.apply("The same " + subject + " being read for longer than " + wait.getSeconds() + " s. Ask again later.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw busy.apply("Interrupted while waiting for the same read");
    } catch (ExecutionException | CompletionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new IllegalStateException(cause);
    }
  }

  /**
   * Keeps an answer. Writers take turns, so that the cache never holds more than
   * {@code maxEntries}; readers do not wait for them.
   */
  private void put(Object key, Object value) {
    Duration ttl = timeToLive;
    if (ttl.isZero() || value == null) {
      return;
    }
    synchronized (writeLock) {
      long now = System.nanoTime();
      if (!entries.containsKey(key) && entries.size() >= maxEntries) {
        entries.values().removeIf(entry -> entry.expired(now));
        while (entries.size() >= maxEntries && removeOldest()) {
          // one at a time: a few hundred entries at most
        }
      }
      entries.put(key, new Entry(now, now + ttl.toNanos(), value));
    }
  }

  private boolean removeOldest() {
    Object oldestKey = null;
    Entry oldest = null;
    for (Map.Entry<Object, Entry> candidate : entries.entrySet()) {
      if (oldest == null || candidate.getValue().stored - oldest.stored < 0) {
        oldestKey = candidate.getKey();
        oldest = candidate.getValue();
      }
    }
    if (oldestKey == null) {
      return false;
    }
    // false only when a reader removed it as expired meanwhile: it is gone either way
    entries.remove(oldestKey, oldest);
    return true;
  }
}
