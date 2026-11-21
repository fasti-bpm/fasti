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
package dev.fasti.engine.foureyes;

import static dev.fasti.engine.foureyes.FourEyesModels.draftApprove;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * The control rests on the variable it records, so it works at every history level; the
 * history adds to it where it is kept.
 *
 * <p>Each history level is a nested test class of its own. Surefire runs the nested classes
 * directly, so this class is abstract and has no runner of its own: running a nested class a
 * second time through {@code Enclosed} would find the engine of its class rule already closed.
 */
public abstract class FourEyesHistoryLevelTest {

  static ProcessEngineBootstrapRule engine(String name, String historyLevel) {
    return new ProcessEngineBootstrapRule(configuration -> {
      configuration.setJdbcUrl("jdbc:h2:mem:fasti-foureyes-" + name + ";DB_CLOSE_DELAY=-1");
      configuration.setDatabaseSchemaUpdate("create-drop");
      configuration.setProcessEngineName("foureyes-" + name);
      configuration.setHistory(historyLevel);
      configuration.getProcessEnginePlugins().add(new FourEyesPlugin());
    });
  }

  abstract static class Scenario {
    protected ProvidedProcessEngineRule engineRule;
    protected ProcessEngineTestRule testRule;

    @Rule
    public RuleChain ruleChain;

    Scenario(ProcessEngineBootstrapRule bootstrapRule) {
      engineRule = new ProvidedProcessEngineRule(bootstrapRule);
      testRule = new ProcessEngineTestRule(engineRule);
      ruleChain = RuleChain.outerRule(engineRule).around(testRule);
    }

    void completeAs(String userId, Task task) {
      IdentityService identityService = engineRule.getIdentityService();
      identityService.setAuthenticatedUserId(userId);
      try {
        engineRule.getTaskService().claim(task.getId(), userId);
        engineRule.getTaskService().complete(task.getId());
      } finally {
        identityService.clearAuthentication();
      }
    }

    Task task(String processInstanceId) {
      return engineRule.getTaskService().createTaskQuery().processInstanceId(processInstanceId).singleResult();
    }

    String draftedBy(String userId) {
      testRule.deploy(draftApprove());
      String pi = engineRule.getRuntimeService().startProcessInstanceByKey("fourEyes").getId();
      completeAs(userId, task(pi));
      return pi;
    }

    void assertAliceRefused(String pi) {
      Task approve = task(pi);
      TaskService taskService = engineRule.getTaskService();
      engineRule.getIdentityService().setAuthenticatedUserId("alice");
      try {
        taskService.claim(approve.getId(), "alice");
        assertThatThrownBy(() -> taskService.complete(approve.getId())).isInstanceOf(FourEyesViolationException.class);
      } finally {
        engineRule.getIdentityService().clearAuthentication();
      }
    }

    @Test
    public void shouldRefuseTheSameUser() {
      assertAliceRefused(draftedBy("alice"));
    }

    @Test
    public void shouldAllowAnotherUser() {
      String pi = draftedBy("alice");
      completeAs("bob", task(pi));
      assertThat(engineRule.getRuntimeService().createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
    }

    @Test
    public void shouldReportTheStatus() {
      String pi = draftedBy("alice");
      Task approve = task(pi);
      engineRule.getIdentityService().setAuthenticatedUserId("alice");
      try {
        FourEyesStatus status = FourEyes.of(engineRule.getProcessEngine()).getStatus(approve.getId());
        assertThat(status.isUserExcluded()).isTrue();
        assertThat(status.isBlocked()).isTrue();
      } finally {
        engineRule.getIdentityService().clearAuthentication();
      }
    }
  }

  public static class HistoryNone extends Scenario {
    @ClassRule
    public static ProcessEngineBootstrapRule bootstrapRule = engine("none", ProcessEngineConfiguration.HISTORY_NONE);

    public HistoryNone() {
      super(bootstrapRule);
    }

    @Test
    public void shouldNotEnforceOnceTheRecordIsRemoved() {
      // without history the variable is the only record (documented limitation)
      String pi = draftedBy("alice");
      RuntimeService runtimeService = engineRule.getRuntimeService();
      runtimeService.removeVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft");
      completeAs("alice", task(pi));
      assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi).count()).isZero();
    }
  }

  public static class HistoryActivity extends Scenario {
    @ClassRule
    public static ProcessEngineBootstrapRule bootstrapRule = engine("activity", ProcessEngineConfiguration.HISTORY_ACTIVITY);

    public HistoryActivity() {
      super(bootstrapRule);
    }

    @Test
    public void shouldUseTheHistoricAssigneeOnceTheRecordIsRemoved() {
      String pi = draftedBy("alice");
      engineRule.getRuntimeService().removeVariable(pi, FourEyesService.VARIABLE_PREFIX + "draft");
      assertAliceRefused(pi);
    }
  }

  public static class HistoryAudit extends Scenario {
    @ClassRule
    public static ProcessEngineBootstrapRule bootstrapRule = engine("audit", ProcessEngineConfiguration.HISTORY_AUDIT);

    public HistoryAudit() {
      super(bootstrapRule);
    }
  }
}
