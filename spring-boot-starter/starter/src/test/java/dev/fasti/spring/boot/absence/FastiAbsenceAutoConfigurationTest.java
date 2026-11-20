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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import dev.fasti.engine.absence.AbsenceService;
import dev.fasti.engine.absence.AbsenceSubstitutionPlugin;
import dev.fasti.engine.absence.Absences;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(Enclosed.class)
public class FastiAbsenceAutoConfigurationTest {

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=absenceEngine",
      "camunda.bpm.job-execution.enabled=false",
      "fasti.absence.enabled=true",
      "fasti.absence.max-hops=2"
  })
  public static class Enabled {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected AbsenceSubstitutionPlugin plugin;

    @Test
    public void shouldSubstituteAbsentAssignees() {
      assertThat(plugin.getMaxHops()).isEqualTo(2);
      AbsenceService absences = Absences.of(processEngine);
      assertThat(absences.isEnabled()).isTrue();

      IdentityService identityService = processEngine.getIdentityService();
      identityService.saveUser(identityService.newUser("absentAlice"));
      identityService.saveUser(identityService.newUser("presentBob"));
      RepositoryService repositoryService = processEngine.getRepositoryService();
      Deployment deployment = repositoryService.createDeployment().addModelInstance("absence.bpmn",
          Bpmn.createExecutableProcess("absenceProcess").camundaHistoryTimeToLive(1)
              .startEvent().userTask().camundaAssignee("absentAlice").endEvent().done()).deploy();
      try {
        absences.setAbsence("absentAlice", null, Date.from(Instant.now().plus(1, ChronoUnit.DAYS)), "presentBob", null);

        String processInstanceId = processEngine.getRuntimeService().startProcessInstanceByKey("absenceProcess").getId();

        assertThat(processEngine.getTaskService().createTaskQuery().processInstanceId(processInstanceId)
            .singleResult().getAssignee()).isEqualTo("presentBob");
      } finally {
        repositoryService.deleteDeployment(deployment.getId(), true);
        identityService.deleteUser("absentAlice");
        identityService.deleteUser("presentBob");
      }
    }
  }

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=noAbsenceEngine",
      "camunda.bpm.job-execution.enabled=false"
  })
  public static class DisabledByDefault {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected ObjectProvider<AbsenceSubstitutionPlugin> plugin;

    @Test
    public void shouldNotRegisterThePlugin() {
      assertThat(plugin.getIfAvailable()).isNull();
      assertThat(Absences.of(processEngine).isEnabled()).isFalse();
    }
  }
}
