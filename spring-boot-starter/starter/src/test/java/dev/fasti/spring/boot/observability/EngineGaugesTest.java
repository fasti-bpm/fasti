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
package dev.fasti.spring.boot.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.camunda.bpm.engine.ProcessEngine;
import org.junit.Before;
import org.junit.Test;

/** Reading a gauge returns the values read last and leaves the count queries to a background thread. */
public class EngineGaugesTest {

  static final String NAME = "fasti.engine.test";

  final ProcessEngine engine = mock(ProcessEngine.class);
  final AtomicLong now = new AtomicLong(1);
  final AtomicInteger rounds = new AtomicInteger();
  final Queue<Runnable> background = new ArrayDeque<>();

  @Before
  public void nameEngine() {
    when(engine.getName()).thenReturn("gauges");
  }

  /** Gauges with one query; background work is queued and run by the test. */
  EngineGauges gauges(Duration interval, Runnable query) {
    EngineGauges gauges = new EngineGauges(engine, interval, now::get, background::add);
    gauges.queries.clear();
    gauges.descriptions.clear();
    gauges.add(NAME, "A test value", processEngine -> {
      query.run();
      return rounds.incrementAndGet();
    });
    return gauges;
  }

  void runBackground() {
    Runnable task;
    while ((task = background.poll()) != null) {
      task.run();
    }
  }

  @Test
  public void shouldNotQueryInTheReadingThread() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> { });

    assertThat(gauges.value(NAME)).isNaN();
    assertThat(rounds).hasValue(0);
    assertThat(background).hasSize(1);

    runBackground();
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(background).isEmpty();
  }

  @Test
  public void shouldKeepTheValuesForTheInterval() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> { });
    gauges.value(NAME);
    runBackground();

    now.addAndGet(Duration.ofSeconds(29).toNanos());
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(background).isEmpty();

    // stale: the read returns the last value and starts one round, however often it is read
    now.addAndGet(Duration.ofSeconds(1).toNanos());
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(background).hasSize(1);

    runBackground();
    assertThat(gauges.value(NAME)).isEqualTo(2);
    assertThat(rounds).hasValue(2);
  }

  @Test
  public void shouldStartTheFirstRoundWhenRegistered() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> { });
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    gauges.bindTo(registry);
    assertThat(background).hasSize(1);
    runBackground();
    assertThat(registry.get(NAME).tag("engine", "gauges").gauge().value()).isEqualTo(1);
    assertThat(rounds).hasValue(1);
  }

  @Test
  public void shouldNotWaitForASlowQuery() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    EngineGauges gauges = new EngineGauges(engine, Duration.ofSeconds(30), now::get, EngineGauges.backgroundExecutor("gauges"));
    gauges.queries.clear();
    gauges.add(NAME, "A slow value", processEngine -> {
      started.countDown();
      try {
        release.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return rounds.incrementAndGet();
    });

    long before = System.nanoTime();
    assertThat(gauges.value(NAME)).isNaN();
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    // while the query hangs, reads return at once and start no second round
    for (int i = 0; i < 100; i++) {
      assertThat(gauges.value(NAME)).isNaN();
    }
    assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(10));

    release.countDown();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (Double.isNaN(gauges.value(NAME)) && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(rounds).hasValue(1);
  }

  @Test
  public void shouldNotStartASecondRoundForAReadThatSawStaleValues() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> { });
    gauges.value(NAME);
    Runnable first = background.poll();
    // a second reader found the values stale too, but got the flag only after the first round
    first.run();
    gauges.refreshInBackground();
    runBackground();
    assertThat(rounds).hasValue(1);
    assertThat(gauges.value(NAME)).isEqualTo(1);
  }

  @Test
  public void shouldKeepTheLastValuesWhenClosed() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> { });
    gauges.value(NAME);
    runBackground();
    gauges.close();
    now.addAndGet(Duration.ofMinutes(5).toNanos());
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(background).isEmpty();
    assertThat(rounds).hasValue(1);

    EngineGauges synchronous = gauges(Duration.ZERO, () -> { });
    synchronous.close();
    assertThat(synchronous.value(NAME)).isNaN();
    assertThat(rounds).hasValue(1);
  }

  @Test
  public void shouldReadOnEveryReadWithoutInterval() {
    EngineGauges gauges = gauges(Duration.ZERO, () -> { });
    assertThat(gauges.value(NAME)).isEqualTo(1);
    assertThat(gauges.value(NAME)).isEqualTo(2);
    assertThat(background).isEmpty();
  }

  @Test
  public void shouldReportNaNForAFailingQuery() {
    EngineGauges gauges = gauges(Duration.ofSeconds(30), () -> {
      throw new IllegalStateException("no database");
    });
    gauges.value(NAME);
    runBackground();
    assertThat(gauges.value(NAME)).isNaN();
    // the failed round counts: the next one starts after the interval
    assertThat(background).isEmpty();
  }
}
