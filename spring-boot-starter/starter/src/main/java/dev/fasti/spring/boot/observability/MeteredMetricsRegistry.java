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

import java.util.Map;

import io.micrometer.core.instrument.Counter;

import org.camunda.bpm.engine.impl.metrics.MetricsRegistry;

/**
 * The metrics registry of the engine that also counts each occurrence on a Micrometer counter.
 * <p>
 * The meters of the engine are cleared whenever the database reporter writes them (every 15
 * minutes by default), so they cannot back a Prometheus counter. The Micrometer counters only
 * ever grow while the engine runs. Only the metric names of the given map are counted, so the
 * number of time series stays fixed.
 */
public class MeteredMetricsRegistry extends MetricsRegistry {

  protected final Map<String, Counter> counters;

  public MeteredMetricsRegistry(Map<String, Counter> counters) {
    this.counters = Map.copyOf(counters);
  }

  @Override
  public void markOccurrence(String name, long times) {
    super.markOccurrence(name, times);
    Counter counter = counters.get(name);
    if (counter != null && times > 0) {
      counter.increment(times);
    }
  }

  public Map<String, Counter> getCounters() {
    return counters;
  }
}
