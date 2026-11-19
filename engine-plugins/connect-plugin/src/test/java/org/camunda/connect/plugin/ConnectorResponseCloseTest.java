/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.connect.plugin;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.connect.plugin.util.CloseableTestConnector;
import org.camunda.connect.plugin.util.CloseableTestConnector.Response;

/**
 * Asserts that the response of a connector is released by the engine exactly
 * once, whatever happens to the output mapping.
 */
public class ConnectorResponseCloseTest extends PluggableProcessEngineTestCase {

  protected static final String NO_IO_MAPPING = "";

  protected static final String INPUT_MAPPING_ONLY =
      "<camunda:inputOutput>"
    + "  <camunda:inputParameter name=\"in\">value</camunda:inputParameter>"
    + "</camunda:inputOutput>";

  protected static final String OUTPUT_MAPPING =
      "<camunda:inputOutput>"
    + "  <camunda:outputParameter name=\"out\">${result}</camunda:outputParameter>"
    + "</camunda:inputOutput>";

  protected static final String FAILING_OUTPUT_MAPPING =
      "<camunda:inputOutput>"
    + "  <camunda:outputParameter name=\"out\">${unknownResponseParameter}</camunda:outputParameter>"
    + "</camunda:inputOutput>";

  protected String deploymentId;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CloseableTestConnector.reset();
    CloseableTestConnector.responseParameters.put("result", "someValue");
  }

  @Override
  protected void tearDown() throws Exception {
    CloseableTestConnector.reset();
    if (deploymentId != null) {
      repositoryService.deleteDeployment(deploymentId, true);
    }
    super.tearDown();
  }

  public void testResponseClosedAfterOutputMapping() {
    // given
    deploy(OUTPUT_MAPPING);

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the output mapping could read the response
    assertEquals("someValue", runtimeService.getVariable(processInstance.getId(), "out"));

    // and the response was closed once, after it was read
    Response response = getSingleResponse();
    assertEquals(1, response.closeCount);
    assertEquals(0, response.readCountAfterClose);
  }

  public void testResponseClosedWithoutIoMapping() {
    // given
    deploy(NO_IO_MAPPING);

    // when
    runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertEquals(1, getSingleResponse().closeCount);
  }

  public void testResponseClosedWithoutOutputMapping() {
    // given
    deploy(INPUT_MAPPING_ONLY);

    // when
    runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertEquals(1, getSingleResponse().closeCount);
  }

  public void testResponseClosedIfOutputMappingFails() {
    // given
    deploy(FAILING_OUTPUT_MAPPING);

    // when
    try {
      runtimeService.startProcessInstanceByKey("testProcess");
      fail("Exception expected");
    } catch (ProcessEngineException e) {
      // then the failure of the mapping is reported
      assertTrue(e.getMessage(), e.getMessage().contains("unknownResponseParameter"));
    }

    // and the response was closed once
    assertEquals(1, getSingleResponse().closeCount);
  }

  public void testFailingCloseDoesNotFailActivity() {
    // given
    CloseableTestConnector.closeFailure = new IllegalStateException("expected close failure");
    deploy(OUTPUT_MAPPING);

    // when
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("testProcess");

    // then the activity is completed
    assertEquals("someValue", runtimeService.getVariable(processInstance.getId(), "out"));
    assertEquals(1, runtimeService.createExecutionQuery().activityId("wait").count());

    // and closing the response was not attempted again
    assertEquals(1, getSingleResponse().closeCount);
  }

  public void testFailingCloseDoesNotHideFailureOfOutputMapping() {
    // given
    CloseableTestConnector.closeFailure = new IllegalStateException("expected close failure");
    deploy(FAILING_OUTPUT_MAPPING);

    // when
    try {
      runtimeService.startProcessInstanceByKey("testProcess");
      fail("Exception expected");
    } catch (ProcessEngineException e) {
      // then
      assertTrue(e.getMessage(), e.getMessage().contains("unknownResponseParameter"));
    }
    assertEquals(1, getSingleResponse().closeCount);
  }

  public void testEveryResponseClosed() {
    // given
    deploy(OUTPUT_MAPPING);

    // when
    runtimeService.startProcessInstanceByKey("testProcess");
    runtimeService.startProcessInstanceByKey("testProcess");
    runtimeService.startProcessInstanceByKey("testProcess");

    // then
    assertEquals(3, CloseableTestConnector.responses.size());
    for (Response response : CloseableTestConnector.responses) {
      assertEquals(1, response.closeCount);
    }
  }

  protected Response getSingleResponse() {
    assertEquals(1, CloseableTestConnector.responses.size());
    return CloseableTestConnector.responses.get(0);
  }

  protected void deploy(String ioMapping) {
    String process =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
      + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
      + "    xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\""
      + "    targetNamespace=\"http://camunda.org/schema/1.0/bpmn\">"
      + "  <process id=\"testProcess\" isExecutable=\"true\">"
      + "    <startEvent id=\"start\" />"
      + "    <sequenceFlow sourceRef=\"start\" targetRef=\"task\" />"
      + "    <serviceTask id=\"task\">"
      + "      <extensionElements>"
      + "        <camunda:connector>"
      + "          <camunda:connectorId>" + CloseableTestConnector.ID + "</camunda:connectorId>"
      + ioMapping
      + "        </camunda:connector>"
      + "      </extensionElements>"
      + "    </serviceTask>"
      + "    <sequenceFlow sourceRef=\"task\" targetRef=\"wait\" />"
      + "    <receiveTask id=\"wait\" />"
      + "    <sequenceFlow sourceRef=\"wait\" targetRef=\"end\" />"
      + "    <endEvent id=\"end\" />"
      + "  </process>"
      + "</definitions>";

    deploymentId = repositoryService.createDeployment()
      .addString("testProcess.bpmn", process)
      .deploy()
      .getId();
  }

}
