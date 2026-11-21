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
package dev.fasti.spring.boot.mcp;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

/**
 * Users, permissions and models of the tests:
 * <ul>
 * <li>{@code admin}: member of camunda-admin, may do everything;</li>
 * <li>{@code carol}: may read the definitions and instances of {@code mcpProcess} only;</li>
 * <li>{@code bob}: no permissions at all;</li>
 * <li>{@code eve}: no permissions, used for wrong passwords (the engine delays her logins).</li>
 * </ul>
 * {@code mcpProcess} has a review task and an approval that the reviewer may not do (four eyes),
 * {@code otherProcess} a single task, {@code failingProcess} an asynchronous service task that
 * fails; {@code mcpDecision} picks an approver by amount.
 */
public final class McpTestFixture {

  public static final String ADMIN = "admin";
  public static final String ADMIN_PASSWORD = "admin-Secret-1";
  public static final String CAROL = "carol";
  public static final String BOB = "bob";
  public static final String EVE = "eve";
  public static final String PASSWORD = "user-Secret-1";

  private McpTestFixture() {
  }

  public static synchronized void setUp(ProcessEngine engine) {
    IdentityService identityService = engine.getIdentityService();
    if (identityService.createUserQuery().userId(BOB).count() > 0) {
      return;
    }
    for (String id : new String[] { CAROL, BOB, EVE }) {
      User user = identityService.newUser(id);
      user.setFirstName(id);
      user.setPassword(PASSWORD);
      identityService.saveUser(user);
    }
    Group reviewers = identityService.newGroup("reviewers");
    reviewers.setName("Reviewers");
    identityService.saveGroup(reviewers);
    identityService.createMembership(CAROL, "reviewers");

    AuthorizationService authorizationService = engine.getAuthorizationService();
    Authorization definition = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    definition.setUserId(CAROL);
    definition.setResource(Resources.PROCESS_DEFINITION);
    definition.setResourceId("mcpProcess");
    definition.addPermission(ProcessDefinitionPermissions.READ);
    definition.addPermission(ProcessDefinitionPermissions.READ_INSTANCE);
    definition.addPermission(ProcessDefinitionPermissions.READ_HISTORY);
    authorizationService.saveAuthorization(definition);
    Authorization instances = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    instances.setUserId(CAROL);
    instances.setResource(Resources.PROCESS_INSTANCE);
    instances.setResourceId("*");
    instances.addPermission(Permissions.READ);
    authorizationService.saveAuthorization(instances);

    engine.getRepositoryService().createDeployment()
        .name("mcp-test")
        .addModelInstance("mcpProcess.bpmn", mcpProcess())
        .addModelInstance("otherProcess.bpmn", Bpmn.createExecutableProcess("otherProcess")
            .name("Other process").camundaHistoryTimeToLive(1)
            .startEvent().userTask("other").name("Other task").endEvent().done())
        .addModelInstance("failingProcess.bpmn", Bpmn.createExecutableProcess("failingProcess")
            .camundaHistoryTimeToLive(1)
            .startEvent().serviceTask("fail").camundaAsyncBefore().camundaClass("dev.fasti.does.not.Exist")
            .endEvent().done())
        .addClasspathResource("mcp-decision.dmn")
        .deploy();
  }

  static BpmnModelInstance mcpProcess() {
    BpmnModelInstance model = Bpmn.createExecutableProcess("mcpProcess").name("MCP process")
        .camundaHistoryTimeToLive(1)
        .startEvent()
        .userTask("review").name("Review").camundaCandidateGroups("reviewers")
        .userTask("approve").name("Approve").camundaCandidateGroups("reviewers")
        .endEvent().done();
    UserTask approve = model.getModelElementById("approve");
    ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
    approve.setExtensionElements(extensionElements);
    CamundaProperty property = model.newInstance(CamundaProperty.class);
    property.setCamundaName("fasti:fourEyes");
    property.setCamundaValue("review");
    extensionElements.addExtensionElement(CamundaProperties.class).getCamundaProperties().add(property);
    return model;
  }
}
