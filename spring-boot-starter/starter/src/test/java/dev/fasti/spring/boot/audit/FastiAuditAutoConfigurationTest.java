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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.sql.DataSource;

import dev.fasti.engine.audit.AuditEvent;
import dev.fasti.engine.audit.AuditService;
import dev.fasti.engine.audit.AuditTrail;
import dev.fasti.engine.audit.AuditTrailPlugin;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    String chainOf(String processInstanceId) {
      return FastiAuditAutoConfigurationTest.chainOf(processEngine, processInstanceId);
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
        // inside an outer transaction that goes on after the command: written first, on the chains
        // that 1.1 writes to as well
        String joined = new TransactionTemplate(transactionManager).execute(
            status -> processEngine.getRuntimeService().startProcessInstanceByKey("chainsProcess").getId());
        assertThat(chainOf(own)).matches("starter-node/end-[01]");
        assertThat(chainOf(joined)).matches("starter-node/[01]");
        AuditService audit = AuditTrail.of(processEngine);
        assertThat(audit.verify().isIntact()).isTrue();
      } finally {
        repositoryService.deleteDeployment(deployment.getId(), true);
      }
    }
  }

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=auditProbeEngine",
      "camunda.bpm.job-execution.enabled=false",
      "fasti.audit.enabled=true",
      "fasti.audit.node-id=probe-node",
      "fasti.audit.chains=2",
      "fasti.audit.purge-batch-size=7"
  })
  public static class TransactionBoundaries {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected AuditTrailPlugin plugin;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @Autowired
    protected DataSource dataSource;

    protected String deploymentId;

    @Before
    public void deploy() {
      deploymentId = processEngine.getRepositoryService().createDeployment().addModelInstance("probe.bpmn",
          Bpmn.createExecutableProcess("probeProcess").camundaHistoryTimeToLive(1).startEvent().userTask().endEvent().done())
          .deploy().getId();
    }

    @After
    public void undeploy() {
      processEngine.getRepositoryService().deleteDeployment(deploymentId, true);
    }

    String start() {
      return processEngine.getRuntimeService().startProcessInstanceByKey("probeProcess").getId();
    }

    String chainOf(String processInstanceId) {
      return FastiAuditAutoConfigurationTest.chainOf(processEngine, processInstanceId);
    }

    @Test
    public void shouldPassThePurgeBatchSize() {
      assertThat(plugin.getPurgeBatchSize()).isEqualTo(7);
    }

    @Test
    public void shouldWriteLastInsideATransactionOfAnotherTransactionManager() {
      // a transaction on another DataSource (as of a second database, JMS or Kafka): the engine
      // begins a transaction of its own beside it, which ends with the command
      DriverManagerDataSource other = new DriverManagerDataSource("jdbc:h2:mem:fasti-audit-other;DB_CLOSE_DELAY=-1", "sa", "");
      String id = new TransactionTemplate(new DataSourceTransactionManager(other)).execute(status -> start());
      assertThat(chainOf(id)).matches("probe-node/end-[01]");
      // in a transaction of the transaction manager of the engine: joined, written first
      String joined = new TransactionTemplate(transactionManager).execute(status -> start());
      assertThat(chainOf(joined)).matches("probe-node/[01]");
      assertThat(AuditTrail.of(processEngine).verify().isIntact()).isTrue();
    }

    @Test
    public void shouldGiveTheChainBackWhenACommitFails() {
      // an outer transaction of the kind of transaction manager the engine uses, whose commit fails: Spring reports the
      // end as unknown, which the engine does not hear of
      PlatformTransactionManager failing;
      if (transactionManager instanceof JpaTransactionManager) {
        failing = new JpaTransactionManager(((JpaTransactionManager) transactionManager).getEntityManagerFactory()) {
          @Override
          protected void doCommit(DefaultTransactionStatus status) {
            super.doCommit(status);
            throw new TransactionSystemException("the commit was lost");
          }
        };
      } else {
        failing = new DataSourceTransactionManager(dataSource) {
          @Override
          protected void doCommit(DefaultTransactionStatus status) {
            super.doCommit(status);
            throw new TransactionSystemException("the commit was lost");
          }
        };
      }
      assertThatThrownBy(() -> new TransactionTemplate(failing).execute(status -> start()))
          .isInstanceOf(TransactionSystemException.class);
      // the next transactions of the thread are outermost ones again, not of the next band
      assertThat(chainOf(start())).matches("probe-node/end-[01]");
      assertThat(chainOf(new TransactionTemplate(transactionManager).execute(status -> start()))).matches("probe-node/[01]");
    }

    @Test
    public void shouldGiveTheChainBackWhenAnAfterCommitCallbackThrows() {
      // a callback registered before the engine command throws in afterCommit: Spring skips the
      // afterCommit callbacks after it, among them the one of the engine
      assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            throw new IllegalStateException("a failing callback");
          }
        });
        return start();
      })).isInstanceOf(IllegalStateException.class);
      assertThat(chainOf(start())).matches("probe-node/end-[01]");
      assertThat(chainOf(new TransactionTemplate(transactionManager).execute(status -> start()))).matches("probe-node/[01]");
    }

    @Test
    public void shouldWriteANestedRequiresNewTransactionInTheNextBand() {
      // an application transaction that requires a new one inside an outer one that holds a chain
      TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
      requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      String[] ids = new String[2];
      new TransactionTemplate(transactionManager).execute(status -> {
        ids[0] = start();
        ids[1] = requiresNew.execute(inner -> start());
        return null;
      });
      assertThat(chainOf(ids[0])).matches("probe-node/[01]");
      assertThat(chainOf(ids[1])).matches("probe-node/[23]");
      assertThat(AuditTrail.of(processEngine).verify().isIntact()).isTrue();
    }
  }

  static String chainOf(ProcessEngine processEngine, String processInstanceId) {
    List<AuditEvent> records = AuditTrail.of(processEngine).createQuery().processInstanceId(processInstanceId)
        .types(AuditService.TYPE_PROCESS).list();
    assertThat(records).hasSize(1);
    return records.get(0).getChainId();
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
