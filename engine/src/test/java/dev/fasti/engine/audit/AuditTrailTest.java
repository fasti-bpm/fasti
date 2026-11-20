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

import static dev.fasti.engine.audit.AuditTestSupport.as;
import static dev.fasti.engine.audit.AuditTestSupport.startAs;
import static dev.fasti.engine.audit.AuditTestSupport.userTaskProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.SystemPermissions;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** What the audit trail records, and that what it records verifies. */
public class AuditTrailTest {

  static final String KEY = "0123456789abcdef0123456789abcdef-test-key";

  static ProcessEngine engine;
  static AuditService audit;

  @BeforeClass
  public static void buildEngine() {
    engine = AuditTestSupport.engine("trail", plugin -> {
      plugin.setCheckpointInterval(5);
      plugin.setHmacKey(KEY);
      plugin.setRetentionDays(0);
    });
    audit = AuditTrail.of(engine);
  }

  @AfterClass
  public static void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  @After
  public void resetClock() {
    ClockUtil.reset();
    engine.getIdentityService().clearAuthentication();
  }

  static List<AuditEvent> of(String processInstanceId) {
    return audit.createQuery().processInstanceId(processInstanceId).oldestFirst().list();
  }

  static List<String> actions(List<AuditEvent> events, String type) {
    return events.stream().filter(event -> type.equals(event.getType())).map(AuditEvent::getAction).collect(Collectors.toList());
  }

  static AuditEvent first(List<AuditEvent> events, String type, String action) {
    return events.stream().filter(event -> type.equals(event.getType()) && action.equals(event.getAction())).findFirst()
        .orElseThrow(() -> new AssertionError("no " + type + "/" + action + " in " + events));
  }

  @Test
  public void shouldRecordTheLifeOfAProcessInstanceWithActorsAndBusinessKey() {
    engine.getRepositoryService().createDeployment().addModelInstance("life.bpmn", userTaskProcess("life")).deploy();
    ProcessInstance instance = startAs(engine, "alice", "life", "order-4711");
    Task task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
    as(engine, "bob", () -> {
      engine.getTaskService().claim(task.getId(), "bob");
      engine.getTaskService().complete(task.getId());
    });

    List<AuditEvent> events = of(instance.getId());
    assertThat(actions(events, AuditService.TYPE_PROCESS)).containsExactly("start", "end");
    assertThat(actions(events, AuditService.TYPE_TASK)).contains("create", "assign", "complete");

    AuditEvent start = first(events, AuditService.TYPE_PROCESS, "start");
    assertThat(start.getActor()).isEqualTo("alice");
    assertThat(start.getBusinessKey()).isEqualTo("order-4711");
    assertThat(start.getProcessDefinitionKey()).isEqualTo("life");
    assertThat(start.getEngineName()).isEqualTo(engine.getName());
    assertThat(start.getDetails()).contains("\"startUserId\":\"alice\"");

    AuditEvent assign = first(events, AuditService.TYPE_TASK, "assign");
    assertThat(assign.getActor()).isEqualTo("bob");
    assertThat(assign.getEntityId()).isEqualTo(task.getId());
    assertThat(assign.getEntityName()).isEqualTo("Review");
    assertThat(assign.getDetails()).contains("\"assignee\":\"bob\"");
    assertThat(assign.getBusinessKey()).isEqualTo("order-4711");

    AuditEvent complete = first(events, AuditService.TYPE_TASK, "complete");
    assertThat(complete.getActor()).isEqualTo("bob");
    assertThat(first(events, AuditService.TYPE_PROCESS, "end").getActor()).isEqualTo("bob");

    // the user operation log as well: claim and complete by bob
    assertThat(events.stream().filter(e -> AuditService.TYPE_OPERATION.equals(e.getType())).map(AuditEvent::getAction))
        .contains("Claim", "Complete");
    assertThat(audit.createQuery().businessKey("order-4711").count()).isEqualTo(events.size());
    assertThat(audit.verify().isIntact()).isTrue();
  }

  @Test
  public void shouldRecordTheEngineAsActorWithoutAuthenticatedUser() {
    engine.getRepositoryService().createDeployment().addModelInstance("system.bpmn", userTaskProcess("system")).deploy();
    ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("system");
    AuditEvent start = first(of(instance.getId()), AuditService.TYPE_PROCESS, "start");
    assertThat(start.getActor()).isNull();
    assertThat(audit.createQuery().processInstanceId(instance.getId()).systemActor().count()).isGreaterThanOrEqualTo(2);
  }

