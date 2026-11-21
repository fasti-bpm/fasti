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
package dev.fasti.spring.boot.audit;

import dev.fasti.engine.audit.AuditTrailPlugin;
import org.camunda.bpm.spring.boot.starter.property.CamundaBpmProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registers the audit trail plugin with the process engine when {@code fasti.audit.enabled=true}.
 * Off by default: it adds two tables and a write per command, and every node of a cluster should
 * run it, so an embedding application turns it on deliberately.
 */
@AutoConfiguration(afterName = "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration")
@ConditionalOnBooleanProperty(prefix = CamundaBpmProperties.PREFIX, name = "enabled", matchIfMissing = true)
@ConditionalOnBooleanProperty(prefix = FastiAuditProperties.PREFIX, name = "enabled")
@EnableConfigurationProperties(FastiAuditProperties.class)
public class FastiAuditAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(AuditTrailPlugin.class)
  static AuditTrailPlugin fastiAuditTrailPlugin(FastiAuditProperties properties) {
    AuditTrailPlugin plugin = new AuditTrailPlugin();
    plugin.setNodeId(properties.getNodeId());
    plugin.setChains(properties.getChains());
    plugin.setTypes(properties.getTypes());
    plugin.setVariableValues(properties.getVariableValues());
    plugin.setMaskedVariables(properties.getMaskedVariables());
    plugin.setHmacKey(properties.getHmacKey());
    plugin.setHmacKeyFile(properties.getHmacKeyFile());
    plugin.setCheckpointInterval(properties.getCheckpointInterval());
    plugin.setRetentionDays(properties.getRetentionDays());
    plugin.setRetiredHmacKeys(properties.getRetiredHmacKeys());
    plugin.setRetiredKeyIds(properties.getRetiredKeyIds());
    plugin.setLockTimeoutSeconds(properties.getLockTimeoutSeconds());
    plugin.setQueryTimeoutSeconds(properties.getQueryTimeoutSeconds());
    // a command that finds no Spring transaction to join begins and ends its own: its records are
    // then written after the statements of the engine
    plugin.setTransactionActive(TransactionSynchronizationManager::isActualTransactionActive);
    return plugin;
  }
}
