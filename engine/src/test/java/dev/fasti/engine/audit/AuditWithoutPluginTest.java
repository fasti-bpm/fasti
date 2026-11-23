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
package dev.fasti.engine.audit;

import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fasti.engine.audit.impl.AuditStore;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.junit.After;
import org.junit.Test;

/**
 * Without the plugin nothing changes: no table is created, nothing is written, and the service
 * says it is off. A node without the plugin next to one with it does not write records.
 */
public class AuditWithoutPluginTest {

  ProcessEngine plain;
  ProcessEngine audited;

  @After
  public void closeEngines() {
    if (audited != null) {
      AuditTestSupport.cleanUp(audited, true);
    }
    if (plain != null) {
      AuditTestSupport.cleanUp(plain, false);
    }
  }

  static boolean tablesPresent(ProcessEngine engine) {
    return ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getCommandExecutorTxRequired()
        .execute(AuditStore::isTablePresent);
  }

  @Test
  public void shouldCreateNoTableAndWriteNothing() {
    plain = AuditTestSupport.configuration("off").buildProcessEngine();
    plain.getRepositoryService().createDeployment().addModelInstance("o.bpmn", userTaskProcess("o")).deploy();
    plain.getRuntimeService().startProcessInstanceByKey("o");

    assertThat(tablesPresent(plain)).isFalse();
    AuditService audit = AuditTrail.of(plain);
    assertThat(audit.isEnabled()).isFalse();
    assertThat(audit.getStatus()).isNull();
    assertThatThrownBy(audit::createQuery).isInstanceOf(BadUserRequestException.class).hasMessageContaining("not enabled");
    assertThatThrownBy(audit::verify).isInstanceOf(BadUserRequestException.class);
  }

  @Test
  public void shouldNotRecordWhatANodeWithoutThePluginDoes() {
    audited = AuditTestSupport.engine("mixed1", plugin -> { }, configuration -> configuration
        .setJdbcUrl(configuration.getJdbcUrl().startsWith("jdbc:h2:mem:") ? "jdbc:h2:mem:fasti-audit-mixed;DB_CLOSE_DELAY=10"
            : configuration.getJdbcUrl()));
    ProcessEngineConfigurationImpl plainConfiguration = AuditTestSupport.configuration("mixed2");
    plainConfiguration.setJdbcUrl(((ProcessEngineConfigurationImpl) audited.getProcessEngineConfiguration()).getJdbcUrl());
    plain = plainConfiguration.buildProcessEngine();

    audited.getRepositoryService().createDeployment().addModelInstance("m.bpmn", userTaskProcess("m")).deploy();
    String onAudited = audited.getRuntimeService().startProcessInstanceByKey("m").getId();
    String onPlain = plain.getRuntimeService().startProcessInstanceByKey("m").getId();

    AuditService audit = AuditTrail.of(audited);
    assertThat(audit.createQuery().processInstanceId(onAudited).count()).isPositive();
    assertThat(audit.createQuery().processInstanceId(onPlain).count()).isZero();
    // the node without the plugin leaves the tables alone
    assertThat(audit.verify().isIntact()).isTrue();
  }
}
