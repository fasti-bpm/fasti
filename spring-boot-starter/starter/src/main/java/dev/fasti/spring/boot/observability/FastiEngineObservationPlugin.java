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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.micrometer.observation.ObservationRegistry;

import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

/**
 * Puts an {@link ObservationCommandInterceptor} in front of the command chains of the engine
 * (with and without a new transaction), so that engine commands and job executions appear in
 * traces.
 */
public class FastiEngineObservationPlugin extends AbstractProcessEnginePlugin {

  protected final Supplier<ObservationRegistry> observationRegistry;

  public FastiEngineObservationPlugin(Supplier<ObservationRegistry> observationRegistry) {
    this.observationRegistry = observationRegistry;
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    String engine = configuration.getProcessEngineName();
    configuration.setCustomPreCommandInterceptorsTxRequired(
        prepend(configuration.getCustomPreCommandInterceptorsTxRequired(), engine));
    configuration.setCustomPreCommandInterceptorsTxRequiresNew(
        prepend(configuration.getCustomPreCommandInterceptorsTxRequiresNew(), engine));
  }

  protected List<CommandInterceptor> prepend(List<CommandInterceptor> interceptors, String engine) {
    List<CommandInterceptor> result = new ArrayList<>();
    // a chain links its interceptors, so each chain needs an instance of its own
    result.add(new ObservationCommandInterceptor(observationRegistry, engine));
    if (interceptors != null) {
      result.addAll(interceptors);
    }
    return result;
  }
}
