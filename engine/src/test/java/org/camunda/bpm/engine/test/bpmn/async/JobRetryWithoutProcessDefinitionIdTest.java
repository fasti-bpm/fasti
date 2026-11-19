/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.engine.test.bpmn.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.jobexecutor.FailingDelegate;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A job that does not reference a process definition, as it could be created
 * by former versions of the engine, must still be handled when it fails.
 */
public class JobRetryWithoutProcessDefinitionIdTest {

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected RuntimeService runtimeService;
  protected ManagementService managementService;

  @Before
  public void setUp() {
    runtimeService = engineRule.getRuntimeService();
    managementService = engineRule.getManagementService();
  }

  @Test
  public void shouldDecrementRetriesOfFailedJobWithoutProcessDefinitionId() {
    // given a job that does not reference its process definition
    testRule.deploy(Bpmn.createExecutableProcess("process")
        .camundaHistoryTimeToLive(180)
        .startEvent()
        .serviceTask("failingTask")
          .camundaAsyncBefore()
          .camundaClass(FailingDelegate.class.getName())
        .endEvent()
        .done());

    runtimeService.startProcessInstanceByKey("process");
    Job job = managementService.createJobQuery().singleResult();
    removeProcessDefinitionId(job.getId());

    job = managementService.createJobQuery().singleResult();
    assertThat(job.getProcessDefinitionId()).isNull();
    assertThat(job.getRetries()).isEqualTo(3);

    // when
    try {
      managementService.executeJob(job.getId());
    } catch (Exception e) {
      // expected, the service task fails
    }

    // then the job failure is handled with the default retry strategy
    job = managementService.createJobQuery().singleResult();
    assertThat(job.getRetries()).isEqualTo(2);
    assertThat(job.getExceptionMessage()).isEqualTo("Expected Exception");
  }

  protected void removeProcessDefinitionId(final String jobId) {
    final ProcessEngineConfigurationImpl configuration = engineRule.getProcessEngineConfiguration();

    configuration.getCommandExecutorTxRequired().execute(commandContext -> {
      String table = configuration.getDatabaseTablePrefix() + "ACT_RU_JOB";
      try (PreparedStatement statement = commandContext.getDbSqlSession().getSqlSession().getConnection()
          .prepareStatement("update " + table + " set PROCESS_DEF_ID_ = null where ID_ = ?")) {
        statement.setString(1, jobId);
        statement.executeUpdate();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    });
  }

}