  @Test
  public void shouldRecordDeletionOfATaskAndAnInstance() {
    engine.getRepositoryService().createDeployment().addModelInstance("deleted.bpmn", userTaskProcess("deleted")).deploy();
    ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("deleted");
    as(engine, "carol", () -> engine.getRuntimeService().deleteProcessInstance(instance.getId(), "no longer needed"));

    List<AuditEvent> events = of(instance.getId());
    AuditEvent deleted = first(events, AuditService.TYPE_TASK, "delete");
    assertThat(deleted.getActor()).isEqualTo("carol");
    AuditEvent end = first(events, AuditService.TYPE_PROCESS, "end");
    assertThat(end.getDetails()).contains("no longer needed");
    assertThat(first(events, AuditService.TYPE_OPERATION, "Delete").getActor()).isEqualTo("carol");
  }

  @Test
  public void shouldRecordVariablesAsHashesAndMaskSecrets() {
    engine.getRepositoryService().createDeployment().addModelInstance("vars.bpmn", userTaskProcess("vars")).deploy();
    ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("vars",
        Variables.createVariables().putValue("amount", 1200L).putValue("dbPassword", "hunter2"));
    as(engine, "dave", () -> {
      engine.getRuntimeService().setVariable(instance.getId(), "amount", 1500L);
      engine.getRuntimeService().removeVariable(instance.getId(), "amount");
    });

    List<AuditEvent> variables = audit.createQuery().processInstanceId(instance.getId())
        .types(AuditService.TYPE_VARIABLE).oldestFirst().list();
    List<AuditEvent> amount = variables.stream().filter(v -> "amount".equals(v.getEntityName())).collect(Collectors.toList());
    assertThat(amount).extracting(AuditEvent::getAction).containsExactly("create", "update", "delete");
    // signed, so short values cannot be guessed from the hash
    assertThat(amount.get(0).getDetails()).contains("\"valueHash\":\"hmac-sha256:").doesNotContain("1200");
    assertThat(amount.get(1).getDetails()).doesNotContain("1500");
    assertThat(amount.get(0).getDetails()).isNotEqualTo(amount.get(1).getDetails());
    assertThat(amount.get(1).getActor()).isEqualTo("dave");
    assertThat(amount.get(2).getDetails()).doesNotContain("valueHash");

    AuditEvent secret = variables.stream().filter(v -> "dbPassword".equals(v.getEntityName())).findFirst().orElseThrow();
    assertThat(secret.getDetails()).contains("\"masked\":true").doesNotContain("hunter2").doesNotContain("valueHash");
  }

  @Test
  public void shouldRecordDeploymentsWithTheHashOfEachResource() {
    as(engine, "erin", () -> engine.getRepositoryService().createDeployment().name("contracts")
        .addModelInstance("contract.bpmn", userTaskProcess("contract")).deploy());
    Deployment deployment = engine.getRepositoryService().createDeploymentQuery().deploymentName("contracts").singleResult();
    // the same resources again with duplicate filtering: nothing new is deployed, nothing recorded
    engine.getRepositoryService().createDeployment().name("contracts").enableDuplicateFiltering(false)
        .addModelInstance("contract.bpmn", userTaskProcess("contract")).deploy();
    engine.getRepositoryService().deleteDeployment(deployment.getId(), true);

    List<AuditEvent> events = audit.createQuery().types(AuditService.TYPE_DEPLOYMENT).entityId(deployment.getId())
        .oldestFirst().list();
    assertThat(events).extracting(AuditEvent::getAction).containsExactly("create", "delete");
    assertThat(events.get(0).getActor()).isEqualTo("erin");
    assertThat(events.get(0).getEntityName()).isEqualTo("contracts");
    assertThat(events.get(0).getDetails()).containsPattern("\"contract.bpmn\":\"[0-9a-f]{64}\"");
  }

