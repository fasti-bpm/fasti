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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import org.camunda.bpm.engine.impl.cmd.ExecuteJobsCmd;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.JobFailureCollector;
import org.junit.Before;
import org.junit.Test;

public class ObservationCommandInterceptorTest {

  protected final List<Observation.Context> stopped = new ArrayList<>();
  protected ObservationRegistry registry;
  protected ObservationCommandInterceptor interceptor;

  @Before
  public void setUp() {
    registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(new ObservationHandler<>() {
      @Override
      public boolean supportsContext(Observation.Context context) {
        return true;
      }

      @Override
      public void onStop(Observation.Context context) {
        stopped.add(context);
      }
    });
    interceptor = new ObservationCommandInterceptor(() -> registry, "default");
    interceptor.setNext(new CommandExecutor() {
      @Override
      public <T> T execute(Command<T> command) {
        return command.execute(null);
      }
    });
  }

  @Test
  public void shouldNotStartTracesForEngineCommandsOutsideAnObservation() {
    assertThat(interceptor.execute(new AnswerCmd())).isEqualTo(42);
    assertThat(stopped).isEmpty();
  }

  @Test
  public void shouldObserveCommandsInsideAnObservation() {
    Observation parent = Observation.start("http.server.requests", registry);
    try (Observation.Scope scope = parent.openScope()) {
      assertThat(interceptor.execute(new AnswerCmd())).isEqualTo(42);
    } finally {
      parent.stop();
    }

    assertThat(stopped).hasSize(2);
    Observation.Context command = stopped.get(0);
    assertThat(command.getName()).isEqualTo(ObservationCommandInterceptor.COMMAND_OBSERVATION);
    assertThat(command.getContextualName()).isEqualTo("AnswerCmd");
    assertThat(command.getLowCardinalityKeyValue("engine").getValue()).isEqualTo("default");
    assertThat(command.getLowCardinalityKeyValue("fasti.engine.command")).isNull();
    assertThat(command.getHighCardinalityKeyValue("fasti.engine.command").getValue()).isEqualTo("AnswerCmd");
    assertThat(command.getParentObservation()).isSameAs(parent);
  }

  @Test
  public void shouldStartATraceForJobExecution() {
    interceptor.setNext(new CommandExecutor() {
      @Override
      public <T> T execute(Command<T> command) {
        return null;
      }
    });

    interceptor.execute(new ExecuteJobsCmd("job", new JobFailureCollector("job")));

    assertThat(stopped).hasSize(1);
    assertThat(stopped.get(0).getName()).isEqualTo(ObservationCommandInterceptor.JOB_OBSERVATION);
    assertThat(stopped.get(0).getContextualName()).isEqualTo("job execution");
  }

  @Test
  public void shouldRecordErrors() {
    Observation parent = Observation.start("parent", registry);
    try (Observation.Scope scope = parent.openScope()) {
      assertThatThrownBy(() -> interceptor.execute(new FailingCmd())).hasMessage("boom");
    } finally {
      parent.stop();
    }

    assertThat(stopped.get(0).getError()).hasMessage("boom");
  }

  @Test
  public void shouldPassThroughWithoutRegistry() {
    ObservationCommandInterceptor withoutRegistry = new ObservationCommandInterceptor(() -> null, "default");
    withoutRegistry.setNext(interceptor.getNext());
    assertThat(withoutRegistry.execute(new AnswerCmd())).isEqualTo(42);

    ObservationCommandInterceptor noop = new ObservationCommandInterceptor(() -> ObservationRegistry.NOOP, "default");
    noop.setNext(interceptor.getNext());
    assertThat(noop.execute(new ExecuteJobsCmd("job", new JobFailureCollector("job")) {
      @Override
      public Void execute(CommandContext commandContext) {
        return null;
      }
    })).isNull();
    assertThat(stopped).isEmpty();
  }

  static class AnswerCmd implements Command<Integer> {
    @Override
    public Integer execute(CommandContext commandContext) {
      return 42;
    }
  }

  static class FailingCmd implements Command<Void> {
    @Override
    public Void execute(CommandContext commandContext) {
      throw new IllegalStateException("boom");
    }
  }
}
