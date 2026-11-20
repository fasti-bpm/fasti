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

import java.util.List;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.After;
import org.junit.Test;

/** How variable values are recorded: in clear, as plain SHA-256, or not at all; masked names never. */
public class AuditVariableValuesTest {

  ProcessEngine engine;

  @After
  public void closeEngine() {
    AuditTestSupport.cleanUp(engine, true);
  }

  List<AuditEvent> variables(String values, String masked) {
    engine = AuditTestSupport.engine("values-" + values, plugin -> {
      plugin.setVariableValues(values);
      if (masked != null) {
        plugin.setMaskedVariables(masked);
      }
    });
    engine.getRepositoryService().createDeployment().addModelInstance("v.bpmn", userTaskProcess("v")).deploy();
    engine.getRuntimeService().startProcessInstanceByKey("v", Variables.createVariables()
        .putValue("customer", "ACME Ltd.")
        .putValue("limit", 5000L)
        .putValue("rate", 1.5d)
        .putValue("apiKey", "sk-live-123")
        .putValue("iban", "DE89370400440532013000")
        .putValue("bytes", new byte[] {1, 2, 3}));
    return AuditTrail.of(engine).createQuery().types(AuditService.TYPE_VARIABLE).oldestFirst().list();
  }

  static String details(List<AuditEvent> events, String name) {
    return events.stream().filter(e -> name.equals(e.getEntityName())).findFirst().orElseThrow().getDetails();
  }

  @Test
  public void shouldRecordValuesInClearExceptMaskedAndBinary() {
    List<AuditEvent> events = variables("value", "*key*,iban");
    assertThat(details(events, "customer")).contains("\"value\":\"ACME Ltd.\"");
    assertThat(details(events, "limit")).contains("\"value\":5000");
    assertThat(details(events, "rate")).contains("\"value\":1.5");
    assertThat(details(events, "apiKey")).contains("\"masked\":true").doesNotContain("sk-live");
    assertThat(details(events, "iban")).contains("\"masked\":true").doesNotContain("DE89");
    assertThat(details(events, "bytes")).contains("\"valueHash\":\"sha256:").contains("\"size\":3");
    assertThat(AuditTrail.of(engine).verify().isIntact()).isTrue();
  }

  @Test
  public void shouldRecordPlainHashesWithoutKey() {
    List<AuditEvent> events = variables("hash", null);
    assertThat(details(events, "customer")).containsPattern("\"valueHash\":\"sha256:[0-9a-f]{64}\"").doesNotContain("ACME");
    assertThat(details(events, "apiKey")).contains("\"masked\":true");
  }

  @Test
  public void shouldRecordNoValues() {
    List<AuditEvent> events = variables("none", null);
    assertThat(details(events, "customer")).doesNotContain("value").contains("\"type\":\"string\"");
  }
}
