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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.fasti.engine.audit.impl.AuditHistoryEventHandler;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Test;

/**
 * Many commands at once, on one engine and on two engines that share the database (two nodes of a
 * cluster): no record is lost, no position is used twice, and every chain verifies.
 */
public class AuditConcurrencyTest {

  static final int THREADS = 8;
  static final int INSTANCES_PER_THREAD = 15;

  final List<ProcessEngine> engines = new ArrayList<>();

  @After
  public void closeEngines() {
    for (int i = engines.size() - 1; i >= 0; i--) {
      AuditTestSupport.cleanUp(engines.get(i), i == 0);
    }
  }

  ProcessEngine engine(String name, String nodeId, String database) {
    ProcessEngine engine = AuditTestSupport.engine(name, plugin -> {
      plugin.setNodeId(nodeId);
      plugin.setChains(3);
      plugin.setCheckpointInterval(10);
    }, configuration -> {
      if (database != null && configuration.getJdbcUrl().startsWith("jdbc:h2:mem:")) {
        configuration.setJdbcUrl("jdbc:h2:mem:fasti-audit-" + database + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000");
      }
      configuration.setJdbcMaxActiveConnections(THREADS + 2);
      // as in a cluster: ids that need no shared block of the database
      configuration.setIdGenerator(new StrongUuidGenerator());
    });
    engines.add(engine);
    return engine;
  }

  /** Each thread starts instances and completes their tasks as its own user. */
  static Callable<Integer> worker(ProcessEngine engine, String user) {
    return () -> {
      int done = 0;
      for (int i = 0; i < INSTANCES_PER_THREAD; i++) {
        engine.getIdentityService().setAuthenticatedUserId(user);
        try {
          String pi = engine.getRuntimeService().startProcessInstanceByKey("parallel", user + "-" + i).getId();
          Task task = engine.getTaskService().createTaskQuery().processInstanceId(pi).singleResult();
          engine.getTaskService().setAssignee(task.getId(), user);
          engine.getTaskService().complete(task.getId());
          done++;
        } finally {
          engine.getIdentityService().clearAuthentication();
        }
      }
      return done;
    };
  }

  static void runAll(List<Callable<Integer>> workers) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(workers.size());
    try {
      List<Future<Integer>> futures = executor.invokeAll(workers, 5, TimeUnit.MINUTES);
      for (Future<Integer> future : futures) {
        assertThat(future.get()).isEqualTo(INSTANCES_PER_THREAD);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  static void assertComplete(ProcessEngine engine, int instances) {
    AuditService audit = AuditTrail.of(engine);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count()).isEqualTo(instances);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("end").count()).isEqualTo(instances);
    assertThat(audit.createQuery().types(AuditService.TYPE_TASK).action("complete").count()).isEqualTo(instances);
    List<AuditEvent> all = audit.createQuery().list();
    Set<String> positions = all.stream().map(e -> e.getChainId() + "#" + e.getSequence()).collect(Collectors.toSet());
    assertThat(positions).hasSize(all.size());
    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).as("%s", verification.getFirstBreak() == null ? "" : verification.getFirstBreak().getMessage())
        .isTrue();
  }

  @Test
  public void shouldChainParallelCommandsOfOneEngine() throws Exception {
    ProcessEngine engine = engine("parallel1", "node-a", null);
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    List<Callable<Integer>> workers = new ArrayList<>();
    for (int t = 0; t < THREADS; t++) {
      workers.add(worker(engine, "user" + t));
    }
    runAll(workers);
    assertComplete(engine, THREADS * INSTANCES_PER_THREAD);
    // the work was spread over the chains of the node
    assertThat(AuditTrail.of(engine).verify().getChains()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  public void shouldChainTwoEnginesOnOneDatabase() throws Exception {
    ProcessEngine first = engine("shared1", "node-1", "shared");
    ProcessEngine second = engine("shared2", "node-2", "shared");
    first.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    List<Callable<Integer>> workers = new ArrayList<>();
    for (int t = 0; t < THREADS; t++) {
      workers.add(worker(t % 2 == 0 ? first : second, "user" + t));
    }
    runAll(workers);
    assertComplete(first, THREADS * INSTANCES_PER_THREAD);
    List<String> chains = AuditTrail.of(second).verify().getChains().stream()
        .map(AuditVerification.Chain::getChainId).collect(Collectors.toList());
    assertThat(chains).anyMatch(id -> id.startsWith("node-1/")).anyMatch(id -> id.startsWith("node-2/"));
  }

  @Test
  public void shouldStaySoundWhenTwoEnginesShareANodeId() throws Exception {
    // misconfigured: both nodes write the same chains; the head lock serializes them
    ProcessEngine first = engine("same1", "same-node", "same");
    ProcessEngine second = engine("same2", "same-node", "same");
    first.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    List<Callable<Integer>> workers = new ArrayList<>();
    for (int t = 0; t < THREADS; t++) {
      workers.add(worker(t % 2 == 0 ? first : second, "user" + t));
    }
    runAll(workers);
    assertComplete(first, THREADS * INSTANCES_PER_THREAD);
  }

  @Test
  public void shouldNotWaitForItsOwnOuterTransactionInANestedOne() {
    ProcessEngine engine = engine("nested", "node-nested", null);
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("parallel");
      // as in an outer transaction that spans several commands: the records of the first are
      // written (and its chain is locked) before a nested command starts a transaction of its own
      AuditHistoryEventHandler.session(commandContext).flush();
      configuration.getCommandExecutorTxRequiresNew().execute(nested -> {
        engine.getRuntimeService().startProcessInstanceByKey("parallel");
        return null;
      });
      return null;
    });
    assertComplete0(engine, 2);
  }

  @Test
  public void shouldTakeAnExtraChainForANestedTransactionWhenAllAreHeld() {
    ProcessEngine engine = AuditTestSupport.engine("nested1", plugin -> plugin.setChains(1));
    engines.add(engine);
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("parallel");
      AuditHistoryEventHandler.session(commandContext).flush();
      configuration.getCommandExecutorTxRequiresNew().execute(nested -> {
        engine.getRuntimeService().startProcessInstanceByKey("parallel");
        return null;
      });
      return null;
    });
    assertComplete0(engine, 2);
    assertThat(AuditTrail.of(engine).verify().getChains()).extracting(AuditVerification.Chain::getChainId)
        .contains("node-nested1/end-0", "node-nested1/end-1");
  }

  @Test
  public void shouldRefuseToPurgeInsideATransactionThatHoldsAChain() {
    ProcessEngine engine = AuditTestSupport.engine("nestedpurge", plugin -> plugin.setRetentionDays(0));
    engines.add(engine);
    engine.getRepositoryService().createDeployment().addModelInstance("p.bpmn", userTaskProcess("parallel")).deploy();
    ProcessEngineConfigurationImpl configuration = (ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      engine.getRuntimeService().startProcessInstanceByKey("parallel");
      AuditHistoryEventHandler.session(commandContext).flush();
      assertThatThrownBy(() -> AuditTrail.of(engine).purge(new java.util.Date(0)))
          .isInstanceOf(org.camunda.bpm.engine.BadUserRequestException.class).hasMessageContaining("transaction of its own");
      return null;
    });
    assertThat(AuditTrail.of(engine).purge(new java.util.Date(0)).getRemoved()).isZero();
  }

  static void assertComplete0(ProcessEngine engine, int starts) {
    AuditService audit = AuditTrail.of(engine);
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).action("start").count()).isEqualTo(starts);
    assertThat(audit.verify().isIntact()).isTrue();
  }
}
