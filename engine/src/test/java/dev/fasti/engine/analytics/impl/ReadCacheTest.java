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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

/** What the cache keeps, what it lets go first when it is full, and what it never keeps. */
public class ReadCacheTest {

  protected final AtomicInteger reads = new AtomicInteger();
  protected final ExecutorService threads = Executors.newFixedThreadPool(4);

  @After
  public void stopThreads() throws InterruptedException {
    threads.shutdownNow();
    threads.awaitTermination(10, TimeUnit.SECONDS);
  }

  static ReadCache cache(Duration ttl, int maxEntries) {
    return new ReadCache(ttl, Duration.ofDays(1), maxEntries, () -> Duration.ofSeconds(30), "test is",
        IllegalStateException::new);
  }

  String read(ReadCache cache, String key) {
    return cache.get(key, true, () -> key + reads.incrementAndGet());
  }

  @Test
  public void shouldLetTheOldestGoWhenFullInsteadOfEverything() {
    ReadCache cache = cache(Duration.ofMinutes(1), 3);
    String a = read(cache, "a");
    String b = read(cache, "b");
    String c = read(cache, "c");
    String d = read(cache, "d");

    assertThat(cache.size()).isEqualTo(3);
    assertThat(read(cache, "b")).isEqualTo(b);
    assertThat(read(cache, "c")).isEqualTo(c);
    assertThat(read(cache, "d")).isEqualTo(d);
    assertThat(reads.get()).isEqualTo(4);
    // a was the oldest: read again, and b, now the oldest, makes room for it
    assertThat(read(cache, "a")).isNotEqualTo(a);
    assertThat(reads.get()).isEqualTo(5);
    assertThat(cache.size()).isEqualTo(3);
    assertThat(read(cache, "c")).isEqualTo(c);
    assertThat(reads.get()).isEqualTo(5);
  }

  @Test
  public void shouldLetTheExpiredGoBeforeTheOldest() throws InterruptedException {
    ReadCache cache = cache(Duration.ofMillis(50), 3);
    String a = read(cache, "a");
    cache.setTimeToLive(Duration.ofMinutes(1));
    String b = read(cache, "b");
    String c = read(cache, "c");
    Thread.sleep(150);
    String d = read(cache, "d");

    assertThat(cache.size()).isEqualTo(3);
    assertThat(read(cache, "b")).isEqualTo(b);
    assertThat(read(cache, "c")).isEqualTo(c);
    assertThat(read(cache, "d")).isEqualTo(d);
    assertThat(read(cache, "a")).isNotEqualTo(a);
  }

  @Test
  public void shouldNotKeepWhatIsNotToBeKept() {
    ReadCache cache = cache(Duration.ofMinutes(1), 3);
    assertThat(cache.get("now", false, () -> "first")).isEqualTo("first");
    assertThat(cache.get("now", false, () -> "second")).isEqualTo("second");
    assertThat(cache.getWithoutWaiting("now", false, () -> "third")).isEqualTo("third");
    assertThat(cache.size()).isZero();
    // nor does it answer from what was kept for the same key
    cache.get("now", true, () -> "kept");
    assertThat(cache.get("now", false, () -> "fresh")).isEqualTo("fresh");
  }

  @Test
  public void shouldShareOneReadAlsoForWhatIsNotKept() throws Exception {
    ReadCache cache = cache(Duration.ofMinutes(1), 3);
    CountDownLatch release = new CountDownLatch(1);
    List<Future<String>> answers = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      answers.add(threads.submit(() -> cache.get("now", false, () -> {
        reads.incrementAndGet();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return "answer";
      })));
    }
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (reads.get() == 0 && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    Thread.sleep(100);
    release.countDown();
    for (Future<String> answer : answers) {
      assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo("answer");
    }
    assertThat(reads.get()).isEqualTo(1);
    assertThat(cache.size()).isZero();
  }

  @Test
  public void shouldStayWithinItsSizeWhenWrittenFromManyThreads() throws Exception {
    ReadCache cache = cache(Duration.ofMinutes(1), 16);
    List<Future<?>> writers = new ArrayList<>();
    for (int thread = 0; thread < 4; thread++) {
      int offset = thread * 1000;
      writers.add(threads.submit(() -> {
        for (int index = 0; index < 500; index++) {
          read(cache, "key" + (offset + index));
        }
      }));
    }
    for (Future<?> writer : writers) {
      writer.get(30, TimeUnit.SECONDS);
    }
    assertThat(cache.size()).isEqualTo(16);
  }
}
