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

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

/** Models for the four-eyes tests. */
final class FourEyesModels {

  static final String CANDIDATES = "alice,bob,carol,dave";

  private FourEyesModels() {
  }

  /** Adds the four-eyes property with the given value to the element. */
  static BpmnModelInstance rule(BpmnModelInstance model, String elementId, String value) {
    BaseElement element = model.getModelElementById(elementId);
    ExtensionElements extensionElements = element.getExtensionElements();
    if (extensionElements == null) {
      extensionElements = model.newInstance(ExtensionElements.class);
      element.setExtensionElements(extensionElements);
    }
    CamundaProperties properties = extensionElements.addExtensionElement(CamundaProperties.class);
    CamundaProperty property = model.newInstance(CamundaProperty.class);
    property.setCamundaName(FourEyesService.PROPERTY_NAME);
    property.setCamundaValue(value);
    properties.getCamundaProperties().add(property);
    return model;
  }

  /** draft, then approve (checks draft); both open to {@link #CANDIDATES}. */
  static BpmnModelInstance draftApprove() {
    return rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").name("Draft").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").name("Approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft");
  }

  /** draft, review, then approve (checks draft and review). */
  static BpmnModelInstance draftReviewApprove() {
    return rule(Bpmn.createExecutableProcess("fourEyes").camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("draft").name("Draft").camundaCandidateUsers(CANDIDATES)
        .userTask("review").name("Review").camundaCandidateUsers(CANDIDATES)
        .userTask("approve").name("Approve").camundaCandidateUsers(CANDIDATES)
        .endEvent().done(), "approve", "draft, review");
  }
}
