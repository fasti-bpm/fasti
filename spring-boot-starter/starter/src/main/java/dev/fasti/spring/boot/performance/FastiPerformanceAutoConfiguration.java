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
package dev.fasti.spring.boot.performance;

import dev.fasti.engine.performance.PerformanceAdvisorPlugin;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registers the performance hints of the engine start (docs/performance.md): one warning that
 * lists missing optional indexes ({@code fasti.performance.index-check}) and warnings about
 * settings that cost performance ({@code fasti.performance.config-check}), each {@code warn}
 * (default) or {@code off}. On by default, unlike the Fasti engine features, because it only
 * reads the database catalog and the configuration and changes nothing.
 */
@AutoConfiguration(afterName = "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration")
@ConditionalOnBooleanProperty(prefix = CamundaBpmProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(FastiPerformanceProperties.class)
public class FastiPerformanceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(PerformanceAdvisorPlugin.class)
  static PerformanceAdvisorPlugin fastiPerformanceAdvisorPlugin(FastiPerformanceProperties properties,
      Environment environment) {
    PerformanceAdvisorPlugin plugin = new PerformanceAdvisorPlugin();
    // the starter starts the job executor itself and leaves jobExecutorActivate of the engine off
    plugin.setJobExecutorActive(environment.getProperty(CamundaBpmProperties.PREFIX + ".job-execution.enabled", Boolean.class, true));
    plugin.setIndexCheck(properties.getIndexCheck());
    plugin.setConfigCheck(properties.getConfigCheck());
    return plugin;
  }
}
