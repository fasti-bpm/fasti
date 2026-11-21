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
import java.util.concurrent.atomic.AtomicBoolean;
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
 * scrape does not wait for the database, frequent scrapes or several registries cause at most
 * one round of queries per interval, and an engine nobody scrapes is not queried. A value is
 * therefore up to one refresh interval plus one scrape interval old, and {@code NaN} until the
 * first round has finished (it is started when the gauges are registered). With a refresh
 * interval of zero the values are read on every read of a gauge, in the reading thread (for
 * tests). The queries run without an authenticated user and therefore see the whole engine.
 */
public class EngineGauges {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGauges.class);

  protected final ProcessEngine processEngine;
  protected final long refreshNanos;
  protected final LongSupplier clock;
  protected final Executor executor;
  protected final AtomicBoolean refreshing = new AtomicBoolean();
  protected volatile boolean closed;

  protected final Map<String, ToLongFunction<ProcessEngine>> queries = new LinkedHashMap<>();
  protected final Map<String, String> descriptions = new LinkedHashMap<>();

  protected volatile Map<String, Double> values = Map.of();
  protected volatile long refreshedAt;
  protected volatile boolean refreshed;

  public EngineGauges(ProcessEngine processEngine, Duration refreshInterval) {
    this(processEngine, refreshInterval, System::nanoTime, backgroundExecutor(processEngine.getName()));
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
   * One daemon thread that exists only while a round of queries runs (and a few seconds after),
   * so nothing is left behind when the engine is closed.
   */
  static Executor backgroundExecutor(String engineName) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
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

  /** The value read last, or {@code NaN} if it was not read yet or could not be read. Does not wait for the database. */
  public double value(String name) {
    if (closed) {
      // the values read last
    } else if (refreshNanos == 0) {
      refresh();
    } else if (isStale()) {
      refreshInBackground();
    }
    Double value = values.get(name);
    return value == null ? Double.NaN : value;
  }

  /** Starts a round of queries on the background thread, unless one is running or the gauges are closed. */
  protected void refreshInBackground() {
    if (closed || !refreshing.compareAndSet(false, true)) {
      return;
    }
    try {
      executor.execute(() -> {
        try {
          // another round may have finished between the read that found the values stale and this one
          if (!closed && isStale()) {
            refresh();
          }
        } finally {
          refreshing.set(false);
        }
      });
    } catch (RejectedExecutionException e) {
      refreshing.set(false);
    }
  }

  /**
   * Ends the reading: the gauges keep the values read last and the background thread is released.
   * Called when the application context closes, so that an engine that was closed is not queried.
   */
  public void close() {
    closed = true;
    if (executor instanceof ExecutorService) {
      ((ExecutorService) executor).shutdown();
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

  protected boolean isStale() {
    return !refreshed || clock.getAsLong() - refreshedAt >= refreshNanos;
  }
}
