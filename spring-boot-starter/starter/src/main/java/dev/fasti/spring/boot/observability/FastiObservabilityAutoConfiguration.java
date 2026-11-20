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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics and tracing of the process engine, when Micrometer is on the classpath.
 * <ul>
 * <li>{@code fasti.metrics.engine.enabled} (default {@code true}): counters and gauges of the
 * engine in the Micrometer registry of the application; they are only exported where the
 * application exports its metrics (for example the Prometheus endpoint).</li>
 * <li>{@code fasti.tracing.enabled} (default {@code false}): observations of engine commands
 * and job executions, which become spans when Micrometer Tracing is configured;
 * {@code fasti.tracing.engine.enabled=false} keeps them off while tracing is on.</li>
 * </ul>
 */
@AutoConfiguration(afterName = "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration")
@ConditionalOnBooleanProperty(prefix = CamundaBpmProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class FastiObservabilityAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBooleanProperty(prefix = FastiEngineMetricsProperties.PREFIX, name = "enabled", matchIfMissing = true)
  @EnableConfigurationProperties(FastiEngineMetricsProperties.class)
  static class EngineMetricsConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fastiEngineMetricsPlugin")
    static FastiEngineMetricsPlugin fastiEngineMetricsPlugin(ObjectProvider<MeterRegistry> meterRegistry,
                                                             FastiEngineMetricsProperties properties) {
      return new FastiEngineMetricsPlugin(meterRegistry::getIfAvailable, properties.getGauges().isEnabled(),
          properties.getGauges().getRefreshInterval());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(ObservationRegistry.class)
  @ConditionalOnBooleanProperty(name = "fasti.tracing.enabled")
  @ConditionalOnBooleanProperty(name = "fasti.tracing.engine.enabled", matchIfMissing = true)
  static class EngineObservationConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fastiEngineObservationPlugin")
    static FastiEngineObservationPlugin fastiEngineObservationPlugin(ObjectProvider<ObservationRegistry> observationRegistry) {
      return new FastiEngineObservationPlugin(observationRegistry::getIfAvailable);
    }
  }
}
