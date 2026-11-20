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
package dev.fasti.spring.boot.foureyes;

import dev.fasti.engine.foureyes.FourEyesPlugin;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the four-eyes plugin with the process engine when {@code fasti.four-eyes.enabled=true}.
 * Off by default in the starter, like every Fasti engine feature, so that an embedding application
 * opts in; the plugin only acts on user tasks whose model declares a rule.
 */
@AutoConfiguration(afterName = "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration")
@ConditionalOnBooleanProperty(prefix = CamundaBpmProperties.PREFIX, name = "enabled", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = FastiFourEyesProperties.PREFIX, name = "enabled")
@EnableConfigurationProperties(FastiFourEyesProperties.class)
public class FastiFourEyesAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(FourEyesPlugin.class)
  static FourEyesPlugin fastiFourEyesPlugin() {
    return new FourEyesPlugin();
  }
}
