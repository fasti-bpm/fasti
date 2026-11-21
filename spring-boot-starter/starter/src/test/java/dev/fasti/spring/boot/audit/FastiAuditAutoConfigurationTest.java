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

import static org.assertj.core.api.Assertions.assertThat;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditTrailPlugin;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(Enclosed.class)
public class FastiAuditAutoConfigurationTest {

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=auditEngine",
      "camunda.bpm.job-execution.enabled=false",
      "fasti.audit.enabled=true",
      "fasti.audit.node-id=starter-node",
      "fasti.audit.chains=2",
      "fasti.audit.variable-values=none",
      "fasti.audit.hmac-key=a-starter-test-key-that-is-long-enough-0123",
      "fasti.audit.retired-hmac-keys=an-earlier-starter-key-that-is-long-enough-01",
      "fasti.audit.retired-key-ids=unsigned",
      "fasti.audit.lock-timeout-seconds=7",
      "fasti.audit.query-timeout-seconds=9"
  })
  public static class Enabled {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected AuditTrailPlugin plugin;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Test
    public void shouldRecordInSpringManagedTransactions() {
      assertThat(plugin.getChains()).isEqualTo(2);
      AuditService audit = AuditTrail.of(processEngine);
      assertThat(audit.isEnabled()).isTrue();
      assertThat(audit.getStatus().getNodeId()).isEqualTo("starter-node");
      assertThat(audit.getStatus().getVariableValues()).isEqualTo("none");
      assertThat(audit.getStatus().isSigned()).isTrue();
      assertThat(audit.getStatus().getRetiredKeyIds()).hasSize(2).contains("unsigned");
      assertThat(plugin.getLockTimeoutSeconds()).isEqualTo(7);
      assertThat(plugin.getQueryTimeoutSeconds()).isEqualTo(9);

      RepositoryService repositoryService = processEngine.getRepositoryService();
      Deployment deployment = repositoryService.createDeployment().addModelInstance("audit.bpmn",
          Bpmn.createExecutableProcess("auditProcess").camundaHistoryTimeToLive(1)
              .startEvent().userTask().endEvent().done()).deploy();
      try {
        String processInstanceId = processEngine.getRuntimeService().startProcessInstanceByKey("auditProcess", "starter-1").getId();
        assertThat(audit.createQuery().processInstanceId(processInstanceId).types(AuditService.TYPE_PROCESS).count())
            .isEqualTo(1);
        assertThat(audit.createQuery().businessKey("starter-1").count()).isPositive();
        assertThat(audit.createQuery().types(AuditService.TYPE_DEPLOYMENT).entityId(deployment.getId()).count()).isEqualTo(1);
        assertThat(audit.verify().isIntact()).isTrue();
      } finally {
        repositoryService.deleteDeployment(deployment.getId(), true);
      }
    }

    @Test
    public void shouldWriteOnChainsOfTheirOwnInsideAnOuterTransaction() {
      assertThat(plugin.getTransactionActive()).isNotNull();
      RepositoryService repositoryService = processEngine.getRepositoryService();
      Deployment deployment = repositoryService.createDeployment().addModelInstance("chains.bpmn",
          Bpmn.createExecutableProcess("chainsProcess").camundaHistoryTimeToLive(1)
              .startEvent().userTask().endEvent().done()).deploy();
      try {
        // no Spring transaction: the engine begins one for the command and writes the records last
        String own = processEngine.getRuntimeService().startProcessInstanceByKey("chainsProcess").getId();
        // inside an outer transaction that goes on after the command: written first, on an ext- chain
        String joined = new TransactionTemplate(transactionManager).execute(
            status -> processEngine.getRuntimeService().startProcessInstanceByKey("chainsProcess").getId());
        AuditService audit = AuditTrail.of(processEngine);
        assertThat(audit.createQuery().processInstanceId(own).types(AuditService.TYPE_PROCESS).list())
            .extracting(AuditEvent::getChainId).singleElement().asString().matches("starter-node/[0-9]+");
        assertThat(audit.createQuery().processInstanceId(joined).types(AuditService.TYPE_PROCESS).list())
            .extracting(AuditEvent::getChainId).singleElement().asString().matches("starter-node/ext-[0-9]+");
        assertThat(audit.verify().isIntact()).isTrue();
      } finally {
        repositoryService.deleteDeployment(deployment.getId(), true);
      }
    }
  }

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=noAuditEngine",
      "camunda.bpm.job-execution.enabled=false"
  })
  public static class DisabledByDefault {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected ObjectProvider<AuditTrailPlugin> plugin;

    @Test
    public void shouldNotRegisterThePlugin() {
      assertThat(plugin.getIfAvailable()).isNull();
      assertThat(AuditTrail.of(processEngine).isEnabled()).isFalse();
    }
  }
}
