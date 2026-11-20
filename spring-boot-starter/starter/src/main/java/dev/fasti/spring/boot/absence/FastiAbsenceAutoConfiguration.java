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
package dev.fasti.spring.boot.absence;

import dev.fasti.engine.absence.AbsenceSubstitutionPlugin;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the absence plugin with the process engine when {@code fasti.absence.enabled=true}.
 * Off by default: an embedding application only changes how its tasks are assigned when it asks
 * for it, and nodes of a cluster should agree on it.
 */
@AutoConfiguration(afterName = "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration")
@ConditionalOnBooleanProperty(prefix = CamundaBpmProperties.PREFIX, name = "enabled", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = FastiAbsenceProperties.PREFIX, name = "enabled")
@EnableConfigurationProperties(FastiAbsenceProperties.class)
public class FastiAbsenceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AbsenceSubstitutionPlugin.class)
  static AbsenceSubstitutionPlugin fastiAbsenceSubstitutionPlugin(FastiAbsenceProperties properties) {
    AbsenceSubstitutionPlugin plugin = new AbsenceSubstitutionPlugin();
    plugin.setMaxHops(properties.getMaxHops());
    plugin.setSubstituteCandidates(properties.isSubstituteCandidates());
    return plugin;
  }
}
