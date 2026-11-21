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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.camunda.bpm.engine.management.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Publishes metrics of the process engine to Micrometer.
 * <p>
 * Counters (tag {@code engine}, some with one more tag of a fixed set of values) follow the
 * metrics that the engine collects anyway (process and activity instances, jobs, decisions);
 * the end of root process instances is counted by an additional built-in listener. Gauges with
 * the current state are read from the database, see {@link EngineGauges}. Process definition
 * keys, activity ids or other unbounded values never become tags.
 * <p>
 * The counters only move while the metrics of the engine are enabled
 * ({@code camunda.bpm.metrics.enabled}, on by default).
 * <p>
 * As a {@link SmartLifecycle} bean the plugin stops the gauges when the application context
 * stops or closes, before any bean is destroyed: the engine is closed only after that, so no
 * round of queries runs against a closed engine or connection pool.
 */
public class FastiEngineMetricsPlugin extends AbstractProcessEnginePlugin implements AutoCloseable, SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(FastiEngineMetricsPlugin.class);

  public static final String PROCESS_INSTANCES_ENDED = "fasti.engine.process.instances.ended";

  protected final Supplier<MeterRegistry> meterRegistry;
  protected final boolean gaugesEnabled;
  protected final Duration gaugeRefreshInterval;

  protected final Map<String, EngineGauges> gauges = new LinkedHashMap<>();

  protected volatile boolean running;

  public FastiEngineMetricsPlugin(Supplier<MeterRegistry> meterRegistry, boolean gaugesEnabled,
                                  Duration gaugeRefreshInterval) {
    this.meterRegistry = meterRegistry;
    this.gaugesEnabled = gaugesEnabled;
    this.gaugeRefreshInterval = gaugeRefreshInterval;
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    MeterRegistry registry = meterRegistry.get();
    if (registry == null || !configuration.isMetricsEnabled()) {
      return;
    }
    if (configuration.getMetricsRegistry() != null) {
      LOG.info("Process engine {} has its own metrics registry; its counters are not published to Micrometer",
          configuration.getProcessEngineName());
      return;
    }
    String engine = configuration.getProcessEngineName();
    configuration.setMetricsRegistry(new MeteredMetricsRegistry(counters(registry, engine)));

    List<BpmnParseListener> listeners = configuration.getCustomPostBPMNParseListeners();
    if (listeners == null) {
      listeners = new ArrayList<>();
      configuration.setCustomPostBPMNParseListeners(listeners);
    }
    listeners.add(new ProcessEndParseListener(
        counter(registry, PROCESS_INSTANCES_ENDED, "Ended root process instances", engine, "state", "completed"),
        counter(registry, PROCESS_INSTANCES_ENDED, "Ended root process instances", engine, "state", "canceled")));
  }

  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    MeterRegistry registry = meterRegistry.get();
    if (registry == null || !gaugesEnabled) {
      return;
    }
    EngineGauges engineGauges = new EngineGauges(processEngine, gaugeRefreshInterval);
    engineGauges.bindTo(registry);
    gauges.put(processEngine.getName(), engineGauges);
  }

  public Map<String, EngineGauges> getGauges() {
    return gauges;
  }

  /**
   * Stops the gauges from reading the database; they keep the values read last. Called by
   * {@link #stop()} and again when the bean is destroyed; a second call does nothing.
   */
  @Override
  public void close() {
    gauges.values().forEach(EngineGauges::close);
  }

  @Override
  public void start() {
    running = true;
  }

  /** The gauges stay closed after the context was stopped, also if it is started again. */
  @Override
  public void stop() {
    running = false;
    close();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** The counters per metric name of the engine. */
  protected Map<String, Counter> counters(MeterRegistry registry, String engine) {
    Map<String, Counter> counters = new LinkedHashMap<>();
    counters.put(Metrics.ROOT_PROCESS_INSTANCE_START,
        counter(registry, "fasti.engine.process.instances.started", "Started root process instances", engine));
    counters.put(Metrics.ACTIVTY_INSTANCE_START,
        counter(registry, "fasti.engine.activity.instances.started", "Started activity instances", engine));
    counters.put(Metrics.ACTIVTY_INSTANCE_END,
        counter(registry, "fasti.engine.activity.instances.ended", "Ended activity instances", engine));
    counters.put(Metrics.JOB_SUCCESSFUL,
        counter(registry, "fasti.engine.jobs.executed", "Executed jobs", engine, "result", "successful"));
    counters.put(Metrics.JOB_FAILED,
        counter(registry, "fasti.engine.jobs.executed", "Executed jobs", engine, "result", "failed"));
    counters.put(Metrics.JOB_ACQUISITION_ATTEMPT,
        counter(registry, "fasti.engine.jobs.acquisition.attempts", "Job acquisition cycles", engine));
    counters.put(Metrics.JOB_ACQUIRED_SUCCESS,
        counter(registry, "fasti.engine.jobs.acquired", "Jobs selected for execution", engine, "result", "success"));
    counters.put(Metrics.JOB_ACQUIRED_FAILURE,
        counter(registry, "fasti.engine.jobs.acquired", "Jobs selected for execution", engine, "result", "failure"));
    counters.put(Metrics.JOB_EXECUTION_REJECTED,
        counter(registry, "fasti.engine.jobs.rejected", "Acquired jobs rejected by a full executor queue", engine));
    counters.put(Metrics.JOB_LOCKED_EXCLUSIVE,
        counter(registry, "fasti.engine.jobs.locked.exclusive", "Exclusive jobs executed right after the job that created them", engine));
    counters.put(Metrics.EXECUTED_DECISION_INSTANCES,
        counter(registry, "fasti.engine.decision.instances.evaluated", "Evaluated decision instances", engine));
    counters.put(Metrics.EXECUTED_DECISION_ELEMENTS,
        counter(registry, "fasti.engine.decision.elements.evaluated", "Evaluated decision elements", engine));
    return counters;
  }

  protected static Counter counter(MeterRegistry registry, String name, String description, String engine,
                                   String... tags) {
    return Counter.builder(name)
        .description(description)
        .tag("engine", engine)
        .tags(tags)
        .register(registry);
  }

  /** Counts the end of root process instances, by whether they completed or were canceled. */
  public static class ProcessEndParseListener extends AbstractBpmnParseListener {

    protected final ExecutionListener listener;

    public ProcessEndParseListener(Counter completed, Counter canceled) {
      this.listener = new ProcessEndCounter(completed, canceled);
    }

    @Override
    public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
      processDefinition.addBuiltInListener(ExecutionListener.EVENTNAME_END, listener);
    }
  }

  public static class ProcessEndCounter implements ExecutionListener {

    protected final Counter completed;
    protected final Counter canceled;

    public ProcessEndCounter(Counter completed, Counter canceled) {
      this.completed = completed;
      this.canceled = canceled;
    }

    @Override
    public void notify(DelegateExecution execution) {
      if (execution instanceof ExecutionEntity entity
          && !entity.getId().equals(entity.getRootProcessInstanceId())) {
        return;
      }
      (execution.isCanceled() ? canceled : completed).increment();
    }
  }
}