  @Test
  public void shouldRecordIdentityAndAuthorizationChangesOfTheOperationLog() {
    as(engine, "admin", () -> {
      User user = engine.getIdentityService().newUser("frank");
      user.setPassword("not-in-the-audit-trail");
      engine.getIdentityService().saveUser(user);
      Authorization authorization = engine.getAuthorizationService().createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
      authorization.setUserId("frank");
      authorization.setResource(Resources.SYSTEM);
      authorization.setResourceId("*");
      authorization.addPermission(SystemPermissions.READ);
      engine.getAuthorizationService().saveAuthorization(authorization);
      engine.getAuthorizationService().deleteAuthorization(authorization.getId());
    });

    List<AuditEvent> operations = audit.createQuery().types(AuditService.TYPE_OPERATION).actor("admin").oldestFirst().list();
    assertThat(operations).anySatisfy(e -> {
      assertThat(e.getEntityType()).isEqualTo("User");
      assertThat(e.getAction()).isEqualTo("Create");
      assertThat(e.getDetails()).contains("frank");
    });
    assertThat(operations).anySatisfy(e -> {
      assertThat(e.getEntityType()).isEqualTo("Authorization");
      assertThat(e.getAction()).isEqualTo("Create");
    });
    assertThat(operations).anySatisfy(e -> {
      assertThat(e.getEntityType()).isEqualTo("Authorization");
      assertThat(e.getAction()).isEqualTo("Delete");
    });
    assertThat(operations).allSatisfy(e -> assertThat(e.getDetails()).doesNotContain("not-in-the-audit-trail"));
  }

  @Test
  public void shouldRecordIncidents() {
    engine.getRepositoryService().createDeployment().addModelInstance("incident.bpmn", userTaskProcess("incident")).deploy();
    ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("incident");
    String executionId = engine.getRuntimeService().createExecutionQuery().processInstanceId(instance.getId())
        .activityId("review").singleResult().getId();
    String incidentId = engine.getRuntimeService().createIncident("payment-failed", executionId, "cfg", "card declined").getId();
    engine.getRuntimeService().resolveIncident(incidentId);

    List<AuditEvent> incidents = audit.createQuery().processInstanceId(instance.getId()).types(AuditService.TYPE_INCIDENT)
        .oldestFirst().list();
    assertThat(incidents).extracting(AuditEvent::getAction).containsExactly("create", "resolve");
    assertThat(incidents.get(0).getEntityId()).isEqualTo(incidentId);
    assertThat(incidents.get(0).getEntityName()).isEqualTo("payment-failed");
    assertThat(incidents.get(0).getDetails()).contains("card declined");
  }

  @Test
  public void shouldRecordDecisions() {
    engine.getRepositoryService().createDeployment().addClasspathResource("dev/fasti/engine/audit/approval.dmn").deploy();
    engine.getDecisionService().evaluateDecisionTableByKey("approval", Variables.createVariables().putValue("amount", 50));
    List<AuditEvent> decisions = audit.createQuery().types(AuditService.TYPE_DECISION).list();
    assertThat(decisions).isNotEmpty();
    assertThat(decisions.get(0).getAction()).isEqualTo("evaluate");
    assertThat(decisions.get(0).getEntityName()).isEqualTo("approval");
  }

  @Test
  public void shouldNotRecordWhatIsRolledBack() {
    engine.getRepositoryService().createDeployment().addModelInstance("failing.bpmn",
        Bpmn.createExecutableProcess("failing").camundaHistoryTimeToLive(180).startEvent()
            .serviceTask().camundaClass(Failing.class.getName()).endEvent().done()).deploy();
    long before = audit.createQuery().types(AuditService.TYPE_PROCESS).count();
    assertThatThrownBy(() -> engine.getRuntimeService().startProcessInstanceByKey("failing")).hasMessageContaining("boom");
    assertThat(audit.createQuery().types(AuditService.TYPE_PROCESS).count()).isEqualTo(before);
    assertThat(audit.verify().isIntact()).isTrue();
  }

  public static class Failing implements JavaDelegate {
    @Override
    public void execute(org.camunda.bpm.engine.delegate.DelegateExecution execution) {
      throw new IllegalStateException("boom");
    }
  }

