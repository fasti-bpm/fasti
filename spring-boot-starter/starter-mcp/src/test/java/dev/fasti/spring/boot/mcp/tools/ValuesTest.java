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
package dev.fasti.spring.boot.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;

import dev.fasti.spring.boot.mcp.FastiMcpProperties;

class ValuesTest {

  FastiMcpProperties properties = properties();
  Values values = new Values(properties);

  static FastiMcpProperties properties() {
    FastiMcpProperties properties = new FastiMcpProperties();
    properties.setMaxValueLength(10);
    properties.setMaxInputVariables(3);
    properties.setMaxInputValueLength(20);
    return properties;
  }

  @Test
  void masksSecretLookingNamesWhateverTheCase() {
    for (String name : List.of("password", "dbPassword", "CLIENT_SECRET", "accessToken", "awsCredentials",
        "apiKey", "my_api_key")) {
      Map<String, Object> variable = values.variable(name, Variables.stringValue("top-secret"), null);
      assertThat(variable).as(name).containsEntry("masked", true).doesNotContainKey("value");
    }
    assertThat(values.variable("amount", Variables.integerValue(5), null)).containsEntry("value", 5)
        .doesNotContainKey("masked");
  }

  @Test
  void masksConfiguredNames() {
    properties.setMaskedVariableNames(List.of("iban"));
    Values custom = new Values(properties);

    assertThat(custom.variable("customerIban", Variables.stringValue("DE00"), null)).containsEntry("masked", true);
    assertThat(custom.variable("password", Variables.stringValue("x"), null)).containsEntry("value", "x");
  }

  @Test
  void cutsLongValues() {
    Map<String, Object> variable = values.variable("note", Variables.stringValue("0123456789abcdef"), null);

    assertThat(variable).containsEntry("value", "0123456789").containsEntry("truncated", true)
        .containsEntry("length", 16);
  }

  @Test
  void describesBinaryAndFilesWithoutContent() {
    Map<String, Object> bytes = values.variable("scan", Variables.byteArrayValue(new byte[] { 1, 2, 3 }), null);
    Map<String, Object> file = values.variable("invoice",
        Variables.fileValue("invoice.pdf").file(new byte[] { 1 }).mimeType("application/pdf").create(), null);

    assertThat(bytes).containsEntry("size", 3).doesNotContainKey("value");
    assertThat(file).containsEntry("filename", "invoice.pdf").containsEntry("mimeType", "application/pdf")
        .doesNotContainKey("value");
  }

  @Test
  void neverReturnsJavaSerializedObjects() {
    Map<String, Object> java = values.variable("order", Variables.serializedObjectValue("rO0ABXNy")
        .serializationDataFormat("application/x-java-serialized-object").objectTypeName("com.example.Order").create(),
        null);
    Map<String, Object> json = values.variable("order", Variables.serializedObjectValue("{\"id\":1}")
        .serializationDataFormat("application/json").objectTypeName("com.example.Order").create(), null);

    assertThat(java).containsEntry("objectTypeName", "com.example.Order").doesNotContainKey("value");
    assertThat(json).containsEntry("value", "{\"id\":1}");
  }

  @Test
  void returnsDatesAsInstants() {
    Map<String, Object> variable = values.variable("due", Variables.dateValue(new Date(0)), null);

    assertThat(variable).containsEntry("value", "1970-01-01T00:00:00Z");
  }

  @Test
  void returnsErrorsOfUnreadableVariables() {
    assertThat(values.variable("broken", null, "bad value")).containsEntry("error", "bad value");
    assertThat(values.variable("broken", null, "cannot deserialize")).containsEntry("error", "cannot des")
        .containsEntry("truncated", true);
  }

  @Test
  void acceptsPlainValuesAsVariables() {
    Map<String, Object> input = new HashMap<>();
    input.put("text", "hello");
    input.put("count", 7L);
    input.put("ratio", 0.5);

    VariableMap variables = values.toVariables(input);

    assertThat(variables.get("text")).isEqualTo("hello");
    assertThat(variables.get("count")).isEqualTo(7);
    assertThat(variables.get("ratio")).isEqualTo(0.5);
    assertThat(values.toVariables(Map.of("big", 5_000_000_000L)).get("big")).isEqualTo(5_000_000_000L);
  }

  @Test
  void refusesObjectsListsAndOversizedInput() {
    assertThatThrownBy(() -> values.toVariables(Map.of("bean", Map.of("class", "java.lang.Runtime"))))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(() -> values.toVariables(Map.of("list", List.of(1, 2))))
        .isInstanceOf(ToolException.class);
    assertThatThrownBy(() -> values.toVariables(Map.of("text", "x".repeat(21))))
        .hasMessageContaining("longer than 20");
    assertThatThrownBy(() -> values.toVariables(Map.of("a", 1, "b", 2, "c", 3, "d", 4)))
        .hasMessageContaining("At most 3");
    assertThatThrownBy(() -> values.toVariables(Map.of("nan", Double.NaN)))
        .isInstanceOf(ToolException.class);
  }
}
