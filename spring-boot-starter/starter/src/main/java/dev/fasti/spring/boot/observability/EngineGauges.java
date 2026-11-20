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
 * Each value is a count query on the database. The values are read together and kept for the
 * refresh interval, so frequent scrapes or several registries cause at most one round of
 * queries per interval. The queries run without an authenticated user and therefore see the
 * whole engine.
 */
public class EngineGauges {

  private static final Logger LOG = LoggerFactory.getLogger(EngineGauges.class);

  protected final ProcessEngine processEngine;
  protected final long refreshNanos;
  protected final LongSupplier clock;

  protected final Map<String, ToLongFunction<ProcessEngine>> queries = new LinkedHashMap<>();
  protected final Map<String, String> descriptions = new LinkedHashMap<>();

  protected volatile Map<String, Double> values = Map.of();
  protected volatile long refreshedAt;
  protected volatile boolean refreshed;

  public EngineGauges(ProcessEngine processEngine, Duration refreshInterval) {
    this(processEngine, refreshInterval, System::nanoTime);
  }

  EngineGauges(ProcessEngine processEngine, Duration refreshInterval, LongSupplier clock) {
    this.processEngine = processEngine;
    this.refreshNanos = Math.max(0, refreshInterval == null ? 0 : refreshInterval.toNanos());
    this.clock = clock;

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

  public void bindTo(MeterRegistry registry) {
    for (String name : queries.keySet()) {
      Gauge.builder(name, this, gauges -> gauges.value(name))
          .description(descriptions.get(name))
          .tag("engine", processEngine.getName())
          .strongReference(true)
          .register(registry);
    }
  }

  public Iterable<String> names() {
    return queries.keySet();
  }

  /** The current value, or {@code NaN} if it could not be read. */
  public double value(String name) {
    refreshIfStale();
    Double value = values.get(name);
    return value == null ? Double.NaN : value;
  }

  protected void refreshIfStale() {
    if (!isStale()) {
      return;
    }
    synchronized (this) {
      if (!isStale()) {
        return;
      }
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
  }

  protected boolean isStale() {
    return !refreshed || clock.getAsLong() - refreshedAt >= refreshNanos;
  }
}