  @Test
  public void shouldChainRecordsAndSignCheckpoints() {
    engine.getRepositoryService().createDeployment().addModelInstance("chain.bpmn", userTaskProcess("chain")).deploy();
    for (int i = 0; i < 5; i++) {
      engine.getRuntimeService().startProcessInstanceByKey("chain");
    }
    List<AuditEvent> all = audit.createQuery().oldestFirst().list();
    Map<String, List<AuditEvent>> byChain = all.stream().collect(Collectors.groupingBy(AuditEvent::getChainId));
    for (List<AuditEvent> chain : byChain.values()) {
      chain.sort((a, b) -> Long.compare(a.getSequence(), b.getSequence()));
      for (int i = 1; i < chain.size(); i++) {
        assertThat(chain.get(i).getSequence()).isEqualTo(chain.get(i - 1).getSequence() + 1);
        assertThat(chain.get(i).getPreviousHash()).isEqualTo(chain.get(i - 1).getHash());
      }
    }
    assertThat(all).anySatisfy(e -> assertThat(e.getAction()).isEqualTo(AuditService.ACTION_CHECKPOINT));
    assertThat(all).filteredOn(e -> AuditService.ACTION_CHECKPOINT.equals(e.getAction()))
        .allSatisfy(e -> assertThat(e.getDetails()).contains("\"hmac\":").contains(audit.getStatus().getKeyId()));

    AuditVerification verification = audit.verify();
    assertThat(verification.isIntact()).isTrue();
    assertThat(verification.getChains()).allSatisfy(chain -> assertThat(chain.getCheckpointsVerified())
        .isEqualTo(chain.getCheckpoints()));
    assertThat(verification.getChains().stream().mapToLong(AuditVerification.Chain::getCheckpoints).sum()).isPositive();
    // the verification itself is on record
    assertThat(audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_VERIFY).count()).isPositive();
  }

  @Test
  public void shouldRecordExports() {
    as(engine, "auditor", () -> audit.recordExport("csv", "processInstanceId=1", 3));
    AuditEvent export = audit.createQuery().types(AuditService.TYPE_AUDIT).action(AuditService.ACTION_EXPORT).list().get(0);
    assertThat(export.getActor()).isEqualTo("auditor");
    assertThat(export.getDetails()).contains("\"count\":3").contains("\"format\":\"csv\"");
  }

  @Test
  public void shouldPageAndFilterByTime() {
    engine.getRepositoryService().createDeployment().addModelInstance("paging.bpmn", userTaskProcess("paging")).deploy();
    Date start = new Date(ClockUtil.getCurrentTime().getTime() + 3_600_000);
    ClockUtil.setCurrentTime(start);
    String pi = engine.getRuntimeService().startProcessInstanceByKey("paging").getId();
    long total = audit.createQuery().processInstanceId(pi).count();
    assertThat(total).isGreaterThanOrEqualTo(2);
    assertThat(audit.createQuery().processInstanceId(pi).listPage(1, 1)).hasSize(1);
    assertThat(audit.createQuery().from(start).processInstanceId(pi).count()).isEqualTo(total);
    assertThat(audit.createQuery().to(start).processInstanceId(pi).count()).isZero();
    assertThat(audit.createQuery().processInstanceId(pi).list().get(0).getTimestamp()).isEqualTo(start);
  }

  @Test
  public void shouldNotBeTouchedByHistoryCleanup() {
    engine.getRepositoryService().createDeployment().addModelInstance("cleanup.bpmn",
        Bpmn.createExecutableProcess("cleanup").camundaHistoryTimeToLive(0).startEvent().endEvent().done()).deploy();
    String pi = engine.getRuntimeService().startProcessInstanceByKey("cleanup").getId();
    long recorded = audit.createQuery().processInstanceId(pi).count();
    assertThat(recorded).isPositive();

    ClockUtil.setCurrentTime(new Date(ClockUtil.getCurrentTime().getTime() + 3 * 86_400_000L));
    String jobId = engine.getHistoryService().cleanUpHistoryAsync(true).getId();
    engine.getManagementService().executeJob(jobId);
    assertThat(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(pi).count()).isZero();
    engine.getHistoryService().findHistoryCleanupJobs().forEach(job -> engine.getManagementService().deleteJob(job.getId()));

    assertThat(audit.createQuery().processInstanceId(pi).count()).isEqualTo(recorded);
  }

  @Test
  public void shouldDescribeItsSettingsWithoutTheKey() {
    AuditStatus status = audit.getStatus();
    assertThat(audit.isEnabled()).isTrue();
    assertThat(status.getNodeId()).isEqualTo("node-trail");
    assertThat(status.getChains()).isEqualTo(4);
    assertThat(status.isSigned()).isTrue();
    assertThat(status.getKeyId()).hasSize(12).doesNotContain(KEY);
    assertThat(status.getTypes()).contains(AuditService.TYPE_TASK).doesNotContain(AuditService.TYPE_ACTIVITY);
    assertThat(status.getHistoryLevel()).isEqualTo("full");
  }
}
