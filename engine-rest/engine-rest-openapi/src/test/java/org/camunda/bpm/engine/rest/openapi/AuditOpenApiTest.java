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
package org.camunda.bpm.engine.rest.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

/** The audit trail endpoints in the generated OpenAPI document. */
public class AuditOpenApiTest {

  static JsonObject document() throws Exception {
    try (InputStream in = AuditOpenApiTest.class.getClassLoader().getResourceAsStream("openapi.json")) {
      assertThat(in).as("openapi.json").isNotNull();
      return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }

  @Test
  public void shouldDocumentMaxCountOfTheCount() throws Exception {
    JsonObject parameter = null;
    for (JsonElement element : document().getAsJsonObject("paths").getAsJsonObject("/audit/event/count")
        .getAsJsonObject("get").getAsJsonArray("parameters")) {
      if ("maxCount".equals(element.getAsJsonObject().get("name").getAsString())) {
        parameter = element.getAsJsonObject();
      }
    }
    assertThat(parameter).as("maxCount").isNotNull();
    assertThat(parameter.get("in").getAsString()).isEqualTo("query");
    assertThat(parameter.getAsJsonObject("schema").get("type").getAsString()).isEqualTo("integer");
    assertThat(parameter.get("description").getAsString()).contains("not the rows");
  }

  @Test
  public void shouldDocumentTheFailedChainsOfAPurge() throws Exception {
    JsonObject properties = document().getAsJsonObject("components").getAsJsonObject("schemas")
        .getAsJsonObject("AuditPurgeResultDto").getAsJsonObject("properties");
    assertThat(properties.getAsJsonObject("failedChains").get("type").getAsString()).isEqualTo("array");
  }
}
