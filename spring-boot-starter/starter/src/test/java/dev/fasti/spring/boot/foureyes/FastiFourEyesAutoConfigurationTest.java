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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fasti.engine.foureyes.FourEyes;
import dev.fasti.engine.foureyes.FourEyesPlugin;
import dev.fasti.engine.foureyes.FourEyesService;
import dev.fasti.engine.foureyes.FourEyesViolationException;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.camunda.bpm.spring.boot.starter.test.nonpa.TestApplication;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(Enclosed.class)
public class FastiFourEyesAutoConfigurationTest {

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=fourEyesEngine",
      "camunda.bpm.job-execution.enabled=false",
      "fasti.four-eyes.enabled=true"
  })
  public static class Enabled {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected FourEyesPlugin plugin;

    @Test
    public void shouldRefuseTheSameUser() {
      assertThat(plugin).isNotNull();
      assertThat(FourEyes.of(processEngine).isEnabled()).isTrue();

      BpmnModelInstance model = Bpmn.createExecutableProcess("fourEyesProcess").camundaHistoryTimeToLive(1)
          .startEvent().userTask("draft").camundaAssignee("alice").userTask("approve").camundaAssignee("alice")
          .endEvent().done();
      UserTask approve = model.getModelElementById("approve");
      ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
      approve.setExtensionElements(extensionElements);
      CamundaProperty property = model.newInstance(CamundaProperty.class);
      property.setCamundaName(FourEyesService.PROPERTY_NAME);
      property.setCamundaValue("draft");
      extensionElements.addExtensionElement(CamundaProperties.class).getCamundaProperties().add(property);

      RepositoryService repositoryService = processEngine.getRepositoryService();
      Deployment deployment = repositoryService.createDeployment().addModelInstance("fourEyes.bpmn", model).deploy();
      TaskService taskService = processEngine.getTaskService();
      IdentityService identityService = processEngine.getIdentityService();
      try {
        String pi = processEngine.getRuntimeService().startProcessInstanceByKey("fourEyesProcess").getId();
        identityService.setAuthenticatedUserId("alice");
        taskService.complete(taskService.createTaskQuery().processInstanceId(pi).singleResult().getId());
        Task approval = taskService.createTaskQuery().processInstanceId(pi).singleResult();

        assertThatThrownBy(() -> taskService.complete(approval.getId())).isInstanceOf(FourEyesViolationException.class);
      } finally {
        identityService.clearAuthentication();
        repositoryService.deleteDeployment(deployment.getId(), true);
      }
    }
  }

  @RunWith(SpringRunner.class)
  @SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
      "camunda.bpm.process-engine-name=noFourEyesEngine",
      "camunda.bpm.job-execution.enabled=false"
  })
  public static class DisabledByDefault {

    @Autowired
    protected ProcessEngine processEngine;

    @Autowired
    protected ObjectProvider<FourEyesPlugin> plugin;

    @Test
    public void shouldNotRegisterThePlugin() {
      assertThat(plugin.getIfAvailable()).isNull();
      assertThat(FourEyes.of(processEngine).isEnabled()).isFalse();
    }
  }
}
