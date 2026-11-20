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

import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.camunda.bpm.engine.impl.cmd.ExecuteJobsCmd;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

/**
 * Observes the commands of the process engine, which tracing turns into spans.
 * <p>
 * A command becomes an observation when it runs inside another one (for example a REST request
 * or a job), so it shows up in that trace. Of the commands outside any observation only job
 * execution is observed: it starts a trace of its own. The many commands the engine runs on its
 * own, such as job acquisition every few seconds, would otherwise each start a trace.
 * <p>
 * The name of the command class is only a span attribute (high cardinality): the timers that
 * Micrometer derives from the observations have the tags {@code engine} and {@code error} only.
 */
public class ObservationCommandInterceptor extends CommandInterceptor {

  public static final String COMMAND_OBSERVATION = "fasti.engine.command";
  public static final String JOB_OBSERVATION = "fasti.engine.job";

  protected final Supplier<ObservationRegistry> observationRegistry;
  protected final String engineName;

  public ObservationCommandInterceptor(Supplier<ObservationRegistry> observationRegistry, String engineName) {
    this.observationRegistry = observationRegistry;
    this.engineName = engineName;
  }

  @Override
  public <T> T execute(Command<T> command) {
    ObservationRegistry registry = observationRegistry.get();
    if (registry == null || registry.isNoop()) {
      return next.execute(command);
    }
    boolean job = command instanceof ExecuteJobsCmd;
    if (!job && registry.getCurrentObservation() == null) {
      return next.execute(command);
    }
    String commandName = command.getClass().getSimpleName();
    if (commandName.isEmpty()) {
      commandName = command.getClass().getName();
    }
    Observation observation = Observation
        .createNotStarted(job ? JOB_OBSERVATION : COMMAND_OBSERVATION, registry)
        .contextualName(job ? "job execution" : commandName)
        .lowCardinalityKeyValue("engine", engineName)
        .highCardinalityKeyValue("fasti.engine.command", commandName);
    return observation.observe(() -> next.execute(command));
  }
}
