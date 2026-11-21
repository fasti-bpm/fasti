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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gauges with the current state of an engine: running process instances, open user tasks,
 * open incidents, due and failed jobs, open external tasks.
 * <p>
 * Each value is a count query on the database. Reading a gauge never runs a query: it returns
 * the values read last. When they are older than the refresh interval, reading a gauge starts
 * one round of queries on a background thread, and the following reads get its result; so a
 * scrape does not wait for the database, and frequent scrapes or several registries cause at
 * most one round of queries per interval. The first round starts when the gauges are
 * registered; after it, an engine nobody scrapes is not queried. A value is therefore up to one
 * refresh interval plus one scrape interval old, and {@code NaN} until the first round has
 * finished. A round that has been running for more than {@value #HUNG_ROUND_INTERVALS} refresh
 * intervals (a count query that waits for a lock or on a dead connection) turns every value
 * into {@code NaN} until it ends, so that stale values do not pass for current ones. With a
 * refresh interval of zero the values are read on every read of a gauge, in the reading thread
 * (for tests). The queries run without an authenticated user and therefore see the whole engine.
 */
public class EngineGauges {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGauges.class);

  /** After how many refresh intervals a running round counts as hung and the values as unknown. */
  static final int HUNG_ROUND_INTERVALS = 3;

  /** How long {@link #close()} waits for a running round to end before it interrupts it. */
  static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

  static final long NOT_RUNNING = Long.MIN_VALUE;

  protected final ProcessEngine processEngine;
  protected final long refreshNanos;
  protected final LongSupplier clock;
  protected final Executor executor;
  /** When the running round was started (clock value), or {@link #NOT_RUNNING}; at most one round runs. */
  protected final AtomicLong roundStartedAt = new AtomicLong(NOT_RUNNING);
  protected volatile boolean closed;

  protected final Map<String, ToLongFunction<ProcessEngine>> queries = new LinkedHashMap<>();
  protected final Map<String, String> descriptions = new LinkedHashMap<>();

  protected volatile Map<String, Double> values = Map.of();
  protected volatile long refreshedAt;
  protected volatile boolean refreshed;

  public EngineGauges(ProcessEngine processEngine, Duration refreshInterval) {
    this(processEngine, refreshInterval, System::nanoTime,
        backgroundExecutor(processEngine.getName(), keepAlive(refreshInterval)));
  }

  EngineGauges(ProcessEngine processEngine, Duration refreshInterval, LongSupplier clock, Executor executor) {
    this.processEngine = processEngine;
    this.refreshNanos = Math.max(0, refreshInterval == null ? 0 : refreshInterval.toNanos());
    this.clock = clock;
    this.executor = executor;

    add("fasti.engine.process.instances.active", "Active (not suspended) process instances",
        engine -> engine.getRuntimeService().createProcessInstanceQuery().active().count());
    add("fasti.engine.tasks.open", "Open user tasks",
        engine -> engine.getTaskService().createTaskQuery().count());
    add("fasti.engine.incidents.open", "Open incidents",
        engine -> engine.getRuntimeService().createIncidentQuery().count());
    add("fasti.engine.jobs.executable", "Jobs that are due and have retries left",
        engine -> engine.getManagementService().createJobQuery().executable().count());
    add("fasti.engine.jobs.failed", "Jobs without retries left",
        engine -> engine.getManagementService().createJobQuery().noRetriesLeft().count());
    add("fasti.engine.external.tasks.open", "Open external tasks",
        engine -> engine.getExternalTaskService().createExternalTaskQuery().count());
  }

  protected void add(String name, String description, ToLongFunction<ProcessEngine> query) {
    queries.put(name, query);
    descriptions.put(name, description);
  }

  /**
   * Twice the refresh interval, at least ten seconds: while the gauges are scraped, one thread
   * serves every round instead of a new thread per round.
   */
  static Duration keepAlive(Duration refreshInterval) {
    Duration twice = refreshInterval == null ? Duration.ZERO : refreshInterval.multipliedBy(2);
    return twice.compareTo(Duration.ofSeconds(10)) > 0 ? twice : Duration.ofSeconds(10);
  }

  /**
   * One daemon thread that ends when no round has run for the keep-alive time, or when the
   * gauges are closed, so nothing is left behind when the engine is closed.
   */
  static Executor backgroundExecutor(String engineName, Duration keepAlive) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, keepAlive.toMillis(), TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(), task -> {
      Thread thread = new Thread(task, "fasti-engine-gauges-" + engineName);
      thread.setDaemon(true);
      return thread;
    });
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  public void bindTo(MeterRegistry registry) {
    for (String name : queries.keySet()) {
      Gauge.builder(name, this, gauges -> gauges.value(name))
          .description(descriptions.get(name))
          .tag("engine", processEngine.getName())
          .strongReference(true)
          .register(registry);
    }
    if (refreshNanos > 0) {
      // so that the first scrape finds values
      refreshInBackground();
    }
  }

  public Iterable<String> names() {
    return queries.keySet();
  }

  /**
   * The value read last, or {@code NaN} if it was not read yet, could not be read, or the round
   * that reads it again hangs. Does not wait for the database.
   */
  public double value(String name) {
    if (closed) {
      // the values read last
    } else if (refreshNanos == 0) {
      refresh();
    } else if (isHung()) {
      return Double.NaN;
    } else if (isStale()) {
      refreshInBackground();
    }
    Double value = values.get(name);
    return value == null ? Double.NaN : value;
  }

  /** Starts a round of queries on the background thread, unless one is running or the gauges are closed. */
  protected void refreshInBackground() {
    if (closed || !roundStartedAt.compareAndSet(NOT_RUNNING, clock.getAsLong())) {
      return;
    }
    boolean submitted = false;
    try {
      executor.execute(() -> {
        try {
          // another round may have finished between the read that found the values stale and this one
          if (!closed && isStale()) {
            refresh();
          }
        } finally {
          roundStartedAt.set(NOT_RUNNING);
        }
      });
      submitted = true;
    } catch (RejectedExecutionException e) {
      LOG.debug("Could not start reading the gauges of engine {}", processEngine.getName(), e);
    } finally {
      // whatever the executor threw (no thread could be created, for example), the next stale
      // read tries again
      if (!submitted) {
        roundStartedAt.set(NOT_RUNNING);
      }
    }
  }

  /**
   * Ends the reading: the gauges keep the values read last and the background thread is released.
   * Called when the application context closes, before the engine is closed, so that an engine
   * that was closed is not queried. Waits up to {@link #CLOSE_TIMEOUT} for a running round, then
   * interrupts it.
   */
  public void close() {
    closed = true;
    if (executor instanceof ExecutorService) {
      ExecutorService service = (ExecutorService) executor;
      service.shutdown();
      try {
        if (!service.awaitTermination(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
          service.shutdownNow();
        }
      } catch (InterruptedException e) {
        service.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Reads all values from the database, one query after the other. */
  protected synchronized void refresh() {
    Map<String, Double> fresh = new LinkedHashMap<>();
    for (Map.Entry<String, ToLongFunction<ProcessEngine>> query : queries.entrySet()) {
      try {
        fresh.put(query.getKey(), (double) query.getValue().applyAsLong(processEngine));
      } catch (RuntimeException e) {
        LOG.debug("Could not read {} of engine {}", query.getKey(), processEngine.getName(), e);
        fresh.put(query.getKey(), Double.NaN);
      }
    }
    values = fresh;
    refreshedAt = clock.getAsLong();
    refreshed = true;
  }

  /** Whether a round has been running for more than {@value #HUNG_ROUND_INTERVALS} refresh intervals. */
  protected boolean isHung() {
    long started = roundStartedAt.get();
    return started != NOT_RUNNING && clock.getAsLong() - started > HUNG_ROUND_INTERVALS * refreshNanos;
  }

  protected boolean isStale() {
    return !refreshed || clock.getAsLong() - refreshedAt >= refreshNanos;
  }
}
